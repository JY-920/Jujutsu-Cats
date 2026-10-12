package dev.jujutsuvfx.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.jujutsuvfx.item.FistCombo;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scale the final primary melee amount once, including cooldown, crit and enchantment calculation. */
@Mixin(Player.class)
abstract class CursedFistDamageMixin {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean jujutsu$chargedContact(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        float amount = FistCombo.empoweredHit(player, target, source) ? damage * 2 : damage;
        return original.call(target, source, amount);
    }
}
