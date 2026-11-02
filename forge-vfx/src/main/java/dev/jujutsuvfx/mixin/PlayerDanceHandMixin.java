package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.client.PlayerDanceClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** First-person native arm setup must not inherit the full third-person dance root. */
@Mixin(PlayerRenderer.class)
abstract class PlayerDanceHandMixin {
 @Inject(method="renderHand",at=@At("HEAD"))
 private void before(PoseStack p,MultiBufferSource b,int light,AbstractClientPlayer player,ModelPart arm,ModelPart sleeve,CallbackInfo ci){PlayerDanceClient.beginNativeHand();}
 @Inject(method="renderHand",at=@At("RETURN"))
 private void after(PoseStack p,MultiBufferSource b,int light,AbstractClientPlayer player,ModelPart arm,ModelPart sleeve,CallbackInfo ci){PlayerDanceClient.endNativeHand();}
}
