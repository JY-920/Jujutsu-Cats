package dev.jujutsuvfx.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.core.YutaDanceMotion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class YutaDanceModelTest {
 public static void main(String[] args)throws Exception{
  ResourceManager resources=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(p,m,a)->{
   if(!m.getName().equals("openAsReader"))throw new UnsupportedOperationException(m.getName());
   ResourceLocation id=(ResourceLocation)a[0];var in=YutaDanceModelTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());
   if(in==null)throw new IOException(id.toString());return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
  });
  var rika=new SorcererCatGeometry(resources,"rika_dance","animation.rika.dance");
  verifyCloseStagger();
  verifyReferenceClock(resources);
  verifyCompanionScale(rika);
  var model=new YutaCatModel(YutaCatModel.createRoot(),new SorcererCatGeometry(resources,"yuta_cat",null));
  model.young=false;
  JsonArray frames=new JsonArray();boolean escaped=false;String escapedPoint="";
  for(int frame=0;frame<=88;frame++){
   float age=10+frame*20f/30;
   model.applyAction(18,age);JsonArray faces=new JsonArray();
   for(int material=0;material<3;material++){
    var capture=new CutMeshCapture();var p=new PoseStack();
    if(material<2)model.renderMaterial(p,capture.record(null,true),15728880,0,-1,material);
    else{RikaDanceLayer.placement(p);rika.render(p,capture.record(null,true),15728880,0,-1,0,null,YutaDanceMotion.progress(age));}
    capture.finish();
    if(capture.faces.size()<(material==2?306:material==0?120:40))throw new AssertionError("Missing dance geometry");
    for(var f:capture.faces){JsonObject face=new JsonObject();face.addProperty("material",material);JsonArray vertices=new JsonArray();
     for(var v:f.points()){
      if(!Double.isFinite(v.x()+v.y()+v.z())||Math.max(Math.abs(v.x()),Math.max(Math.abs(v.y()),Math.abs(v.z())))>12)throw new AssertionError("Detached dance model");
      if(v.x()<-.7||v.x()>2.9||v.z()<-1.65||v.z()>3.1||v.y()<-1.2||v.y()>1.53){escaped=true;escapedPoint="material="+material+", frame="+frame+", point="+v;}
      JsonArray point=new JsonArray();for(double value:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})point.add(value);vertices.add(point);
     }
     face.add("vertices",vertices);faces.add(face);
    }
   }
   JsonObject f=new JsonObject();f.addProperty("age",age);f.add("faces",faces);frames.add(f);
  }
  Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/yuta-rika-dance.json"),new Gson().toJson(frames));
  // The actual importer, authored hierarchy and texture batches must return to exactly the same pose.
  var first=frames.get(0).getAsJsonObject().getAsJsonArray("faces");var last=frames.get(88).getAsJsonObject().getAsJsonArray("faces");
  if(first.size()!=last.size())throw new AssertionError("Loop topology mismatch: "+first.size()+" / "+last.size());
  double seam=0;
  for(int i=0;i<first.size();i++)for(int j=0;j<4;j++)for(int k=0;k<3;k++){
   double a=first.get(i).getAsJsonObject().getAsJsonArray("vertices").get(j).getAsJsonArray().get(k).getAsDouble();
   double b=last.get(i).getAsJsonObject().getAsJsonArray("vertices").get(j).getAsJsonArray().get(k).getAsDouble();seam=Math.max(seam,Math.abs(a-b));
  }
  if(seam>.0001)throw new AssertionError("Dance loop seam: "+seam);

  if(escaped)throw new AssertionError("Dance escaped startup clearance envelope: "+escapedPoint);
  System.out.println("Yuta/Rika production geometry: 89 frames, 3 materials; loop seam="+seam);
 }
 private static void verifyReferenceClock(ResourceManager resources)throws Exception{
  JsonObject source;
  try(var reader=resources.openAsReader(new ResourceLocation("jujutsuvfx","models/entity/rika_dance.bbmodel"))){source=JsonParser.parseReader(reader).getAsJsonObject();}
  var clip=source.getAsJsonArray("animations").get(0).getAsJsonObject();
  if(Math.abs(clip.get("length").getAsFloat()*20-YutaDanceMotion.LOOP)>.0001)throw new AssertionError("Companion loop clock differs from Yuta");
  JsonObject left=null,right=null;
  for(var entry:clip.getAsJsonObject("animators").entrySet()){
   var animator=entry.getValue().getAsJsonObject();String name=animator.get("name").getAsString();
   if(name.equals("left_front_leg"))left=animator;
   if(name.equals("right_front_leg"))right=animator;
  }
  if(left==null||right==null)throw new AssertionError("Missing synchronized boxing arms");
  var keys=left.getAsJsonArray("keyframes");var mirrored=right.getAsJsonArray("keyframes");
  for(int i=0;i<keys.size();i++){
   var key=keys.get(i).getAsJsonObject();float age=10+key.get("time").getAsFloat()*20;
   var value=key.getAsJsonArray("data_points").get(0).getAsJsonObject();
   var other=mirrored.get(i).getAsJsonObject().getAsJsonArray("data_points").get(0).getAsJsonObject();
   if(Math.abs(value.get("x").getAsFloat()+2.58f*YutaDanceMotion.bounce(age))>.001
       ||Math.abs(value.get("z").getAsFloat()-8f*YutaDanceMotion.open(age))>.001
       ||Math.abs(value.get("z").getAsFloat()+other.get("z").getAsFloat())>.0001)
    throw new AssertionError("Companion fists do not follow Yuta's push/settle and elbow clock at "+age);
  }
 }
 private static void verifyCompanionScale(SorcererCatGeometry rika){
  CutMeshCapture baseline=null;
  for(float parentScale:new float[]{1.5f,.9375f,1}){
   var p=new PoseStack();p.scale(parentScale,parentScale,parentScale);p.translate(0,-1.501,0);RikaDanceLayer.placement(p,parentScale);
   var capture=new CutMeshCapture();rika.render(p,capture.record(null,true),15728880,0,-1,0,null,.05f);capture.finish();
   if(baseline!=null)for(int i=0;i<capture.faces.size();i++)for(int j=0;j<4;j++){
    var a=baseline.faces.get(i).points().get(j);var b=capture.faces.get(i).points().get(j);
    if(Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z())>.00001)throw new AssertionError("Cat/player companion world size or placement differs");
   }
   baseline=capture;
  }
 }
 private static void verifyCloseStagger(){
  var p=new PoseStack();p.translate(0,-1.501,0);RikaDanceLayer.placement(p,1);
  var anchor=p.last().pose().transformPosition(new org.joml.Vector3f(0,1.5f,0));
  if(anchor.x<1.2f||anchor.x>1.9f||anchor.z<.65f||anchor.z>1.5f)
   throw new AssertionError("Rika must share a close behind-right dance composition, anchor="+anchor);
 }
}
