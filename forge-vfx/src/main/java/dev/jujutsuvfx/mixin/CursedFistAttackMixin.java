package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.client.CursedFistHands;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the cursed fist's third-person attack sweep; skin sleeves inherit the resulting arm pose. */
@Mixin(HumanoidModel.class)
abstract class CursedFistAttackMixin {
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void jujutsu$forwardPunch(LivingEntity entity, float age, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if(dev.jujutsuvfx.client.PlayfulCloudHands.pose(entity,model,age)){ci.cancel();return;}
        if(dev.jujutsuvfx.client.MartialClient.owns(entity)){ci.cancel();return;}
        if (model.attackTime <= 0) return;
        InteractionHand hand = player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm;
        if (!VfxItems.isHeld(player.getItemInHand(hand))) return;
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float sign = side == HumanoidArm.RIGHT ? 1 : -1, reach = CursedFistHands.thrust(model.attackTime);
        arm.xRot = Mth.lerp(reach, arm.xRot, -Mth.HALF_PI + model.head.xRot * .82f);
        arm.yRot = Mth.lerp(reach, arm.yRot, -sign * .045f);
        arm.zRot = Mth.lerp(reach, arm.zRot, sign * .035f);
        arm.z = -2.2f * reach;
        ci.cancel();
    }
}
