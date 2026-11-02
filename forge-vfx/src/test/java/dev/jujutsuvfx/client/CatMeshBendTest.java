package dev.jujutsuvfx.client;
import org.joml.Vector3f;

public final class CatMeshBendTest {
 public static void main(String[] args){
  CatMeshBend spine=new CatMeshBend(1,-5/16f,-1.35f,true);
  CatMeshBend knee=new CatMeshBend(0,2/16f,5.866667f,false);
  Vector3f rest=new Vector3f(.1f,.4f,.2f);
  if(new CatMeshBend(1,0,0,true).position(new Vector3f(rest)).distance(rest)>1e-7)throw new AssertionError("Rest mesh changed");
  if(spine.position(new Vector3f(0,1.2f,-.4f)).distance(new Vector3f(0,1.2f,-.4f))>1e-7)throw new AssertionError("Hip must stay attached");
  for(CatMeshBend bend:new CatMeshBend[]{spine,knee}){
   for(float y=bend==spine?.1f:.02f;y<(bend==spine?.98f:.375f);y+=.01f){
    float step=.0001f;
    Vector3f p=new Vector3f(.1f,y,bend.pivotZ());
    Vector3f q=bend.position(new Vector3f(p));
    float speed=bend.position(new Vector3f(p).add(0,step,0)).distance(q)/step;
    if(Math.abs(speed-1)>.004)throw new AssertionError("Spine centerline stretched: "+speed);
    for(float z:bend==spine?new float[]{-.5f,-.125f}:new float[]{.0625f,.1875f}){
     p.set(.1f,y,z);q=bend.position(new Vector3f(p));
     Vector3f tangent=bend.position(new Vector3f(p).add(0,step,0)).sub(q).normalize();
     Vector3f normal=bend.normal(new Vector3f(0,0,1),y,z);
     if(Math.abs(normal.dot(tangent))>.005||Math.abs(normal.length()-1)>.0001)throw new AssertionError("Bent surface lighting has incorrect normal");
    }
   }
  }
  var model=new YutaCatModel(YutaCatModel.createRoot());
  for(int action:new int[]{0,2,3,14,15,17}){
   model.applyAction(action,10);
   if(model.meshBend("body")!=null||model.meshBend("left_hind_leg")!=null)throw new AssertionError("Dance deforms ordinary combat");
  }
  model.applyAction(18,20);
  if(model.meshBend("body")==null||model.meshBend("right_hind_leg")==null)throw new AssertionError("Missing curved dance skin");
  if(Math.abs(model.bone("body").xRot-(float)Math.toRadians(40))>.001f)throw new AssertionError("Compact forward dance stance");
  if(model.meshBend("head")!=null)throw new AssertionError("Face must retain its shape");
  if(!model.smoothArms()||model.meshBend("right_front_leg")==null)throw new AssertionError("Dance elbow must be one continuous surface");
  CatMeshBend elbow=new CatMeshBend(3/16f,1/16f,-8,false,1,4/16f);
  for(float y:new float[]{3/16f,7/16f}){
   Vector3f lo=elbow.position(new Vector3f(0,y-.00001f,1/16f)),hi=elbow.position(new Vector3f(0,y+.00001f,1/16f));
   if(lo.distance(hi)>.00003f)throw new AssertionError("Elbow skin tears at arc boundary");
  }
  float minHip=100,maxHip=-100;
  float maxLift=0,minOpen=10,maxOpen=-10;boolean leftUp=false,rightUp=false;
  for(int f=0;f<=88;f++){
   model.applyAction(18,10+f*20f/30);
   minHip=Math.min(minHip,model.bone("left_hind_leg").x);maxHip=Math.max(maxHip,model.bone("left_hind_leg").x);
   float[] bottoms=new float[2];int i=0;
   for(String name:new String[]{"left_hind_leg","right_hind_leg"}){
    var part=model.bone(name);var ps=new com.mojang.blaze3d.vertex.PoseStack();part.translateAndRotate(ps);float bottom=-100;
    for(int row=0;row<=12;row++)for(float z:new float[]{1/16f,3/16f})for(float x:new float[]{-1/16f,1/16f}){
     Vector3f v=model.meshBend(name).position(new Vector3f(x,row/32f,z));ps.last().pose().transformPosition(v);bottom=Math.max(bottom,v.y*16);
    }bottoms[i++]=bottom;
   }
   if(Math.abs(Math.max(bottoms[0],bottoms[1])-24)>.01)throw new AssertionError("No planted supporting foot");
   maxLift=Math.max(maxLift,Math.abs(bottoms[0]-bottoms[1]));leftUp|=bottoms[0]<bottoms[1]-1.3f;rightUp|=bottoms[1]<bottoms[0]-1.3f;
   if(Math.abs(model.bone("left_front_leg").zRot+model.bone("right_front_leg").zRot)>.001f)throw new AssertionError("Elbows must open together");
   float roll=model.bone("left_front_leg").zRot;minOpen=Math.min(minOpen,roll);maxOpen=Math.max(maxOpen,roll);
  }
  if(maxHip-minHip<.35f)throw new AssertionError("Leg roots do not follow torso sway");
  if(maxLift<1.4f||leftUp||!rightUp)throw new AssertionError("Right support / raised left foot must never swap");
  if(maxOpen-minOpen<.30f)throw new AssertionError("Elbows must visibly open and return");
  model.applyActionBlend(0,10,18,20,1,.8f);
  var leg=model.bone("left_hind_leg");Vector3f exit=new Vector3f(leg.x,leg.y,leg.z);
  model.applyActionBlend(0,10,18,20,.9999f,.8f);
  leg=model.bone("left_hind_leg");
  if(exit.distance(new Vector3f(leg.x,leg.y,leg.z))>.005)throw new AssertionError("Interrupted dance planting snaps during walking");
  System.out.println("Curved dance skin: arc length, lighting Jacobian, anchored hip, rigid face and combat isolation passed");
 }
}
