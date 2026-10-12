package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.LimitlessTechnique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/** World refraction and a separate, directly projected first-person material. */
final class RedRenderer {
    private static final SceneSnapshot SCENE = new SceneSnapshot();
    private static ShaderInstance shader, handShader;

    private RedRenderer() {}
    static void setShader(ShaderInstance value) { shader = value; }
    static void setHandShader(ShaderInstance value) { handShader = value; }
    static void release() { SCENE.release(); }

    static void render(RenderLevelStageEvent event, LimitlessCast cast) {
        if (cast.phase() == 0 && handShader != null && RedChargeHands.handView(cast)) return;
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        Minecraft mc = Minecraft.getInstance();
        float partial = event.getPartialTick();
        draw(cast, cast.center(mc, partial).subtract(event.getCamera().getPosition()), cast.direction(mc, partial),
                projection, event.getProjectionMatrix().m11(), partial, false);
    }

    static void hand(LimitlessCast cast, Matrix4f transform, Matrix4f projection, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f tip = transform == null ? null : transform.transformPosition(new Vector3f()).add(0, .21f, 0);
        // Iris/other hand pipelines can leave a different matrix or suppress the callback.
        // Use a perspective camera socket in that case, never the HUD's orthographic matrix.
        if (projection == null || !projection.isFinite() || Math.abs(projection.m33()) > .01f
                || Math.abs(projection.m23()) < .5f) projection = mc.gameRenderer.getProjectionMatrix(70);
        Vector4f clip = tip == null ? null : projection.transform(new Vector4f(tip, 1));
        if (tip == null || !tip.isFinite() || tip.z > -.15f || tip.lengthSquared() > 9
                || clip == null || clip.w <= .01f || Math.abs(clip.x / clip.w) > .90f || Math.abs(clip.y / clip.w) > .90f)
            tip = RedChargeHands.cameraSocket(cast, partial);
        draw(cast, new Vec3(tip.x, tip.y, tip.z), new Vec3(0, 0, -1), projection, projection.m11(), partial, true);
    }

    private static void draw(LimitlessCast cast, Vec3 center, Vec3 axis, Matrix4f projection, float focalY, float partial, boolean hand) {
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = hand ? handShader : RedRenderer.shader;
        if (shader == null || mc.level == null || mc.player == null) return;
        long now = ClientEffectClock.ticks();
        float visibility = cast.visibility(now, partial);
        if (visibility < .001f) return;
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try (DomainRenderer.State ignored = new DomainRenderer.State(); SceneSnapshot.Target target = new SceneSnapshot.Target();
             OverlayState overlay = hand ? new OverlayState() : null) {
            // A hand effect must not depend on an Iris framebuffer/depth attachment.
            // Its material uses CPU-projected coordinates and premultiplied alpha.
            boolean sceneReady = !hand && SCENE.capture();
            if (!sceneReady && !hand) return;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (sceneReady) {
                shader.setSampler("SceneColor", SCENE.color());
            } else shader.setSampler("SceneColor", 0);
            shader.setSampler("SceneDepth", sceneReady && !hand ? SCENE.depth() : 0);
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            shader.safeGetUniform("ScreenSize").set((float) Math.max(1, viewport[2]), (float) Math.max(1, viewport[3]));
            shader.safeGetUniform("SceneReady").set(sceneReady ? 1f : 0f);
            shader.safeGetUniform("InverseViewProjection").set(hand ? new Matrix4f() : new Matrix4f(projection).invert());
            shader.safeGetUniform("CenterRelative").set((float) center.x, (float) center.y, (float) center.z);
            shader.safeGetUniform("Axis").set((float) axis.x, (float) axis.y, (float) axis.z);
            shader.safeGetUniform("Time").set(cast.age(now, partial));
            shader.safeGetUniform("Phase").set((float) cast.phase());
            shader.safeGetUniform("PhaseTime").set(cast.phaseAge(now, partial));
            shader.safeGetUniform("FlightTime").set(Math.max(0,cast.flightAge(now,partial)));
            shader.safeGetUniform("Charge").set(cast.chargeProgress(now, partial));
            shader.safeGetUniform("CoreRadius").set(cast.coreRadius(now, partial));
            shader.safeGetUniform("Radius").set(cast.radius);
            shader.safeGetUniform("Visibility").set(visibility);
            shader.safeGetUniform("BeamMode").set(cast.redBeam() ? 1f : 0f);
            shader.safeGetUniform("BeamLength").set(cast.beamLength(partial));
            shader.safeGetUniform("BeamRadius").set(cast.beamRadius());
            shader.safeGetUniform("UseDepth").set(hand ? 0f : 1f);
            shader.safeGetUniform("Distortion").set(mc.options.screenEffectScale().get().floatValue());
            shader.safeGetUniform("Seed").set((float) Math.floorMod(cast.session, 4096));
            Vector4f screen = projection.transform(new Vector4f((float) center.x, (float) center.y, (float) center.z, 1));
            if (screen.w > .01) {
                float pixelScale = Math.abs(focalY) / (2 * screen.w);
                shader.safeGetUniform("CoreScreen").set(screen.x / screen.w * .5f + .5f,
                        screen.y / screen.w * .5f + .5f, pixelScale, screen.z / screen.w * .5f + .5f);
            } else shader.safeGetUniform("CoreScreen").set(.5f, .5f, 0f, 1f);
            RenderSystem.setShader(() -> shader);
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            if (sceneReady) RenderSystem.disableBlend();
            else {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            BufferBuilder b = dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            b.vertex(-1, -1, 0).uv(0, 0).color(-1).endVertex(); b.vertex(1, -1, 0).uv(1, 0).color(-1).endVertex();
            b.vertex(1, 1, 0).uv(1, 1).color(-1).endVertex(); b.vertex(-1, 1, 0).uv(0, 1).color(-1).endVertex();
            BufferUploader.drawWithShader(b.end());
        } finally {
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /** GUI clipping and channel masks must not leak into or out of the hand material. */
    private static final class OverlayState implements AutoCloseable {
        private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        private final boolean red, green, blue, alpha;
        OverlayState() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                red = mask.get(0) != 0; green = mask.get(1) != 0;
                blue = mask.get(2) != 0; alpha = mask.get(3) != 0;
            }
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
        }
        public void close() {
            RenderSystem.colorMask(red, green, blue, alpha);
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }
}
