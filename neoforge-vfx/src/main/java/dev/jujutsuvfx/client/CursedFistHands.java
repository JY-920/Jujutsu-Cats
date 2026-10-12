package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import java.util.Map;
import java.util.WeakHashMap;

/** Attaches cursed energy to the skin model's actual palm in both camera modes. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class CursedFistHands {
    private static final Map<ModelPart, FistAuraRenderer.ArmShape> ARM_SHAPES = new WeakHashMap<>();
    private CursedFistHands() {}

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) { FistAuraRenderer.beginFirstPersonFrame(); }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Pre event) { FistAuraRenderer.renderFirstPerson(event); }

    @SubscribeEvent
    public static void firstPerson(RenderHandEvent event) {
        if(MartialClient.hands(event))return;
        if(TechniqueHands.ownsHand(event.getHand(),event.getPartialTick()))return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !player.isAlive() || player.isInvisible() || player.isSpectator()
                || !VfxItems.isHeld(event.getItemStack())) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;

        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        PoseStack poses = event.getPoseStack();
        Matrix4f parent=new Matrix4f(poses.last().pose());
        event.setCanceled(true);
        poses.pushPose();
        try {
            // Attack cooldown normally lowers the held-item model. A jab keeps the fist up;
            // equip movement is used only when this hand is actually putting the item away.
            float equip = VfxItems.isHeld(player.getItemInHand(event.getHand())) ? 0 : event.getEquipProgress();
            punchPose(poses, arm, equip, event.getSwingProgress());
            FirstPersonArmSpace.apply(poses, arm);
            FirstPersonArmSpace.fromEmpty(poses,parent,arm,event.getEquipProgress(),event.getSwingProgress(),thrust(event.getSwingProgress()),false);
            FirstPersonSkin.draw(renderer,poses,event.getMultiBufferSource(),event.getPackedLight(),player,arm);
            // The vanilla call prepared the arm pose, including skin sleeve and hand-render hooks.
            var shape = palmPose(renderer.getModel(), poses, arm);
            FistAuraRenderer.draw(poses, time(player, event.getPartialTick()), power(event.getSwingProgress()),
                    true, player, arm, event.getPartialTick(), shape);
        } finally {
            poses.popPose();
        }
    }

    /** Called from the client MOD-bus AddLayers hook, once for each rebuilt skin renderer. */
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new AuraLayer(renderer));
            }
        }
    }

    private static float time(AbstractClientPlayer player, float partial) {
        return ((ClientEffectClock.ticks() % 24000L) + partial) * .05f;
    }

    private static float power(float swing) {
        return 1f + .28f * thrust(swing);
    }

    static FistAuraRenderer.ArmShape palmPose(PlayerModel<AbstractClientPlayer> model, PoseStack poses, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        FistAuraRenderer.ArmShape shape = ARM_SHAPES.computeIfAbsent(part, CursedFistHands::armShape);
        // Follow the rendered skin cube itself, without translateToHand's separate held-item offset.
        FlexibleLimbRenderer.palm(part,poses,shape.palm());
        return shape;
    }

    private static FistAuraRenderer.ArmShape armShape(ModelPart part) {
        FistAuraRenderer.ArmShape[] result = new FistAuraRenderer.ArmShape[1];
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (!path.isEmpty() || index != 0 || result[0] != null) return;
            Vector3f palm = new Vector3f((cube.minX + cube.maxX) / 32, (cube.maxY - 2) / 16,
                    (cube.minZ + cube.maxZ) / 32);
            result[0] = new FistAuraRenderer.ArmShape(palm,
                    new Vector3f(cube.minX / 16, cube.minY / 16, cube.minZ / 16).sub(palm),
                    new Vector3f(cube.maxX / 16, cube.maxY / 16, cube.maxZ / 16).sub(palm));
        });
        return result[0] != null ? result[0] : new FistAuraRenderer.ArmShape(new Vector3f(0, .5f, 0),
                new Vector3f(-.125f, -.625f, -.125f), new Vector3f(.125f, .125f, .125f));
    }

    /** Fast extension, brief contact, then a slower return; both camera modes use the same envelope. */
    public static float thrust(float swing) {
        float s = Mth.clamp(swing, 0, 1);
        if (s < .18f) return smooth(s / .18f);
        if (s < .33f) return 1;
        return 1 - smooth((s - .33f) / .67f);
    }
    private static float smooth(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    /** Place the fist in front of the camera and drive it down the sight line, without the vanilla side swing. */
    private static void punchPose(PoseStack poses, HumanoidArm arm, float equip, float swing) {
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        float jab = thrust(swing);
        poses.translate(side * (.35f - .19f * jab), -.28f + .09f * jab - .62f * equip,
                -.70f - .43f * jab);
        poses.mulPose(Axis.YP.rotationDegrees(side * (9 - 5 * jab)));
        poses.mulPose(Axis.XP.rotationDegrees(-74 - 16 * jab));
        poses.mulPose(Axis.ZP.rotationDegrees(side * (5 - 3 * jab)));
        poses.scale(1, 1 + .64f * jab, 1);
    }

    private static final class AuraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        AuraLayer(PlayerRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partial, float age,
                           float headYaw, float headPitch) {
            if (!player.isAlive() || player.isInvisible() || player.isSpectator() || !FistAuraRenderer.ready()) return;
            if(MartialClient.owns(player)) {
                hand(poses,buffers,player,InteractionHand.MAIN_HAND,player.getMainArm(),partial);
                if(player.getOffhandItem().isEmpty())hand(poses,buffers,player,InteractionHand.OFF_HAND,player.getMainArm().getOpposite(),partial);
                return;
            }
            if (VfxItems.isHeld(player.getMainHandItem())) {
                hand(poses, buffers, player, InteractionHand.MAIN_HAND, player.getMainArm(), partial);
            }
            if (VfxItems.isHeld(player.getOffhandItem())) {
                hand(poses, buffers, player, InteractionHand.OFF_HAND, player.getMainArm().getOpposite(), partial);
            }
        }

        private void hand(PoseStack poses, MultiBufferSource buffers, AbstractClientPlayer player,
                          InteractionHand hand, HumanoidArm arm, float partial) {
            poses.pushPose();
            try {
                var shape = palmPose(getParentModel(), poses, arm);
                float swing = player.swingingArm == hand ? player.getAttackAnim(partial) : 0f;
                // The renderer queues this animated palm in the current frame's world pass.
                FistAuraRenderer.draw(poses, time(player, partial), power(swing), false, player, arm, partial, shape);
            } finally {
                poses.popPose();
            }
        }
    }
}
