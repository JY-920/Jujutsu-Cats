package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.client.LivingDanceFallback;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(EntityRenderDispatcher.class)
abstract class LivingDanceFallbackMixin {
 @ModifyArgs(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
 private void jujutsu$specialRenderer(Args args){LivingDanceFallback.before(args.get(0),args.get(1),args.get(2),args.get(3),args.get(4),args.get(5));}
}
