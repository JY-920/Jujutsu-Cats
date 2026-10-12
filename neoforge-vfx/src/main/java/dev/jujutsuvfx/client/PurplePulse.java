package dev.jujutsuvfx.client;

/** Brief screen-white accents have their own timing, independent from the persistent lightning geometry. */
final class PurplePulse {
    private PurplePulse() {}
    private static int burst(LimitlessCast cast, long now, float partial) {
        if (!cast.purple() || cast.preparingPurple() || cast.phase() == 2) return -1;
        if (cast.phase() == 1) return cast.phaseAge(now, partial) < .26f ? 2 : -1;
        float age = cast.age(now, partial);
        if (!cast.largePurple()) return -1;
        if (age >= 1.62f && age < 1.78f) return 0;
        if (age >= 2.64f && age < 2.86f) return 1;
        return -1;
    }
    private static float burstAge(LimitlessCast cast, long now, float partial, int burst) {
        return burst == 2 ? cast.phaseAge(now, partial) : cast.age(now, partial) - (burst == 0 ? 1.62f : 2.64f);
    }
    static float flash(LimitlessCast cast, long now, float partial) {
        if (!dev.jujutsuvfx.config.VfxConfig.flashes()) return 0;
        int burst = burst(cast, now, partial);
        if (burst < 0) return 0;
        float age = burstAge(cast, now, partial, burst);
        return (burst == 2 ? .86f : burst == 1 ? .22f : .10f) * (float) Math.exp(-age * (burst == 2 ? 25 : 35));
    }
    static float reveal(LimitlessCast cast, long now, float partial) {
        return cast.preparingPurple() ? 0 : 1;
    }
}
