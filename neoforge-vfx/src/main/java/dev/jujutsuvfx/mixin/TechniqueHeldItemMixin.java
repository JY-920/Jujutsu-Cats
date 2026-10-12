package dev.jujutsuvfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only suppress the third-person tool mesh, leaving arms, aura layers and GUI icons intact. */
@Mixin(ItemInHandLayer.class)
abstract class TechniqueHeldItemMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void jujutsu$emptyHand(LivingEntity actor,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,
                                   PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(actor instanceof Player&&stack.getItem() instanceof dev.jujutsuvfx.item.PlayfulCloudItem){
            dev.jujutsuvfx.client.PlayfulCloudHands.thirdPerson(actor,arm,poses,buffers,light);ci.cancel();
        }else if(actor instanceof Player&&VfxItems.isTechniqueHand(stack))ci.cancel();
    }
}
