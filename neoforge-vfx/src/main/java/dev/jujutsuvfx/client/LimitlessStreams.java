package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.core.LimitlessTechnique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Curved light filaments make Blue's attraction readable across the entire affected volume. */
final class LimitlessStreams {
    private static final int SEGMENTS = 5;
    private static ShaderInstance shader;

    private LimitlessStreams() {}
    static void setShader(ShaderInstance value) { shader = value; }
    static boolean ready() { return shader != null; }

    static void render(RenderLevelStageEvent event, LimitlessCast cast) {
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.level == null || !cast.blue()) return;
        long tick = ClientEffectClock.ticks();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = cast.visibility(tick, partial) * cast.streamsVisibility(), expansion = cast.expansion(tick, partial);
        if (strength < .002f || expansion < .025f) return;
        Vec3 camera = event.getCamera().getPosition(), center = cast.center(mc, partial);
        if (camera.distanceToSqr(center) > (cast.radius + 144) * (cast.radius + 144)) return;
        Vec3 relativeCenter = center.subtract(camera), axis = cast.direction(mc, partial).normalize();
        if (axis.lengthSqr() < .001) axis = new Vec3(0, 0, 1);
        Vec3 right = FurnaceFlight.right(axis), up = right.cross(axis).normalize();
        float core = LimitlessTechnique.blueCoreRadius(cast.radius);
        double influence = cast.radius * expansion;
        if (influence < core * .95) return;
        double age = cast.age(tick, partial);
        int count = Math.clamp(Math.round(72 + (cast.radius - 3) * 112 / 47), 72, 184);
        // Reuse six nodes for every filament; all phases derive from its index and cast session.
        Vec3[] nodes = new Vec3[SEGMENTS + 1], sides = new Vec3[SEGMENTS + 1];
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()));
            shader.safeGetUniform("CenterRelative").set((float) relativeCenter.x, (float) relativeCenter.y, (float) relativeCenter.z);
            shader.safeGetUniform("CoreRadius").set(core * .80f);
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < count; i++) {
                double seed = hash(i * 13.19 + cast.session * .731), variety = hash(i * 3.71 + 4.13);
                double cycle = fract(age * (.56 + variety * .43) + seed);
                float life = smooth((float) cycle / .09f) * (1 - smooth(((float) cycle - .87f) / .13f));
                float opacity = strength * life * (.46f + (float) variety * .42f);
                if (opacity < .006f) continue;
                double tail = Math.max(0, cycle - (.052 + variety * .052));
                double polar = hash(i * 7.37 + 19.4) * 1.84 - .92;
                double angle = hash(i * 17.63 + cast.session * .279) * Math.PI * 2;
                double twist = (1.6 + variety * 2.5) * (i % 7 == 0 ? -1 : 1);
                double width = Math.min(.080, (.023 + seed * .021) * (.75 + Math.sqrt(cast.radius / 12.0) * .42));
                for (int j = 0; j <= SEGMENTS; j++) {
                    double p = tail + (cycle - tail) * j / SEGMENTS;
                    nodes[j] = point(relativeCenter, axis, right, up, core, influence, polar, angle, twist, p);
                }
                for (int j = 0; j <= SEGMENTS; j++) {
                    Vec3 tangent = nodes[Math.min(SEGMENTS, j + 1)].subtract(nodes[Math.max(0, j - 1)]);
                    Vec3 side = tangent.cross(nodes[j].scale(-1));
                    if (side.lengthSqr() < .000001) side = right;
                    side = side.normalize();
                    // Keep billboard orientation continuous when a curved ribbon points into the camera.
                    if (j > 0 && side.dot(sides[j - 1]) < 0) side = side.scale(-1);
                    sides[j] = side.scale(width * (.76 + .24 * j / SEGMENTS));
                }
                for (int j = 0; j < SEGMENTS; j++) {
                    float v0 = (float) j / SEGMENTS, v1 = (float) (j + 1) / SEGMENTS;
                    int c0 = color(v0, opacity), c1 = color(v1, opacity);
                    vertex(b, nodes[j].subtract(sides[j]), -1, v0, c0);
                    vertex(b, nodes[j].add(sides[j]), 1, v0, c0);
                    vertex(b, nodes[j + 1].add(sides[j + 1]), 1, v1, c1);
                    vertex(b, nodes[j + 1].subtract(sides[j + 1]), -1, v1, c1);
                }
            }
            MeshData mesh = b.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }
    }

    private static Vec3 point(Vec3 center, Vec3 axis, Vec3 right, Vec3 up, float core, double reach,
                              double polar, double angle, double twist, double phase) {
        // The radius has a steadily increasing inward speed; the brighter head always leads its trail.
        double radius = core * .70 + (reach - core * .70) * (1 - Math.pow(phase, 2.15));
        double turn = angle + twist * Math.pow(phase, 1.45);
        double latitude = Math.clamp(polar + Math.sin(turn * .7 + phase * 2.1) * .075, -.97, .97);
        double circle = Math.sqrt(1 - latitude * latitude);
        return center.add(axis.scale(latitude * radius))
                .add(right.scale(Math.cos(turn) * circle * radius)).add(up.scale(Math.sin(turn) * circle * radius));
    }

    private static int color(float along, float opacity) {
        float hot = smooth((along - .20f) / .80f);
        int r = Math.round(14 + hot * 128), g = Math.round(66 + hot * 164), b = 255;
        return (Math.clamp(Math.round(opacity * 255), 0, 255) << 24) | (r << 16) | (g << 8) | b;
    }
    private static void vertex(BufferBuilder b, Vec3 p, float u, float v, int color) {
        b.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(color);
    }
    private static float smooth(float value) { value = Math.clamp(value, 0, 1); return value * value * (3 - 2 * value); }
    private static double hash(double n) { return fract(Math.sin(n * 12.9898) * 43758.5453); }
    private static double fract(double n) { return n - Math.floor(n); }
}
