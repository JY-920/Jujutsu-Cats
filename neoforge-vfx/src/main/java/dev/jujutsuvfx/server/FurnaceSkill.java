package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.network.FurnaceControl;
import dev.jujutsuvfx.network.FurnaceState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** A bounded cast slot per player. Damage happens only at the authoritative arrow contact. */
@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class FurnaceSkill {
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "furnace"));
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private record Slot(UUID owner,int session) {}
    private static final class State {
        final Map<Slot, Cast> active = new HashMap<>(), damaging = new HashMap<>();
        boolean stopped;
    }
    private static final class Cast {
        final int session; final float radius; final long born;
        final ResourceKey<Level> dimension;
        Vec3 position, direction;
        float traveled;long launchedAt;
        boolean canceled;
        Cast(FurnaceControl packet, ServerPlayer owner, long now) {
            session = packet.session(); radius = packet.radius(); born = now; dimension = owner.level().dimension();
        }
    }
    public static void handle(FurnaceControl packet, Player player) {
        if (!(player instanceof ServerPlayer owner) || owner.getServer() == null
                || !owner.connection.hasChannel(FurnaceState.TYPE)) return;
        State state = STATES.computeIfAbsent(owner.getServer(), key -> new State());
        if (packet.action() == FurnaceControl.CANCEL) {
            cancel(state, owner, packet.session(), false);
            return;
        }
        if (packet.action() != FurnaceControl.CAST) return;
        if (!Float.isFinite(packet.radius()) || packet.radius() < 3 || packet.radius() > FurnaceFlight.MAX_RADIUS
                || !owner.isAlive() || owner.isSpectator()) {
            send(owner, packet.session(), FurnaceState.CANCELED, Vec3.ZERO, Vec3.ZERO); return;
        }
        Slot slot=new Slot(owner.getUUID(),packet.session());
        if(state.active.containsKey(slot)||state.damaging.containsKey(slot))return;
        state.active.put(slot,new Cast(packet,owner,owner.getServer().getTickCount()));
        send(owner, packet.session(), FurnaceState.ACCEPTED, FurnaceFlight.bow(owner, 1), owner.getLookAngle());
    }
    private static void cancel(State state, ServerPlayer owner, int session, boolean anySession) {
        Slot slot=new Slot(owner.getUUID(),session);
        Cast active = state.active.get(slot);
        if (active != null && (anySession || active.session == session)) {
            state.active.remove(slot, active); active.canceled = true;
            send(owner, active.session, FurnaceState.CANCELED, Vec3.ZERO, Vec3.ZERO);
        }
        // hurt() can call a mod which clears or replaces this skill synchronously.
        Cast damaging = state.damaging.get(slot);
        if (damaging != null && !damaging.canceled && (anySession || damaging.session == session)) {
            damaging.canceled = true;
            send(owner, damaging.session, FurnaceState.CANCELED, Vec3.ZERO, Vec3.ZERO);
        }
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        State state = STATES.get(server); if (state == null) return;
        var casts = state.active;
        for (var entry : Map.copyOf(casts).entrySet()) {
            Cast cast = entry.getValue();
            if (casts.get(entry.getKey()) != cast) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey().owner());
            if (owner == null || !ownerValid(state, owner, cast)) {
                casts.remove(entry.getKey(), cast); cast.canceled = true;
                if (owner != null) send(owner, cast.session, FurnaceState.CANCELED, Vec3.ZERO, Vec3.ZERO);
                continue;
            }
            if (server.getTickCount() - cast.born < FurnaceFlight.CHARGE_TICKS) { observe(owner,cast,false);continue; }
            if (cast.position == null) {
                Vec3 eye = owner.getEyePosition(), direction = owner.getLookAngle();
                Vec3 muzzle = FurnaceFlight.bow(owner, 1).add(direction.scale(2.8));
                var clearance = LimitlessTechnique.trace(owner.level(), owner, eye, muzzle);
                Vec3 aim = LimitlessTechnique.trace(owner.level(), owner, eye, eye.add(direction.scale(FurnaceFlight.RANGE))).point();
                cast.position = clearance.point();cast.launchedAt=server.getTickCount();
                cast.direction = aim.subtract(cast.position).lengthSqr() < .01 ? direction : aim.subtract(cast.position).normalize();
                send(owner, cast.session, FurnaceState.LAUNCHED, cast.position, cast.direction);
                if (clearance.hit()) { casts.remove(entry.getKey(), cast); impact(state, owner, cast); }
                continue;
            }
            Vec3 end = cast.position.add(cast.direction.scale(Math.min(FurnaceFlight.SPEED / 20,
                    Math.max(0, FurnaceFlight.RANGE - cast.traveled))));
            // A fire arrow cannot force distant chunks to load.
            if (!LimitlessTechnique.loadedSegment(owner.serverLevel(), cast.position, end)) {
                cancel(state, owner, cast.session, false); continue;
            }
            var contact = LimitlessTechnique.trace(owner.level(), owner, cast.position, end);
            cast.traveled += cast.position.distanceTo(contact.point());
            cast.position = contact.point();
            if (contact.hit() || cast.traveled >= FurnaceFlight.RANGE) {
                // Remove before calling modded damage handlers, so reentrant cancellation cannot repeat an explosion.
                casts.remove(entry.getKey(), cast); impact(state, owner, cast);
            }else observe(owner,cast,false);
        }
    }
    private static void impact(State state, ServerPlayer owner, Cast cast) {
        if (!impactCurrent(state, owner, cast)) return;
        state.damaging.put(new Slot(owner.getUUID(),cast.session), cast);
        try {
            send(owner, cast.session, FurnaceState.IMPACT, cast.position, cast.direction);
            DamageSource source = new DamageSource(owner.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DAMAGE), owner, owner);
            var level = owner.serverLevel();
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cast.position, cast.position).inflate(cast.radius),
                    e -> TechniquePlayerRules.canAffect(owner,e))) {
                if (!impactCurrent(state, owner, cast)) return;
                if (target.level() != level || !target.isAlive() || target.isRemoved()) continue;
                AABB box = target.getBoundingBox();
                Vec3 nearest = new Vec3(Math.clamp(cast.position.x, box.minX, box.maxX),
                        Math.clamp(cast.position.y, box.minY, box.maxY), Math.clamp(cast.position.z, box.minZ, box.maxZ));
                double distance = nearest.distanceTo(cast.position);
                if (distance > cast.radius) continue;
                Vec3 sample = box.getCenter();
                if (!LimitlessTechnique.loadedSegment(level, cast.position, sample)) continue;
                if (distance > .15 && level.clip(new ClipContext(cast.position, sample, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS) continue;
                float damage = 60 + 140 * (1 - (float) distance / cast.radius);
                boolean hit = TechniqueDamageRules.hurt(target, source, damage, TechniqueDamage.FURNACE);
                if (!impactCurrent(state, owner, cast)) return;
                if (target.level() == level && target.isAlive() && !target.isRemoved()) {
                    if (hit) target.igniteForSeconds(4);
                    DomainFreeze.hold(target);
                }
            }
            // The original walls must still exist throughout the damage / line-of-sight pass.
            SkillTerrain.furnace(owner, source, cast.position, cast.radius, () -> impactCurrent(state, owner, cast));
        } finally {
            state.damaging.remove(new Slot(owner.getUUID(),cast.session), cast);
        }
    }
    private static boolean ownerValid(State state, ServerPlayer owner, Cast cast) {
        return !state.stopped && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
                && owner.level().dimension().equals(cast.dimension) && owner.getServer() != null
                && owner.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner;
    }
    private static boolean impactCurrent(State state, ServerPlayer owner, Cast cast) {
        return !cast.canceled && ownerValid(state, owner, cast) && !state.active.containsKey(new Slot(owner.getUUID(),cast.session));
    }
    private static void observe(ServerPlayer owner,Cast cast,boolean impact) {
        int age=(int)(owner.getServer().getTickCount()-cast.born);
        int phase=impact?dev.jujutsuvfx.core.CatSpell.IMPACT:cast.position!=null?dev.jujutsuvfx.core.CatSpell.FLIGHT:dev.jujutsuvfx.core.CatSpell.CHARGE;
        int stage=impact?0:cast.position==null?age:(int)(owner.getServer().getTickCount()-cast.launchedAt);
        PlayerSpellSync.publish(owner,cast.session,dev.jujutsuvfx.core.CatSpell.FURNACE,false,phase,age,stage,
                impact?Math.round(FurnaceFlight.AFTERGLOW*20):100,cast.radius,
                cast.position==null?FurnaceFlight.bow(owner,1):cast.position,
                cast.direction==null?owner.getLookAngle():cast.direction,owner.getEyePosition(),0,0,null,cast.radius);
    }
    private static void send(ServerPlayer owner, int session, int phase, Vec3 point, Vec3 direction) {
        if(phase==FurnaceState.CANCELED)PlayerSpellSync.end(owner,dev.jujutsuvfx.core.CatSpell.FURNACE,session);
        else{
            State state=STATES.get(owner.getServer());Slot slot=new Slot(owner.getUUID(),session);
            Cast cast=state==null?null:state.active.get(slot);if(cast==null&&state!=null)cast=state.damaging.get(slot);
            if(cast!=null)observe(owner,cast,phase==FurnaceState.IMPACT);
        }
        PacketDistributor.sendToPlayer(owner, new FurnaceState(session, phase, owner.level().getGameTime(), point, direction));
        int kind=dev.jujutsuvfx.network.SkillGesture.FURNACE;
        if(phase==FurnaceState.ACCEPTED)TechniqueGestures.start(owner,session,kind,null,false);
        else if(phase==FurnaceState.LAUNCHED)TechniqueGestures.release(owner,session,kind);
        else if(phase==FurnaceState.CANCELED)TechniqueGestures.cancel(owner,session,kind);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        State state = STATES.remove(event.getServer());
        if (state != null) { state.stopped = true; state.active.clear(); state.damaging.clear(); }
    }
}
