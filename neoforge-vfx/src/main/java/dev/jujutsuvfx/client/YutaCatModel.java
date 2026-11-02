package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.YutaBladeMotion;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import org.joml.*;
import static dev.jujutsuvfx.core.YutaBladeMotion.*;

/** Vanilla cat geometry with whole-body rooted choreography and a jaw-attached weapon. */
final class YutaCatModel extends OcelotModel<YutaCat> implements CatGeometryPose {
 private final SorcererCatGeometry geometry;
 private final YutaCombatPose motion=new YutaCombatPose(),previous=new YutaCombatPose();
 private final CatTailChain tailChain=new CatTailChain();
 private CatMeshBend spine,knees;
 private float shuffleWeight;
 private final ModelPart leftForearm,rightForearm;
 YutaCatModel(ModelPart root){this(root,null);}
 YutaCatModel(ModelPart root,SorcererCatGeometry geometry){super(root);this.geometry=geometry;leftForearm=leftFrontLeg.getChild("forearm");rightForearm=rightFrontLeg.getChild("forearm");}
 public ModelPart bone(String name){return switch(name){
  case "head"->head;case "body"->body;case "left_front_leg"->rightFrontLeg;case "right_front_leg"->leftFrontLeg;
  case "left_hind_leg"->rightHindLeg;case "right_hind_leg"->leftHindLeg;case "tail1"->tail1;case "tail2"->tail2;default->null;};}
 public void attachmentPose(PoseStack p,String name){ModelPart b=bone(name);if(b!=null)b.translateAndRotate(p);}
 public void detailPose(PoseStack poses,String name){if(name.equals("group60"))poses.translate(0,motion.ringSlide,0);}
 public boolean hideBlindfold(){return false;}
 public boolean handUnavailable(int hand){return false;}
 public float elbow(int hand){return motion.elbow(hand);}
 public float palmTwist(int hand){return motion.forearmTwist(hand);}
 public boolean smoothArms(){return motion.danceWeight>.00001f||shuffleWeight>.00001f;}
 public CatMeshBend meshBend(String bone){return switch(bone){case "body"->spine;case "left_front_leg"->armBend(2);case "right_front_leg"->armBend(1);case "left_hind_leg","right_hind_leg"->knees;default->null;};}
 private CatMeshBend armBend(int hand){return smoothArms()?new CatMeshBend(3/16f,1/16f,motion.elbow(hand)*4,false,1,4/16f):null;}
 void dancePalm(PoseStack p){if(young){p.scale(.5f,.5f,.5f);p.translate(0,1.5,0);}rightFrontLeg.translateAndRotate(p);CatMeshBend bend=armBend(2);Vector3f palm=new Vector3f(0,9.4f/16,1/16f);if(bend!=null)bend.position(palm);p.translate(palm.x,palm.y,palm.z);if(bend!=null)p.mulPose(Axis.XP.rotation(bend.angle(9.4f/16)));}
 void renderMaterial(PoseStack p,VertexConsumer b,int light,int overlay,int color,int material){
  if(geometry==null)return;
  p.pushPose();try{combatRoot(p);if(young){p.scale(.5f,.5f,.5f);p.translate(0,1.5,0);}
   geometry.render(p,b,light,overlay,color,material,this,0);
  }finally{p.popPose();}
 }
 static ModelPart createRoot(){
  MeshDefinition mesh=OcelotModel.createBodyMesh(CubeDeformation.NONE);
  for(String name:new String[]{"left_front_leg","right_front_leg"}){
   // Split the original ten-pixel foreleg at its elbow, preserving the cat silhouette and UV strip.
   PartDefinition arm=mesh.getRoot().addOrReplaceChild(name,CubeListBuilder.create().texOffs(40,0).addBox(-1,0,-1,2,5,2),
     PartPose.offset(name.equals("left_front_leg")?1.2f:-1.2f,14.1f,-5));
   arm.addOrReplaceChild("forearm",CubeListBuilder.create(),PartPose.offset(0,5,0))
    .addOrReplaceChild("paw",CubeListBuilder.create().texOffs(40,5).addBox(-1,0,-1,2,5,2),PartPose.ZERO);
  }
  return LayerDefinition.create(mesh,64,32).bakeRoot();
 }
 @Override public void prepareMobModel(YutaCat cat,float swing,float amount,float partial){
  resetBones();super.prepareMobModel(cat,swing,amount,partial);
 }
 private void resetBones(){
  for(ModelPart part:new ModelPart[]{head,body,leftFrontLeg,rightFrontLeg,leftHindLeg,rightHindLeg,tail1,tail2,leftForearm,rightForearm})part.resetPose();
 }
 @Override public void setupAnim(YutaCat cat,float swing,float amount,float age,float yaw,float pitch){
  shuffleWeight=0;float partial=Mth.clamp(age-cat.tickCount,0,1);
  if(cat.bladeAction()==DANCE){swing=amount=yaw=pitch=0;}
  if(cat.danceStyle()!=0){applyDance(cat.danceStyle(),cat.danceAge(partial));return;}
  super.setupAnim(cat,swing,amount,age,yaw,pitch);
  if(cat.copiedSpellActive()&&cat.bladeAction()!=DOMAIN)motion.sampleCopied(cat.copiedSpellKind(),cat.copiedSpellLarge(),cat.copiedSpellAge()+partial);
  else motion.sample(cat.bladeAction(),cat.bladeHand(),cat.bladeAge(partial));
  previous.sample(cat.previousBladeAction(),cat.previousBladeHand(),cat.previousBladeAge());
  motion.blendFrom(previous,cat.bladeBlend(partial));applyPose();
 }
 void applyDance(int style,float age){
  shuffleWeight=style==2?dev.jujutsuvfx.core.ShuffleDanceMotion.weight(age):0;
  resetBones();super.setupAnim(null,0,0,age,0,0);
  if(style==2)motion.sampleShuffle(age);else motion.sample(DANCE,2,dev.jujutsuvfx.server.CatDance.loopAge(age));
  applyPose();
 }
 // Shared locomotion for another imported cat skin, without constructing a fake Yuta entity.
 void applyLocomotion(float swing,float amount,float age,float yaw,float pitch){
  shuffleWeight=0;resetBones();super.setupAnim(null,swing,amount,age,yaw,pitch);motion.sample(IDLE,2,0);applyPose();
 }
 // Used by the model regression checks without constructing a running world.
 void applyAction(int action,float t){shuffleWeight=0;resetBones();super.setupAnim(null,0,0,t,0,0);motion.sample(action,2,t);applyPose();}
 void applyActionBlend(int action,float age,int oldAction,float oldAge,float blend,float gait){
  resetBones();super.setupAnim(null,1,gait,age,0,0);motion.sample(action,2,age);
  previous.sample(oldAction,2,oldAge);motion.blendFrom(previous,blend);applyPose();
 }
 private void applyPose(){
  tailChain.capture(body);
  apply(head,0,false);apply(body,1,false);
  apply(leftFrontLeg,2,true);apply(rightFrontLeg,3,true);apply(leftHindLeg,4,true);apply(rightHindLeg,5,true);
  apply(tail1,6,false);apply(tail2,7,false);
  leftForearm.xRot=motion.elbow(1);rightForearm.xRot=motion.elbow(2);
  leftForearm.getChild("paw").yRot=motion.forearmTwist(1);rightForearm.getChild("paw").yRot=motion.forearmTwist(2);
  spine=motion.danceWeight>.00001f?new CatMeshBend(1,-5/16f,-.62f*motion.danceWeight,true,1-.18f*motion.danceWeight,Float.POSITIVE_INFINITY):shuffleWeight>.00001f?new CatMeshBend(1,-5/16f,-.24f*shuffleWeight,true):null;
  knees=motion.danceWeight>.00001f?new CatMeshBend(0,2/16f,3.4f*motion.danceWeight,false,1+.12f*motion.danceWeight,Float.POSITIVE_INFINITY):shuffleWeight>.00001f?new CatMeshBend(0,2/16f,1.6f*shuffleWeight,false):null;
  if(spine!=null){followSpine(head);followSpine(leftFrontLeg);followSpine(rightFrontLeg);plantBentLeg(leftHindLeg);plantBentLeg(rightHindLeg);}
  tailChain.connect(body,tail1,tail2,new CatTailChain.Anchors(0,8,.5f,0,0,.5f));
  if(spine!=null){float x=tail1.x,y=tail1.y,z=tail1.z;followSpine(tail1);tail2.x+=tail1.x-x;tail2.y+=tail1.y-y;tail2.z+=tail1.z-z;}
  followTorso(leftHindLeg,motion.hipFollow);followTorso(rightHindLeg,motion.hipFollow);
 }
 private void followSpine(ModelPart part){
  PoseStack p=new PoseStack();body.translateAndRotate(p);Matrix4f bodyPose=p.last().pose();
  Vector3f point=new Matrix4f(bodyPose).invert().transformPosition(new Vector3f(part.x/16,part.y/16,part.z/16));
  spine.position(point);bodyPose.transformPosition(point);part.setPos(point.x*16,point.y*16,point.z*16);
 }
 private void plantBentLeg(ModelPart leg){
  Quaternionf rotation=new Quaternionf().rotationZYX(leg.zRot,leg.yRot,leg.xRot);
  float bottom=-Float.MAX_VALUE;
  for(int row=0;row<=12;row++)for(float z:new float[]{1/16f,3/16f})for(float x:new float[]{-1/16f,1/16f}){
   Vector3f p=knees.position(new Vector3f(x,row/32f,z));rotation.transform(p);bottom=java.lang.Math.max(bottom,p.y*16);
  }
  Vector3f center=knees.position(new Vector3f(0,6/16f,2/16f));rotation.transform(center);
  float w=java.lang.Math.max(motion.danceWeight,shuffleWeight);leg.y+=(6-bottom)*w;leg.z+=(2-center.z*16)*w;
 }
 private void apply(ModelPart part,int bone,boolean replace){
  float w=motion.value(bone,6);
  if(replace){part.xRot=Mth.lerp(w,part.xRot,motion.value(bone,0));part.yRot=Mth.lerp(w,part.yRot,motion.value(bone,1));part.zRot=Mth.lerp(w,part.zRot,motion.value(bone,2));}
  else{part.xRot+=motion.value(bone,0);part.yRot+=motion.value(bone,1);part.zRot+=motion.value(bone,2);}
  part.x+=motion.value(bone,3);part.y+=motion.value(bone,4);part.z+=motion.value(bone,5);
 }
 private void followTorso(ModelPart leg,float amount){
  if(amount<=0)return;float oldX=leg.x,oldY=leg.y,oldZ=leg.z;
  float pitch=motion.value(1,0),yaw=motion.value(1,1),roll=motion.value(1,2);
  float x=leg.x-body.x+motion.value(1,3),y=leg.y-body.y+motion.value(1,4),z=leg.z-body.z+motion.value(1,5);
  float py=y*Mth.cos(pitch)-z*Mth.sin(pitch),pz=y*Mth.sin(pitch)+z*Mth.cos(pitch);
  float px=x*Mth.cos(yaw)+pz*Mth.sin(yaw);pz=-x*Mth.sin(yaw)+pz*Mth.cos(yaw);
  leg.x=Mth.lerp(amount,oldX,body.x+px*Mth.cos(roll)-py*Mth.sin(roll));
  leg.y=Mth.lerp(amount,oldY,body.y+px*Mth.sin(roll)+py*Mth.cos(roll));leg.z=Mth.lerp(amount,oldZ,body.z+pz);
  leg.xRot+=pitch*amount;leg.yRot+=yaw*amount;leg.zRot+=roll*amount;
 }
 void combatRoot(PoseStack p){
  p.translate(0,15/16f+motion.crouch,0);
  p.mulPose(Axis.YP.rotation(motion.yaw()));p.mulPose(Axis.ZP.rotation(motion.roll()));p.mulPose(Axis.XP.rotation(motion.pitch()));
  p.translate(0,-15/16f,0);
 }
 @Override public void renderToBuffer(PoseStack p,VertexConsumer b,int light,int overlay,int color){
  if(geometry!=null){renderMaterial(p,b,light,overlay,color,0);return;}
  p.pushPose();combatRoot(p);super.renderToBuffer(p,b,light,overlay,color);p.popPose();
 }
 private Matrix4f back(){
  PoseStack p=new PoseStack();if(young){p.scale(.5f,.5f,.5f);p.translate(0,1.5,0);}body.translateAndRotate(p);
  Vector3f mount=new Vector3f(.11f,young?.96f:.68f,-.08f);float bend=spine==null?0:spine.angle(mount.y);
  if(spine!=null)spine.position(mount);p.translate(mount.x,mount.y,mount.z);p.mulPose(Axis.XP.rotation(bend));p.mulPose(Axis.ZP.rotationDegrees(12));return new Matrix4f(p.last().pose());
 }
 private Matrix4f mouth(){
  PoseStack p=new PoseStack();if(young){p.scale(.5f,.5f,.5f);p.translate(0,1.5,0);}head.translateAndRotate(p);p.translate(0,.085,-.23);p.mulPose(Axis.ZP.rotationDegrees(90));return new Matrix4f(p.last().pose());
 }
 Matrix4f weaponPose(YutaCat cat,float partial){
  return weaponPose(cat.bladeAction(),cat.bladeAge(partial),cat.bladeDrawn());
 }
 Matrix4f weaponPose(int action,float age,boolean drawn){
  if(action==VOW||action==LOVE_FIRE)return back();
  if((action==DOMAIN||action==DANCE||action==RIKA_SUMMON)&&drawn){
   float t=action==DANCE?dev.jujutsuvfx.core.YutaDanceMotion.weight(age):phase(age,0,8)*(1-phase(age,action==RIKA_SUMMON?28:32,action==RIKA_SUMMON?36:40));Matrix4f a=mouth(),b=back();
   Vector3f position=a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()),t);
   Quaternionf rotation=a.getUnnormalizedRotation(new Quaternionf()).slerp(b.getUnnormalizedRotation(new Quaternionf()),t);
   return new Matrix4f().translation(position).rotate(rotation).scale(a.getScale(new Vector3f()).lerp(b.getScale(new Vector3f()),t));
  }
  if(action!=DRAW)return drawn?mouth():back();
  if(age>=CATCH)return mouth();
  if(age<=THROW)return back();
  float t=toss(age),s=smooth(t);Matrix4f a=back(),b=mouth();
  Vector3f position=a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()),s);
  position.y-=.92f*airborne(age);
  Quaternionf rotation=a.getUnnormalizedRotation(new Quaternionf()).slerp(b.getUnnormalizedRotation(new Quaternionf()),s);
  return new Matrix4f().translation(position).rotate(rotation).scale(a.getScale(new Vector3f()).lerp(b.getScale(new Vector3f()),s)).translate(0,.38f,0).rotateZ(spin(age)).translate(0,-.38f,0);
 }
}
