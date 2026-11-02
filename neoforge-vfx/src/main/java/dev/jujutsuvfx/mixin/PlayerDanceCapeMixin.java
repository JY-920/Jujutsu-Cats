package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.client.PlayerDanceClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The rigid vanilla cape cannot follow the dance's curved torso. Hide only during the dance. */
@Mixin(CapeLayer.class)
abstract class PlayerDanceCapeMixin {
 @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",at=@At("HEAD"),cancellable=true)
 private void hideDanceCape(PoseStack p,MultiBufferSource b,int light,AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci){
  if(PlayerDanceClient.active(player))ci.cancel();
 }
}
