package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.network.CatSpellState;
import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.network.TechniqueControl;
import dev.jujutsuvfx.network.TechniqueState;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Blue, Red and Purple own separate lifetimes. Pure-client fallback only renders their trajectories. */
final class LimitlessCast {
    private static final SoundEvent FORM = sound("blue_form"), HUM = sound("blue_loop"), CLOSE = sound("blue_close");
    private static final SoundEvent CHARGE = sound("red_charge"), RELEASE = sound("red_release"), IMPACT = sound("red_impact");
    private static final SoundEvent RED_AIR = sound("red_air"), RED_CANNON = sound("red_cannon"), RED_ROAR = sound("red_roar");
    private static final SoundEvent PURPLE_CHARGE = sound("purple_charge"), PURPLE_RELEASE = sound("purple_release"),
            PURPLE_FLIGHT = sound("purple_flight"), PURPLE_END = sound("purple_end");
    final int session, kind;
    final float radius;
    final boolean networked;
    private final ClientLevel owner;
    private final InteractionHand guideHand;
    private final ItemStack controlStack;
    private final long born;
    private long deadline, closing = Long.MAX_VALUE, launched = Long.MAX_VALUE, impact = Long.MAX_VALUE, nextHum;
    private Vec3 previous, position, direction;
    private dev.jujutsuvfx.core.BlueGuidance blueGuide;
    private double motionReceivedAt;
    private float traveled, projectileRadius;
    private Vec3 purpleGatherOrigin;
    private long lastServerMotion;
    private long releaseRequested = Long.MAX_VALUE;
    private long committedAt = Long.MAX_VALUE;
    private boolean largePurple;
    private boolean largeRed, redReadySent;
    private Vec3 beamOrigin;
    private long beamFullAt = Long.MAX_VALUE;
    private float lockedYaw, lockedPitch;
    private boolean disposed, accepted, parked;
    private CatSpellState remote; private long remoteReceived;
    private BlueHum hum;
    private final CastAudio audio=new CastAudio();
    private PurpleHum purpleHum;
    private RedHum redHum;
    private boolean fusionPreview;
    private float previewCore, previewAlpha, previewAge, previewLength, previewStreams = 1;

    /** Render-only incoming geometry; never sends controls, starts sounds or owns an item. */
    LimitlessCast(Minecraft mc, dev.jujutsuvfx.network.UnlimitedPurple.Source source, int kind, boolean beam) {
        session = source.session(); this.kind = kind; radius = source.influenceRadius(); owner = mc.level;
        guideHand = null; controlStack = ItemStack.EMPTY; networked = false;
        born = ClientEffectClock.ticks(); deadline = born; fusionPreview = true;
        previous = position = source.point(); direction = source.direction().normalize();
        projectileRadius = previewCore = source.coreRadius(); previewAge = source.ageSeconds();
        largeRed = kind == LimitlessTechnique.RED && beam;
    }
    void preview(Vec3 point, float core, float alpha, float age, float length, float streams) {
        if (!fusionPreview) throw new IllegalStateException("Only collision previews may be morphed");
        previous = position = point; previewCore = core; previewAlpha = alpha;
        previewAge = age; previewLength = length; previewStreams = streams;
    }
    float streamsVisibility() { return fusionPreview ? previewStreams : 1; }
    float beamRadius() { return fusionPreview ? previewCore : LimitlessTechnique.RED_BEAM_RADIUS; }

    LimitlessCast(Minecraft mc, int session, int kind, float radius, int ticks) {
        this(mc, session, kind, radius, ticks, null);
    }

    LimitlessCast(Minecraft mc, int session, int kind, float radius, int ticks, InteractionHand guideHand) {
        this.session = session; this.kind = kind; this.radius = radius; owner = mc.level;
        this.guideHand = guideHand;
        controlStack = guideHand == null ? ItemStack.EMPTY : mc.player.getItemInHand(guideHand);
        projectileRadius = LimitlessTechnique.shotRadius(kind, radius);
        born = ClientEffectClock.ticks(); deadline = born + ticks;
        motionReceivedAt = born;
        direction = mc.player.getLookAngle();
        previous = position = blue() ? guideHand == null ? LimitlessTechnique.blueFocus(owner, mc.player)
                : LimitlessTechnique.blueMuzzle(mc.player) : LimitlessTechnique.shotMuzzle(mc.player, kind, 0, 1);
        if(blue()&&guideHand!=null)blueGuide=new dev.jujutsuvfx.core.BlueGuidance(position,mc.player.getEyePosition(),direction);
        var connection = mc.getConnection();
        networked = connection != null && connection.hasChannel(TechniqueControl.TYPE) && connection.hasChannel(TechniqueState.TYPE);
        if (!purple()) play(blue() ? FORM : CHARGE, position, .66f, 1);
        else if (guideHand == null) commitPurple(true, born, false);
        int action = guideHand == null ? TechniqueControl.CAST : purple() || red()
                ? guideHand == InteractionHand.MAIN_HAND ? TechniqueControl.CHARGE_MAIN : TechniqueControl.CHARGE_OFF
                : guideHand == InteractionHand.MAIN_HAND ? TechniqueControl.GUIDE_MAIN : TechniqueControl.GUIDE_OFF;
        if (networked) {
            TechniqueControl request = new TechniqueControl(action, session, kind, radius, ticks);
            if (purple() && guideHand == null) request = request.withAim(lockedYaw, lockedPitch);
            PacketDistributor.sendToServer(request);
        }
    }
    LimitlessCast(Minecraft mc,CatSpellState packet) {
        session=packet.session();kind=packet.kind();radius=packet.playerCast()?packet.influenceRadius():packet.radius();owner=mc.level;
        guideHand=null;controlStack=ItemStack.EMPTY;networked=false;born=ClientEffectClock.ticks()-packet.age();
        deadline=ClientEffectClock.ticks()+packet.remaining();
        previous=position=packet.point();direction=packet.direction();projectileRadius=packet.radius();
        remoteState(packet,true);
    }
    void remoteState(CatSpellState packet,boolean initial) {
        if(remote!=null&&(packet.phase()<remote.phase()||packet.age()<remote.age()
                ||packet.phase()==remote.phase()&&packet.age()==remote.age()))return;
        int old=remote==null?-1:remote.phase();
        previous=remote==null||packet.phase()!=remote.phase()?packet.point():previous.lerp(position,Math.clamp((ClientEffectClock.ticks()-remoteReceived)*CatSpells.castRate(remote)/2f,0,1));position=packet.point();direction=packet.direction();remoteReceived=ClientEffectClock.ticks();remote=packet;
        motionReceivedAt=remoteReceived;projectileRadius=packet.radius();
        largePurple=purple()&&packet.large();largeRed=red()&&packet.large();
        if(packet.phase()>=CatSpell.CHARGE&&purple())committedAt=remoteReceived-packet.stageAge();
        if(packet.phase()==CatSpell.FLIGHT||packet.phase()==CatSpell.IMPACT&&launched==Long.MAX_VALUE){launched=Math.min(launched,remoteReceived-packet.stageAge());if(redBeam())beamOrigin=packet.origin();}
        if(packet.phase()==CatSpell.IMPACT){impact=Math.min(impact,remoteReceived-packet.stageAge());if(blue())closing=impact;}
        if(old!=packet.phase()&&(!initial||packet.stageAge()<4)){
            if(packet.phase()>=CatSpell.FLIGHT)audio.stop(purple()?PURPLE_CHARGE:CHARGE);
            SoundEvent sound=packet.phase()==CatSpell.IMPACT?blue()?CLOSE:purple()?PURPLE_END:IMPACT
                    :packet.phase()==CatSpell.FLIGHT?blue()?null:purple()?PURPLE_RELEASE:redBeam()?RED_CANNON:RELEASE
                    :packet.phase()==CatSpell.CHARGE?blue()?FORM:purple()?PURPLE_CHARGE:CHARGE:null;
            if(sound!=null)play(sound,position,.65f*range(position,96),1);
            if(redHum!=null){redHum.cancel();redHum=null;}if(purpleHum!=null){purpleHum.cancel();purpleHum=null;}
        }
    }
    private float remoteExtra(long now,float partial){return Math.clamp((now-remoteReceived+partial)*CatSpells.castRate(remote),0,2);}
    private void remoteTick(Minecraft mc){
        long now=ClientEffectClock.ticks();if(now<nextHum)return;nextHum=now+20;
        if(blue()&&phase()!=2&&(hum==null||!mc.getSoundManager().isActive(hum))){if(hum!=null)hum.cancel();hum=new BlueHum(this);mc.getSoundManager().play(hum);}
        if(red()&&(phase()==0||redBeam()&&phase()==1)&&(redHum==null||!mc.getSoundManager().isActive(redHum))){if(redHum!=null)redHum.cancel();redHum=new RedHum(this);mc.getSoundManager().play(redHum);}
        if(purple()&&phase()==1&&(purpleHum==null||!mc.getSoundManager().isActive(purpleHum))){if(purpleHum!=null)purpleHum.cancel();purpleHum=new PurpleHum(this);mc.getSoundManager().play(purpleHum);}
    }
    boolean remote(){return remote!=null;}
    boolean blue() { return kind == LimitlessTechnique.BLUE; }
    boolean red() { return kind == LimitlessTechnique.RED; }
    boolean redHeld() { return red() && (guideHand != null || remote()); }
    boolean redBeam() { return red() && largeRed; }
    boolean redCommitted() { return redBeam() && phase() == 0 && !disposed; }
    boolean purple() { return kind == LimitlessTechnique.PURPLE; }
    boolean heldCharge() { return purple() && (guideHand != null || remote()&&remote.playerCast()); }
    InteractionHand chargeHand() { return remote()&&remote.playerCast()?remote.hand()==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND:heldCharge() || redHeld() ? guideHand : null; }
    boolean preparingPurple() { return purple() && !disposed && phase() == 0 && committedAt == Long.MAX_VALUE; }
    boolean committedPurple() { return purple() && !disposed && phase() == 0 && committedAt != Long.MAX_VALUE; }
    boolean largePurple() { return purple() && largePurple; }
    boolean amplifiedPurple() { return largePurple() && projectileRadius > LimitlessTechnique.PURPLE_RADIUS + .5f; }
    float visualRadius() { return purple() ? projectileRadius : radius; }
    float fusionRadius() { return visualRadius(); }
    float amplificationProgress(long now, float partial) {
        if (!amplifiedPurple()) return 0;
        return 1;
    }
    float preparationAge(long now, float partial) { if(remote())return (remote.age()+remoteExtra(now,partial))/20f;return Math.max(0, (now - born + partial) / 20f); }
    float synthesisAge(long now, float partial) {
        if(remote())return remote.phase()==CatSpell.PREPARE?-1:Math.max(0,(remote.age()-remote.prepareTicks()+remoteExtra(now,partial))/20f);
        return committedAt == Long.MAX_VALUE ? -1 : Math.max(0, (now - committedAt + partial) / 20f);
    }
    private int synthesisTicks() {
        return largePurple ? LimitlessTechnique.PURPLE_LARGE_CHARGE_TICKS : LimitlessTechnique.PURPLE_SMALL_CHARGE_TICKS;
    }
    private int launchTicks() { return LimitlessTechnique.purpleLaunchTicks(largePurple); }
    void holdPlayer(Minecraft mc) {
        if(remote())return;
        if (!committedPurple() || mc.player == null || mc.level != owner) return;
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.fallDistance = 0;
        mc.player.setSprinting(false);
        mc.player.setYRot(lockedYaw); mc.player.yRotO = lockedYaw;
        mc.player.setXRot(lockedPitch); mc.player.xRotO = lockedPitch;
        mc.player.setYHeadRot(lockedYaw);
    }
    void receive(TechniqueState packet) {
        long now = ClientEffectClock.ticks();
        if (red() && packet.phase() == TechniqueState.RED_BEAM && phase() == 0) {
            commitRed(false);
            return;
        }
        if (packet.phase() == TechniqueState.ACCEPTED && !accepted) {
            accepted = true;
            if (blue() && closing == Long.MAX_VALUE) {
                moveTo(packet.point(), packet.direction());
                deadline = ClientEffectClock.after(Math.clamp(packet.remainingTicks(), 0, 600));
            }
        } else if (purple() && phase() == 0 && (packet.phase() == TechniqueState.PURPLE_SMALL
                || packet.phase() == TechniqueState.PURPLE_LARGE)) {
            // Prediction may already show this stage. A server confirmation never restarts its clock.
            boolean incomingLarge = packet.phase() == TechniqueState.PURPLE_LARGE;
            // Committing to synthesis is irreversible. An older SMALL confirmation cannot shrink it.
            if (largePurple && !incomingLarge) return;
            if (committedAt == Long.MAX_VALUE) commitPurple(incomingLarge, now, true);
            largePurple |= incomingLarge;
            acceptCharge(packet);
            // The lock owns the exact local look at commit time. A delayed server pose must never
            // pull the camera back; accept its clipped origin only once it agrees with that aim.
            if (direction.dot(packet.direction().normalize()) > .99999)
                previous = position = packet.point();
        } else if (blue() && guideHand != null && closing == Long.MAX_VALUE
                && packet.phase() == TechniqueState.BLUE_MOVING && !parked) {
            moveTo(packet.point(), packet.direction());
        } else if (blue() && guideHand != null && closing == Long.MAX_VALUE
                && packet.phase() == TechniqueState.BLUE_PARKED) {
            moveTo(packet.point(), packet.direction());
            parked = true;
        } else if (!blue() && packet.phase() == TechniqueState.LAUNCHED && launched == Long.MAX_VALUE) {
            acceptCharge(packet);
            launch(packet.point(), packet.direction(), now);
        } else if (!blue() && packet.phase() == TechniqueState.SHOT_MOVING && impact == Long.MAX_VALUE) {
            lastServerMotion = now;
            acceptCharge(packet);
            if (launched == Long.MAX_VALUE) launch(packet.point(), packet.direction(), now);
            moveTo(packet.point(), packet.direction());
        } else if (!blue() && packet.phase() == TechniqueState.IMPACT && impact == Long.MAX_VALUE) {
            acceptCharge(packet);
            hit(packet.point(), now);
        } else if (blue() && packet.phase() == TechniqueState.CLOSING) {
            moveTo(packet.point(), packet.direction()); close(now);
        }
    }
    void tick(Minecraft mc) {
        if(remote()){remoteTick(mc);return;}
        long now = ClientEffectClock.ticks();
        if (red() && (phase() == 0 || redBeam() && phase() == 1) && now >= nextHum) {
            nextHum = now + 20;
            if (redHum == null || !mc.getSoundManager().isActive(redHum)) {
                if (redHum != null) redHum.cancel();
                redHum = new RedHum(this); mc.getSoundManager().play(redHum);
            }
        }
        if (blue()) {
            if (!networked && now >= deadline) close(now);
            if (guideHand != null && !parked && closing == Long.MAX_VALUE) {
                if (!mc.options.keyUse.isDown() || !mc.player.isUsingItem()
                        || mc.player.getUsedItemHand() != guideHand || !VfxItems.isBlue(mc.player.getItemInHand(guideHand))) park();
            }
            if(!networked&&blueGuide!=null&&closing==Long.MAX_VALUE&&(!parked||blueGuide.coasting())){
                previous=position;motionReceivedAt=now;
                Vec3 desired=blueGuide.next(mc.player.getEyePosition(),mc.player.getLookAngle());
                position=LimitlessTechnique.trace(owner,mc.player,position,desired).point();
                Vec3 motion=position.subtract(previous);if(motion.lengthSqr()>1e-6)direction=motion.normalize();
                blueGuide.moved(position);
            }
            if (closing == Long.MAX_VALUE && now >= nextHum) {
                nextHum = now + 20;
                if (hum == null || !mc.getSoundManager().isActive(hum)) {
                    if (hum != null) hum.cancel();
                    hum = new BlueHum(this); mc.getSoundManager().play(hum);
                }
            }
            return;
        }
        if (launched == Long.MAX_VALUE) {
            if (redHeld()) {
                if (mc.player.getItemInHand(guideHand) != controlStack || !VfxItems.isRed(controlStack)
                        || mc.player.isUsingItem() && mc.player.getUsedItemHand() != guideHand) { stop(); return; }
                if (!largeRed && releaseRequested == Long.MAX_VALUE) {
                    if (now - born >= LimitlessTechnique.RED_PREP_TICKS) commitRed(true);
                    else if (!mc.options.keyUse.isDown() || !mc.player.isUsingItem()) releaseRed();
                }
                if (largeRed && now - born >= LimitlessTechnique.RED_BEAM_CHARGE_TICKS && !redReadySent) {
                    redReadySent = true;
                    captureRedAim();
                    if (networked) PacketDistributor.sendToServer(new TechniqueControl(TechniqueControl.RED_FIRE,
                            session, kind, 0, 0).withAim(mc.player.getYRot(), mc.player.getXRot()));
                }
                if (!networked && (redReadySent || !largeRed && releaseRequested != Long.MAX_VALUE)) launch(position, direction, now);
            } else if (purple()) {
                if (heldCharge() && (mc.player.getItemInHand(guideHand) != controlStack || !VfxItems.isPurple(controlStack)
                        || (preparingPurple() && mc.player.isUsingItem() && mc.player.getUsedItemHand() != guideHand))) { stop(); return; }
                if (preparingPurple()) {
                    if (now - born >= LimitlessTechnique.PURPLE_PREP_TICKS) commitPurple(true, now, true);
                    else if (!mc.options.keyUse.isDown() || !mc.player.isUsingItem()) release();
                }
                holdPlayer(mc);
                if (!networked && committedPurple() && now - committedAt >= launchTicks()) launch(position, direction, now);
            } else if (!networked && now - born >= LimitlessTechnique.chargeTicks(kind)) {
                launch(LimitlessTechnique.shotMuzzle(mc.player, kind, age(now, 0), 1), mc.player.getLookAngle(), now);
            }
            return;
        }
        if (impact != Long.MAX_VALUE) return;
        if (purple() && (purpleHum == null || !mc.getSoundManager().isActive(purpleHum)) && now >= nextHum) {
            nextHum = now + 20;
            if (purpleHum != null) purpleHum.cancel();
            purpleHum = new PurpleHum(this); mc.getSoundManager().play(purpleHum);
        }
        if (networked) return;
        previous = position;
        motionReceivedAt = now;
        float range = redBeam() ? LimitlessTechnique.RED_BEAM_RANGE : LimitlessTechnique.shotRange(kind, largePurple, amplifiedPurple());
        double step = Math.min((redBeam() ? LimitlessTechnique.RED_BEAM_SPEED : LimitlessTechnique.shotSpeed(kind)) / 20,
                Math.max(0, range - traveled));
        traveled += step; position = position.add(direction.scale(step));
        if (traveled >= range - .001f && redBeam() && beamFullAt == Long.MAX_VALUE) beamFullAt = now;
        if (traveled >= range - .001f && (!redBeam() || now - beamFullAt >= LimitlessTechnique.RED_BEAM_HOLD_TICKS)) hit(position, now);
    }

    private void moveTo(Vec3 point, Vec3 axis) {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        previous = redBeam() && beamOrigin != null ? previous.lerp(position,
                Math.clamp(ClientEffectClock.ticks() + partial - motionReceivedAt, 0, 1)) : center(mc, partial);
        position = point;
        motionReceivedAt = ClientEffectClock.ticks() + partial;
        if (axis.lengthSqr() > .000001) direction = axis.normalize();
    }

    void park() {
        if (!blue() || guideHand == null || parked || disposed || closing != Long.MAX_VALUE) return;
        Minecraft mc = Minecraft.getInstance();
        parked = true;
        blueGuide.release();
        if (networked) PacketDistributor.sendToServer(new TechniqueControl(TechniqueControl.PARK, session, kind, 0, 0));
    }

    void releaseRed() {
        if (!redHeld() || disposed || phase() != 0 || largeRed || releaseRequested != Long.MAX_VALUE) return;
        long now = ClientEffectClock.ticks();
        if (now - born >= LimitlessTechnique.RED_PREP_TICKS) { commitRed(true); return; }
        releaseRequested = now;
        captureRedAim();
        Minecraft mc = Minecraft.getInstance();
        if (networked) PacketDistributor.sendToServer(new TechniqueControl(TechniqueControl.COMMIT_SMALL,
                session, kind, 0, 0).withAim(mc.player.getYRot(), mc.player.getXRot()));
    }

    private void commitRed(boolean notifyServer) {
        if (!red() || largeRed || phase() != 0 || disposed) return;
        largeRed = true;
        projectileRadius = LimitlessTechnique.RED_BEAM_RADIUS;
        committedAt = born + LimitlessTechnique.RED_PREP_TICKS;
        Minecraft mc = Minecraft.getInstance();
        if (notifyServer && networked && redHeld()) PacketDistributor.sendToServer(new TechniqueControl(
                TechniqueControl.COMMIT_LARGE, session, kind, 0, 0).withAim(mc.player.getYRot(), mc.player.getXRot()));
    }

    private void captureRedAim() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.mouseHandler.isMouseGrabbed()
                && (mc.mouseHandler.getXVelocity() != 0 || mc.mouseHandler.getYVelocity() != 0)) mc.mouseHandler.handleAccumulatedMovement();
        direction = mc.player.getLookAngle().normalize();
        previous = position = redMuzzle(mc, 1);
    }

    private Vec3 redMuzzle(Minecraft mc, float partial) {
        HumanoidArm arm = guideHand == InteractionHand.OFF_HAND ? mc.player.getMainArm().getOpposite() : mc.player.getMainArm();
        return LimitlessTechnique.redMuzzle(mc.player, partial, mc.player.getViewVector(partial), arm == HumanoidArm.RIGHT ? 1 : -1);
    }

    float beamLength(float partial) {
        if (fusionPreview) return previewLength;
        if (!redBeam() || beamOrigin == null) return 0;
        Vec3 front = phase() == 2 ? position : previous.lerp(position,
                Math.clamp((ClientEffectClock.ticks() + partial - motionReceivedAt)/(remote()?2:1), 0, 1));
        return (float) Math.clamp(beamOrigin.distanceTo(front), 0, LimitlessTechnique.RED_BEAM_RANGE);
    }
    void release() {
        if (remote() || !heldCharge() || disposed || launched != Long.MAX_VALUE || releaseRequested != Long.MAX_VALUE) return;
        releaseRequested = ClientEffectClock.ticks();
        if (preparingPurple()) commitPurple(releaseRequested - born >= LimitlessTechnique.PURPLE_PREP_TICKS, releaseRequested, true);
        if (networked) PacketDistributor.sendToServer(new TechniqueControl(TechniqueControl.RELEASE, session, kind, 0, 0));
    }
    private void commitPurple(boolean large, long now, boolean notifyServer) {
        if (committedAt != Long.MAX_VALUE || disposed) return;
        Minecraft mc = Minecraft.getInstance();
        // Consume a same-frame mouse flick before the input lock starts rejecting turn() calls.
        if (mc.screen == null && mc.mouseHandler.isMouseGrabbed()
                && (mc.mouseHandler.getXVelocity() != 0 || mc.mouseHandler.getYVelocity() != 0))
            mc.mouseHandler.handleAccumulatedMovement();
        committedAt = now; largePurple = large;
        if (mc.player.isPassenger()) mc.player.stopRiding();
        projectileRadius = large && heldCharge() && dev.jujutsuvfx.effect.VfxEffects.amplified(mc.player)
                ? LimitlessTechnique.PURPLE_AMPLIFIED_RADIUS
                : large ? LimitlessTechnique.PURPLE_RADIUS : LimitlessTechnique.PURPLE_MIN_RADIUS;
        lockedYaw = mc.player.getYRot();
        lockedPitch = Math.clamp(mc.player.getXRot(), -90, 90);
        direction = Vec3.directionFromRotation(lockedPitch, Mth.wrapDegrees(lockedYaw)).normalize();
        previous = position = LimitlessTechnique.purpleMuzzle(mc.player, projectileRadius, 1, direction);
        purpleGatherOrigin = LimitlessTechnique.purpleMuzzle(mc.player, fusionRadius(), 1, direction);
        holdPlayer(mc);
        // Both branches use the same unpitched air-friction recording throughout formation.
        play(PURPLE_CHARGE, position, large ? .72f : .52f, 1);
        if (notifyServer && networked && heldCharge()) PacketDistributor.sendToServer(new TechniqueControl(
                large ? TechniqueControl.COMMIT_LARGE : TechniqueControl.COMMIT_SMALL, session, kind, 0, 0)
                .withAim(lockedYaw, lockedPitch));
    }
    private void acceptCharge(TechniqueState packet) {
        if (red()) {
            if (Float.isFinite(packet.radius()) && packet.radius() > 0) {
                projectileRadius = Math.clamp(packet.radius(), .44f, LimitlessTechnique.RED_BEAM_RADIUS);
                largeRed |= projectileRadius > 1.5f;
            }
            return;
        }
        if (!purple()) return;
        if (Float.isFinite(packet.radius()) && (!largePurple || packet.radius() > 1.5f))
            projectileRadius = LimitlessTechnique.shotRadius(kind, packet.radius());
        largePurple |= projectileRadius > 1.5f;
    }
    private void launch(Vec3 point, Vec3 axis, long now) {
        if (purple() && committedAt == Long.MAX_VALUE) committedAt = now - launchTicks();
        launched = now; previous = position = point; direction = axis.normalize(); motionReceivedAt = now;
        lastServerMotion = now;
        if (redBeam()) beamOrigin = point;
        if (redHum != null) { redHum.cancel(); redHum = null; }
        nextHum = now;
        audio.stop(purple() ? PURPLE_CHARGE : CHARGE);
        play(purple() ? PURPLE_RELEASE : redBeam() ? RED_CANNON : RELEASE, point, .95f, 1);
    }
    private void hit(Vec3 point, long now) {
        impact = now; previous = position = point;
        if (launched == Long.MAX_VALUE) launched = now;
        if (purpleHum != null) purpleHum.cancel();
        if (redHum != null) { redHum.cancel(); redHum = null; }
        play(purple() ? PURPLE_END : IMPACT, point, 1.1f * range(point, radius + (purple() ? 160 : 48)), 1);
    }
    private void close(long now) {
        if (closing != Long.MAX_VALUE) return;
        closing = now; play(CLOSE, position, .62f * range(position, radius + 24), 1);
    }
    int phase() { return fusionPreview ? blue() ? 0 : 1 : blue() ? (closing == Long.MAX_VALUE ? 0 : 2) : impact != Long.MAX_VALUE ? 2 : launched != Long.MAX_VALUE ? 1 : 0; }
    float age(long now, float partial) {
        if (fusionPreview) return previewAge;
        if (purple()) return Math.max(0, synthesisAge(now, partial));
        return preparationAge(now, partial);
    }
    float flightAge(long now, float partial) {
        if(remote())return remote.phase()<CatSpell.FLIGHT?0:Math.max(0,(now-launched+partial)/20f);
        return launched == Long.MAX_VALUE ? 0 : Math.max(0, (now - launched + partial) / 20f);
    }
    float handReleaseAge(long now, float partial) {
        if (redBeam()) return impact == Long.MAX_VALUE ? -1 : Math.max(0, (now - impact + partial) / 20f);
        // Mouse release selects a charge branch; the arm returns only after the projectile launches.
        return launched == Long.MAX_VALUE ? -1 : Math.max(0, (now - launched + partial) / 20f);
    }
    float phaseAge(long now, float partial) {
        if (fusionPreview) return 1 + previewAge;
        if(remote())return (remote.stageAge()+remoteExtra(now,partial))/20f;
        long start = blue() ? (closing == Long.MAX_VALUE ? born : closing) : impact != Long.MAX_VALUE ? impact
                : launched != Long.MAX_VALUE ? launched : purple() && committedAt != Long.MAX_VALUE ? committedAt : born;
        return Math.max(0, (now - start + partial) / 20f);
    }
    float visibility(long now, float partial) {
        if (fusionPreview) return previewAlpha;
        if (!blue()) {
            if (preparingPurple()) return 0;
            float fade = purple() ? .45f : 1.25f;
            return phase() == 2 ? 1 - smooth((phaseAge(now, partial) - fade) / (LimitlessTechnique.afterglow(kind) - fade))
                    : phase() == 0 ? smooth(age(now, partial) / (purple() ? .35f : .16f)) : 1;
        }
        return smooth(age(now, partial) * 20 / LimitlessTechnique.BLUE_OPEN_TICKS)
                * (closing == Long.MAX_VALUE ? 1 : 1 - smooth((now - closing + partial) / LimitlessTechnique.BLUE_CLOSE_TICKS));
    }
    float chargeProgress(long now, float partial) {
        if (red()) {
            if (phase() != 0) return 1;
            float seconds = age(now, partial);
            if (!redHeld()) return Math.clamp(seconds, 0, 1);
            // The one-second branch adds pressure continuously; it never shrinks a full
            // small charge back to one third when the cannon branch is confirmed.
            return .45f * smooth(seconds) + (redBeam() ? .55f * smooth((seconds - 1) / 2) : 0);
        }
        if (purple()) return phase() != 0 ? 1 : Math.clamp(synthesisAge(now, partial) * 20 / synthesisTicks(), 0, 1);
        return Math.clamp(age(now, partial) * 20 / LimitlessTechnique.chargeTicks(kind), 0, 1);
    }
    float expansion(long now, float partial) { return fusionPreview ? 1 : purple() ? smooth(chargeProgress(now, partial)) : smooth(age(now, partial) * 20
            / (blue() ? LimitlessTechnique.BLUE_OPEN_TICKS : LimitlessTechnique.chargeTicks(kind))); }
    float coreRadius(long now, float partial) {
        if (fusionPreview) return previewCore;
        if (blue()) return LimitlessTechnique.blueCoreRadius(radius);
        if (phase() != 0) return purple() ? projectileRadius : projectileRadius * .70f;
        float formingRadius = amplifiedPurple() ? Mth.lerp(amplificationProgress(now, partial), fusionRadius(), projectileRadius) : projectileRadius;
        return purple() ? .045f + (formingRadius - .045f) * smooth(largePurple
                ? (chargeProgress(now, partial) - .43f) / .57f : chargeProgress(now, partial))
                : .16f;
    }
    Vec3 center(Minecraft mc, float partial) {
        if (fusionPreview) return position;
        if(remote())return redBeam()&&beamOrigin!=null?beamOrigin:previous.lerp(position,Math.clamp((ClientEffectClock.ticks()+partial-remoteReceived)*CatSpells.castRate(remote)/2,0,1));
        if (redBeam() && beamOrigin != null) return beamOrigin;
        if (redHeld() && phase() == 0) return releaseRequested != Long.MAX_VALUE && !largeRed || redReadySent ? position : redMuzzle(mc, partial);
        if (blue()) return guideHand == null ? position
                : previous.lerp(position, Math.clamp(ClientEffectClock.ticks() + partial - motionReceivedAt, 0, 1));
        if (phase() == 2) return position;
        if (committedPurple()) return amplifiedPurple() && purpleGatherOrigin != null
                ? purpleGatherOrigin.lerp(position, amplificationProgress(ClientEffectClock.ticks(), partial)) : position;
        return phase() == 0 ? LimitlessTechnique.shotMuzzle(mc.player, kind, age(ClientEffectClock.ticks(), partial), partial)
                : previous.lerp(position, Math.clamp(ClientEffectClock.ticks() + partial - motionReceivedAt, 0, 1));
    }
    Vec3 direction(Minecraft mc, float partial) { if(remote())return direction;return !blue() && phase() == 0 && !committedPurple()
            && !(redHeld() && (redReadySent || !largeRed && releaseRequested != Long.MAX_VALUE))
            ? mc.player.getViewVector(partial) : direction; }
    boolean expired(long now) {
        if(remote())return disposed||now-remoteReceived>100;
        if (disposed) return true;
        if (blue()) return closing != Long.MAX_VALUE ? now - closing >= LimitlessTechnique.BLUE_CLOSE_TICKS : now > deadline + 100;
        if (impact != Long.MAX_VALUE) return phaseAge(now, 0) >= LimitlessTechnique.afterglow(kind);
        if (launched != Long.MAX_VALUE) return amplifiedPurple() && networked
                ? now - lastServerMotion > 200 : now - launched > 700;
        if (purple()) return committedAt != Long.MAX_VALUE ? now - committedAt > 600 : now - born > 200;
        return age(now, 0) > 35;
    }
    String status() { return (blue() ? phase() == 2 ? "苍收束" : guideHand != null && !parked ? "苍持续前进" : "苍定点吸引"
            : purple() ? preparingPurple() ? "茈起手准备" : phase() == 0 ? amplifiedPurple() ? "200%茈·增幅合成" : largePurple ? "苍赫合成大茈" : "小茈自动蓄力"
                    : phase() == 1 ? "茈贯穿" : "茈消散"
            : phase() == 0 ? redBeam() ? "赫光束蓄力" : "赫凝聚"
                    : phase() == 1 ? redBeam() ? "赫光束炮" : "赫小球贯穿" : "赫斥力爆发")
            + (networked ? "（服务端控制）" : "（仅画面）"); }
    void camera(ViewportEvent.ComputeCameraAngles event, long now, float partial, float scale) {
        if(remote())scale*=range(center(Minecraft.getInstance(),partial),64);
        float age = age(now, partial), t = phaseAge(now, partial);
        float amplitude;
        if (blue()) amplitude = (.065f + .065f * (float) Math.exp(-Math.pow((age - .45f) / .22f, 2)))
                * visibility(now, partial) * range(position, radius + 14);
        else if (purple()) amplitude = preparingPurple() ? 0 : phase() == 0 ? .05f + (largePurple ? .38f : .16f) * expansion(now, partial)
                : phase() == 1 ? (1.2f * (float) Math.exp(-t * 8) + .14f * range(position, 80))
                : .55f * (float) Math.exp(-t * 5) * range(position, 160);
        else if (phase() == 0) amplitude = .06f + .28f * smooth(chargeProgress(now, partial));
        else if (phase() == 1) amplitude = redBeam() ? .16f + .85f * (float) Math.exp(-t * 12) : .80f * (float) Math.exp(-t * 13);
        else amplitude = (1.25f * (float) Math.exp(-t * 5) + .09f * (float) Math.exp(-t * 1.6f)) * range(position, radius + 32);
        if (amplifiedPurple()) amplitude *= 1.65f;
        event.setPitch(event.getPitch() + (float) Math.sin(age * 65) * amplitude * scale);
        event.setYaw(event.getYaw() + (float) Math.sin(age * 49 + 1.3) * amplitude * .52f * scale);
        event.setRoll(event.getRoll() + (float) Math.sin(age * 53 + 2.7) * amplitude * .60f * scale);
    }
    void stop() {
        disposed = true;
        Minecraft mc = Minecraft.getInstance(); var connection = mc.getConnection();
        if (!remote() && networked && connection != null && connection.hasChannel(TechniqueControl.TYPE))
            PacketDistributor.sendToServer(new TechniqueControl(TechniqueControl.CANCEL, session, kind, 0, 0));
        if (hum != null) hum.cancel(); hum = null;
        if (purpleHum != null) purpleHum.cancel(); purpleHum = null;
        if (redHum != null) redHum.cancel(); redHum = null;
        audio.stop();
    }
    private static float smooth(float value) { value = Math.clamp(value, 0, 1); return value * value * (3 - 2 * value); }
    private static SoundEvent sound(String id) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, id)); }
    private static float range(Vec3 point, float distance) {
        return (float) Math.clamp(1 - Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(point) / distance, 0, 1);
    }
    private void play(SoundEvent sound,Vec3 point,float volume,float pitch){audio.play(sound,point,volume,pitch);}
    private static final class PurpleHum extends AbstractTickableSoundInstance {
        private final LimitlessCast cast;
        PurpleHum(LimitlessCast cast) {
            super(PURPLE_FLIGHT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom()); this.cast = cast;
            looping = true; relative = true; attenuation = SoundInstance.Attenuation.NONE; delay = 0; volume = 0; pitch = 1;
        }
        public boolean canStartSilent() { return true; }
        public boolean canPlaySound() { return !isStopped() && !cast.disposed; }
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != cast.owner || mc.player == null || cast.disposed || cast.phase() != 1) { stop(); return; }
            volume += (.52f * range(cast.position, 192) - volume) * .30f;
        }
        void cancel() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
    }

    private static final class RedHum extends AbstractTickableSoundInstance {
        private final LimitlessCast cast;
        private final boolean firing;
        RedHum(LimitlessCast cast) {
            super(cast.phase() == 1 ? RED_ROAR : RED_AIR, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.cast = cast; firing = cast.phase() == 1;
            looping = true; relative = true; attenuation = SoundInstance.Attenuation.NONE; delay = 0; volume = 0; pitch = 1;
        }
        public boolean canStartSilent() { return true; }
        public void tick() {
            if (Minecraft.getInstance().level != cast.owner || cast.disposed || cast.phase() != (firing ? 1 : 0)) { stop(); return; }
            float target = firing ? .76f : .18f + .42f * cast.chargeProgress(ClientEffectClock.ticks(), 0);
            if(cast.remote())target*=range(cast.position,96);
            volume += (target - volume) * .24f;
        }
        void cancel() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
    }

    private static final class BlueHum extends AbstractTickableSoundInstance {
        private final LimitlessCast cast;
        BlueHum(LimitlessCast cast) {
            super(HUM, SoundSource.PLAYERS, SoundInstance.createUnseededRandom()); this.cast = cast;
            looping = true; relative = true; attenuation = SoundInstance.Attenuation.NONE; delay = 0; volume = 0; pitch = 1;
        }
        public boolean canStartSilent() { return true; }
        public boolean canPlaySound() { return !isStopped() && !cast.disposed; }
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != cast.owner || mc.player == null || !mc.player.isAlive() || cast.expired(ClientEffectClock.ticks())) { stop(); return; }
            long now = ClientEffectClock.ticks();
            float target = cast.visibility(now, 0) * range(cast.position, cast.radius + 24)
                    * (.50f + .055f * (float) Math.sin(cast.age(now, 0) * 3.6));
            volume += (target - volume) * .30f;
        }
        void cancel() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
    }
}
