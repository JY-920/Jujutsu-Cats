package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Keep the actual world view when Forge's AFTER_LEVEL supplies the projection pose stack. */
final class ForgeWorldView {
    private static final Matrix4f VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static final Matrix3f NORMAL = new Matrix3f();
    private static LevelRenderer renderer;
    private static Camera camera;
    private static int tick;
    private static boolean captured;

    private ForgeWorldView() {}

    static void capture(RenderLevelStageEvent event) {
        var stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_SKY
                && stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                && stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                && stage != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        VIEW.set(event.getPoseStack().last().pose());
        NORMAL.set(event.getPoseStack().last().normal());
        PROJECTION.set(event.getProjectionMatrix());
        renderer = event.getLevelRenderer();
        camera = event.getCamera();
        tick = event.getRenderTick();
        captured = true;
    }

    static RenderLevelStageEvent finish(RenderLevelStageEvent event) {
        PoseStack poses = new PoseStack();
        Matrix4f projection;
        if (captured && renderer == event.getLevelRenderer() && camera == event.getCamera()
                && tick == event.getRenderTick()) {
            poses.last().pose().set(VIEW);
            poses.last().normal().set(NORMAL);
            projection = new Matrix4f(PROJECTION);
        } else {
            // This is also set from the real world view by GameRenderer, before AFTER_LEVEL.
            // It includes camera roll; rebuilding only from yaw/pitch would lose domain shake.
            Matrix3f rotation = new Matrix3f(RenderSystem.getInverseViewRotationMatrix()).invert();
            poses.last().pose().set(rotation);
            poses.last().normal().set(rotation);
            projection = new Matrix4f(event.getProjectionMatrix());
        }
        clear();
        // Pass a private event context to our renderers. Never mutate/repost Forge's shared event.
        return new RenderLevelStageEvent(event.getStage(), event.getLevelRenderer(), poses, projection,
                event.getRenderTick(), event.getPartialTick(), event.getCamera(), event.getFrustum());
    }

    static void clear() {
        captured = false;
        renderer = null;
        camera = null;
    }
}
