package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.client.PurpleChargeHands;
import dev.jujutsuvfx.client.RedChargeHands;
import dev.jujutsuvfx.client.SukunaSlashHands;
import dev.jujutsuvfx.client.TechniqueHands;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select the active skill's arm pose; each track owns its release and return blend. */
@Mixin(HumanoidModel.class)
abstract class PurpleHandReturnMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void jujutsu$posePurpleHands(LivingEntity entity, float limbSwing, float limbAmount,
                                          float age, float yaw, float pitch, CallbackInfo ci) {
        if(TechniqueHands.renderingHand()||TechniqueHands.poseArms((HumanoidModel<?>) (Object) this,entity))return;
        PurpleChargeHands.poseArms((HumanoidModel<?>) (Object) this, entity);
        RedChargeHands.poseArms((HumanoidModel<?>) (Object) this, entity);
        SukunaSlashHands.poseArms((HumanoidModel<?>) (Object) this, entity);
    }
}
