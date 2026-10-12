package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.item.FistCombo;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Add Knockback I only while this successful charged melee is being resolved by vanilla. */
@Mixin(LivingEntity.class)
abstract class CursedFistKnockbackMixin {
    @Inject(method = "getKnockback", at = @At("RETURN"), cancellable = true)
    private void jujutsu$chargedKnockback(Entity target, DamageSource source, CallbackInfoReturnable<Float> cir) {
        LivingEntity attacker = (LivingEntity) (Object) this;
        if (FistCombo.empoweredHit(attacker, target, source)) cir.setReturnValue(cir.getReturnValueF() + 1f);
    }
}
