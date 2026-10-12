package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.jujutsuvfx.core.RiftShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Method;
import java.util.List;

final class RiftRenderer {
    private static ShaderInstance shader;
    private static TextureTarget scene;
    private static boolean irisChecked;
    private static Object irisApi;
    private static Method irisEnabled;

    private RiftRenderer() {}
    static void setShader(ShaderInstance instance) { shader = instance; }
    static boolean ready() { return shader != null; }

    static boolean shaderPackActive() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisEnabled = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException ignored) { irisApi = null; }
        }
        try {
            return irisApi != null && (boolean) irisEnabled.invoke(irisApi);
        } catch (ReflectiveOperationException ignored) { return true; }
    }

    static void release() {
        if (scene != null) { scene.destroyBuffers(); scene = null; }
    }

    /** Copy once per frame, never sample the framebuffer we are currently writing. */
    private static void copyScene(RenderTarget main) {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            if (scene == null || scene.width != main.width || scene.height != main.height) {
                release();
                scene = new EffectTarget(main.width, main.height, true, Minecraft.ON_OSX);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, scene.width, scene.height,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    static void render(RenderLevelStageEvent event, List<Rift> rifts, MalevolentShrine shrine) {
        if (shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Rift> visible = rifts.stream().filter(r -> !r.slash()
                && camera.distanceToSqr(r.center()) < 128 * 128
                && event.getFrustum().isVisible(new AABB(r.center(), r.center())
                .inflate(r.height() + r.launchOffset().length()))).toList();
        if (visible.isEmpty()) return;

        ShaderInstance oldShader = RenderSystem.getShader();
        boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean oldDepthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean refract = !shaderPackActive() && visible.stream().anyMatch(r -> !r.slash());
        try {
            if (refract) copyScene(mc.getMainRenderTarget());
            shader.setSampler("SceneColor", scene == null ? 0 : scene.getColorTextureId());
            shader.setSampler("SceneDepth", scene == null ? 0 : scene.getDepthTextureId());
            shader.safeGetUniform("UseScene").set(refract ? 1f : 0f);
            shader.safeGetUniform("Viewport").set((float) mc.getMainRenderTarget().width,
                    (float) mc.getMainRenderTarget().height);
            shader.safeGetUniform("RiftView").set(event.getPoseStack().last().pose());
            shader.safeGetUniform("RiftProjection").set(event.getProjectionMatrix());
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            float partial = event.getPartialTick();
            DomainRenderer.setRiftBoundary(shader,shrine,camera,ClientEffectClock.ticks(),partial);

            // Draw the slit cores before their separate edge pass; opening also controls their fade.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            for (Rift r : visible) drawRift(r, camera, r.age(ClientEffectClock.ticks(), partial), false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            for (Rift r : visible) if (!r.slash()) drawRift(r, camera, r.age(ClientEffectClock.ticks(), partial), true);
        } finally {
            RenderSystem.depthMask(oldDepthWrite);
            RenderSystem.depthFunc(oldDepthFunc);
            if (oldDepth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (oldCull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (oldBlend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> oldShader);
        }
    }

    private static void drawRift(Rift r, Vec3 camera, float age, boolean aura) {
        float opening = r.opening(age);
        if (opening < 0.005f) return;
        r = r.pose(age);
        float w = r.halfWidth(opening), h = r.halfHeight(opening), d = r.halfDepth(opening);
        Vec3 eye = camera.subtract(r.center());
        shader.safeGetUniform("EyeLocal").set((float) eye.dot(r.right()),
                (float) eye.dot(r.up()), (float) eye.dot(r.normal()));
        shader.safeGetUniform("HalfSize").set(w, h);
        // A lingering scar stays still instead of looping its cutting/ripple animation.
        shader.safeGetUniform("Time").set(r.slash() ? Math.min(age, RiftShape.STRIKE_SECONDS) : age);
        shader.safeGetUniform("CutProgress").set(r.slash() ? RiftShape.cutProgress(age) : 1f);
        shader.safeGetUniform("Seed").set(r.seed());
        shader.safeGetUniform("Opening").set(opening);
        shader.safeGetUniform("Strike").set(r.slash() ? 1f : 0f);
        shader.safeGetUniform("Impact").set(r.slash() ? RiftShape.impact(age) : 0f);
        if (aura) {
            // Only the near face carries the halo; both sides remain visible.
            float sign = eye.dot(r.normal()) >= 0 ? 1 : -1;
            shader.safeGetUniform("Surface").set(2f);
            face(r, camera, w, h, sign * (d + 0.008f), 1.65f, 1.08f);
        } else {
            shader.safeGetUniform("Surface").set(0f);
            face(r, camera, w, h, d, 1.15f, 1f);
            face(r, camera, w, h, -d, 1.15f, 1f);
            if (r.slash()) return; // Thin cuts use the dedicated emissive edge pass instead of extruded walls.
            shader.safeGetUniform("Surface").set(1f);
            BufferBuilder buffer = dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            int segments = r.slash() ? 40 : 96;
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < segments; i++) {
                    float y0 = -1 + i * 2f / segments, y1 = -1 + (i + 1) * 2f / segments;
                    float x0 = RiftShape.spine(y0) + side * RiftShape.width(y0);
                    float x1 = RiftShape.spine(y1) + side * RiftShape.width(y1);
                    vertex(buffer, r, camera, x0 * w, y0 * h, -d, 0, y0);
                    vertex(buffer, r, camera, x1 * w, y1 * h, -d, 0, y1);
                    vertex(buffer, r, camera, x1 * w, y1 * h, d, 1, y1);
                    vertex(buffer, r, camera, x0 * w, y0 * h, d, 1, y0);
                }
            }
            BufferUploader.drawWithShader(buffer.end());
        }
    }

    private static void face(Rift r, Vec3 camera, float w, float h, float z, float sx, float sy) {
        shader.safeGetUniform("FaceZ").set(z);
        BufferBuilder buffer = dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(buffer, r, camera, -sx * w, -sy * h, z, -sx, -sy);
        vertex(buffer, r, camera, sx * w, -sy * h, z, sx, -sy);
        vertex(buffer, r, camera, sx * w, sy * h, z, sx, sy);
        vertex(buffer, r, camera, -sx * w, sy * h, z, -sx, sy);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void vertex(BufferBuilder buffer, Rift r, Vec3 camera,
                               float x, float y, float z, float u, float v) {
        Vec3 p = r.point(x, y, z).subtract(camera);
        buffer.vertex((float) p.x, (float) p.y, (float) p.z).uv(u, v).color(0xFFFFFFFF).endVertex();
    }
}
