package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.core.UnlimitedPurpleTiming;
import dev.jujutsuvfx.core.ScopedEffectTimeline;
import dev.jujutsuvfx.network.UnlimitedPurple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A server-timed, omnidirectional Blue/Red collision; observer refreshes never restart its presentation. */
public final class UnlimitedPurpleEffects {
    private static final int MAX_TRACES = 8, VISIBLE_END = UnlimitedPurpleTiming.VISIBLE_TICKS, SERVER_END = UnlimitedPurpleTiming.MAX_PACKET_AGE;
    private static final Map<UUID, Burst> BURSTS = new LinkedHashMap<>();
    private static final Map<UUID, Long> FINISHED = new LinkedHashMap<>();
    private static final SceneSnapshot SCENE = new SceneSnapshot();
    private static final SoundEvent GATHER = sound("purple_flight"), EXPLODE = sound("furnace_burst"), TAIL = sound("purple_end");
    private static ShaderInstance volume, composite;
    private static TextureTarget energy;
    private static ClientLevel level;

    private static final class Burst {
        final Vec3 center;
        final float radius, seed;
        final UnlimitedPurple.Source blueSource, redSource;
        final Vec3 right, up;
        final LimitlessCast blueForm, redForm, incomingBeam;
        final ScopedEffectTimeline timeline;
        int seenAge;final boolean slowFusion;
        boolean exploded, ended;
        final CastAudio audio=new CastAudio();
        Burst(UnlimitedPurple packet, long now) {
            center = packet.center(); radius = packet.radius();slowFusion=packet.slowFusion();
            blueSource = packet.blue(); redSource = packet.red();
            Vec3 axis = redSource.direction().normalize();
            right = FurnaceFlight.right(axis); up = right.cross(axis).normalize();
            Minecraft mc = Minecraft.getInstance();
            blueForm = new LimitlessCast(mc, blueSource, LimitlessTechnique.BLUE, false);
            redForm = new LimitlessCast(mc, redSource, LimitlessTechnique.RED, false);
            incomingBeam = redSource.beam() ? new LimitlessCast(mc, redSource, LimitlessTechnique.RED, true) : null;
            seed = Math.floorMod(packet.id().hashCode(), 4096);
            timeline = new ScopedEffectTimeline(packet.ageTicks(),now); seenAge = packet.ageTicks();
            exploded = seenAge >= UnlimitedPurpleTiming.CHARGE_TICKS+20; ended = seenAge >= 52 + UnlimitedPurpleTiming.CONDENSE_TICKS;
        }
        double rate(){return slowFusion&&seenAge<UnlimitedPurpleTiming.CHARGE_TICKS?.25:1;}
        float age(float partial) { return (float)timeline.advance(ClientEffectClock.ticks()+partial,rate()); }
        float presentationAge(float partial) { return Math.max(0, age(partial) - UnlimitedPurpleTiming.CONDENSE_TICKS) * .05f; }
    }

    record CameraFocus(Vec3 point,float extent,float frameExtent) {}
    static CameraFocus cameraFocus(UUID actor,UUID rival,float partial){
        Burst best=null;float youngest=Float.MAX_VALUE;
        for(Burst burst:BURSTS.values()){
            boolean owner=burst.blueSource.caster().equals(actor)||burst.redSource.caster().equals(actor)
                    ||rival!=null&&(burst.blueSource.caster().equals(rival)||burst.redSource.caster().equals(rival));
            float age=burst.age(partial);if(!owner||age>=VISIBLE_END)continue;
            if(age<youngest){best=burst;youngest=age;}
        }
        if(best==null)return null;
        float age=best.presentationAge(partial),extent=framingExtent(best,age);
        return new CameraFocus(best.center,extent,Math.max(extent,framingExtent(best,age+.55f)));
    }
    private static float framingExtent(Burst burst,float age){
        float initial=6.8f*burst.radius/32;
        if(age<0)return Math.max(initial,(float)Math.max(burst.blueSource.point().distanceTo(burst.center)+burst.blueSource.coreRadius(),
                burst.redSource.point().distanceTo(burst.center)+burst.redSource.coreRadius()));
        return Math.max(initial,burst.radius*1.12f*Math.max(0,Math.min(age-1,1)));
    }
    private UnlimitedPurpleEffects() {}
    public static void setVolume(ShaderInstance shader) { volume = shader; }
    public static void setComposite(ShaderInstance shader) { composite = shader; if (shader == null) releaseTargets(); }
    private static void releaseTargets() {
        SCENE.release();
        if (energy != null) { energy.destroyBuffers(); energy = null; }
    }
    public static void clear() { BURSTS.values().forEach(b->b.audio.stop());BURSTS.clear(); FINISHED.clear(); level = null; releaseTargets(); }
    private static void ensureWorld() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) { clear(); level = current; }
    }

    public static void receive(UnlimitedPurple packet) {
        ensureWorld(); Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || packet.id() == null) return;
        long now = ClientEffectClock.ticks();
        if (packet.ageTicks() == -1) { Burst old=BURSTS.remove(packet.id());if(old!=null)old.audio.stop();remember(packet.id(), now); return; }
        if (packet.ageTicks() < 0 || packet.ageTicks() > SERVER_END || !finite(packet.center())
                || !Float.isFinite(packet.radius()) || packet.radius() < 1 || packet.radius() > 64
                || !validSource(packet.blue(), packet.center()) || !validSource(packet.red(), packet.center())
                || packet.center().distanceToSqr(mc.player.position()) > 224 * 224 || FINISHED.containsKey(packet.id())) return;
        Burst burst = BURSTS.get(packet.id());
        if (burst != null) {
            if (packet.ageTicks() < burst.seenAge) return;
            // Clock corrections may advance a trace, but may never rewind the gathering/flash.
            burst.timeline.synchronize(packet.ageTicks(),now,burst.rate());
            burst.seenAge = packet.ageTicks();
            return;
        }
        if (packet.ageTicks() >= VISIBLE_END) { remember(packet.id(), now); return; }
        if (BURSTS.size() >= MAX_TRACES) {
            UUID oldest = BURSTS.keySet().iterator().next(); BURSTS.remove(oldest).audio.stop(); remember(oldest, now);
        }
        burst = new Burst(packet, now); BURSTS.put(packet.id(), burst);
        ClientEvents.absorbTechnique(packet.blue(), LimitlessTechnique.BLUE);
        ClientEvents.absorbTechnique(packet.red(), LimitlessTechnique.RED);
        if (packet.ageTicks() <= 2) play(burst, GATHER, .85f, .74f);
    }
    private static void remember(UUID id, long now) {
        if (FINISHED.size() >= 64) FINISHED.remove(FINISHED.keySet().iterator().next());
        FINISHED.put(id, now + 240);
    }
    public static void tick() {
        ensureWorld(); if (level == null) return;
        long now = ClientEffectClock.ticks(); FINISHED.values().removeIf(expiry -> expiry < now);
        for (var iterator = BURSTS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next(); Burst burst = entry.getValue(); float age = burst.age(0);
            if (age >= VISIBLE_END) { burst.audio.stop();remember(entry.getKey(), now); iterator.remove(); continue; }
            if (!burst.exploded && age >= UnlimitedPurpleTiming.CHARGE_TICKS) {
                burst.exploded = true;
                burst.audio.stop(GATHER);
                if (age < UnlimitedPurpleTiming.CHARGE_TICKS + 20){
                    play(burst, EXPLODE, 1.35f, .78f);
                    play(burst, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 1.7f, .65f);
                }
            }
            if (!burst.ended && age >= 52 + UnlimitedPurpleTiming.CONDENSE_TICKS) {
                burst.ended = true;
                if (age < 59 + UnlimitedPurpleTiming.CONDENSE_TICKS) play(burst, TAIL, 1.15f, .77f);
            }
        }
        if (BURSTS.isEmpty()) releaseTargets();
    }

    public static void render(RenderLevelStageEvent event) {
        ensureWorld();
        if (volume == null || composite == null || BURSTS.isEmpty() || level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        var visible = new ArrayList<>(BURSTS.values());
        visible.removeIf(b -> b.age(partial) >= VISIBLE_END || b.center.distanceToSqr(camera) > 192 * 192);
        visible.sort(Comparator.comparingDouble(b -> b.center.distanceToSqr(camera)));
        // Limit expensive volumes independently of the observer cache. Lightning is geometry, not particles.
        for (int i = Math.min(2, visible.size()) - 1; i >= 0; i--) renderBurst(event, visible.get(i), partial);
    }
    private static void renderBurst(RenderLevelStageEvent event, Burst burst, float partial) {
        Vec3 relative = burst.center.subtract(event.getCamera().getPosition());
        float distanceFade = 1 - smooth(((float) relative.length() - 112) / 80);
        if (distanceFade <= .001f) return;
        condense(event, burst, partial, distanceFade);
        if (burst.age(partial) * .05f <= .44f) return;
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Matrix4f inverse = new Matrix4f(projection).invert();
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float[] clear = new float[4]; GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try (DomainRenderer.State ignored = new DomainRenderer.State(); SceneSnapshot.Target target = new SceneSnapshot.Target()) {
            if (!SCENE.capture()) return;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int width = Math.max(1, (viewport[2] + 1) / 2), height = Math.max(1, (viewport[3] + 1) / 2);
            try (SceneSnapshot.Target original = new SceneSnapshot.Target()) {
                if (energy == null || energy.width != width || energy.height != height) {
                    if (energy != null) energy.destroyBuffers();
                    energy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
                    energy.setFilterMode(GL11.GL_NEAREST);
                }
                energy.bindWrite(true); volume.setSampler("SceneDepth", SCENE.depth());
                uniforms(volume, burst, partial, inverse, relative, distanceFade); draw(volume);
            }
            composite.setSampler("SceneColor", SCENE.color()); composite.setSampler("SceneDepth", SCENE.depth());
            composite.setSampler("EnergyLayer", energy.getColorTextureId());
            uniforms(composite, burst, partial, inverse, relative, distanceFade);
            composite.safeGetUniform("ViewProjection").set(projection);
            composite.safeGetUniform("FlashEnabled").set(dev.jujutsuvfx.config.VfxConfig.flashScale());
            composite.safeGetUniform("Distortion").set(Minecraft.getInstance().options.screenEffectScale().get().floatValue());
            draw(composite);
        } finally {
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        arcs(event, burst, partial, distanceFade);
    }
    private static void uniforms(ShaderInstance shader, Burst burst, float partial, Matrix4f inverse, Vec3 p, float visibility) {
        shader.safeGetUniform("InverseViewProjection").set(inverse);
        shader.safeGetUniform("CenterRelative").set((float) p.x, (float) p.y, (float) p.z);
        shader.safeGetUniform("Age").set(burst.presentationAge(partial));
        shader.safeGetUniform("Radius").set(burst.radius);
        shader.safeGetUniform("Seed").set(burst.seed);
        shader.safeGetUniform("Visibility").set(visibility * smooth((burst.age(partial) * .05f - .44f) / .30f));
        shader.safeGetUniform("FusionRight").set((float) burst.right.x, (float) burst.right.y, (float) burst.right.z);
        shader.safeGetUniform("FusionUp").set((float) burst.up.x, (float) burst.up.y, (float) burst.up.z);
    }
    private static void condense(RenderLevelStageEvent event, Burst burst, float partial, float visibility) {
        float raw = burst.age(partial) * .05f;
        float remaining = 1 - smooth((raw - .44f) / .30f);
        if (remaining < .001f) return;
        float elapsed = burst.presentationAge(partial), unit = burst.radius / 32;
        float gather = smooth(elapsed / .82f), collapse = smooth((elapsed - .68f) / .32f);
        float orbit = (1 - gather) * 3.8f * unit;
        float radius = unit * (1.22f + .38f * gather) * (1 - collapse * .55f);
        Vec3 split = burst.right.scale(Math.cos(elapsed * 2.2f) * orbit)
                .add(burst.up.scale(Math.sin(elapsed * 2.2f) * orbit));
        float move = smooth(raw * 20 / UnlimitedPurpleTiming.CONDENSE_TICKS), retract = smooth(raw / .42f);
        Vec3 blue = burst.blueSource.point().lerp(burst.center.add(split), move);
        Vec3 red = burst.redSource.point().lerp(burst.center.subtract(split), move);
        float alpha = remaining * visibility;
        burst.blueForm.preview(blue, mix(burst.blueSource.coreRadius(), radius, move), alpha,
                burst.blueSource.ageSeconds() + raw, 0, 1 - smooth(raw / .30f));
        burst.redForm.preview(red, mix(burst.redSource.coreRadius(), radius, move),
                alpha * (burst.incomingBeam == null ? 1 : retract), burst.redSource.ageSeconds() + raw, 0, 0);
        if (burst.incomingBeam != null && retract < .999f) {
            Vec3 socket = burst.redSource.beamOrigin().lerp(burst.redSource.point(), retract);
            float length = (float) burst.redSource.beamOrigin().distanceTo(burst.redSource.point()) * (1 - retract);
            burst.incomingBeam.preview(socket, mix(burst.redSource.coreRadius(), radius, retract),
                    alpha * (1 - retract), burst.redSource.ageSeconds() + raw, length, 0);
            LimitlessRenderer.render(event, burst.incomingBeam);
        }
        Vec3 camera = event.getCamera().getPosition();
        // Sort the two real materials by distance, then let the fusion layer crossfade over them.
        if (blue.distanceToSqr(camera) > red.distanceToSqr(camera)) {
            LimitlessRenderer.render(event, burst.blueForm); LimitlessRenderer.render(event, burst.redForm);
        } else {
            LimitlessRenderer.render(event, burst.redForm); LimitlessRenderer.render(event, burst.blueForm);
        }
    }
    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(-1, -1, 1).setUv(0, 0).setColor(-1); b.addVertex(1, -1, 1).setUv(1, 0).setColor(-1);
        b.addVertex(1, 1, 1).setUv(1, 1).setColor(-1); b.addVertex(-1, 1, 1).setUv(0, 1).setColor(-1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
    private static void arcs(RenderLevelStageEvent event, Burst burst, float partial, float visibility) {
        float age = burst.presentationAge(partial), elapsed = age - 1;
        if (elapsed < 0 || elapsed > 3.2f || !CatTechniqueEffects.ready()) return;
        float front = burst.radius * Math.clamp(elapsed, .04f, 1);
        float opacity = (1 - smooth((elapsed - .55f) / 2.65f)) * visibility;
        int epoch = (int) Math.floor(elapsed / .14f); float within = elapsed / .14f - epoch;
        float blink = smooth(within / .06f) * (1 - smooth((within - .38f) / .40f));
        if (blink < .01f) return;
        Matrix4f projection = event.getProjectionMatrix(), view = event.getModelViewMatrix();
        Vec3 camera = event.getCamera().getPosition(); float aspect = FistSpriteRenderer.aspect(projection);
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            BufferBuilder b = CatTechniqueEffects.begin(age, 0, burst.seed, 1, false, 2);
            for (int ray = 0; ray < 20; ray++) {
                float seed = burst.seed + ray * 23.3f + epoch * 113.17f;
                if (hash(seed + 9) > (elapsed < .8f ? .80f : .30f)) continue;
                double y = hash(seed) * 2 - 1, angle = hash(seed + 5) * Math.PI * 2, horizontal = Math.sqrt(Math.max(0, 1 - y * y));
                Vec3 axis = new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
                Vec3 right = axis.cross(Math.abs(y) > .90 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
                Vec3 up = right.cross(axis).normalize(); Vec3 previous = null;
                float length = front * (.52f + .48f * hash(seed + 7));
                for (int segment = 0; segment <= 10; segment++) {
                    float u = segment / 10f, radius = length * (.10f + .90f * u);
                    float jag = length * .04f * (float) Math.sin(u * Math.PI);
                    Vec3 point = burst.center.add(axis.scale(radius))
                            .add(right.scale((hash(seed + segment * 3.7f) * 2 - 1) * jag))
                            .add(up.scale((hash(seed + segment * 8.1f) * 2 - 1) * jag));
                    if (previous != null) {
                        float alpha = opacity * blink * (1 - u * .60f), width = (.07f + burst.radius * .0018f) * (1 - u * .65f);
                        CatTechniqueEffects.line(b, projection, view, camera, aspect, previous, point, width * 3.7f, .39f, .06f, 1, alpha * .66f);
                        CatTechniqueEffects.line(b, projection, view, camera, aspect, previous, point, width, .78f, .39f, 1, alpha);
                        CatTechniqueEffects.line(b, projection, view, camera, aspect, previous, point, width * .36f, 1, .94f, 1, alpha);
                    }
                    previous = point;
                }
            }
            CatTechniqueEffects.finish(b);
        }
    }
    /** Unscaled camera impulse; the common camera handler applies the user's screen-effect setting. */
    public static float shake(float partial) {
        ensureWorld(); Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return 0;
        float strength = 0;
        for (Burst burst : BURSTS.values()) {
            float age = burst.presentationAge(partial), distance = (float) mc.player.position().distanceTo(burst.center);
            float near = 1 - smooth(distance / (burst.radius + 48));
            float anticipation = age < 1 ? smooth(age) * .13f : 0;
            float impact = age >= 1 ? 1.35f * (float) Math.exp(-(age - 1) * 3.5f) : 0;
            float arrival = age - 1 - distance / burst.radius;
            float passing = arrival > -.07f ? .58f * (float) Math.exp(-Math.abs(arrival) * 13) : 0;
            strength += near * (anticipation + impact + passing);
        }
        return Math.min(1.7f, strength);
    }
    private static SoundEvent sound(String name) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, name)); }
    private static void play(Burst burst, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null) return;
        float distance=(float)mc.gameRenderer.getMainCamera().getPosition().distanceTo(burst.center);
        burst.audio.play(sound,burst.center,volume*Math.clamp(1-distance/240,.12f,1f),pitch);
    }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static boolean validSource(UnlimitedPurple.Source source, Vec3 center) {
        return source != null && source.caster() != null && finite(source.point()) && finite(source.beamOrigin()) && finite(source.direction())
                && source.point().distanceToSqr(center) <= 256 * 256 && source.beamOrigin().distanceToSqr(center) <= 256 * 256
                && source.direction().lengthSqr() > .25 && source.direction().lengthSqr() < 4
                && Float.isFinite(source.influenceRadius()) && source.influenceRadius() > 0 && source.influenceRadius() <= 64
                && Float.isFinite(source.coreRadius()) && source.coreRadius() > 0 && source.coreRadius() <= 32
                && Float.isFinite(source.ageSeconds()) && source.ageSeconds() >= 0 && source.ageSeconds() <= 3600;
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float smooth(float x) { float t = Math.clamp(x, 0, 1); return t * t * (3 - 2 * t); }
    private static float hash(float x) { double value = Math.sin(x * 127.1) * 43758.5453; return (float) (value - Math.floor(value)); }
}
