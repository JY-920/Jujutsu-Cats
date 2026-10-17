package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.PlaneCut;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;
import java.util.*;
/** Real posed triangles, clipped once on impact, then animated as two independent fragments. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class WorldCutRenderer {
 private record Face(RenderType material,List<PlaneCut.Vertex> points,int seed){}
 private record Body(WorldCutEffect hit,long born,Vec3 normal,List<Face> positive,List<Face> negative,List<List<PlaneCut.Vertex>> caps){}
 private record Blade(WorldCutEffect hit,long born){}
 private static final LinkedHashMap<UUID,Body> BODIES=new LinkedHashMap<>();
 private static final Map<UUID,Integer> HIDDEN_DEAD=new HashMap<>();
 private static final List<Blade> BLADES=new ArrayList<>();
 private static ClientLevel world;private static boolean capturing;private static int soundSeed;private static long soundTick=Long.MIN_VALUE;
 private WorldCutRenderer(){}
 private static void ensure(){var level=Minecraft.getInstance().level;if(world!=level){BODIES.clear();BLADES.clear();HIDDEN_DEAD.clear();world=level;soundTick=Long.MIN_VALUE;}}
 public static void receive(WorldCutEffect hit){
  ensure();Minecraft mc=Minecraft.getInstance();if(world==null||!hit.valid()||hit.center().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())>192*192)return;
  long now=ClientEffectClock.ticks();WorldCutEffect visual=hit;
  if(world.getEntity(hit.target()) instanceof LivingEntity entity&&entity.getUUID().equals(hit.uuid()))visual=capture(entity,hit,now);
  BLADES.add(new Blade(visual,now));while(BLADES.size()>96)BLADES.remove(0);
  if(soundTick!=now||soundSeed!=hit.seed()){
   soundTick=now;soundSeed=hit.seed();Vec3 c=hit.center();
   world.playLocalSound(c.x,c.y,c.z,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"shrine_slash")),SoundSource.PLAYERS,1.5f,1.38f,false);
   world.playLocalSound(c.x,c.y,c.z,SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,.65f,.68f,false);
  }
 }
 @SuppressWarnings({"rawtypes","unchecked"})
 private static WorldCutEffect capture(LivingEntity entity,WorldCutEffect hit,long now){
  Minecraft mc=Minecraft.getInstance();CutMeshCapture recorder=new CutMeshCapture();PoseStack poses=new PoseStack();
  int death=entity.deathTime,hurt=entity.hurtTime;entity.deathTime=entity.hurtTime=0;
  capturing=true;
  try{
   EntityRenderer renderer=mc.getEntityRenderDispatcher().getRenderer(entity);
   float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
   Vec3 interpolated=new Vec3(Mth.lerp((double)partial,entity.xOld,entity.getX()),Mth.lerp((double)partial,entity.yOld,entity.getY()),Mth.lerp((double)partial,entity.zOld,entity.getZ()));
   Vec3 offset=interpolated.add(renderer.getRenderOffset(entity,partial)).subtract(hit.center());poses.translate(offset.x,offset.y,offset.z);
   renderer.render(entity,Mth.lerp(partial,entity.yRotO,entity.getYRot()),partial,poses,recorder,LightTexture.FULL_BRIGHT);
   recorder.finish();
   if(recorder.faces.isEmpty())return hit;
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
 @SubscribeEvent public static void render(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;ensure();if(world==null)return;
  long now=ClientEffectClock.ticks();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
  BODIES.values().removeIf(b->now-b.born>18||now<b.born);BLADES.removeIf(b->now-b.born>9||now<b.born);
  HIDDEN_DEAD.entrySet().removeIf(e->!(world.getEntity(e.getValue()) instanceof LivingEntity entity)
    ||!entity.getUUID().equals(e.getKey())||!BODIES.containsKey(e.getKey())&&entity.isAlive());
  if(BODIES.isEmpty()&&BLADES.isEmpty())return;
  var buffers=Minecraft.getInstance().renderBuffers().bufferSource();Set<RenderType> used=new LinkedHashSet<>();PoseStack poses=event.getPoseStack();Vec3 camera=event.getCamera().getPosition();
  for(Body b:BODIES.values()){
   float age=(now-b.born+partial)*.05f;float flight=Math.max(0,age-.055f),dissolve=smooth((age-.35f)/.48f);
   for(int side:new int[]{1,-1}){
    poses.pushPose();Vec3 c=b.hit.center().subtract(camera);double impulse=side*(.015+flight*1.9+flight*flight*.8);
    poses.translate(c.x+b.normal.x*impulse,c.y+b.normal.y*impulse-flight*flight*1.5,c.z+b.normal.z*impulse);
    Vec3 axis=b.hit.stroke();poses.mulPose(new Quaternionf().rotationAxis(side*flight*.9f,(float)axis.x,(float)axis.y,(float)axis.z));
    for(Face f:side>0?b.positive:b.negative){
     float random=random(f.seed,b.hit.seed());if(random<dissolve)continue;
     poses.pushPose();double scatter=dissolve*dissolve*.65;poses.translate((random-.5)*scatter,random(f.seed+7,b.hit.seed())*scatter,(random(f.seed+19,b.hit.seed())-.5)*scatter);
     VertexConsumer out=buffers.getBuffer(f.material);used.add(f.material);
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
  RenderType glow=RenderType.lightning();used.add(glow);VertexConsumer rays=buffers.getBuffer(glow);
  for(Blade b:BLADES){float age=(now-b.born+partial)*.05f;if(age>.36f)continue;
   Vec3 center=b.hit.center().subtract(camera),stroke=b.hit.stroke(),width=b.hit.forward().cross(stroke).normalize();double length=b.hit.bladeLength()*.5;
   double tip=-length+2*length*smooth(age/.09f);float fade=1-smooth((age-.09f)/.27f);
   ribbon(rays,poses,center,stroke,width,-length,tip,.12*fade,.43f,.25f,.95f,fade*.25f);
   ribbon(rays,poses,center,stroke,width,-length,tip,.024*fade,1,1,1,fade);
  }
  for(RenderType type:used)buffers.endBatch(type);
 }
 private static void ribbon(VertexConsumer out,PoseStack pose,Vec3 c,Vec3 axis,Vec3 wide,double a,double b,double thickness,float r,float g,float blue,float alpha){
  Vec3 x=c.add(axis.scale(a)),y=c.add(axis.scale(b)),w=wide.scale(thickness);
  color(out,pose,x.subtract(w),r,g,blue,alpha);color(out,pose,y.subtract(w),r,g,blue,alpha);color(out,pose,y.add(w),r,g,blue,alpha);color(out,pose,x.add(w),r,g,blue,alpha);
 }
 private static void vertex(VertexConsumer out,PoseStack poses,PlaneCut.Vertex v){out.addVertex(poses.last().pose(),(float)v.x(),(float)v.y(),(float)v.z()).setColor(v.r(),v.g(),v.b(),v.a()).setUv(v.u(),v.v()).setOverlay(v.overlay()).setLight(v.light()).setNormal(poses.last(),v.nx(),v.ny(),v.nz());}
 private static void color(VertexConsumer out,PoseStack pose,Vec3 p,float r,float g,float b,float a){out.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,a);}
 private static float random(int a,int seed){int x=a*0x45d9f3b+seed;x=(x^(x>>>16))*0x45d9f3b;return (x&65535)/65535f;}
 private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
