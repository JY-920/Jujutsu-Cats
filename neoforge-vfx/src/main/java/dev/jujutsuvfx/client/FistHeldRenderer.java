package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;

/** Held effects have their own material; changing them cannot change the accepted impact sprites. */
final class FistHeldRenderer {
    /** Flame-up in arm axes; canvas sizes and natural flame height are measured in flame half-widths. */
    record FlameSpace(float upX, float upY, float canvasX, float canvasY, float aspect) {}
    private static ShaderInstance shader;
    private static final double ANIMATION_FPS = 24.0;
    private static final int SEGMENTS = 12, FORK_SEGMENTS = 5;
    // Render-thread scratch space: the base flame plus two chains and four forks use 45 quads at most.
    private static final Vector2f[] CHAIN = points(SEGMENTS + 1);
    private static final Vector2f[] FORK = points(FORK_SEGMENTS + 1);
    private static final Vector2f[] EDGES = points(SEGMENTS + 1);
    private record Frame(float x, float y, float depth, float rx, float ry, float ux, float uy,
                         float bendX, float bendY, float rearScale) {}
    private FistHeldRenderer() {}
    static void setShader(ShaderInstance value) { shader = value; }
    static boolean ready() { return shader != null; }

    static void render(float time, float power, float seed, boolean empowered, boolean overlay,
                       float x, float y, float depth, float rx, float ry, float ux, float uy,
                       float bendX, float bendY, float coreX, float coreY, FlameSpace flame) {
        renderColored(time, power, seed, empowered, empowered ? 1 : 0, overlay,
                x, y, depth, rx, ry, ux, uy, bendX, bendY, coreX, coreY, flame);
    }

    static void renderColored(float time, float power, float seed, boolean empowered, float red, boolean overlay,
                              float x, float y, float depth, float rx, float ry, float ux, float uy,
                              float bendX, float bendY, float coreX, float coreY, FlameSpace flame) {
        float chainX = 1 / flame.canvasX, chainY = flame.aspect / flame.canvasY;
        float boundX = Math.abs(rx) * Math.max(1, chainX) + Math.abs(ux) * Math.max(1, chainY);
        float boundY = Math.abs(ry) * Math.max(1, chainX) + Math.abs(uy) * Math.max(1, chainY);
        if (!ready() || depth < -1 || depth > 1
                || Math.abs(x) > 1 + 1.45f * boundX
                || Math.abs(y) > 1 + 1.45f * boundY) return;
        // Hold intrinsic animation for one cel. Position, projection, arm pose and airflow still update normally.
        int handSeed = (int) seed;
        double phase = (random(handSeed, 73, 0) + 1) * .5;
        int cel = (int) Math.floor(time * ANIMATION_FPS + phase);
        float celTime = (float) ((cel - phase) / ANIMATION_FPS);
        shader.safeGetUniform("Time").set(celTime);
        shader.safeGetUniform("FlamePose").set(pose(handSeed, cel, 1, 4), pose(handSeed, cel, 2, 7),
                pose(handSeed, cel, 3, 11), pose(handSeed, cel, 4, 5));
        shader.safeGetUniform("FlameFlicker").set(random(handSeed, 31, cel), random(handSeed, 37, cel),
                random(handSeed, 41, cel), random(handSeed, 47, cel));
        shader.safeGetUniform("Power").set(power);
        shader.safeGetUniform("Seed").set(seed);
        shader.safeGetUniform("Charged").set(red);
        shader.safeGetUniform("Bend").set(bendX, bendY);
        shader.safeGetUniform("CoreHalf").set(coreX, coreY);
        shader.safeGetUniform("FlameUp").set(flame.upX, flame.upY);
        shader.safeGetUniform("CanvasHalf").set(flame.canvasX, flame.canvasY);
        shader.safeGetUniform("FlameAspect").set(flame.aspect);
        RenderSystem.setShader(() -> shader);
        if (overlay) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        // The shared flame is always first. SPIKE packs R into 0..63; chain vertices use R=255.
        FistSpriteRenderer.quad(buffer, x, y, depth, rx, ry, ux, uy,
                FistSpriteRenderer.SPIKE, seed, 0, power, 1);
        if (empowered) {
            // The enlarged flame canvas must not stretch the existing chains or their motion response.
            float rearScale = Math.clamp((coreY / chainY + .055f) / .78f, .25f, 1);
            lightning(buffer, new Frame(x, y, depth, rx * chainX, ry * chainX, ux * chainY, uy * chainY,
                            Math.clamp(bendX / chainX, -.40f, .40f),
                            Math.clamp(bendY / chainY, -.32f, .32f), rearScale),
                    cel, celTime, power, handSeed);
        }
        FistSpriteRenderer.finish(buffer);
    }

    private static void lightning(BufferBuilder buffer, Frame frame, int cel, float time, float power, int seed) {
        // No interpolation between lightning cels; the chain shares the flame's 24 Hz sample-and-hold clock.
        float spin = time * .46f + seed * .018f + .12f * (float) Math.sin(time * 1.7 + seed);
        float width = .054f * (1 + .13f * (power - 1));
        for (int chain = 0; chain < 2; chain++) {
            int key = seed * 127 + chain * 313;
            float start = (chain == 0 ? -2.4f : .55f) + spin;
            float span = chain == 0 ? 3.3f : 2.85f;
            for (int i = 0; i <= SEGMENTS; i++) {
                float t = i / (float) SEGMENTS;
                float angle = start + span * t + .075f * random(key, i * 2, cel);
                float radius = .51f + ((i & 1) == 0 ? -.062f : .062f)
                        + .072f * random(key, i * 2 + 1, cel)
                        + .035f * (float) Math.sin(t * 7 + seed);
                CHAIN[i].set((float) Math.cos(angle) * radius * .96f + (chain == 0 ? -.025f : .035f),
                        (float) Math.sin(angle) * radius * .84f + (chain == 0 ? .025f : -.035f));
            }
            ribbon(buffer, frame, CHAIN, width, false, chain * 1.71f, 1);
            for (int fork = 0; fork < 2; fork++) {
                Vector2f root = CHAIN[fork == 0 ? 3 : 9];
                float angle = (float) Math.atan2(root.y, root.x) + (fork == 0 ? -.55f : .65f);
                float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
                float length = .30f + .085f * random(key + 41, fork, cel);
                FORK[0].set(root);
                for (int i = 1; i <= FORK_SEGMENTS; i++) {
                    float t = i / (float) FORK_SEGMENTS;
                    float jag = (float) Math.sin(t * Math.PI) * (.075f * random(key + 59,
                            fork * 11 + i, cel) + ((i & 1) == 0 ? -.035f : .035f));
                    FORK[i].set(root.x + dx * length * t - dy * jag,
                            root.y + dy * length * t + dx * jag);
                }
                ribbon(buffer, frame, FORK, width * .60f, true, chain * 1.71f + fork * .43f + .35f, .87f);
            }
        }
    }

    /** Shared mitered joins keep every zigzag connected, including at the sharp direction changes. */
    private static void ribbon(BufferBuilder buffer, Frame frame, Vector2f[] path,
                               float width, boolean fork, float phase, float opacity) {
        int last = path.length - 1;
        for (int i = 0; i <= last; i++) {
            Vector2f point = path[i], before = path[Math.max(0, i - 1)], after = path[Math.min(last, i + 1)];
            float ax = point.x - before.x, ay = point.y - before.y;
            float bx = after.x - point.x, by = after.y - point.y;
            if (i == 0) { ax = bx; ay = by; }
            if (i == last) { bx = ax; by = ay; }
            float a = Math.max(.00001f, (float) Math.sqrt(ax * ax + ay * ay));
            float b = Math.max(.00001f, (float) Math.sqrt(bx * bx + by * by));
            ax /= a; ay /= a; bx /= b; by /= b;
            float nx = -ay - by, ny = ax + bx;
            float n = (float) Math.sqrt(nx * nx + ny * ny);
            if (n < .001f) { nx = -by; ny = bx; } else { nx /= n; ny /= n; }
            float tip = fork ? Math.min(1, (last - i) * .50f) : Math.min(1, Math.min(i, last - i) * .65f);
            float w = width * Math.max(.015f, tip) / Math.max(.48f, nx * -by + ny * bx);
            EDGES[i].set(nx * w, ny * w);
        }
        int alpha = Math.round(opacity * 255);
        for (int i = 0; i < last; i++) {
            float v0 = phase + i / (float) last, v1 = phase + (i + 1f) / last;
            vertex(buffer, frame, path[i], EDGES[i], -1, v0, alpha);
            vertex(buffer, frame, path[i], EDGES[i], 1, v0, alpha);
            vertex(buffer, frame, path[i + 1], EDGES[i + 1], 1, v1, alpha);
            vertex(buffer, frame, path[i + 1], EDGES[i + 1], -1, v1, alpha);
        }
    }

    private static void vertex(BufferBuilder buffer, Frame frame, Vector2f point, Vector2f edge,
                               float side, float along, int alpha) {
        float px = point.x + edge.x * side, py = point.y + edge.y * side;
        // Keep the rear arcs close to the short cuff; forks are free to extend ahead of the knuckles.
        if (py < 0) py *= frame.rearScale;
        float free = Math.min(1, px * px + py * py);
        px += frame.bendX * free * .55f;
        py += frame.bendY * free * .55f;
        buffer.addVertex(frame.x + frame.rx * px + frame.ux * py,
                        frame.y + frame.ry * px + frame.uy * py, frame.depth)
                .setUv(side, along).setColor(255, 255, 255, alpha);
    }

    /** Independent random anchors preserve some shape continuity without a repeating sine-wave cycle. */
    private static float pose(int seed, int cel, int channel, int span) {
        int anchor = Math.floorDiv(cel, span);
        float t = Math.floorMod(cel, span) / (float) span;
        t = t * t * (3 - 2 * t);
        float before = random(seed, channel, anchor);
        float slow = before + (random(seed, channel, anchor + 1) - before) * t;
        return slow * .78f + random(seed, channel + 101, cel) * .22f;
    }
    private static float random(int seed, int point, int epoch) {
        int bits = seed ^ point * 0x632be5ab ^ epoch * 0x85157af5;
        bits = (bits ^ (bits >>> 16)) * 0x7feb352d;
        bits = (bits ^ (bits >>> 15)) * 0x846ca68b;
        bits ^= bits >>> 16;
        return (bits & 0xffffff) / 8388607.5f - 1;
    }
    private static Vector2f[] points(int count) {
        Vector2f[] result = new Vector2f[count];
        for (int i = 0; i < count; i++) result[i] = new Vector2f();
        return result;
    }
}
