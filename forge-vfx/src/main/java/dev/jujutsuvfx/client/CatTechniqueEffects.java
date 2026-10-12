package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.TechniqueFistImpact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Spell catalysts and Purple's established lightning material. Punches have their own renderer. */
public final class CatTechniqueEffects {
    private static ShaderInstance shader;
    private static final List<Hand> HANDS = new ArrayList<>();
    private record Hand(Vector3f center, Matrix4f projection, float radius, float time, float power, float seed, boolean red) {}
    private CatTechniqueEffects() {}
    static void setShader(ShaderInstance value) { shader = value; }
    static boolean ready() { return shader != null; }
    static void clear() { HANDS.clear(); }
    static void beginFrame() { HANDS.clear(); }
    static void tick() {}

    public static void receive(TechniqueFistImpact packet) {
        CatFistEffects.receive(packet);
    }

    static void hand(PoseStack poses, float time, float power, SorcererCat cat, int hand, boolean red) {
        if (shader == null || HANDS.size() >= 64) return;
        Matrix4f transform = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        if (!Float.isFinite(transform.determinant()) || Math.abs(transform.determinant()) < 1e-8f) return;
        boolean mixed = cat.hasEnergy(SorcererCat.BLUE, hand) && cat.hasEnergy(SorcererCat.RED, hand);
        Vector3f center = transform.transformPosition(new Vector3f(mixed ? (red ? .047f : -.047f) : 0, -.015f, 0));
        if (center.lengthSquared() > 72 * 72) return;
        float scale = transform.transformDirection(new Vector3f(1, 0, 0)).length();
        float radius = scale * (mixed ? .106f : .128f) * (.86f + .14f * power);
        // Match the near skin surface while preserving hardware world occlusion.
        center.z += radius * .6f;
        HANDS.add(new Hand(center, new Matrix4f(RenderSystem.getProjectionMatrix()), radius,
                time, power, Math.floorMod(cat.getId() * 19 + hand * 83, 1021), red));
    }

    static void render(RenderLevelStageEvent event) {
        if (shader == null || HANDS.isEmpty()) { HANDS.clear(); return; }
        float partial = event.getPartialTick();
        float time = ((ClientEffectClock.ticks() % 24000) + partial) * .05f;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f projection = event.getProjectionMatrix(), view = event.getPoseStack().last().pose();
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            HANDS.sort(Comparator.comparingDouble(hand -> hand.center.lengthSquared()));
            for (int i = Math.min(12, HANDS.size()) - 1; i >= 0; i--) {
                Hand hand = HANDS.get(i);
                billboard(hand.projection, hand.center, hand.radius * 2.5f,
                        hand.time, 0, hand.seed, hand.power, hand.red, false);
            }
        } finally { HANDS.clear(); }
    }

    private static void billboard(Matrix4f projection, Vector3f center, float radius, float time, float age,
                                  float seed, float power, boolean red, boolean impact) {
        float aspect = FistSpriteRenderer.aspect(projection);
        Vector3f p = FistSpriteRenderer.project(projection, center, aspect);
        Vector3f side = FistSpriteRenderer.project(projection, new Vector3f(center).add(radius, 0, 0), aspect);
        if (p == null || side == null || p.z < -1 || p.z > 1) return;
        float extent = Math.abs(side.x - p.x);
        if (extent < .0002f || Math.abs(p.x) > aspect + extent || Math.abs(p.y) > 1 + extent) return;
        BufferBuilder b = begin(time, age, seed, power, red, impact ? 1 : 0);
        float x = p.x / aspect, y = p.y, rx = extent / aspect;
        vertex(b, x - rx, y - extent, p.z, -1, -1, 1, 1, 1, 1);
        vertex(b, x + rx, y - extent, p.z, 1, -1, 1, 1, 1, 1);
        vertex(b, x + rx, y + extent, p.z, 1, 1, 1, 1, 1, 1);
        vertex(b, x - rx, y + extent, p.z, -1, 1, 1, 1, 1, 1);
        finish(b);
    }

    static void line(BufferBuilder b, Matrix4f projection, Matrix4f view, Vec3 camera, float aspect,
                             Vec3 from, Vec3 to, float width, float r, float g, float blue, float alpha) {
        Vector3f aView = view.transformPosition(relative(from, camera)), bView = view.transformPosition(relative(to, camera));
        Vector3f a = FistSpriteRenderer.project(projection, aView, aspect), end = FistSpriteRenderer.project(projection, bView, aspect);
        Vector3f size = FistSpriteRenderer.project(projection, new Vector3f(aView).add(width, 0, 0), aspect);
        if (a == null || end == null || size == null || a.z < -1 || end.z < -1 || a.z > 1 || end.z > 1) return;
        float dx = end.x - a.x, dy = end.y - a.y, length = Mth.sqrt(dx * dx + dy * dy);
        if (length < .00001f) return;
        float w = Math.abs(size.x - a.x), x = -dy / length * w / aspect, y = dx / length * w;
        vertex(b, a.x / aspect - x, a.y - y, a.z, -1, -1, r, g, blue, alpha);
        vertex(b, end.x / aspect - x, end.y - y, end.z, -1, 1, r, g, blue, alpha);
        vertex(b, end.x / aspect + x, end.y + y, end.z, 1, 1, r, g, blue, alpha);
        vertex(b, a.x / aspect + x, a.y + y, a.z, 1, -1, r, g, blue, alpha);
    }
    static BufferBuilder begin(float time, float age, float seed, float power, boolean red, int pass) {
        shader.safeGetUniform("Time").set(time); shader.safeGetUniform("Age").set(age);
        shader.safeGetUniform("Seed").set(seed); shader.safeGetUniform("Power").set(power);
        shader.safeGetUniform("Kind").set(red ? 1f : 0f); shader.safeGetUniform("Pass").set((float) pass);
        RenderSystem.setShader(() -> shader); RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        return dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }
    static void vertex(BufferBuilder b, float x, float y, float z, float u, float v, float r, float g, float blue, float alpha) {
        b.vertex(x, y, z).uv(u, v).color(r, g, blue, alpha).endVertex();
    }
    static void finish(BufferBuilder b) { BufferBuilder.RenderedBuffer mesh = b.endOrDiscardIfEmpty(); if (mesh != null) BufferUploader.drawWithShader(mesh); }
    private static float smooth(float x) { float t = Mth.clamp(x, 0, 1); return t * t * (3 - 2 * t); }
    private static float hash(float seed) { double n = Math.sin(seed * 127.1) * 43758.5453; return (float) (n - Math.floor(n)); }
    private static Vector3f relative(Vec3 p, Vec3 camera) { return new Vector3f((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z)); }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
}
