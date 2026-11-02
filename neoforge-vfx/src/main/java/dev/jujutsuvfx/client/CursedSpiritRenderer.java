package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.SpiritRules;
import dev.jujutsuvfx.entity.CursedSpirit;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import java.util.*;

/** Only a cached, unticked client-side stand-in is created. It never enters a level entity list. */
public final class CursedSpiritRenderer extends EntityRenderer<CursedSpirit> {
 private record StandIn(int skin,LivingEntity entity){}
 private final Map<CursedSpirit,java.lang.ref.WeakReference<StandIn>> models=new WeakHashMap<>();
 public CursedSpiritRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=.45f;}
 public ResourceLocation getTextureLocation(CursedSpirit s){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/wolf/wolf.png");}
 private record Stitched(String key,int generation,ChimeraRig.Plan plan){}
 private final Map<CursedSpirit,Stitched> chimeras=new WeakHashMap<>();
 /** Recipe spirits: organs of the absorbed species stitched together, then the curse filter on top. */
 private boolean chimera(CursedSpirit spirit,float partial,PoseStack p,MultiBufferSource buffers,int light){
  String[] recipe=spirit.recipe();if(recipe.length==0)return false;
  boolean air=spirit.role()==SpiritRules.AIR;
  String key=String.join("|",recipe)+(air?"#air":"");
  Stitched cached=chimeras.get(spirit);
  if(cached==null||!cached.key().equals(key)||cached.generation()!=ChimeraRig.generation()){
   ChimeraRig.Plan plan=ChimeraRig.plan(recipe,air,spirit.level());cached=new Stitched(key,ChimeraRig.generation(),plan);chimeras.put(spirit,cached);
  }
  if(cached.plan()==null)return false;
  float material=spirit.materialize(partial);if(material<.005f)return true;
  float bodyYaw=net.minecraft.util.Mth.rotLerp(partial,spirit.yBodyRotO,spirit.yBodyRot);
  float headYaw=net.minecraft.util.Mth.wrapDegrees(net.minecraft.util.Mth.rotLerp(partial,spirit.yHeadRotO,spirit.yHeadRot)-bodyYaw);
  float age=spirit.tickCount+partial;
  var motion=new ChimeraRig.Motion(spirit.walkAnimation.position(partial),spirit.walkAnimation.speed(partial),age,
    net.minecraft.util.Mth.clamp(headYaw,-60,60),net.minecraft.util.Mth.lerp(partial,spirit.xRotO,spirit.getXRot()),spirit.getAttackAnim(partial),air);
  p.pushPose();
  try{
   float scale=spirit.modelScale()*material;p.scale(scale,scale,scale);
   p.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-bodyYaw));
   p.scale(-1,-1,1);p.translate(0,-1.501,0);
   float aura=.16f+.10f*(float)Math.sin(age*.13f);
   ChimeraRig.draw(cached.plan(),motion,p,buffers,spirit.palette(),Math.max(light,0x800080),
     net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(spirit,0),aura*material);
  }finally{p.popPose();}
  return true;
 }
 @Override public void render(CursedSpirit spirit,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){
  if(chimera(spirit,partial,p,buffers,light)){super.render(spirit,yaw,partial,p,buffers,light);return;}
  var ref=models.get(spirit);StandIn cached=ref==null?null:ref.get();
  if(cached==null||cached.skin()!=spirit.skin()||cached.entity().level()!=spirit.level()){
   Entity e=spirit.skinType().create(spirit.level());if(!(e instanceof LivingEntity living))return;
   if(living instanceof Mob mob)mob.setNoAi(true);
   if(living instanceof net.minecraft.world.entity.ambient.Bat bat)bat.setResting(false);
   cached=new StandIn(spirit.skin(),living);models.put(spirit,new java.lang.ref.WeakReference<>(cached));
  }
  LivingEntity shape=cached.entity();
  if(shape.tickCount!=spirit.tickCount){shape.walkAnimation.update((float)Math.min(1,spirit.getDeltaMovement().horizontalDistance()*5),.5f);shape.tickCount=spirit.tickCount;}
  if(shape instanceof net.minecraft.world.entity.ambient.Bat bat){bat.restAnimationState.stop();bat.flyAnimationState.startIfStopped(shape.tickCount);}
  shape.setPos(spirit.position());shape.xo=spirit.xo;shape.yo=spirit.yo;shape.zo=spirit.zo;
  shape.yBodyRot=spirit.yBodyRot;shape.yBodyRotO=spirit.yBodyRotO;shape.yHeadRot=spirit.yHeadRot;shape.yHeadRotO=spirit.yHeadRotO;
  shape.setYRot(spirit.getYRot());shape.yRotO=spirit.yRotO;shape.setXRot(spirit.getXRot());shape.xRotO=spirit.xRotO;
  shape.attackAnim=spirit.getAttackAnim(partial);shape.oAttackAnim=spirit.oAttackAnim;shape.hurtTime=spirit.hurtTime;shape.deathTime=spirit.deathTime;
  float material=spirit.materialize(partial);if(material<.005f)return;
  int rgb=SpiritRules.tint(spirit.palette());
  p.pushPose();try{
   float scale=spirit.modelScale()*material;p.scale(scale,scale,scale);
   EntityRenderer renderer=entityRenderDispatcher.getRenderer(shape);
   ResourceLocation body=renderer.getTextureLocation(shape);
   MultiBufferSource tinted=type->{RenderType replacement=SpiritTextures.material(type,body,spirit.palette());return replacement==type?new Tint(buffers.getBuffer(type),rgb):buffers.getBuffer(replacement);};
   renderer.render(shape,yaw,partial,p,tinted,Math.max(light,0x800080));
  }finally{p.popPose();}
  super.render(spirit,yaw,partial,p,buffers,light);
 }
 private static final class Tint implements VertexConsumer {
  private final VertexConsumer out;private final int rgb;
  Tint(VertexConsumer out,int rgb){this.out=out;this.rgb=rgb;}
  public VertexConsumer addVertex(float x,float y,float z){out.addVertex(x,y,z);return this;}
  public VertexConsumer setColor(int r,int g,int b,int a){out.setColor(r*(rgb>>16&255)/255,g*(rgb>>8&255)/255,b*(rgb&255)/255,a);return this;}
  public VertexConsumer setUv(float u,float v){out.setUv(u,v);return this;}
  public VertexConsumer setUv1(int u,int v){out.setUv1(u,v);return this;}
  public VertexConsumer setUv2(int u,int v){out.setUv2(u,v);return this;}
  public VertexConsumer setNormal(float x,float y,float z){out.setNormal(x,y,z);return this;}
 }
}
