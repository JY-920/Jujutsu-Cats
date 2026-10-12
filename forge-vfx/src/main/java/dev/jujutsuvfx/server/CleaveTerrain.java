package dev.jujutsuvfx.server;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.compat.CleaveCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Ordinary world blocks only: remove a thin plane through the slash, without assembly or physics cutting. */
@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class CleaveTerrain {
    private static final int MAX_JOBS = 96, BLOCKS_PER_TICK = 128, BATCH = 24;
    private static final Map<MinecraftServer, ArrayDeque<Job>> JOBS = new WeakHashMap<>();
    private record Cell(BlockPos pos, BlockState state) {}
    private static final class Job {
        final LivingEntity owner;
        final ServerLevel level;
        final List<Cell> cells;
        final int expires;
        int cursor;
        Job(LivingEntity owner, List<Cell> cells) {
            this.owner = owner; level = (ServerLevel) owner.level(); this.cells = cells;
            expires = level.getServer().getTickCount() + 100;
        }
        boolean active() {
            return TechniqueTerrainRules.enabled() && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
                    && owner.level() == level && level.getServer().getTickCount() <= expires
                    && (!(owner instanceof ServerPlayer player) || player.mayBuild());
        }
    }
    private CleaveTerrain() {}

    static Vec3 point(LivingEntity owner, double reach) {
        ServerLevel level = (ServerLevel) owner.level();
        Vec3 eye = CleaveCompatibility.worldEye(owner), direction = CleaveCompatibility.worldDirection(owner, owner.getLookAngle());
        Vec3 end = eye.add(direction.scale(reach));
        ClipContext context = new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
        // Traverse real world cells directly. Level.clip may also return a moving structure's storage coordinates.
        BlockHitResult hit = BlockGetter.traverseBlocks(eye, end, context, (ctx, pos) -> {
            if (!loaded(level, pos)) return BlockHitResult.miss(pos.getCenter(), Direction.getNearest(direction.x, direction.y, direction.z), pos);
            if (!CleaveCompatibility.worldBlock(level, pos)) return null;
            BlockState block = level.getBlockState(pos);
            return ctx.getBlockShape(block, level, pos).clip(eye, end, pos);
        }, ctx -> BlockHitResult.miss(end, Direction.getNearest(direction.x, direction.y, direction.z), BlockPos.containing(end)));
        boolean blockHit = hit.getType() == HitResult.Type.BLOCK;
        Vec3 selected = blockHit ? hit.getLocation().add(direction.scale(.15)) : eye.add(direction.scale(Math.min(12, reach)));
        double closest = blockHit ? eye.distanceToSqr(hit.getLocation()) : reach * reach;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(.7),
                target -> TechniquePlayerRules.canAffect(owner, target))) {
            AABB box = target.getBoundingBox().inflate(.3);
            Vec3 entry = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
            if (entry != null && eye.distanceToSqr(entry) < closest) {
                closest = eye.distanceToSqr(entry); selected = target.getBoundingBox().getCenter();
            }
        }
        return selected;
    }

    static boolean cut(LivingEntity owner, Vec3 center, Vec3 normal, double radius, Vec3 second, double scale) {
        if (!(owner.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !TechniqueTerrainRules.canGrief(owner) || !finite(center) || !finite(normal) || normal.lengthSqr() < .9
                || !Double.isFinite(radius) || radius <= 0 || !Double.isFinite(scale) || scale <= 0
                || second != null && (!finite(second) || second.lengthSqr() < .9)) return false;
        var queue = JOBS.computeIfAbsent(level.getServer(), key -> new ArrayDeque<>());
        if (queue.size() >= MAX_JOBS) return false;
        // Physical structures can be enormous; this fallback always keeps the ordinary slash's bounded radius.
        radius = Math.min(8, radius);
        double secondaryRadius = Math.min(8, radius * scale), reach = second == null ? radius : Math.max(radius, secondaryRadius);
        normal = normal.normalize(); if (second != null) second = second.normalize();
        List<Cell> cells = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(BlockPos.containing(center.subtract(reach, reach, reach)),
                BlockPos.containing(center.add(reach, reach, reach)))) {
            if (!touches(cursor, center, normal, radius) && (second == null || !touches(cursor, center, second, secondaryRadius))) continue;
            if (!loaded(level, cursor) || !CleaveCompatibility.worldBlock(level, cursor)) continue;
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock) && state.getDestroySpeed(level, cursor) >= 0)
                cells.add(new Cell(cursor.immutable(), state));
        }
        if (cells.isEmpty()) return false;
        queue.addLast(new Job(owner, cells));
        return true;
    }
    private static boolean touches(BlockPos pos, Vec3 center, Vec3 normal, double radius) {
        double x = pos.getX() + .5 - center.x, y = pos.getY() + .5 - center.y, z = pos.getZ() + .5 - center.z;
        double dx = Math.max(0, Math.abs(x) - .5), dy = Math.max(0, Math.abs(y) - .5), dz = Math.max(0, Math.abs(z) - .5);
        double extent = .5 * (Math.abs(normal.x) + Math.abs(normal.y) + Math.abs(normal.z)) + .12;
        return dx * dx + dy * dy + dz * dz <= radius * radius && Math.abs(x * normal.x + y * normal.y + z * normal.z) <= extent;
    }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++)
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean mayBreak(Job job, BlockPos pos) {
        return !(job.owner instanceof ServerPlayer player) || job.level.mayInteract(player, pos) && player.mayInteract(job.level, pos)
                && !player.blockActionRestricted(job.level, pos, player.gameMode.getGameModeForPlayer());
    }
    @SubscribeEvent public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var queue = JOBS.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        if (queue == null) return;
        if (!TechniqueTerrainRules.enabled()) { JOBS.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()); return; }
        long deadline = System.nanoTime() + 3_000_000L;
        int budget = BLOCKS_PER_TICK, turns = queue.size();
        while (turns-- > 0 && !queue.isEmpty() && budget > 0 && System.nanoTime() < deadline) {
            Job job = queue.removeFirst();
            if (!job.active() || !TechniqueTerrainRules.canGrief(job.owner)) continue;
            int stop = Math.min(job.cells.size(), job.cursor + Math.min(BATCH, budget));
            try (SkillBlockDrops.Scope ignored = SkillBlockDrops.quietTerrain(job.level);
                 SkillHangingBreaks attachments = SkillHangingBreaks.capture(job.level,
                         job.cells.subList(job.cursor, stop).stream().map(Cell::pos).toList())) {
                while (job.cursor < stop && job.active() && System.nanoTime() < deadline) {
                    Cell cell = job.cells.get(job.cursor++); budget--;
                    if (!loaded(job.level, cell.pos) || !CleaveCompatibility.worldBlock(job.level, cell.pos) || !mayBreak(job, cell.pos)
                            || job.level.getBlockState(cell.pos) != cell.state || !cell.state.canEntityDestroy(job.level, cell.pos, job.owner)) continue;
                    boolean permit;
                    if (job.owner instanceof ServerPlayer player) {
                        var breaking = new BlockEvent.BreakEvent(job.level, cell.pos, cell.state, player);
                        MinecraftForge.EVENT_BUS.post(breaking); permit = !breaking.isCanceled();
                    } else permit = net.minecraftforge.event.ForgeEventFactory.onEntityDestroyBlock(job.owner, cell.pos, cell.state);
                    if (!permit || !job.active() || !loaded(job.level, cell.pos) || !mayBreak(job, cell.pos)
                            || job.level.getBlockState(cell.pos) != cell.state || !CleaveCompatibility.worldBlock(job.level, cell.pos)) continue;
                    CleaveCompatibility.beforeWorldBreak(job.level, cell.pos);
                    if (job.active()) job.level.destroyBlock(cell.pos, false, job.owner);
                }
            } catch (RuntimeException | LinkageError failure) {
                LogUtils.getLogger().warn("Could not finish ordinary Cleave terrain batch; later slashes remain usable", failure);
                continue;
            }
            if (job.active() && job.cursor < job.cells.size()) queue.addLast(job);
        }
        if (queue.isEmpty()) JOBS.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { JOBS.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()); }
}
