package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.client.MartialClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class MartialModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("HEAD"))
    private void jujutsu$restorePose(LivingEntity actor,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
        MartialClient.restore((HumanoidModel<?>)(Object)this);
    }
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void jujutsu$martialPose(LivingEntity actor,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
        MartialClient.pose(actor,(HumanoidModel<?>)(Object)this,age);
    }
}
