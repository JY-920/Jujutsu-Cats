package dev.jujutsuvfx.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.client.*;
import dev.jujutsuvfx.server.LivingDance;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
abstract class LivingDanceRenderMixin {
 @Shadow protected EntityModel model;
 @Inject(method="<init>",at=@At("TAIL"))
 private void jujutsu$layer(EntityRendererProvider.Context context,EntityModel model,float shadow,CallbackInfo ci){((LivingEntityRenderer)(Object)this).addLayer(new LivingDanceLayer((LivingEntityRenderer)(Object)this));}
 @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER))
 private void jujutsu$root(LivingEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light,CallbackInfo ci){LivingDanceClient.root(e,model,p,partial);}
 // @ModifyArgs generates a synthetic Args$ class that the Forge 1.20.1 module loader can fail to load
 // (NoClassDefFoundError: org/spongepowered/asm/synthetic/args/Args$N). Single @ModifyArg handlers do not.
 @ModifyArg(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),index=1)
 private float jujutsu$swing(net.minecraft.world.entity.Entity e,float swing,float amount,float age,float yaw,float pitch){
  if(!(e instanceof LivingEntity l)||!LivingDanceClient.active(l))return swing;
  if(NativeDanceRig.available(model))return 0f;
  float time=LivingDance.age(l,Math.max(0,Math.min(1,age-l.tickCount)));
  return LivingDance.style(l)==2?(time-10)*(float)(Math.PI*2/dev.jujutsuvfx.core.ShuffleDanceMotion.STEP_CYCLE/.6662):time*.59f;
 }
 @ModifyArg(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),index=2)
 private float jujutsu$amount(net.minecraft.world.entity.Entity e,float swing,float amount,float age,float yaw,float pitch){
  if(!(e instanceof LivingEntity l)||!LivingDanceClient.active(l))return amount;
  if(NativeDanceRig.available(model))return 0f;
  return LivingDance.style(l)==2?.85f:.65f;
 }
 @ModifyArg(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),index=4)
 private float jujutsu$yaw(net.minecraft.world.entity.Entity e,float swing,float amount,float age,float yaw,float pitch){return e instanceof LivingEntity l&&LivingDanceClient.active(l)?0f:yaw;}
 @ModifyArg(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),index=5)
 private float jujutsu$pitch(net.minecraft.world.entity.Entity e,float swing,float amount,float age,float yaw,float pitch){return e instanceof LivingEntity l&&LivingDanceClient.active(l)?0f:pitch;}
 @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",shift=At.Shift.AFTER))
 private void jujutsu$pose(LivingEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light,CallbackInfo ci){LivingDanceClient.pose(e,model,partial);}
 @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
 private void jujutsu$restore(LivingEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light,CallbackInfo ci){if(LivingDanceClient.active(e))LivingDanceClient.restore(model);}
}
