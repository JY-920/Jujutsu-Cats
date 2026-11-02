package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.*;
import dev.jujutsuvfx.entity.YutaCat;
import dev.jujutsuvfx.network.CatSpellState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import static dev.jujutsuvfx.core.CatSpell.*;

/** Yuta's own casting adapter for the shared flight, terrain, collision, damage and VFX engines. */
public final class YutaCopiedTechniques {
 private static final Map<YutaCat,List<Cast>> ACTIVE=new WeakHashMap<>();
 private YutaCopiedTechniques(){}
 private static final class Cast {
  final YutaCat cat;final ServerLevel level;final int selection,kind,id;final boolean large;
  final Runnable released;final Set<UUID> hits=new HashSet<>();final Map<UUID,Integer> nextHit=new HashMap<>();
  UUID target;Vec3 point,origin,direction;int age,stage,phase,stalled;double traveled;boolean parked;
  Cast(YutaCat cat,LivingEntity target,int selection,Runnable released){
   this.cat=cat;level=(ServerLevel)cat.level();this.selection=selection;this.released=released;this.target=target.getUUID();
   kind=switch(selection){case YutaCopiedDeck.BLUE->BLUE;case YutaCopiedDeck.RED->RED;
    case YutaCopiedDeck.SMALL_PURPLE,YutaCopiedDeck.LARGE_PURPLE->PURPLE;case YutaCopiedDeck.FUGA->FURNACE;
    case YutaCopiedDeck.DISMANTLE->DISMANTLE;case YutaCopiedDeck.CLEAVE->CLEAVE;default->-1;};
   large=selection==YutaCopiedDeck.LARGE_PURPLE;id=PlayerSpellSync.nextId(cat.getServer());
   phase=kind==PURPLE?PREPARE:CHARGE;direction=aim(cat,target);face(cat,direction);point=origin=muzzle();
  }
  int launchAge(){return kind<0?CursedSpeechShape.RELEASE:CatSpell.launchTick(kind,large);}
  int poseAge(){return kind<0?CursedSpeechShape.DURATION:CatSpell.poseTicks(kind,large);}
  float radius(){return kind==BLUE?8:kind==PURPLE?(large?3:1):kind==FURNACE?12:LimitlessTechnique.shotRadius(RED,10);}
  Vec3 muzzle(){return kind==BLUE?LimitlessTechnique.blueMuzzle(cat):kind==PURPLE?LimitlessTechnique.purpleMuzzle(cat,radius(),1,direction)
    :kind==FURNACE?FurnaceFlight.bow(cat,1):kind<0?CursedSpeech.mouth(cat):LimitlessTechnique.redMuzzle(cat,1,direction,1);}
  void send(){if(kind>=0&&kind<DISMANTLE)new CatSpellState(id,cat.getId(),cat.getUUID(),kind,large,phase,age,stage,
    Math.max(0,kind==BLUE?200-age:poseAge()-age),radius(),point,direction,origin,0).broadcast(level);}
 }
 public static boolean busy(YutaCat cat){return ACTIVE.getOrDefault(cat,List.of()).stream().anyMatch(c->c.age<c.poseAge());}
 public static void cancel(YutaCat cat){List<Cast> casts=ACTIVE.remove(cat);if(casts!=null)for(Cast c:casts){c.phase=END;c.send();}}
 static boolean viable(YutaCat cat,LivingEntity target,int selection){
  double range=selection==YutaCopiedDeck.SPEECH?CursedSpeechShape.RANGE-1:selection==YutaCopiedDeck.DISMANTLE?SukunaSlashSkills.REACH-1
    :selection==YutaCopiedDeck.RED?LimitlessTechnique.RED_RANGE-2:64;
  return target!=null&&cat.canFight(target)&&target.level()==cat.level()&&cat.distanceToSqr(target)<=range*range&&cat.getSensing().hasLineOfSight(target);
 }
 static boolean start(YutaCat cat,LivingEntity target,int selection,Runnable released){
  if(busy(cat)||!viable(cat,target,selection))return false;
  Cast c=new Cast(cat,target,selection,released);
  cat.prepareCopiedTechnique(c.kind<0?BARRAGE:c.kind,c.large,c.poseAge());
  ACTIVE.computeIfAbsent(cat,key->new ArrayList<>()).add(c);
  if(c.kind<0)CursedSpeech.charge(cat,target);else c.send();return true;
 }
 static void tick(YutaCat cat){
  List<Cast> casts=ACTIVE.get(cat);if(casts==null||DomainFreeze.hold(cat))return;
  for(Cast c:List.copyOf(casts)){
   if(!cat.isAlive()||cat.isNoAi()||cat.isInSittingPose()||cat.level()!=c.level){cancel(cat);return;}
   c.age++;c.stage++;
   LivingEntity target=c.level.getEntity(c.target) instanceof LivingEntity e&&cat.canFight(e)?e:null;
   if(c.phase<=CHARGE){
    if(!viable(cat,target,c.selection)){c.phase=END;c.send();casts.remove(c);continue;}
    c.direction=aim(cat,target);face(cat,c.direction);c.point=c.origin=c.muzzle();
    if(c.kind==PURPLE&&c.phase==PREPARE&&c.age>=CatSpell.prepareTicks(c.kind,c.large)){c.phase=CHARGE;c.stage=0;}
    if(c.age>=c.launchAge()){
     if(c.kind==CLEAVE&&!SukunaSlashSkills.reservePhysics(cat.getServer(),2)){c.age=c.launchAge()-1;continue;}
     c.phase=FLIGHT;c.stage=0;c.released.run();
     if(c.kind<0){CursedSpeech.releaseCopied(cat,target);impact(c);}
     else if(c.kind==DISMANTLE){dismantle(c);impact(c);}
     else if(c.kind==CLEAVE){cleave(c,target);impact(c);}
     else if(c.kind==FURNACE){var hit=LimitlessTechnique.trace(c.level,cat,cat.getEyePosition(),c.point);if(hit.hit()){c.point=hit.point();furnaceImpact(c);}}
     else if(c.kind!=BLUE)shotHits(c,cat.getEyePosition(),c.point);
    }
   }else if(c.phase==FLIGHT){
    if(c.kind==BLUE)blue(c,target);else if(c.kind==FURNACE)furnace(c);else shot(c);
   }
   if(!ACTIVE.containsKey(cat))return;
   if(c.age<c.poseAge()){cat.getNavigation().stop();cat.setDeltaMovement(cat.getDeltaMovement().multiply(0,1,0));}
   if(c.phase==IMPACT&&c.stage>=afterglow(c)&&c.age>=c.poseAge()){
    c.phase=END;c.send();casts.remove(c);
   }else if(c.age%2==0)c.send();
  }
  if(casts.isEmpty())ACTIVE.remove(cat);
 }
 private static boolean current(Cast c){return c.cat.isAlive()&&ACTIVE.getOrDefault(c.cat,List.of()).contains(c);}
 private static void blue(Cast c,LivingEntity target){
  if(!c.parked){
   if(target!=null)c.direction=LimitlessTechnique.steerBlue(c.direction,target.getBoundingBox().getCenter().subtract(c.point));
   if(c.traveled>=32||target!=null&&c.point.distanceToSqr(target.getBoundingBox().getCenter())<.64&&c.stage>3)c.parked=true;
   else{Vec3 old=c.point;c.point=BlueTerrain.sweep(c.cat,old,old.add(c.direction.scale(LimitlessTechnique.blueSpeed(old.distanceTo(c.cat.getEyePosition()))/20)),LimitlessTechnique.blueCoreRadius(c.radius()),()->current(c));
    double moved=old.distanceTo(c.point);c.traveled+=moved;if(moved<.002&&++c.stalled>8)c.parked=true;}
  }
  int count=0;
  for(LivingEntity targetEntity:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(c.point,c.point).inflate(c.radius()),c.cat::canFight)){
   if(++count>24||!current(c))break;
   Vec3 delta=c.point.subtract(targetEntity.getBoundingBox().getCenter());double distance=delta.length();
   if(distance>c.radius()||!visible(c,c.point,targetEntity))continue;
   CatAdaptation.touch(targetEntity,CatAdaptation.Phenomenon.BLUE);float response=CatAdaptation.controlScale(targetEntity,CatAdaptation.Phenomenon.BLUE);
   if(response>0&&!DomainFreeze.hold(targetEntity)){
    double strength=Math.max(.018,Math.min(.15,(1-distance/c.radius())*.15))*response;
    targetEntity.setDeltaMovement(targetEntity.getDeltaMovement().scale(1-.15*response).add(delta.normalize().scale(strength)));
    targetEntity.hasImpulse=true;targetEntity.hurtMarked=true;
   }
   if(distance<LimitlessTechnique.blueCoreRadius(c.radius())+1.4&&c.age>=c.nextHit.getOrDefault(targetEntity.getUUID(),0)){
    c.nextHit.put(targetEntity.getUUID(),c.age+8);TechniqueDamageRules.hurt(targetEntity,source(c),6,TechniqueDamage.BLUE);
   }
  }
  if(c.age>=200)impact(c);
 }
 private static void shot(Cast c){
  double range=LimitlessTechnique.shotRange(c.kind,c.large),step=Math.min(LimitlessTechnique.shotSpeed(c.kind)/20,Math.max(0,range-c.traveled));
  Vec3 old=c.point;c.point=BlueTerrain.pierce(c.cat,old,old.add(c.direction.scale(step)),c.radius(),()->current(c));
  double moved=old.distanceTo(c.point);c.traveled+=moved;c.stalled=moved<.001?c.stalled+1:0;
  shotHits(c,old,c.point);if(c.traveled>=range-.01||c.stalled>30||c.stage>140)impact(c);
 }
 private static void shotHits(Cast c,Vec3 start,Vec3 end){
  TechniqueDamage technique=c.kind==RED?TechniqueDamage.RED:c.large?TechniqueDamage.PURPLE_LARGE:TechniqueDamage.PURPLE_SMALL;
  int count=0;
  for(LivingEntity target:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(c.radius()),c.cat::canFight)){
   if(++count>48||!current(c))return;
   if(c.hits.contains(target.getUUID())||SweptSphere.distanceSquared(start,end,target.getBoundingBox())>c.radius()*c.radius())continue;
   c.hits.add(target.getUUID());
   if(TechniqueDamageRules.hurt(target,source(c),c.kind==PURPLE?LimitlessTechnique.PURPLE_DAMAGE:70,technique)&&c.kind==RED&&!DomainFreeze.hold(target)){
    CatAdaptation.touch(target,CatAdaptation.Phenomenon.RED);float response=CatAdaptation.controlScale(target,CatAdaptation.Phenomenon.RED);
    target.knockback(.6*response,-c.direction.x,-c.direction.z);target.hurtMarked=true;
   }
  }
 }
 private static void furnace(Cast c){
  double step=Math.min(FurnaceFlight.SPEED/20,Math.max(0,FurnaceFlight.RANGE-c.traveled));Vec3 old=c.point,end=old.add(c.direction.scale(step));
  if(!LimitlessTechnique.loadedSegment(c.level,old,end)){impact(c);return;}
  var contact=LimitlessTechnique.trace(c.level,c.cat,old,end);c.point=contact.point();c.traveled+=old.distanceTo(c.point);
  if(contact.hit()||c.traveled>=FurnaceFlight.RANGE-.01)furnaceImpact(c);
 }
 private static void furnaceImpact(Cast c){
  impact(c);DamageSource damage=source(c);
  for(LivingEntity target:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(c.point,c.point).inflate(c.radius()),c.cat::canFight)){
   if(!current(c))return;double distance=Math.sqrt(SweptSphere.distanceSquared(c.point,c.point,target.getBoundingBox()));
   if(distance>c.radius()||!visible(c,c.point,target))continue;
   if(TechniqueDamageRules.hurt(target,damage,60+140*(1-(float)distance/c.radius()),TechniqueDamage.FURNACE))target.igniteForSeconds(4);
  }
  SkillTerrain.furnace(c.cat,damage,c.point,c.radius(),()->current(c));
 }
 private static void dismantle(Cast c){
  Vec3 start=c.cat.getEyePosition(),end=start.add(c.direction.scale(SukunaSlashSkills.REACH));int count=0;
  for(LivingEntity target:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(1.3),c.cat::canFight)){
   if(count>=16||!current(c))break;
   if(SweptSphere.distanceSquared(start,end,target.getBoundingBox())>.55*.55||!visible(c,start,target))continue;
   TechniqueDamageRules.hurt(target,SukunaCleaveDamage.source(c.cat),SukunaSlashSkills.DISMANTLE_DAMAGE,TechniqueDamage.DISMANTLE);
   cutVisual(c,target.getBoundingBox().getCenter(),Math.max(3,Math.min(8,target.getBbHeight()*2.2f)),true,false);count++;
  }
  if(count==0)cutVisual(c,start.add(c.direction.scale(6)),5,false,false);
 }
 private static void cleave(Cast c,LivingEntity target){
  Vec3 center=dev.jujutsuvfx.compat.CleaveCompatibility.worldPosition(c.level,target.getBoundingBox().getCenter());
  Vec3 forward=dev.jujutsuvfx.compat.CleaveCompatibility.worldDirection(c.cat,c.direction),up=FurnaceFlight.right(forward).cross(forward).normalize();
  int seed=c.cat.getRandom().nextInt();Vec3 stroke=SukunaSlashStyle.rotateStroke(seed,up,forward),normal=forward.cross(stroke).normalize();
  boolean cross=SukunaSlashStyle.isCross(seed);Vec3 second=cross?forward.cross(SukunaSlashStyle.secondStroke(stroke,forward)).normalize():null;
  double scale=SukunaSlashStyle.crossScale(seed);boolean terrain=cleaveTerrain(c,center,normal,forward,second,scale);
  boolean hit=SukunaCleaveDamage.strike(c.cat,center,normal,forward,4.5,second,scale);
  CatCleave.visual(c.level,center,forward,stroke,10.125f,terrain||hit,seed,cross);
 }
 private static boolean cleaveTerrain(Cast c,Vec3 center,Vec3 normal,Vec3 forward,Vec3 second,double scale){
  if(!TechniqueTerrainRules.enabled())return false;
  if(TechniqueTerrainRules.cleavePhysics())try{return Physics.cut(c.cat,center,normal,forward,second,scale);}
   catch(RuntimeException|LinkageError error){com.mojang.logging.LogUtils.getLogger().warn("Yuta copied Cleave physics unavailable; using ordinary terrain",error);}
  return CleaveTerrain.cut(c.cat,center,normal,4.5,second,scale);
 }
 private static final class Physics {
  static boolean cut(YutaCat cat,Vec3 center,Vec3 normal,Vec3 forward,Vec3 second,double scale){
   return (second==null?dev.jujutsuvfx.compat.SableStructureCutter.cut(cat,center,normal,forward,4.5)
    :dev.jujutsuvfx.compat.SableStructureCutter.cutCross(cat,center,normal,forward,4.5,second,scale)).success();
  }
 }
 private static void cutVisual(Cast c,Vec3 point,float length,boolean hit,boolean cross){
  int seed=c.cat.getRandom().nextInt();Vec3 up=FurnaceFlight.right(c.direction).cross(c.direction).normalize();
  var packet=new dev.jujutsuvfx.network.SukunaCut(point,c.direction,SukunaSlashStyle.rotateStroke(seed,up,c.direction),length,0,hit,seed,cross);
  for(var observer:c.level.players())if(observer.distanceToSqr(point)<128*128&&observer.connection.hasChannel(dev.jujutsuvfx.network.SukunaCut.TYPE))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(observer,packet);
 }
 private static boolean visible(Cast c,Vec3 from,LivingEntity target){return c.level.clip(new ClipContext(from,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.cat)).getType()==HitResult.Type.MISS;}
 private static DamageSource source(Cast c){String name=c.kind==BLUE?"limitless_blue":c.kind==RED?"limitless_red":c.kind==PURPLE?"hollow_purple":"furnace";
  return new DamageSource(c.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,name))),c.cat,c.cat);}
 private static Vec3 aim(YutaCat cat,LivingEntity target){Vec3 direction=target.getBoundingBox().getCenter().subtract(cat.getEyePosition()).normalize();return direction.lengthSqr()<.01?cat.getLookAngle():direction;}
 private static void face(YutaCat cat,Vec3 direction){cat.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));cat.setXRot((float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,direction.y)))));cat.yHeadRot=cat.yBodyRot=cat.getYRot();}
 private static void impact(Cast c){if(c.phase!=IMPACT){c.phase=IMPACT;c.stage=0;c.send();}}
 private static int afterglow(Cast c){return c.kind<0||c.kind>=DISMANTLE?12:c.kind==BLUE?16:c.kind==FURNACE?84:Math.round(LimitlessTechnique.afterglow(c.kind)*20);}
}
