package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.SukunaSlashCharge;
import dev.jujutsuvfx.item.SukunaSlashItem;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.RenderHandEvent;

import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;

/** Use-driven hand gestures, isolated from every other technique's hand callback. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class SukunaSlashHands {
    private static final Map<AbstractClientPlayer, Motion> MOTIONS = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, RemoteInput> REMOTE = new WeakHashMap<>();
    private static ClientLevel owner;
    private static AbstractClientPlayer localPlayer;
    private static long localReadyAt;
    private static int firstPersonDepth;
    private static final class Motion {
        final SukunaSlashItem.Kind kind;
        final InteractionHand hand;
        final long born;
        long releaseAt = -1;
        boolean observedUsing, burst;
        Motion(SukunaSlashItem.Kind kind, InteractionHand hand, long born) {
            this.kind = kind; this.hand = hand; this.born = born;
        }
    }
    private record Pose(SukunaSlashItem.Kind kind, InteractionHand hand, float age,
                        float chargeAge, float release, boolean released, boolean burst) {}
    private static final class RemoteInput { boolean using, swinging; InteractionHand hand; long readyAt; }
    private record Frame(float time, float x, float y, float z, float pitch, float yaw, float roll) {}

    // Both tracks start and finish at the same neutral wrist. The fast middle interval is
    // the actual stroke; the longer final interval lets the hand visibly return to rest.
    private static final Frame[] DISMANTLE = {
            new Frame(0, .39f, -.35f, -.75f, -74, 10, 8),
            new Frame(.15f, .30f, -.16f, -.76f, -55, 15, -35),
            new Frame(.23f, .48f, -.08f, -.87f, -55, 17, -45),
            new Frame(.34f, -.14f, -.16f, -1.03f, -82, -22, 26),
            new Frame(.50f, -.20f, -.25f, -.97f, -87, -26, 32),
            new Frame(1, .39f, -.35f, -.75f, -74, 10, 8)
    };
    private static final Frame[] CLEAVE_CHARGE = {
            new Frame(0, .39f, -.35f, -.75f, -74, 10, 8),
            new Frame(.27f, .24f, .02f, -.84f, -6, 3, -11),
            new Frame(SukunaSlashCharge.CHARGE_TICKS / 20f, .25f, .06f, -.79f, 10, 4, -13)
    };
    private static final Frame CLEAVE_WIND = new Frame(.04f, .25f, -.05f, -.83f, -38, 4, -8);
    private static final Frame CLEAVE_IMPACT = new Frame(SukunaSlashCharge.FIRST_CUT_DELAY / 20f,
            .13f, -.29f, -1.04f, -98, 1, 13);
    private static final Frame CLEAVE_CONTROL = new Frame(.25f, .02f, -.24f, -.98f, -88, -6, 20);

    private SukunaSlashHands() {}

    public static void install() {
        SukunaSlashItem.setClientActions((player, kind, hand) -> {
            Minecraft mc = Minecraft.getInstance();
            ensureWorld(mc);
            if (player != mc.player || mc.level == null || !(player instanceof AbstractClientPlayer clientPlayer)) return;
            long now = ClientEffectClock.ticks();
            // Holding use owns the charge; the full recovery cooldown starts on release.
            if (now < localReadyAt) return;
            TechniqueHands.interrupt();
            localReadyAt = now + (kind == SukunaSlashItem.Kind.CLEAVE ? 2 : 20);
            Motion motion = new Motion(kind, hand, now);
            motion.observedUsing = true;
            MOTIONS.put(clientPlayer, motion);
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        ensureWorld(Minecraft.getInstance());
        long now = ClientEffectClock.ticks();
        MOTIONS.forEach((player, motion) -> updateRelease(player, motion, now));
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(); owner = null; localPlayer = null;
    }

    private static void ensureWorld(Minecraft mc) {
        if (owner == mc.level && localPlayer == mc.player) return;
        clear(); owner = mc.level; localPlayer = mc.player;
    }

    private static void clear() { MOTIONS.clear(); REMOTE.clear(); localReadyAt = 0; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        if(TechniqueHands.ownsHand(event.getHand(),event.getPartialTick()))return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !player.isAlive() || player.isInvisible() || player.isSpectator()
                || !VfxItems.isSukunaSlash(event.getItemStack())) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        Pose pose = pose(player, event.getPartialTick());
        if (pose != null && pose.hand != event.getHand()) pose = null;
        HumanoidArm arm = arm(player, event.getHand());
        PoseStack stack = event.getPoseStack();
        Matrix4f parent=new Matrix4f(stack.last().pose());
        event.setCanceled(true);
        stack.pushPose();
        firstPersonDepth++;
        try {
            Frame frame = sample(pose);
            float side = arm == HumanoidArm.RIGHT ? 1 : -1;
            // Preserve the native equip animation only while this particular item is leaving.
            float equip = VfxItems.isSukunaSlash(player.getItemInHand(event.getHand())) ? 0 : event.getEquipProgress();
            stack.translate(side * frame.x, frame.y - equip * .60f, frame.z);
            stack.mulPose(Axis.YP.rotationDegrees(side * frame.yaw));
            stack.mulPose(Axis.XP.rotationDegrees(frame.pitch));
            stack.mulPose(Axis.ZP.rotationDegrees(side * frame.roll));
            FirstPersonArmSpace.apply(stack, arm);
            float weight=pose==null?0:pose.kind==SukunaSlashItem.Kind.DISMANTLE
                    ?smooth(pose.age/.12f)*(1-smooth((pose.age-.65f)/.35f))
                    :smooth((pose.chargeAge+(pose.released?pose.age:0))/.14f)*(1-pose.release);
            FirstPersonArmSpace.fromEmpty(stack,parent,arm,event.getEquipProgress(),event.getSwingProgress(),weight,false);
            FirstPersonSkin.draw(renderer,stack,event.getMultiBufferSource(),event.getPackedLight(),player,arm);
        } finally {
            firstPersonDepth--;
            stack.popPose();
        }
    }

    public static void poseArms(HumanoidModel<?> model, LivingEntity entity) {
        if (firstPersonDepth > 0 || !(entity instanceof AbstractClientPlayer player) || !player.isAlive()
                || player.isInvisible() || player.isSpectator()) return;
        Pose pose = pose(player, Minecraft.getInstance().getFrameTime());
        if (pose == null) return;
        boolean heavy = pose.kind == SukunaSlashItem.Kind.CLEAVE;
        float cut = heavy ? dev.jujutsuvfx.platform.Numbers.clamp((10 - sampleCleave(pose).pitch) / 108, 0, 1)
                : smooth((pose.age - .23f) / .11f);
        float settle = heavy ? pose.release : smooth((pose.age - .50f) / .50f);
        float raisedAge = heavy && pose.released ? pose.chargeAge + pose.age : pose.age;
        float weight = smooth(raisedAge / (heavy ? .27f : .15f)) * (1 - settle);
        if (weight < .001f) return;
        HumanoidArm arm = arm(player, pose.hand);
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float headPitch = model.head.xRot, headYaw = model.head.yRot;
        float pitch = heavy ? mix(-2.94f, -1.13f, cut) : mix(-1.42f, -1.61f, cut);
        float yaw = heavy ? mix(-side * .09f, side * .12f, cut) : mix(-side * .80f, side * .78f, cut);
        float roll = heavy ? mix(-side * .12f, side * .10f, cut) : mix(-side * .25f, side * .16f, cut);
        blend(part, pitch + headPitch * .72f, yaw + headYaw * .75f, roll, weight);
    }

    private static Pose pose(AbstractClientPlayer player, float partial) {
        ensureWorld(Minecraft.getInstance());
        long now = ClientEffectClock.ticks();
        if (player != Minecraft.getInstance().player) updateRemote(player, now);
        Motion motion = MOTIONS.get(player);
        if (motion == null) return null;
        float age = (now + partial - motion.born) / 20f;
        if (age < 0 || (motion.kind != SukunaSlashItem.Kind.CLEAVE && age >= 1)
                || !(player.getItemInHand(motion.hand).getItem() instanceof SukunaSlashItem item)
                || item.kind() != motion.kind) {
            MOTIONS.remove(player);
            return null;
        }
        updateRelease(player, motion, now);
        float release = 0, chargeAge = age;
        boolean released = motion.releaseAt >= 0;
        if (released) {
            age = (now + partial - motion.releaseAt) / 20f;
            chargeAge = (motion.releaseAt - motion.born) / 20f;
            release = smooth((age - releaseEnd(motion.burst)) / .32f);
            if (release >= 1) { MOTIONS.remove(player); return null; }
        }
        return new Pose(motion.kind, motion.hand, age, chargeAge, release, released, motion.burst);
    }

    private static void updateRelease(AbstractClientPlayer player, Motion motion, long now) {
        if (motion.kind != SukunaSlashItem.Kind.CLEAVE || motion.releaseAt >= 0) return;
        boolean using = player.isUsingItem() && player.getUsedItemHand() == motion.hand
                && player.getUseItem().getItem() instanceof SukunaSlashItem item && item.kind() == motion.kind;
        if (using) { motion.observedUsing = true; return; }
        // Remote swing and use metadata need not arrive in the same frame.
        if (!motion.observedUsing && now - motion.born < 4) return;
        motion.releaseAt = now;
        motion.burst = now - motion.born >= SukunaSlashCharge.CHARGE_TICKS;
        int recovery = motion.burst ? SukunaSlashCharge.BURST_COOLDOWN : SukunaSlashCharge.SHORT_COOLDOWN;
        if (player == Minecraft.getInstance().player) localReadyAt = Math.max(localReadyAt, now + recovery);
        else REMOTE.computeIfAbsent(player, key -> new RemoteInput()).readyAt = now + recovery;
    }

    private static void updateRemote(AbstractClientPlayer player, long now) {
        RemoteInput input = REMOTE.computeIfAbsent(player, key -> new RemoteInput());
        boolean using = player.isUsingItem() && VfxItems.isSukunaSlash(player.getUseItem());
        InteractionHand hand = using ? player.getUsedItemHand() : player.swingingArm;
        boolean swinging = player.swinging && hand != null && VfxItems.isSukunaSlash(player.getItemInHand(hand));
        Motion active = MOTIONS.get(player);
        boolean continuing = active != null && active.kind == SukunaSlashItem.Kind.CLEAVE && active.hand == hand
                && (active.releaseAt < 0 || now < active.releaseAt + SukunaSlashCharge.BURST_COOLDOWN);
        if (hand != null && player.getItemInHand(hand).getItem() instanceof SukunaSlashItem item
                && !continuing
                && ((using && (!input.using || input.hand != hand)) || (swinging && !input.swinging))) {
            // The use metadata and vanilla swing packet can arrive separately for one press.
            // Keep the latch even if an equipment change has removed the visible motion.
            if (now >= input.readyAt) {
                long elapsed = using ? item.kind() == SukunaSlashItem.Kind.CLEAVE ? player.getTicksUsingItem()
                        : Math.min(player.getTicksUsingItem(), 20) : 0;
                Motion motion = new Motion(item.kind(), hand, now - elapsed);
                motion.observedUsing = using;
                MOTIONS.put(player, motion);
                input.readyAt = now + (item.kind() == SukunaSlashItem.Kind.CLEAVE ? 2 : 20) - elapsed;
            }
        }
        input.using = using;
        input.swinging = swinging;
        input.hand = hand;
    }

    private static Frame sample(Pose pose) {
        if (pose == null) return DISMANTLE[0];
        Frame frame = pose.kind == SukunaSlashItem.Kind.CLEAVE ? sampleCleave(pose) : sample(DISMANTLE, pose.age);
        return interpolate(frame, DISMANTLE[0], pose.release);
    }

    private static Frame sampleCleave(Pose pose) {
        Frame charge = sampleCharge(pose.chargeAge);
        if (!pose.released) return charge;
        float age = Math.min(pose.age, releaseEnd(pose.burst));
        if (age < CLEAVE_WIND.time) return interpolate(charge, CLEAVE_WIND, smooth(age / CLEAVE_WIND.time));
        if (age < CLEAVE_IMPACT.time) return interpolate(CLEAVE_WIND, CLEAVE_IMPACT,
                smooth((age - CLEAVE_WIND.time) / (CLEAVE_IMPACT.time - CLEAVE_WIND.time)));
        if (age < CLEAVE_CONTROL.time) return interpolate(CLEAVE_IMPACT, CLEAVE_CONTROL,
                smooth((age - CLEAVE_IMPACT.time) / (CLEAVE_CONTROL.time - CLEAVE_IMPACT.time)));
        if (!pose.burst) return CLEAVE_CONTROL;
        // One release controls the whole finite burst. Small wrist pulses convey the
        // rapid follow-up cuts without restarting a full arm swing for each fragment.
        float pulse = (float) Math.sin((age - CLEAVE_CONTROL.time) * 20f * Math.PI / SukunaSlashCharge.BURST_INTERVAL);
        return new Frame(age, CLEAVE_CONTROL.x, CLEAVE_CONTROL.y + pulse * .008f, CLEAVE_CONTROL.z,
                CLEAVE_CONTROL.pitch + pulse * 2, CLEAVE_CONTROL.yaw, CLEAVE_CONTROL.roll + pulse * 2);
    }

    private static Frame sampleCharge(float age) {
        Frame frame = sample(CLEAVE_CHARGE, age);
        float charged = smooth((age - SukunaSlashCharge.CHARGE_TICKS / 20f) / .2f);
        float tension = (float) Math.sin(age * 13) * charged;
        return new Frame(age, frame.x, frame.y + tension * .003f, frame.z,
                frame.pitch + tension * .8f, frame.yaw, frame.roll);
    }

    private static float releaseEnd(boolean burst) {
        return burst ? (SukunaSlashCharge.FIRST_CUT_DELAY
                + (SukunaSlashCharge.BURST_CUTS - 1) * SukunaSlashCharge.BURST_INTERVAL) / 20f : .28f;
    }

    private static Frame sample(Frame[] track, float age) {
        for (int i = 1; i < track.length; i++) {
            Frame a = track[i - 1], b = track[i];
            if (age > b.time) continue;
            return interpolate(a, b, smooth((age - a.time) / (b.time - a.time)));
        }
        return track[track.length - 1];
    }

    private static Frame interpolate(Frame a, Frame b, float weight) {
        return new Frame(mix(a.time, b.time, weight), mix(a.x, b.x, weight), mix(a.y, b.y, weight), mix(a.z, b.z, weight),
                mix(a.pitch, b.pitch, weight), mix(a.yaw, b.yaw, weight), mix(a.roll, b.roll, weight));
    }

    private static HumanoidArm arm(AbstractClientPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }
    private static void blend(ModelPart part, float pitch, float yaw, float roll, float weight) {
        part.xRot += (pitch - part.xRot) * weight;
        part.yRot += (yaw - part.yRot) * weight;
        part.zRot += (roll - part.zRot) * weight;
    }
    private static float mix(float from, float to, float amount) { return from + (to - from) * amount; }
    private static float smooth(float value) {
        float t = dev.jujutsuvfx.platform.Numbers.clamp(value, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
