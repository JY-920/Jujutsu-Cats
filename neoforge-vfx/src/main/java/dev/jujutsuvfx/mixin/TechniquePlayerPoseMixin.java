package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
abstract class TechniquePlayerPoseMixin {
    @Inject(method="getArmPose",at=@At("HEAD"),cancellable=true)
    private static void jujutsu$restingHand(AbstractClientPlayer player,InteractionHand hand,
                                           CallbackInfoReturnable<HumanoidModel.ArmPose> cir){
        if(VfxItems.isTechniqueHand(player.getItemInHand(hand)))cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
    }
}
