package dev.jujutsuvfx.client;

import net.minecraftforge.client.event.ViewportEvent;
import net.minecraft.world.phys.Vec3;

/** Render-only camera motion follows the cast clock, including its visibility and cancellation. */
final class VoidFeedback {
    private VoidFeedback() {}

    static void camera(ViewportEvent.ComputeCameraAngles event, UnlimitedVoid domain,
                       long now, float partial, float effectScale) {
        float age = domain.age(now, partial);
        float strength = range(domain, event.getCamera().getPosition())
                * domain.visibility(now, partial) * effectScale;
        if (strength < .001f) return;

        // White invades during 0.025–0.90 s; its irregular cosmic reveal runs during 0.94–3.10 s.
        float invasion = .20f * smooth((age - .05f) / .20f) * (1 - smooth((age - .80f) / .40f));
        float breach = .74f * impulse(age - 1.20f, .65f) + .88f * impulse(age - 1.72f, .72f);
        float opened = .24f * impulse(age - 3.10f, .65f);
        // Keep a nonzero wind-pressure tremor throughout the universe, even while standing still.
        float flow = (.26f + .14f * gust(domain, age)) * smooth((age - .94f) / .60f);
        float amplitude = invasion + breach + opened + flow;
        float phase = (domain.born & 1023L) * .17f;

        // Continuous, mixed frequencies retain a quick impact without choosing a new random angle per frame.
        float pitch = amplitude * wave(age, 63, phase) + breach * .16f * (float) Math.cos(age * 24)
                + flow * .22f * (float) Math.sin(age * 9.3f + phase);
        float yaw = amplitude * .56f * wave(age, 53, phase + 2.1f)
                + flow * .14f * (float) Math.sin(age * 7.7f + phase + 1.2f);
        float roll = amplitude * .63f * wave(age, 45, phase + 4.3f)
                + flow * .18f * (float) Math.sin(age * 8.6f + phase + 3.1f);
        event.setPitch(event.getPitch() + dev.jujutsuvfx.platform.Numbers.clamp(pitch, -.95f, .95f) * strength);
        event.setYaw(event.getYaw() + dev.jujutsuvfx.platform.Numbers.clamp(yaw, -.60f, .60f) * strength);
        event.setRoll(event.getRoll() + dev.jujutsuvfx.platform.Numbers.clamp(roll, -.65f, .65f) * strength);
    }

    static float range(UnlimitedVoid domain, Vec3 listener) {
        float distance = (float) listener.distanceTo(domain.center);
        return 1 - smooth((distance - domain.radius) / Math.max(10, domain.radius * .35f));
    }

    /** Shared slow gusts bind the sustained camera movement to the air-friction loop. */
    static float gust(UnlimitedVoid domain, float age) {
        float phase = (domain.born & 1023L) * .17f;
        return .5f + .30f * (float) Math.sin(age * 2.15f + phase)
                + .20f * (float) Math.sin(age * 5.37f + phase * 1.3f);
    }

    private static float impulse(float elapsed, float duration) {
        if (elapsed < 0 || elapsed >= duration) return 0;
        float tail = 1 - elapsed / duration;
        return smooth(elapsed / .025f) * tail * tail;
    }

    private static float wave(float age, float frequency, float phase) {
        return .68f * (float) Math.sin(age * frequency + phase)
                + .22f * (float) Math.sin(age * frequency * 1.73f + phase * 1.9f)
                + .10f * (float) Math.sin(age * frequency * 2.71f + phase * .7f);
    }

    private static float smooth(float value) {
        value = dev.jujutsuvfx.platform.Numbers.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }
}
