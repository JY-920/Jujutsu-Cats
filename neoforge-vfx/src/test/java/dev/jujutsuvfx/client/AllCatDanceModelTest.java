package dev.jujutsuvfx.client;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public final class AllCatDanceModelTest {
 public static void main(String[] args)throws Exception{
  ResourceManager resources=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(p,m,a)->{
   if(!m.getName().equals("openAsReader"))throw new UnsupportedOperationException(m.getName());
   ResourceLocation id=(ResourceLocation)a[0];var in=AllCatDanceModelTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());
   if(in==null)throw new IOException(id.toString());return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
  });
  JsonArray frames=new JsonArray();
  for(String name:new String[]{"yuta_cat","gojo_cat","sukuna_cat","mahoraga_cat"}){
   var yuta=name.equals("yuta_cat")?new YutaCatModel(YutaCatModel.createRoot(),new SorcererCatGeometry(resources,name,null)):null;
   var other=yuta==null?new SorcererCatModel<>(YutaCatModel.createRoot(),resources,name):null;
   if(yuta!=null)yuta.young=false;else other.young=false;
   for(int style=1;style<=2;style++)for(float time:new float[]{10,12,14,16,18,22,50}){
    if(yuta!=null)yuta.applyDance(style,time);else other.applyDance(style,time);
    JsonArray faces=new JsonArray();
    for(int material=0;material<2;material++){
     var capture=new CutMeshCapture();var pose=new PoseStack();
     if(yuta!=null)yuta.renderMaterial(pose,capture.record(null,true),15728880,0,-1,material);
     else other.renderMaterial(pose,capture.record(null,true),15728880,0,-1,material);
     capture.finish();
     for(var f:capture.faces){JsonObject face=new JsonObject();face.addProperty("material",material);JsonArray vertices=new JsonArray();
      for(var v:f.points()){
       if(!Double.isFinite(v.x()+v.y()+v.z())||Math.max(Math.abs(v.x()),Math.max(Math.abs(v.y()),Math.abs(v.z())))>6)throw new AssertionError("Detached dance "+name);
       JsonArray point=new JsonArray();for(double value:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})point.add(value);vertices.add(point);
      }face.add("vertices",vertices);faces.add(face);
     }
    }
    if(faces.size()<60)throw new AssertionError("Missing posed mesh "+name);
    JsonObject f=new JsonObject();f.addProperty("name",name);f.addProperty("style",style);f.addProperty("age",time);f.add("faces",faces);frames.add(f);
   }
  }
  Files.writeString(Path.of("build/all-cat-dances.json"),new Gson().toJson(frames));
  System.out.println("Four actual cat meshes, both dances, 56 posed frames passed");
 }
}
