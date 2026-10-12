package dev.jujutsuvfx.core;

import java.math.BigDecimal;

/** Reference hit values, before armor, defenses and the shared burst budget; never changes combat. */
public final class TechniqueDamagePreview {
    private TechniqueDamagePreview() {}
    public record Range(float min,float max) {
        public String text() { return min==max?number(min):number(min)+"–"+number(max); }
    }
    public static String number(float value) {
        return BigDecimal.valueOf(Math.round((double)value*10000)/10000d).stripTrailingZeros().toPlainString();
    }
    public static Range sample(TechniqueDamage technique,boolean catCaster,boolean playerTarget,boolean cinematic,int percent) {
        if(percent<0||percent>1000)throw new IllegalArgumentException("Damage percentage out of range");
        Range raw=switch(technique) {
            case BLUE -> new Range(6,6);
            // Include orb contact, terminal impact and the cannon's individual hit.
            case RED -> catCaster?new Range(18,70):new Range(40,160);
            case PURPLE_SMALL,PURPLE_LARGE -> new Range(200,200);
            case PURPLE_AMPLIFIED -> new Range(400,400);
            case PURPLE_UNLIMITED -> {
                float amount=cinematic?999:playerTarget?38:240;
                yield new Range(amount,amount);
            }
            case DISMANTLE,CLEAVE -> new Range(catCaster?59:50,catCaster?59:50);
            case FURNACE -> new Range(60,200);
            // Ordinary targets: focused / normal slash. Gojo's special 6-point basis is shown separately.
            case SHRINE -> new Range(20,50);
        };
        float low=raw.min(),high=raw.max();
        if(playerTarget&&technique!=TechniqueDamage.PURPLE_UNLIMITED) {
            low=playerBalanced(technique,low,catCaster);high=playerBalanced(technique,high,catCaster);
        } else if(catCaster&&!playerTarget&&switch(technique) {
            case BLUE,RED,PURPLE_SMALL,PURPLE_LARGE,PURPLE_AMPLIFIED -> true;
            default -> false;
        }) { low*=1.2f;high*=1.2f; }
        return new Range(DamageScaling.scaled(low,percent/100f),DamageScaling.scaled(high,percent/100f));
    }
    private static float playerBalanced(TechniqueDamage technique,float raw,boolean cat) {
        if(!cat)return technique==TechniqueDamage.SHRINE?Math.min(.5f,raw*.025f):Math.min(6f,raw*.08f);
        return switch(technique) {
            case BLUE -> Math.min(6f,raw*.8f);
            case RED -> Math.min(20f,raw*.35f);
            case PURPLE_SMALL,PURPLE_LARGE,PURPLE_AMPLIFIED -> Math.min(28f,raw*.12f);
            case DISMANTLE,CLEAVE -> Math.min(14f,raw*.24f);
            case FURNACE -> Math.min(24f,raw*.12f);
            case SHRINE -> Math.min(6f,raw*.2f);
            case PURPLE_UNLIMITED -> raw;
        };
    }
}
