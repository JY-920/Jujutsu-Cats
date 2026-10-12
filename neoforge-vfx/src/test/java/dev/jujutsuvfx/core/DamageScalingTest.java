package dev.jujutsuvfx.core;

/** Regression coverage for tunable damage surviving the player's existing burst protection. */
public final class DamageScalingTest {
    private static void near(float actual,float expected,String message) {
        if(!Float.isFinite(actual)||Math.abs(actual-expected)>.0001f)
            throw new AssertionError(message+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        near(DamageScaling.scaled(50,1),50,"100 percent preserves base damage");
        near(DamageScaling.scaled(20,2),40,"200 percent scales a continuous hit");
        near(DamageScaling.scaled(50,0),0,"zero damage is allowed");
        near(DamageScaling.budgeted(8,2,6,1),4,"default burst protection is unchanged");
        near(DamageScaling.budgeted(24,0,12,2),24,"increased damage is not swallowed by the old cap");
        near(DamageScaling.budgeted(6,0,12,.5f),6,"reduced damage reduces the available burst budget");
        float spent=DamageScaling.baseSpent(8,2); // 200% Red hit consumed 4 baseline points.
        near(spent,4,"ledger records normalized damage");
        near(DamageScaling.budgeted(10,spent,12,.5f),4,"a different multiplier shares a fair baseline budget");
        spent+=DamageScaling.baseSpent(4,.5f);
        near(DamageScaling.budgeted(100,spent,12,10),0,"switching techniques cannot reset the burst budget");
        near(DamageScaling.baseSpent(4,0),0,"disabled attacks never divide by zero");
        near(DamageScaling.budgeted(100,0,12,0),0,"disabled attacks cannot damage health");
        near(DamageScaling.budgeted(4,20,12,1),0,"an exhausted budget never heals the player");
        near(DamageScaling.scaled(Float.NaN,2),0,"invalid damage is not propagated into health");
        near(DamageScaling.scaled(20,Float.NaN),0,"invalid factors fail closed");
        near(DamageScaling.scaled(-2,2),0,"negative damage is not healing");
        System.out.println("DamageScaling: default balance, zero damage, mixed multipliers and player burst protection passed.");
    }
}
