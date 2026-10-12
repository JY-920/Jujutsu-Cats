package dev.jujutsuvfx.core;

/** The burst ledger uses baseline points, so differently tuned spells share the same budget. */
public final class DamageScaling {
    private DamageScaling() {}
    public static float scaled(float amount,float multiplier) {
        if(!Float.isFinite(amount)||!Float.isFinite(multiplier)||amount<=0||multiplier<=0)return 0;
        float result=amount*multiplier;
        return Float.isFinite(result)?result:Float.MAX_VALUE;
    }
    public static float baseSpent(float actualDamage,float multiplier) {
        if(!Float.isFinite(actualDamage)||!Float.isFinite(multiplier)||actualDamage<=0||multiplier<=0)return 0;
        return actualDamage/multiplier;
    }
    public static float budgeted(float actualDamage,float baselineSpent,float baselineBudget,float multiplier) {
        return Math.min(scaled(actualDamage,1),scaled(Math.max(0,baselineBudget-baselineSpent),multiplier));
    }
}
