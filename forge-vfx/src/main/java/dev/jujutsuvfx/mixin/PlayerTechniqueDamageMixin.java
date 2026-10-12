package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.TechniquePlayerRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class PlayerTechniqueDamageMixin {
    @ModifyVariable(method="hurt",at=@At("HEAD"),argsOnly=true)
    private float jujutsuvfx$balanceBeforeShield(float amount,DamageSource source,float original) {
        return TechniquePlayerRules.incoming((LivingEntity)(Object)this,source,amount);
    }
}
