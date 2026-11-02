package dev.jujutsuvfx.client;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import java.lang.reflect.Proxy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
/** Loads the shipped BB hierarchy and both material batches through the production importer. */
public final class GetoImportedModelTest {
 public static void main(String[] args)throws Exception{
  ResourceManager resources=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(p,m,a)->{
   if(!m.getName().equals("openAsReader"))throw new UnsupportedOperationException(m.getName());
   ResourceLocation id=(ResourceLocation)a[0];var in=GetoImportedModelTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());
   if(in==null)throw new IOException(id.toString());return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
  });
  var geometry=new SorcererCatGeometry(resources,"geto_cat",null);
  var model=new YutaCatModel(YutaCatModel.createRoot(),geometry);
  JsonArray frames=new JsonArray();int total=0;
  var samples=new java.util.ArrayList<int[]>();
  for(int action=200;action<=206;action++)for(int age:new int[]{0,4,8,9,11,14,18,24,30})samples.add(new int[]{action,age});
  for(int age:new int[]{0,14,40,58,70,74,90,112})samples.add(new int[]{101,age});
  for(int[] pose:samples){
   model.applyAction(pose[0],pose[1]);JsonArray faces=new JsonArray();
   for(int material=0;material<2;material++){
    var capture=new CutMeshCapture();
    // The dummy material token is not drawn: the real importer still emits complete entity vertices.
    model.renderMaterial(new PoseStack(),capture.record(null,true),15728880,0,-1,material);capture.finish();
    if(capture.faces.size()<(material==0?30:100))throw new AssertionError("Missing supplied material "+material+": "+capture.faces.size());
    for(var f:capture.faces){JsonObject face=new JsonObject();face.addProperty("material",material);JsonArray vertices=new JsonArray();
     for(var v:f.points()){
      if(!Double.isFinite(v.x()+v.y()+v.z())||Math.max(Math.abs(v.x()),Math.max(Math.abs(v.y()),Math.abs(v.z())))>5)throw new AssertionError("Detached imported mesh");
      if(v.u()<0||v.u()>1||v.v()<0||v.v()>1)throw new AssertionError("UV outside supplied atlas");
      JsonArray point=new JsonArray();for(double value:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})point.add(value);vertices.add(point);
     }
     face.add("vertices",vertices);faces.add(face);total++;
    }
   }
   JsonObject frame=new JsonObject();frame.addProperty("action",pose[0]);frame.addProperty("age",pose[1]);frame.add("faces",faces);frames.add(frame);
  }
  Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/geto1102-model.json"),new Gson().toJson(frames));
  System.out.println("Geto imported model: "+total+" posed quads, both texture atlases, finite bones and UV bounds passed");
 }
}
