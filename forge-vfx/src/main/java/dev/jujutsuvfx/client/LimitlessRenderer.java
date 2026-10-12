package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.core.LimitlessTechnique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** Bounded emissive volume, depth-aware space lens, and world-space attraction ribbons. */
final class LimitlessRenderer {
    private static ShaderInstance volume, composite;
    private static final SceneSnapshot SCENE = new SceneSnapshot();
    private static TextureTarget energy;
    static void setVolume(ShaderInstance shader) { volume = shader; }
    static void setComposite(ShaderInstance shader) { composite = shader; }
    static boolean ready() { return volume != null && composite != null && LimitlessStreams.ready(); }
    static void release() { SCENE.release(); RedRenderer.release(); if (energy != null) { energy.destroyBuffers(); energy = null; } }

    static void render(RenderLevelStageEvent event, LimitlessCast cast) {
        if (cast.red()) { RedRenderer.render(event, cast); return; }
        Minecraft mc = Minecraft.getInstance();
        if (!ready() || mc.level == null || mc.player == null) return;
        float partial = event.getPartialTick();
        long now = ClientEffectClock.ticks();
        float visibility = cast.visibility(now, partial) * PurplePulse.reveal(cast, now, partial);
        if (visibility < .001f) return;
        Vec3 center = cast.center(mc, partial).subtract(event.getCamera().getPosition());
        Vec3 axis = cast.direction(mc, partial), right = FurnaceFlight.right(axis), up = right.cross(axis).normalize();
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        Matrix4f inverse = new Matrix4f(projection).invert();
        float core = cast.coreRadius(now, partial);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float[] clear = new float[4]; GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try (DomainRenderer.State ignored = new DomainRenderer.State(); SceneSnapshot.Target target = new SceneSnapshot.Target()) {
            // The snapshot includes any domains and the previously drawn technique, never an old world-only frame.
            if (!SCENE.capture()) return;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int width = Math.max(1, (viewport[2] + 1) / 2), height = Math.max(1, (viewport[3] + 1) / 2);
            try (SceneSnapshot.Target original = new SceneSnapshot.Target()) {
                if (energy == null || energy.width != width || energy.height != height) {
                    if (energy != null) energy.destroyBuffers();
                    energy = new EffectTarget(width, height, false, Minecraft.ON_OSX);
                    energy.setFilterMode(GL11.GL_NEAREST);
                }
                energy.bindWrite(true);
                volume.setSampler("SceneDepth", SCENE.depth());
                uniforms(volume, cast, now, partial, inverse, center, core, visibility);
                set(volume, "Axis", axis); set(volume, "Right", right); set(volume, "Up", up);
                draw(volume);
            }
            composite.setSampler("SceneColor", SCENE.color());
            composite.setSampler("SceneDepth", SCENE.depth());
            composite.setSampler("EnergyLayer", energy.getColorTextureId());
            uniforms(composite, cast, now, partial, inverse, center, core, visibility);
            set(composite, "Axis", axis); set(composite, "Right", right); set(composite, "Up", up);
            composite.safeGetUniform("Distortion").set(mc.options.screenEffectScale().get().floatValue());
            composite.safeGetUniform("Seed").set((float) Math.floorMod(cast.session, 4096));
            composite.safeGetUniform("Flash").set(PurplePulse.flash(cast, now, partial)
                    * (float) dev.jujutsuvfx.platform.Numbers.clamp(1 - center.length() / 96, 0, 1));
            Vector4f projected = projection.transform(new Vector4f((float) center.x, (float) center.y, (float) center.z, 1));
            if (projected.w > .01f) {
                float lensCore = cast.largePurple() && cast.committedPurple() ? Math.max(core, cast.fusionRadius() * .55f) : core;
                float screenRadius = dev.jujutsuvfx.platform.Numbers.clamp(lensCore * event.getProjectionMatrix().m11() / (projected.w * 2), .0001f, 4);
                composite.safeGetUniform("CoreScreen").set(projected.x / projected.w * .5f + .5f,
                        projected.y / projected.w * .5f + .5f, screenRadius, projected.z / projected.w * .5f + .5f);
            } else composite.safeGetUniform("CoreScreen").set(.5f, .5f, 0f, 1f);
            draw(composite);
        } finally {
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        if (cast.blue()) LimitlessStreams.render(event, cast);
        else TechniqueAccents.render(event, cast);
    }
    private static void uniforms(ShaderInstance shader, LimitlessCast cast, long now, float partial,
                                 Matrix4f inverse, Vec3 center, float core, float visibility) {
        shader.safeGetUniform("InverseViewProjection").set(inverse);
        set(shader, "CenterRelative", center);
        shader.safeGetUniform("Time").set(cast.age(now, partial));
        shader.safeGetUniform("PhaseTime").set(cast.phaseAge(now, partial));
        shader.safeGetUniform("Kind").set((float) cast.kind);
        shader.safeGetUniform("Phase").set((float) cast.phase());
        shader.safeGetUniform("Radius").set(cast.largePurple() && cast.committedPurple() ? cast.fusionRadius() : cast.visualRadius());
        shader.safeGetUniform("PurpleOutput").set(1f + cast.amplificationProgress(now, partial));
        shader.safeGetUniform("CoreRadius").set(core);
        shader.safeGetUniform("Visibility").set(visibility);
        shader.safeGetUniform("Expansion").set(cast.expansion(now, partial));
        shader.safeGetUniform("PurpleFusion").set(cast.largePurple() && cast.committedPurple() ? 1f : 0f,
                cast.chargeProgress(now, partial));
        if (cast.purple()) {
            // Integrate charge spin, then carry its angle and speed into a short release coast.
            // Flight age also continues through impact, so the material never resets its orientation.
            float flight = cast.flightAge(now, partial), held = cast.age(now, partial) - flight;
            float ramp = Math.min(held, 3), coast = (float) Math.exp(-flight / .42f);
            float spin = .90f * held + .16f * ramp * ramp + .96f * (held - ramp)
                    + .55f * flight + (.35f + .32f * ramp) * .42f * (1 - coast);
            // Negative angle is clockwise from the caster's forward view.
            shader.safeGetUniform("PurpleMotion").set(-spin, coast);
        }
    }
    private static void set(ShaderInstance shader, String name, Vec3 p) {
        shader.safeGetUniform(name).set((float) p.x, (float) p.y, (float) p.z);
    }
    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.disableBlend();
        BufferBuilder b = dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(-1, -1, 1).uv(0, 0).color(-1).endVertex(); b.vertex(1, -1, 1).uv(1, 0).color(-1).endVertex();
        b.vertex(1, 1, 1).uv(1, 1).color(-1).endVertex(); b.vertex(-1, 1, 1).uv(0, 1).color(-1).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
}
