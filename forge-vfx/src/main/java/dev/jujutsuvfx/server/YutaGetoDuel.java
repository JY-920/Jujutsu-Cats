package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.core.YutaGetoBeat;
import dev.jujutsuvfx.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import java.util.*;
import static dev.jujutsuvfx.core.YutaGetoBeat.*;

/** Pair-owned screenplay; ordinary AI still runs during every combat beat and the partner's ring/draw. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class YutaGetoDuel {
 private static final String HISTORY="JujutsuYutaGetoHistory";
 private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
 private static final class State {
  final Map<UUID,Duel> actors=new HashMap<>();
  int serial;
 }
 private static final class Duel {
  final YutaCat yuta;final GetoCat geto;final ServerLevel level;final int serial;
  int phase=WAIT;long start,lastTick=Long.MIN_VALUE;float progress;boolean terminal,closing,impact,cancelled,domainUsed;
  Vec3 y,g,r,m,e,l,axis;
  Duel(State state,YutaCat yuta,GetoCat geto){
   this.yuta=yuta;this.geto=geto;level=(ServerLevel)yuta.level();serial=++state.serial;
   CompoundTag a=memory(yuta,geto),b=memory(geto,yuta);
   int saved=Math.max(a.getInt("Stage"),b.getInt("Stage"));
   phase=saved>0?YutaGetoBeat.resume(saved):WAIT;
   progress=Math.max(a.getFloat("Progress"),b.getFloat("Progress"));
   domainUsed=a.getBoolean("Domain")||b.getBoolean("Domain");
   terminal=Math.min(yuta.getHealth(),geto.getHealth())<=1.1f;
   int savedAge=a.getInt("Stage")==saved?a.getInt("Age"):0;
   if(b.getInt("Stage")==saved)savedAge=Math.max(savedAge,b.getInt("Age"));
   start=level.getGameTime()-YutaGetoBeat.resumeAge(saved,savedAge);anchors(this);
  }
 }
 private YutaGetoDuel(){}
 private static Duel find(LivingEntity actor){
  if(actor==null||actor.level().isClientSide)return null;
  State state=STATES.get(actor.getServer());return state==null?null:state.actors.get(actor.getUUID());
 }
 public static boolean manages(LivingEntity actor){
  Duel d=find(actor);return d!=null&&!d.closing&&VfxConfig.cinematic();
 }
 public static int phase(LivingEntity actor){Duel d=find(actor);return d==null?NONE:d.phase;}
 private static CompoundTag memory(LivingEntity actor,LivingEntity peer){
  return actor.getPersistentData().getCompound(HISTORY).getCompound(peer.getStringUUID());
 }
 /** Only the pair that has finished its story stops reacquiring each other; unrelated combat is unchanged. */
 public static boolean suppressed(LivingEntity actor,LivingEntity peer){
  return actor!=null&&peer!=null&&!actor.level().isClientSide
    &&(memory(actor,peer).getBoolean("Complete")||memory(peer,actor).getBoolean("Complete"));
 }
 private static void remember(Duel d,boolean complete){
  for(LivingEntity actor:List.of(d.yuta,d.geto)){
   LivingEntity peer=actor==d.yuta?d.geto:d.yuta;
   CompoundTag history=actor.getPersistentData().getCompound(HISTORY).copy(),entry=new CompoundTag();
   entry.putInt("Stage",d.phase);entry.putFloat("Progress",d.progress);entry.putBoolean("Complete",complete);entry.putBoolean("Cancelled",d.cancelled);entry.putInt("Age",age(d));entry.putBoolean("Domain",d.domainUsed);
   entry.putLong("Updated",d.level.getGameTime());history.put(peer.getStringUUID(),entry);
   // Bounded history, evict the oldest unrelated pair rather than a random current record.
   while(history.getAllKeys().size()>16){
    String oldest=history.getAllKeys().stream().filter(k->!k.equals(peer.getStringUUID()))
      .min(Comparator.comparingLong(k->history.getCompound(k).getLong("Updated"))).orElse(null);
    if(oldest==null)break;history.remove(oldest);
   }
   actor.getPersistentData().put(HISTORY,history);
  }
 }
 private static LivingEntity principal(LivingEntity target,ServerLevel level){
  if(target instanceof CursedSpirit spirit&&spirit.ownerId()!=null&&level.getEntity(spirit.ownerId()) instanceof GetoCat geto)return geto;
  return target;
 }
 private static boolean outside(Mob actor,LivingEntity expected,ServerLevel level){
  LivingEntity target=principal(actor.getTarget(),level);
  return target!=null&&target.isAlive()&&target!=expected;
 }
 private static boolean valid(Duel d){
  return VfxConfig.cinematic()&&d.yuta.isAlive()&&d.geto.isAlive()&&!d.yuta.isRemoved()&&!d.geto.isRemoved()
    &&d.yuta.level()==d.level&&d.geto.level()==d.level&&!d.yuta.isNoAi()&&!d.geto.isNoAi()
    &&d.level.getEntity(d.yuta.getUUID())==d.yuta&&d.level.getEntity(d.geto.getUUID())==d.geto
    &&d.yuta.distanceToSqr(d.geto)<128*128&&!outside(d.yuta,d.geto,d.level)&&!outside(d.geto,d.yuta,d.level);
 }
 private static Duel discover(LivingEntity actor){
  if(!VfxConfig.cinematic()||!(actor.level() instanceof ServerLevel level)||!(actor instanceof Mob mob)
    ||!actor.isAlive()||mob.isNoAi())return null;
  LivingEntity target=principal(mob.getTarget(),level);
  YutaCat y=actor instanceof YutaCat cat?cat:target instanceof YutaCat cat?cat:null;
  GetoCat g=actor instanceof GetoCat cat?cat:target instanceof GetoCat cat?cat:null;
  if(y==null||g==null||!y.isAlive()||!g.isAlive()||y.level()!=g.level()||y.isNoAi()||g.isNoAi()
    ||suppressed(y,g)||memory(y,g).getBoolean("Cancelled")||memory(g,y).getBoolean("Cancelled")||outside(y,g,level)||outside(g,y,level)||y.distanceToSqr(g)>128*128
    ||y.behavior()!=YutaCat.AGGRESSIVE||YutaDomain.active(y))return null;
  State state=STATES.computeIfAbsent(level.getServer(),s->new State());
  if(state.actors.containsKey(y.getUUID())||state.actors.containsKey(g.getUUID()))return null;
  Duel d=new Duel(state,y,g);state.actors.put(y.getUUID(),d);state.actors.put(g.getUUID(),d);
  y.setTarget(g);g.setTarget(y);
  if(d.phase>=SWARM)y.setBladeDrawn(true);
  if(d.phase==COOP||d.phase==RANGED)y.cinematicRika(true);
  enter(d);publish(d);return d;
 }
 /** Called after native tick, before attack selection. Returns true only when this actor owns a staged action. */
 public static boolean control(LivingEntity actor){
  Duel d=find(actor);if(d==null)d=discover(actor);
  if(d==null)return false;
  advance(d);if(d.closing)return false;
  int age=age(d);boolean yuta=actor==d.yuta;
  if(YutaGetoBeat.combat(d.phase)||d.phase==DRAW||d.phase==RING)return false;
  if(d.phase==SEPARATE){
   // Yuta holds his ground. Geto makes one fast backward escape instead of both actors drifting apart.
   actorMotion(actor,yuta||age>=28?0:GetoCat.RETREAT,age);
   face(actor,yuta?d.geto.position():d.yuta.position());
   if(!yuta){
    Vec3 away=d.geto.position().subtract(d.yuta.position()).multiply(1,0,1);
    walk(d.geto,away,YutaGetoBeat.separationStep(age,away.length()));
   }
   return true;
  }
  if(d.phase==RETREAT){
   actorMotion(actor,yuta?0:106,age);
   if(yuta)face(actor,d.geto.position());
   else {
    face(actor,age<34?d.y:d.geto.position().add(d.axis));
    if(age>=52)walk(d.geto,d.axis,.28*dev.jujutsuvfx.core.YutaBladeMotion.phase(age,52,64));
   }
   return true;
  }
  int pose=0,poseAge=age;
  if(yuta){
   if(d.phase==VOW)pose=23;
   else if(d.phase>=CHARGE&&d.phase<=AFTERMATH){
    pose=24;poseAge+=d.phase>=CLASH?duration(CHARGE):0;
    poseAge+=d.phase>=BREAKTHROUGH?duration(CLASH):0;
    poseAge+=d.phase>=AFTERMATH?duration(BREAKTHROUGH):0;
   }
  }else{
   if(d.phase==VOW){pose=104;poseAge=0;}
   if(d.phase==CHARGE||d.phase==CLASH){pose=104;poseAge+=d.phase==CLASH?duration(CHARGE):0;}
   if(d.phase==BREAKTHROUGH||d.phase==AFTERMATH){pose=105;poseAge+=d.phase==AFTERMATH?duration(BREAKTHROUGH):0;}
  }
  actorMotion(actor,pose,poseAge);
  face(actor,yuta?d.g:d.y);
  return true;
 }
 private static int age(Duel d){return (int)Math.max(0,d.level.getGameTime()-d.start);}
 private static void actorMotion(LivingEntity actor,int pose,int age){
  if(actor instanceof YutaCat y)y.cinematicPose(pose,age);else if(actor instanceof GetoCat g)g.cinematicPose(pose,age);
 }
 private static void face(LivingEntity actor,Vec3 toward){
  Vec3 v=toward.subtract(actor.position());
  if(v.horizontalDistanceSqr()<.001)return;
  float desired=(float)(Math.toDegrees(Math.atan2(v.z,v.x))-90);
  float yaw=net.minecraft.util.Mth.rotLerp(.32f,actor.getYRot(),desired);
  actor.setYRot(yaw);actor.yBodyRot=yaw;actor.setYHeadRot(yaw);
  if(actor instanceof Mob mob)mob.getLookControl().setLookAt(toward.x,toward.y+1,toward.z,30,30);
 }
 private static void walk(Mob actor,Vec3 axis,double speed){
  if(axis.lengthSqr()<.01||speed<=0)return;
  Vec3 step=axis.multiply(1,0,1).normalize().scale(speed);
  // Try a slight arc around an obstruction. No teleport, terrain damage, liquid crossing or forced falling.
  for(double angle:new double[]{0,.5,-.5,1,-1}){
   Vec3 move=step.yRot((float)angle);BlockPos foot=BlockPos.containing(actor.position().add(move).add(0,-.5,0));
   if(!actor.level().hasChunkAt(foot)||!actor.level().getFluidState(foot).isEmpty()
      ||actor.level().getBlockState(foot).getCollisionShape(actor.level(),foot).isEmpty()
      ||!actor.level().noCollision(actor,actor.getBoundingBox().move(move)))continue;
   actor.move(MoverType.SELF,move);actor.hasImpulse=true;return;
  }
 }
 private static void anchors(Duel d){
  d.y=d.yuta.position();d.g=d.geto.position();
  d.axis=d.g.subtract(d.y).multiply(1,0,1).normalize();
  if(d.axis.lengthSqr()<.01)d.axis=new Vec3(0,0,1);
  Vec3 side=new Vec3(-d.axis.z,0,d.axis.x);
  d.r=groundAnchor(d,d.y.subtract(d.axis.scale(3.3)).add(side.scale(2.0)));
  // Keep both source heads behind and above the cats, leaving their faces and gestures visible.
  d.l=d.r.add(0,3.0,0);
  d.e=d.g.add(d.axis.scale(3.0)).add(0,4.4,0);
  double fraction=d.phase==CLASH?clashFraction(age(d)):d.phase>=BREAKTHROUGH?breakthroughFraction(age(d)):.5;
  d.m=d.l.lerp(d.e,fraction);
 }
 private static Vec3 groundAnchor(Duel d,Vec3 wanted){
  // Rika is a render companion, but still needs a real support surface for her taper/tail.
  for(Vec3 candidate:List.of(wanted,d.y.subtract(d.axis.scale(2.4)),d.y.add(d.axis.cross(new Vec3(0,1,0)).scale(2.5)))){
   BlockPos top=BlockPos.containing(candidate.add(0,2,0));
   if(!d.level.hasChunkAt(top))continue;
   for(int drop=0;drop<=5;drop++){
    BlockPos support=top.below(drop);var shape=d.level.getBlockState(support).getCollisionShape(d.level,support);
    if(shape.isEmpty()||!d.level.getFluidState(support).isEmpty())continue;
    double y=support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
    AABB space=new AABB(candidate.x-.45,y+.03,candidate.z-.45,candidate.x+.45,y+2.7,candidate.z+.45);
    if(d.level.noCollision(space))return new Vec3(candidate.x,y,candidate.z);
   }
  }
  return d.y;
 }
 private static void advance(Duel d){
  if(d.closing||d.lastTick==d.level.getGameTime())return;
  d.lastTick=d.level.getGameTime();
  // Geto fell to the final exchange: Yuta and the aftermath finish alone, then the pair closes.
  if(d.impact&&!d.geto.isAlive()){
   if(!d.yuta.isAlive()||d.yuta.level()!=d.level){close(d,true);return;}
   if(age(d)>=duration(AFTERMATH)){d.phase=DONE;remember(d,true);close(d,true);return;}
   publish(d);return;
  }
  if(!valid(d)){
   boolean unloaded=d.level.getEntity(d.yuta.getUUID())!=d.yuta||d.level.getEntity(d.geto.getUUID())!=d.geto;
   if(unloaded)close(d,false);else abort(d);
   return;
  }
  boolean yutaHeld=DomainFreeze.hold(d.yuta),getoHeld=DomainFreeze.hold(d.geto);
  if(DomainFreeze.hasDomainLock(d.yuta)||DomainFreeze.hasDomainLock(d.geto)){abort(d);return;}
  // Yuta's own speech shares DomainFreeze with domains. Keep its three-second stun and
  // the current scene, instead of cancelling the entire pair when a normal attack lands.
  if(d.phase==COOP||d.phase==RANGED)d.yuta.cinematicRika(true);
  if(yutaHeld||getoHeld){d.start++;publish(d);return;}
  d.terminal|=Math.min(d.yuta.getHealth(),d.geto.getHealth())<=1.1f;
  d.progress=YutaGetoBeat.progress(d.progress,d.yuta.getHealth(),d.yuta.getMaxHealth(),d.geto.getHealth(),d.geto.getMaxHealth());
  // The half-way domain: while Yuta's own domain stands, the combat beat holds instead of advancing.
  if(YutaDomain.active(d.yuta)){
   if(!d.domainUsed){d.domainUsed=true;remember(d,false);}
   if(YutaGetoBeat.combat(d.phase)){anchors(d);publish(d);return;}
  }
  if(d.phase<AFTERMATH){
   if(d.yuta.getTarget()==null)d.yuta.setTarget(d.geto);
   if(d.geto.getTarget()==null)d.geto.setTarget(d.yuta);
  }
  int next=YutaGetoBeat.next(d.phase,age(d),d.progress,d.terminal);
  if(next!=d.phase){
   if(d.phase==DRAW)d.yuta.setBladeDrawn(true);
   if(d.phase==RING)d.yuta.cinematicRika(true);
   if(d.phase==BREAKTHROUGH)impact(d);
   d.phase=next;d.start=d.level.getGameTime();
   if(d.phase==DONE){remember(d,true);close(d,true);return;}
   enter(d);remember(d,d.phase>=AFTERMATH);
  }
  anchors(d);publish(d);
  if(d.lastTick%100==0)remember(d,d.phase>=AFTERMATH);
 }
 private static void enter(Duel d){
  switch(d.phase){
   case DRAW->d.yuta.beginCinematicDraw();
   case SWARM->d.geto.cinematicSwarm();
   case RING->d.yuta.cinematicRing();
   case RANGED->d.geto.cinematicRanged();
   case SEPARATE->{
    d.yuta.cinematicRelease();d.geto.cinematicRelease();d.yuta.cinematicRika(false);
    d.geto.cinematicRecall();
   }
   case AFTERMATH->{
    d.yuta.cinematicRika(false);d.geto.cinematicRecall();clearTargets(d);
   }
   default->{}
  }
 }
 private static void clearTargets(Duel d){
  d.yuta.setTarget(null);d.geto.setTarget(null);
  d.yuta.setLastHurtByMob(null);d.geto.setLastHurtByMob(null);
 }
 private static void impact(Duel d){
  if(d.impact)return;d.impact=true;
  // Rika's discharge breaks through: Geto dies to it, credited to Yuta.
  remember(d,true);
  Vec3 at=d.geto.position().add(0,1,0);
  d.geto.invulnerableTime=0;
  if(!d.geto.hurt(d.level.damageSources().indirectMagic(d.yuta,d.yuta),Float.MAX_VALUE)||d.geto.isAlive())d.geto.kill();
  d.level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,at.x,at.y,at.z,32,1.2,.6,1.2,.16);
  d.level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,at.x,at.y,at.z,24,.7,1,.7,.2);
 }
 private static void put(CompoundTag tag,String key,Vec3 p){tag.putDouble(key+"X",p.x);tag.putDouble(key+"Y",p.y);tag.putDouble(key+"Z",p.z);}
 private static void publish(Duel d){
  CompoundTag t=new CompoundTag();t.putInt("Phase",d.phase);t.putLong("Start",d.start);t.putInt("Serial",d.serial);
  t.putFloat("Progress",d.progress);put(t,"Y",d.y);put(t,"G",d.g);put(t,"R",d.r);put(t,"M",d.m);put(t,"E",d.e);put(t,"L",d.l);
  t.putInt("Peer",d.geto.getId());d.yuta.setDuelState(t);
  t=t.copy();t.putInt("Peer",d.yuta.getId());d.geto.setDuelState(t);
 }
 /** /kill, void and outside interference escape the stage protection immediately. */
 public static boolean acceptsDamage(LivingEntity actor,DamageSource source){
  Duel d=find(actor);if(d==null||d.closing||!VfxConfig.cinematic())return true;
  if(d.impact&&actor==d.geto)return true;
  if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||source.is(DamageTypes.FELL_OUT_OF_WORLD)){
   abort(d);return true;
  }
  Entity cause=source.getEntity();
  if(cause instanceof LivingEntity attacker){
   LivingEntity owner=principal(attacker,d.level),peer=actor==d.yuta?d.geto:d.yuta;
   if(owner!=peer&&owner!=actor){abort(d);return true;}
  }
  // Health still falls during real exchanges. Only staged casts deflect the partner's stray old projectiles.
  return d.phase<SEPARATE||cause==null;
 }
 public static float limitDamage(LivingEntity actor,DamageSource source,float amount){
  if(!acceptsDamage(actor,source))return 0;
  Duel d=find(actor);if(d==null||d.closing||!VfxConfig.cinematic())return amount;
  if(d.impact&&actor==d.geto)return amount;
  return Math.min(amount,Math.max(0,actor.getHealth()-1));
 }
 public static void cancel(LivingEntity actor){Duel d=find(actor);if(d!=null&&!(d.impact&&actor==d.geto))abort(d);}
 /** Half-way stage of the pair's average health loss: Yuta opens his domain once in the screenplay. */
 public static boolean domainCue(YutaCat yuta){
  Duel d=find(yuta);
  return d!=null&&!d.closing&&!d.domainUsed&&VfxConfig.cinematic()&&YutaGetoBeat.combat(d.phase)&&d.progress>=.5f&&!YutaDomain.active(yuta);
 }
 /** External interference ends this pair's screenplay, not its ordinary combat eligibility. */
 private static void abort(Duel d){d.cancelled=true;close(d,false);}
 private static void close(Duel d,boolean complete){
  if(d.closing)return;d.closing=true;
  remember(d,complete||d.phase>=AFTERMATH);
  State s=STATES.get(d.level.getServer());if(s!=null){s.actors.remove(d.yuta.getUUID());s.actors.remove(d.geto.getUUID());}
  d.yuta.setDuelState(new CompoundTag());d.geto.setDuelState(new CompoundTag());
  d.yuta.cinematicRelease();d.geto.cinematicRelease();
  if(complete){clearTargets(d);d.geto.cinematicRecall();}
 }
 @SubscribeEvent public static void tick(ServerTickEvent event){
  if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
  State s=STATES.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());if(s!=null)for(Duel d:new HashSet<>(s.actors.values()))advance(d);
 }
 @SubscribeEvent public static void stopping(ServerStoppingEvent event){
  State s=STATES.get(event.getServer());if(s!=null)for(Duel d:new HashSet<>(s.actors.values()))close(d,false);
  STATES.remove(event.getServer());
 }
}
