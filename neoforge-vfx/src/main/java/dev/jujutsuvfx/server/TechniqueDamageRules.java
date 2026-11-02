package dev.jujutsuvfx.server;

import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.core.DamageScaling;
import dev.jujutsuvfx.core.TechniqueDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Snapshot tuning on each contact; player balancing applies it after its original base calculation. */
public final class TechniqueDamageRules {
    private TechniqueDamageRules() {}

    public static boolean hurt(LivingEntity target,DamageSource source,float amount,TechniqueDamage technique) {
        if(!Float.isFinite(amount)||amount<=0)return false;
        return hurtScaled(target,source,amount,VfxConfig.damagePercent(technique)/100f * dev.jujutsuvfx.core.YutaCombatRules.amplification(
            source.getEntity() instanceof ServerPlayer, source.getEntity() instanceof LivingEntity owner && dev.jujutsuvfx.effect.VfxEffects.amplified(owner),true));
    }
    public static boolean hurtYutaMelee(LivingEntity target,DamageSource source,float amount) {
        return hurtScaled(target,source,amount,1f);
    }
    private static boolean hurtScaled(LivingEntity target,DamageSource source,float amount,float tuning) {
        if(!Float.isFinite(amount)||amount<=0)return false;
        float multiplier=tuning*(source.getEntity() instanceof dev.jujutsuvfx.entity.YutaCat cat?YutaDomain.damageMultiplier(cat):1f);
        if(multiplier<=0)return false;
        DamageSource tuned=new TunedSource(source,multiplier);
        return target.hurt(tuned,target instanceof ServerPlayer?amount:DamageScaling.scaled(amount,multiplier));
    }
    public static float multiplier(DamageSource source) {
        return source instanceof TunedSource tuned?tuned.multiplier:1f;
    }
    private static final class TunedSource extends DamageSource {
        private final float multiplier;
        private final DamageSource original;
        private TunedSource(DamageSource original,float multiplier) {
            super(original.typeHolder(),original.getDirectEntity(),original.getEntity(),original.sourcePositionRaw());
            this.original=original;this.multiplier=multiplier;
        }
        @Override public Vec3 getSourcePosition() { return original.getSourcePosition(); }
    }
}
