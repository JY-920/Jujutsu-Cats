package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.PlaneCut;
import dev.jujutsuvfx.core.WorldCutPath;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraft.core.particles.ParticleTypes;
import dev.jujutsuvfx.network.WorldCutEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;
import java.util.*;
/** Real posed triangles, clipped once on impact, then animated as two independent fragments. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class WorldCutRenderer {
 private record Face(RenderType material,List<PlaneCut.Vertex> points,int seed){}
 private record Body(WorldCutEffect hit,long born,Vec3 normal,List<Face> positive,List<Face> negative,List<List<PlaneCut.Vertex>> caps){}
 record Blade(WorldCutEffect hit,long born){}
 private record Shard(Vec3 center,Vec3 velocity,long born,int seed){}
 private static final List<Shard> SHARDS=new ArrayList<>();
 private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));
 private static long shakeBorn=-100;private static float shakePower;
 private static final LinkedHashMap<UUID,Body> BODIES=new LinkedHashMap<>();
 private static final Map<UUID,Integer> HIDDEN_DEAD=new HashMap<>();
 private static final List<Blade> BLADES=new ArrayList<>();
 private static ClientLevel world;private static boolean capturing;private static int soundSeed;private static long soundTick=Long.MIN_VALUE;
 private WorldCutRenderer(){}
 private static void ensure(){var level=Minecraft.getInstance().level;if(world!=level){BODIES.clear();WorldCutRenderTypes.clearBodies();WorldCutBladeRenderer.clear();BLADES.clear();HIDDEN_DEAD.clear();SHARDS.clear();shakeBorn=-100;shakePower=0;world=level;soundTick=Long.MIN_VALUE;}}
 public static void receive(WorldCutEffect hit){
  ensure();Minecraft mc=Minecraft.getInstance();if(world==null||!hit.valid()||hit.center().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())>192*192)return;
  ClientEvents.prepareSukunaEffects(world);
  long now=ClientEffectClock.ticks();WorldCutEffect visual=hit;
  if(hit.target()>=0){
   if(world.getEntity(hit.target()) instanceof LivingEntity entity&&entity.getUUID().equals(hit.uuid()))visual=capture(entity,hit,now);
   burst(visual,now);
  }
  BLADES.add(new Blade(visual,now));while(BLADES.size()>96)BLADES.remove(0);
  double distance=hit.center().distanceTo(mc.gameRenderer.getMainCamera().getPosition());
  float strength=(float)Math.max(0,1-distance/36)*(hit.target()<0?.85f:2.5f);
  if(strength>0){shakePower=Math.max(strength,now-shakeBorn<5?shakePower*.8f:0);shakeBorn=now;}
  if(hit.target()<0&&(soundTick!=now||soundSeed!=hit.seed())){
   soundTick=now;soundSeed=hit.seed();Vec3 c=hit.center();
   world.playLocalSound(c.x,c.y,c.z,SoundEvent.createVariableRangeEvent(new ResourceLocation(JujutsuVfx.ID,"world_cut_draw")),SoundSource.PLAYERS,1.65f,.96f+random(7,hit.seed())*.08f,false);
  }
 }
 private static void burst(WorldCutEffect hit,long now){
  Vec3 n=hit.forward().cross(hit.stroke()).normalize();
  for(int i=0;i<34;i++){
   double a=random(i*5,hit.seed())*Math.PI*2,speed=3+random(i*5+1,hit.seed())*9;
   Vec3 velocity=hit.stroke().scale(Math.cos(a)*speed).add(n.scale(Math.sin(a)*speed)).add(hit.forward().scale((random(i+91,hit.seed())-.5)*6));
   SHARDS.add(new Shard(hit.center(),velocity,now,hit.seed()+i*197));
  }
  while(SHARDS.size()>512)SHARDS.remove(0);
  for(int i=0;i<8;i++)world.addParticle(ParticleTypes.POOF,hit.center().x,hit.center().y,hit.center().z,
   (random(i,hit.seed())-.5)*.65,(random(i+8,hit.seed())-.35)*.65,(random(i+16,hit.seed())-.5)*.65);
 }
 static void camera(ViewportEvent.ComputeCameraAngles event,long now,float partial,float scale){
  float age=(now-shakeBorn+partial)*.05f;if(age<0||age>.26f)return;
  float power=shakePower*(1-age/.26f)*(1-age/.26f)*scale;
  event.setPitch(event.getPitch()+(float)Math.cos(age*88)*power);
  event.setYaw(event.getYaw()+(float)Math.sin(age*69)*power*.4f);
  event.setRoll(event.getRoll()+(float)Math.sin(age*97)*power*.65f);
 }
 @SuppressWarnings({"rawtypes","unchecked"})
 private static WorldCutEffect capture(LivingEntity entity,WorldCutEffect hit,long now){
  Minecraft mc=Minecraft.getInstance();CutMeshCapture recorder=new CutMeshCapture();PoseStack poses=new PoseStack();
  int death=entity.deathTime,hurt=entity.hurtTime;entity.deathTime=entity.hurtTime=0;
  capturing=true;
  try{
   EntityRenderer renderer=mc.getEntityRenderDispatcher().getRenderer(entity);
   float partial=mc.getFrameTime();
   Vec3 interpolated=new Vec3(Mth.lerp((double)partial,entity.xOld,entity.getX()),Mth.lerp((double)partial,entity.yOld,entity.getY()),Mth.lerp((double)partial,entity.zOld,entity.getZ()));
   Vec3 offset=interpolated.add(renderer.getRenderOffset(entity,partial)).subtract(hit.center());poses.translate(offset.x,offset.y,offset.z);
   renderer.render(entity,Mth.lerp(partial,entity.yRotO,entity.getYRot()),partial,poses,recorder,LightTexture.FULL_BRIGHT);
   recorder.finish();
   if(recorder.faces.isEmpty()){LogUtils.getLogger().debug("Space cut: no capturable model quads for {}",entity.getType());return hit;}
   var center=PlaneCut.boundsCenter(recorder.faces.stream().map(CutMeshCapture.Face::points).toList());
   Vec3 visualCenter=hit.center().add(center.x(),center.y(),center.z());
   WorldCutEffect visual=new WorldCutEffect(hit.target(),hit.uuid(),visualCenter,hit.forward(),hit.stroke(),hit.seed(),hit.bladeLength());
   Vec3 n=hit.forward().cross(hit.stroke()).normalize();var plane=new PlaneCut.Plane(n.x,n.y,n.z,0);
   List<Face> positive=new ArrayList<>(),negative=new ArrayList<>();List<List<PlaneCut.Vertex>> polygons=new ArrayList<>();int i=0;
   for(var f:recorder.faces){var points=f.points().stream().map(v->v.shifted(-center.x(),-center.y(),-center.z())).toList();var p=PlaneCut.clip(points,plane,true);var q=PlaneCut.clip(points,plane,false);
    if(!p.isEmpty())positive.add(new Face(f.material(),p,i));if(!q.isEmpty())negative.add(new Face(f.material(),q,i));polygons.add(points);i++;
   }
   if(positive.isEmpty()||negative.isEmpty())return hit;
   while(BODIES.size()>=32)BODIES.remove(BODIES.keySet().iterator().next());
   BODIES.put(hit.uuid(),new Body(visual,now,n,List.copyOf(positive),List.copyOf(negative),PlaneCut.sections(polygons,plane)));
   HIDDEN_DEAD.put(entity.getUUID(),entity.getId());
   return visual;
  }catch(RuntimeException failure){LogUtils.getLogger().warn("Could not capture space-cut model {}",entity.getType(),failure);}
  finally{capturing=false;entity.deathTime=death;entity.hurtTime=hurt;}
  return hit;
 }
 @SubscribeEvent public static void hide(RenderLivingEvent.Pre<?,?> event){
  LivingEntity entity=event.getEntity();
  boolean hiddenCorpse=Objects.equals(HIDDEN_DEAD.get(entity.getUUID()),entity.getId())&&entity.isDeadOrDying();
  if(!capturing&&(BODIES.containsKey(entity.getUUID())||hiddenCorpse))event.setCanceled(true);
 }
 public static void render(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;ensure();if(world==null)return;
  long now=ClientEffectClock.ticks();float partial=event.getPartialTick();
  BODIES.values().removeIf(b->now-b.born>18||now<b.born);BLADES.removeIf(b->now-b.born>11||now<b.born);SHARDS.removeIf(b->now-b.born>16||now<b.born);
  HIDDEN_DEAD.entrySet().removeIf(e->!(world.getEntity(e.getValue()) instanceof LivingEntity entity)
    ||!entity.getUUID().equals(e.getKey())||!BODIES.containsKey(e.getKey())&&entity.isAlive());
  if(BODIES.isEmpty()&&BLADES.isEmpty()&&SHARDS.isEmpty()){WorldCutBladeRenderer.clear();return;}
  var buffers=BUFFERS;Set<RenderType> used=new LinkedHashSet<>();PoseStack poses=new PoseStack();Vec3 camera=event.getCamera().getPosition();
  // Set the view transform exactly once; AFTER_LEVEL has already restored the global stack.
  poses.last().pose().set(event.getPoseStack().last().pose());poses.last().normal().set(new org.joml.Matrix3f(event.getPoseStack().last().pose()));
  var modelView=RenderSystem.getModelViewStack();modelView.pushPose();modelView.setIdentity();RenderSystem.applyModelViewMatrix();
  float[] shaderColor=RenderSystem.getShaderColor().clone();
  try(DomainRenderer.State ignored=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
  RenderSystem.setShaderColor(1,1,1,1);
  for(Body b:BODIES.values()){
   float age=(now-b.born+partial)*.05f;float flight=Math.max(0,age-.035f),dissolve=smooth((age-.22f)/.55f);
   for(int side:new int[]{1,-1}){
    poses.pushPose();Vec3 c=b.hit.center().subtract(camera);double impulse=side*(.03+flight*6.8+flight*flight*2.4);
    poses.translate(c.x+b.normal.x*impulse,c.y+b.normal.y*impulse-flight*flight*1.5,c.z+b.normal.z*impulse);
    Vec3 axis=b.hit.stroke();poses.mulPose(new Quaternionf().rotationAxis(side*flight*2.4f,(float)axis.x,(float)axis.y,(float)axis.z));
    for(Face f:side>0?b.positive:b.negative){
     float random=random(f.seed,b.hit.seed());if(random<dissolve)continue;
     poses.pushPose();double scatter=dissolve*dissolve*2.5;poses.translate((random-.5)*scatter,random(f.seed+7,b.hit.seed())*scatter,(random(f.seed+19,b.hit.seed())-.5)*scatter);
     RenderType material=WorldCutRenderTypes.body(f.material);VertexConsumer out=buffers.getBuffer(material);used.add(material);
     var v=f.points;for(int i=1;i+1<v.size();i++){vertex(out,poses,v.get(0));vertex(out,poses,v.get(i));vertex(out,poses,v.get(i+1));vertex(out,poses,v.get(i+1));}
     poses.popPose();
    }
    if(dissolve<.9f){RenderType type=RenderType.debugQuads();used.add(type);VertexConsumer cap=buffers.getBuffer(type);
     for(var loop:b.caps){var a=loop.get(0);for(int i=1;i+1<loop.size();i++){
      var p=loop.get(i);var q=loop.get(i+1);color(cap,poses,new Vec3(a.x(),a.y(),a.z()),.035f,.015f,.055f,1-dissolve);
      color(cap,poses,new Vec3(p.x(),p.y(),p.z()),.035f,.015f,.055f,1-dissolve);color(cap,poses,new Vec3(q.x(),q.y(),q.z()),.035f,.015f,.055f,1-dissolve);color(cap,poses,new Vec3(q.x(),q.y(),q.z()),.035f,.015f,.055f,1-dissolve);
     }}
    }
    poses.popPose();
   }
  }
  RenderType glow=WorldCutRenderTypes.GLOW;used.add(glow);VertexConsumer rays=buffers.getBuffer(glow);
  for(Shard shard:SHARDS){
   float age=(now-shard.born+partial)*.05f,fade=1-smooth(age/.75f);
   Vec3 c=shard.center.add(shard.velocity.scale(age)).add(0,-age*age*2,0).subtract(camera);
   Vec3 axis=shard.velocity.normalize(),toward=c.scale(-1).normalize(),wide=axis.cross(toward).normalize();
   double length=(.09+random(5,shard.seed)*.35)*fade;
   ribbon(rays,poses,c,axis,wide,-length,length,(.012+random(9,shard.seed)*.032)*fade,1,1,1,fade);
  }
  for(RenderType type:used)buffers.endBatch(type);
  WorldCutBladeRenderer.render(event,BLADES,now,partial);
  }finally{buffers.endBatch();RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);modelView.popPose();RenderSystem.applyModelViewMatrix();}
 }
 private static void ribbon(VertexConsumer out,PoseStack pose,Vec3 c,Vec3 axis,Vec3 wide,double a,double b,double thickness,float r,float g,float blue,float alpha){
  Vec3 x=c.add(axis.scale(a)),y=c.add(axis.scale(b)),w=wide.scale(thickness);
  color(out,pose,x.subtract(w),r,g,blue,alpha);color(out,pose,y.subtract(w),r,g,blue,alpha);color(out,pose,y.add(w),r,g,blue,alpha);color(out,pose,x.add(w),r,g,blue,alpha);
 }
 private static void vertex(VertexConsumer out,PoseStack poses,PlaneCut.Vertex v){out.vertex(poses.last().pose(),(float)v.x(),(float)v.y(),(float)v.z()).color(v.r(),v.g(),v.b(),v.a()).uv(v.u(),v.v()).overlayCoords(v.overlay()).uv2(v.light()).normal(poses.last().normal(),v.nx(),v.ny(),v.nz()).endVertex();}
 private static void color(VertexConsumer out,PoseStack pose,Vec3 p,float r,float g,float b,float a){out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(r,g,b,a).endVertex();}
 private static float random(int a,int seed){int x=a*0x45d9f3b+seed;x=(x^(x>>>16))*0x45d9f3b;return (x&65535)/65535f;}
 private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
