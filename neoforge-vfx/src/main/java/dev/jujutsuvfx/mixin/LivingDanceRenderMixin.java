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
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
abstract class LivingDanceRenderMixin {
 @Shadow protected EntityModel model;
 @Inject(method="<init>",at=@At("TAIL"))
 private void jujutsu$layer(EntityRendererProvider.Context context,EntityModel model,float shadow,CallbackInfo ci){((LivingEntityRenderer)(Object)this).addLayer(new LivingDanceLayer((LivingEntityRenderer)(Object)this));}
 @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER))
 private void jujutsu$root(LivingEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light,CallbackInfo ci){LivingDanceClient.root(e,model,p,partial);}
 @ModifyArgs(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
 private void jujutsu$limbs(Args args){LivingEntity e=(LivingEntity)args.get(0);if(LivingDanceClient.active(e)){float partial=Math.max(0,Math.min(1,(Float)args.get(3)-e.tickCount));float time=LivingDance.age(e,partial);if(NativeDanceRig.available(model)){args.set(1,0f);args.set(2,0f);}else{args.set(1,LivingDance.style(e)==2?(time-10)*(float)(Math.PI*2/dev.jujutsuvfx.core.ShuffleDanceMotion.STEP_CYCLE/.6662):time*.59f);args.set(2,LivingDance.style(e)==2?.85f:.65f);}args.set(4,0f);args.set(5,0f);}}
 @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",shift=At.Shift.AFTER))
 private void jujutsu$pose(LivingEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light,CallbackInfo ci){LivingDanceClient.pose(e,model,partial);}
 @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
 private void jujutsu$restore(LivingEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light,CallbackInfo ci){if(LivingDanceClient.active(e))LivingDanceClient.restore(model);}
}
