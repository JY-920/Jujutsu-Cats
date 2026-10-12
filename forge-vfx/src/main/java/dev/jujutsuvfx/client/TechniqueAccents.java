package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.FurnaceFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Broad pressure ribbons and branching electricity share one bounded, depth-tested draw. */
final class TechniqueAccents {
    private static ShaderInstance shader;
    private static final int FLOW_SEGMENTS = 12, ARC_SEGMENTS = 20, FORK_SEGMENTS = 7;
    private static final Vec3[] NODES = new Vec3[Math.max(FLOW_SEGMENTS, ARC_SEGMENTS) + 1];
    private static final Vec3[] FORK_NODES = new Vec3[FORK_SEGMENTS + 1];
    private static final Vec3[] SIDES = new Vec3[Math.max(FLOW_SEGMENTS, ARC_SEGMENTS) + 1];

    private TechniqueAccents() {}
    static void setShader(ShaderInstance value) { shader = value; }

    static void render(RenderLevelStageEvent event, LimitlessCast cast) {
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.level == null || cast.blue()) return;
        long now = ClientEffectClock.ticks();
        float partial = event.getPartialTick();
        float age = cast.age(now, partial), core = cast.coreRadius(now, partial);
        float strength = cast.visibility(now, partial) * PurplePulse.reveal(cast, now, partial);
        if (cast.largePurple() && cast.committedPurple()) strength *= smooth((cast.chargeProgress(now, partial) - .46f) / .28f);
        if (cast.phase() == 2) strength *= (float) Math.exp(-cast.phaseAge(now, partial) * 5);
        if (strength < .003f) return;
        Vec3 center = cast.center(mc, partial).subtract(event.getCamera().getPosition());
        Vec3 axis = cast.direction(mc, partial).normalize(), right = FurnaceFlight.right(axis), up = right.cross(axis).normalize();
        boolean purple = cast.purple(), flight = cast.phase() == 1;
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()));
            shader.safeGetUniform("CenterRelative").set((float) center.x, (float) center.y, (float) center.z);
            // The galaxy is translucent; a fictitious opaque sphere would cut off its radial roots.
            shader.safeGetUniform("CoreRadius").set(purple ? 0f : core * .14f);
            shader.safeGetUniform("Time").set(age);
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            BufferBuilder b = dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            // Purple's air now refracts the scene in the composite pass; these strips only carry electricity.
            if (!purple) flows(b, cast.session, center, axis, right, up, age, core, strength, false, flight);
            if (purple) lightning(b, cast.session, center, axis, right, up, age, core, strength, flight,
                    cast.chargeProgress(now, partial));
            BufferBuilder.RenderedBuffer mesh = b.endOrDiscardIfEmpty();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }
    }

    private static void flows(BufferBuilder b, long session, Vec3 center, Vec3 axis, Vec3 right, Vec3 up,
                              float age, float core, float strength, boolean purple, boolean flight) {
        int count = purple ? 14 : 18;
        double reach = purple ? core * 2.70 : .55 + core * 4.4;
        for (int i = 0; i < count; i++) {
            double slot = session * 2.37 + i * 39.31;
            double clock = age * (.58 + hash(slot + 3.7) * .74) + hash(slot);
            double cycle = fract(clock), seed = slot + Math.floor(clock) * 107.19;
            float life = smooth((float) cycle / .14f) * (1 - smooth(((float) cycle - .70f) / .30f));
            if (life < .005f) continue;
            double variation = hash(seed + 7.7), phase = hash(seed + 23.1) * Math.PI * 2;
            // Each birth chooses a direction, then grows along it. Time never rotates it around the core.
            Vec3 outward = outward(right, up, axis, seed);
            Vec3 across = FurnaceFlight.right(outward), bank = across.cross(outward).normalize();
            double spread = reach * (.65 + .50 * variation) * (.08 + .92 * smooth((float) cycle / .32f));
            for (int j = 0; j <= FLOW_SEGMENTS; j++) {
                double f = (double) j / FLOW_SEGMENTS;
                double envelope = f * (.22 + .78 * f);
                double curl = Math.sin(f * 7.6 - age * 6.2 + phase) * .15
                        + Math.sin(f * 14.3 - age * 11.8 + phase * 2.1) * .065;
                double fold = Math.sin(f * 9.1 - age * 7.5 + phase * 1.7) * .12;
                double wake = flight ? -core * f * f * (purple ? .48 : .85) : 0;
                NODES[j] = center.add(outward.scale(spread * f))
                        .add(across.scale(spread * envelope * curl)).add(bank.scale(spread * envelope * fold))
                        .add(axis.scale(wake));
            }
            int color = purple ? (variation > .55 ? 0xe5e2ff : 0x9f99d7)
                    : (variation > .65 ? 0xffe8ed : 0xf32950);
            double width = (purple ? core * .16 : .065 + core * .22) * (.68 + variation * .65);
            ribbon(b, NODES, FLOW_SEGMENTS, right, width, strength * life * (purple ? .55f : .80f), color, 0);
        }
    }

    private static void lightning(BufferBuilder b, long session, Vec3 center, Vec3 axis, Vec3 right, Vec3 up,
                                  float age, float core, float strength, boolean flight, float charge) {
        int frame = (int) Math.floor(age * 24);
        float chargedChains = dev.jujutsuvfx.platform.Numbers.clamp(charge, 0, 1) * 2.99f;
        int chains = 1 + (int) chargedChains;
        for (int i = 0; i < chains; i++) {
            double slot = session * 13.31 + i * 71.9;
            // Stagger short discharges. A new direction is chosen only while this lane is dark,
            // so electricity flashes in a new place instead of sliding around as a solid tether.
            double period = .27 + hash(slot + 8.1) * .13;
            double clock = age / period + hash(slot + 33.7), cycle = Math.floor(clock);
            double seed = slot + cycle * 169.31;
            float local = (float) (fract(clock) * period - .015 - hash(seed + 3.1) * .025);
            float duration = .09f + (float) hash(seed + 27.9) * .075f;
            if (local <= 0 || local >= duration) continue;
            float introduced = i == 0 ? 1 : smooth((chargedChains - i) / .24f);
            if (introduced < .003f) continue;
            double variation = hash(seed + 71.1);
            double angle = hash(slot + 19.7) * Math.PI * 2 + cycle * 2.39996323
                    + (hash(seed + 17.1) - .5) * .65;
            double depth = (hash(seed + 53.1) - .5) * 1.50, plane = Math.sqrt(1 - depth * depth);
            Vec3 outward = right.scale(Math.cos(angle) * plane).add(up.scale(Math.sin(angle) * plane)).add(axis.scale(depth));
            Vec3 across = FurnaceFlight.right(outward), bank = across.cross(outward).normalize();
            double reach = core * (1.70 + variation * .90)
                    * (.72 + .28 * smooth(local / .022f));
            double previousX = 0, previousY = 0, previousF = 0;
            for (int j = 0; j <= ARC_SEGMENTS; j++) {
                double f = (double) j / ARC_SEGMENTS;
                if (j > 0 && j < ARC_SEGMENTS) f += (hash(seed + j * 13.7 + frame * 3.1) - .5) * .018;
                double envelope = Math.min(1, f * 6), large = core * (.20 + f * .28) * envelope;
                double fine = core * (.022 + f * .045) * envelope;
                // A few broad bends set the path; small secondary teeth break up each long edge.
                // Different node spacing avoids a regular zigzag even within one held animation frame.
                double x = brokenNoise(seed + frame * 31.7, f * (4.4 + variation * 1.7)) * large
                        + (hash(seed + frame * 11.3 + j * 17.31) - .5) * fine;
                double y = brokenNoise(seed + frame * 23.3 + 97.7, f * (5.1 + variation * 1.3)) * large
                        + (hash(seed + frame * 19.1 + j * 47.17) - .5) * fine;
                double lateral = Math.hypot(x - previousX, y - previousY);
                double limit = reach * (f - previousF) * .72;
                if (lateral > limit && lateral > .000001) {
                    x = previousX + (x - previousX) * limit / lateral;
                    y = previousY + (y - previousY) * limit / lateral;
                }
                NODES[j] = center.add(outward.scale(reach * f)).add(across.scale(x)).add(bank.scale(y))
                        .add(axis.scale(flight ? -core * f * f * .35 : 0));
                previousX = x; previousY = y; previousF = f;
            }
            float pulse = strength * introduced * smooth(local / .012f)
                    * (1 - smooth((local / duration - .55f) / .45f));
            ribbon(b, NODES, ARC_SEGMENTS, right, core * (.090 + variation * .034), pulse, 0x7526ff, 2);
            for (int fork = 0; fork < 2; fork++) {
                if (hash(seed + fork * 29.7 + 111.1) > (fork == 0 ? .78 : .48)) continue;
                Vec3 root = NODES[5 + fork * 6 + (int) (hash(seed + fork + 13.7) * 3)];
                Vec3 direction = outward.scale(.78).add(across.scale(fork == 0 ? .62 : -.62))
                        .add(bank.scale((hash(seed + fork + 9.1) - .5) * .65)).normalize();
                FORK_NODES[0] = root;
                double length = core * (.55 + hash(seed + fork + 89.1) * .85);
                for (int j = 1; j <= FORK_SEGMENTS; j++) {
                    double f = (double) j / FORK_SEGMENTS;
                    if (j < FORK_SEGMENTS) f += (hash(seed + fork * 21.1 + j * 9.7) - .5) * .045;
                    double bend = brokenNoise(seed + frame * 7.7 + fork * 19.1, f * 2.6) * core * .12 * f;
                    double tooth = (hash(seed + frame * 13.1 + fork * 23.7 + j * 17) - .5) * core * .035 * f;
                    FORK_NODES[j] = root.add(direction.scale(length * f))
                            .add(bank.scale(bend + tooth))
                            .add(across.scale((hash(seed + frame * 5.3 + j * 31.7 + fork * 71.3) - .5) * core * .018 * f));
                }
                ribbon(b, FORK_NODES, FORK_SEGMENTS, right, core * .052, pulse * .80f, 0x8e3dff, 2);
            }
        }
    }

    private static Vec3 outward(Vec3 right, Vec3 up, Vec3 axis, double seed) {
        double angle = hash(seed + 19.7) * Math.PI * 2;
        double depth = (hash(seed + 53.1) - .5) * 1.50;
        double plane = Math.sqrt(1 - depth * depth);
        return right.scale(Math.cos(angle) * plane).add(up.scale(Math.sin(angle) * plane)).add(axis.scale(depth));
    }

    private static void ribbon(BufferBuilder b, Vec3[] nodes, int count, Vec3 fallback, double width, float alpha, int rgb, float style) {
        for (int i = 0; i <= count; i++) {
            Vec3 tangent = nodes[Math.min(count, i + 1)].subtract(nodes[Math.max(0, i - 1)]);
            Vec3 side = tangent.cross(nodes[i].scale(-1));
            side = side.lengthSqr() < .000001 ? fallback : side.normalize();
            if (i > 0 && side.dot(SIDES[i - 1]) < 0) side = side.scale(-1);
            float f = (float) i / count;
            double taper = style > 1.5f ? 1 - f * .88
                    : (.10 + .90 * smooth(f / .24f)) * (.035 + .965 * Math.pow(1 - f, .80));
            SIDES[i] = side.scale(width * taper);
        }
        int color = (dev.jujutsuvfx.platform.Numbers.clamp(Math.round(alpha * 255), 0, 255) << 24) | rgb;
        for (int i = 0; i < count; i++) {
            float f0 = (float) i / count, f1 = (float) (i + 1) / count;
            vertex(b, nodes[i].subtract(SIDES[i]), -1, style + f0, color);
            vertex(b, nodes[i].add(SIDES[i]), 1, style + f0, color);
            vertex(b, nodes[i + 1].add(SIDES[i + 1]), 1, style + f1, color);
            vertex(b, nodes[i + 1].subtract(SIDES[i + 1]), -1, style + f1, color);
        }
    }
    private static void vertex(BufferBuilder b, Vec3 p, float u, float v, int color) {
        b.vertex((float) p.x, (float) p.y, (float) p.z).uv(u, v).color(color).endVertex();
    }
    private static double hash(double n) { return fract(Math.sin(n * 12.9898) * 43758.5453); }
    private static double brokenNoise(double seed, double position) {
        double cell = Math.floor(position), t = position - cell;
        return (hash(seed + cell * 37.19) - .5) * (1 - t) + (hash(seed + (cell + 1) * 37.19) - .5) * t;
    }
    private static double fract(double n) { return n - Math.floor(n); }
    private static float smooth(float t) { t = dev.jujutsuvfx.platform.Numbers.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
}
