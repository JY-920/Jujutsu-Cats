package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.TechniqueDamage;
import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.compat.CleaveCompatibility;
import dev.jujutsuvfx.core.SukunaSlashCharge;
import dev.jujutsuvfx.core.SukunaSlashStyle;
import dev.jujutsuvfx.item.SukunaSlashItem;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.SukunaCut;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import dev.jujutsuvfx.platform.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-owned charge -> release -> finite burst, with real cuts following only the chosen body. */
@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class SukunaSlashSkills {
    public static final double REACH = 28, CLEAVE_REACH = 64, CUT_RADIUS = 4.5;
    public static final float DISMANTLE_DAMAGE = 50;
    private static final int MAX_PENDING = 64, MAX_TRACKED_FRAGMENTS = 512;
    private static final int MAX_PHYSICS_PLANES_PER_TICK = 2;
    private static final Map<MinecraftServer, int[]> PHYSICS_BUDGET = new WeakHashMap<>();
    static boolean reservePhysics(MinecraftServer server, int planes) {
        int[] budget = PHYSICS_BUDGET.computeIfAbsent(server, key -> new int[]{server.getTickCount(), 0});
        if (budget[0] != server.getTickCount()) { budget[0] = server.getTickCount(); budget[1] = 0; }
        if (budget[1] + planes > MAX_PHYSICS_PLANES_PER_TICK) return false;
        budget[1] += planes; return true;
    }
    private static final Map<MinecraftServer, List<Pending>> PENDING = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Charge>> CHARGES = new WeakHashMap<>();
    private record PhysicsTarget(UUID id, Vec3 center, double radius, int blocks) {}
    private record Aim(UUID player, ServerLevel level, InteractionHand hand, SukunaSlashItem.Kind kind,
                       Vec3 origin, Vec3 forward, Vec3 center, Vec3 stroke, PhysicsTarget target,
                       ItemStack stack, int slot) {}
    private record Pending(Aim aim, int due, int expires) {}
    private static final class Charge {
        Aim aim;
        final int born;
        final ArrayDeque<UUID> targets = new ArrayDeque<>();
        boolean committed, initial = true, trackFragments, burst, physics;
        Vec3 previousCut;
        int next = Integer.MAX_VALUE, remaining, deadline, styleSeed, lastMessage = -1000;
        Charge(Aim aim, int born) { this.aim = aim; this.born = born; }
        boolean addTargets(List<UUID> bodies) {
            var all = new LinkedHashSet<>(targets);
            all.addAll(bodies);
            if (all.size() > MAX_TRACKED_FRAGMENTS) { targets.clear(); remaining = 0; return false; }
            targets.clear(); targets.addAll(all); return true;
        }
    }
    private SukunaSlashSkills() {}

    public static boolean cast(ServerPlayer player, InteractionHand hand, SukunaSlashItem.Kind kind) {
        if (!player.isAlive() || player.isSpectator()
                || !(player.getItemInHand(hand).getItem() instanceof SukunaSlashItem item) || item.kind() != kind
                || player.getCooldowns().isOnCooldown(item)) return false;
        MinecraftServer server = player.server;
        List<Pending> queue = PENDING.getOrDefault(server, List.of());
        Map<UUID, Charge> charges = CHARGES.getOrDefault(server, Map.of());
        if (queue.size() >= MAX_PENDING || charges.size() >= MAX_PENDING
                || charges.values().stream().anyMatch(c->c.aim.player.equals(player.getUUID())&&!c.committed)) return false;
        boolean heavy = kind == SukunaSlashItem.Kind.CLEAVE;
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 horizontal = new Vec3(-forward.z, 0, forward.x);
        if (horizontal.lengthSqr() < 1e-6) horizontal = new Vec3(1, 0, 0);
        horizontal = horizontal.normalize();
        Vec3 vertical = horizontal.cross(forward).normalize();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        if (arm == HumanoidArm.LEFT) horizontal = horizontal.scale(-1);
        Vec3 stroke = vertical.scale(.94).add(horizontal.scale(.34)).normalize();
        Vec3 origin = player.getEyePosition(), center = origin.add(forward.scale(6));
        PhysicsTarget target = null;
        try {
            if (heavy) {
                target = TechniqueTerrainRules.cleavePhysics() ? Physics.point(player, CLEAVE_REACH, CUT_RADIUS) : ordinaryTarget(player);
                if (target == null) return false;
                center = target.center;
                origin = CleaveCompatibility.worldEye(player);
                forward = CleaveCompatibility.worldDirection(player, forward);
                stroke = CleaveCompatibility.worldDirection(player, stroke);
            }
        } catch (RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("Could not select Sukuna cut target", failure);
            message(player, "无法读取物理结构，请检查日志中的 Sable 兼容信息。"); return false;
        }
        Aim aim = new Aim(player.getUUID(), player.serverLevel(), hand, kind, origin, forward, center, stroke, target,
                player.getItemInHand(hand), player.getInventory().selected);
        int now = server.getTickCount();
        if (heavy) {
            CHARGES.computeIfAbsent(server, ignored -> new LinkedHashMap<>()).put(UUID.randomUUID(), new Charge(aim, now));
            cooldown(player, 2);
        } else {
            PENDING.computeIfAbsent(server, ignored -> new ArrayList<>()).add(new Pending(aim, now + 6, now + 26));
            cooldown(player, 20);
        }
        TechniqueGestures.interrupt(player);
        return true;
    }

    public static void release(ServerPlayer player) {
        Charge charge = CHARGES.getOrDefault(player.server, Map.of()).values().stream()
                .filter(c->c.aim.player.equals(player.getUUID())&&!c.committed).reduce((a,b)->b).orElse(null);
        if (charge != null && !charge.committed && valid(player, charge.aim)) commit(player, charge);
    }

    private static void commit(ServerPlayer player, Charge charge) {
        int now = player.server.getTickCount(), held = Math.max(0, now - charge.born);
        // Resolve the release direction now. A short press never acquires or recenters on a body.
        Aim previous = charge.aim;
        charge.physics = TechniqueTerrainRules.cleavePhysics();
        PhysicsTarget target = charge.physics ? Physics.point(player, CLEAVE_REACH, CUT_RADIUS) : ordinaryTarget(player);
        if (charge.physics && held >= SukunaSlashCharge.CHARGE_TICKS) {
            try { target = Physics.acquire(player, CLEAVE_REACH, CUT_RADIUS); }
            catch (RuntimeException failure) { LogUtils.getLogger().warn("Could not lock crosshair body; using aimed area cut", failure); }
        }
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 horizontal = new Vec3(-direction.z, 0, direction.x);
        if (horizontal.lengthSqr() < 1e-6) horizontal = new Vec3(1, 0, 0);
        horizontal = horizontal.normalize();
        Vec3 vertical = horizontal.cross(direction).normalize();
        HumanoidArm arm = previous.hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        if (arm == HumanoidArm.LEFT) horizontal = horizontal.scale(-1);
        Vec3 stroke = vertical.scale(.94).add(horizontal.scale(.34)).normalize();
        charge.aim = new Aim(previous.player, previous.level, previous.hand, previous.kind,
                CleaveCompatibility.worldEye(player), CleaveCompatibility.worldDirection(player, direction), target.center, CleaveCompatibility.worldDirection(player, stroke),
                target, previous.stack, previous.slot);
        charge.trackFragments = charge.physics && held >= SukunaSlashCharge.CHARGE_TICKS && target.id != null;
        charge.committed = true;
        charge.remaining = SukunaSlashCharge.cuts(held);
        charge.burst = charge.remaining > 1;
        charge.next = now + SukunaSlashCharge.FIRST_CUT_DELAY;
        // Do not accumulate an unbounded delayed barrage when multiplayer physics work is busy.
        charge.deadline = now + 80;
        charge.styleSeed = player.getRandom().nextInt();
        cooldown(player, SukunaSlashCharge.cooldown(held));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        int now = server.getTickCount();
        List<Pending> queue = PENDING.get(server);
        if (queue != null) {
            List<Pending> ready = new ArrayList<>();
            var iterator = queue.iterator();
            while (iterator.hasNext()) {
                Pending pending = iterator.next();
                ServerPlayer player = server.getPlayerList().getPlayer(pending.aim.player);
                if (now > pending.expires || !valid(player, pending.aim)) { iterator.remove(); continue; }
                if (now >= pending.due) { ready.add(pending); iterator.remove(); }
            }
            for (Pending pending : ready) {
                ServerPlayer player = server.getPlayerList().getPlayer(pending.aim.player);
                if (!valid(player, pending.aim)) continue;
                // Wind-up only reserves the hand/slot. Resolve the actual eye and look at
                // the stroke, after this tick's player movement packets have been handled.
                try { dismantle(player, releaseAim(player, pending.aim)); }
                catch (RuntimeException failure) { LogUtils.getLogger().error("Dismantle failed", failure); }
            }
            if (queue.isEmpty()) PENDING.remove(server);
        }
        tickCharges(server, now);
    }

    private static void tickCharges(MinecraftServer server, int now) {
        Map<UUID, Charge> charges = CHARGES.get(server);
        if (charges == null) return;
        List<Charge> work = new ArrayList<>(charges.values());
        // A queued cross needs both plane slots. Oldest due first gives it the full next tick.
        work.sort(Comparator.comparingInt(charge -> charge.next));
        int budget = MAX_PHYSICS_PLANES_PER_TICK;
        for (Charge charge : work) {
            Aim aim = charge.aim;
            ServerPlayer player = server.getPlayerList().getPlayer(aim.player);
            if (!valid(player, aim)) { charges.values().remove(charge); continue; }
            if (!charge.committed) {
                // Release packet normally commits. This also handles a vanilla use ending naturally.
                if (using(player, aim)) continue;
                commit(player, charge);
            }
            // commit may replace the aim with the current release direction.
            aim = charge.aim;
            // Revoking physics affects queued slashes immediately. Enabling it never changes a committed plain burst into a physical one.
            if (!TechniqueTerrainRules.cleavePhysics()) { charge.physics = false; charge.trackFragments = false; charge.targets.clear(); }
            if (charge.remaining <= 0 || (charge.trackFragments && !charge.initial && charge.targets.isEmpty())) {
                charges.values().remove(charge); continue;
            }
            if (now > charge.deadline) {
                message(player, "本轮快速斩击已结束，已切开的结构保留。");
                charges.values().remove(charge); continue;
            }
            boolean cross = SukunaSlashStyle.isCross(charge.styleSeed);
            int cost = cross ? 2 : 1;
            if (now < charge.next || budget < cost) continue;
            try {
                PhysicsTarget target = null;
                if (!charge.trackFragments) {
                    charge.initial = false;
                    target = charge.physics ? aim.target : new PhysicsTarget(null, aim.target.center, CUT_RADIUS, 0);
                } else if (charge.initial) {
                    charge.initial = false;
                    target = aim.target.id == null ? aim.target : Physics.inspect(player, aim.target.id);
                } else {
                    int inspected = 0;
                    while (!charge.targets.isEmpty() && target == null && inspected++ < 16)
                        target = Physics.inspect(player, charge.targets.removeFirst());
                }
                if (target == null) { charge.next = now + 1; continue; }
                if (CleaveCompatibility.worldEye(player).distanceToSqr(target.center) > 192 * 192) { charge.next = now + 1; continue; }
                if (!reservePhysics(server, cost)) continue;
                budget -= cost;
                cleave(player, charge, target, cross, now);
                charge.remaining--;
                charge.next = now + SukunaSlashCharge.BURST_INTERVAL;
                charge.styleSeed = player.getRandom().nextInt();
                if (charge.remaining <= 0 || charge.trackFragments && charge.targets.isEmpty()) charges.values().remove(charge);
            } catch (RuntimeException | LinkageError failure) {
                LogUtils.getLogger().error("Cleave burst failed in {} at {}", aim.level.dimension().location(), aim.center, failure);
                message(player, "本轮斩击已停止，已完成的分割会保留。");
                charges.values().remove(charge);
            }
        }
        if (charges.isEmpty()) CHARGES.remove(server);
    }

    private static boolean using(ServerPlayer player, Aim aim) {
        return player.isUsingItem() && player.getUsedItemHand() == aim.hand
                && player.getUseItem().getItem() == player.getItemInHand(aim.hand).getItem();
    }
    private static boolean valid(ServerPlayer player, Aim aim) {
        return player != null && player.isAlive() && !player.isSpectator() && player.serverLevel() == aim.level
                && player.getItemInHand(aim.hand) == aim.stack
                && (aim.hand != InteractionHand.MAIN_HAND || player.getInventory().selected == aim.slot)
                && player.getItemInHand(aim.hand).getItem() instanceof SukunaSlashItem item && item.kind() == aim.kind
                && (aim.kind == SukunaSlashItem.Kind.CLEAVE || player.getEyePosition().distanceToSqr(aim.origin) < 16 * 16);
    }

    private static void cleave(ServerPlayer player, Charge charge, PhysicsTarget target, boolean cross, int now) {
        Aim aim = charge.aim;
        int seed = charge.styleSeed;
        Vec3 stroke = SukunaSlashStyle.rotateStroke(seed, aim.stroke, aim.forward);
        double tilt = new java.util.Random(seed ^ 0x721a).nextDouble() * 1.1 - .55;
        Vec3 normal = aim.forward.cross(stroke).add(aim.forward.scale(tilt)).normalize();
        Vec3 forward = stroke.cross(normal).normalize();
        Vec3 secondStroke = SukunaSlashStyle.secondStroke(stroke, forward);
        boolean physics = charge.physics && TechniqueTerrainRules.cleavePhysics();
        CutResult result = physics ? Physics.cut(player, target, normal, forward, cross,
                forward.cross(secondStroke).normalize(), SukunaSlashStyle.crossScale(seed),
                charge.burst ? seed : null, charge.previousCut) : visualCut(target, charge.burst, seed, stroke, forward);
        if (!physics && TechniqueTerrainRules.enabled()) {
            boolean terrain = CleaveTerrain.cut(player, result.center, normal, result.radius,
                    cross ? forward.cross(secondStroke).normalize() : null, SukunaSlashStyle.crossScale(seed));
            result = new CutResult(terrain, "", List.of(), result.center, result.radius);
        }
        charge.previousCut = result.center;
        boolean damaged = SukunaCleaveDamage.strike(player, result.center, normal, forward, result.radius,
                cross ? forward.cross(secondStroke).normalize() : null, SukunaSlashStyle.crossScale(seed));
        if (charge.trackFragments && !charge.addTargets(result.bodies)) {
            message(player, "本轮已产生大量碎片，可重新选择继续切割；现有碎片保留物理运动。");
            charge.lastMessage = now;
        }
        if (!result.success && !result.message.isBlank() && now - charge.lastMessage >= 40) {
            message(player, result.message); charge.lastMessage = now;
        }
        emit(aim.level, player, new SukunaCut(result.center, forward, stroke, (float) Math.min(256, result.radius * 2.25),
                1, result.success || damaged, seed, cross));
    }

    private static Aim releaseAim(ServerPlayer player, Aim previous) {
        Vec3 forward = player.getLookAngle().normalize(), origin = player.getEyePosition();
        Vec3 horizontal = new Vec3(-forward.z, 0, forward.x);
        if (horizontal.lengthSqr() < 1e-6) horizontal = new Vec3(1, 0, 0);
        horizontal = horizontal.normalize();
        Vec3 vertical = horizontal.cross(forward).normalize();
        HumanoidArm arm = previous.hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        if (arm == HumanoidArm.LEFT) horizontal = horizontal.scale(-1);
        return new Aim(previous.player, previous.level, previous.hand, previous.kind, origin, forward,
                origin.add(forward.scale(6)), vertical.scale(.94).add(horizontal.scale(.34)).normalize(),
                null, previous.stack, previous.slot);
    }

    private static CutResult visualCut(PhysicsTarget target, boolean burst, int seed, Vec3 stroke, Vec3 forward) {
        Vec3 center = target.center;
        double radius = target.radius;
        if (burst) {
            var random = new java.util.Random(seed);
            double spread = Math.min(4, target.radius * .45);
            center = center.add(stroke.scale((random.nextDouble() - .5) * spread))
                    .add(forward.scale((random.nextDouble() - .5) * spread));
            radius = Math.max(1, radius - center.distanceTo(target.center));
        }
        return new CutResult(false, "", List.of(), center, radius);
    }

    private static PhysicsTarget ordinaryTarget(ServerPlayer player) {
        return new PhysicsTarget(null, CleaveTerrain.point(player, CLEAVE_REACH), CUT_RADIUS, 0);
    }

    private static void dismantle(ServerPlayer player, Aim aim) {
        Vec3 end = aim.origin.add(aim.forward.scale(REACH));
        List<LivingEntity> targets = aim.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(aim.origin, end).inflate(1.5), entity -> TechniquePlayerRules.canAffect(player,entity));
        targets.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(aim.origin)));
        int hit = 0;
        for (LivingEntity target : targets) {
            if (hit >= 16 || !valid(player, aim)) break;
            AABB body = target.getBoundingBox();
            double distance = Math.sqrt(body.distanceToSqr(aim.origin));
            if (distance > REACH) continue;
            // A little more angular forgiveness at distance, with a bounded margin.
            AABB box = body.inflate(.70 + Math.min(.70, distance * .028));
            Vec3 entry = box.contains(aim.origin) ? aim.origin : box.clip(aim.origin, end).orElse(null);
            if (entry == null) continue;
            Vec3 visiblePoint = new Vec3(dev.jujutsuvfx.platform.Numbers.clamp(entry.x, body.minX, body.maxX),
                    dev.jujutsuvfx.platform.Numbers.clamp(entry.y, body.minY, body.maxY), dev.jujutsuvfx.platform.Numbers.clamp(entry.z, body.minZ, body.maxZ));
            // Visibility ends on the real body, so forgiving aim cannot reach through a wall.
            if (visiblePoint.distanceToSqr(aim.origin) > 1e-8 && aim.level.clip(new ClipContext(aim.origin, visiblePoint,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            Vec3 point = target.getBoundingBox().getCenter().subtract(aim.forward.scale(target.getBbWidth() * .42));
            float length = dev.jujutsuvfx.platform.Numbers.clamp(Math.max(target.getBbHeight(), target.getBbWidth()) * 2.2f, 3, 8);
            if (!TechniqueDamageRules.hurt(target, SukunaCleaveDamage.source(player), DISMANTLE_DAMAGE, TechniqueDamage.DISMANTLE)) continue;
            hit++; emitDismantle(player, aim, point, length, true);
        }
        if (hit == 0) emitDismantle(player, aim, aim.origin.add(aim.forward.scale(6)), 5, false);
    }
    private static void emitDismantle(ServerPlayer player, Aim aim, Vec3 center, float length, boolean impact) {
        int seed = player.getRandom().nextInt();
        emit(aim.level, player, new SukunaCut(center, aim.forward,
                SukunaSlashStyle.rotateStroke(seed, aim.stroke, aim.forward), length, 0, impact, seed, SukunaSlashStyle.isCross(seed)));
    }
    private static void emit(ServerLevel level, ServerPlayer caster, SukunaCut packet) {
        double reach = 96 + packet.length() * .70;
        for (ServerPlayer observer : level.players())
            if (dev.jujutsuvfx.platform.PacketDistributor.hasChannel(observer.connection,SukunaCut.TYPE)
                    && (observer == caster || observer.distanceToSqr(packet.center()) < reach * reach))
                PacketDistributor.sendToPlayer(observer, packet);
    }
    private static void cooldown(ServerPlayer player, int ticks) {
        player.getCooldowns().addCooldown(VfxItems.CLEAVE.get(), ticks);
        player.getCooldowns().addCooldown(VfxItems.DISMANTLE.get(), ticks);
    }
    private static void message(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text), true); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PENDING.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()); CHARGES.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()); PHYSICS_BUDGET.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
    }
    private record CutResult(boolean success, String message, List<UUID> bodies, Vec3 center, double radius) {}
    /** World-space fallback; no moving-body IDs exist in the Forge 1.20.1 backend. */
    private static final class Physics {
        static Vec3 eye(ServerPlayer player) { return player.getEyePosition(); }
        static PhysicsTarget point(ServerPlayer player,double reach,double radius) {
            return new PhysicsTarget(null,CleaveTerrain.point(player,reach),radius,0);
        }
        static PhysicsTarget acquire(ServerPlayer player,double reach,double radius) { return point(player,reach,radius); }
        static PhysicsTarget inspect(ServerPlayer player,UUID id) { return null; }
        static Vec3 direction(ServerPlayer player,Vec3 vector) { return vector.normalize(); }
        static CutResult cut(ServerPlayer player,PhysicsTarget target,Vec3 normal,Vec3 forward,
                             boolean cross,Vec3 secondNormal,double secondaryScale,Integer scatterSeed,Vec3 previous) {
            boolean result=CleaveTerrain.cut(player,target.center,normal,target.radius,cross?secondNormal:null,secondaryScale);
            return new CutResult(result,"",List.of(),target.center,target.radius);
        }
    }
}
