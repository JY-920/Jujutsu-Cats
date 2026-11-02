package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.GetoVortexMotion;
import dev.jujutsuvfx.core.SustainedBeam;
import dev.jujutsuvfx.entity.GetoCat;
import org.joml.Matrix4f;
import dev.jujutsuvfx.network.UzumakiState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Same material/geometry as Geto, rendered directly without a proxy entity. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class PlayerUzumakiRenderer {
 private record Cast(UzumakiState state,long received){}
 private static final Map<UUID,Cast> CASTS=new HashMap<>();
 record Clash(int id,float age,Vec3 rika,Vec3 geto,Vec3 contact,long received){}
 private static final Map<Integer,Clash> CLASHES=new HashMap<>();
 private static ClientLevel level;
 private PlayerUzumakiRenderer(){}
 private static void ensureWorld(){var next=Minecraft.getInstance().level;if(next!=level){CASTS.clear();CLASHES.clear();level=next;}}
 public static void receive(UzumakiState state){
  ensureWorld();if(level==null)return;
  Cast previous=CASTS.get(state.owner());
  if(state.age()<0){if(previous!=null&&previous.state.session()==state.session())CASTS.remove(state.owner());return;}
  if(state.age()>SustainedBeam.USE_TICKS||state.kind()<0||state.kind()>1||state.clashAge()<-1||state.clashAge()>40
    ||!finite(state.origin())||!finite(state.direction())||!finite(state.contact())||!finite(state.otherOrigin())
    ||!Double.isFinite(state.length())||state.length()<0||state.length()>SustainedBeam.RANGE+2
    ||state.direction().lengthSqr()<.8||state.direction().lengthSqr()>1.2)return;
  if(previous!=null&&previous.state.session()==state.session()&&previous.state.age()>state.age())return;
  if(CASTS.size()>=64&&!CASTS.containsKey(state.owner()))CASTS.remove(CASTS.keySet().iterator().next());
  CASTS.put(state.owner(),new Cast(state,ClientEffectClock.ticks()));
  if(state.clashId()>0&&state.clashAge()>=0){
   if(CLASHES.size()>=32&&!CLASHES.containsKey(state.clashId()))CLASHES.remove(CLASHES.keySet().iterator().next());
   CLASHES.put(state.clashId(),new Clash(state.clashId(),state.clashAge(),state.kind()==1?state.origin():state.otherOrigin(),
     state.kind()==0?state.origin():state.otherOrigin(),state.contact(),ClientEffectClock.ticks()));
  }
 }
 private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  ensureWorld();long now=ClientEffectClock.ticks();
  CASTS.values().removeIf(c->now-c.received>30);
  CLASHES.values().removeIf(c->c.age+now-c.received>=40);
 }
 record Visual(float age,Vec3 origin,Vec3 direction,double length,double scale,boolean maximum,int kind,boolean sustained){
  Visual(float age,Vec3 origin,Vec3 direction,double length,double scale,boolean maximum){this(age,origin,direction,length,scale,maximum,0,false);}
 }
 static List<Clash> clashes(float partial,Vec3 camera){
  ensureWorld();List<Clash> result=new ArrayList<>();
  for(Clash c:CLASHES.values()){
   float age=c.age+ClientEffectClock.ticks()-c.received+partial;
   if(age<40&&camera.distanceToSqr(c.contact)<192*192)result.add(new Clash(c.id,age,c.rika,c.geto,c.contact,c.received));
  }
  return result;
 }
 static void camera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event,float partial,float scale){
  double power=0;dev.jujutsuvfx.core.YutaCinematicFraming.Shake strongest=null;
  for(Clash c:clashes(partial,event.getCamera().getPosition())){
   int phase=c.age<12?dev.jujutsuvfx.core.YutaGetoBeat.CLASH:dev.jujutsuvfx.core.YutaGetoBeat.BREAKTHROUGH;
   double age=c.age<12?c.age:c.age-12;
   var shake=dev.jujutsuvfx.core.YutaCinematicFraming.shake(phase,age,event.getCamera().getPosition().distanceTo(c.contact),scale);
   double value=shake.yaw()*shake.yaw()+shake.pitch()*shake.pitch()+shake.roll()*shake.roll();
   if(value>power){power=value;strongest=shake;}
  }
  if(strongest!=null){event.setYaw(event.getYaw()+(float)strongest.yaw());event.setPitch(event.getPitch()+(float)strongest.pitch());event.setRoll(event.getRoll()+(float)strongest.roll());}
 }
 /** Every Geto blast/Maximum and player Maximum in range, shared by the geometry and volumetric passes. */
 static List<Visual> visuals(float partial,Vec3 camera){
  ensureWorld();List<Visual> visuals=new ArrayList<>();if(level==null)return visuals;
  // Query the world directly: a visible beam must not depend on the caster's model being on screen.
  for(var entity:level.entitiesForRendering())if(entity instanceof GetoCat cat&&(cat.action()==GetoCat.UZUMAKI||cat.action()==GetoCat.BLAST)){
   Vec3 origin=cat.spellOrigin(),axis=cat.beamDirection();if(axis.lengthSqr()<.5)axis=cat.getLookAngle();
   if(visuals.size()<24&&camera.distanceToSqr(origin)<=192*192)visuals.add(new Visual(cat.vortexAge(partial),origin,axis,cat.beamLength(),cat.vortexScale(),cat.action()==GetoCat.UZUMAKI));
  }
  for(Cast cast:CASTS.values()){
   var s=cast.state;float age=s.age()+ClientEffectClock.ticks()-cast.received+partial;
   if(s.clashId()!=0)continue;
   Vec3 origin=s.origin(),axis=s.direction();double length=s.length();
   if(!s.fired())age=Math.min(GetoVortexMotion.FIRE-.001f,age);
   {
    if(level.getEntity(s.actor()) instanceof Player player&&player.getUUID().equals(s.owner())){
     if(!player.isAlive()||player.isSpectator())continue;
     axis=player.getViewVector(partial).normalize();origin=player.getEyePosition(partial).add(axis.scale(1.6)).add(0,-.15,0);
     var clip=level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(partial),origin.add(axis.scale(SustainedBeam.RANGE)),
       net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
     length=Math.max(0,Math.min(SustainedBeam.RANGE,clip.getLocation().subtract(origin).dot(axis)));
     if(length==0)origin=clip.getLocation();
    }
   }
   if(visuals.size()<24&&camera.distanceToSqr(origin)<=192*192)visuals.add(new Visual(age,origin,axis,length,1,true,s.kind(),true));
  }
  return visuals;
 }
 @SubscribeEvent public static void render(RenderLevelStageEvent event){
  // Iris masks unknown materials during entities/particles. AFTER_LEVEL runs after its final composite.
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
  ensureWorld();if(level==null||(!GetoVortexMaterial.ready()&&!YutaGetoFinaleMaterial.ready()))return;
  float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
  Vec3 camera=event.getCamera().getPosition();
  // The volumetric pass owns every vortex when available; geometry remains the fallback.
  List<Visual> visuals=YutaGetoClashEffects.ready()?List.of():visuals(partial,camera);
  List<Clash> contacts=clashes(partial,camera);
  var waves=new ArrayList<net.minecraft.nbt.CompoundTag>();
  var duels=new ArrayList<net.minecraft.nbt.CompoundTag>();
  for(var entity:level.entitiesForRendering())if(entity instanceof dev.jujutsuvfx.entity.YutaCat cat&&cat.isAlive()){
   var duel=cat.duelState();
   if(duels.size()<4&&YutaGetoFinaleRenderer.active(duel)
     &&(level.getEntity(duel.getInt("Peer")) instanceof GetoCat peer&&peer.isAlive()||duel.getInt("Phase")>=dev.jujutsuvfx.core.YutaGetoBeat.AFTERMATH)
     &&camera.distanceToSqr(YutaGetoFinaleRenderer.anchor(duel,"M"))<=192*192)duels.add(duel);
   var state=cat.rikaState();
   if(state.getInt("Action")==dev.jujutsuvfx.core.RikaSupportClock.WAVE&&state.contains("WaveX"))waves.add(state);
  }
  if(visuals.isEmpty()&&waves.isEmpty()&&duels.isEmpty()&&contacts.isEmpty())return;
  Matrix4f viewProjection=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
  PoseStack pose=new PoseStack();
  YutaGetoFinaleMaterial.draw(viewProjection,true,out->{
   for(Visual v:visuals)if(v.maximum)drawBeam(v,camera,true,out);
   for(Clash c:contacts)drawClash(c,camera,true,out);
   for(var state:duels)YutaGetoFinaleRenderer.draw(state,level.getGameTime(),partial,camera,true,out);
  });
  GetoVortexMaterial.draw(viewProjection,out->{
   for(Visual v:visuals)if(!v.maximum){
    Vec3 relative=v.origin.subtract(camera);pose.pushPose();
    try{pose.translate(relative.x,relative.y,relative.z);pose.scale((float)v.scale,(float)v.scale,(float)v.scale);
     GetoVortexRenderer.draw(v.age,Vec3.ZERO,v.direction,v.length/v.scale,pose,out);
    }finally{pose.popPose();}
   }
   for(var state:waves)RikaWaveRenderer.draw(state,level.getGameTime(),partial,camera,pose,out);
  });
  YutaGetoFinaleMaterial.draw(viewProjection,false,out->{
   for(Visual v:visuals)if(v.maximum)drawBeam(v,camera,false,out);
   for(Clash c:contacts)drawClash(c,camera,false,out);
   for(var state:duels)YutaGetoFinaleRenderer.draw(state,level.getGameTime(),partial,camera,false,out);
  });
 }
 private static YutaGetoFinaleGeometry.P p(Vec3 v){return new YutaGetoFinaleGeometry.P(v.x,v.y,v.z);}
 private static void drawBeam(Visual v,Vec3 camera,boolean solid,com.mojang.blaze3d.vertex.VertexConsumer out){
  if(v.sustained)YutaGetoFinaleGeometry.sustained(v.kind,v.age,v.length,p(v.origin),p(v.direction),p(camera),solid,YutaGetoFinaleRenderer.sink(out,camera));
  else YutaGetoFinaleRenderer.maximum(v.age,v.origin,v.direction,v.length,v.scale,camera,solid,out);
 }
 private static void drawClash(Clash c,Vec3 camera,boolean solid,com.mojang.blaze3d.vertex.VertexConsumer out){
  YutaGetoFinaleGeometry.playerClash(c.age,p(c.rika),p(c.geto),p(c.contact),p(camera),dev.jujutsuvfx.config.VfxConfig.flashes(),solid,
    YutaGetoClashEffects.ready(),YutaGetoFinaleRenderer.sink(out,camera));
 }
}
