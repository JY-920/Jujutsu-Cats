package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.FurnaceFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Visible-surface colored fill, independent of the active shader pack and block light engine. */
final class TechniqueLighting {
    private static final int MAX_LIGHTS = 12;
    private static final Vec3 BLUE = new Vec3(.025, .59, 1), RED = new Vec3(1, .025, .095);
    private static final Vec3 PURPLE = new Vec3(.53, .075, 1), FIRE = new Vec3(1, .40, .035);
    private static final SceneSnapshot SCENE = new SceneSnapshot();
    private static final Light[] LIGHTS = new Light[MAX_LIGHTS];
    private static ShaderInstance surface, composite;
    private static TextureTarget irradiance;
    private static int count;
    private record Light(Vec3 start, Vec3 end, float radius, Vec3 color, float energy, int kind) {}

    private TechniqueLighting() {}
    static void setSurface(ShaderInstance shader) { surface = shader; }
    static void setComposite(ShaderInstance shader) { composite = shader; }
    static boolean ready() { return surface != null && composite != null; }
    static float strength() { return dev.jujutsuvfx.config.VfxConfig.lighting(); }
    static int setStrength(float value) {
        dev.jujutsuvfx.config.VfxConfig.lighting(Float.isFinite(value) ? Math.round(Math.clamp(value, 0, 5)) : 2);
        if (strength() == 0) release();
        return 1;
    }
    static void release() {
        SCENE.release();
        if (irradiance != null) { irradiance.destroyBuffers(); irradiance = null; }
        Arrays.fill(LIGHTS, null); count = 0;
    }

    static void render(RenderLevelStageEvent event, java.util.List<LimitlessCast> techniques,
                       java.util.List<FurnaceCast> furnaces, MalevolentShrine shrine, UnlimitedVoid unlimitedVoid) {
        Minecraft mc = Minecraft.getInstance();
        float strength = strength();
        if (!ready() || strength <= 0 || mc.level == null || mc.player == null) return;
        count = 0; Arrays.fill(LIGHTS, null);
        long now = ClientEffectClock.ticks();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        domains(shrine, unlimitedVoid, now, partial);
        // Retain the existing light budget while admitting every live cast as a candidate.
        Vec3 observer=event.getCamera().getPosition();
        techniques.stream().sorted(java.util.Comparator.comparingDouble(c->c.center(mc,partial).distanceToSqr(observer)))
                .forEach(c->technique(mc,c,now,partial));
        furnaces.stream().sorted(java.util.Comparator.comparingDouble(c->c.center(mc,partial).distanceToSqr(observer)))
                .forEach(c->furnace(mc,c,now,partial));
        if (count == 0) { release(); return; }
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Matrix4f inverse = new Matrix4f(projection).invert();
        Vec3 camera = event.getCamera().getPosition();
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float[] clear = new float[4]; GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try (DomainRenderer.State ignored = new DomainRenderer.State(); SceneSnapshot.Target target = new SceneSnapshot.Target()) {
            // This pass runs before the domains/energy cores. Only real visible surfaces
            // enter the light buffer; a replacement cosmic sky cannot be mistaken for terrain.
            if (!SCENE.capture()) return;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int width = Math.max(1, (viewport[2] + 1) / 2), height = Math.max(1, (viewport[3] + 1) / 2);
            try (SceneSnapshot.Target original = new SceneSnapshot.Target()) {
                ensureTarget(width, height);
                irradiance.bindWrite(true);
                surface.setSampler("SceneDepth", SCENE.depth());
                surface.safeGetUniform("InverseViewProjection").set(inverse);
                surface.safeGetUniform("ViewProjection").set(projection);
                surface.safeGetUniform("SceneSize").set((float) viewport[2], (float) viewport[3]);
                surface.safeGetUniform("LightReachScale").set(1f + Math.max(0, strength - 1) * .60f);
                for (int i = 0; i < MAX_LIGHTS; i++) {
                    Light light = i < count ? LIGHTS[i] : null;
                    if (light == null) {
                        surface.safeGetUniform("LightPosition" + i).set(0f, 0f, 0f, 0f);
                        surface.safeGetUniform("LightEnd" + i).set(0f, 0f, 0f, 0f);
                        surface.safeGetUniform("LightColor" + i).set(0f, 0f, 0f, 0f);
                    } else {
                        set(surface, "LightPosition" + i, light.start.subtract(camera), light.radius);
                        set(surface, "LightEnd" + i, light.kind >= 2 ? light.end : light.end.subtract(camera), light.kind);
                        set(surface, "LightColor" + i, light.color, light.energy * strength);
                    }
                }
                draw(surface);
            }
            composite.setSampler("SceneColor", SCENE.color());
            composite.setSampler("SceneDepth", SCENE.depth());
            composite.setSampler("LightLayer", irradiance.getColorTextureId());
            composite.safeGetUniform("InverseViewProjection").set(inverse);
            composite.safeGetUniform("LightSize").set((float) width, (float) height);
            draw(composite);
        } finally {
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void ensureTarget(int width, int height) {
        if (irradiance != null && irradiance.width == width && irradiance.height == height) return;
        if (irradiance != null) irradiance.destroyBuffers();
        irradiance = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        // Alpha stores linear camera distance for edge-aware reconstruction. RGBA8 would
        // clamp this distance and smear colored light across silhouettes and the sky.
        RenderSystem.bindTexture(irradiance.getColorTextureId());
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        irradiance.setFilterMode(GL11.GL_NEAREST);
    }

    private static void technique(Minecraft mc, LimitlessCast cast, long now, float partial) {
        if (cast == null || cast.preparingPurple()) return;
        float visibility = cast.visibility(now, partial);
        if (visibility < .002f) return;
        float core = cast.coreRadius(now, partial), age = cast.phaseAge(now, partial);
        float charge = cast.chargeProgress(now, partial);
        Vec3 center = cast.center(mc, partial);
        if (cast.blue()) {
            point(center, Math.min(24, 9 + core * 4), BLUE,
                    visibility * (1.45f + .55f * cast.expansion(now, partial)) * (.95f + .05f * (float) Math.sin(age * 5.5)));
        } else if (cast.red()) {
            if (cast.phase() == 0) point(center, 6 + 5 * charge, RED, visibility * (.65f + 1.45f * charge));
            else if (cast.redBeam()) {
                float fade = cast.phase() == 2 ? (float) Math.exp(-age * 7.5) : smooth(age / .07f);
                Vec3 end = center.add(cast.direction(mc, partial).scale(cast.beamLength(partial)));
                add(center, end, 14, RED, 2.9f * visibility * fade, 1);
                if (cast.phase() == 2) point(end, Math.min(32, cast.radius + 6), new Vec3(1, .18, .25),
                        3.6f * visibility * (float) Math.exp(-age * 2.6));
            } else point(center, cast.phase() == 2 ? Math.min(30, cast.radius + 5) : 10,
                    RED, visibility * (cast.phase() == 2 ? 3.2f * (float) Math.exp(-age * 2.6) : 2.5f));
        } else {
            // Use the same merge/separation curve and basis as limitless_volume.fsh.
            if (cast.largePurple() && cast.committedPurple()) {
                float split = 1 - between(.68f, .95f, charge);
                float separation = cast.fusionRadius() * 1.04f * (1 - between(.12f, .83f, charge));
                Vec3 axis=cast.direction(mc,partial),right=FurnaceFlight.right(axis),up=right.cross(axis).normalize();
                double angle=cast.amplifiedPurple()?Math.PI*3*between(0,.86f,charge):0;
                Vec3 side=right.scale(Math.cos(angle)*separation).add(up.scale(-Math.sin(angle)*separation));
                float lightRadius = 7 + cast.fusionRadius() * 2;
                point(center.add(side), lightRadius, BLUE, split * visibility * 1.7f);
                point(center.subtract(side), lightRadius, RED, split * visibility * 1.7f);
            }
            float opening = cast.committedPurple() && cast.largePurple() ? between(.34f, .62f, charge) : 1;
            float fade = cast.phase() == 2 ? (float) Math.exp(-age * 1.8) : 1;
            float energy = cast.phase() == 0 ? .65f + 2.5f * charge : 3.6f;
            energy += PurplePulse.flash(cast, now, partial) * 1.5f * mc.options.screenEffectScale().get().floatValue();
            point(center, Math.min(32, 8 + core * 4), PURPLE, visibility * opening * fade * energy);
        }
    }

    private static void furnace(Minecraft mc, FurnaceCast cast, long now, float partial) {
        if (cast == null) return;
        float age = cast.phaseAge(now, partial);
        float charge = smooth(age * 20 / FurnaceFlight.CHARGE_TICKS);
        float fade = cast.phase() == 2 ? (float) Math.exp(-age * .95) * (1 - between(3.3f, FurnaceFlight.AFTERGLOW, age)) : 1;
        float radius = cast.phase() == 0 ? 7 + charge * 5 : cast.phase() == 1 ? 14 : Math.min(50, cast.radius * 1.4f + 5);
        float power = cast.phase() == 0 ? .7f + 2.1f * charge : cast.phase() == 1 ? 3 : 5.2f;
        float flutter = .95f + .035f * (float) Math.sin(age * 13.7) + .015f * (float) Math.sin(age * 29.3);
        point(cast.center(mc, partial), radius, FIRE, power * fade * flutter * smooth(cast.age(now, partial) / .16f));
    }

    private static void domains(MalevolentShrine shrine, UnlimitedVoid domain, long now, float partial) {
        if (shrine != null) {
            float radius = shrine.radius * shrine.expansion(now, partial), amount = shrine.visibility(now, partial);
            add(shrine.center, new Vec3(0, 1, 0), radius, new Vec3(1, .055, .14), .50f * amount, 2);
            add(shrine.center, new Vec3(0, -1, 0), radius, new Vec3(.025, .70, .67), .24f * amount, 2);
        }
        if (domain != null) {
            float radius = domain.radius * domain.expansion(now, partial), amount = domain.visibility(now, partial);
            float cosmos = between(.94f, 3.10f, domain.age(now, partial));
            if (!dev.jujutsuvfx.config.VfxConfig.flashes()) cosmos = 1;
            Vec3 upper = new Vec3(.78, .90, 1).lerp(new Vec3(.32, .18, 1), cosmos);
            add(domain.center, new Vec3(0, 1, 0), radius, upper, .46f * amount, 2);
            add(domain.center, new Vec3(.35, .15, .92).normalize(), radius,
                    new Vec3(.91, .38, .97), .24f * amount * cosmos, 2);
        }
    }

    private static void point(Vec3 center, float radius, Vec3 color, float energy) { add(center, center, radius, color, energy, 0); }
    private static void add(Vec3 start, Vec3 end, float radius, Vec3 color, float energy, int kind) {
        if (count >= MAX_LIGHTS || radius < .1f || energy < .002f || !Double.isFinite(start.lengthSqr()) || !Double.isFinite(end.lengthSqr())) return;
        // Widen technique point/beam lights as well as brightening them. Domain fill stays within its real volume.
        if (kind < 2) radius *= 1f + Math.max(0, strength() - 1) * .60f;
        LIGHTS[count++] = new Light(start, end, radius, color, energy, kind);
    }
    private static float smooth(float value) { float t = Math.clamp(value, 0, 1); return t * t * (3 - 2 * t); }
    private static float between(float start, float end, float value) { return smooth((value - start) / (end - start)); }
    private static void set(ShaderInstance shader, String name, Vec3 p, float w) {
        shader.safeGetUniform(name).set((float) p.x, (float) p.y, (float) p.z, w);
    }
    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(-1, -1, 1).setUv(0, 0).setColor(-1); b.addVertex(1, -1, 1).setUv(1, 0).setColor(-1);
        b.addVertex(1, 1, 1).setUv(1, 1).setColor(-1); b.addVertex(-1, 1, 1).setUv(0, 1).setColor(-1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
