package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.RikaSupportClock;
import dev.jujutsuvfx.core.RikaResidentClock;
import dev.jujutsuvfx.core.RikaAssistClock;
import dev.jujutsuvfx.core.RikaGroundPath;
import dev.jujutsuvfx.core.YutaBladeMotion;
import dev.jujutsuvfx.entity.YutaCat;
import dev.jujutsuvfx.network.FistImpact;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned temporary assistance and a finite resident companion; neither freezes a fighter. */
public final class RikaSupport {
 private final YutaCat cat;
 private final RikaSupportClock clock=new RikaSupportClock();
 private final RikaResidentClock resident=new RikaResidentClock();
 private final RikaAssistClock temporary=new RikaAssistClock();
 private final RikaGroundPath path=new RikaGroundPath();
 private final Ground ground=new Ground();
 private LivingEntity target,summonTarget;
 private Level ownerLevel;
 private Vec3 origin=Vec3.ZERO,previousOrigin=Vec3.ZERO,forward=new Vec3(0,0,1);
 private float facing,previousFacing;
 private float walk,previousWalk,moving,previousMoving;
 private double fallSpeed;
 private int pendingAction;
 private long actionStart,residentStart;
 private boolean scriptedPrelude;
 private Vec3 waveOrigin=Vec3.ZERO,waveAxis=Vec3.ZERO;
 private double waveDistance;
 private final java.util.Set<java.util.UUID> waveHits=new java.util.HashSet<>();
 public boolean ranged(LivingEntity victim){
  if(!validTarget(victim,32)||!cat.hasLineOfSight(victim))return false;
  boolean distant=cat.distanceToSqr(victim)>18*18||victim.getY()-cat.getY()>2.5;
  return distant&&request(RikaSupportClock.WAVE,victim);
 }
 private int lastHurt=-1000,comboHits,residentAttacks;
 public RikaSupport(YutaCat cat){this.cat=cat;}
 public void cancel(){
  clock.cancel();resident.cancel();temporary.cancel();path.clear();scriptedPrelude=false;waveAxis=Vec3.ZERO;waveHits.clear();target=null;summonTarget=null;ownerLevel=null;
  pendingAction=0;moving=previousMoving=0;fallSpeed=0;
  if(!cat.rikaState().isEmpty())cat.setRikaState(new CompoundTag());
 }
 public boolean preparing(){return resident.preparing();}
 public void scriptedRing(){
  cancel();scriptedPrelude=true;resident.scriptedBegin();summonTarget=cat.getTarget();ownerLevel=cat.level();
  origin=cat.position();previousOrigin=origin;publish();
 }
 public void sustainScripted(){
  if(!resident.active()){
   if(!temporary.active()&&!spawnBesideOwner())return;
   resident.scriptedResident();scriptedPrelude=false;residentStart=cat.level().getGameTime();
   ownerLevel=cat.level();temporary.cancel();summonTarget=null;publish();appearanceSound();
  }
  resident.sustain();
 }
 public boolean beginSummon(){
  LivingEntity victim=cat.getTarget();
  if(cat.level().isClientSide||!cat.isAlive()||cat.isNoAi()||cat.forcedDance()||!cat.bladeDrawn()
    ||!cat.onGround()||clock.active()||temporary.active()||!validTarget(victim,16)||!cat.hasLineOfSight(victim)||!resident.begin())return false;
  summonTarget=victim;ownerLevel=cat.level();origin=cat.position();previousOrigin=origin;publish();return true;
 }
 public boolean manifest(){
  if(!preparing())return false;
  if(!cat.isAlive()||cat.isNoAi()||cat.forcedDance()||cat.level()!=ownerLevel||!cat.bladeDrawn()
    ||!cat.onGround()||!validTarget(summonTarget,scriptedPrelude?128:16)){cancel();return false;}
  if(!resident.manifest())return false;
  if(!spawnBesideOwner()){cancel();return false;}
  residentStart=cat.level().getGameTime();residentAttacks=0;
  summonTarget=null;scriptedPrelude=false;publish();appearanceSound();return true;
 }
 public void tick(){
  boolean ownerValid=cat.isAlive()&&!cat.isNoAi()&&!cat.forcedDance()
      &&cat.bladeAction()!=YutaBladeMotion.DANCE&&(ownerLevel==null||ownerLevel==cat.level());
  if(!ownerValid){cancel();clock.tick(false);resident.tick(false);return;}
  previousOrigin=origin;previousFacing=facing;previousWalk=walk;previousMoving=moving;
  boolean wasResident=resident.active();
  resident.tick(!preparing()||cat.bladeDrawn()&&cat.onGround()&&validTarget(summonTarget,scriptedPrelude?128:16)
      &&cat.bladeAction()==YutaBladeMotion.RIKA_SUMMON);
  if(wasResident&&!resident.active()){clock.cancel();target=null;pendingAction=0;path.clear();}
  boolean visible=resident.active()||temporary.active();
  if(visible)settle();
  if(clock.tick(!clock.active()||validTarget(target,36)
      ||clock.action()==RikaSupportClock.WAVE&&waveAxis.lengthSqr()>.5))strike();
  if(clock.action()==RikaSupportClock.WAVE&&waveAxis.lengthSqr()>.5)tickWave();
  temporary.tick(clock.active(),validTarget(target,36));
  visible=resident.active()||temporary.active();
  if(visible){
   if(!clock.active()){
    LivingEntity victim=resident.active()&&validTarget(cat.getTarget(),36)?cat.getTarget():validTarget(target,36)?target:null;
    target=victim;
    if(victim!=null&&(resident.active()||temporary.waiting()&&pendingAction!=0)){
     int action=victim.getY()-origin.y>2.5&&pendingAction!=RikaSupportClock.GUARD?RikaSupportClock.WAVE
         :pendingAction!=0?pendingAction:RikaSupportClock.melee(residentAttacks);
     if(action==RikaSupportClock.WAVE||action==RikaSupportClock.GUARD||origin.distanceToSqr(victim.position())<=3.1*3.1)face(victim);
     if(!startAttack(action,victim)){
      moveToward(victim.position(),2.35);
      if(origin.distanceToSqr(victim.position())<=3.1*3.1)face(victim);
      startAttack(action,victim);
     }
    }else if(victim==null&&resident.active()){
     pendingAction=0;moveToward(ownerAnchor(),.6);
    }
   }
   double distance=origin.subtract(previousOrigin).multiply(1,0,1).length();
   if(distance>.001)walk+=(float)(distance/2.1);
   moving+=(Math.min(1,(float)(distance/.23))-moving)*.4f;
   publish();
  }else if(preparing())publish();
  else if(!clock.active()){
   target=null;summonTarget=null;ownerLevel=null;pendingAction=0;path.clear();waveAxis=Vec3.ZERO;
   if(!cat.rikaState().isEmpty())cat.setRikaState(new CompoundTag());
  }
 }
 public boolean request(int action,LivingEntity victim){
  // Manifest beside Yuta, then approach on the same grounded route as a resident.
  return !preparing()&&appear(action,victim);
 }
 private boolean validTarget(LivingEntity victim,double range){
  return victim!=null&&victim.level()==cat.level()&&cat.canFight(victim)&&cat.distanceToSqr(victim)<=range*range;
 }
 private Vec3 ownerAnchor(){
  double angle=Math.toRadians(cat.getYRot());
  return cat.position().add(Math.cos(angle)*2.4,0,Math.sin(angle)*2.4);
 }
 private boolean spawnBesideOwner(){
  var point=RikaGroundPath.beside(point(cat.position()),cat.getYRot(),ground);if(point==null)return false;
  origin=vector(point);previousOrigin=origin;facing=previousFacing=cat.getYRot();face(cat.getTarget());previousFacing=facing;
  walk=previousWalk=moving=previousMoving=0;fallSpeed=0;path.clear();ownerLevel=cat.level();return true;
 }
 private void moveToward(Vec3 desired,double stop){
  if(Math.abs(fallSpeed)>.001)return;
  Vec3 next=vector(path.move(point(origin),point(desired),.26,stop,ground));
  Vec3 delta=next.subtract(origin);origin=next;
  if(delta.horizontalDistanceSqr()>.0001)turnToward(delta);
 }
 private void settle(){
  var supported=ground.ground(origin.x,origin.z,origin.y);
  if(supported!=null&&Math.abs(supported.y()-origin.y)<.04){fallSpeed=0;return;}
  // When terrain is removed, fall physically instead of hanging at the old anchor.
  fallSpeed=Math.max(-.8,fallSpeed-.08);
  double y=origin.y+fallSpeed;
  if(supported!=null&&supported.y()<=origin.y&&supported.y()>=y){origin=new Vec3(origin.x,supported.y(),origin.z);fallSpeed=0;}
  else if(ground.empty(new RikaGroundPath.Point(origin.x,y,origin.z)))origin=new Vec3(origin.x,y,origin.z);
  else fallSpeed=0;
 }
 private void face(LivingEntity victim){
  Vec3 direction=victim==null?new Vec3(-Math.sin(Math.toRadians(cat.getYRot())),0,Math.cos(Math.toRadians(cat.getYRot())))
      :victim.position().subtract(origin).multiply(1,0,1).normalize();
  turnToward(direction);
 }
 private void turnToward(Vec3 direction){
  if(direction.lengthSqr()<.01)return;
  float desired=(float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90);
  float turn=(desired-facing)%360;if(turn>180)turn-=360;if(turn<-180)turn+=360;
  facing+=Math.max(-24,Math.min(24,turn));
  double radians=Math.toRadians(facing);forward=new Vec3(-Math.sin(radians),0,Math.cos(radians));
 }
 private boolean appear(int action,LivingEntity victim){
  if(cat.level().isClientSide||!clock.ready()||!cat.isAlive()||cat.isNoAi()||cat.forcedDance()
    ||pendingAction!=0||action<RikaSupportClock.PUNCH||action>RikaSupportClock.OVERHEAD
    ||cat.bladeAction()==YutaBladeMotion.DANCE||!validTarget(victim,32))return false;
  if(!resident.active()&&temporary.active())return false;
  if(action==RikaSupportClock.GUARD&&resident.active()&&cat.distanceToSqr(origin)>3.2*3.2)return false;
  if(!resident.active()){
   if(!temporary.begin())return false;
   if(!spawnBesideOwner()){temporary.cancel();return false;}
   residentStart=cat.level().getGameTime();residentAttacks=0;appearanceSound();
  }
  target=victim;pendingAction=action;face(victim);startAttack(action,victim);publish();return true;
 }
 private boolean startAttack(int action,LivingEntity victim){
  if(Math.abs(fallSpeed)>.001||!clear(origin.add(0,1.4,0),victim.getBoundingBox().getCenter()))return false;
  if(action==RikaSupportClock.GUARD){if(cat.distanceToSqr(origin)>3.2*3.2)return false;}
  else if(action!=RikaSupportClock.WAVE&&(!contact(victim,action)||origin.distanceToSqr(victim.position())>3.1*3.1))return false;
  if(!clock.begin(action))return false;
  if(!resident.active())temporary.attackStarted();
  pendingAction=0;residentAttacks++;path.clear();waveAxis=Vec3.ZERO;waveDistance=0;waveHits.clear();
  actionStart=cat.level().getGameTime();return true;
 }
 private boolean contact(LivingEntity victim,int action){
  Vec3 delta=victim.getBoundingBox().getCenter().subtract(origin);
  return RikaSupportClock.inReach(action,delta.dot(forward),delta.x*forward.z-delta.z*forward.x,delta.y,Math.min(.8,victim.getBbWidth()*.5));
 }
 private void publish(){
  CompoundTag state=new CompoundTag();
  state.putInt("Phase",preparing()?RikaResidentClock.PREPARING:resident.active()?RikaResidentClock.RESIDENT:RikaResidentClock.TEMPORARY);
  state.putBoolean("Resident",resident.active());state.putInt("Action",clock.action());
  state.putLong("Start",actionStart);state.putLong("ResidentStart",residentStart);
  state.putInt("Duration",resident.active()?RikaResidentClock.DURATION:RikaAssistClock.APPROACH+RikaSupportClock.DURATION+RikaAssistClock.FADE);
  state.putInt("Remaining",resident.active()?resident.remaining():temporary.remaining());state.putLong("PositionTick",cat.level().getGameTime());
  state.putFloat("Walk",walk);state.putFloat("PrevWalk",previousWalk);state.putFloat("Moving",moving);state.putFloat("PrevMoving",previousMoving);
  state.putBoolean("Pursuing",!clock.active()&&target!=null&&(resident.active()||temporary.waiting()));
  state.putDouble("X",origin.x);state.putDouble("Y",origin.y);state.putDouble("Z",origin.z);
  state.putDouble("PrevX",previousOrigin.x);state.putDouble("PrevY",previousOrigin.y);state.putDouble("PrevZ",previousOrigin.z);
  if(clock.action()==RikaSupportClock.WAVE&&waveAxis.lengthSqr()>.5){
   state.putDouble("WaveX",waveOrigin.x);state.putDouble("WaveY",waveOrigin.y);state.putDouble("WaveZ",waveOrigin.z);
   state.putDouble("WaveDX",waveAxis.x);state.putDouble("WaveDY",waveAxis.y);state.putDouble("WaveDZ",waveAxis.z);
   state.putDouble("WaveDistance",waveDistance);
  }
  state.putFloat("Yaw",facing);state.putFloat("PrevYaw",previousFacing);cat.setRikaState(state);
 }
 private void appearanceSound(){
  ((ServerLevel)cat.level()).playSound(null,origin.x,origin.y+1,origin.z,SoundEvents.EVOKER_CAST_SPELL,SoundSource.HOSTILE,.45f,1.5f);
 }
 public void speech(LivingEntity victim){request(RikaSupportClock.PUNCH,victim);}
 public void melee(LivingEntity victim,int action){
  if(!cat.bladeDrawn()||!clock.ready())return;
  comboHits++;
  if(action==YutaBladeMotion.RISING||action==YutaBladeMotion.SPIN_CUT)request(RikaSupportClock.SLAM,victim);
  else if(comboHits>=3&&request(RikaSupportClock.LAUNCH,victim))comboHits=0;
 }
 public void hurtBy(LivingEntity attacker,boolean bypass){
  if(bypass||!cat.isAlive())return;
  boolean pressure=cat.tickCount-lastHurt<=30;lastHurt=cat.tickCount;
  if(pressure&&cat.distanceToSqr(attacker)<=6*6)request(RikaSupportClock.GUARD,attacker);
 }
 public float guardMultiplier(DamageSource source,boolean bypass){
  if(bypass||!clock.guarding()||cat.distanceToSqr(origin)>3.2*3.2||source.getSourcePosition()==null)return 1;
  Vec3 direction=source.getSourcePosition().subtract(cat.position()).normalize();
  if(direction.dot(forward)<.15)return 1;
  cat.playSound(SoundEvents.SHIELD_BLOCK,.8f,.8f);return .5f;
 }
 private boolean clear(Vec3 from,Vec3 to){return cat.level().clip(new ClipContext(from,to,
    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,cat)).getType()==HitResult.Type.MISS;}
 private static RikaGroundPath.Point point(Vec3 p){return new RikaGroundPath.Point(p.x,p.y,p.z);}
 private static Vec3 vector(RikaGroundPath.Point p){return new Vec3(p.x(),p.y(),p.z());}
 /** Collision shapes supply real slab/stair surfaces; no heightmap or victim-relative Y is used. */
 private final class Ground implements RikaGroundPath.Terrain {
  private AABB body(RikaGroundPath.Point p){return new AABB(p.x()-.78,p.y()+.025,p.z()-.78,p.x()+.78,p.y()+3.3,p.z()+.78);}
  private boolean loaded(AABB box){
   Level level=cat.level();
   if(box.minY<level.getMinBuildHeight()||box.maxY>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box))return false;
   for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)
    for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)
     if(!level.hasChunkAt(new BlockPos(x<<4,(int)box.minY,z<<4)))return false;
   return true;
  }
  boolean empty(RikaGroundPath.Point p){
   AABB box=body(p);return loaded(box)&&!cat.level().getBlockCollisions(cat,box).iterator().hasNext()&&!cat.level().containsAnyLiquid(box);
  }
  public RikaGroundPath.Point ground(double x,double z,double nearY){
   AABB search=new AABB(x-.76,nearY-1.251,z-.76,x+.76,nearY+1.001,z+.76);
   if(!loaded(search))return null;
   double floor=Double.NEGATIVE_INFINITY;
   for(var shape:cat.level().getBlockCollisions(cat,search))for(var box:shape.toAabbs()){
    if(box.maxX<=search.minX||box.minX>=search.maxX||box.maxZ<=search.minZ||box.minZ>=search.maxZ)continue;
    if(box.maxY<=nearY+1.001&&box.maxY>=nearY-1.251)floor=Math.max(floor,box.maxY);
   }
   if(!Double.isFinite(floor))return null;
   var p=new RikaGroundPath.Point(x,floor,z);
   return empty(p)?p:null;
  }
  public boolean clear(RikaGroundPath.Point from,RikaGroundPath.Point to){
   if(to.y()-from.y()>1.001||from.y()-to.y()>1.251)return false;
   int steps=Math.max(1,(int)Math.ceil(from.distance(to)/.18));
   double raised=Math.max(from.y(),to.y());
   // Lift, sweep the complete body, then settle. This permits normal one-block steps
   // without allowing a diagonal segment to tunnel through a wall or low ceiling.
   for(int i=0;i<=steps;i++){
    double t=i/(double)steps,x=from.x()+(to.x()-from.x())*t,z=from.z()+(to.z()-from.z())*t;
    if(!empty(new RikaGroundPath.Point(x,raised,z)))return false;
    if(ground(x,z,from.y()+(to.y()-from.y())*t)==null)return false;
   }
   int liftSteps=Math.max(1,(int)Math.ceil(Math.abs(to.y()-from.y())/.18));
   for(int i=0;i<=liftSteps;i++){
    double y=from.y()+(to.y()-from.y())*i/liftSteps;
    if(!empty(new RikaGroundPath.Point(to.y()>from.y()?from.x():to.x(),y,to.y()>from.y()?from.z():to.z())))return false;
   }
   return true;
  }
 }
 private void tickWave(){
  double next=Math.min(32,waveDistance+1.6);
  Vec3 from=waveOrigin.add(waveAxis.scale(waveDistance)),to=waveOrigin.add(waveAxis.scale(next));
  var clip=cat.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,cat));
  if(clip.getType()!=HitResult.Type.MISS)to=clip.getLocation();
  ServerLevel level=(ServerLevel)cat.level();
  var box=new net.minecraft.world.phys.AABB(from,to).inflate(1.1);
  for(LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,box,cat::canFight)){
   if(waveHits.contains(victim.getUUID())||!dev.jujutsuvfx.core.RangedCatRules.segment(
      victim.getBoundingBox().getCenter().subtract(from).x,victim.getBoundingBox().getCenter().subtract(from).y,
      victim.getBoundingBox().getCenter().subtract(from).z,waveAxis.x,waveAxis.y,waveAxis.z,
      from.distanceTo(to),Math.min(2,victim.getBbWidth()*.5)+1.1)
      ||!clear(from,victim.getBoundingBox().getCenter()))continue;
   waveHits.add(victim.getUUID());
   DamageSource damage=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
     .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("jujutsuvfx","yuta_melee"))),cat);
   if(TechniqueDamageRules.hurtYutaMelee(victim,damage,32)){
    victim.setDeltaMovement(victim.getDeltaMovement().add(waveAxis.scale(.55)));victim.hurtMarked=true;
    level.sendParticles(ParticleTypes.CRIT,victim.getX(),victim.getY()+.7,victim.getZ(),15,.3,.3,.3,.12);
    level.playSound(null,victim.blockPosition(),SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.HOSTILE,.9f,.7f);
   }
  }
  waveDistance=waveOrigin.distanceTo(to);
  if(clip.getType()!=HitResult.Type.MISS||next>=32)waveAxis=Vec3.ZERO;
 }
 private void strike(){
  if(clock.action()==RikaSupportClock.WAVE){
   if(!validTarget(target,36))return;
   waveOrigin=origin.add(0,1.6,0);waveAxis=target.getBoundingBox().getCenter().subtract(waveOrigin).normalize();waveDistance=0;
   cat.playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.2f,.55f);publish();return;
  }
  // The attack commits its anchor: an opponent that escapes the fist is never snapped back or remotely hit.
  if(!validTarget(target,36)||!contact(target,clock.action())
    ||!clear(origin.add(0,1,0),target.getBoundingBox().getCenter()))return;
  ServerLevel level=(ServerLevel)cat.level();int action=clock.action();
  DamageSource damage=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
      .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("jujutsuvfx","yuta_melee"))),cat);
  if(!TechniqueDamageRules.hurtYutaMelee(target,damage,RikaSupportClock.damage(action)))return;
  Vec3 hit=target.getBoundingBox().getCenter();
  target.knockback(action==RikaSupportClock.GUARD?.8:.35,-forward.x,-forward.z);
  if(action==RikaSupportClock.LAUNCH){
   target.setDeltaMovement(target.getDeltaMovement().add(0,.32,0));cat.queueRikaFollow(target);
  }else if(action==RikaSupportClock.SLAM&&!target.onGround()){
   target.setDeltaMovement(target.getDeltaMovement().multiply(.4,0,.4).add(0,-.55,0));
  }
  target.hurtMarked=true;
  level.sendParticles(ParticleTypes.CRIT,hit.x,hit.y,hit.z,12,.3,.35,.3,.16);
  level.sendParticles(ParticleTypes.CLOUD,hit.x,hit.y,hit.z,8,.35,.2,.35,.06);
  level.playSound(null,hit.x,hit.y,hit.z,SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.HOSTILE,1.1f,.65f);
  var packet=new FistImpact(cat.getId(),target.getId(),hit,forward,action==RikaSupportClock.GUARD?.65f:1.1f);
  for(var observer:level.players())if(CinematicAudio.visualRange(observer,hit,128)&&observer.connection.hasChannel(FistImpact.TYPE))PacketDistributor.sendToPlayer(observer,packet);
 }
}
