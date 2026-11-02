package dev.jujutsuvfx.client;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.core.ShuffleDanceMotion;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
/** A complete reference phrase: test feet and hips on the real cat/player rigs, export posed skin. */
public final class ShuffleReferenceModelTest {
 static float bottom(YutaCatModel cat,String name){
  var bone=cat.bone(name);var bend=cat.meshBend(name);PoseStack p=new PoseStack();bone.translateAndRotate(p);float bottom=-Float.MAX_VALUE;
  for(int row=0;row<=12;row++)for(float z:new float[]{1/16f,3/16f})for(float x:new float[]{-1/16f,1/16f}){
   var v=new Vector3f(x,row/32f,z);bend.position(v);p.last().pose().transformPosition(v);bottom=Math.max(bottom,v.y*16);
  }return bottom;
 }
 static float bottom(ModelPart leg){
  PoseStack p=new PoseStack();leg.translateAndRotate(p);float bottom=-Float.MAX_VALUE;
  for(int row=0;row<=24;row++)for(float z:new float[]{-2/16f,2/16f})for(float x:new float[]{-2/16f,2/16f}){
   var v=FlexibleLimbRenderer.bent(leg,new Vector3f(x,row/32f,z));p.last().pose().transformPosition(v);bottom=Math.max(bottom,v.y*16);
  }return bottom;
 }
 public static void main(String[] args)throws Exception{
  ResourceManager resources=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(p,m,a)->{
   if(!m.getName().equals("openAsReader"))throw new UnsupportedOperationException(m.getName());ResourceLocation id=(ResourceLocation)a[0];
   var in=ShuffleReferenceModelTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
  });
  var cat=new YutaCatModel(YutaCatModel.createRoot(),new SorcererCatGeometry(resources,"yuta_cat",null));cat.young=false;
  JsonArray frames=new JsonArray();float catLift=0,playerLift=0,maxLean=0,maxHeadX=0,maxAir=0;
  for(int frame=0;frame<=30;frame++){
   float age=10+frame*.5f;cat.applyDance(2,age);
   var l=cat.bone("left_hind_leg");var r=cat.bone("right_hind_leg");
   if(Math.abs(l.x-r.x)<2.9f||Math.abs(l.x-r.x)>3.7f)throw new AssertionError("Cat stance must be compact without overlapping feet");
   if(Math.abs(l.x+r.x)>.001)throw new AssertionError("Cat feet slide sideways");
   float lb=bottom(cat,"left_hind_leg"),rb=bottom(cat,"right_hind_leg");catLift=Math.max(catLift,Math.abs(lb-rb));
   if(Math.abs(lb-rb)>3.8f){
    var raised=lb<rb?l:r;var planted=lb<rb?r:l;
    // Plant correction preserves ankle Z at +2: include it when comparing the leg transforms.
    PoseStack rp=new PoseStack();raised.translateAndRotate(rp);PoseStack pp=new PoseStack();planted.translateAndRotate(pp);
    var rv=cat.meshBend("left_hind_leg").position(new Vector3f(0,6/16f,2/16f));var pv=cat.meshBend("right_hind_leg").position(new Vector3f(0,6/16f,2/16f));rp.last().pose().transformPosition(rv);pp.last().pose().transformPosition(pv);
    if((pv.z-rv.z)*16<3)throw new AssertionError("Raised shoe remains behind/inside the body");
   }
   maxAir=Math.max(maxAir,24-Math.max(lb,rb));
   float support=24-ShuffleDanceMotion.sample(age)[0]*16;
   if(Math.abs(Math.max(lb,rb)-support)>.01)throw new AssertionError("Cat support foot slides vertically");
   maxLean=Math.max(maxLean,Math.abs(cat.bone("body").zRot));maxHeadX=Math.max(maxHeadX,Math.abs(cat.bone("head").x));
   for(boolean slim:new boolean[]{false,true}){
    var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();var model=new PlayerModel<net.minecraft.world.entity.LivingEntity>(root,slim);
    PlayerDanceClient.applyShuffle(model,age,1);
    if(Math.abs(model.leftLeg.x-model.rightLeg.x)<4.1||Math.abs(model.leftLeg.x-model.rightLeg.x)>4.7)throw new AssertionError("Player stance must be compact");
    float left=bottom(model.leftLeg),right=bottom(model.rightLeg);playerLift=Math.max(playerLift,Math.abs(left-right));
    if(Math.abs(Math.max(left,right)-support)>.01)throw new AssertionError("Player support foot slides vertically");
    FlexibleLimbRenderer.clear(model);
   }
   JsonArray faces=new JsonArray();for(int material=0;material<2;material++){
    var capture=new CutMeshCapture();cat.renderMaterial(new PoseStack(),capture.record(null,true),15728880,0,-1,material);capture.finish();
    for(var face:capture.faces){JsonObject f=new JsonObject();f.addProperty("material",material);JsonArray points=new JsonArray();for(var v:face.points()){
     if(!Double.isFinite(v.x()+v.y()+v.z()))throw new AssertionError("Non-finite skin vertex");JsonArray point=new JsonArray();for(double n:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})point.add(n);points.add(point);
    }f.add("vertices",points);faces.add(f);}
   }JsonObject f=new JsonObject();f.addProperty("name","yuta_cat");f.addProperty("age",age);f.add("faces",faces);frames.add(f);
  }
  if(catLift<3.8||playerLift<3.8||maxLean<.04||maxLean>.10||maxHeadX>1.2||maxAir<2.5)throw new AssertionError("Incorrect compact-hop motion "+catLift+" / "+playerLift+" / "+maxLean+" / "+maxHeadX);
  Files.writeString(Path.of("build/shuffle-reference-phrase.json"),new Gson().toJson(frames));
  System.out.println("Full phrase: planted separated feet, alternating four-pixel lifts, subtle hip lean, visible double-foot airtime and real textured cat mesh passed");
 }
}
