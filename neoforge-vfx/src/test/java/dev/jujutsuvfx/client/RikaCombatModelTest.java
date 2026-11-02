package dev.jujutsuvfx.client;
import com.google.gson.*;
import dev.jujutsuvfx.core.RikaSupportClock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class RikaCombatModelTest {
 public static void main(String[] args)throws Exception{
  ResourceManager resources=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(p,m,a)->{
   if(!m.getName().equals("openAsReader"))throw new UnsupportedOperationException(m.getName());
   ResourceLocation id=(ResourceLocation)a[0];var in=RikaCombatModelTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());
   if(in==null)throw new IOException(id.toString());return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
  });
  verifyContactKeys();
  JsonArray frames=new JsonArray();
  java.util.Set<String> contacts=new java.util.HashSet<>();
  java.util.List<org.joml.Vector3d> rest=null;
  for(String name:new String[]{"idle","crawl","punch","launch","slam","guard","wave","hook","claw","overhead"}){
   int action=0;for(int candidate=1;candidate<=RikaSupportClock.OVERHEAD;candidate++)if(name.equals(RikaSupportClock.clip(candidate)))action=candidate;
   int duration=RikaSupportClock.duration(action),contact=RikaSupportClock.contact(action);
   var geometry=new SorcererCatGeometry(resources,"rika_combat","animation.rika."+name);
   for(int tick=0;tick<=duration;tick++){
    var capture=new CutMeshCapture();var p=new PoseStack();p.scale(2.7f,2.7f,2.7f);p.translate(0,-1.5,0);
    geometry.render(p,capture.record(null,true),15728880,0,-1,0,null,tick/(float)duration);capture.finish();
    if(capture.faces.size()!=306)throw new AssertionError("Missing Rika mesh: "+name);
    JsonArray faces=new JsonArray();double floor=Double.NEGATIVE_INFINITY;boolean skin=false,details=false;
    for(var f:capture.faces){JsonArray points=new JsonArray();
     for(var v:f.points()){
      floor=Math.max(floor,v.y());skin|=v.v()<1f/3;details|=v.v()>1f/3;
      if(v.u()<0||v.u()>1||v.v()<0||v.v()>1)throw new AssertionError("Rika atlas UV escaped");
      if(!Double.isFinite(v.x()+v.y()+v.z())||Math.max(Math.abs(v.x()),Math.max(Math.abs(v.y()),Math.abs(v.z())))>16)
       throw new AssertionError("Unbounded combat mesh "+name+" tick "+tick);
      JsonArray point=new JsonArray();for(double value:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})point.add(value);points.add(point);
     }faces.add(points);
    }
    if(Math.abs(floor)>.012||!skin||!details)throw new AssertionError("Rika floor/material loss "+name+" tick "+tick+" floor "+floor);
    if(tick==0||tick==duration){
     var points=new java.util.ArrayList<org.joml.Vector3d>();
     for(var face:capture.faces)for(var v:face.points())points.add(new org.joml.Vector3d(v.x(),v.y(),v.z()));
     if(rest==null)rest=points;
     for(int i=0;i<points.size();i++)if(points.get(i).distance(rest.get(i))>.001)
      throw new AssertionError("Rika clips must join through their shared supported rest pose: "+name+" tick "+tick);
    }
    if(tick==contact&&!name.equals("idle")&&!name.equals("crawl")&&!name.equals("guard")&&!name.equals("wave"))contacts.add(faces.toString());
    if(tick==0||tick==contact-4||tick==contact||tick==contact+2||tick==duration-3){JsonObject frame=new JsonObject();frame.addProperty("clip",name);frame.addProperty("age",tick);frame.add("faces",faces);frames.add(frame);}
   }
  }
  if(contacts.size()!=6)throw new AssertionError("Six melee variants need distinct visible contact poses");
  Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/rika-combat.json"),new Gson().toJson(frames));
  System.out.println("Rika combat: 320 geometry frames, six distinct strikes, shared rest joins, crawl loop, grounded atlas passed");
 }
 private static void verifyContactKeys()throws Exception{
  try(var stream=RikaCombatModelTest.class.getResourceAsStream("/assets/jujutsuvfx/models/entity/rika_combat.bbmodel")){
   if(stream==null)throw new AssertionError("Missing Rika clip asset");
   var model=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
   for(var raw:model.getAsJsonArray("animations")){
    var clip=raw.getAsJsonObject();String name=clip.get("name").getAsString();
    int action=0;for(int candidate=1;candidate<=RikaSupportClock.OVERHEAD;candidate++)if(name.equals("animation.rika."+RikaSupportClock.clip(candidate)))action=candidate;
    if(action==0)continue;
    float seconds=clip.get("length").getAsFloat();
    if(Math.abs(seconds-RikaSupportClock.duration(action)/20f)>1e-6)throw new AssertionError("Rika clip length no longer matches support clock");
    // SorcererCatGeometry.render converts normalized progress to seconds internally.
    float contactTime=RikaSupportClock.contact(action)/(float)RikaSupportClock.duration(action)*seconds;
    if(Math.abs(contactTime-RikaSupportClock.contact(action)/20f)>1e-6)throw new AssertionError("Rika contact clock drift");
    float expected=switch(action){case RikaSupportClock.PUNCH->85;case RikaSupportClock.LAUNCH->108;case RikaSupportClock.SLAM->5;case RikaSupportClock.GUARD->77;case RikaSupportClock.WAVE->80;case RikaSupportClock.HOOK->35;case RikaSupportClock.CLAW->70;default->30;};
    boolean found=false;
    for(var entry:clip.getAsJsonObject("animators").entrySet()){
     var track=entry.getValue().getAsJsonObject();if(!track.get("name").getAsString().equals("right_front_leg"))continue;
     for(var keyRaw:track.getAsJsonArray("keyframes")){
      var key=keyRaw.getAsJsonObject();if(!key.get("channel").getAsString().equals("rotation")||Math.abs(key.get("time").getAsFloat()-contactTime)>1e-6)continue;
      float angle=key.getAsJsonArray("data_points").get(0).getAsJsonObject().get("x").getAsFloat();
      if(Math.abs(angle-expected)>1e-5)throw new AssertionError("Rika contact pose mismatch: "+name+" at "+contactTime+"s");found=true;
     }
    }
    if(!found)throw new AssertionError("Rika contact key absent: "+name);
   }
  }
 }

}
