package dev.jujutsuvfx.compat;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.core.StructureSever;
import dev.jujutsuvfx.core.CutContact;
import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Objects;
import java.util.Random;

/** Optional Sable 2.0 integration. Only load this class after checking that Sable is installed. */
public final class SableStructureCutter {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_SOURCE_BLOCKS = 32768;
    private static final int MAX_MOVED_BLOCKS = 32768;
    private static final int MAX_SCAN_CELLS = 1048576;
    private static final int MAX_CANDIDATES = 96;
    private static final int MAX_HIT_BODIES = 24;
    private static final int MAX_FRAGMENTS = 128;
    private static final int MAX_KERF_BLOCKS = 4096;
    private static final int MAX_LOADED_BODIES = 1024;
    private static final double MAX_ADDED_SPEED = 18.0;
    private static final Map<ServerSubLevelContainer, AllocationObserver> ALLOCATION_OBSERVERS = new WeakHashMap<>();

    private SableStructureCutter() {}

    public record Result(boolean success, int movedBlocks, int fragments, String message, List<UUID> bodies) {
        public Result { bodies = List.copyOf(bodies); }
        public Result(boolean success, int movedBlocks, int fragments, String message) {
            this(success, movedBlocks, fragments, message, List.of());
        }
    }
    /** Stable identity only: never retain plot coordinates between swings of a held technique. */
    public record Target(UUID id, Vec3 center, double radius, int blocks) {}
    /** The exact pre-cut placement is also the placement broadcast to every observer. */
    public record Strike(Result result, Vec3 center, double radius) {}

    public static Strike strike(LivingEntity player, Target target, Vec3 normal, Vec3 forward,
                                Vec3 secondaryNormal, double secondaryScale, Integer scatterSeed, Vec3 previous) {
        if (target == null || !finite(target.center()) || !Double.isFinite(target.radius()) || target.radius() <= 0)
            return new Strike(fail("斩击目标无效"), Vec3.ZERO, 1);
        if (target.id() != null)
            return cutTargetShape(player, target, normal, forward, secondaryNormal, secondaryScale, scatterSeed, previous);
        Vec3 center = target.center();
        double radius = Math.clamp(target.radius(), 1, 8);
        if (scatterSeed != null) {
            Vec3 offset = scatterOffset(scatterSeed, center, radius, previous);
            center = center.add(offset);
            // Keep the entire new source sphere (including either cross arm) inside the
            // original selection. Random placement must not make the burst's range creep.
            double scale = secondaryNormal == null ? 1 : Math.max(1, secondaryScale);
            radius -= offset.length() / scale;
        }
        Result result = secondaryNormal == null ? cut(player, center, normal, forward, radius)
                : cutCross(player, center, normal, forward, radius, secondaryNormal, secondaryScale);
        return new Strike(result, center, radius);
    }

    private static Random scatterRandom(int seed) {
        return new Random(Integer.toUnsignedLong(seed) * 0x9e3779b97f4a7c15L ^ 0x4ac71d392b5e680fL);
    }

    private static Vec3 scatterOffset(int seed, Vec3 anchor, double radius, Vec3 previous) {
        Random random = scatterRandom(seed);
        Vec3 best = Vec3.ZERO;
        double bestDistance = -1, separation = radius * .28;
        for (int attempt = 0; attempt < 8; attempt++) {
            double z = random.nextDouble() * 2 - 1, angle = random.nextDouble() * Math.PI * 2;
            double radial = Math.sqrt(1 - z * z), distance = radius * (.18 + random.nextDouble() * .22);
            Vec3 offset = new Vec3(radial * Math.cos(angle), radial * Math.sin(angle), z).scale(distance);
            if (previous == null) return offset;
            double spacing = anchor.add(offset).distanceToSqr(previous);
            if (spacing > bestDistance) { best = offset; bestDistance = spacing; }
            if (spacing >= separation * separation) return offset;
        }
        return best;
    }

    private static Vec3 scatterBodyPoint(Source source, Vec3 anchor, double radius, int seed, Vec3 previous) {
        Random random = scatterRandom(seed);
        List<BlockPos> candidates = new ArrayList<>();
        // Reuse this cut's loaded block snapshot. No second plot read or bounding-box air target.
        for (var entry : source.world.entrySet())
            if (entry.getValue().distanceToSqr(anchor) <= radius * radius) candidates.add(entry.getKey());
        Vec3 best = null;
        double bestDistance = -1, separation = radius * .22;
        int sampled = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Collections.swap(candidates, i, i + random.nextInt(candidates.size() - i));
            BlockPos pos = candidates.get(i);
            List<AABB> boxes = source.level.getBlockState(pos).getCollisionShape(source.level, pos).toAabbs();
            if (boxes.isEmpty()) continue;
            AABB box = boxes.get(random.nextInt(boxes.size())).move(pos);
            Vec3 local = box.getCenter().add((random.nextDouble() - .5) * box.getXsize() * .4,
                    (random.nextDouble() - .5) * box.getYsize() * .4, (random.nextDouble() - .5) * box.getZsize() * .4);
            Vec3 point = source.pose.transformPosition(local);
            if (point.distanceToSqr(anchor) > radius * radius) continue;
            if (previous == null) return point;
            double spacing = point.distanceToSqr(previous);
            if (spacing > bestDistance) { best = point; bestDistance = spacing; }
            if (spacing >= separation * separation || ++sampled >= 12) break;
        }
        // A tiny fragment may not have two well-separated points; still hit its actual geometry.
        return best == null ? anchor : best;
    }

    public static Target acquire(LivingEntity player, double reach, double fallbackRadius) {
        ServerLevel level = ((ServerLevel) player.level());
        Vec3 eye = Sable.HELPER.projectOutOfSubLevel(level, player.getEyePosition());
        Vec3 direction = worldDirection(player, player.getLookAngle());
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(direction.scale(reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.BLOCK
                ? Sable.HELPER.projectOutOfSubLevel(level, hit.getLocation())
                : eye.add(direction.scale(Math.min(12, reach)));
        if (hit.getType() == HitResult.Type.BLOCK) {
            SubLevel containing = Sable.HELPER.getContaining(level, hit.getBlockPos());
            if (containing instanceof ServerSubLevel body && valid(body)) return describe(body);
            // A real terrain hit wins. Never pull the aim off a wall toward nearby debris.
            return new Target(null, center, Math.clamp(fallbackRadius, 1, 8), 0);
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        // Allow only a small near-miss of the crosshair ray, not the whole slash radius.
        if (container != null) {
            ServerSubLevel closest = null;
            double closestDistance = Double.POSITIVE_INFINITY;
            Vec3 end = eye.add(direction.scale(reach));
            double tolerance = .6;
            List<ServerSubLevel> candidates = new ArrayList<>();
            for (SubLevel sub : container.queryIntersecting(new BoundingBox3d(new AABB(eye, end).inflate(tolerance))))
                if (sub instanceof ServerSubLevel body && valid(body)) candidates.add(body);
            candidates.sort(Comparator.comparingDouble(body -> boundsDistance(body, eye)));
            ScanBudget scan = new ScanBudget();
            for (ServerSubLevel body : candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size()))) {
                try {
                    Source source = readBody(level, body, scan);
                    Vec3 localEye = source.pose.transformPositionInverse(eye), localEnd = source.pose.transformPositionInverse(end);
                    for (BlockPos pos : source.world.keySet()) {
                        for (AABB shape : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                            AABB box = shape.move(pos).inflate(tolerance);
                            Vec3 entry = box.contains(localEye) ? localEye : box.clip(localEye, localEnd).orElse(null);
                            if (entry != null && localEye.distanceToSqr(entry) < closestDistance) {
                                closest = body; closestDistance = localEye.distanceToSqr(entry);
                            }
                        }
                    }
                } catch (Refusal ignored) { /* An unreadable nearby body is not a cursor lock. */ }
                if (scan.scanned >= MAX_SCAN_CELLS) break;
            }
            if (closest != null) return describe(closest);
        }
        return new Target(null, center, Math.clamp(fallbackRadius, 1, 8), 0);
    }

    public static Target point(LivingEntity player, double reach, double radius) {
        return new Target(null, aim(player, reach), Math.clamp(radius, 1, 8), 0);
    }

    public static Target inspect(LivingEntity player, UUID id) {
        if (id == null) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(((ServerLevel) player.level()));
        if (container == null) return null;
        SubLevel loaded = container.getSubLevel(id);
        return loaded instanceof ServerSubLevel body && valid(body) ? describe(body) : null;
    }

    public static Result cutTarget(LivingEntity player, Target target, Vec3 planeNormal, Vec3 forward) {
        return cutTargetShape(player, target, planeNormal, forward, null, 1, null, null).result();
    }

    /**
     * Both arms share one source and world center, before either arm creates descendants. The second
     * radius is relative to the primary physical radius; no neighboring body or extra terrain is acquired.
     */
    public static Result cutTargetCross(LivingEntity player, Target target, Vec3 primaryNormal, Vec3 forward,
                                        Vec3 secondaryNormal, double secondaryRadiusScale) {
        if (!finite(secondaryNormal) || secondaryNormal.lengthSqr() < 1e-12
                || !Double.isFinite(secondaryRadiusScale) || secondaryRadiusScale <= 0 || secondaryRadiusScale > 2)
            return fail("十字斩的第二切面无效");
        return cutTargetShape(player, target, primaryNormal, forward, secondaryNormal, secondaryRadiusScale, null, null).result();
    }

    private static Strike cutTargetShape(LivingEntity player, Target target, Vec3 planeNormal, Vec3 forward,
                                         Vec3 secondaryNormal, double secondaryRadiusScale, Integer scatterSeed, Vec3 previous) {
        ServerLevel level = ((ServerLevel) player.level());
        Vec3 center = target == null ? Vec3.ZERO : target.center();
        double radius = target == null ? 1 : target.radius();
        if (!level.getServer().isSameThread() || target == null || !finite(target.center())
                || !finite(planeNormal) || planeNormal.lengthSqr() < 1e-12 || !finite(forward))
            return new Strike(fail("斩击目标无效"), center, radius);
        if (secondaryNormal != null && (!finite(secondaryNormal) || secondaryNormal.lengthSqr() < 1e-12
                || !Double.isFinite(secondaryRadiusScale) || secondaryRadiusScale <= 0 || secondaryRadiusScale > 2))
            return new Strike(fail("十字斩的第二切面无效"), center, radius);
        if (!allowed(player, level)) return new Strike(fail("当前术式破坏配置或权限不允许改变方块"), center, radius);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return new Strike(fail("当前维度没有可用的航空学物理空间"), center, radius);
        Vec3 normal = planeNormal.normalize();
        forward = forward.subtract(normal.scale(forward.dot(normal))).normalize();
        Vec3 secondNormal = secondaryNormal == null ? null : secondaryNormal.normalize();
        if (secondNormal != null && (normal.lengthSqr() < .99 || forward.lengthSqr() < .99
                || secondNormal.lengthSqr() < .99 || Math.abs(secondNormal.dot(forward)) > 1e-5
                || Math.abs(secondNormal.dot(normal)) > 1e-5))
            return new Strike(fail("十字斩的两个切面必须共用方向且互相垂直"), center, radius);
        try {
            Source source;
            if (target.id() == null) {
                center = target.center(); radius = Math.clamp(target.radius(), 1, 8);
                source = readWorld(level, center, radius, new ScanBudget());
            } else {
                SubLevel loaded = container.getSubLevel(target.id());
                if (!(loaded instanceof ServerSubLevel body) || !valid(body))
                    return new Strike(fail("选中的物理结构已移除或离开加载范围"), center, radius);
                Target current = describe(body);
                center = current.center(); radius = current.radius();
                source = readBody(level, body, new ScanBudget());
                if (scatterSeed != null) center = scatterBodyPoint(source, center, radius, scatterSeed, previous);
            }
            CrossPlane secondary = secondNormal == null ? null : new CrossPlane(secondNormal, radius * secondaryRadiusScale);
            source.contact = contactDistance(source, center, normal, radius);
            if (secondary != null)
                source.contact = Math.min(source.contact, contactDistance(source, center, secondary.normal, secondary.radius));
            if (!Double.isFinite(source.contact)) return new Strike(source.body == null ? fail("斩痕未接触到可切割的方块")
                    : new Result(secondary == null, 0, 0, secondary == null ? "" : "十字斩未接触到目标的碰撞面",
                            List.of(source.body.getUniqueId())), center, radius);
            return new Strike(process(player, level, container, source, center, normal, forward, radius, new Budget(), secondary), center, radius);
        } catch (Refusal refusal) { return new Strike(fail(refusal.getMessage()), center, radius); }
        catch (StructureSever.BudgetExceededException budget) { return new Strike(fail("目标的分片数量超过单刀处理预算"), center, radius); }
        catch (RuntimeException problem) {
            LOG.error("Could not cut locked Sable target {}", target.id(), problem);
            return new Strike(fail("此次切割未完成，详细原因已记录到日志"), center, radius);
        }
    }

    /** Cinematic grab: only a small, already-independent loaded body, never somebody's whole ship. */
    public static Target projectileCandidate(LivingEntity owner, Vec3 around) {
        ServerLevel level=(ServerLevel)owner.level();
        if(!allowed(owner,level))return null;
        ServerSubLevelContainer container=SubLevelContainer.getContainer(level);
        if(container==null)return null;
        int inspected=0;
        for(SubLevel sub:container.queryIntersecting(new BoundingBox3d(new AABB(around,around).inflate(22)))) {
            if(!(sub instanceof ServerSubLevel body)||!valid(body))continue;
            if(Math.abs(body.getMassTracker().getMass()-body.getSelfMassTracker().getMass())>1e-6)continue;
            var box=body.boundingBox();double x=box.maxX()-box.minX(),y=box.maxY()-box.minY(),z=box.maxZ()-box.minZ();
            if(x>10||y>10||z>10||x*y*z>1200)continue;
            Target candidate=describe(body);
            if(!dev.jujutsuvfx.core.StructureGrabSafety.bodyAllowed(owner.getX(),owner.getY(),owner.getZ(),
                    candidate.center.x,candidate.center.y,candidate.center.z,candidate.radius))continue;
            if(++inspected>4)break;
            try {
                Source source=readBody(level,body,new ScanBudget(4096));
                if(source.world.size()<8||source.world.size()>256||hasRunningContraption(level,source))continue;
                boolean simple=true;
                for(var cell:source.world.entrySet()) {
                    BlockState state=level.getBlockState(cell.getKey());
                    if(state.hasBlockEntity()||SableDestructiveBlocks.fragile(state)){simple=false;break;}
                    preflight(owner,level,cell.getKey(),cell.getValue(),state);
                }
                if(simple){Target t=describe(body);return new Target(t.id,t.center,t.radius,source.world.size());}
            } catch(Refusal ignored) { /* A protected or busy body does not stop this scene. */ }
        }
        return null;
    }

    /** One bounded, reversible assembly; the same transfer/attachment verification used by Cleave. */
    public static Result assembleProjectile(LivingEntity owner,List<BlockPos> selected) {
        return assembleProjectile(owner,selected,4);
    }
    private static Result assembleProjectile(LivingEntity owner,List<BlockPos> selected,int retries) {
        ServerLevel level=(ServerLevel)owner.level();
        if(!allowed(owner,level)||selected.isEmpty()||selected.size()>256)return fail("投掷结构预算或配置不允许");
        ServerSubLevelContainer container=SubLevelContainer.getContainer(level);
        if(container==null||container.getLoadedCount()>=MAX_LOADED_BODIES)return fail("物理空间没有空余预算");
        Source source=new Source(level,null);Map<BlockPos,Snapshot> snapshots=new LinkedHashMap<>();
        try {
            for(BlockPos input:selected) {
                BlockPos pos=input.immutable();
                if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null||Sable.HELPER.getContaining(level,pos)!=null)
                    continue;
                BlockState block=level.getBlockState(pos);
                // Containers and machinery are left out of a staged throw; no lossy block-entity copy.
                if(block.hasBlockEntity()||SableDestructiveBlocks.fragile(block)||!permitsCell(owner,level,pos,pos.getCenter(),block))continue;
                double mass=PhysicsBlockPropertyHelper.getMass(level,pos,block);if(!Double.isFinite(mass)||mass<0)continue;
                source.world.put(pos,pos.getCenter());snapshots.put(pos,new Snapshot(block,null));
            }
            if(hasRunningContraption(level,source))return fail("选区包含运行中的机械");
            if(source.world.isEmpty())return fail("选区没有可移动方块");
            validateMass(level,source.world.keySet());
        }catch(Refusal refused){return fail(refused.getMessage());}
        Transfer transfer=new Transfer(source,source.world.keySet(),0);
        SableHangingAttachments attachments=SableHangingAttachments.capture(level,source.world.keySet());
        Map<UUID,TrackingPoint> tracking=snapshotTracking(level,List.of(transfer));List<Moved> moved=new ArrayList<>();
        try {
            attachments.prepare(List.of(transfer.blocks));
            if(!allowed(owner,level))throw refusal("组装前配置已改变");
            move(level,container,transfer,snapshots,moved);correctTracking(level,moved,tracking);
            for(Moved part:moved)if(part.body.isRemoved())throw refusal("组装后的结构已移除");
            attachments.finish(moved.stream().map(Moved::offset).toList());
            return new Result(true,snapshots.size(),moved.size(),"",moved.stream().map(m->m.body.getUniqueId()).toList());
        }catch(RuntimeException problem){
            LOG.warn("Building throw assembly failed; restoring only its selected blocks",problem);
            boolean restored=rollback(level,container,List.of(source),moved,snapshots,tracking,attachments);
            // A verified failed voxel stays where it was. Retry only after the original
            // transaction has been restored, never duplicate a partly assembled structure.
            if(restored && retries>0 && problem instanceof MoveFailure failed && snapshots.containsKey(failed.pos)
                    && matchesSnapshot(level,failed.pos,snapshots.get(failed.pos)) && allowed(owner,level)) {
                List<BlockPos> remaining=new ArrayList<>(snapshots.keySet());remaining.remove(failed.pos);
                if(!remaining.isEmpty())return assembleProjectile(owner,remaining,retries-1);
            }
            return fail(restored?"投掷结构已恢复":"投掷结构回滚异常已写入日志");
        }
    }

    /** Force-driven motion only. Keep the physics engine authoritative; never teleport a grabbed building. */
    public static boolean projectileVelocity(LivingEntity owner,UUID id,Vec3 velocity,Vec3 spin) {
        ServerLevel level=(ServerLevel)owner.level();
        if(!allowed(owner,level)||!finite(velocity)||!finite(spin))return false;
        ServerSubLevelContainer container=SubLevelContainer.getContainer(level);
        SubLevel sub=container==null?null:container.getSubLevel(id);
        if(!(sub instanceof ServerSubLevel body)||!valid(body))return false;
        var box=body.boundingBox();
        if(box.maxX()-box.minX()>12||box.maxY()-box.minY()>12||box.maxZ()-box.minZ()>12
                ||Math.abs(body.getMassTracker().getMass()-body.getSelfMassTracker().getMass())>1e-6)return false;
        var handle=RigidBodyHandle.of(body);Vector3d current=handle.getLinearVelocity(new Vector3d());
        Vec3 desired=velocity.length()>28?velocity.normalize().scale(28):velocity;
        Vector3d delta=new Vector3d(desired.x,desired.y,desired.z).sub(current);
        if(delta.length()>12)delta.mul(12/delta.length());
        handle.applyLinearImpulse(delta.mul(body.getMassTracker().getMass()));
        Vector3d angular=handle.getAngularVelocity(new Vector3d());
        handle.addLinearAndAngularVelocity(new Vector3d(),new Vector3d(spin.x,spin.y,spin.z).sub(angular));
        return true;
    }

    /** A single chosen projectile, with a much smaller split budget than a player's unrestricted Cleave. */
    public static Result cutProjectile(LivingEntity owner,UUID id,Vec3 normal,Vec3 forward) {
        ServerLevel level=(ServerLevel)owner.level();
        if(!allowed(owner,level))return fail("当前配置禁止物理切割");
        ServerSubLevelContainer container=SubLevelContainer.getContainer(level);
        SubLevel sub=container==null?null:container.getSubLevel(id);
        if(!(sub instanceof ServerSubLevel body)||!valid(body))return fail("投掷物已移除");
        try {
            Source source=readBody(level,body,new ScanBudget(4096));
            if(source.world.size()>256)return fail("投掷物已合并为更大的结构，停止操作");
            Target target=describe(body);normal=normal.normalize();forward=forward.subtract(normal.scale(forward.dot(normal))).normalize();
            if(normal.lengthSqr()<.99||forward.lengthSqr()<.99)return fail("斩面无效");
            source.contact=contactDistance(source,target.center,normal,target.radius);
            Budget budget=new Budget();budget.fragments=MAX_FRAGMENTS-8;budget.moved=MAX_MOVED_BLOCKS-256;
            return process(owner,level,container,source,target.center,normal,forward,target.radius,budget);
        }catch(Refusal|StructureSever.BudgetExceededException refused){return fail("投掷物不再满足此次分割预算");}
    }

    private static boolean valid(ServerSubLevel body) {
        double mass = body.getMassTracker().getMass();
        return !body.isRemoved() && !body.getSelfMassTracker().isInvalid() && !body.getMassTracker().isInvalid()
                && Double.isFinite(mass) && mass > 0 && RigidBodyHandle.of(body).isValid();
    }
    private static Target describe(ServerSubLevel body) {
        var box = body.boundingBox();
        Vec3 center = new Vec3((box.minX() + box.maxX()) * .5, (box.minY() + box.maxY()) * .5,
                (box.minZ() + box.maxZ()) * .5);
        double diagonal = Math.sqrt(Math.pow(box.maxX() - box.minX(), 2)
                + Math.pow(box.maxY() - box.minY(), 2) + Math.pow(box.maxZ() - box.minZ(), 2));
        int estimatedBlocks = (int) Math.clamp(Math.ceil(body.getMassTracker().getMass()), 1, Integer.MAX_VALUE);
        return new Target(body.getUniqueId(), center, Math.clamp(diagonal * .5 + .9, 1.5, 64), estimatedBlocks);
    }

    /** Sable deliberately returns a plot-space BlockHitResult for hits on moving structures. */
    public static Vec3 aim(LivingEntity player, double reach) {
        ServerLevel level = ((ServerLevel) player.level());
        Vec3 eye = Sable.HELPER.projectOutOfSubLevel(level, player.getEyePosition());
        Vec3 direction = worldDirection(player, player.getLookAngle());
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(direction.scale(reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK
                ? Sable.HELPER.projectOutOfSubLevel(level, hit.getLocation())
                : eye.add(direction.scale(Math.min(12, reach)));
    }

    public static Vec3 worldDirection(LivingEntity player, Vec3 direction) {
        SubLevel containing = Sable.HELPER.getContaining(player);
        return (containing == null ? direction : containing.logicalPose().transformNormal(direction)).normalize();
    }

    /** Each contacted source has its own transaction; unrelated or unsupported bodies cannot veto a cut. */
    public static Result cut(LivingEntity player, Vec3 center, Vec3 planeNormal, Vec3 forward, double radius) {
        return cutArea(player, center, planeNormal, forward, radius, null, 1);
    }

    public static Result cutCross(LivingEntity player, Vec3 center, Vec3 planeNormal, Vec3 forward, double radius,
                                  Vec3 secondaryNormal, double secondaryRadiusScale) {
        if (!finite(secondaryNormal) || secondaryNormal.lengthSqr() < 1e-12
                || !Double.isFinite(secondaryRadiusScale) || secondaryRadiusScale <= 0 || secondaryRadiusScale > 2)
            return fail("十字斩的第二切面无效");
        return cutArea(player, center, planeNormal, forward, radius, secondaryNormal, secondaryRadiusScale);
    }

    private static Result cutArea(LivingEntity player, Vec3 center, Vec3 planeNormal, Vec3 forward, double radius,
                                  Vec3 secondaryNormal, double secondaryRadiusScale) {
        ServerLevel level = ((ServerLevel) player.level());
        if (!level.getServer().isSameThread()) return fail("结构切割必须在服务端执行");
        if (!finite(center) || !finite(planeNormal) || !finite(forward) || planeNormal.lengthSqr() < 1e-12
                || !Double.isFinite(radius) || radius <= 0 || radius > 8) return fail("斩击范围无效");
        if (!allowed(player, level)) return fail("当前术式破坏配置或权限不允许改变方块");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return fail("当前维度没有可用的航空学物理空间");
        Vec3 normal = planeNormal.normalize();
        forward = forward.subtract(normal.scale(forward.dot(normal))).normalize();
        CrossPlane secondary = secondaryNormal == null ? null : new CrossPlane(secondaryNormal.normalize(), radius * secondaryRadiusScale);
        if (forward.lengthSqr() < .99 || secondary != null && (Math.abs(secondary.normal.dot(normal)) > 1e-5
                || Math.abs(secondary.normal.dot(forward)) > 1e-5)) return fail("斩击切面方向无效");
        Budget budget = new Budget();
        ScanBudget scan = new ScanBudget();
        List<Source> contacts = new ArrayList<>();
        String reason = "斩痕未接触到可切割的方块";
        // Broad phase only gathers candidates. It must never count nearby debris as successful targets.
        List<ServerSubLevel> nearby = new ArrayList<>();
        AABB selection = new AABB(center, center).inflate(Math.max(radius, secondary == null ? 0 : secondary.radius) + .87);
        for (SubLevel sub : container.queryIntersecting(new BoundingBox3d(selection)))
            if (sub instanceof ServerSubLevel body && !body.isRemoved() && (crossesBounds(body, center, normal)
                    || secondary != null && crossesBounds(body, center, secondary.normal))) nearby.add(body);
        nearby.sort(Comparator.comparingDouble((ServerSubLevel body) -> boundsDistance(body, center))
                .thenComparing(body -> body.getUniqueId()));
        for (int i = 0; i < Math.min(nearby.size(), MAX_CANDIDATES); i++) {
            if (scan.scanned >= MAX_SCAN_CELLS) break;
            try {
                Source source = readBody(level, nearby.get(i), scan);
                source.contact = contactDistance(source, center, normal, radius);
                if (secondary != null) source.contact = Math.min(source.contact, contactDistance(source, center, secondary.normal, secondary.radius));
                if (!Double.isFinite(source.contact)) continue;
                contacts.add(source);
            } catch (Refusal ignored) { reason = ignored.getMessage(); }
            catch (RuntimeException invalidSource) {
                LOG.warn("Skipping unreadable slash candidate {}", nearby.get(i).getUniqueId(), invalidSource);
                reason = "部分结构暂时无法读取，其他命中结构仍会处理";
            }
        }
        // The aimed terrain and nearby bodies share one contact list. Debris never suppresses
        // the terrain pass. Both use this strike's resolved position, not the burst's anchor.
        try {
            Source terrain = readWorld(level, center, Math.max(radius, secondary == null ? 0 : secondary.radius), new ScanBudget());
            terrain.contact = contactDistance(terrain, center, normal, radius);
            if (secondary != null) terrain.contact = Math.min(terrain.contact, contactDistance(terrain, center, secondary.normal, secondary.radius));
            if (Double.isFinite(terrain.contact)) contacts.add(terrain);
        } catch (Refusal skipped) { reason = skipped.getMessage(); }
        catch (RuntimeException problem) { LOG.warn("Could not read aimed terrain for slash", problem); }
        contacts.sort(Comparator.comparingDouble((Source source) -> source.contact)
                .thenComparing(source -> source.body == null ? new UUID(0, 0) : source.body.getUniqueId()));
        for (Source source : contacts) {
            if (budget.hitBodies >= MAX_HIT_BODIES || !allowed(player, level)) break;
            try {
                Result result = process(player, level, container, source, center, normal, forward, radius, budget, secondary);
                if (!result.success()) reason = result.message();
            } catch (Refusal skipped) { reason = skipped.getMessage(); }
            catch (StructureSever.BudgetExceededException skipped) { reason = "此结构超过分组预算，其他命中结构仍会处理"; }
            catch (RuntimeException problem) {
                LOG.error("Could not prepare slash source {}", source.body == null ? "terrain" : source.body.getUniqueId(), problem);
                reason = "此结构暂时无法切割，其他命中结构仍会处理";
            }
        }
        if (budget.hitBodies > 0) return new Result(true, budget.moved, budget.fragments, "", List.copyOf(budget.bodies));
        // Budget and unsupported-source skips are not global errors when another target was handled.
        return fail(reason);
    }

    private static Result process(LivingEntity player, ServerLevel level, ServerSubLevelContainer container,
                                  Source source, Vec3 center, Vec3 normal, Vec3 forward, double radius, Budget budget) {
        return process(player, level, container, source, center, normal, forward, radius, budget, null);
    }

    private static Result process(LivingEntity player, ServerLevel level, ServerSubLevelContainer container,
                                  Source source, Vec3 center, Vec3 normal, Vec3 forward, double radius, Budget budget,
                                  CrossPlane secondary) {
        return process(player, level, container, source, center, normal, forward, radius, budget, secondary, 2);
    }

    private static Result process(LivingEntity player, ServerLevel level, ServerSubLevelContainer container,
                                  Source source, Vec3 center, Vec3 normal, Vec3 forward, double radius, Budget budget,
                                  CrossPlane secondary, int retries) {
        // An active entity-owned machine cannot be block-transferred. It can still be cut in
        // place; its native block-removal callbacks handle the controller and its connections.
        source.destructiveOnly = hasRunningContraption(level, source);
        markFixed(player,level,source);
        SableHangingAttachments attachments = SableHangingAttachments.capture(level, source.world.keySet());
        try {
            source = openKerf(player, level, container, source, center, normal, radius, budget, secondary);
        } finally {
            // Attachments whose support this cut removed disappear without item drops.
            // Their committed removal is separate from the reversible block transfer.
            attachments.dropUnsupportedAfterCut();
        }
        if (source.world.isEmpty() && source.kerfed || source.destructiveOnly && source.kerfed) {
            List<UUID> bodies = source.body != null && valid(source.body) ? List.of(source.body.getUniqueId()) : List.of();
            if (!bodies.isEmpty() && source.fixed.isEmpty()) kick(source.body, 0, center, normal, forward, source.world.size());
            budget.hitBodies++;
            budget.bodies.addAll(bodies);
            return new Result(true, 0, 0, "", bodies);
        }
        List<Transfer> transfers = new ArrayList<>();
        if (!source.impulseOnly && !source.destructiveOnly) addPlan(source, center, normal, radius, transfers);
        if (transfers.isEmpty()) {
            if (source.body != null) {
                // A whole-block shard already at minimum resolution still receives the blow.
                if(!source.kerfed)preflightContact(player, level, source);
                if(source.fixed.isEmpty())kick(source.body, source.retainedSide, center, normal, forward, source.world.size());
                budget.hitBodies++;
                budget.bodies.add(source.body.getUniqueId());
                return new Result(true, 0, 0, "", List.of(source.body.getUniqueId()));
            }
            if(source.kerfed){budget.hitBodies++;return new Result(true,0,0,"",List.of());}
            return fail("斩痕没有接触到有碰撞体积的方块");
        }
        int count = transfers.stream().mapToInt(t -> t.blocks.size()).sum();
        if (count + budget.moved > MAX_MOVED_BLOCKS || transfers.size() + budget.fragments > MAX_FRAGMENTS
                || container.getLoadedCount() + transfers.size() > MAX_LOADED_BODIES) {
            // Continue against smaller/later targets. Never truncate a component or drop its blocks.
            if (source.body != null) {
                if(!source.kerfed)preflightContact(player, level, source);
                if(source.fixed.isEmpty())kick(source.body, source.retainedSide, center, normal, forward, source.world.size());
                budget.hitBodies++;
                budget.bodies.add(source.body.getUniqueId());
                return new Result(true, 0, 0, "", List.of(source.body.getUniqueId()));
            }
            return fail("本次新碎片预算已用完，现有结构仍可受到斩击冲量");
        }
        Map<BlockPos, Snapshot> snapshots = new LinkedHashMap<>();
        for (Transfer transfer : transfers) {
            validateMass(level, transfer.blocks);
            for (BlockPos pos : transfer.blocks) {
                if (snapshots.containsKey(pos)) throw new IllegalStateException("Duplicate source block in cut");
                BlockState state = level.getBlockState(pos);
                preflight(player, level, pos, source.world.get(pos), state);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                snapshots.put(pos, new Snapshot(state,
                        blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess()).copy()));
            }
        }
        if (!allowed(player, level)) throw refusal("术式破坏配置或所在维度已改变，本次斩击取消");
        for (Map.Entry<BlockPos, Snapshot> entry : snapshots.entrySet())
            if (!matchesSnapshot(level, entry.getKey(), entry.getValue()))
                throw refusal("检查期间方块或容器内容发生变化，跳过此结构");
        attachments.prepare(transfers.stream().map(Transfer::blocks).toList());
        Map<UUID, TrackingPoint> trackingBefore = snapshotTracking(level, transfers);
        List<Moved> moved = new ArrayList<>();
        try {
            for (Transfer transfer : transfers) {
                if (!allowed(player, level)) throw refusal("术式破坏配置或所在维度已改变，本次斩击取消");
                move(level, container, transfer, snapshots, moved);
            }
            correctTracking(level, moved, trackingBefore);
            for (Moved part : moved) if (part.body.isRemoved())
                throw new IllegalStateException("New fragment was removed during assembly");
            // Sable moves a painting by its visible center. Re-anchor from the original
            // integer attachment position, which is different for even-sized paintings.
            attachments.finish(moved.stream().map(Moved::offset).toList());
        } catch (RuntimeException problem) {
            LOG.error("Sable source assembly failed; restoring this source only", problem);
            boolean restored = false;
            try { restored = rollback(level, container, List.of(source), moved, snapshots, trackingBefore, attachments); }
            catch (RuntimeException recovery) { LOG.error("Block recovery failed after cut", recovery); }
            if (restored && problem instanceof MoveFailure failed && snapshots.containsKey(failed.pos)
                    && matchesSnapshot(level, failed.pos, snapshots.get(failed.pos)) && allowed(player, level)) {
                // The user chose destruction over preservation of incompatible blocks. First
                // restore the transaction, then destroy the identified original, never its copy.
                BlockState state = level.getBlockState(failed.pos);
                preflight(player, level, failed.pos, source.world.get(failed.pos), state);
                if (matchesSnapshot(level, failed.pos, snapshots.get(failed.pos))
                        && SkillBlockDrops.destroyWithoutDrops(level, failed.pos, player)) {
                    if (source.body != null && !valid(source.body)) {
                        budget.hitBodies++;
                        return new Result(true, 0, 0, "", List.of());
                    }
                    Source remaining = source.body == null ? readWorld(level, center, radius, new ScanBudget())
                            : readBody(level, source.body, new ScanBudget());
                    remaining.kerfed = true;
                    contactDistance(remaining, center, normal, radius);
                    if (secondary != null) contactDistance(remaining, center, secondary.normal, secondary.radius);
                    if (retries > 0 && !remaining.world.isEmpty())
                        return process(player, level, container, remaining, center, normal, forward, radius, budget, secondary, retries - 1);
                    List<UUID> bodies = remaining.body == null ? List.of() : List.of(remaining.body.getUniqueId());
                    if (remaining.body != null) kick(remaining.body, 0, center, normal, forward, remaining.world.size());
                    budget.hitBodies++; budget.bodies.addAll(bodies);
                    return new Result(true, 0, 0, "", bodies);
                }
            }
            return fail(restored ? source.kerfed ? "斩痕已打开，未成功分离的方块已恢复到开槽后的结构"
                    : "此结构组装失败并已恢复，其他命中结构继续切割" : "此结构组装异常，详情已记录到日志");
        }
        // Physics feedback is outside the transfer transaction; a queued impulse cannot undo good cuts.
        List<UUID> surviving = new ArrayList<>();
        for (Moved part : moved) {
            kick(part.body, part.transfer.side, center, normal, forward, part.transfer.blocks.size());
            if (valid(part.body)) surviving.add(part.body.getUniqueId());
        }
        if (source.body != null) {
            if(source.fixed.isEmpty())kick(source.body, source.retainedSide, center, normal, forward, source.retainedBlocks);
            if (valid(source.body)) surviving.add(source.body.getUniqueId());
        }
        budget.moved += snapshots.size();
        budget.fragments += moved.size();
        budget.hitBodies++;
        budget.bodies.addAll(surviving);
        return new Result(true, snapshots.size(), moved.size(), "", surviving);
    }

    /** Plan the clearance first. Destruction is an intentional world edit, not a reversible transfer. */
    private static Source openKerf(LivingEntity player, ServerLevel level, ServerSubLevelContainer container,
                                   Source source, Vec3 center, Vec3 normal, double radius, Budget budget,
                                   CrossPlane secondary) {
        if (source.world.isEmpty()) return source;
        Vec3 localCenter = source.pose == null ? center : source.pose.transformPositionInverse(center);
        Vec3 localNormal = source.pose == null ? normal : source.pose.transformNormalInverse(normal);
        Vec3 secondLocalNormal = secondary == null ? null : source.pose == null ? secondary.normal
                : source.pose.transformNormalInverse(secondary.normal);
        Set<BlockPos> kerf = new LinkedHashSet<>();
        double retainedMass = 0;
        for (BlockPos pos : source.world.keySet()) {
            // Whole voxel intersections make a genuine gap even across Sable's diagonal heat-map links.
            AABB cell = new AABB(pos).inflate(.04);
            boolean hit = CutContact.distanceSquared(cell, localCenter, localNormal) <= radius * radius + 1e-7;
            if (!hit && secondary != null)
                hit = CutContact.distanceSquared(cell, localCenter, secondLocalNormal)
                        <= secondary.radius * secondary.radius + 1e-7;
            // Union before budgets, protection checks, destruction and grouping. Calling the
            // single-plane entry point twice would recenter arm two on an already detached half.
            if (hit && !source.fixed.contains(pos)) kerf.add(pos);
            else retainedMass += positiveMass(level,pos);
        }
        if (kerf.isEmpty()) return source;
        if (kerf.size() > MAX_KERF_BLOCKS) {
            if (secondary != null) throw refusal("十字斩需要移除的刀痕方块超过单刀预算");
            source.impulseOnly = source.body != null;
            return source;
        }
        List<Transfer> planned = new ArrayList<>();
        Set<BlockPos> destructiveConnections = new LinkedHashSet<>();
        for (BlockPos pos : kerf) if (SableDestructiveBlocks.fragile(level.getBlockState(pos))) destructiveConnections.add(pos);
        if (!source.destructiveOnly) {
            // Removing a connector can change the retained component. Re-plan before moving
            // anything, turning affected connectors into real cuts rather than stale NBT links.
            for (int pass = 0; pass < 8; pass++) {
                Source preview = new Source(level, source.body);
                preview.kerfed = true;preview.fixed.addAll(source.fixed);
                for (var entry : source.world.entrySet()) if (!kerf.contains(entry.getKey()))
                    preview.world.put(entry.getKey(), entry.getValue());
                planned.clear();
                if (!preview.world.isEmpty()) addPlan(preview, center, normal, radius, planned);
                boolean expanded = false;
                for (Transfer transfer : planned) for (BlockPos pos : transfer.blocks)
                    if (SableDestructiveBlocks.fragile(level.getBlockState(pos)) && kerf.add(pos)) {
                        destructiveConnections.add(pos); expanded = true;
                    }
                if (!expanded) break;
                if (pass == 7) { source.destructiveOnly = true; planned.clear(); }
            }
        }
        // Springs and swivel bearings have paired blocks. Destroy checked, reciprocal ends
        // explicitly so native callbacks cannot destroy stale or unchecked saved coordinates.
        Set<BlockPos> paired = SableDestructiveBlocks.pairedEnds(level, destructiveConnections);
        // A native paired-connector callback must not remove an endpoint rejected by protection.
        for(BlockPos pos:paired)if(!permitsCell(player,level,pos,Sable.HELPER.projectOutOfSubLevel(level,pos.getCenter()),level.getBlockState(pos)))source.fixed.add(pos);
        if(paired.stream().anyMatch(source.fixed::contains)){
            kerf.removeAll(destructiveConnections);destructiveConnections.clear();paired=Set.of();
        }
        destructiveConnections.addAll(paired);kerf.addAll(paired);kerf.removeAll(source.fixed);
        // If a paired end belongs to another planned fragment, the old plan is now stale.
        planned.clear();
        Source preview = new Source(level, source.body);
        preview.kerfed = true;preview.fixed.addAll(source.fixed);
        retainedMass = 0;
        for (var entry : source.world.entrySet()) if (!kerf.contains(entry.getKey())) {
            preview.world.put(entry.getKey(), entry.getValue());
            retainedMass += positiveMass(level,entry.getKey());
        }
        if (!source.destructiveOnly && !preview.world.isEmpty()) addPlan(preview, center, normal, radius, planned);
        for (Transfer transfer : planned) for (BlockPos pos : transfer.blocks)
            if (SableDestructiveBlocks.fragile(level.getBlockState(pos))) source.destructiveOnly = true;
        if (source.destructiveOnly) planned.clear();
        if (kerf.size() > MAX_KERF_BLOCKS) {
            source.impulseOnly = source.body != null;
            return source;
        }
        if (source.body != null && source.fixed.isEmpty() && (retainedMass <= 1e-8 || !Double.isFinite(retainedMass)) && destructiveConnections.isEmpty() && !source.destructiveOnly) {
            // Ordinary single-block shards remain physical. Problem machinery may be destroyed completely.
            source.impulseOnly = source.body != null;
            return source;
        }
        int moved = planned.stream().mapToInt(part -> part.blocks.size()).sum();
        if (moved + budget.moved > MAX_MOVED_BLOCKS || planned.size() + budget.fragments > MAX_FRAGMENTS
                || container.getLoadedCount() + planned.size() > MAX_LOADED_BODIES) {
            if (secondary != null) throw refusal("十字斩产生的物理碎片超过单刀预算");
            source.impulseOnly = source.body != null;
            return source;
        }
        // Validate every block that may be removed or transferred before making the first hole.
        Set<BlockPos> changing = new LinkedHashSet<>(kerf);
        for (Transfer transfer : planned) changing.addAll(transfer.blocks);
        Map<BlockPos, Snapshot> checked = new LinkedHashMap<>();
        for (BlockPos pos : changing) {
            BlockState state = level.getBlockState(pos);
            preflight(player, level, pos, source.world.getOrDefault(pos, Sable.HELPER.projectOutOfSubLevel(level, pos.getCenter())), state);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            checked.put(pos, new Snapshot(state, blockEntity == null ? null
                    : blockEntity.saveWithFullMetadata(level.registryAccess()).copy()));
        }
        if (!allowed(player, level)) throw refusal("术式破坏配置或所在维度已改变，本次斩击取消");
        for (var entry : checked.entrySet()) if (!matchesSnapshot(level, entry.getKey(), entry.getValue()))
            throw refusal("检查期间方块或容器内容发生变化，本次斩击取消");
        boolean removed = false;
        try (var ignored = SkillBlockDrops.suppress(level)) {
            for (BlockPos pos : destructiveConnections) SableDestructiveBlocks.beforeDestroy(level, pos);
            for (BlockPos pos : kerf) {
                if (!allowed(player, level)) throw refusal("术式破坏配置或所在维度已改变，停止后续切割");
                if (source.body != null && !valid(source.body) && !destructiveConnections.contains(pos)) continue;
                BlockState current = level.getBlockState(pos);
                if (current.isAir() || current.getBlock() instanceof LiquidBlock) continue;
                if (!compatibleState(checked.get(pos).state, current)) continue;
                if (source.body != null && source.world.containsKey(pos) && !destructiveConnections.contains(pos) && !source.destructiveOnly) {
                    double blockMass = PhysicsBlockPropertyHelper.getMass(level, pos, current);
                    if (source.body.getSelfMassTracker().getMass() - blockMass <= 1e-8) continue;
                }
                // Skip the direct loot calculation; the scoped hooks also suppress container
                // contents and unsupported decorations broken by synchronous neighbor updates.
                removed |= source.world.containsKey(pos) ? level.destroyBlock(pos, false, player)
                        : SkillBlockDrops.destroyWithoutDrops(level, pos, player);
            }
        }
        if (!removed) return source;
        Source current = source.body == null ? readWorld(level, center, Math.max(radius, secondary == null ? 0 : secondary.radius), new ScanBudget())
                : valid(source.body) ? readBody(level, source.body, new ScanBudget()) : new Source(level, null);
        current.kerfed = true;
        current.destructiveOnly = source.destructiveOnly;
        markFixed(player,level,current);
        // The empty slit itself no longer contains contacts; its freshly cut bordering blocks do.
        for (BlockPos pos : current.world.keySet()) {
            for (Direction side : Direction.values()) if (kerf.contains(pos.relative(side))) {
                current.contacted.add(pos); break;
            }
        }
        return current;
    }

    private static boolean crossesBounds(ServerSubLevel body, Vec3 center, Vec3 normal) {
        var b = body.boundingBox();
        Vec3 mid = new Vec3((b.minX() + b.maxX()) * .5, (b.minY() + b.maxY()) * .5, (b.minZ() + b.maxZ()) * .5);
        double extent = (Math.abs(normal.x) * (b.maxX() - b.minX())
                + Math.abs(normal.y) * (b.maxY() - b.minY()) + Math.abs(normal.z) * (b.maxZ() - b.minZ())) * .5;
        return Math.abs(mid.subtract(center).dot(normal)) <= extent + .001;
    }
    private static double boundsDistance(ServerSubLevel body, Vec3 point) {
        var b = body.boundingBox();
        double x = point.x - Math.clamp(point.x, b.minX(), b.maxX());
        double y = point.y - Math.clamp(point.y, b.minY(), b.maxY());
        double z = point.z - Math.clamp(point.z, b.minZ(), b.maxZ());
        return x * x + y * y + z * z;
    }
    private static double contactDistance(Source source, Vec3 center, Vec3 normal, double radius) {
        Vec3 localNormal = source.pose == null ? normal : source.pose.transformNormalInverse(normal);
        Vec3 localCenter = source.pose == null ? center : source.pose.transformPositionInverse(center);
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos pos : source.world.keySet()) {
            BlockState state = source.level.getBlockState(pos);
            for (AABB box : state.getCollisionShape(source.level, pos).toAabbs()) {
                double distance = CutContact.distanceSquared(box.move(pos), localCenter, localNormal);
                if (distance > radius * radius + 1e-7) continue;
                source.contacted.add(pos);
                best = Math.min(best, distance);
            }
        }
        return best;
    }
    private static void preflightContact(LivingEntity player, ServerLevel level, Source source) {
        if (source.contacted.isEmpty()) throw refusal("斩痕未接触到结构的碰撞面");
        boolean permitted=false;
        for (BlockPos pos : source.contacted)if(!source.fixed.contains(pos))
            permitted|=permitsCell(player,level,pos,source.world.get(pos),level.getBlockState(pos));
        if(!permitted)throw refusal("斩痕只接触到不可破坏的方块");
        if (!allowed(player, level)) throw refusal("术式破坏配置或所在维度已改变，本次斩击取消");
    }
    private static void kick(ServerSubLevel body, int side, Vec3 center, Vec3 normal, Vec3 forward, int blocks) {
        if (body.isRemoved()) return;
        try {
            var handle = RigidBodyHandle.of(body);
            double mass = body.getMassTracker().getMass();
            if (!handle.isValid() || !Double.isFinite(mass) || mass <= 0) return;
            if (side == 0) {
                Vector3d pos = body.logicalPose().position();
                side = new Vec3(pos.x, pos.y, pos.z).subtract(center).dot(normal) >= 0 ? 1 : -1;
            }
            double speed = Math.clamp(30 / Math.cbrt(Math.max(1, blocks)), 7.5, MAX_ADDED_SPEED);
            Vec3 delta = normal.scale(side * speed).add(forward.scale(5.0)).add(0, 2.6, 0);
            Vector3d current = handle.getLinearVelocity(new Vector3d());
            Vector3d target = new Vector3d(current).add(delta.x, delta.y, delta.z);
            double cap = Math.max(36, current.length());
            if (target.length() > cap) target.mul(cap / target.length());
            // J = m * delta-v: use a real engine impulse and leave inherited motion intact.
            handle.applyLinearImpulse(target.sub(current).mul(mass));
            Vec3 axis = forward.cross(normal).normalize().scale(side * Math.min(1.6, speed * .12));
            Vector3d angular = handle.getAngularVelocity(new Vector3d());
            Vector3d spin = new Vector3d(angular).add(axis.x, axis.y, axis.z);
            double angularCap = Math.max(3.5, angular.length());
            if (spin.length() > angularCap) spin.mul(angularCap / spin.length());
            handle.addLinearAndAngularVelocity(new Vector3d(), spin.sub(angular));
        } catch (RuntimeException failure) { LOG.warn("Could not apply slash momentum to {}", body.getUniqueId(), failure); }
    }
    private static final class Budget { int moved, fragments, hitBodies; final Set<UUID> bodies = new LinkedHashSet<>(); }
    private record CrossPlane(Vec3 normal, double radius) {}

    private static Source readBody(ServerLevel level, ServerSubLevel body, ScanBudget scan) {
        if (body.getMassTracker().isInvalid() || !RigidBodyHandle.of(body).isValid())
            throw refusal("目标结构正在加载或已经失效，请稍后再试");
        Source source = new Source(level, body);
        for (PlotChunkHolder holder : List.copyOf(body.getPlot().getLoadedChunks())) {
            LevelChunk chunk = holder.getChunk();
            BoundingBox3ic bounds = holder.getBoundingBox();
            if (bounds == null || bounds == BoundingBox3i.EMPTY) continue;
            int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
            LevelChunkSection[] sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) continue;
                int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                int minY = Math.max(0, bounds.minY() - sectionY), maxY = Math.min(15, bounds.maxY() - sectionY);
                for (int x = bounds.minX(); x <= bounds.maxX(); x++)
                    for (int y = minY; y <= maxY; y++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        scan.cell();
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        BlockPos pos = new BlockPos(startX + x, sectionY + y, startZ + z);
                        source.world.put(pos, source.pose.transformPosition(pos.getCenter()));
                        if (source.world.size() > MAX_SOURCE_BLOCKS)
                            throw refusal("目标物理结构超过 32768 个方块，暂不一次读取更大的结构");
                    }
            }
        }
        return source;
    }

    private static Source readWorld(ServerLevel level, Vec3 center, double radius, ScanBudget scan) {
        Source source = new Source(level, null);
        BlockPos min = BlockPos.containing(center.add(-radius, -radius, -radius));
        BlockPos max = BlockPos.containing(center.add(radius, radius, radius));
        for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
            scan.cell();
            BlockPos pos = mutable.immutable();
            if (pos.getCenter().distanceToSqr(center) > radius * radius) continue;
            if (level.isOutsideBuildHeight(pos)) continue;
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
                continue;
            if (Sable.HELPER.getContaining(level, pos) != null) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) continue;
            source.world.put(pos, pos.getCenter());
            if (source.world.size() > MAX_SOURCE_BLOCKS) throw refusal("斩击范围内的方块过多");
        }
        return source;
    }

    private static void addPlan(Source source, Vec3 center, Vec3 normal, double radius, List<Transfer> transfers) {
        StructureSever.Plan plan = StructureSever.plan(source.world, center, normal, radius, MAX_SOURCE_BLOCKS, 512);
        if (source.body != null && ((!source.kerfed && plan.cutEdges() == 0) || plan.components().size() < 2)) return;
        ServerLevel level = source.body == null ? source.level : source.body.getLevel();
        List<Part> parts = new ArrayList<>();
        Map<BlockPos, Part> owners = new LinkedHashMap<>();
        for (Set<BlockPos> component : plan.components()) {
            Part part = new Part(component);
            for (BlockPos pos : component) {
                double mass = PhysicsBlockPropertyHelper.getMass(level, pos, level.getBlockState(pos));
                if (!Double.isFinite(mass) || mass < 0) {source.fixed.add(pos);mass=0;}
                part.mass += mass;
                owners.put(pos, part);
            }
            parts.add(part);
        }
        Part largest = parts.stream().filter(p -> p.mass > 0).max(Comparator.comparingDouble(p -> p.mass)).orElse(null);
        if (largest == null) return;
        // Flowers/grass have no collision mass. Attach them to a supporting solid piece, never spawn an empty body.
        for (Part decoration : List.copyOf(parts)) if (decoration.mass == 0) {
            Part target = null;
            int bestScore = -1;
            for (BlockPos pos : decoration.blocks) for (Direction direction : Direction.values()) {
                Part adjacent = owners.get(pos.relative(direction));
                if (adjacent == null || adjacent == decoration || adjacent.mass <= 0) continue;
                int score = direction == Direction.DOWN ? 10 : direction == Direction.UP ? 1 : 3;
                if (score > bestScore || score == bestScore && target != null && adjacent.mass > target.mass) {
                    target = adjacent; bestScore = score;
                }
            }
            // An existing body's loose decorations remain with its retained solid core.
            // In ordinary terrain unattached zero-mass flora stays in the world and follows native support rules.
            if (target == null && source.body != null) target = largest;
            if (target != null) {
                target.blocks.addAll(decoration.blocks);
                for (BlockPos pos : decoration.blocks) owners.put(pos, target);
            }
            parts.remove(decoration);
        }
        if (source.body != null) {
            // Retain an anchored component in the original body, even if the movable half
            // is larger. Retaining the largest unconditionally would prevent that half splitting.
            Part retained=parts.stream().filter(part->!Collections.disjoint(part.blocks,source.fixed))
                    .max(Comparator.comparingDouble(part->part.mass)).orElse(largest);
            parts.remove(retained);
            source.retainedSide = sideOf(retained.blocks, source, center, normal);
            source.retainedBlocks = retained.blocks.size();
        }
        // Leave protected/immovable anchors, and the material still connected to them,
        // in their original world/body. Independent permitted shards continue transferring.
        for (Part part : parts) if (part.mass > 0 && Collections.disjoint(part.blocks,source.fixed))
            transfers.add(new Transfer(source, part.blocks, sideOf(part.blocks, source, center, normal)));
    }
    private static int sideOf(Collection<BlockPos> blocks, Source source, Vec3 center, Vec3 normal) {
        double sum = 0;
        for (BlockPos pos : blocks) sum += source.world.get(pos).subtract(center).dot(normal);
        return sum >= 0 ? 1 : -1;
    }
    private static final class Part {
        final Set<BlockPos> blocks;
        double mass;
        Part(Set<BlockPos> blocks) { this.blocks = new LinkedHashSet<>(blocks); }
    }

    private static boolean permitsCell(LivingEntity owner,ServerLevel level,BlockPos storage,Vec3 center,BlockState state){
        if(!allowed(owner,level))throw refusal("术式破坏配置或所在维度已改变");
        try{preflight(owner,level,storage,center,state);return true;}
        catch(Refusal denied){if(!allowed(owner,level))throw denied;return false;}
    }
    private static double positiveMass(ServerLevel level,BlockPos pos){
        double mass=PhysicsBlockPropertyHelper.getMass(level,pos,level.getBlockState(pos));
        return Double.isFinite(mass)&&mass>0?mass:0;
    }

    private static void markFixed(LivingEntity owner,ServerLevel level,Source source){
        for(var cell:source.world.entrySet()){
            BlockState state=level.getBlockState(cell.getKey());
            double mass=PhysicsBlockPropertyHelper.getMass(level,cell.getKey(),state);
            if(!Double.isFinite(mass)||mass<0||!permitsCell(owner,level,cell.getKey(),cell.getValue(),state))source.fixed.add(cell.getKey());
        }
    }
    private static void preflight(LivingEntity owner, ServerLevel level, BlockPos storage, Vec3 worldCenter, BlockState state) {
        if (owner.level() != level) throw refusal("释放者已离开维度，本次斩击取消");
        BlockPos world = BlockPos.containing(worldCenter);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, storage) < 0
                || !state.canEntityDestroy(level, storage, owner)) throw refusal("斩击范围包含不可移动或不可破坏的方块");
        if (!level.getWorldBorder().isWithinBounds(world) || !level.isInWorldBounds(world)) throw refusal("斩击范围越过世界边界");
        if (owner instanceof ServerPlayer player) {
            if (!level.mayInteract(player, world) || !player.mayInteract(level, world)
                    || player.blockActionRestricted(level, world, player.gameMode.getGameModeForPlayer()))
                throw refusal("斩击范围包含无权改变的方块");
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, world, state, player);
            NeoForge.EVENT_BUS.post(event);
            if (owner.level() != level || event.isCanceled()) throw refusal("保护规则阻止了这次结构切割");
            if (!world.equals(storage)) {
                event = new BlockEvent.BreakEvent(level, storage, state, player); NeoForge.EVENT_BUS.post(event);
                if (owner.level() != level || event.isCanceled()) throw refusal("物理结构的保护规则阻止了这次切割");
            }
        } else {
            // Use the real mob and both world/storage coordinates; never impersonate a player.
            if (!EventHooks.onEntityDestroyBlock(owner, world, state)
                    || !world.equals(storage) && !EventHooks.onEntityDestroyBlock(owner, storage, state)
                    || !allowed(owner, level)) throw refusal("术式破坏配置或保护权限阻止了这次结构切割");
        }
    }

    private static boolean hasRunningContraption(ServerLevel level, Source source) {
        if (source.body != null && !source.body.getPlot().getContraptions().isEmpty())
            return true;
        BoundingBox3i bounds = BoundingBox3i.from(source.world.keySet());
        if (bounds == null) return false;
        AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0).inflate(2);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
            for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
                String name = type.getName();
                if (name.equals("com.simibubi.create.content.contraptions.ControlledContraptionEntity")
                        || name.equals("com.simibubi.create.content.contraptions.AbstractContraptionEntity"))
                    return true;
            }
        }
        return false;
    }

    private static void validateMass(ServerLevel level, Collection<BlockPos> blocks) {
        double mass = 0;
        for (BlockPos pos : blocks) mass += PhysicsBlockPropertyHelper.getMass(level, pos, level.getBlockState(pos));
        if (!Double.isFinite(mass) || mass <= 1e-8) throw refusal("斩击会产生没有有效物理质量的碎片，请换一个斩面");
    }

    private static void move(ServerLevel level, ServerSubLevelContainer container, Transfer transfer,
                             Map<BlockPos, Snapshot> snapshots, List<Moved> moved) {
        List<BlockPos> ordered = new ArrayList<>(transfer.blocks);
        for (BlockPos pos : ordered) if (!matchesAfterNeighborUpdates(level, pos, snapshots.get(pos)))
            throw new IllegalStateException("Source changed between fragment transfers: " + pos);
        // Place a positive-mass block first so a decorative block cannot invalidate a new body.
        ordered.sort(Comparator.comparingDouble((BlockPos p) ->
                -PhysicsBlockPropertyHelper.getMass(level, p, level.getBlockState(p))));
        BlockPos anchor = ordered.getFirst();
        BoundingBox3i bounds = BoundingBox3i.from(ordered).expand(1, 1, 1);
        AllocationObserver observer = allocationObserver(container);
        AllocationCapture capture = new AllocationCapture();
        AllocationCapture previousCapture = observer.active.get();
        observer.active.set(capture);
        ServerSubLevel result;
        try (var ignored = SkillBlockDrops.suppress(level)) {
            result = SubLevelAssemblyHelper.assembleBlocks(level, anchor, ordered, bounds);
        } catch (RuntimeException failure) {
            // Capture only the native call's direct allocation. Bodies created by somebody
            // else's nested assembly listener are never inferred to be ours from a set diff.
            if (capture.body != null) moved.add(new Moved(transfer, capture.body,
                    capture.body.getPlot().getCenterBlock().subtract(anchor)));
            else LOG.error("Sable failed before its direct allocation was observed; no unknown body will be removed");
            throw failure;
        } finally {
            if (previousCapture == null) observer.active.remove(); else observer.active.set(previousCapture);
        }
        BlockPos offset = result.getPlot().getCenterBlock().subtract(anchor);
        moved.add(new Moved(transfer, result, offset));
        SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
                anchor, result.getPlot().getCenterBlock(), 0, Rotation.NONE, level);
        for (BlockPos source : ordered) {
            BlockPos destination = source.offset(offset);
            Snapshot snapshot = snapshots.get(source);
            BlockState oldState = level.getBlockState(source), actualState = level.getBlockState(destination);
            BlockState expectedState = transform.apply(snapshot.state);
            boolean removed = oldState.isAir() || oldState.getBlock() instanceof LiquidBlock;
            // Unsupported, zero-mass flora may disappear without loot during native neighbor updates.
            // Never exempt a solid block, a container, or a supported decoration from transfer checks.
            boolean removedDecoration = snapshot.tag == null && actualState.isAir()
                    && PhysicsBlockPropertyHelper.getMass(level, destination, expectedState) <= 0
                    && !expectedState.canSurvive(level, destination);
            if (!removed || !compatibleState(expectedState, actualState) && !removedDecoration
                    || snapshot.tag != null && level.getBlockEntity(destination) == null)
                throw new MoveFailure(source, "Incomplete Sable transfer at " + source + " -> " + destination
                        + "; source=" + oldState + "; expected=" + expectedState + "; actual=" + actualState);
            if (snapshot.tag != null) {
                CompoundTag expected = snapshot.tag.copy();
                expected.putInt("x", destination.getX());
                expected.putInt("y", destination.getY());
                expected.putInt("z", destination.getZ());
                CompoundTag actual = level.getBlockEntity(destination).saveWithFullMetadata(level.registryAccess());
                if (!expected.equals(actual))
                    throw new MoveFailure(source, "Block entity data did not survive assembly unchanged: " + source);
            }
        }
    }

    private static Map<UUID, TrackingPoint> snapshotTracking(ServerLevel level, List<Transfer> transfers) {
        Map<UUID, TrackingPoint> result = new LinkedHashMap<>();
        SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
        for (Transfer transfer : transfers) for (var entry : data.getAllTrackingPoints(BoundingBox3i.from(transfer.blocks).expand(1, 1, 1))) {
            TrackingPoint p = entry.second();
            result.putIfAbsent(entry.first(), new TrackingPoint(p.inSubLevel(), p.subLevelID(), p.lastSavedSubLevelPointer(),
                    new Vector3d(p.point()), copyNullable(p.globalPlaceholderPosition())));
        }
        return result;
    }

    private static void correctTracking(ServerLevel level, List<Moved> moved, Map<UUID, TrackingPoint> before) {
        SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
        for (Map.Entry<UUID, TrackingPoint> entry : before.entrySet()) {
            TrackingPoint original = entry.getValue();
            // A saved login point is at the feet, often just above its supporting block.
            BlockPos feet = BlockPos.containing(original.point().x, original.point().y, original.point().z);
            Moved owner = null;
            for (Moved result : moved) if (result.transfer.blocks.contains(feet)) { owner = result; break; }
            if (owner == null) for (Moved result : moved)
                if (result.transfer.blocks.contains(feet.below())) { owner = result; break; }
            if (owner == null) data.setTrackingPoint(entry.getKey(), original);
            else {
                Vector3d point = new Vector3d(original.point()).add(owner.offset.getX(), owner.offset.getY(), owner.offset.getZ());
                // A fresh body has no serialization pointer yet, so provide its real world
                // placeholder even if the original saved body's placeholder was null.
                Vector3d placeholder = owner.body.getLastSerializationPointer() == null
                        ? owner.body.logicalPose().transformPosition(new Vector3d(point)) : null;
                data.setTrackingPoint(entry.getKey(), new TrackingPoint(true, owner.body.getUniqueId(),
                        owner.body.getLastSerializationPointer(), point, placeholder));
            }
        }
    }

    private static boolean rollback(ServerLevel level, ServerSubLevelContainer container, List<Source> sources,
                                    List<Moved> moved, Map<BlockPos, Snapshot> snapshots,
                                    Map<UUID, TrackingPoint> trackingBefore, SableHangingAttachments attachments) {
        try (var ignored = SkillBlockDrops.suppress(level)) {
            return rollbackWithoutDrops(level, container, sources, moved, snapshots, trackingBefore, attachments);
        }
    }

    private static boolean rollbackWithoutDrops(ServerLevel level, ServerSubLevelContainer container, List<Source> sources,
                                                List<Moved> moved, Map<BlockPos, Snapshot> snapshots,
                                                Map<UUID, TrackingPoint> trackingBefore, SableHangingAttachments attachments) {
        boolean restored = true;
        Set<BlockPos> unclearedSources = new HashSet<>();
        Set<ServerSubLevel> unclearedBodies = new HashSet<>();
        Set<BlockPos> naturallyRemoved = new HashSet<>();
        // Clearing the final mass block can deallocate a plot before explicit removeSubLevel.
        // Move the same live hangings back first; no entity support tick runs during recovery.
        boolean parked = true;
        try { attachments.parkForRollback(); }
        catch (RuntimeException failure) {
            parked = false; restored = false;
            LOG.error("Could not park attached entities before block recovery", failure);
        }
        Set<SubLevel> occupied = new HashSet<>();
        for (BlockPos anchor : attachments.liveAnchors()) {
            SubLevel owner = Sable.HELPER.getContaining(level, anchor);
            if (owner != null) occupied.add(owner);
        }
        for (Moved result : moved) if (!parked || occupied.contains(result.body)) {
            // A failed relocation must preserve that destination AND skip the matching source
            // restoration, rather than deleting its entity or duplicating its block contents.
            unclearedBodies.add(result.body);
            unclearedSources.addAll(result.transfer.blocks);
            restored = false;
        }
        // Keep native support-loss removals committed; recovery must not reconstruct vanished flora.
        for (Moved result : moved) for (BlockPos source : result.transfer.blocks) {
            Snapshot snapshot = snapshots.get(source);
            BlockPos destination = source.offset(result.offset);
            BlockState oldState = level.getBlockState(source);
            if (snapshot.tag == null && (oldState.isAir() || oldState.getBlock() instanceof LiquidBlock)
                    && level.getBlockState(destination).isAir()
                    && PhysicsBlockPropertyHelper.getMass(level, destination, snapshot.state) <= 0
                    && !snapshot.state.canSurvive(level, destination)) naturallyRemoved.add(source);
        }
        // Clear only owned destination cells; no destroyBlock(), drops, or blast is involved.
        for (Moved result : moved) for (BlockPos source : result.transfer.blocks) {
            if (unclearedBodies.contains(result.body)) {
                unclearedSources.add(source);
                continue;
            }
            try { clearWithoutDrops(level, source.offset(result.offset)); }
            catch (RuntimeException ex) {
                restored = false;
                unclearedSources.add(source);
                unclearedBodies.add(result.body);
                LOG.error("Could not clear failed cut destination; original will not be duplicated", ex);
            }
        }
        for (Map.Entry<BlockPos, Snapshot> entry : snapshots.entrySet()) {
            if (unclearedSources.contains(entry.getKey()) || naturallyRemoved.contains(entry.getKey())) continue;
            try {
                BlockPos pos = entry.getKey();
                Snapshot snapshot = entry.getValue();
                if (matchesSnapshot(level, pos, snapshot)) continue;
                Clearable.tryClear(level.getBlockEntity(pos));
                level.setBlock(pos, snapshot.state, 18);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (snapshot.tag != null) {
                    if (blockEntity == null) throw new IllegalStateException("Missing restored block entity at " + pos);
                    blockEntity.loadWithComponents(snapshot.tag.copy(), level.registryAccess());
                    blockEntity.setChanged();
                }
                level.sendBlockUpdated(pos, Blocks.AIR.defaultBlockState(), snapshot.state, 3);
                if (!matchesSnapshot(level, pos, snapshot)) throw new IllegalStateException("Restored block data mismatch at " + pos);
            } catch (RuntimeException ex) { restored = false; LOG.error("Could not restore cut source {}", entry.getKey(), ex); }
        }
        restored = attachments.rollback() && restored;
        for (BlockPos anchor : attachments.liveAnchors()) {
            SubLevel owner = Sable.HELPER.getContaining(level, anchor);
            for (Moved result : moved) if (owner == result.body) { unclearedBodies.add(result.body); restored = false; }
        }
        for (Moved result : moved) {
            if (unclearedBodies.contains(result.body)) continue;
            try {
                if (container.getSubLevel(result.body.getUniqueId()) != null)
                    container.removeSubLevel(result.body, SubLevelRemovalReason.REMOVED);
            } catch (RuntimeException ex) { restored = false; LOG.error("Could not remove failed cut body", ex); }
        }
        for (Source source : sources) if (source.body != null) {
            try {
                source.body.logicalPose().set(source.pose);
                container.physicsSystem().getPipeline().teleport(source.body, source.pose.position(), source.pose.orientation());
                container.physicsSystem().getPipeline().resetVelocity(source.body);
                RigidBodyHandle.of(source.body).addLinearAndAngularVelocity(source.linear, source.angular);
                source.body.updateLastPose();
            } catch (RuntimeException ex) { restored = false; LOG.error("Could not restore cut body pose", ex); }
        }
        SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
        trackingBefore.forEach(data::setTrackingPoint);
        return restored;
    }

    private static void clearWithoutDrops(ServerLevel level, BlockPos pos) {
        Clearable.tryClear(level.getBlockEntity(pos));
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null)
            throw new IllegalStateException("Failed to clear owned destination " + pos);
    }

    /** Only properties recalculated from neighbors may differ. Facing, half, axis and inventories remain strict. */
    private static boolean compatibleState(BlockState expected, BlockState actual) {
        if (expected == actual) return true;
        if (expected.getBlock() != actual.getBlock()) return false;
        for (Property<?> property : expected.getProperties()) {
            if (Objects.equals(expected.getValue(property), actual.getValue(property))) continue;
            String name = property.getName();
            boolean connection = name.equals("north") || name.equals("south") || name.equals("east") || name.equals("west");
            boolean recalculated = expected.getBlock() instanceof StairBlock && property == BlockStateProperties.STAIRS_SHAPE
                    || expected.getBlock() instanceof LeavesBlock && name.equals("distance")
                    || expected.getBlock() instanceof SnowyDirtBlock && name.equals("snowy")
                    || (expected.getBlock() instanceof FenceBlock || expected.getBlock() instanceof IronBarsBlock) && connection
                    || expected.getBlock() instanceof WallBlock && (connection || name.equals("up"))
                    || expected.getBlock() instanceof FenceGateBlock && name.equals("in_wall");
            if (!recalculated) return false;
        }
        return true;
    }
    private static boolean matchesAfterNeighborUpdates(ServerLevel level, BlockPos pos, Snapshot snapshot) {
        if (!compatibleState(snapshot.state, level.getBlockState(pos))) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (snapshot.tag == null) return blockEntity == null;
        return blockEntity != null && snapshot.tag.equals(blockEntity.saveWithFullMetadata(level.registryAccess()));
    }

    private static boolean matchesSnapshot(ServerLevel level, BlockPos pos, Snapshot snapshot) {
        if (level.getBlockState(pos) != snapshot.state) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (snapshot.tag == null) return blockEntity == null;
        return blockEntity != null && snapshot.tag.equals(blockEntity.saveWithFullMetadata(level.registryAccess()));
    }

    private static boolean allowed(LivingEntity player, ServerLevel level) {
        return dev.jujutsuvfx.server.TechniqueTerrainRules.cleavePhysics()
                && ((ServerLevel) player.level()) == level && player.isAlive() && !player.isSpectator() && (!(player instanceof ServerPlayer actor) || actor.mayBuild())
                && dev.jujutsuvfx.server.TechniqueTerrainRules.canGrief(player) && ((ServerLevel) player.level()) == level;
    }

    private static AllocationObserver allocationObserver(ServerSubLevelContainer container) {
        synchronized (ALLOCATION_OBSERVERS) {
            return ALLOCATION_OBSERVERS.computeIfAbsent(container, key -> {
                AllocationObserver observer = new AllocationObserver();
                key.addObserver(observer);
                return observer;
            });
        }
    }

    private static final class AllocationCapture { ServerSubLevel body; }
    private static final class AllocationObserver implements SubLevelObserver {
        final ThreadLocal<AllocationCapture> active = new ThreadLocal<>();
        @Override public void onSubLevelAdded(SubLevel subLevel) {
            AllocationCapture capture = active.get();
            if (capture == null || capture.body != null || !(subLevel instanceof ServerSubLevel server)) return;
            // A listener may synchronously create a second body before this observer sees
            // the first. Only the outer allocation belongs to our assembleBlocks call.
            long allocationDepth = StackWalker.getInstance().walk(frames -> frames.filter(frame ->
                    frame.getClassName().equals(SubLevelContainer.class.getName())
                            && frame.getMethodName().equals("allocateSubLevel")).count());
            if (allocationDepth == 1) capture.body = server;
        }
    }

    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static Vector3d copyNullable(Vector3d p) { return p == null ? null : new Vector3d(p); }
    private static Result fail(String message) { return new Result(false, 0, 0, message); }
    private static Refusal refusal(String message) { return new Refusal(message); }
    private static final class Refusal extends RuntimeException { private Refusal(String message) { super(message); } }
    private static final class MoveFailure extends RuntimeException {
        final BlockPos pos;
        MoveFailure(BlockPos pos, String message) { super(message); this.pos = pos.immutable(); }
    }
    private static final class ScanBudget {
        int scanned;final int limit;
        ScanBudget(){this(MAX_SCAN_CELLS);}
        ScanBudget(int limit){this.limit=limit;}
        void cell() { if (++scanned > limit) throw refusal("结构扫描量过大，请选择较小的结构"); }
    }
    private static final class Source {
        final ServerSubLevel body;
        final Pose3d pose;
        final Vector3d linear, angular;
        final Map<BlockPos, Vec3> world = new LinkedHashMap<>();
        final Set<BlockPos> contacted = new LinkedHashSet<>();
        final Set<BlockPos> fixed = new LinkedHashSet<>();
        int retainedSide, retainedBlocks;
        boolean kerfed, impulseOnly, destructiveOnly;
        double contact;
        final ServerLevel level;
        Source(ServerLevel level, ServerSubLevel body) {
            this.level = level;
            this.body = body;
            this.pose = body == null ? null : new Pose3d(body.logicalPose());
            this.linear = body == null ? null : RigidBodyHandle.of(body).getLinearVelocity(new Vector3d());
            this.angular = body == null ? null : RigidBodyHandle.of(body).getAngularVelocity(new Vector3d());
        }
    }
    private record Transfer(Source source, Set<BlockPos> blocks, int side) {
        private Transfer { blocks = Set.copyOf(new LinkedHashSet<>(blocks)); }
    }
    private record Snapshot(BlockState state, CompoundTag tag) {}
    private record Moved(Transfer transfer, ServerSubLevel body, BlockPos offset) {}
}
