package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.client.LivingDanceFallback;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
@Mixin(EntityRenderDispatcher.class)
abstract class LivingDanceFallbackMixin {
 // Observes the call without @ModifyArgs: its synthetic Args$ class can fail to load under Forge 1.20.1.
 @ModifyArg(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),index=0)
 private Entity jujutsu$specialRenderer(Entity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){LivingDanceFallback.before(e,yaw,partial,p,buffers,light);return e;}
}
