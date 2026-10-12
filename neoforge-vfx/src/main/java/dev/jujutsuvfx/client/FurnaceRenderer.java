package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import dev.jujutsuvfx.core.FurnaceFlight;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** Half-resolution bounded volume, then depth-aware reconstruction and heat refraction. */
final class FurnaceRenderer {
    private static ShaderInstance volume, composite;
    private static final SceneSnapshot SCENE = new SceneSnapshot();
    private static TextureTarget fire;
    static void setVolume(ShaderInstance shader) { volume = shader; }
    static void setComposite(ShaderInstance shader) { composite = shader; }
    static boolean ready() { return volume != null && composite != null; }
    static void release() { SCENE.release(); if (fire != null) { fire.destroyBuffers(); fire = null; } }

    static void render(RenderLevelStageEvent event, FurnaceCast cast) {
        Minecraft mc = Minecraft.getInstance(); if (!ready() || mc.level == null || mc.player == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = ClientEffectClock.ticks();
        Vec3 center = cast.center(mc, partial).subtract(event.getCamera().getPosition());
        Vec3 direction = cast.direction(mc, partial), right = FurnaceFlight.right(direction), up = right.cross(direction).normalize();
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Matrix4f inverse = new Matrix4f(projection).invert();
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float[] clear = new float[4]; GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try (DomainRenderer.State ignored = new DomainRenderer.State(); SceneSnapshot.Target target = new SceneSnapshot.Target()) {
            if (!SCENE.capture()) return;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int width = Math.max(1, (viewport[2]+1)/2), height = Math.max(1, (viewport[3]+1)/2);
            // Allocation also binds a framebuffer, so restore the caller before the composite pass.
            try (SceneSnapshot.Target original = new SceneSnapshot.Target()) {
                if (fire == null || fire.width != width || fire.height != height) {
                    if (fire != null) fire.destroyBuffers();
                    fire = new TextureTarget(width, height, false, Minecraft.ON_OSX);
                    fire.setFilterMode(GL11.GL_NEAREST);
                }
                fire.bindWrite(true);
                volume.setSampler("SceneDepth", SCENE.depth());
                volume.safeGetUniform("InverseViewProjection").set(inverse);
                set(volume, "CenterRelative", center); set(volume, "Axis", direction); set(volume, "Right", right); set(volume, "Up", up);
                volume.safeGetUniform("Time").set(cast.age(now, partial));
                volume.safeGetUniform("Phase").set((float) cast.phase());
                volume.safeGetUniform("PhaseTime").set(cast.phaseAge(now, partial));
                volume.safeGetUniform("Radius").set(cast.radius);
                draw(volume);
            }
            composite.setSampler("SceneColor", SCENE.color()); composite.setSampler("SceneDepth", SCENE.depth());
            composite.setSampler("FireLayer", fire.getColorTextureId());
            composite.safeGetUniform("InverseViewProjection").set(inverse);
            set(composite, "CenterRelative", center);
            composite.safeGetUniform("Time").set(cast.age(now, partial));
            composite.safeGetUniform("Phase").set((float) cast.phase());
            composite.safeGetUniform("PhaseTime").set(cast.phaseAge(now, partial));
            composite.safeGetUniform("Radius").set(cast.radius);
            composite.safeGetUniform("Distortion").set(mc.options.screenEffectScale().get().floatValue());
            composite.safeGetUniform("FlashEnabled").set(dev.jujutsuvfx.config.VfxConfig.flashScale());
            Vector4f projected = projection.transform(new Vector4f((float) center.x, (float) center.y, (float) center.z, 1));
            if (projected.w > .01f) {
                float screenRadius = Math.clamp(cast.radius * event.getProjectionMatrix().m11() / (projected.w * 2), .005f, 3);
                composite.safeGetUniform("BurstScreen").set(projected.x/projected.w*.5f+.5f,
                        projected.y/projected.w*.5f+.5f, screenRadius, projected.z/projected.w*.5f+.5f);
            } else composite.safeGetUniform("BurstScreen").set(.5f,.5f,0f,1f);
            draw(composite);
        } finally {
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
    private static void set(ShaderInstance shader, String name, Vec3 p) {
        shader.safeGetUniform(name).set((float) p.x, (float) p.y, (float) p.z);
    }
    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(-1,-1,1).setUv(0,0).setColor(-1); b.addVertex(1,-1,1).setUv(1,0).setColor(-1);
        b.addVertex(1,1,1).setUv(1,1).setColor(-1); b.addVertex(-1,1,1).setUv(0,1).setColor(-1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
