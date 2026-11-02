package dev.jujutsuvfx.client;
import dev.jujutsuvfx.core.YutaBladeMotion;
import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/** Exercises actual model attachment matrices, including the catch packet boundary. */
public final class YutaModelGeometryTest {
 public static void main(String[] args){
  slamAttachments();
  for(boolean baby:new boolean[]{false,true}){
   var root=YutaCatModel.createRoot();
   YutaCatModel m=new YutaCatModel(root);m.young=baby;
   Matrix4f back=m.weaponPose(0,0,false),mouth=m.weaponPose(0,0,true);
   try(var stream=YutaModelGeometryTest.class.getResourceAsStream("/assets/jujutsuvfx/meshes/yuta_katana.json")){
    if(stream==null)throw new AssertionError("katana geometry missing");
    var faces=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
    float minZ=Float.POSITIVE_INFINITY;
    for(var face:faces)for(var entry:face.getAsJsonArray()){
     var v=entry.getAsJsonArray();Vector3f point=back.transformPosition(new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()));
     minZ=Math.min(minZ,point.z);
    }
    float headZ=baby?(-9/16f+4/16f)*.75f:-9/16f;
    float radius=(float)Math.sqrt(2.5f*2.5f+3*3+4*4)/16*(baby?.75f:1);
    if(minZ<=headZ+radius+.015f)throw new AssertionError("back sword overlaps rotating head: baby="+baby+", minimumZ="+minZ);
   }catch(java.io.IOException error){throw new AssertionError(error);}
   near(back,m.weaponPose(2,12,false),1e-5f,"throw attaches to back");
   near(mouth,m.weaponPose(2,34,false),1e-5f,"catch stays in mouth before DRAWN metadata");
   near(mouth,m.weaponPose(2,33.999f,false),.001f,"continuous catch");
   for(float age=12;age<=34;age+=.125f){
    Matrix4f matrix=m.weaponPose(2,age,false);float[] values=matrix.get(new float[16]);
    for(float value:values)if(!Float.isFinite(value))throw new AssertionError("finite attachment");
    if(matrix.determinant()<=0)throw new AssertionError("no inverted or collapsed sword");
   }
   // Exercise the actual render transforms at 400 FPS: a pose cannot jump at spin-wrap,
   // catch/dash boundaries or the transition back to idle.
   java.util.List<Integer> actions=new java.util.ArrayList<>();
   for(int a=2;a<=18;a++)actions.add(a);
   for(int i=0;i<8;i++)actions.add(YutaBladeMotion.chooseBrawl(i));
   for(int action:actions){
    Matrix4f last=null;Matrix4f[] paws=new Matrix4f[2];float[] upperAngles=new float[2],elbowAngles=new float[2];
    for(int frame=0;frame<=YutaBladeMotion.duration(action)*20;frame++){
     float t=frame/20f;m.applyAction(action,t);
     var stack=new com.mojang.blaze3d.vertex.PoseStack();m.combatRoot(stack);
     Matrix4f matrix=new Matrix4f(stack.last().pose()).mul(m.weaponPose(action,t,action!=2&&action<32));
     for(float value:matrix.get(new float[16]))if(!Float.isFinite(value))throw new AssertionError("nonfinite combat matrix");
     if(last!=null)near(last,matrix,.28f,"continuous action "+action+" at "+t);
     last=matrix;
     String[] names={"left_front_leg","right_front_leg"};
     for(int hand=0;hand<2;hand++){
      var pawStack=new com.mojang.blaze3d.vertex.PoseStack();m.combatRoot(pawStack);
      var arm=root.getChild(names[hand]);
      if(frame>0&&(Math.abs(arm.xRot-upperAngles[hand])>.18f||Math.abs(arm.getChild("forearm").xRot-elbowAngles[hand])>.18f))
       throw new AssertionError("joint angle discontinuity: action="+action+", time="+t+", hand="+hand);
      upperAngles[hand]=arm.xRot;elbowAngles[hand]=arm.getChild("forearm").xRot;
      arm.translateAndRotate(pawStack);arm.getChild("forearm").translateAndRotate(pawStack);
      Matrix4f paw=new Matrix4f(pawStack.last().pose());
      if(paws[hand]!=null)near(paws[hand],paw,.28f,"continuous elbow "+hand+" action "+action+" at "+t);
      paws[hand]=paw;
     }
    }
   }
   m.applyAction(YutaBladeMotion.DOMAIN,20);
   Vector3f leftSeal=pawTip(root,"left_front_leg"),rightSeal=pawTip(root,"right_front_leg");
   if(rightSeal.z>=leftSeal.z-.20f)throw new AssertionError("Yuta domain must extend right paw ahead of the folded left paw");
   if(leftSeal.y>=rightSeal.y-.08f)throw new AssertionError("Yuta left seal stays higher against chest");
   if(root.getChild("right_front_leg").getChild("forearm").getChild("paw").yRot<2.8f)throw new AssertionError("Yuta right paw turns palm-up");
   m.applyAction(0,0);
   if(!baby&&back.getTranslation(new Vector3f()).y>=.875f)throw new AssertionError("sword buried in back");
  }
  System.out.println("Yuta model: actual vanilla bones, head clearance for entire sword, clear back mount, continuous throw/catch, baby head/body scaling passed");
 }
 /** All leg roots are siblings in the imported cat rig; airborne root lift must carry them together. */
 private static void slamAttachments(){
  String[] legs={"left_front_leg","right_front_leg","left_hind_leg","right_hind_leg"};
  var model=new YutaCatModel(YutaCatModel.createRoot());model.young=false;
  model.applyAction(dev.jujutsuvfx.core.YutaAreaMotion.SLAM,0);
  Vector3f[] anchors=new Vector3f[legs.length];
  for(int i=0;i<legs.length;i++)anchors[i]=bodyLocalJoint(model,legs[i]);
  for(int frame=0;frame<=128;frame++){
   float age=frame*.25f;model.applyAction(dev.jujutsuvfx.core.YutaAreaMotion.SLAM,age);
   for(int i=0;i<legs.length;i++){
    float separation=bodyLocalJoint(model,legs[i]).distance(anchors[i]);
    if(separation>.0001f)throw new AssertionError("SLAM detaches "+legs[i]+" from torso at "+age+": "+separation+" blocks");
   }
  }
  model.applyAction(dev.jujutsuvfx.core.YutaAreaMotion.SLAM,10);
  var lifted=new com.mojang.blaze3d.vertex.PoseStack();model.combatRoot(lifted);
  if(lifted.last().pose().transformPosition(new Vector3f(0,15/16f,0)).y>.3f)
   throw new AssertionError("SLAM no longer visibly lifts the attached full skeleton");
 }
 private static Vector3f bodyLocalJoint(YutaCatModel model,String name){
  var torso=new com.mojang.blaze3d.vertex.PoseStack();model.combatRoot(torso);model.bone("body").translateAndRotate(torso);
  var leg=new com.mojang.blaze3d.vertex.PoseStack();model.combatRoot(leg);model.bone(name).translateAndRotate(leg);
  return new Matrix4f(torso.last().pose()).invert().transformPosition(leg.last().pose().transformPosition(new Vector3f()));
 }
 private static Vector3f pawTip(net.minecraft.client.model.geom.ModelPart root,String name){
  var p=new com.mojang.blaze3d.vertex.PoseStack();var arm=root.getChild(name);
  arm.translateAndRotate(p);arm.getChild("forearm").translateAndRotate(p);
  return p.last().pose().transformPosition(new Vector3f(0,5/16f,0));
 }
 private static void near(Matrix4f a,Matrix4f b,float epsilon,String message){
  float[] x=a.get(new float[16]),y=b.get(new float[16]);for(int i=0;i<16;i++)if(Math.abs(x[i]-y[i])>epsilon)throw new AssertionError(message+" at "+i);
 }
}
