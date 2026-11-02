package dev.jujutsuvfx.client;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
/** Check real vanilla hook descriptors and shared humanoid pose lifetime, on both versions. */
public final class LivingDanceModelTest {
 static ClassNode read(String name)throws Exception{
  try(var in=LivingDanceModelTest.class.getResourceAsStream("/"+name+".class")){if(in==null)throw new AssertionError(name);var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}
 }
 static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(()->new AssertionError(n.name+" "+name+desc));}
 static boolean same(PartPose a,PartPose b){return a.x==b.x&&a.y==b.y&&a.z==b.z&&a.xRot==b.xRot&&a.yRot==b.yRot&&a.zRot==b.zRot;}
 public static void main(String[] args)throws Exception{
  var living=read("net/minecraft/world/entity/LivingEntity");
  method(living,"travel","(Lnet/minecraft/world/phys/Vec3;)V");method(living,"tick","()V");
  if(living.methods.stream().noneMatch(m->m.name.equals("defineSynchedData")))throw new AssertionError("Tracked-data hook missing");
  method(read("net/minecraft/world/entity/Mob"),"isEffectiveAi","()Z");
  var renderer=read("net/minecraft/client/renderer/entity/LivingEntityRenderer");
  method(renderer,"<init>","(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;F)V");
  var render=method(renderer,"render","(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V");
  int setup=0,push=0;for(var insn:render.instructions)if(insn instanceof MethodInsnNode m){
   if(m.owner.equals("net/minecraft/client/model/EntityModel")&&m.name.equals("setupAnim")&&m.desc.equals("(Lnet/minecraft/world/entity/Entity;FFFFF)V"))setup++;
   if(m.owner.equals("com/mojang/blaze3d/vertex/PoseStack")&&m.name.equals("pushPose"))push++;
  }
  if(setup!=1||push!=1)throw new AssertionError("Ambiguous renderer injection: "+setup+" / "+push);
  var dispatcher=read("net/minecraft/client/renderer/entity/EntityRenderDispatcher");int calls=0;
  for(var m:dispatcher.methods)if(m.name.equals("render"))for(var insn:m.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/client/renderer/entity/EntityRenderer")&&call.name.equals("render")&&call.desc.equals("(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))calls++;
  if(calls!=1)throw new AssertionError("Special renderer fallback hook: "+calls);
  for(boolean slim:new boolean[]{false,true}){
   var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
   var model=new PlayerModel<net.minecraft.world.entity.LivingEntity>(root,slim);
   float min=10,max=-10;
   for(float age=10;age<=90;age+=.25f){
    var before=model.rightArm.storePose();var hip=model.leftLeg.storePose();
    MartialClient.save(model);PlayerDanceClient.applyShuffle(model,age,1);
    min=Math.min(min,model.rightArm.xRot);max=Math.max(max,model.rightArm.xRot);
    if(!Float.isFinite(model.head.y)||Math.abs(model.body.x)>3)throw new AssertionError("Invalid pose");
    LivingDanceClient.restore(model);
    if(!same(before,model.rightArm.storePose())||!same(hip,model.leftLeg.storePose()))throw new AssertionError("Dance pose leaked into next entity");
   }
   if(max-min<.15f)throw new AssertionError("Arms do not pump forward/back with the jog");
  }
  System.out.println("Generic dance: vanilla hook contracts, smooth arm travel and shared-model restoration passed");
 }
}
