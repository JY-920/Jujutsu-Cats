package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.item.FistCombo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** 1.20.1 resolves weapon knockback through the enchantment helper before its hit callback. */
@Mixin(EnchantmentHelper.class)
abstract class CursedFistKnockbackMixin {
    @Inject(method="getKnockbackBonus",at=@At("RETURN"),cancellable=true)
    private static void jujutsu$chargedKnockback(LivingEntity attacker,CallbackInfoReturnable<Integer> cir) {
        if(attacker instanceof ServerPlayer player && FistCombo.ready(player.getMainHandItem()))
            cir.setReturnValue(cir.getReturnValueI()+1);
    }
}
