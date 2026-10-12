package dev.jujutsuvfx.core;

/** One monotonic health-loss timeline for the two primary fighters in a duel. */
public final class DuelProgress {
    public enum Beat { NONE, STRUCTURE, DOMAIN, SOLO_DOMAIN, WHEEL, FINALE }
    // Average health lost by the two primary fighters; leave room for the two-on-one act.
    private static final float STRUCTURE_PROGRESS = .15f;
    private static final float DOMAIN_PROGRESS = .30f;
    private static final float SOLO_PROGRESS = .45f;
    private static final float WHEEL_PROGRESS = .60f;
    private DuelProgress() {}
    public static float bounded(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
    private static float remaining(float health, float maximum) {
        return Float.isFinite(health) && Float.isFinite(maximum) && maximum > 0
                ? bounded(health / maximum) : 1;
    }
    public static float observe(float previous, float gojoHealth, float gojoMaximum, float sukunaHealth, float sukunaMaximum) {
        float lost = 1 - (remaining(gojoHealth, gojoMaximum) + remaining(sukunaHealth, sukunaMaximum)) * .5f;
        return Math.max(bounded(previous), bounded(lost));
    }
    public static float restored(float saved, boolean structures, boolean domains, boolean wheel, boolean finished) {
        return restored(saved,structures,domains,false,wheel,finished);
    }
    public static float restored(float saved,boolean structures,boolean domains,boolean solo,boolean wheel,boolean finished) {
        return Math.max(bounded(saved), finished ? 1 : wheel ? WHEEL_PROGRESS : solo ? SOLO_PROGRESS : domains ? DOMAIN_PROGRESS : structures ? STRUCTURE_PROGRESS : 0);
    }
    public static Beat next(float progress, boolean structures, boolean domains, boolean wheel, boolean terminal, boolean finished) {
        return next(progress,structures,domains,true,wheel,terminal,finished);
    }
    public static Beat next(float progress,boolean structures,boolean domains,boolean solo,boolean wheel,boolean terminal,boolean finished) {
        if (finished) return Beat.NONE;
        if (terminal || progress >= 1) return Beat.FINALE;
        if (!structures && progress >= STRUCTURE_PROGRESS) return Beat.STRUCTURE;
        if (!domains && progress >= DOMAIN_PROGRESS) return Beat.DOMAIN;
        if (!solo && progress >= SOLO_PROGRESS) return Beat.SOLO_DOMAIN;
        if (!wheel && progress >= WHEEL_PROGRESS) return Beat.WHEEL;
        return Beat.NONE;
    }
}
