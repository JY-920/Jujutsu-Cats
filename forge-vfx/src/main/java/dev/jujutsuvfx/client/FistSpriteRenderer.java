package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** One inexpensive 2D material. No ray marching, framebuffer allocation or scene copies. */
final class FistSpriteRenderer {
    static final int SPIKE = 0, ROUND = 1, GOURD = 2, LIGHTNING = 3;
    private static ShaderInstance shader;
    private FistSpriteRenderer() {}
    static void setShader(ShaderInstance value) { shader = value; }
    static boolean ready() { return shader != null; }

    static BufferBuilder begin(float time, boolean hand, float coreX, float coreY,
                               float bendX, float bendY, boolean overlay) {
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("Hand").set(hand ? 1f : 0f);
        shader.safeGetUniform("CoreHalf").set(coreX, coreY);
        shader.safeGetUniform("Bend").set(bendX, bendY);
        RenderSystem.setShader(() -> shader);
        if (overlay) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        return dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    static void finish(BufferBuilder buffer) {
        BufferBuilder.RenderedBuffer mesh = buffer.endOrDiscardIfEmpty();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /** NDC coordinates preserve the captured hand projection even during the GUI's orthographic pass. */
    static void quad(BufferBuilder b, float x, float y, float depth, float rightX, float rightY,
                     float upX, float upY, int shape, float seed, float erosion, float power, float opacity) {
        quadColored(b, x, y, depth, rightX, rightY, upX, upY, shape, seed, erosion, power, opacity, false);
    }
    static void quadColored(BufferBuilder b, float x, float y, float depth, float rightX, float rightY,
                            float upX, float upY, int shape, float seed, float erosion, float power, float opacity, boolean redFlame) {
        float uvOffset = redFlame ? 4 : 0;
        if (Math.abs(x) > 1 + Math.abs(rightX) + Math.abs(upX)
                || Math.abs(y) > 1 + Math.abs(rightY) + Math.abs(upY) || depth < -1 || depth > 1) return;
        int r = dev.jujutsuvfx.platform.Numbers.clamp(shape, 0, 3) * 64 + Math.floorMod((int) (seed * 31.17f), 64);
        int g = Math.round(dev.jujutsuvfx.platform.Numbers.clamp(erosion, 0, 1) * 255);
        int blue = Math.round(dev.jujutsuvfx.platform.Numbers.clamp(power / 1.3f, 0, 1) * 255);
        int a = Math.round(dev.jujutsuvfx.platform.Numbers.clamp(opacity, 0, 1) * 255);
        if (a == 0) return;
        vertex(b, x - rightX - upX, y - rightY - upY, depth, -1, -1 + uvOffset, r, g, blue, a);
        vertex(b, x + rightX - upX, y + rightY - upY, depth, 1, -1 + uvOffset, r, g, blue, a);
        vertex(b, x + rightX + upX, y + rightY + upY, depth, 1, 1 + uvOffset, r, g, blue, a);
        vertex(b, x - rightX + upX, y - rightY + upY, depth, -1, 1 + uvOffset, r, g, blue, a);
    }
    private static void vertex(BufferBuilder b, float x, float y, float z, float u, float v,
                               int r, int g, int blue, int a) {
        // R packs two shape bits plus six seed bits; all impact shapes and colors still share one draw.
        b.vertex(x, y, z).uv(u, v).color(r, g, blue, a).endVertex();
    }

    /** x uses height-based screen units, so screen-space rotation does not stretch on wide displays. */
    static Vector3f project(Matrix4f projection, Vector3f view, float aspect) {
        Vector4f clip = projection.transform(new Vector4f(view, 1));
        if (clip.w < .035f) return null;
        return new Vector3f(clip.x / clip.w * aspect, clip.y / clip.w, clip.z / clip.w);
    }
    static float aspect(Matrix4f projection) { return Math.abs(projection.m11() / projection.m00()); }
}
