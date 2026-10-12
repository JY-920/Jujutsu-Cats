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
        float multiplier=VfxConfig.damagePercent(technique)/100f;
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
