package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.jujutsuvfx.core.FurnaceFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Irregular flame cards follow the punch in world space; all impacts are one sorted draw batch. */
final class FistWaveRenderer {
    private record Flame(Vector3f center, Vector3f heading, float width, float length, float roll,
                         int shape, float erosion, float power, float seed, float opacity, boolean redFlame) {}
    private FistWaveRenderer() {}

    static void render(RenderLevelStageEvent event, List<ClientFistEffects.Burst> hits) {
        Minecraft mc = Minecraft.getInstance();
        if (!FistSpriteRenderer.ready() || mc.level == null || hits.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = ClientEffectClock.ticks();
        Vec3 camera = event.getCamera().getPosition();
        List<Flame> flames = new ArrayList<>(58);
        int emberBudget = 40;
        for (int index = hits.size() - 1; index >= Math.max(0, hits.size() - 6); index--) {
            ClientFistEffects.Burst hit = hits.get(index);
            float age = hit.age(now, partial);
            double distance = hit.point().distanceToSqr(camera);
            if (age >= ClientFistEffects.LIFETIME || distance > 64 * 64) continue;
            Vec3 axis = hit.direction().normalize();
            if (axis.lengthSqr() < .001) continue;
            Vec3 right = FurnaceFlight.right(axis), up = right.cross(axis).normalize();
            float power = hit.power(), seed = hit.seed();
            float launch = 1 - (float) Math.exp(-age * 22), bloom = smooth(age / .13f);
            float opacity = (1 - smooth((age - .17f) / .29f)) * Math.min(1, age * 100 + .15f);
            if (opacity > .005f) for (int j = 0; j < 3; j++) {
                float s = seed + j * 31.37f;
                double angle = random(s + 6) * Math.PI * 2;
                Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                Vec3 heading = axis.add(radial.scale(j == 0 ? .08 : .30 + random(s + 2) * .35)).normalize();
                Vec3 center = hit.point().add(heading.scale(.22 + launch * (.85 + power * 1.35)))
                        .add(radial.scale(j == 0 ? 0 : bloom * .32));
                float width = (.48f + power * .68f) * (.25f + bloom * .75f) * (j == 0 ? 1 : .67f);
                if (hit.empowered()) add(flames, event, camera, center, heading, width * .95f,
                        width * (1.35f + random(s + 4) * .70f), .12f * (random(s + 5) - .5f), j % 3,
                        smooth((age - .11f) / .38f), power, s + 91, opacity * .76f, true);
                add(flames, event, camera, center, heading, width, width * (1.35f + random(s + 4) * .70f),
                        .12f * (random(s + 5) - .5f), hit.empowered() ? FistSpriteRenderer.LIGHTNING : j % 3,
                        smooth((age - .11f) / .38f), power, s,
                        opacity * (j == 0 ? .85f : .67f));
            }
            int count = distance < 18 * 18 ? 16 : distance < 36 * 36 ? 10 : 5;
            for (int i = 0; i < count && emberBudget > 0; i++) {
                float s = seed + i * 17.37f;
                float born = .025f + random(s + 2) * .13f, t = age - born;
                float life = .34f + random(s + 7) * .34f;
                if (t <= 0 || t > life) continue;
                double angle = random(s + 3) * Math.PI * 2;
                Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                Vec3 heading = axis.add(radial.scale(.20f + random(s + 5) * .93f)).normalize();
                float speed = 5.5f + random(s + 11) * 9.5f;
                float travel = (float) ((1 - Math.exp(-t * 2.4)) / 2.4) * speed;
                Vec3 location = hit.point().add(axis.scale(.32 + random(s + 9) * .60))
                        .add(heading.scale(travel)).add(radial.scale(.08 + .25 * random(s + 4)))
                        .add(0, t * t * (.7 + random(s + 10)), 0);
                float fade = smooth(t / .035f) * (1 - smooth((t / life - .45f) / .55f));
                // Explicit size bands guarantee conspicuous tiny, medium and large wisps in each near burst.
                int sizeBand = i % 7;
                float baseSize = sizeBand < 3 ? .055f + random(s + 13) * .080f
                        : sizeBand < 6 ? .16f + random(s + 13) * .13f : .34f + random(s + 13) * .17f;
                float size = baseSize * (.70f + power * .30f)
                        * (1 + t * 1.1f) * (.48f + fade * .52f);
                Vec3 tilt = heading.add(radial.scale(Math.sin(t * 14 + s) * .21)).add(0, t * .65, 0).normalize();
                int shape = hit.empowered() ? FistSpriteRenderer.LIGHTNING : Math.floorMod(i + (int) seed, 3);
                float stretch = switch (shape) {
                    case FistSpriteRenderer.ROUND -> .72f + random(s + 14) * .48f;
                    case FistSpriteRenderer.GOURD -> 1.30f + random(s + 14) * .65f;
                    case FistSpriteRenderer.LIGHTNING -> 1.80f + random(s + 14) * 1.40f;
                    default -> 1.70f + random(s + 14) * 1.25f;
                };
                add(flames, event, camera, location, tilt, size * (.78f + random(s + 16) * .42f), size * stretch,
                        (float) Math.sin(t * 11 + s) * .48f, shape, smooth((t / life - .30f) / .70f), power, s, fade);
                emberBudget--;
                if (hit.empowered() && i % 2 == 0 && emberBudget > 0) {
                    int flameShape = Math.floorMod(i + (int) seed, 3);
                    float flameStretch = flameShape == FistSpriteRenderer.ROUND ? .9f : flameShape == FistSpriteRenderer.GOURD ? 1.6f : 2.0f;
                    add(flames, event, camera, location, tilt, size * 1.14f, size * flameStretch,
                            (float) Math.sin(t * 11 + s) * .48f, flameShape,
                            smooth((t / life - .30f) / .70f), power, s + 47, fade * .86f, true);
                    emberBudget--;
                }
            }
        }
        if (flames.isEmpty()) return;
        flames.sort(Comparator.comparingDouble(flame -> flame.center.z));
        float time = ((now % 24000L) + partial) * .05f;
        BufferBuilder buffer = FistSpriteRenderer.begin(time, false, .4f, .5f, 0, 0, false);
        float aspect = FistSpriteRenderer.aspect(event.getProjectionMatrix());
        for (Flame flame : flames) emit(buffer, event, flame, aspect);
        FistSpriteRenderer.finish(buffer);
    }

    private static void add(List<Flame> flames, RenderLevelStageEvent event, Vec3 camera, Vec3 position,
                            Vec3 heading, float width, float length, float roll,
                            int shape, float erosion, float power, float seed, float opacity) {
        add(flames, event, camera, position, heading, width, length, roll, shape, erosion, power, seed, opacity, false);
    }
    private static void add(List<Flame> flames, RenderLevelStageEvent event, Vec3 camera, Vec3 position,
                            Vec3 heading, float width, float length, float roll,
                            int shape, float erosion, float power, float seed, float opacity, boolean redFlame) {
        Vec3 relative = position.subtract(camera);
        Vector3f center = event.getModelViewMatrix().transformPosition(new Vector3f((float) relative.x,
                (float) relative.y, (float) relative.z));
        if (center.z > -.10f || opacity < .005f) return;
        Vector3f axis = event.getModelViewMatrix().transformDirection(new Vector3f((float) heading.x,
                (float) heading.y, (float) heading.z));
        flames.add(new Flame(center, axis, width, length, roll, shape, erosion, power, seed, opacity, redFlame));
    }

    private static void emit(BufferBuilder buffer, RenderLevelStageEvent event, Flame flame, float aspect) {
        var projection = event.getProjectionMatrix();
        Vector3f center = FistSpriteRenderer.project(projection, flame.center, aspect);
        if (center == null) return;
        Vector3f tip = FistSpriteRenderer.project(projection,
                new Vector3f(flame.center).fma(.25f, flame.heading), aspect);
        Vector2f up = tip == null ? new Vector2f() : new Vector2f(tip.x - center.x, tip.y - center.y);
        if (up.lengthSquared() < .000004f) {
            float angle = random(flame.seed + 19) * (float) (Math.PI * 2);
            up.set((float) Math.sin(angle), (float) Math.cos(angle));
        } else up.normalize();
        float c = (float) Math.cos(flame.roll), s = (float) Math.sin(flame.roll);
        up.set(up.x * c - up.y * s, up.x * s + up.y * c);
        float scale = Math.abs(projection.m11() / flame.center.z);
        float width = flame.width * scale, length = flame.length * scale;
        FistSpriteRenderer.quadColored(buffer, center.x / aspect, center.y, center.z,
                up.y * width / aspect, -up.x * width, up.x * length / aspect, up.y * length,
                flame.shape, flame.seed, flame.erosion, flame.power, flame.opacity, flame.redFlame);
    }
    private static float smooth(float t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private static float random(float seed) {
        double v = Math.sin(seed * 12.9898 + 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }
}
