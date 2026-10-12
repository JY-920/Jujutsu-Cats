package dev.jujutsuvfx.core;

/** Loader-independent silhouette and animation. Keep the GLSL profile in sync. */
public final class RiftShape {
    public static final float STRIKE_SECONDS = 0.085f;
    public static final float FADE_SECONDS = 0.22f;
    private RiftShape() {}

    public static float width(float y) {
        float taper = Math.max(0, 1 - Math.abs(y));
        return (float) Math.pow(taper, 0.72) *
                (0.86f + 0.085f * (float) Math.sin(y * 19) + 0.045f * (float) Math.sin(y * 43 + 1));
    }

    public static float spine(float y) {
        return 0.045f * (float) Math.sin(y * 11) + 0.022f * (float) Math.sin(y * 27);
    }

    public static float envelope(float ageSeconds, float durationSeconds) {
        if (ageSeconds <= 0 || ageSeconds >= durationSeconds) return 0;
        return smooth(ageSeconds / 0.65f) * smooth((durationSeconds - ageSeconds) / 1.0f);
    }

    public static float slashEnvelope(float age, float duration) {
        if (age <= 0 || age >= duration) return 0;
        // The moving blade front reveals each part locally in the shader. A global
        // opening ramp would widen and brighten the whole scar at the same time.
        return smooth((duration - age) / FADE_SECONDS);
    }

    /** Accelerating cut, then an exactly stationary scar. There is no post-impact drift. */
    public static float approach(float age) {
        float t = dev.jujutsuvfx.platform.Numbers.clamp(age / STRIKE_SECONDS, 0, 1);
        return 1 - t * t;
    }

    public static float cutProgress(float age) {
        return dev.jujutsuvfx.platform.Numbers.clamp(age / STRIKE_SECONDS, 0, 1);
    }

    public static float impact(float age) {
        float t = (age - STRIKE_SECONDS) / 0.025f;
        return (float) Math.exp(-t * t);
    }

    private static float smooth(float x) {
        x = dev.jujutsuvfx.platform.Numbers.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
