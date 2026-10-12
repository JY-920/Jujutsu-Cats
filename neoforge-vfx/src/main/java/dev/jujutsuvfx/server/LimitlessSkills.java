package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.core.SweptSphere;
import dev.jujutsuvfx.network.TechniqueControl;
import dev.jujutsuvfx.network.TechniqueState;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.effect.VfxEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Independent Blue, Red and Purple slots. Flight, carving and swept hits share one server trajectory. */
@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class LimitlessSkills {
    private static final ResourceKey<DamageType> BLUE_DAMAGE = damageKey("limitless_blue");
    private static final ResourceKey<DamageType> RED_DAMAGE = damageKey("limitless_red");
    private static final ResourceKey<DamageType> PURPLE_DAMAGE = damageKey("hollow_purple");
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final long PURPLE_INPUT_GRACE = PurpleCastClock.ticks(6);

    private record Slot(UUID owner, int kind, int session) {}
    private static final class State {
        final Map<Slot, Cast> active = new HashMap<>();
        // A callback may cancel/recast a skill while hurt() is executing. Keep that cancellation addressable.
        final Map<Slot, Cast> damaging = new HashMap<>();
        final PurpleCarver.Budget carvingBudget = new PurpleCarver.Budget();
        int nextCast;
        boolean stopped;
    }
    private static final class Cast {
        final int session, kind, ticks;
        final float radius;
        final long born, purpleBorn;
        final ResourceKey<Level> dimension;
        final Map<UUID, Long> nextDamage = new HashMap<>();
        final InteractionHand guideHand;
        final ItemStack controlStack;
        final Set<UUID> struck = new HashSet<>();
        final ShotChunks chunks;
        Vec3 point, direction;
        dev.jujutsuvfx.core.BlueGuidance blueGuide;
        float traveled, shotRadius;
        int stalled, chargedTicks, purpleBranch, observerLaunchAge=-1;
        long launchedAt, committedAt = Long.MAX_VALUE, purpleDeadline = Long.MAX_VALUE;
        long releaseRequestedAt = Long.MAX_VALUE;
        long redLaunchAt = Long.MAX_VALUE;
        long redFullAt = Long.MAX_VALUE;
        Vec3 beamOrigin;
        boolean largeRed, redFireConfirmed, amplifiedPurple;
        PurpleCarver carver;
        PurpleMovementLock movementLock;
        boolean launched, canceled, parked, aimConfirmed;

        Cast(TechniqueControl packet, ServerPlayer owner, long now, long inputTime) {
            session = packet.session(); kind = packet.kind(); ticks = packet.ticks();
            radius = packet.radius(); born = now; purpleBorn = inputTime; dimension = owner.level().dimension();
            guideHand = packet.action() == TechniqueControl.GUIDE_MAIN || packet.action() == TechniqueControl.CHARGE_MAIN
                    ? InteractionHand.MAIN_HAND
                    : packet.action() == TechniqueControl.GUIDE_OFF || packet.action() == TechniqueControl.CHARGE_OFF
                    ? InteractionHand.OFF_HAND : null;
            controlStack = guideHand == null ? ItemStack.EMPTY : owner.getItemInHand(guideHand);
            shotRadius = kind == LimitlessTechnique.PURPLE ? LimitlessTechnique.PURPLE_MIN_RADIUS
                    : LimitlessTechnique.shotRadius(kind, radius);
            chunks = kind == LimitlessTechnique.BLUE ? null : new ShotChunks(owner.serverLevel());
            point = kind == LimitlessTechnique.BLUE
                    ? guideHand == null ? LimitlessTechnique.blueFocus(owner.level(), owner) : LimitlessTechnique.blueMuzzle(owner)
                    : LimitlessTechnique.shotMuzzle(owner, kind, 0, 1);
            direction = owner.getLookAngle();
            if(kind==LimitlessTechnique.BLUE&&guideHand!=null)blueGuide=new dev.jujutsuvfx.core.BlueGuidance(point,owner.getEyePosition(),direction);
        }
        boolean heldCharge() { return kind == LimitlessTechnique.PURPLE && guideHand != null; }
        boolean heldRed() { return kind == LimitlessTechnique.RED && guideHand != null; }
        boolean committed() { return ((kind == LimitlessTechnique.PURPLE && purpleBranch != 0)
                || (kind == LimitlessTechnique.RED && largeRed)) && !launched && !canceled; }
        void unlock() { if (movementLock != null) { movementLock.release(); movementLock = null; } }
        void close() { unlock(); if (chunks != null) chunks.close(); if (carver != null) carver.close(); }
    }

    private LimitlessSkills() {}

    /** Capture before enqueueWork: queued server tasks must not become extra time holding the button. */
    public static long purpleInputTime(Player player) {
        return player instanceof ServerPlayer owner && owner.getServer() != null
                ? PurpleCastClock.now(owner.getServer()) : 0;
    }

    public static void handle(TechniqueControl packet, Player player) {
        handle(packet, player, purpleInputTime(player));
    }

    public static void handle(TechniqueControl packet, Player player, long receivedNanos) {
        if (!(player instanceof ServerPlayer owner) || owner.getServer() == null
                || !owner.connection.hasChannel(TechniqueState.TYPE)) return;
        if (packet.kind() < LimitlessTechnique.BLUE || packet.kind() > LimitlessTechnique.PURPLE) return;
        long inputTime = Math.clamp(receivedNanos, 0, PurpleCastClock.now(owner.getServer()));
        State state = STATES.computeIfAbsent(owner.getServer(), key -> new State());
        Slot slot = new Slot(owner.getUUID(), packet.kind(), packet.session());
        if (packet.action() == TechniqueControl.CANCEL) {
            cancel(state, slot, owner, packet.session(), false);
            return;
        }
        if (packet.action() == TechniqueControl.PARK) {
            Cast cast = state.active.get(slot);
            if (cast == null) cast = state.damaging.get(slot);
            if (cast != null && cast.session == packet.session()) park(owner, cast);
            return;
        }
        if (packet.action() == TechniqueControl.COMMIT_SMALL || packet.action() == TechniqueControl.COMMIT_LARGE) {
            Cast cast = state.active.get(slot);
            if (cast != null && cast.session == packet.session() && redControlValid(owner, cast)) {
                if (!packet.validAim()) return;
                if (packet.action() == TechniqueControl.COMMIT_SMALL) requestRedRelease(owner, cast, inputTime, packet);
                else if (inputTime + PurpleCastClock.ticks(2) >= cast.purpleBorn + PurpleCastClock.ticks(LimitlessTechnique.RED_PREP_TICKS))
                    commitRedBeam(owner, cast);
                return;
            }
            if (cast != null && cast.session == packet.session() && purpleControlValid(owner, cast)) {
                if (!packet.validAim()) cancel(state, slot, owner, cast.session, false);
                else commitPurple(owner, cast, packet.action() == TechniqueControl.COMMIT_LARGE, inputTime, packet);
            }
            return;
        }
        if (packet.action() == TechniqueControl.RED_FIRE) {
            Cast cast = state.active.get(slot);
            if (cast != null && cast.session == packet.session() && redControlValid(owner, cast)
                    && cast.largeRed && !cast.redFireConfirmed && packet.validAim()
                    && inputTime + PurpleCastClock.ticks(2) >= cast.redLaunchAt) {
                aimRed(owner, cast, packet);
                cast.redFireConfirmed = true;
            }
            return;
        }
        if (packet.action() == TechniqueControl.RELEASE) {
            Cast cast = state.active.get(slot);
            if (cast != null && cast.session == packet.session()) requestPurpleRelease(owner, cast, inputTime);
            return;
        }
        boolean guided = packet.action() == TechniqueControl.GUIDE_MAIN || packet.action() == TechniqueControl.GUIDE_OFF;
        boolean charged = packet.action() == TechniqueControl.CHARGE_MAIN || packet.action() == TechniqueControl.CHARGE_OFF;
        if (packet.action() != TechniqueControl.CAST && !guided && !charged) return;
        Cast current = state.active.get(slot);
        if (current != null && current.committed()) {
            // A fresh press cannot replace a branch that is already synthesizing. Explicit CANCEL
            // remains available to /rift stop, /rift clear and lifecycle cleanup.
            if (current.session != packet.session())
                send(owner, packet.session(), packet.kind(), TechniqueState.CANCELED, 0, Vec3.ZERO, Vec3.ZERO);
            return;
        }
        InteractionHand hand = packet.action() == TechniqueControl.GUIDE_OFF || packet.action() == TechniqueControl.CHARGE_OFF
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean guidanceValid = !guided || (packet.kind() == LimitlessTechnique.BLUE
                && packet.ticks() == LimitlessTechnique.DEFAULT_BLUE_SECONDS * 20
                && packet.radius() == LimitlessTechnique.DEFAULT_BLUE_RADIUS && VfxItems.isBlue(owner.getItemInHand(hand)));
        boolean chargeValid = !charged || (packet.kind() == LimitlessTechnique.PURPLE
                && packet.radius() == LimitlessTechnique.PURPLE_RADIUS && VfxItems.isPurple(owner.getItemInHand(hand)))
                || (packet.kind() == LimitlessTechnique.RED && packet.radius() == LimitlessTechnique.DEFAULT_RED_RADIUS
                && VfxItems.isRed(owner.getItemInHand(hand)));
        boolean durationValid = packet.kind() == LimitlessTechnique.BLUE
                ? packet.ticks() >= 40 && packet.ticks() <= 600 : packet.ticks() == 0;
        boolean aimValid = packet.kind() != LimitlessTechnique.PURPLE || packet.action() != TechniqueControl.CAST || packet.validAim();
        if (!Float.isFinite(packet.radius()) || packet.radius() < 3
                || packet.radius() > LimitlessTechnique.MAX_RADIUS || !durationValid || !guidanceValid || !chargeValid || !aimValid
                || (packet.kind() == LimitlessTechnique.PURPLE && packet.radius() != LimitlessTechnique.PURPLE_RADIUS)
                || !owner.isAlive() || owner.isSpectator()) {
            send(owner, packet.session(), packet.kind(), TechniqueState.CANCELED, 0, Vec3.ZERO, Vec3.ZERO);
            return;
        }
        if(state.active.containsKey(slot)||state.damaging.containsKey(slot))return;
        Cast cast = new Cast(packet, owner, owner.getServer().getTickCount(), inputTime);
        if (!owner.serverLevel().isLoaded(BlockPos.containing(cast.point))) {
            cast.close();
            send(owner, cast, TechniqueState.CANCELED, 0);
            return;
        }
        state.active.put(slot, cast);
        if(cast.kind!=LimitlessTechnique.BLUE)TechniqueGestures.interrupt(owner);
        send(owner, cast, TechniqueState.ACCEPTED,
                cast.kind == LimitlessTechnique.BLUE ? cast.ticks : cast.kind == LimitlessTechnique.PURPLE
                        ? PurpleCastClock.remaining(cast.purpleBorn + PurpleCastClock.ticks(LimitlessTechnique.PURPLE_PREP_TICKS),
                        PurpleCastClock.now(owner.getServer())) : LimitlessTechnique.chargeTicks(cast.kind));
        if (cast.kind == LimitlessTechnique.PURPLE && !cast.heldCharge())
            commitPurple(owner, cast, true, inputTime, packet);
    }

    private static Cast latest(State state,ServerPlayer owner,int kind){
        return state.active.entrySet().stream().filter(e->e.getKey().owner().equals(owner.getUUID())&&e.getKey().kind()==kind)
                .map(Map.Entry::getValue).filter(c->!c.canceled&&!c.launched)
                .max(java.util.Comparator.comparingLong((Cast c)->c.born).thenComparingInt(c->c.session)).orElse(null);
    }
    public static void releaseBlue(ServerPlayer owner,ItemStack stack){
        State state=STATES.get(owner.getServer());if(state==null)return;
        for(var entry:java.util.List.copyOf(state.active.entrySet()))if(entry.getKey().owner().equals(owner.getUUID())
                &&entry.getKey().kind()==LimitlessTechnique.BLUE&&entry.getValue().controlStack==stack)park(owner,entry.getValue());
        for(var entry:java.util.List.copyOf(state.damaging.entrySet()))if(entry.getKey().owner().equals(owner.getUUID())
                &&entry.getKey().kind()==LimitlessTechnique.BLUE&&entry.getValue().controlStack==stack)park(owner,entry.getValue());
    }
    public static void releasePurple(ServerPlayer owner,ItemStack stack){
        State state=STATES.get(owner.getServer());if(state==null)return;
        Cast cast=latest(state,owner,LimitlessTechnique.PURPLE);
        if(cast!=null&&cast.controlStack==stack)requestPurpleRelease(owner,cast,PurpleCastClock.now(owner.getServer()));
    }
    public static void releaseRed(ServerPlayer owner,ItemStack stack){
        State state=STATES.get(owner.getServer());if(state==null)return;
        Cast cast=latest(state,owner,LimitlessTechnique.RED);
        if(cast!=null&&cast.controlStack==stack&&redControlValid(owner,cast)&&!cast.largeRed)
            cast.releaseRequestedAt=Math.min(cast.releaseRequestedAt,PurpleCastClock.now(owner.getServer()));
    }

    private static boolean redControlValid(ServerPlayer owner, Cast cast) {
        return cast.heldRed() && !cast.launched && !cast.canceled && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
                && owner.level().dimension().equals(cast.dimension)
                && (!owner.isUsingItem() || owner.getUsedItemHand() == cast.guideHand)
                && owner.getItemInHand(cast.guideHand) == cast.controlStack && VfxItems.isRed(cast.controlStack);
    }

    private static void aimRed(ServerPlayer owner, Cast cast, TechniqueControl aim) {
        cast.direction = aim == null ? owner.getLookAngle().normalize()
                : Vec3.directionFromRotation(aim.pitch(), aim.yaw()).normalize();
        HumanoidArm arm = cast.guideHand == InteractionHand.OFF_HAND ? owner.getMainArm().getOpposite() : owner.getMainArm();
        cast.point = LimitlessTechnique.redMuzzle(owner, 1, cast.direction, arm == HumanoidArm.RIGHT ? 1 : -1);
    }

    private static void requestRedRelease(ServerPlayer owner, Cast cast, long inputTime, TechniqueControl aim) {
        if (!redControlValid(owner, cast) || cast.largeRed || cast.redLaunchAt != Long.MAX_VALUE) return;
        long released = Math.min(inputTime, cast.releaseRequestedAt);
        if (released - cast.purpleBorn >= PurpleCastClock.ticks(LimitlessTechnique.RED_PREP_TICKS)) {
            commitRedBeam(owner, cast);
            return;
        }
        cast.redLaunchAt = Math.max(cast.purpleBorn, released);
        cast.redFireConfirmed = true;
        aimRed(owner, cast, aim);
    }

    private static void commitRedBeam(ServerPlayer owner, Cast cast) {
        if (!redControlValid(owner, cast) || cast.largeRed || cast.redLaunchAt != Long.MAX_VALUE) return;
        cast.largeRed = true;
        cast.shotRadius = LimitlessTechnique.RED_BEAM_RADIUS;
        cast.redLaunchAt = cast.purpleBorn + PurpleCastClock.ticks(LimitlessTechnique.RED_BEAM_CHARGE_TICKS);
        cast.releaseRequestedAt = Long.MAX_VALUE;
        send(owner, cast, TechniqueState.RED_BEAM, PurpleCastClock.remaining(cast.redLaunchAt, PurpleCastClock.now(owner.getServer())));
    }

    private static boolean chargeRed(State state, Slot slot, ServerPlayer owner, Cast cast) {
        if (!redControlValid(owner, cast)) { cancel(state, slot, owner, cast.session, false); return false; }
        long now = PurpleCastClock.now(owner.getServer());
        if (cast.redLaunchAt == Long.MAX_VALUE) {
            if (cast.releaseRequestedAt != Long.MAX_VALUE) {
                if (now >= cast.releaseRequestedAt + PURPLE_INPUT_GRACE)
                    requestRedRelease(owner, cast, cast.releaseRequestedAt, null);
            } else if (!owner.isUsingItem() && now - cast.purpleBorn > PurpleCastClock.ticks(2)) {
                cast.releaseRequestedAt = now;
            } else if (now >= cast.purpleBorn + PurpleCastClock.ticks(LimitlessTechnique.RED_PREP_TICKS) + PURPLE_INPUT_GRACE)
                commitRedBeam(owner, cast);
        }
        if (!cast.redFireConfirmed) aimRed(owner, cast, null);
        cast.chargedTicks = (int) Math.clamp((now - cast.purpleBorn) / PurpleCastClock.ticks(1), 0, LimitlessTechnique.RED_BEAM_CHARGE_TICKS);
        if (cast.largeRed) {
            cast.chunks.prepare(cast.point, cast.direction, cast.shotRadius, LimitlessTechnique.RED_BEAM_RANGE);
            // A same-frame look payload is primary. Missing input can only add a short grace,
            // never restart charge or leave the cannon stuck indefinitely.
            if (!cast.redFireConfirmed && now >= cast.redLaunchAt + PURPLE_INPUT_GRACE) cast.redFireConfirmed = true;
        }
        return cast.redFireConfirmed && now >= cast.redLaunchAt;
    }

    private static boolean purpleControlValid(ServerPlayer owner, Cast cast) {
        return cast.heldCharge() && !cast.launched && !cast.canceled
                && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
                && owner.level().dimension().equals(cast.dimension)
                && owner.getItemInHand(cast.guideHand) == cast.controlStack && VfxItems.isPurple(cast.controlStack);
    }

    private static void requestPurpleRelease(ServerPlayer owner, Cast cast, long inputTime) {
        if (!purpleControlValid(owner, cast) || cast.committed()) return;
        // Vanilla releaseUsing can run before the explicit branch payload queued in the same tick.
        // Preserve its arrival as a fallback, giving that irreversible branch request priority.
        cast.releaseRequestedAt = Math.min(cast.releaseRequestedAt, Math.max(cast.purpleBorn, inputTime));
    }

    private static void commitPurple(ServerPlayer owner, Cast cast, boolean large, long inputTime) {
        commitPurple(owner, cast, large, inputTime, null);
    }

    private static void commitPurple(ServerPlayer owner, Cast cast, boolean large, long inputTime, TechniqueControl aim) {
        if (cast.kind != LimitlessTechnique.PURPLE || cast.launched || cast.canceled) return;
        long now = PurpleCastClock.now(owner.getServer());
        if (cast.committed()) {
            // The automatic fallback may precede a delayed explicit commit. Accept its first look
            // sample once, without changing the chosen branch or restarting any stage deadline.
            if (!cast.aimConfirmed && aim != null && (cast.purpleBranch == TechniqueState.PURPLE_LARGE) == large) {
                anchorPurple(owner, cast, aim, now);
                send(owner, cast, cast.purpleBranch, PurpleCastClock.remaining(cast.purpleDeadline, now));
            }
            return;
        }
        cast.committedAt = Math.clamp(inputTime, cast.purpleBorn, now);
        // A forged early LARGE request can select the branch, but cannot skip its preparation time.
        // Command casts deliberately enter the large synthesis directly, without the held PREP stage.
        if (large && cast.heldCharge()) cast.committedAt = Math.max(cast.committedAt,
                cast.purpleBorn + PurpleCastClock.ticks(LimitlessTechnique.PURPLE_PREP_TICKS));
        cast.purpleBranch = large ? TechniqueState.PURPLE_LARGE : TechniqueState.PURPLE_SMALL;
        cast.chargedTicks = large ? LimitlessTechnique.PURPLE_PREP_TICKS
                : (int) Math.clamp((cast.committedAt - cast.purpleBorn) / PurpleCastClock.TICK_NANOS,
                0, LimitlessTechnique.PURPLE_PREP_TICKS - 1);
        // Latch the authoritative buff only when a held large branch becomes irreversible.
        // Expiry during synthesis cannot shrink it; gaining a buff after commit cannot enlarge it.
        cast.amplifiedPurple = large && cast.heldCharge() && VfxEffects.amplified(owner);
        cast.shotRadius = cast.amplifiedPurple ? LimitlessTechnique.PURPLE_AMPLIFIED_RADIUS
                : large ? LimitlessTechnique.PURPLE_RADIUS : LimitlessTechnique.PURPLE_MIN_RADIUS;
        cast.purpleDeadline = cast.committedAt + PurpleCastClock.ticks(LimitlessTechnique.purpleLaunchTicks(large));
        anchorPurple(owner, cast, aim, now);
        send(owner, cast, cast.purpleBranch, PurpleCastClock.remaining(cast.purpleDeadline, now));
    }

    private static void anchorPurple(ServerPlayer owner, Cast cast, TechniqueControl aim, long now) {
        float yaw = aim == null ? owner.getYRot() : aim.yaw();
        float pitch = aim == null ? owner.getXRot() : aim.pitch();
        cast.direction = Vec3.directionFromRotation(pitch, yaw).normalize();
        // Only the view angles come from input. Origin, obstruction clipping and hits remain server-owned.
        cast.point = LimitlessTechnique.purpleMuzzle(owner, cast.shotRadius, 1, cast.direction);
        cast.aimConfirmed = aim != null;
        cast.unlock();
        cast.movementLock = new PurpleMovementLock(owner,
                Math.max(now, cast.purpleDeadline) + PurpleCastClock.ticks(20), yaw, pitch);
    }

    /** Called only by the movement mixin on the server thread; never changes an unrelated domain lock. */
    public static boolean holdPurplePlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) return false;
        State state = STATES.get(server);
        if (state == null) return false;
        Cast cast = latest(state,player,LimitlessTechnique.PURPLE);
        if (cast == null || !cast.committed() || cast.movementLock == null || !ownerValid(state, player, cast)
                || (cast.heldCharge() && (player.getItemInHand(cast.guideHand) != cast.controlStack
                || !VfxItems.isPurple(cast.controlStack)))) return false;
        return cast.movementLock.hold(player, PurpleCastClock.now(server));
    }

    private static void park(ServerPlayer owner, Cast cast) {
        if (cast.kind != LimitlessTechnique.BLUE || cast.guideHand == null || cast.parked || cast.canceled) return;
        cast.parked = true;
        cast.blueGuide.release();
        send(owner, cast, TechniqueState.BLUE_PARKED,
                (int) Math.max(0, cast.born + cast.ticks - owner.getServer().getTickCount()));
    }

    private static void cancel(State state, Slot slot, ServerPlayer owner, int session, boolean anySession) {
        Cast active = state.active.get(slot);
        if (active != null && (anySession || active.session == session)) {
            state.active.remove(slot, active);
            active.canceled = true;
            active.close();
            send(owner, active, TechniqueState.CANCELED, 0);
        }
        Cast damaging = state.damaging.get(slot);
        if (damaging != null && !damaging.canceled && (anySession || damaging.session == session)) {
            damaging.canceled = true;
            damaging.close();
            send(owner, damaging, TechniqueState.CANCELED, 0);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        State state = STATES.get(server);
        if (state == null) return;
        long now = server.getTickCount();
        state.carvingBudget.reset((int) state.active.values().stream().filter(c -> c.amplifiedPurple).count());
        var work = new java.util.ArrayList<>(Map.copyOf(state.active).entrySet());
        int first = work.isEmpty() ? 0 : Math.floorMod(state.nextCast++, work.size());
        for (int i = 0; i < work.size(); i++) {
            var entry = work.get((first + i) % work.size());
            Slot slot = entry.getKey(); Cast cast = entry.getValue();
            if (state.active.get(slot) != cast) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(slot.owner());
            if (owner == null || !ownerValid(state, owner, cast)) {
                state.active.remove(slot, cast); cast.canceled = true;
                cast.close();
                if (owner != null) send(owner, cast, TechniqueState.CANCELED, 0);
                continue;
            }
            if (cast.kind == LimitlessTechnique.BLUE) blueTick(state, slot, owner, cast, now);
            else shotTick(state, slot, owner, cast, now);
            if(state.active.get(slot)==cast)observe(owner,cast,-1,0);
        }
    }

    /** Read the remaining close-range pull window without acquiring a domain freeze. */
    static int blueControlTicks(LivingEntity target) {
        if(CatAdaptation.controlScale(target,CatAdaptation.Phenomenon.BLUE)<=0)return 0;
        MinecraftServer server = target.getServer();
        State state = server == null ? null : STATES.get(server);
        if (state == null || !target.isAlive() || !pullEligible(target)) return 0;
        long now = server.getTickCount(), remaining = 0;
        for (var entry : state.active.entrySet()) {
            Cast cast = entry.getValue();
            if (cast.kind != LimitlessTechnique.BLUE || cast.canceled || now >= cast.born + cast.ticks
                    || target.level().dimension() != cast.dimension) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey().owner());
            if (owner == null || !TechniquePlayerRules.canAffect(owner,target) || !ownerValid(state, owner, cast)) continue;
            float opened = Math.clamp((float) (now - cast.born) / LimitlessTechnique.BLUE_OPEN_TICKS, 0, 1);
            opened = opened * opened * (3 - 2 * opened);
            double core = LimitlessTechnique.blueCoreRadius(cast.radius) * opened;
            if (opened < .5f || nearest(cast.point, target.getBoundingBox()).distanceTo(cast.point) > core + 1.4
                    || !visible(target.level(), owner, cast.point, target.getBoundingBox().getCenter())) continue;
            remaining = Math.max(remaining, cast.born + cast.ticks - now);
        }
        return (int) Math.min(remaining, 2600);
    }

    private static void blueTick(State state, Slot slot, ServerPlayer owner, Cast cast, long now) {
        if (now >= cast.born + cast.ticks) {
            state.active.remove(slot, cast);
            send(owner, cast, TechniqueState.CLOSING, LimitlessTechnique.BLUE_CLOSE_TICKS);
            return;
        }
        ServerLevel level = owner.serverLevel();
        if (!level.isLoaded(BlockPos.containing(cast.point))) {
            cancel(state, slot, owner, cast.session, false);
            return;
        }
        float opened = Math.clamp((float) (now - cast.born) / LimitlessTechnique.BLUE_OPEN_TICKS, 0, 1);
        opened = opened * opened * (3 - 2 * opened);
        float radius = cast.radius * opened;
        if (radius < .05f) return;
        float core = LimitlessTechnique.blueCoreRadius(cast.radius) * opened;
        if (cast.guideHand != null) {
            // Vanilla use/release packets and this payload can start in the same tick. Allow only a short
            // startup grace; changing hands/items, release, death and world changes cannot leave steering active.
            boolean holding = owner.isUsingItem() && owner.getUsedItemHand() == cast.guideHand
                    && owner.getItemInHand(cast.guideHand) == cast.controlStack && VfxItems.isBlue(cast.controlStack);
            if (!cast.parked && !holding && now - cast.born > 2) park(owner, cast);
            if ((!cast.parked && holding) || cast.blueGuide.coasting()) {
                Vec3 end = cast.blueGuide.next(owner.getEyePosition(),owner.getLookAngle());
                float carvingRadius = LimitlessTechnique.blueCoreRadius(cast.radius) * (.28f + .72f * opened);
                boolean released=cast.parked;
                Vec3 next = end.distanceToSqr(cast.point)<1e-8?cast.point:BlueTerrain.sweep(owner, cast.point, end, carvingRadius,
                        () -> current(state, slot, owner, cast)&&cast.parked==released);
                if (!current(state, slot, owner, cast)) return;
                Vec3 motion=next.subtract(cast.point);
                if(motion.lengthSqr()>1e-6)cast.direction=motion.normalize();
                cast.blueGuide.moved(next);cast.point=next;
                send(owner,cast,cast.parked?TechniqueState.BLUE_PARKED:TechniqueState.BLUE_MOVING,(int)(cast.born+cast.ticks-now));
            }
        }
        DamageSource source = source(owner, BLUE_DAMAGE);
        for (Entity target : level.getEntities(owner, new AABB(cast.point, cast.point).inflate(radius),
                e->pullEligible(e)&&TechniquePlayerRules.canAffect(owner,e))) {
            if (!current(state, slot, owner, cast)) return;
            if (target.level() != level || !pullEligible(target)||!TechniquePlayerRules.canAffect(owner,target)) continue;
            double surfaceDistance = nearest(cast.point, target.getBoundingBox()).distanceTo(cast.point);
            if (surfaceDistance > radius || !visible(level, owner, cast.point, target.getBoundingBox().getCenter())) continue;
            CatAdaptation.touch(target, CatAdaptation.Phenomenon.BLUE);
            // Domain stasis owns its saved pose and momentum, even when a second skill acts on it.
            boolean frozen = DomainFreeze.hold(target);
            if (!frozen) pull(target, cast.point, radius, core);
            if (target instanceof LivingEntity living && surfaceDistance <= core
                    && now >= cast.nextDamage.getOrDefault(target.getUUID(), Long.MIN_VALUE)) {
                cast.nextDamage.put(target.getUUID(), now + 10);
                blueDamage(state, slot, owner, cast, living, source);
            }
        }
        // Expired UUIDs do not accumulate for the full duration when an entity stream passes through.
        if ((now - cast.born) % 20 == 0) cast.nextDamage.values().removeIf(deadline -> deadline < now - 20);
    }

    private static void pull(Entity target, Vec3 focus, float radius, float core) {
        CatAdaptation.touch(target,CatAdaptation.Phenomenon.BLUE);
        float response=CatAdaptation.controlScale(target,CatAdaptation.Phenomenon.BLUE);
        if(response<=0)return;
        Vec3 toward = focus.subtract(target.getBoundingBox().getCenter());
        double distance = toward.length();
        double speedCap = target instanceof Projectile ? 1.25 : .82;
        double falloff = Math.clamp(1 - distance / Math.max(radius, .01), 0, 1);
        double speed = Math.min(speedCap * (.55 + .45 * falloff), distance * .20);
        Vec3 desired = distance < .0001 ? Vec3.ZERO : toward.scale(speed / distance);
        // A damped spring captures objects without an orbit or a repeated teleport at the core.
        double damping = (distance < Math.max(.2, core) ? .52 : .34)*response;
        Vec3 velocity = target.getDeltaMovement().scale(1 - damping).add(desired.scale(damping));
        if (!(target instanceof Projectile) && Math.abs(toward.y) < core + .25)
            velocity = velocity.add(0, .055 * damping, 0);
        velocity = bounded(velocity, speedCap);
        target.setDeltaMovement(velocity);
        target.hasImpulse = true;
        target.fallDistance = 0;
    }

    private static void blueDamage(State state, Slot slot, ServerPlayer owner, Cast cast,
                                   LivingEntity target, DamageSource source) {
        if (!current(state, slot, owner, cast) || !target.isAlive()) return;
        state.active.remove(slot, cast);
        state.damaging.put(slot, cast);
        try {
            TechniqueDamageRules.hurt(target, source, 6f, TechniqueDamage.BLUE);
            if (target.isAlive()) DomainFreeze.hold(target);
        } finally {
            state.damaging.remove(slot, cast);
            if (!cast.canceled && ownerValid(state, owner, cast) && !state.active.containsKey(slot))
                state.active.put(slot, cast);
        }
    }

    private static void shotTick(State state, Slot slot, ServerPlayer owner, Cast cast, long now) {
        // Amplified terrain work is bounded by its finite trajectory, not by time spent waiting
        // for the shared budget. Actual chunk-load failure still has a separate stall timeout.
        if (cast.launched && !cast.amplifiedPurple && now - cast.launchedAt > 600) { cancel(state, slot, owner, cast.session, false); return; }
        if (!cast.launched) {
            if (cast.kind == LimitlessTechnique.PURPLE) {
                long phaseNow = PurpleCastClock.now(owner.getServer());
                if (cast.heldCharge()) {
                    if (owner.getItemInHand(cast.guideHand) != cast.controlStack || !VfxItems.isPurple(cast.controlStack)
                            || (!cast.committed() && owner.isUsingItem() && owner.getUsedItemHand() != cast.guideHand)) {
                        cancel(state, slot, owner, cast.session, false); return;
                    }
                    if (!cast.committed()) {
                        long prepEnd = cast.purpleBorn + PurpleCastClock.ticks(LimitlessTechnique.PURPLE_PREP_TICKS);
                        if (cast.releaseRequestedAt != Long.MAX_VALUE) {
                            if (phaseNow >= cast.releaseRequestedAt + PURPLE_INPUT_GRACE)
                                commitPurple(owner, cast, cast.releaseRequestedAt >= prepEnd, cast.releaseRequestedAt);
                        } else if (!owner.isUsingItem() && phaseNow - cast.purpleBorn > PurpleCastClock.ticks(2)) {
                            requestPurpleRelease(owner, cast, phaseNow);
                        } else if (phaseNow >= prepEnd + PURPLE_INPUT_GRACE) {
                            // Explicit COMMIT is primary; a missing request still advances automatically.
                            // Backdate to the true threshold instead of restarting synthesis after a lag spike.
                            commitPurple(owner, cast, true, prepEnd);
                        }
                    }
                }
                if (!cast.committed()) return;
                if (cast.movementLock != null) cast.movementLock.hold(owner, phaseNow);
                long remaining = cast.purpleDeadline - phaseNow;
                float range = LimitlessTechnique.shotRange(cast.kind, cast.purpleBranch == TechniqueState.PURPLE_LARGE, cast.amplifiedPurple);
                if (cast.amplifiedPurple && remaining <= PurpleCastClock.ticks(20))
                    cast.chunks.prepareBudgeted(cast.point, cast.direction, cast.shotRadius, range);
                else if (!cast.amplifiedPurple && remaining <= PurpleCastClock.ticks(4))
                    cast.chunks.prepare(cast.point, cast.direction, cast.shotRadius, range);
                if (remaining > 0) return;
            } else if (cast.heldRed()) {
                if (!chargeRed(state, slot, owner, cast)) return;
            } else {
                cast.chargedTicks = (int) Math.clamp(now - cast.born, 0, LimitlessTechnique.chargeTicks(cast.kind));
                cast.point = LimitlessTechnique.shotMuzzle(owner, cast.kind, cast.chargedTicks / 20f, 1);
                cast.direction = owner.getLookAngle().normalize();
                cast.chunks.prepare(cast.point, cast.direction, cast.shotRadius, LimitlessTechnique.shotRange(cast.kind, false));
                if (cast.chargedTicks < LimitlessTechnique.chargeTicks(cast.kind)) return;
            }
            cast.launched = true; cast.launchedAt = now;
            if (cast.largeRed) cast.beamOrigin = cast.point;
            cast.unlock();
            send(owner, cast, TechniqueState.LAUNCHED, 0);
        }
        float range = cast.largeRed ? LimitlessTechnique.RED_BEAM_RANGE
                : LimitlessTechnique.shotRange(cast.kind, cast.purpleBranch == TechniqueState.PURPLE_LARGE, cast.amplifiedPurple);
        float core = cast.shotRadius;
        double step = Math.min((cast.largeRed ? LimitlessTechnique.RED_BEAM_SPEED : LimitlessTechnique.shotSpeed(cast.kind)) / 20,
                Math.max(0, range - cast.traveled));
        Vec3 start = cast.point, desired = start.add(cast.direction.scale(step));
        if (cast.amplifiedPurple) cast.chunks.prepareBudgeted(start, cast.direction, core, range - cast.traveled);
        else cast.chunks.prepare(start, cast.direction, core, range - cast.traveled);
        if (cast.amplifiedPurple && cast.carver == null) cast.carver = new PurpleCarver();
        Vec3 end = step <= .00001 ? start : cast.amplifiedPurple
                ? cast.carver.advance(owner, start, desired, core, state.carvingBudget, () -> current(state, slot, owner, cast)) : cast.largeRed
                ? BlueTerrain.beam(owner, start, desired, core, cast.traveled, () -> current(state, slot, owner, cast))
                : BlueTerrain.pierce(owner, start, desired, core, () -> current(state, slot, owner, cast));
        if (!current(state, slot, owner, cast)) return;
        if (cast.carver != null && cast.carver.blocked()) { cancel(state, slot, owner, cast.session, false); return; }
        double moved = start.distanceTo(end);
        if (cast.kind == LimitlessTechnique.RED && collideBlue(state, slot, owner, cast,
                cast.largeRed ? cast.beamOrigin : start, end, now)) return;
        cast.traveled += (float) moved; cast.point = end;
        if (!cast.amplifiedPurple) cast.stalled = moved < .0001 ? cast.stalled + 1 : 0;
        else if (moved >= .0001 || cast.carver.worked()) cast.stalled = 0;
        else if (cast.carver.waitingForChunks()) cast.stalled++;
        if (cast.stalled > (cast.amplifiedPurple ? 600 : 200)) { cancel(state, slot, owner, cast.session, false); return; }
        // Also send a stationary sample while asynchronous chunk generation catches up.
        send(owner, cast, TechniqueState.SHOT_MOVING, 0);
        shotHits(state, slot, owner, cast, cast.largeRed ? cast.beamOrigin : start, end, core);
        if (!current(state, slot, owner, cast)) return;
        if (cast.traveled >= range - .001f && cast.largeRed && cast.redFullAt == Long.MAX_VALUE) cast.redFullAt = now;
        if (cast.traveled >= range - .001f && (!cast.largeRed || now - cast.redFullAt >= LimitlessTechnique.RED_BEAM_HOLD_TICKS)) {
            state.active.remove(slot, cast);
            try {
                if (cast.kind == LimitlessTechnique.RED) redImpact(state, slot, owner, cast);
                else send(owner, cast, TechniqueState.IMPACT, Math.round(LimitlessTechnique.PURPLE_AFTERGLOW * 20));
            } finally { cast.close(); }
        }
    }

    /** Only real, launched Red trajectories collide. Purple's decorative synthesis lobes never enter this map. */
    private static boolean collideBlue(State state, Slot redSlot, ServerPlayer owner, Cast red,
                                       Vec3 start, Vec3 end, long now) {
        Cast selected = null; Slot selectedSlot = null; ServerPlayer blueOwner = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (var entry : Map.copyOf(state.active).entrySet()) {
            Cast blue = entry.getValue(); Slot slot = entry.getKey();
            if (blue.kind != LimitlessTechnique.BLUE || blue.canceled || now >= blue.born + blue.ticks
                    || !blue.dimension.equals(red.dimension)) continue;
            ServerPlayer caster = owner.getServer().getPlayerList().getPlayer(slot.owner());
            if (caster == null || !current(state, slot, caster, blue)) continue;
            float opened = Math.clamp((float) (now - blue.born) / LimitlessTechnique.BLUE_OPEN_TICKS, 0, 1);
            opened *= opened * (3 - 2 * opened);
            if (opened < .08f) continue;
            float blueCore = LimitlessTechnique.blueCoreRadius(blue.radius) * opened;
            // The cannon is tapered at its muzzle. Use short consecutive segments with their real
            // local thickness, instead of turning the whole beam into a full-width capsule.
            int pieces = red.largeRed ? Math.max(1, (int) Math.ceil(start.distanceTo(end) / .35)) : 1;
            for (int i = 0; i < pieces; i++) {
                Vec3 a = start.lerp(end, (double) i / pieces), b = start.lerp(end, (double) (i + 1) / pieces);
                double redCore = red.largeRed ? red.shotRadius * Math.clamp(.12 + start.distanceTo(a) / 8, .12, 1)
                        : red.shotRadius;
                double contact = sphereEntry(a, b, blue.point, blueCore + redCore);
                if (!Double.isFinite(contact)) continue;
                double along = (i + contact) / pieces;
                if (along < nearest || along == nearest && selectedSlot != null && slot.owner().compareTo(selectedSlot.owner()) < 0) {
                    nearest = along; selected = blue; selectedSlot = slot; blueOwner = caster;
                }
                break;
            }
        }
        if (selected == null || !current(state, redSlot, owner, red)
                || !current(state, selectedSlot, blueOwner, selected)) return false;
        Vec3 center = selected.point;
        if (!UnlimitedPurpleBlast.canTrigger(owner, center)) return false;
        float opened = Math.clamp((float) (now - selected.born) / LimitlessTechnique.BLUE_OPEN_TICKS, 0, 1);
        opened *= opened * (3 - 2 * opened);
        var blueSource = new dev.jujutsuvfx.network.UnlimitedPurple.Source(blueOwner.getUUID(), selected.session,
                selected.point, selected.direction, selected.point, selected.radius,
                LimitlessTechnique.blueCoreRadius(selected.radius) * (.28f + .72f * opened), (now - selected.born) / 20f, false);
        Vec3 contact = start.lerp(end, nearest);
        var redSource = new dev.jujutsuvfx.network.UnlimitedPurple.Source(owner.getUUID(), red.session,
                contact, red.direction, red.largeRed ? red.beamOrigin : contact, red.radius, red.shotRadius,
                (now - red.born) / 20f, red.largeRed);
        // Send the handoff before cancellation; a render frame can now use the incoming
        // silhouettes instead of briefly exposing an empty world between two effects.
        if (!UnlimitedPurpleBlast.trigger(owner, center, blueSource, redSource)) return false;
        cancel(state, selectedSlot, blueOwner, selected.session, false);
        cancel(state, redSlot, owner, red.session, false);
        return true;
    }

    private static double sphereEntry(Vec3 start, Vec3 end, Vec3 center, double radius) {
        Vec3 relative = start.subtract(center), motion = end.subtract(start);
        double c = relative.lengthSqr() - radius * radius;
        if (c <= 0) return 0;
        double a = motion.lengthSqr();
        if (a < 1e-12) return Double.POSITIVE_INFINITY;
        double b = relative.dot(motion), discriminant = b * b - a * c;
        if (discriminant < 0) return Double.POSITIVE_INFINITY;
        double entry = (-b - Math.sqrt(discriminant)) / a;
        return entry >= 0 && entry <= 1 ? entry : Double.POSITIVE_INFINITY;
    }

    private static void shotHits(State state, Slot slot, ServerPlayer owner, Cast cast, Vec3 start, Vec3 end, float core) {
        ServerLevel level = owner.serverLevel();
        DamageSource damage = source(owner, cast.kind == LimitlessTechnique.PURPLE ? PURPLE_DAMAGE : RED_DAMAGE);
        TechniqueDamage technique = cast.kind != LimitlessTechnique.PURPLE ? TechniqueDamage.RED
                : cast.amplifiedPurple ? TechniqueDamage.PURPLE_AMPLIFIED
                : cast.purpleBranch == TechniqueState.PURPLE_LARGE ? TechniqueDamage.PURPLE_LARGE : TechniqueDamage.PURPLE_SMALL;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(core), e->baseEligible(e)&&TechniquePlayerRules.canAffect(owner,e))) {
            if (!current(state, slot, owner, cast)) return;
            if (target.level() != level || !baseEligible(target)||!TechniquePlayerRules.canAffect(owner,target) || cast.struck.contains(target.getUUID())) continue;
            double distance = Math.sqrt(SweptSphere.distanceSquared(start, end, target.getBoundingBox()));
            if (cast.largeRed) {
                double along = Math.clamp(target.getBoundingBox().getCenter().subtract(start).dot(cast.direction), 0, start.distanceTo(end));
                if (distance > core * Math.clamp(.12 + along / 8, .12, 1)) continue;
            }
            if (distance > core) continue;
            cast.struck.add(target.getUUID());
            float strength = (float) Math.clamp(1 - distance / core, 0, 1);
            state.active.remove(slot, cast); state.damaging.put(slot, cast);
            try {
                boolean hit = TechniqueDamageRules.hurt(target, damage, cast.kind == LimitlessTechnique.PURPLE
                        ? cast.amplifiedPurple ? LimitlessTechnique.PURPLE_AMPLIFIED_DAMAGE : LimitlessTechnique.PURPLE_DAMAGE
                        : cast.largeRed ? 160 : 80 + 40 * strength, technique);
                if (!cast.canceled && ownerValid(state, owner, cast) && !state.active.containsKey(slot)
                        && target.level() == level && baseEligible(target)) {
                    boolean frozen = DomainFreeze.hold(target);
                    if (hit && !frozen && cast.kind == LimitlessTechnique.RED) repel(target, start, cast.direction, strength);
                }
            } finally {
                state.damaging.remove(slot, cast);
                if (!cast.canceled && ownerValid(state, owner, cast) && !state.active.containsKey(slot)) state.active.put(slot, cast);
                else cast.close();
            }
        }
    }

    private static void redImpact(State state, Slot slot, ServerPlayer owner, Cast cast) {
        if (!ownerValid(state, owner, cast) || cast.canceled) return;
        state.damaging.put(slot, cast);
        try {
            send(owner, cast, TechniqueState.IMPACT, Math.round(LimitlessTechnique.RED_AFTERGLOW * 20));
            DamageSource source = source(owner, RED_DAMAGE);
            ServerLevel level = owner.serverLevel();
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cast.point, cast.point).inflate(cast.radius), e->baseEligible(e)&&TechniquePlayerRules.canAffect(owner,e))) {
                if (cast.canceled || !ownerValid(state, owner, cast) || state.active.containsKey(slot)) return;
                if (target.level() != level || !baseEligible(target)||!TechniquePlayerRules.canAffect(owner,target)) continue;
                double distance = nearest(cast.point, target.getBoundingBox()).distanceTo(cast.point);
                if (distance > cast.radius || !visible(level, owner, cast.point, target.getBoundingBox().getCenter())
                        || !cast.struck.add(target.getUUID())) continue;
                float strength = 1 - (float) distance / cast.radius;
                boolean hit = TechniqueDamageRules.hurt(target, source, 40 + 80 * strength, TechniqueDamage.RED);
                if (cast.canceled || !ownerValid(state, owner, cast) || state.active.containsKey(slot)) return;
                if (target.level() == level && baseEligible(target)) {
                    boolean frozen = DomainFreeze.hold(target);
                    if (hit && !frozen) repel(target, cast.point, cast.direction, strength);
                }
            }
            SkillTerrain.red(owner, source, cast.point, cast.radius,
                    () -> !cast.canceled && ownerValid(state, owner, cast) && !state.active.containsKey(slot));
        } finally {
            state.damaging.remove(slot, cast);
        }
    }

    private static void repel(LivingEntity target, Vec3 focus, Vec3 direction, float strength) {
        CatAdaptation.touch(target,CatAdaptation.Phenomenon.RED);
        double response = CatAdaptation.controlScale(target,CatAdaptation.Phenomenon.RED)* Math.clamp(1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        if (response <= 0) return;
        Vec3 away = target.getBoundingBox().getCenter().subtract(focus);
        away = away.lengthSqr() < .01 ? direction : away.normalize();
        Vec3 impulse = away.scale(.65 + 1.55 * strength).add(0, .25 + .30 * strength, 0).scale(response);
        Vec3 velocity = bounded(target.getDeltaMovement().scale(.35).add(impulse), 2.8);
        target.setDeltaMovement(velocity.x, Math.clamp(velocity.y, -.45, 1.15), velocity.z);
        target.hasImpulse = true;
    }

    private static boolean baseEligible(Entity entity) {
        return entity.isAlive() && !entity.isRemoved() && !entity.isSpectator()
                && !entity.isPassenger() && !entity.isVehicle();
    }
    private static boolean pullEligible(Entity entity) {
        return baseEligible(entity) && (entity instanceof LivingEntity || entity instanceof Projectile
                || entity instanceof ItemEntity || entity instanceof ExperienceOrb);
    }
    private static Vec3 nearest(Vec3 point, AABB box) {
        return new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }
    private static Vec3 bounded(Vec3 velocity, double limit) {
        return velocity.lengthSqr() > limit * limit ? velocity.normalize().scale(limit) : velocity;
    }
    private static boolean visible(Level level, Entity owner, Vec3 start, Vec3 end) {
        return LimitlessTechnique.loadedSegment(level, start, end) && level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    private static boolean ownerValid(State state, ServerPlayer owner, Cast cast) {
        return !state.stopped && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
                && owner.level().dimension().equals(cast.dimension) && owner.getServer() != null
                && owner.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner;
    }
    private static boolean current(State state, Slot slot, ServerPlayer owner, Cast cast) {
        return !cast.canceled && state.active.get(slot) == cast && ownerValid(state, owner, cast);
    }
    private static ResourceKey<DamageType> damageKey(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, path));
    }
    private static DamageSource source(ServerPlayer owner, ResourceKey<DamageType> type) {
        return new DamageSource(owner.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), owner, owner);
    }
    private static void send(ServerPlayer owner, Cast cast, int phase, int remaining) {
        observe(owner,cast,phase,remaining);
        PacketDistributor.sendToPlayer(owner, new TechniqueState(cast.session, cast.kind, phase,
                owner.level().getGameTime(), remaining, cast.point, cast.direction,
                cast.kind == LimitlessTechnique.BLUE ? cast.radius : cast.shotRadius, cast.chargedTicks));
        if(cast.kind==LimitlessTechnique.BLUE){
            int kind=dev.jujutsuvfx.network.SkillGesture.BLUE;
            if(phase==TechniqueState.ACCEPTED)TechniqueGestures.start(owner,cast.session,kind,cast.guideHand,cast.guideHand!=null);
            else if(phase==TechniqueState.BLUE_PARKED)TechniqueGestures.release(owner,cast.session,kind);
            else if(phase==TechniqueState.CANCELED)TechniqueGestures.cancel(owner,cast.session,kind);
        }
    }
    private static void observe(ServerPlayer owner,Cast cast,int signal,int remaining) {
        if(signal==TechniqueState.CANCELED){PlayerSpellSync.end(owner,cast.kind,cast.session);return;}
        long now=owner.getServer().getTickCount(),clock=PurpleCastClock.now(owner.getServer());
        int age=(int)Math.max(0,now-cast.born),prepare=0,phase=dev.jujutsuvfx.core.CatSpell.CHARGE,stage=age;
        boolean large=cast.kind==LimitlessTechnique.PURPLE?cast.purpleBranch==TechniqueState.PURPLE_LARGE:cast.largeRed;
        if(cast.kind==LimitlessTechnique.PURPLE){
            age=(int)Math.max(0,(clock-cast.purpleBorn)/PurpleCastClock.TICK_NANOS);
            prepare=cast.purpleBranch==0?0:(int)Math.max(0,(cast.committedAt-cast.purpleBorn)/PurpleCastClock.TICK_NANOS);
            // Purple's pre-release clock also survives a stalled simulation tick. Flight then uses server ticks.
            int charge=cast.purpleBranch==0?0:(int)Math.max(0,(clock-cast.committedAt)/PurpleCastClock.TICK_NANOS);
            if(cast.launched){
                if(cast.observerLaunchAge<0)cast.observerLaunchAge=age;
                age=cast.observerLaunchAge+(int)(now-cast.launchedAt);
            }
            phase=cast.purpleBranch==0?dev.jujutsuvfx.core.CatSpell.PREPARE:dev.jujutsuvfx.core.CatSpell.CHARGE;stage=charge;
        }
        if(cast.launched){phase=dev.jujutsuvfx.core.CatSpell.FLIGHT;stage=(int)Math.max(0,now-cast.launchedAt);}
        if(cast.kind==LimitlessTechnique.BLUE)remaining=(int)Math.max(0,cast.born+cast.ticks-now);
        else remaining=100;
        if(signal==TechniqueState.IMPACT||signal==TechniqueState.CLOSING){
            phase=dev.jujutsuvfx.core.CatSpell.IMPACT;stage=0;
            remaining=cast.kind==LimitlessTechnique.BLUE?LimitlessTechnique.BLUE_CLOSE_TICKS:Math.round(LimitlessTechnique.afterglow(cast.kind)*20);
        }
        Vec3 point=cast.point,direction=cast.direction;
        if(!cast.launched&&cast.kind==LimitlessTechnique.RED){
            direction=owner.getLookAngle().normalize();
            HumanoidArm arm=cast.guideHand==InteractionHand.OFF_HAND?owner.getMainArm().getOpposite():owner.getMainArm();
            point=LimitlessTechnique.redMuzzle(owner,1,direction,arm==HumanoidArm.RIGHT?1:-1);
        }
        PlayerSpellSync.publish(owner,cast.session,cast.kind,large,phase,age,stage,remaining,
                cast.kind==LimitlessTechnique.BLUE?cast.radius:cast.shotRadius,point,direction,
                cast.beamOrigin==null?owner.getEyePosition():cast.beamOrigin,0,prepare,cast.guideHand,cast.radius);
    }

    private static void send(ServerPlayer owner, int session, int kind, int phase, int remaining, Vec3 point, Vec3 direction) {
        PacketDistributor.sendToPlayer(owner, new TechniqueState(session, kind, phase,
                owner.level().getGameTime(), remaining, point, direction, 0, 0));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        State state = STATES.remove(event.getServer());
        if (state != null) {
            state.stopped = true;
            for (Cast cast : state.active.values()) cast.close();
            for (Cast cast : state.damaging.values()) cast.close();
            state.active.clear(); state.damaging.clear();
        }
        PurpleCastClock.discard(event.getServer());
    }
}
