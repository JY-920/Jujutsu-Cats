package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.server.LivingDance;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Mob.class)
abstract class LivingDanceAiMixin {
 // Do not change isNoAi: that value is persisted by vanilla when saving the mob.
 @Inject(method="isEffectiveAi",at=@At("HEAD"),cancellable=true)
 private void jujutsu$pause(CallbackInfoReturnable<Boolean> ci){if(LivingDance.active((Mob)(Object)this))ci.setReturnValue(false);}
}
