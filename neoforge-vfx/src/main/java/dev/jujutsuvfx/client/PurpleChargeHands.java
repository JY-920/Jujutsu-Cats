package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Small Purple lights the casting hand; amplified synthesis lights only the physical right hand. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class PurpleChargeHands {
    private static final float RETURN_START = .08f, RETURN_END = .62f;
    private static final float TWO_HAND_TRANSITION = .60f;
    private static final Map<AbstractClientPlayer, RemotePose> REMOTE = new WeakHashMap<>();
    private static final Map<ModelPart, Vector3f> TIPS = new WeakHashMap<>();
    private static final List<HandGlow> WORLD_GLOWS = new ArrayList<>();
    private static ShaderInstance shader;
    private static final List<HandGlow> FIRST_PERSON_GLOWS=new ArrayList<>(2);
    private static int firstPersonDepth;
    private record Pose(InteractionHand hand, boolean preparing, boolean large, boolean amplified,
                        float preparation, float synthesis, float release) {}
    private record HandGlow(Matrix4f transform, Matrix4f projection, Pose pose, float seed) {}
    private static final class RemotePose {
        InteractionHand hand;
        boolean using, large, amplified;
        long started, committed = -1, launched = -1, lastTick = -1;
    }
    private PurpleChargeHands() {}

    static void setShader(ShaderInstance value) { shader = value; }
    static void catGlow(PoseStack paw,float preparation,float synthesis,float release,int seed,boolean amplified) {
        if(shader==null||WORLD_GLOWS.size()>=16||synthesis<0||release>.20f)return;
        Pose pose=new Pose(InteractionHand.MAIN_HAND,false,amplified,amplified,preparation,synthesis,release);
        if(glowVisibility(pose)>.001f)WORLD_GLOWS.add(captureGlow(paw,pose,seed,false));
    }
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new SmallGlowLayer(renderer));
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        FIRST_PERSON_GLOWS.clear();
        WORLD_GLOWS.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void overlay(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (FIRST_PERSON_GLOWS.isEmpty() || shader == null || mc.level == null || mc.player == null || mc.options.hideGui
                || !mc.options.getCameraType().isFirstPerson() || !mc.player.isAlive()) {FIRST_PERSON_GLOWS.clear();return;}
        event.getGuiGraphics().flush();
        try (DomainRenderer.State ignored = new DomainRenderer.State()) { for(HandGlow glow:FIRST_PERSON_GLOWS)renderGlow(glow,true); }
        finally{FIRST_PERSON_GLOWS.clear();}
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            if (shader != null) for (HandGlow glow : WORLD_GLOWS) renderGlow(glow, false);
        } finally { WORLD_GLOWS.clear(); }
    }

    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                // The model mixin owns the gesture. A two-handed vanilla bow pose would move the idle
                // support arm during PREP/SMALL and would disappear as soon as the mouse was released.
                return null;
            }
        }, VfxItems.PURPLE.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void firstPerson(RenderHandEvent event) {
        if(TechniqueHands.ownsHand(event.getHand(),event.getPartialTick()))return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !player.isAlive() || player.isInvisible() || player.isSpectator()) return;
        Pose pose = pose(player, event.getPartialTick());
        float support = supportWeight(pose);
        // The casting-hand event draws both skin hands together. Suppress the other item/hand once,
        // even when it is empty or holds a shield; vanilla does not always draw an empty offhand.
        if (pose != null && event.getHand() != pose.hand && support > .001f) {
            event.setCanceled(true);
            return;
        }
        if (!VfxItems.isPurple(event.getItemStack())) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        Pose active = pose != null && event.getHand() == pose.hand ? pose : null;
        event.setCanceled(true);
        firstPersonDepth++;
        try {
            renderHand(event, player, renderer, arm(player, event.getHand()), active, false);
            if (active != null && support > .001f)
                renderHand(event, player, renderer, arm(player, event.getHand()).getOpposite(), active, true);
        } finally { firstPersonDepth--; }
    }

    private static void renderHand(RenderHandEvent event, AbstractClientPlayer player, PlayerRenderer renderer,
                                   HumanoidArm arm, Pose pose, boolean supporting) {
        PoseStack stack = event.getPoseStack();
        Matrix4f parent=new Matrix4f(stack.last().pose());
        stack.pushPose();
        try {
            float side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float equip = VfxItems.isPurple(player.getItemInHand(event.getHand())) ? 0 : event.getEquipProgress();
            if (supporting) {
                float weight = supportWeight(pose);
                // Bring the second hand close to the chin, with its forearm upright across the chest.
                // The vanilla cuboid has no finger/elbow joints; foreshortening keeps this a compact sign.
                stack.translate(side * (.47f - .24f * weight), -.79f + .55f * weight - .62f * equip,
                        -.74f + .20f * weight);
                stack.mulPose(Axis.YP.rotationDegrees(side * (14 + 17 * weight)));
                // With the left-hand yaw/roll below, -55 degrees sends wrist-to-fist toward
                // screen-right AND screen-up. The former -143 degrees pointed the fist downward.
                stack.mulPose(Axis.XP.rotationDegrees(-73 + 18 * weight));
                stack.mulPose(Axis.ZP.rotationDegrees(side * (7 + 29 * weight)));
                stack.mulPose(Axis.YP.rotationDegrees(-side * 38 * weight));
                stack.scale(1, 1 - .20f * weight, 1);
            } else {
                float raised = primaryWeight(pose), committed = commitment(pose) * raised;
                float release = releaseKick(pose);
                stack.translate(side * (.39f - .11f * raised - .045f * committed - .025f * release),
                        -.35f + .105f * raised + .018f * committed - .62f * equip,
                        -.75f - .13f * raised - .065f * committed - .15f * release);
                stack.mulPose(Axis.YP.rotationDegrees(side * (10 - 7 * raised)));
                stack.mulPose(Axis.XP.rotationDegrees(-74 - 9 * raised - 4 * committed - 6 * release));
                stack.mulPose(Axis.ZP.rotationDegrees(side * (8 - 5 * raised)));
                // With +Y aimed away from the player, positive local-Y roll is clockwise
                // in first person. Supinate the right hand toward palm-up; mirror for left-hand casts.
                stack.mulPose(Axis.YP.rotationDegrees(side * 52 * raised));
            }
            FirstPersonArmSpace.apply(stack, arm);
            FirstPersonArmSpace.fromEmpty(stack,parent,arm,event.getEquipProgress(),event.getSwingProgress(),supporting?supportWeight(pose):primaryWeight(pose),supporting);
            FirstPersonSkin.draw(renderer,stack,event.getMultiBufferSource(),event.getPackedLight(),player,arm);
            boolean glowingHand = pose != null && pose.amplified ? arm == HumanoidArm.RIGHT : !supporting;
            if (glowingHand && shader != null && glowVisibility(pose) > .001f&&FIRST_PERSON_GLOWS.size()<2) {
                fingertip(renderer.getModel(), stack, arm);
                FIRST_PERSON_GLOWS.add(captureGlow(stack, pose, player.getId()+(supporting?37:0), true));
            }
        } finally { stack.popPose(); }
    }

    private static Pose pose(AbstractClientPlayer player, float partial) {
        if (player == Minecraft.getInstance().player) {
            LimitlessCast cast = ClientEvents.purpleCast();
            if (cast == null || !cast.heldCharge() || cast.chargeHand() == null
                    || !VfxItems.isPurple(player.getItemInHand(cast.chargeHand()))) return null;
            long now = ClientEffectClock.ticks();
            float release = cast.handReleaseAge(now, partial);
            if (release >= RETURN_END) return null;
            return new Pose(cast.chargeHand(), cast.preparingPurple(), cast.largePurple(), cast.amplifiedPurple(),
                    cast.preparationAge(now, partial), cast.synthesisAge(now, partial), release);
        }
        LimitlessCast cast=CatSpells.playerTechnique(player.getUUID(),LimitlessTechnique.PURPLE);
        if(cast!=null){
            long now=ClientEffectClock.ticks();float release=cast.handReleaseAge(now,partial);
            if(release>=RETURN_END||!VfxItems.isPurple(player.getItemInHand(cast.chargeHand())))return null;
            return new Pose(cast.chargeHand(),cast.preparingPurple(),cast.largePurple(),cast.amplifiedPurple(),
                    cast.preparationAge(now,partial),cast.synthesisAge(now,partial),release);
        }
        var connection=Minecraft.getInstance().getConnection();
        if(connection!=null&&connection.hasChannel(dev.jujutsuvfx.network.CatSpellState.TYPE))return null;
        return remotePose(player, partial);
    }

    /** Without the local cast slot, infer only the visible gesture from vanilla item-use duration. */
    private static Pose remotePose(AbstractClientPlayer player, float partial) {
        long now = ClientEffectClock.ticks();
        boolean using = player.isUsingItem() && VfxItems.isPurple(player.getUseItem());
        RemotePose state = REMOTE.get(player);
        if (state == null) {
            if (!using) return null;
            state = new RemotePose();
            REMOTE.put(player, state);
        }
        if (state.lastTick != now) {
            if (now < state.lastTick) { state.using = false; state.hand = null; }
            state.lastTick = now;
            if (using) {
                InteractionHand hand = player.getUsedItemHand();
                if (!state.using || state.hand != hand) {
                    state.started = now - player.getTicksUsingItem();
                    state.hand = hand; state.committed = -1; state.launched = -1; state.large = false;state.amplified=false;
                }
                if (state.committed < 0 && now - state.started >= LimitlessTechnique.PURPLE_PREP_TICKS) {
                    state.large = true;
                    state.amplified=dev.jujutsuvfx.effect.VfxEffects.amplified(player);
                    state.committed = state.started + LimitlessTechnique.PURPLE_PREP_TICKS;
                    state.launched = state.committed + LimitlessTechnique.PURPLE_LARGE_CHARGE_TICKS
                            + LimitlessTechnique.PURPLE_LARGE_HOLD_TICKS;
                }
            } else if (state.using && state.committed < 0) {
                state.large = now - state.started >= LimitlessTechnique.PURPLE_PREP_TICKS;
                state.amplified=state.large&&dev.jujutsuvfx.effect.VfxEffects.amplified(player);
                state.committed = state.large ? state.started + LimitlessTechnique.PURPLE_PREP_TICKS : now;
                state.launched = state.committed + (state.large
                        ? LimitlessTechnique.PURPLE_LARGE_CHARGE_TICKS + LimitlessTechnique.PURPLE_LARGE_HOLD_TICKS
                        : LimitlessTechnique.PURPLE_SMALL_CHARGE_TICKS);
            }
            state.using = using;
        }
        if (state.hand == null || !VfxItems.isPurple(player.getItemInHand(state.hand))) {
            REMOTE.remove(player); return null;
        }
        float release = state.launched >= 0 && now + partial >= state.launched ? (now + partial - state.launched) / 20f : -1;
        if (release >= RETURN_END) return null;
        return new Pose(state.hand, state.committed < 0, state.large, state.amplified, Math.max(0, now + partial - state.started) / 20f,
                state.committed < 0 ? -1 : Math.max(0, now + partial - state.committed) / 20f, release);
    }

    private static float returning(Pose pose) {
        return pose == null ? 0 : pose.release < 0 ? 1 : 1 - gentle((pose.release - RETURN_START) / (RETURN_END - RETURN_START));
    }
    private static float primaryWeight(Pose pose) {
        return pose == null ? 0 : smooth(pose.preparation / .24f) * returning(pose);
    }
    private static float supportWeight(Pose pose) {
        return pose == null || pose.preparing || !pose.large ? 0
                : gentle(pose.synthesis / TWO_HAND_TRANSITION) * returning(pose);
    }
    private static float commitment(Pose pose) {
        if (pose == null || pose.preparing) return 0;
        // Both camera modes use the same progression for the casting arm and joining support arm.
        return pose.large ? gentle(pose.synthesis / TWO_HAND_TRANSITION) : smooth(pose.synthesis / .18f);
    }
    private static float releaseKick(Pose pose) {
        if (pose == null || pose.release < 0) return 0;
        return smooth(pose.release / .025f) * (1 - smooth((pose.release - RETURN_START) / .22f));
    }

    private static float glowVisibility(Pose pose) {
        if (pose == null || pose.preparing || pose.large&&!pose.amplified || pose.synthesis < 0) return 0;
        return smooth(pose.synthesis / (pose.amplified?.35f:.10f)) * (pose.release < 0 ? 1 : 1 - smooth(pose.release / .20f));
    }

    private static void fingertip(PlayerModel<AbstractClientPlayer> model, PoseStack stack, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        Vector3f tip = TIPS.computeIfAbsent(part, key -> {
            Vector3f[] result = {null};
            key.visit(new PoseStack(), (pose, path, index, cube) -> {
                if (path.isEmpty() && index == 0 && result[0] == null)
                    result[0] = new Vector3f((cube.minX + cube.maxX) / 32, cube.maxY / 16 + .16f,
                            (cube.minZ + cube.maxZ) / 32);
            });
            return result[0] == null ? new Vector3f(0, .785f, 0) : result[0];
        });
        part.translateAndRotate(stack);
        stack.translate(tip.x, tip.y, tip.z);
    }

    private static HandGlow captureGlow(PoseStack stack, Pose pose, float seed, boolean firstPerson) {
        Matrix4f transform = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose());
        Vector3f lift = new Vector3f(0, firstPerson ? .14f : .11f, 0);
        if (!firstPerson) lift.rotate(new Quaternionf(Minecraft.getInstance().gameRenderer.getMainCamera().rotation()).conjugate());
        transform.m30(transform.m30() + lift.x).m31(transform.m31() + lift.y).m32(transform.m32() + lift.z);
        return new HandGlow(transform, new Matrix4f(RenderSystem.getProjectionMatrix()), pose, seed);
    }

    private static void renderGlow(HandGlow glow, boolean overlay) {
        Pose pose = glow.pose;
        float visibility = glowVisibility(pose);
        if (visibility < .001f) return;
        float progress = Math.clamp(pose.synthesis * 20 / (pose.amplified
                ?LimitlessTechnique.PURPLE_LARGE_CHARGE_TICKS+LimitlessTechnique.PURPLE_LARGE_HOLD_TICKS:LimitlessTechnique.PURPLE_SMALL_CHARGE_TICKS), 0, 1);
        float aspect = FistSpriteRenderer.aspect(glow.projection);
        Vector3f view = glow.transform.transformPosition(new Vector3f());
        Vector3f center = FistSpriteRenderer.project(glow.projection, new Vector3f(view), aspect);
        Vector3f edge = FistSpriteRenderer.project(glow.projection,
                new Vector3f(view).add((pose.amplified?.24f:.29f) + .15f * smooth(progress), 0, 0), aspect);
        if (center == null || edge == null || center.z < -1 || center.z > 1 || view.lengthSquared() > 96 * 96) return;
        float size = Math.abs(edge.x - center.x);
        if (size < .0003f || Math.abs(center.x) > aspect + size || Math.abs(center.y) > 1 + size) return;
        // Start only when SMALL is committed; an ACK cannot restart the synthesis timeline.
        shader.safeGetUniform("Time").set(pose.synthesis);
        shader.safeGetUniform("Charge").set(progress);
        shader.safeGetUniform("Release").set(pose.release);
        shader.safeGetUniform("Visibility").set(visibility);
        shader.safeGetUniform("Seed").set(glow.seed);
        RenderSystem.setShader(() -> shader);
        if (overlay) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float x = center.x / aspect, y = center.y, z = Math.max(-.999f, center.z - .00001f), rx = size / aspect;
        b.addVertex(x - rx, y - size, z).setUv(-1, -1).setColor(-1);
        b.addVertex(x + rx, y - size, z).setUv(1, -1).setColor(-1);
        b.addVertex(x + rx, y + size, z).setUv(1, 1).setColor(-1);
        b.addVertex(x - rx, y + size, z).setUv(-1, 1).setColor(-1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static final class SmallGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        SmallGlowLayer(PlayerRenderer renderer) { super(renderer); }
        @Override public void render(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                     float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
            if (shader == null || WORLD_GLOWS.size() >= 16 || !player.isAlive() || player.isInvisible() || player.isSpectator()) return;
            Pose pose = pose(player, partial);
            if (glowVisibility(pose) < .001f) return;
            HumanoidArm castingHand = arm(player, pose.hand);
            HumanoidArm glowingHand = pose.amplified ? HumanoidArm.RIGHT : castingHand;
            stack.pushPose();
            try {
                fingertip(getParentModel(), stack, glowingHand);
                WORLD_GLOWS.add(captureGlow(stack, pose, player.getId() + (glowingHand != castingHand ? 37 : 0), false));
            } finally { stack.popPose(); }
        }
    }

    /** Called after vanilla setupAnim and before skin sleeves/armor copy the model's arm transforms. */
    public static void poseArms(HumanoidModel<?> model, LivingEntity entity) {
        if (firstPersonDepth > 0 || !(entity instanceof AbstractClientPlayer player)
                || !player.isAlive() || player.isInvisible() || player.isSpectator()) return;
        Pose pose = pose(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        if (pose == null) return;
        float weight = primaryWeight(pose);
        if (weight < .001f) return;
        HumanoidArm side = arm(player, pose.hand);
        ModelPart primary = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart support = side == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        float sign = side == HumanoidArm.RIGHT ? 1 : -1;
        float committed = commitment(pose), kick = releaseKick(pose);
        blend(primary, -(float) Math.PI / 2 + model.head.xRot * .88f + .08f * (1 - committed) - .06f * kick,
                model.head.yRot - sign * .13f, sign * .035f, weight);
        float supportWeight = supportWeight(pose);
        if (supportWeight > .001f) {
            // This brings the opposite skin hand near the chin; it does not copy a two-arm bow pose.
            blend(support, -2.05f + model.head.xRot * .38f, model.head.yRot * .65f + sign * .65f,
                    sign * .20f, supportWeight);
        }
    }

    private static void blend(ModelPart part, float pitch, float yaw, float roll, float weight) {
        part.xRot += (pitch - part.xRot) * weight;
        part.yRot += (yaw - part.yRot) * weight;
        part.zRot += (roll - part.zRot) * weight;
    }
    private static HumanoidArm arm(AbstractClientPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }
    private static float smooth(float value) { value = Math.clamp(value, 0, 1); return value * value * (3 - 2 * value); }
    /** Zero velocity and acceleration at both ends keep the extra hand from snapping into its pose. */
    private static float gentle(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
