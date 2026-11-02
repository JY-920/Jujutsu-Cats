package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.YutaKatanaMotion;
import dev.jujutsuvfx.core.YutaKatanaMotion.Pose;
import dev.jujutsuvfx.item.YutaKatanaItem;
import dev.jujutsuvfx.network.YutaKatanaState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderHandEvent;
import java.util.WeakHashMap;

@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class YutaKatanaHands {
 private static final WeakHashMap<LivingEntity,Motion> MOTION=new WeakHashMap<>();
 private static int nativeDepth;
 private static final class Motion {float start=-1000,lastCharge=-1000,release=-1000;int step=-1;boolean charging;Pose chargePose=YutaKatanaMotion.IDLE;}
 private static Motion motion(LivingEntity a){return MOTION.computeIfAbsent(a,k->new Motion());}
 public static boolean owns(LivingEntity a){return a instanceof AbstractClientPlayer&&!a.isSpectator()&&a.getMainHandItem().getItem() instanceof YutaKatanaItem;}
 public static void install(){YutaKatanaItem.setSwing(a->{});}
 public static void receive(YutaKatanaState packet){} // Vanilla swing packets own normal sword attacks.
 public static boolean ritual(LivingEntity a){
  if(!owns(a))return false;
  Motion m=motion(a);return charge(a,0)>0||m.charging||a.tickCount-m.release<5;
 }
 private static float charge(LivingEntity a,float partial){return a.isUsingItem()&&a.getUseItem().getItem() instanceof YutaKatanaItem?a.getTicksUsingItem()+partial:0;}
 private static Pose sample(LivingEntity a,float partial){
  var m=motion(a);float t=charge(a,partial),now=a.tickCount+partial;
  if(t>0){m.charging=true;m.lastCharge=t;return m.chargePose=YutaKatanaMotion.charge(t);}
  if(m.charging){m.charging=false;m.release=now;}

  if(now-m.release<5)return m.chargePose.blend(YutaKatanaMotion.IDLE,YutaKatanaMotion.smooth((now-m.release)/5));
  return YutaKatanaMotion.IDLE;
 }
 public static boolean pose(LivingEntity a,HumanoidModel<?> model,float age){
  if(nativeDepth>0||!owns(a))return false;
  float partial=Mth.clamp(age-a.tickCount,0,1),t=charge(a,partial);Pose p=sample(a,partial);
  if(!ritual(a))return false;
  MartialClient.save(model);
  apply(model,p,t,t>0||a.getOffhandItem().isEmpty());
  return true;
 }
 static void apply(HumanoidModel<?> model,Pose p,float t,boolean support){
  float w=YutaKatanaMotion.smooth(t/8),wipe=YutaKatanaMotion.wipe(t);
  model.body.yRot=p.torso()*Mth.DEG_TO_RAD;
  model.rightArm.x=-Mth.cos(model.body.yRot)*5;model.rightArm.z=Mth.sin(model.body.yRot)*5;
  model.rightArm.xRot=(-57+p.pitch()*.65f)*Mth.DEG_TO_RAD+model.head.xRot*.28f;
  model.rightArm.yRot=(p.yaw()*.8f)*Mth.DEG_TO_RAD+model.body.yRot;
  model.rightArm.zRot=(p.roll()*.85f)*Mth.DEG_TO_RAD;
  model.rightArm.x=Mth.lerp(w,model.rightArm.x,-6f);model.rightArm.z=Mth.lerp(w,model.rightArm.z,-1f);
  model.rightArm.xRot=Mth.lerp(w,model.rightArm.xRot,-.70f+model.head.xRot*.1f);model.rightArm.zRot*=1-w;
  FlexibleLimbRenderer.bend(model.rightArm,-24-22*w,false);
  if(support){
   float reach=1-YutaKatanaMotion.smooth(wipe/.35f);
   model.leftArm.x=5+(3*reach-3*wipe)*w;model.leftArm.z=-(2.5f+2*reach)*w;
   model.leftArm.xRot=(-35-55*w)*Mth.DEG_TO_RAD;
   model.leftArm.yRot=(12+(-40+75*wipe)*w)*Mth.DEG_TO_RAD;
   model.leftArm.zRot=(16-24*w)*Mth.DEG_TO_RAD;
   FlexibleLimbRenderer.bend(model.leftArm,-45-25*w,false);
  }
  if(model instanceof PlayerModel<?> raw){
   @SuppressWarnings("unchecked") PlayerModel<AbstractClientPlayer> player=(PlayerModel<AbstractClientPlayer>)raw;
   if(w>.001f){
    var right=player.rightArm;float rx=right.xRot,ry=right.yRot,rz=right.zRot;
    var rightShape=CursedFistHands.palmPose(player,new PoseStack(),HumanoidArm.RIGHT);
    aimPalm(right,new org.joml.Vector3f(-.70f+.30f*wipe,.44f,-.38f),rightShape.palm());
    right.xRot=Mth.lerp(w,rx,right.xRot);right.yRot=Mth.lerp(w,ry,right.yRot);right.zRot=Mth.lerp(w,rz,right.zRot);
    PoseStack weapon=new PoseStack();weapon(player,weapon,t,p);
    org.joml.Vector3f goal=weapon.last().pose().transformPosition(new org.joml.Vector3f(.025f,.98f-.84f*wipe,-.025f));
    var arm=player.leftArm;float oldX=arm.xRot,oldY=arm.yRot,oldZ=arm.zRot;
    var handShape=CursedFistHands.palmPose(player,new PoseStack(),HumanoidArm.LEFT);
    aimPalm(arm,goal,handShape.palm());arm.xRot=Mth.lerp(w,oldX,arm.xRot);arm.yRot=Mth.lerp(w,oldY,arm.yRot);arm.zRot=Mth.lerp(w,oldZ,arm.zRot);
   }
   player.rightSleeve.copyFrom(player.rightArm);player.leftSleeve.copyFrom(player.leftArm);player.jacket.copyFrom(player.body);
  }
 }
 public static void thirdPerson(LivingEntity a,HumanoidArm arm,PoseStack p,MultiBufferSource buffers,int light){
  if(!owns(a)||a.isInvisible()||arm!=a.getMainArm())return;
  if(!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer((AbstractClientPlayer)a) instanceof PlayerRenderer r))return;
  float partial=Minecraft.getInstance().getFrameTime();p.pushPose();
  try{
   // Explicit right-hand ritual; use the actual curved palm, never an estimated wrist pivot.
   if(ritual(a))weapon(r.getModel(),p,charge(a,partial),sample(a,partial));
   else vanillaWeapon(r.getModel(),p,arm);
   YutaKatanaRenderer.draw(p,buffers.getBuffer(YutaKatanaRenderer.SOLID),light,OverlayTexture.NO_OVERLAY);
   if(YutaKatanaItem.imbued(a.getMainHandItem()))FistAuraRenderer.drawPlayerBlade(p,a,partial,1,false);
  }finally{p.popPose();}
 }
 static void vanillaWeapon(PlayerModel<AbstractClientPlayer> model,PoseStack p,HumanoidArm arm){
  var limb=arm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
  limb.translateAndRotate(p);p.translate(arm==HumanoidArm.RIGHT?-.0625:.0625,.625,0);
  p.mulPose(Axis.XP.rotationDegrees(-90));p.scale(1.65f,1.65f,1.65f);
 }
 static void weapon(PlayerModel<AbstractClientPlayer> model,PoseStack p,float charge){weapon(model,p,charge,YutaKatanaMotion.charge(charge));}
 static void weapon(PlayerModel<AbstractClientPlayer> model,PoseStack p,float charge,Pose pose){
  org.joml.Matrix4f root=new org.joml.Matrix4f(p.last().pose());
  CursedFistHands.palmPose(model,p,HumanoidArm.RIGHT);
  org.joml.Vector3f grip=new org.joml.Matrix4f(root).invert().mul(p.last().pose()).getTranslation(new org.joml.Vector3f());
  // The imported blade points +Y. Author its wrist orientation separately from the elbow:
  // model X/Y are inverted relative to the first-person view, while Z points the same way.
  org.joml.Quaternionf rotation=new org.joml.Quaternionf().rotationZ(Mth.PI)
    .rotateY(pose.yaw()*Mth.DEG_TO_RAD).rotateX(pose.pitch()*Mth.DEG_TO_RAD).rotateZ(pose.roll()*Mth.DEG_TO_RAD);
  p.last().pose().set(root).translate(grip).rotate(rotation);
  p.last().normal().set(p.last().pose()).invert().transpose();
  p.translate(0,.08,0);p.scale(1.5f,1.5f,1.5f);
 }
 /** Solve the continuous curved skin to the actual blade contact, instead of guessing shoulder angles. */
 private static void aimPalm(net.minecraft.client.model.geom.ModelPart arm,org.joml.Vector3f goal,org.joml.Vector3f palm){
  org.joml.Vector3f delta=new org.joml.Vector3f(goal).sub(arm.x/16,arm.y/16,arm.z/16);
  float distance=delta.length(),lo=0,hi=130;
  for(int i=0;i<12;i++){
   float degrees=(lo+hi)*.5f;FlexibleLimbRenderer.bend(arm,-degrees,false);
   if(FlexibleLimbRenderer.bent(arm,palm).length()>distance)lo=degrees;else hi=degrees;
  }
  FlexibleLimbRenderer.bend(arm,-(lo+hi)*.5f,false);
  org.joml.Vector3f local=FlexibleLimbRenderer.bent(arm,palm);
  org.joml.Quaternionf base=new org.joml.Quaternionf().rotationX(-Mth.HALF_PI);
  var q=new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(local).rotate(base).normalize(),delta.normalize()).mul(base);
  var matrix=new org.joml.Matrix3f().set(q);arm.xRot=(float)Math.atan2(matrix.m12(),matrix.m22());arm.yRot=(float)Math.asin(Mth.clamp(-matrix.m02(),-1,1));arm.zRot=(float)Math.atan2(matrix.m01(),matrix.m00());
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hands(RenderHandEvent e){
  var mc=Minecraft.getInstance();var a=mc.player;if(a==null||!owns(a)||a.isInvisible()||!ritual(a))return;
  float t=charge(a,e.getPartialTick());
  if(e.getHand()==InteractionHand.OFF_HAND){if(t>0||a.getOffhandItem().isEmpty())e.setCanceled(true);return;}
  if(!(mc.getEntityRenderDispatcher().getRenderer((AbstractClientPlayer)a) instanceof PlayerRenderer renderer))return;
  e.setCanceled(true);Pose pose=sample(a,e.getPartialTick());PoseStack p=e.getPoseStack();
  p.pushPose();nativeDepth++;
  try{
   p.translate(pose.x(),pose.y(),pose.z());p.mulPose(Axis.YP.rotationDegrees(pose.yaw()));p.mulPose(Axis.XP.rotationDegrees(pose.pitch()));p.mulPose(Axis.ZP.rotationDegrees(pose.roll()));
   p.pushPose();p.mulPose(Axis.XP.rotationDegrees(-65));FirstPersonArmSpace.apply(p,HumanoidArm.RIGHT);
   FirstPersonSkin.draw(renderer,p,e.getMultiBufferSource(),e.getPackedLight(),a,HumanoidArm.RIGHT);p.popPose();
   p.translate(0,.08,0);p.scale(1.5f,1.5f,1.5f);
   if(!FirstPersonSkin.attachment(a,YutaKatanaRenderer.TEXTURE,out->YutaKatanaRenderer.draw(p,out,e.getPackedLight(),OverlayTexture.NO_OVERLAY)))
    YutaKatanaRenderer.draw(p,e.getMultiBufferSource().getBuffer(YutaKatanaRenderer.SOLID),e.getPackedLight(),OverlayTexture.NO_OVERLAY);
   if(YutaKatanaItem.imbued(a.getMainHandItem()))FistAuraRenderer.drawPlayerBlade(p,a,e.getPartialTick(),1,true);
  }finally{nativeDepth--;p.popPose();}
  float w=YutaKatanaMotion.smooth(t/8),wipe=YutaKatanaMotion.wipe(t);
  if(w>.001){p.pushPose();nativeDepth++;
   try{
    // Blade points screen-left. The left palm travels continuously from its tip toward the right grip.
    p.translate(Mth.lerp(w,-.46f,-.97f+.96f*wipe),Mth.lerp(w,-.62f,-.20f),Mth.lerp(w,-.68f,-1.05f));
    p.mulPose(Axis.XP.rotationDegrees(-65));p.mulPose(Axis.ZP.rotationDegrees(-28));FirstPersonArmSpace.apply(p,HumanoidArm.LEFT);
    FirstPersonSkin.draw(renderer,p,e.getMultiBufferSource(),e.getPackedLight(),a,HumanoidArm.LEFT);
   }finally{nativeDepth--;p.popPose();}
  }
 }
}
