package dev.jujutsuvfx.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Exercises the imported meshes, so detached shoulders and floating support cannot pass as pose numbers. */
public final class YutaGetoPoseModelTest {
 public static void main(String[] args)throws Exception{
  ResourceManager resources=resources();
  JsonArray frames=new JsonArray();
  var yuta=new YutaCatModel(YutaCatModel.createRoot(),new SorcererCatGeometry(resources,"yuta_cat",null));
  var geto=new YutaCatModel(YutaCatModel.createRoot(),new SorcererCatGeometry(resources,"geto_cat",null));
  yuta.young=geto.young=false;
  yuta.applyAction(23,35);
  if(yuta.bone("head").y>4)throw new AssertionError("Vow must raise Yuta's ring paw and head into a standing silhouette");
  geto.applyAction(104,110);
  PoseStack palmPose=new PoseStack();var castingArm=geto.bone("left_front_leg");
  castingArm.translateAndRotate(palmPose);castingArm.getChild("forearm").translateAndRotate(palmPose);
  float castingHeight=1.501f*1.5f-palmPose.last().pose().transformPosition(new Vector3f(0,5/16f,0)).y*1.5f;
  if(Math.abs(castingHeight-2.2f)>.35f)throw new AssertionError("Geto casting palm must frame the 2.2-block source; height="+castingHeight);
  continuity(yuta,23,70,24,0,.001,"vow into love charge");
  continuity(geto,104,200,105,0,.001,"Geto charge into defeat recoil");
  continuity(geto,105,105,106,0,.001,"Geto recoil into retreat");
  for(int action:new int[]{23,24,104,105,106}){
   var model=action<100?yuta:geto;
   int duration=switch(action){case 23->70;case 24->305;case 104->200;case 105->105;default->100;};
   float[] previous=null;
   for(int tick=0;tick<=duration;tick++){
    model.applyAction(action,tick);var faces=capture(model);float[] now=points(faces);
    double floor=Double.NEGATIVE_INFINITY;
    for(int i=0;i<now.length;i+=3){
     if(!Float.isFinite(now[i]+now[i+1]+now[i+2])||Math.max(Math.abs(now[i]),Math.max(Math.abs(now[i+1]),Math.abs(now[i+2])))>6)
      throw new AssertionError("Unbounded actor "+action+" at "+tick);
     floor=Math.max(floor,now[i+1]);
    }
    if(floor>1.545||floor<1.45)throw new AssertionError("Actor support leaves floor: "+action+" at "+tick+" floor="+floor);
    float[] landmarks=landmarks(model);
    if(previous!=null&&maximumDelta(previous,landmarks)>.28)
     throw new AssertionError("One-tick pose pop: "+action+" at "+tick);
    previous=landmarks;
    if(tick==0||tick==20||tick==40||tick==65||tick==110||tick==118||tick==duration){
     JsonObject frame=new JsonObject();frame.addProperty("actor",action<100?"yuta":"geto");frame.addProperty("action",action);frame.addProperty("age",tick);frame.add("faces",faces);frames.add(frame);
    }
   }
  }
  Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/yuta-geto-finale-poses.json"),new Gson().toJson(frames));
  System.out.println("Yuta/Geto finale: 785 production mesh frames; planted support, bounded motion, continuous phase handoffs");
 }
 private static float[] landmarks(YutaCatModel model){
  float[] values=new float[72];int i=0;
  for(String name:new String[]{"head","body","left_front_leg","right_front_leg","left_hind_leg","right_hind_leg","tail1","tail2"}){
   PoseStack p=new PoseStack();model.combatRoot(p);model.bone(name).translateAndRotate(p);
   for(Vector3f point:new Vector3f[]{new Vector3f(),new Vector3f(0,.4f,0),new Vector3f(0,0,.4f)}){
    p.last().pose().transformPosition(point);values[i++]=point.x;values[i++]=point.y;values[i++]=point.z;
   }
  }
  return values;
 }
 static ResourceManager resources(){
  return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(p,m,a)->{
   if(!m.getName().equals("openAsReader"))throw new UnsupportedOperationException(m.getName());
   ResourceLocation id=(ResourceLocation)a[0];var in=YutaGetoPoseModelTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());
   if(in==null)throw new IOException(id.toString());return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
  });
 }
 private static void continuity(YutaCatModel m,int a,float at,int b,float bt,double limit,String label){
  m.applyAction(a,at);float[] before=points(capture(m));m.applyAction(b,bt);
  double gap=maximumDelta(before,points(capture(m)));
  if(gap>limit)throw new AssertionError(label+" discontinuity="+gap);
 }
 private static JsonArray capture(YutaCatModel model){
  JsonArray faces=new JsonArray();
  for(int material=0;material<2;material++){
   var capture=new CutMeshCapture();model.renderMaterial(new PoseStack(),capture.record(null,true),15728880,0,-1,material);capture.finish();
   if(capture.faces.size()<40)throw new AssertionError("Missing actor material");
   for(var face:capture.faces){
    JsonObject f=new JsonObject();f.addProperty("material",material);JsonArray vertices=new JsonArray();
    for(var v:face.points()){JsonArray xyz=new JsonArray();for(double n:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})xyz.add(n);vertices.add(xyz);}
    f.add("vertices",vertices);faces.add(f);
   }
  }
  return faces;
 }
 private static float[] points(JsonArray faces){
  float[] p=new float[faces.size()*12];int n=0;
  for(var face:faces)for(var v:face.getAsJsonObject().getAsJsonArray("vertices"))for(int j=0;j<3;j++)p[n++]=v.getAsJsonArray().get(j).getAsFloat();
  return p;
 }
 private static double maximumDelta(float[] a,float[] b){
  if(a.length!=b.length)throw new AssertionError("Pose changed mesh topology");double max=0;
  for(int i=0;i<a.length;i+=3)max=Math.max(max,new Vector3f(a[i]-b[i],a[i+1]-b[i+1],a[i+2]-b[i+2]).length());
  return max;
 }
}
