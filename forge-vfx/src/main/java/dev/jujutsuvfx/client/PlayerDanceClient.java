package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.YutaDanceMotion;
import dev.jujutsuvfx.item.DanceItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import org.joml.Vector3f;

/** The supplied boxing bounce adapted to a human's hips, spine, elbows and knees. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class PlayerDanceClient {
 private static boolean handPass;
 private static int nativeHands;
 public static void beginNativeHand(){nativeHands++;}
 public static void endNativeHand(){nativeHands=Math.max(0,nativeHands-1);}
 private static SorcererCatGeometry firstPersonRika;
 private static final ResourceLocation RIKA=new ResourceLocation(JujutsuVfx.ID,"textures/entity/rika_dance.png");
 private PlayerDanceClient(){}
 public static boolean active(LivingEntity actor){return DanceItem.active(actor);}
 private static float age(Player p,float partial){return Math.max(0,(float)(p.level().getGameTime()-DanceItem.started(p))+partial);}
 private static float weight(float age){float t=Mth.clamp(age/10,0,1);return t*t*(3-2*t);}
 public static boolean pose(LivingEntity actor,HumanoidModel<?> model,float ageInTicks){
  if(!active(actor)||!(actor instanceof Player player))return false;
  if(handPass)return true; // Native first-person arms use wrist space, never the third-person folded rig.
  if(nativeHands>0)return true;
  float time=age(player,Mth.clamp(ageInTicks-actor.tickCount,0,1)),w=weight(time);
  if(w<=0)return true;
  MartialClient.save(model);if(DanceItem.style(player)==2)applyShuffle(model,time,w);else apply(model,time,w);return true;
 }
 static void applyShuffle(HumanoidModel<?> m,float time,float w){
  float[] k=dev.jujutsuvfx.core.ShuffleDanceMotion.sample(time);
  float hop=k[0]*16,side=dev.jujutsuvfx.core.ShuffleDanceMotion.LATERAL_TRAVEL*16*k[2],hipY=12.9f-hop+.5f*(1-k[0]/dev.jujutsuvfx.core.ShuffleDanceMotion.HOP_HEIGHT),hipZ=.9f;
  float tilt=.18f+.025f*k[3],lean=-dev.jujutsuvfx.core.ShuffleDanceMotion.LEAN*k[2];
  place(m.body,side+12*Mth.cos(tilt)*Mth.sin(lean),hipY-12*Mth.cos(tilt)*Mth.cos(lean),hipZ-12*Mth.sin(tilt),tilt,.025f*k[1],lean,w);
  PoseStack torso=new PoseStack();m.body.translateAndRotate(torso);
  Vector3f neck=torso.last().pose().transformPosition(new Vector3f(0,0,0)).mul(16);
  place(m.head,neck.x,neck.y,neck.z,.34f,.025f*k[2],lean*.65f,w);m.hat.copyFrom(m.head);
  for(int i=0;i<2;i++){
   float sign=i==0?-1:1,lift=dev.jujutsuvfx.core.ShuffleDanceMotion.lift(k[1],sign);
   ModelPart arm=i==0?m.rightArm:m.leftArm,leg=i==0?m.rightLeg:m.leftLeg;
   Vector3f shoulder=torso.last().pose().transformPosition(new Vector3f(sign*5/16,2/16f,0)).mul(16);
   place(arm,shoulder.x,shoulder.y,shoulder.z,.04f+sign*.34f*k[1],0,lean-sign*.08f,w);FlexibleLimbRenderer.bend(arm,(-18-8*lift)*w,false);
   place(leg,sign*2.2f+side,hipY,hipZ+sign*.8f*k[1],-.12f-.65f*lift,sign*.08f*k[1],sign*.025f,w);FlexibleLimbRenderer.bend(leg,(16+48*lift)*w,true);
   // Both feet clear the ground at the exchange; only the supporting foot lands at the trough.
   float bottom=-Float.MAX_VALUE;PoseStack foot=new PoseStack();leg.translateAndRotate(foot);
   for(int row=0;row<=24;row++)for(float z:new float[]{-2/16f,2/16f})for(float x:new float[]{-2/16f,2/16f}){
    Vector3f point=FlexibleLimbRenderer.bent(leg,new Vector3f(x,row/32f,z));foot.last().pose().transformPosition(point);bottom=Math.max(bottom,point.y*16);
   }
   leg.y+=(24-hop-4*lift-bottom)*w;
  }
  if(m instanceof PlayerModel<?> p){p.rightSleeve.copyFrom(p.rightArm);p.leftSleeve.copyFrom(p.leftArm);p.rightPants.copyFrom(p.rightLeg);p.leftPants.copyFrom(p.leftLeg);p.jacket.copyFrom(p.body);}
 }
 static void apply(HumanoidModel<?> m,float time,float w){
  float step=YutaDanceMotion.step(time),bounce=YutaDanceMotion.bounce(time),open=YutaDanceMotion.open(time);
  float hipY=14.5f-.8f*bounce,hipZ=2.5f,side=.20f*step;
  float torso=(float)Math.toRadians(32),curve=-.28f*w;
  CatMeshBend spine=new CatMeshBend(.75f,0,curve,true);
  place(m.body,side,hipY-12*Mth.cos(torso),hipZ-12*Mth.sin(torso),torso,.025f*step,-.025f*step,w);
  FlexibleLimbRenderer.torso(m.body,spine);
  PoseStack body=new PoseStack();m.body.translateAndRotate(body);
  Vector3f neck=body.last().pose().transformPosition(spine.position(new Vector3f(0,0,0))).mul(16);
  place(m.head,neck.x,neck.y,neck.z,.08f+m.head.xRot*.1f,m.head.yRot*.12f,.02f*step,w);
  m.hat.copyFrom(m.head);
  for(int i=0;i<2;i++){
   float sign=i==0?-1:1;
   ModelPart arm=i==0?m.rightArm:m.leftArm,leg=i==0?m.rightLeg:m.leftLeg;
   Vector3f shoulder=body.last().pose().transformPosition(spine.position(new Vector3f(sign*5/16,2/16f,0))).mul(16);
   place(arm,shoulder.x,shoulder.y,shoulder.z,-.25f+.035f*bounce,-sign*.12f,-sign*(.10f+.24f*open),w);
   FlexibleLimbRenderer.bend(arm,(-112+3*bounce)*w,false);
   float lift=i==1?3.2f+.4f*bounce:0;
   float pitch=i==1?-.40f+.08f*bounce:-1.02f+.08f*bounce;
   place(leg,sign*2.65f+side,hipY-lift,hipZ+(i==1?2.0f:-1.3f),pitch,0,sign*.06f,w);
   FlexibleLimbRenderer.bend(leg,103*w,true);
   // Model deformation alone supplies the hop: the actual player does not levitate.
   float bottom=-Float.MAX_VALUE;
   PoseStack legPose=new PoseStack();leg.translateAndRotate(legPose);
   for(int row=0;row<=24;row++)for(float z:new float[]{-2/16f,2/16f})for(float x:new float[]{-2/16f,2/16f}){
    Vector3f point=FlexibleLimbRenderer.bent(leg,new Vector3f(x,row/32f,z));legPose.last().pose().transformPosition(point);
    bottom=Math.max(bottom,point.y*16);
   }
   leg.y+=(24-lift-bottom)*w;
  }
  if(m instanceof PlayerModel<?> p){p.rightSleeve.copyFrom(p.rightArm);p.leftSleeve.copyFrom(p.leftArm);p.rightPants.copyFrom(p.rightLeg);p.leftPants.copyFrom(p.leftLeg);p.jacket.copyFrom(p.body);}
 }
 private static void place(ModelPart part,float x,float y,float z,float pitch,float yaw,float roll,float w){
  part.x=Mth.lerp(w,part.x,x);part.y=Mth.lerp(w,part.y,y);part.z=Mth.lerp(w,part.z,z);
  part.xRot=Mth.lerp(w,part.xRot,pitch);part.yRot=Mth.lerp(w,part.yRot,yaw);part.zRot=Mth.lerp(w,part.zRot,roll);
 }
 public static void addLayers(EntityRenderersEvent.AddLayers event){
  for(var skin:event.getSkins())if(event.getSkin(skin) instanceof PlayerRenderer renderer)renderer.addLayer(new Companion(renderer));
 }
 private static void rika(PoseStack p,MultiBufferSource buffers,int light,float time,SorcererCatGeometry geometry,float parentScale){
  float w=weight(time);if(w<=0)return;p.pushPose();
  try{RikaDanceLayer.placement(p,parentScale);geometry.render(p,buffers.getBuffer(RenderType.entityTranslucent(RIKA)),light,OverlayTexture.NO_OVERLAY,((int)(255*w)<<24)|0xffffff,0,null,YutaDanceMotion.progress(time));}
  finally{p.popPose();}
 }
 private static final class Companion extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>>{
  final SorcererCatGeometry geometry;
  Companion(PlayerRenderer renderer){super(renderer);geometry=new SorcererCatGeometry(Minecraft.getInstance().getResourceManager(),"rika_dance","animation.rika.dance");}
  @Override public void render(PoseStack p,MultiBufferSource buffers,int light,AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch){
   if(active(player)&&!player.isInvisible()){
    if(DanceItem.style(player)==2){CatDanceLayer.copies(p,age(player,partial),color->{getParentModel().renderToBuffer(p,buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation())),light,OverlayTexture.NO_OVERLAY,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f);});return;}
    rika(p,buffers,light,age(player,partial),geometry,.9375f);
    p.pushPose();try{CursedFistHands.palmPose(getParentModel(),p,HumanoidArm.RIGHT);FistAuraRenderer.drawDancePalm(p,player,partial,weight(age(player,partial)));}finally{p.popPose();}
   }
  }
 }
 @SubscribeEvent public static void localCompanion(RenderLevelStageEvent event){
  Minecraft mc=Minecraft.getInstance();
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||mc.player==null||!active(mc.player)||!mc.options.getCameraType().isFirstPerson()||mc.player.isInvisible())return;
  if(DanceItem.style(mc.player)==2)return;
  if(firstPersonRika==null)firstPersonRika=new SorcererCatGeometry(mc.getResourceManager(),"rika_dance","animation.rika.dance");
  float partial=event.getPartialTick();Vec3 origin=mc.player.getPosition(partial).subtract(event.getCamera().getPosition());
  PoseStack p=event.getPoseStack();p.pushPose();
  try{p.translate(origin.x,origin.y,origin.z);p.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,mc.player.yBodyRotO,mc.player.yBodyRot)));p.scale(-1,-1,1);p.translate(0,-1.501,0);
   rika(p,mc.renderBuffers().bufferSource(),mc.getEntityRenderDispatcher().getPackedLightCoords(mc.player,partial),age(mc.player,partial),firstPersonRika,1);
  }finally{p.popPose();}
 }
 @SubscribeEvent(priority=EventPriority.HIGH) public static void hands(RenderHandEvent event){
  Minecraft mc=Minecraft.getInstance();if(mc.player==null||!active(mc.player)||mc.player.isInvisible())return;
  InteractionHand held=mc.player.getMainHandItem().getItem() instanceof DanceItem?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
  if(event.getHand()!=held){if(event.getItemStack().isEmpty()||event.getItemStack().getItem() instanceof DanceItem)event.setCanceled(true);return;}
  if(!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer))return;
  event.setCanceled(true);float time=age(mc.player,event.getPartialTick()),bob=YutaDanceMotion.bounce(time),sway=YutaDanceMotion.step(time);handPass=true;
  HumanoidArm primary=held==InteractionHand.MAIN_HAND?mc.player.getMainArm():mc.player.getMainArm().getOpposite();
  boolean support=mc.player.getItemInHand(held==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND).isEmpty();
  try{for(HumanoidArm arm:HumanoidArm.values()){
   if(arm!=primary&&!support)continue;
   float sign=arm==HumanoidArm.RIGHT?1:-1;PoseStack p=event.getPoseStack();p.pushPose();
   try{float open=YutaDanceMotion.open(time);
    if(DanceItem.style(mc.player)==2){float[] k=dev.jujutsuvfx.core.ShuffleDanceMotion.sample(time);p.translate(sign*.12,-.45+k[0],.10+sign*.14*k[1]);p.mulPose(Axis.ZP.rotation(-.12f*k[2]));p.mulPose(Axis.XP.rotationDegrees(sign*16*k[1]));}
    p.translate(sign*(.38+.06*open)+.012*sway,-.30+.025*bob,-.78);
    p.mulPose(Axis.YP.rotationDegrees(sign*10));p.mulPose(Axis.XP.rotationDegrees(-72));p.mulPose(Axis.ZP.rotationDegrees(sign*(10+8*open)));
    FirstPersonArmSpace.apply(p,arm);
    FirstPersonSkin.draw(renderer,p,event.getMultiBufferSource(),event.getPackedLight(),mc.player,arm);
    if(arm==HumanoidArm.RIGHT&&DanceItem.style(mc.player)==1){CursedFistHands.palmPose(renderer.getModel(),p,arm);FistAuraRenderer.drawDancePalm(p,mc.player,event.getPartialTick(),weight(time),true);}
   }finally{p.popPose();}
  }}finally{handPass=false;}
 }
}
