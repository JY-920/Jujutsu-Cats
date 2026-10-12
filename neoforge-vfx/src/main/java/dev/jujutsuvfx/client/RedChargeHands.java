package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/** One-arm snap cast and two-arm cannon gestures share a continuous presentation timeline. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class RedChargeHands {
    private static final float LIFT_SECONDS = .25f, SUPPORT_SECONDS = .68f;
    private static final float SMALL_RETURN = .68f, BEAM_RETURN = .90f;
    private static final Map<ModelPart, Vector3f> WRISTS = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, Remote> REMOTE = new WeakHashMap<>();
    private static HandFrame pending;
    private static int firstPersonDepth;
    private record Pose(InteractionHand hand, boolean large, float age, float release, float firing) {}
    private record HandFrame(LimitlessCast cast, Matrix4f transform, Matrix4f projection, float partial) {}
    private static final class Remote { InteractionHand hand; long born, released = -1; boolean using, large; }
    private RedChargeHands() {}

    static boolean handView(LimitlessCast cast) {
        Minecraft mc = Minecraft.getInstance();
        return cast==ClientEvents.redCast() && !cast.remote() && cast.redHeld() && mc.player != null && mc.player.isAlive() && !mc.player.isInvisible()
                && !mc.player.isSpectator() && !mc.options.hideGui && mc.options.getCameraType().isFirstPerson()
                && cast.chargeHand() != null && VfxItems.isRed(mc.player.getItemInHand(cast.chargeHand()));
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) { pending = null; }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void overlay(RenderGuiEvent.Pre event) {
        HandFrame hand = pending; pending = null;
        LimitlessCast cast = ClientEvents.redCast();
        if (cast == null || cast.phase() != 0 || !handView(cast)) return;
        event.getGuiGraphics().flush();
        // Draw directly over the completed hand pass, using its captured projection.
        // No scene/depth copy or world-ray reconstruction is needed for the hand material.
        if (hand != null && hand.cast == cast) RedRenderer.hand(cast, hand.transform, hand.projection, hand.partial);
        else RedRenderer.hand(cast, null, null, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        if(TechniqueHands.ownsHand(event.getHand(),event.getPartialTick()))return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || player.isInvisible() || !player.isAlive() || player.isSpectator()) return;
        Pose pose = pose(player, event.getPartialTick());
        float support = supportWeight(pose);
        // Render both arms from the casting-hand callback, including an otherwise absent
        // empty offhand. Suppress the native second hand/item to avoid duplicate arms.
        if (pose != null && event.getHand() != pose.hand && support > .001f) {
            event.setCanceled(true);
            return;
        }
        if (!VfxItems.isRed(event.getItemStack())) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        event.setCanceled(true);
        if (pose != null && pose.hand != event.getHand()) pose = null;
        HumanoidArm arm = arm(player, event.getHand());
        firstPersonDepth++;
        try {
            renderHand(event, player, renderer, arm, pose, false);
            if (pose != null && support > .001f) renderHand(event, player, renderer, arm.getOpposite(), pose, true);
        } finally { firstPersonDepth--; }
    }

    private static void renderHand(RenderHandEvent event, AbstractClientPlayer player, PlayerRenderer renderer,
                                   HumanoidArm arm, Pose pose, boolean supporting) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack stack = event.getPoseStack();
        Matrix4f parent=new Matrix4f(stack.last().pose());
        stack.pushPose();
        try {
            if (supporting) supportTransform(stack, side, pose);
            else primaryTransform(stack, side, pose);
            FirstPersonArmSpace.apply(stack, arm);
            FirstPersonArmSpace.fromEmpty(stack,parent,arm,event.getEquipProgress(),event.getSwingProgress(),supporting?supportWeight(pose):weight(pose),supporting);
            FirstPersonSkin.draw(renderer,stack,event.getMultiBufferSource(),event.getPackedLight(),player,arm);
            LimitlessCast cast = ClientEvents.redCast();
            if (!supporting && cast != null && cast.phase() == 0 && cast.chargeHand() == event.getHand()) {
                ModelPart part = arm == HumanoidArm.RIGHT ? renderer.getModel().rightArm : renderer.getModel().leftArm;
                wrist(part, stack);
                pending = new HandFrame(cast, new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose()),
                        new Matrix4f(RenderSystem.getProjectionMatrix()), event.getPartialTick());
            }
        } finally { stack.popPose(); }
    }

    private static void primaryTransform(PoseStack stack, float side, Pose pose) {
        float w = weight(pose), gather = gathering(pose), brace = bracing(pose), fire = pushing(pose), kick = kick(pose);
        boolean cannon = pose != null && pose.large;
        // Lift upright -> gather with the other arm -> draw back to brace -> push forward.
        float x = mix(mix(mix(.23f, .27f, gather), .23f, brace), cannon ? .23f : .18f, fire);
        float y = mix(mix(mix(-.25f, -.32f, gather), -.24f, brace), cannon ? -.09f : -.13f, fire);
        float z = mix(mix(mix(-.98f, -.88f, gather), -.80f, brace), cannon ? -.84f : -.97f, fire);
        float pitch = mix(mix(mix(2, -30, gather), -58, brace), cannon ? -84 : -81, fire);
        float yaw = mix(mix(mix(2, 12, gather), 6, brace), 2, fire);
        float roll = mix(mix(mix(2, 13, gather), 6, brace), cannon ? 3 : -3, fire);
        stack.translate(side * mix(.39f, x, w), mix(-.35f, y, w),
                mix(-.75f, z, w) + (cannon ? .10f : -.07f) * kick * w);
        stack.mulPose(Axis.YP.rotationDegrees(side * mix(10, yaw, w)));
        stack.mulPose(Axis.XP.rotationDegrees(mix(-74, pitch, w) + (cannon ? 7 : -7) * kick * w));
        stack.mulPose(Axis.ZP.rotationDegrees(side * mix(8, roll, w)));
    }

    private static void supportTransform(PoseStack stack, float side, Pose pose) {
        float w = supportWeight(pose), brace = bracing(pose), fire = pushing(pose), kick = kick(pose);
        float x = mix(mix(.24f, .20f, brace), .14f, fire);
        float y = mix(mix(-.30f, -.35f, brace), -.27f, fire);
        float z = mix(mix(-.54f, -.68f, brace), -.78f, fire);
        stack.translate(side * mix(.47f, x, w), mix(-.79f, y, w), mix(-.74f, z, w) + .045f * kick * w);
        stack.mulPose(Axis.YP.rotationDegrees(side * mix(14, mix(mix(31, 10, brace), 4, fire), w)));
        stack.mulPose(Axis.XP.rotationDegrees(mix(-73, mix(mix(-55, -68, brace), -78, fire), w)));
        // Left fist travels up and toward screen-right while gathering, then supports
        // the casting wrist from below. The right support arm mirrors that path.
        stack.mulPose(Axis.ZP.rotationDegrees(side * mix(7, mix(mix(36, 48, brace), 54, fire), w)));
        stack.mulPose(Axis.YP.rotationDegrees(-side * mix(mix(38, 20, brace), 16, fire) * w));
        stack.scale(1, 1 - .14f * w, 1);
    }

    /** Approximate neutral skin wrist when a shader omits the native hand callback. */
    static Vector3f cameraSocket(LimitlessCast cast, float partial) {
        Minecraft mc = Minecraft.getInstance();
        HumanoidArm arm = arm(mc.player, cast.chargeHand());
        PoseStack stack = new PoseStack();
        float side=arm==HumanoidArm.RIGHT?1:-1;Pose pose=localPose(cast,partial);
        primaryTransform(stack,side,pose);FirstPersonArmSpace.apply(stack,arm);
        FirstPersonArmSpace.fromEmpty(stack,new Matrix4f(),arm,0,0,weight(pose),false);
        return stack.last().pose().transformPosition(new Vector3f(-side*.375f,12f/16f,0)).add(0,.21f,0);
    }

    private static Pose localPose(LimitlessCast cast, float partial) {
        long now = ClientEffectClock.ticks();
        float release = cast.handReleaseAge(now, partial);
        if (release >= (cast.redBeam() ? BEAM_RETURN : SMALL_RETURN)) return null;
        return new Pose(cast.chargeHand(), cast.redBeam(), cast.age(now, partial), release,
                cast.phase() == 0 ? -1 : cast.flightAge(now, partial));
    }

    private static Pose pose(AbstractClientPlayer player, float partial) {
        if (player == Minecraft.getInstance().player) {
            LimitlessCast cast = ClientEvents.redCast();
            if (cast == null || !cast.redHeld() || !VfxItems.isRed(player.getItemInHand(cast.chargeHand()))) return null;
            return localPose(cast, partial);
        }
        LimitlessCast cast=CatSpells.playerTechnique(player.getUUID(),LimitlessTechnique.RED);
        if(cast!=null)return VfxItems.isRed(player.getItemInHand(cast.chargeHand()))?localPose(cast,partial):null;
        var connection=Minecraft.getInstance().getConnection();
        if(connection!=null&&connection.hasChannel(dev.jujutsuvfx.network.CatSpellState.TYPE))return null;
        long now = ClientEffectClock.ticks();
        boolean using = player.isUsingItem() && VfxItems.isRed(player.getUseItem());
        Remote remote = REMOTE.get(player);
        if (remote == null) { if (!using) return null; remote = new Remote(); REMOTE.put(player, remote); }
        if (using && (!remote.using || remote.hand != player.getUsedItemHand())) {
            remote.hand = player.getUsedItemHand(); remote.born = now - player.getTicksUsingItem(); remote.released = -1; remote.large = false;
        }
        if (using && now - remote.born >= LimitlessTechnique.RED_PREP_TICKS) remote.large = true;
        if (!using && remote.using && !remote.large) remote.released = now;
        remote.using = using;
        if (remote.hand == null || !VfxItems.isRed(player.getItemInHand(remote.hand))) { REMOTE.remove(player); return null; }
        long launch = remote.large ? remote.born + LimitlessTechnique.RED_BEAM_CHARGE_TICKS : remote.released;
        long end = remote.large ? launch + Math.round(LimitlessTechnique.RED_BEAM_RANGE / LimitlessTechnique.RED_BEAM_SPEED * 20)
                + LimitlessTechnique.RED_BEAM_HOLD_TICKS : launch;
        float release = end < 0 || now + partial < end ? -1 : (now + partial - end) / 20f;
        if (release >= (remote.large ? BEAM_RETURN : SMALL_RETURN)) return null;
        return new Pose(remote.hand, remote.large, (now + partial - remote.born) / 20f, release,
                launch < 0 || now + partial < launch ? -1 : (now + partial - launch) / 20f);
    }

    public static void poseArms(HumanoidModel<?> model, LivingEntity entity) {
        if (firstPersonDepth > 0 || !(entity instanceof AbstractClientPlayer player) || !player.isAlive()
                || player.isInvisible() || player.isSpectator()) return;
        Pose pose = pose(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        float weight = weight(pose); if (weight < .001f) return;
        HumanoidArm arm = arm(player, pose.hand);
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart support = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float gather = gathering(pose), brace = bracing(pose), fire = pushing(pose), kick = kick(pose);
        float headX = model.head.xRot, headY = model.head.yRot;
        float x = mix(mix(mix(-2.55f + headX * .12f, -2.15f + headX * .26f, gather),
                -1.85f + headX * .55f, brace), -1.57f + headX * .80f, fire);
        float y = mix(mix(mix(headY * .55f - side * .18f, headY * .70f - side * .23f, gather),
                headY * .85f - side * .08f, brace), headY * .95f - side * .045f, fire);
        float z = mix(mix(mix(-side * .10f, -side * .16f, gather), -side * .035f, brace), side * .015f, fire);
        blend(part, x + (pose.large ? .055f : -.085f) * kick, y, z, weight);
        float supportAmount = supportWeight(pose);
        if (supportAmount > .001f) {
            blend(support,
                    mix(mix(-2.05f + headX * .30f, -1.76f + headX * .62f, brace), -1.48f + headX * .78f, fire) + .045f * kick,
                    mix(mix(headY * .65f + side * .65f, headY * .85f + side * .45f, brace), headY * .95f + side * .62f, fire),
                    mix(mix(side * .20f, side * .12f, brace), side * .10f, fire), supportAmount);
        }
    }

    private static void wrist(ModelPart part, PoseStack stack) {
        Vector3f tip = WRISTS.computeIfAbsent(part, key -> {
            Vector3f[] found = {null};
            key.visit(new PoseStack(), (pose, path, index, cube) -> {
                if (path.isEmpty() && index == 0 && found[0] == null)
                    found[0] = new Vector3f((cube.minX + cube.maxX) / 32, cube.maxY / 16, (cube.minZ + cube.maxZ) / 32);
            });
            return found[0] == null ? new Vector3f(0, .625f, 0) : found[0];
        });
        part.translateAndRotate(stack);
        stack.translate(tip.x, tip.y, tip.z);
    }

    private static HumanoidArm arm(AbstractClientPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }
    private static float weight(Pose pose) {
        if (pose == null) return 0;
        float start = pose.large ? .10f : .18f, end = pose.large ? BEAM_RETURN : SMALL_RETURN;
        return gentle(pose.age / LIFT_SECONDS) * (pose.release < 0 ? 1 : 1 - gentle((pose.release - start) / (end - start)));
    }
    private static float heldAge(Pose pose) {
        // Freeze the input-stage pose at actual launch; flight must not advance its charge keyframes.
        return pose.firing < 0 ? pose.age : Math.max(0, pose.age - pose.firing);
    }
    private static float gathering(Pose pose) {
        return pose == null || !pose.large ? 0 : gentle((heldAge(pose) - LimitlessTechnique.RED_PREP_TICKS / 20f) / SUPPORT_SECONDS);
    }
    private static float bracing(Pose pose) {
        float launch = LimitlessTechnique.RED_BEAM_CHARGE_TICKS / 20f;
        return pose == null || !pose.large ? 0 : gentle((heldAge(pose) - (launch - .60f)) / .60f);
    }
    private static float pushing(Pose pose) {
        return pose == null || pose.firing < 0 ? 0 : gentle(pose.firing / (pose.large ? .10f : .055f));
    }
    private static float supportWeight(Pose pose) { return weight(pose) * gathering(pose); }
    private static float kick(Pose pose) {
        return pose == null || pose.firing < 0 ? 0 : gentle(pose.firing / .035f) * (1 - gentle((pose.firing - .06f) / .26f));
    }
    private static void blend(ModelPart part, float pitch, float yaw, float roll, float weight) {
        part.xRot += (pitch - part.xRot) * weight;
        part.yRot += (yaw - part.yRot) * weight;
        part.zRot += (roll - part.zRot) * weight;
    }
    private static float mix(float from, float to, float amount) { return from + (to - from) * amount; }
    private static float gentle(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
