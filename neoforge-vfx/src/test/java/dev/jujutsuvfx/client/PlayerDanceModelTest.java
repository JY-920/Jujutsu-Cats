package dev.jujutsuvfx.client;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3f;

/** Tests the actual player skin mesh, not an animation-only mock. */
public final class PlayerDanceModelTest {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var field=ModelPart.class.getDeclaredField("cubes");field.setAccessible(true);
  JsonArray frames=new JsonArray();
  for(boolean slim:new boolean[]{false,true})for(int frame=0;frame<=88;frame++){
   var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
   var model=new PlayerModel<net.minecraft.world.entity.LivingEntity>(root,slim);
   PlayerDanceClient.apply(model,10+frame*20f/30,1);
   if(model.head.y>7||model.body.xRot>.70f||model.body.xRot<.35f)throw new AssertionError("Player dance must keep face raised with a moderate torso lean");
   var capture=new CutMeshCapture();var consumer=capture.record(null,true);
   for(ModelPart part:new ModelPart[]{model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg,model.hat,model.jacket,model.leftSleeve,model.rightSleeve,model.leftPants,model.rightPants}){
    var p=new PoseStack();part.translateAndRotate(p);
    var cubes=(List<ModelPart.Cube>)field.get(part);
    if(!FlexibleLimbRenderer.render(part,cubes,p.last(),consumer,15728880,0,-1))for(var cube:cubes)cube.compile(p.last(),consumer,15728880,0,-1);
   }
   capture.finish();JsonArray faces=new JsonArray();
   for(var face:capture.faces){JsonArray vertices=new JsonArray();for(var v:face.points()){
    if(!Double.isFinite(v.x()+v.y()+v.z())||v.y()>1.54||v.y()<-.35)throw new AssertionError("Player dance escaped floor/height: "+v);
    JsonArray point=new JsonArray();for(double x:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})point.add(x);vertices.add(point);
   }faces.add(vertices);}
   if(!slim){JsonObject f=new JsonObject();f.add("faces",faces);frames.add(f);}
   // Wear/armor copies inherit and clear both kinds of deformation.
   ModelPart copy=LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(.5f),slim),64,64).bakeRoot().getChild("body");
   FlexibleLimbRenderer.copy(model.body,copy);
   var dummy=new CutMeshCapture();if(!FlexibleLimbRenderer.render(copy,(List<ModelPart.Cube>)field.get(copy),new PoseStack().last(),dummy.record(null,true),15728880,0,-1))throw new AssertionError("Missing curved armor torso");
   FlexibleLimbRenderer.clear(model);FlexibleLimbRenderer.copy(model.body,copy);
   if(FlexibleLimbRenderer.render(copy,(List<ModelPart.Cube>)field.get(copy),new PoseStack().last(),dummy.record(null,true),15728880,0,-1))throw new AssertionError("Dance leaks into normal armor");
  }
  var a=frames.get(0).toString();var last=frames.get(88).getAsJsonObject().getAsJsonArray("faces");var first=frames.get(0).getAsJsonObject().getAsJsonArray("faces");double seam=0;
  if(first.size()!=last.size())throw new AssertionError("Loop topology");
  for(int i=0;i<first.size();i++)for(int j=0;j<4;j++)for(int k=0;k<3;k++)seam=Math.max(seam,Math.abs(first.get(i).getAsJsonArray().get(j).getAsJsonArray().get(k).getAsDouble()-last.get(i).getAsJsonArray().get(j).getAsJsonArray().get(k).getAsDouble()));
  if(seam>.0001)throw new AssertionError("Player loop seam "+seam);
  Files.writeString(Path.of("build/player-dance.json"),new Gson().toJson(frames));
  System.out.println("Player dance: normal/slim skins, curved torso, planted feet, armor copy/reset and 89-frame loop passed");
 }
}
