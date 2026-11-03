package dev.jujutsuvfx.entity;

import dev.jujutsuvfx.core.*;
import dev.jujutsuvfx.server.CatDance;
import dev.jujutsuvfx.server.DomainFreeze;
import dev.jujutsuvfx.server.YutaGetoDuel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Neutral summoner. Deliberately independent of the Gojo/Sukuna screenplay and domain state. */
public final class GetoCat extends PathfinderMob implements DancingCat {
 private static final EntityDataAccessor<CompoundTag> DUEL=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.COMPOUND_TAG);
 public CompoundTag duelState(){return entityData.get(DUEL);}
 public void setDuelState(CompoundTag state){if(!level().isClientSide)entityData.set(DUEL,state.copy());}
 public void cinematicPose(int action,int age){
  if(action()!=action)start(action);entityData.set(START,level().getGameTime()-age);
  getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);setXxa(0);setZza(0);
 }
 public void cinematicSwarm(){summonChimeras(SpiritRules.ASSAULT,SpiritRules.AIR);summonCooldown=140;}
 public void cinematicRanged(){rangedCooldown=0;retreatCooldown=0;cooldown=0;forcedRanged=true;}
 public void cinematicRecall(){for(CursedSpirit spirit:spirits())spirit.absorb();spirits.clear();spiritExpiry.clear();}
 public void cinematicRelease(){finish();getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);}
 private static final EntityDataAccessor<Integer> CAMERA_TARGET=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.INT);
 public LivingEntity cameraTarget(){return level().getEntity(entityData.get(CAMERA_TARGET)) instanceof LivingEntity target?target:null;}
 public static final int SUMMON=100,UZUMAKI=101,RETREAT=102,BLAST=103;
 private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> DEBUG_POSE=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.LONG);
 private static final EntityDataAccessor<CompoundTag> DANCE=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.COMPOUND_TAG);
 private static final EntityDataAccessor<CompoundTag> AIM=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.COMPOUND_TAG);
 private static final EntityDataAccessor<Boolean> FIGHTING=SynchedEntityData.defineId(GetoCat.class,EntityDataSerializers.BOOLEAN);
 private boolean strikeSpent;
 private final Set<UUID> vortexHits=new HashSet<>();
 private boolean vortexImpact;
 private int retreatCooldown,rangedCooldown=100,meleeExchanges,lastCombo=-1;
 private final GetoMeleeSequence melee=new GetoMeleeSequence();
 private boolean retreatCast,forcedRanged;
 private Vec3 retreatDirection=Vec3.ZERO;
 private int cooldown=20,summonCooldown=35,guardCooldown, milestones,missingTarget;
 private final Set<UUID> spirits=new HashSet<>();
 private final Map<UUID,Long> spiritExpiry=new HashMap<>();
 private ServerBossEvent boss;
 public GetoCat(EntityType<? extends PathfinderMob> type,Level level){super(type,level);setPersistenceRequired();xpReward=50;}
 public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,800).add(Attributes.MOVEMENT_SPEED,.33).add(Attributes.FOLLOW_RANGE,128).add(Attributes.ATTACK_DAMAGE,10).add(Attributes.KNOCKBACK_RESISTANCE,.45);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(DUEL,new CompoundTag());b.define(FIGHTING,false);b.define(CAMERA_TARGET,-1);b.define(ACTION,0);b.define(DEBUG_POSE,0);b.define(START,0L);b.define(DANCE,new CompoundTag());b.define(AIM,new CompoundTag());}
 @Override protected void registerGoals(){
  goalSelector.addGoal(0,new FloatGoal(this));
  goalSelector.addGoal(1,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));} public boolean canUse(){return valid(getTarget());} public void stop(){getNavigation().stop();} });
  goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.8));
  goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));
  targetSelector.addGoal(0,new HurtByTargetGoal(this){
   @Override public boolean canUse(){return valid(getLastHurtByMob())&&super.canUse();}
   @Override public boolean canContinueToUse(){return valid(getTarget());}
  });
  targetSelector.addGoal(1,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<LivingEntity>(this,LivingEntity.class,10,false,false,
    t->valid(t)&&(t instanceof YutaCat||t instanceof SorcererCat c&&c.isGojo())){
   @Override protected net.minecraft.world.phys.AABB getTargetSearchArea(double range){return getBoundingBox().inflate(range);}
   @Override public boolean canContinueToUse(){return valid(getTarget());}
  });
 }
 public boolean valid(LivingEntity t){return !YutaGetoDuel.suppressed(this,t)&&t!=null&&t!=this&&t.isAlive()&&t.level()==level()&&!isAlliedTo(t)&&!(t instanceof CursedSpirit s&&getUUID().equals(s.ownerId()))&&!(t instanceof Player p&&(p.isCreative()||p.isSpectator()))&&distanceToSqr(t)<=128*128;}
 public boolean inCombat(){return entityData.get(FIGHTING);}
 public int debugPose(){return entityData.get(DEBUG_POSE);}
 public void setDebugPose(int pose){
  if(level().isClientSide||!GetoDebugPose.valid(pose))return;
  YutaGetoDuel.cancel(this);CatDance.start(this,0);finish();releasePrey();recall();
  setTarget(null);setLastHurtByMob(null);entityData.set(FIGHTING,false);entityData.set(CAMERA_TARGET,-1);
  entityData.set(DEBUG_POSE,pose);entityData.set(START,level().getGameTime());
  entityData.set(AIM,new CompoundTag());forcedRanged=false;retreatCast=false;
  getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);setXxa(0);setZza(0);
  yBodyRot=getYRot();setYHeadRot(getYRot());if(boss!=null)boss.removeAllPlayers();
 }
 public int action(){return debugPose()!=0?GetoDebugPose.action(debugPose()):entityData.get(ACTION);}
 public float actionAge(float partial){
  float age=Math.max(0,level().getGameTime()-entityData.get(START)+partial);
  return debugPose()!=0?GetoDebugPose.age(debugPose(),age):age*(GetoCloudMotion.active(action())?1.5f:1);
 }
 public Vec3 beamDirection(){CompoundTag t=entityData.get(AIM);return new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));}
 public double beamLength(){return entityData.get(AIM).contains("Length")?entityData.get(AIM).getDouble("Length"):40;}
 public Vec3 spellOrigin(){CompoundTag a=entityData.get(AIM);return (action()==UZUMAKI||action()==BLAST)&&a.contains("OriginX")?new Vec3(a.getDouble("OriginX"),a.getDouble("OriginY"),a.getDouble("OriginZ")):position().add(0,1.7,0).add(getLookAngle().multiply(1,0,1).normalize().scale(.85));}
 public int absorbedSpirits(){return entityData.get(AIM).getInt("Spirits");}
 private void start(int action){if(!GetoCloudMotion.active(action))melee.cancel();strikeSpent=false;entityData.set(ACTION,action);entityData.set(START,level().getGameTime());getNavigation().stop();}
 private void finish(){start(0);cooldown=6+random.nextInt(7);}
 public CompoundTag danceState(){return entityData.get(DANCE);}
 public void danceState(CompoundTag state){entityData.set(DANCE,state);}
 public void setDanceStyle(int style){if(!level().isClientSide){entityData.set(DEBUG_POSE,0);YutaGetoDuel.cancel(this);finish();releasePrey();recall();entityData.set(FIGHTING,false);if(boss!=null)boss.removeAllPlayers();CatDance.start(this,style);}}
 @Override public boolean isNoAi(){return debugPose()!=0||danceStyle()!=0||super.isNoAi();}
 @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
 public List<CursedSpirit> spirits(){
  if(!(level() instanceof ServerLevel l))return List.of();
  List<CursedSpirit> result=new ArrayList<>();
  for(UUID id:spirits)if(l.getEntity(id) instanceof CursedSpirit s&&s.isAlive()&&!s.recalling())result.add(s);
  return result;
 }
 public void forgetSpirit(UUID id){spirits.remove(id);spiritExpiry.remove(id);}
 public void rememberSpirit(CursedSpirit s){if(s.recalling())return;if(spirits.size()<SpiritRules.LIMIT||spirits.contains(s.getUUID())){spirits.add(s.getUUID());spiritExpiry.put(s.getUUID(),s.expires());}else s.recall();}
 private void reconcile(){
  if(!(level() instanceof ServerLevel l))return;
  // Unloaded slots remain reserved until their entities report back or the summon lifetime expires.
  spirits.removeIf(id->spiritExpiry.getOrDefault(id,0L)<=level().getGameTime()||l.getEntity(id) instanceof CursedSpirit s&&(!s.isAlive()||s.recalling()));
  spiritExpiry.keySet().retainAll(spirits);
  if(spirits.isEmpty())for(CursedSpirit s:l.getEntitiesOfClass(CursedSpirit.class,getBoundingBox().inflate(128),s->getUUID().equals(s.ownerId())&&!s.recalling()))rememberSpirit(s);
 }
 private void recall(){for(CursedSpirit s:spirits())s.recall();spirits.clear();spiritExpiry.clear();}
 /** Absorbs up to five nearby creatures and stitches their dissected bodies into new curses. */
 public static final int DEVOUR_RANGE=20,DEVOUR_LIMIT=5,DEVOUR_TICKS=10;
 private final Map<UUID,Integer> devouring=new LinkedHashMap<>();
 public static boolean devourable(LivingEntity e){
  if(e==null||!e.isAlive()||e instanceof Player||e instanceof SorcererCat||e instanceof YutaCat||e instanceof GetoCat||e instanceof CursedSpirit)return false;
  if(e instanceof net.minecraft.world.entity.decoration.ArmorStand||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||e instanceof net.minecraft.world.entity.boss.wither.WitherBoss)return false;
  // Named mobs and anyone's pets are never eaten.
  if(e.hasCustomName()||e instanceof OwnableEntity owned&&owned.getOwnerUUID()!=null)return false;
  return !e.isInvulnerable()&&e.getBbHeight()<=6&&!e.getType().getDescriptionId().isEmpty();
 }
 private List<LivingEntity> prey(){
  List<LivingEntity> found=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(DEVOUR_RANGE),
    e->e!=this&&devourable(e)&&!devouring.containsKey(e.getUUID())&&distanceToSqr(e)<=DEVOUR_RANGE*DEVOUR_RANGE);
  found.sort(Comparator.comparingDouble(this::distanceToSqr));
  return found.size()>DEVOUR_LIMIT?new ArrayList<>(found.subList(0,DEVOUR_LIMIT)):found;
 }
 /** One absorption feeds every curse created by this call; roles beyond the free slots are skipped. */
 private int summonChimeras(int... roles){
  if(level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return 0;
  if(!(level() instanceof ServerLevel l))return 0;
  int free=Math.min(roles.length,SpiritRules.LIMIT-spirits.size());if(free<=0)return 0;
  List<LivingEntity> victims=prey();List<String> donors=new ArrayList<>();
  for(LivingEntity v:victims){String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(v.getType()).toString();donors.add(id);}
  if(donors.isEmpty()){
   // Nothing to eat: Geto draws on curses he already holds (vanilla silhouettes as the stored stock).
   for(int i=0;i<3;i++)donors.add("minecraft:"+SpiritRules.SKINS[random.nextInt(SpiritRules.SKINS.length)]);
  }
  String[][] recipes=dev.jujutsuvfx.core.ChimeraAnatomy.recipes(donors,free,random.nextLong());
  int made=0;
  for(int i=0;i<free;i++){
   int role=roles[i];
   CursedSpirit s=VfxEntities.CURSED_SPIRIT.get().create(l);if(s==null)continue;
   var spec=SpiritRules.choose(role,random.nextInt(3),random.nextInt(3),random.nextFloat());
   s.configure(this,spec,recipes[i],random.nextInt());
   Vec3 side=new Vec3(Math.cos(Math.toRadians(getYRot())),0,Math.sin(Math.toRadians(getYRot()))).scale(made==0?2.2:-2.2);
   s.moveTo(getX()+side.x,getY()+(role==SpiritRules.AIR?1.7:0),getZ()+side.z,getYRot(),0);
   if(role!=SpiritRules.AIR&&!l.noCollision(s))s.moveTo(getX(),getY()+1.5,getZ(),getYRot(),0);
   if(!l.noCollision(s)||!l.addFreshEntity(s))continue;
   rememberSpirit(s);s.setTarget(getTarget());made++;
   l.sendParticles(ParticleTypes.DRAGON_BREATH,s.getX(),s.getY()+.5,s.getZ(),24,.35,.45,.35,.03);
  }
  if(made>0){
   for(LivingEntity v:victims){devouring.put(v.getUUID(),0);if(v instanceof Mob mob){mob.setNoAi(true);mob.setTarget(null);}}
   playSound(SoundEvents.EVOKER_PREPARE_SUMMON,.9f,.6f);
   if(!victims.isEmpty())playSound(SoundEvents.WARDEN_SONIC_CHARGE,.7f,1.35f);
  }
  return made;
 }
 private void releasePrey(){
  if(!(level() instanceof ServerLevel l))return;
  for(UUID id:devouring.keySet())if(l.getEntity(id) instanceof LivingEntity v){v.noPhysics=false;v.setNoGravity(false);if(v instanceof Mob mob)mob.setNoAi(false);}
  devouring.clear();
 }
 /** Victims spiral into Geto's mouth before they vanish; nothing drops and no death event fires. */
 private void digest(){
  if(devouring.isEmpty()||!(level() instanceof ServerLevel l))return;
  Vec3 mouth=position().add(0,1.0,0).add(getLookAngle().multiply(1,0,1).normalize().scale(.5));
  for(var it=devouring.entrySet().iterator();it.hasNext();){
   var e=it.next();Entity found=l.getEntity(e.getKey());
   if(!(found instanceof LivingEntity v)||!v.isAlive()){it.remove();continue;}
   int age=e.getValue()+1;e.setValue(age);
   float t=Math.min(1,age/(float)DEVOUR_TICKS);
   Vec3 offset=v.position().subtract(mouth);Vec3 swirl=new Vec3(-offset.z,0,offset.x).normalize().scale(.35*(1-t));
   Vec3 next=v.position().lerp(mouth,.18+.5*t*t).add(Double.isFinite(swirl.x)?swirl:Vec3.ZERO);
   v.noPhysics=true;v.setNoGravity(true);v.setDeltaMovement(Vec3.ZERO);v.teleportTo(next.x,next.y,next.z);v.hurtMarked=true;
   l.sendParticles(ParticleTypes.SQUID_INK,v.getX(),v.getY()+v.getBbHeight()*.5,v.getZ(),3,v.getBbWidth()*.3,v.getBbHeight()*.3,v.getBbWidth()*.3,.01);
   l.sendParticles(ParticleTypes.REVERSE_PORTAL,v.getX(),v.getY()+v.getBbHeight()*.5,v.getZ(),5,.2,.2,.2,.05);
   if(age>=DEVOUR_TICKS){
    l.sendParticles(ParticleTypes.DRAGON_BREATH,mouth.x,mouth.y,mouth.z,14,.2,.2,.2,.04);
    playSound(SoundEvents.GENERIC_EAT,.8f,.55f+random.nextFloat()*.2f);
    v.discard();it.remove();
   }
  }
 }
 @Override public void tick(){
  if(debugPose()!=0){
   super.tick();getNavigation().stop();setXxa(0);setZza(0);setDeltaMovement(0,getDeltaMovement().y,0);
   yBodyRot=getYRot();setYHeadRot(getYRot());return;
  }
  if(CatDance.hold(this)){if(!level().isClientSide)entityData.set(CAMERA_TARGET,-1);super.tick();CatDance.hold(this);return;}
  LivingEntity retainedTarget=getTarget();
  super.tick();if(level().isClientSide||!isAlive())return;
  // Vanilla retaliation goals forget occluded targets; retain an existing valid opponent through cover.
  if(getTarget()==null&&valid(retainedTarget))setTarget(retainedTarget);
  entityData.set(CAMERA_TARGET,valid(getTarget())&&!isNoAi()?getTarget().getId():-1);
  digest();
  if(tickCount%20==0){reconcile();updateBoss();}
  if(isNoAi()){YutaGetoDuel.cancel(this);if(action()!=0||melee.active())finish();forcedRanged=false;retreatCast=false;recall();return;}
  if(YutaGetoDuel.control(this))return;
  if(retreatCooldown>0)retreatCooldown--;if(rangedCooldown>0)rangedCooldown--;
  if(cooldown>0)cooldown--;if(summonCooldown>0)summonCooldown--;if(guardCooldown>0)guardCooldown--;
  // Once discharged, the visible beam finishes even when its original target dies or leaves range.
  if((action()==UZUMAKI||action()==BLAST)&&vortexAge(0)>GetoVortexMotion.FIRE){
   getNavigation().stop();setDeltaMovement(getDeltaMovement().multiply(.15,1,.15));
   if(GetoVortexMotion.beamActive(vortexAge(0)))hitUzumaki(vortexAge(0));
   if(vortexAge(0)>=GetoVortexMotion.END)finish();return;
  }
  LivingEntity target=getTarget();entityData.set(FIGHTING,valid(target));
  if(!valid(target)){setTarget(null);melee.cancel();forcedRanged=false;if(action()!=0)finish();if(++missingTarget>80)recall();return;}missingTarget=0;
  // Track during preparation, then commit the swing instead of rotating the whole cat at contact.
  if(!GetoCloudMotion.active(action())||actionAge(0)<GetoCloudMotion.impact(action())-3){
   getLookControl().setLookAt(target,30,30);float yaw=(float)(Mth.atan2(target.getZ()-getZ(),target.getX()-getX())*180/Math.PI)-90;
   setYRot(Mth.rotLerp(.35f,getYRot(),yaw));
  }else setYHeadRot(getYRot());
  yBodyRot=getYRot();
  int a=action(),age=(int)actionAge(0);
  if(a==SUMMON){getNavigation().stop();if(age==12)summonChimeras(SpiritRules.attackRole(random.nextInt()),SpiritRules.attackRole(random.nextInt()));if(age>=26)finish();return;}
  if(a==RETREAT){
   getNavigation().stop();
   // A quick hop backwards: fast at take-off, easing into the landing, then the cast.
   double speed=.82*(1-age/11.0)+.12;
   Vec3 step=retreatDirection.scale(Math.max(0,speed));
   double lift=age<5?.24-.05*age:0;
   // Do not dash off ledges or into liquid; normal collision handles walls.
   var below=net.minecraft.core.BlockPos.containing(position().add(step.scale(2)).add(0,-.6,0));
   boolean safe=level().getFluidState(below).isEmpty()&&(level().getBlockState(below).isCollisionShapeFullBlock(level(),below)
     ||level().getBlockState(below.below()).isCollisionShapeFullBlock(level(),below.below()));
   if(safe)move(MoverType.SELF,step.add(0,lift,0));
   if(age==1&&level() instanceof ServerLevel l)l.sendParticles(ParticleTypes.LARGE_SMOKE,getX(),getY()+.2,getZ(),8,.3,.05,.3,.02);
   if(age>=10||horizontalCollision&&age>2){boolean cast=retreatCast;retreatCast=false;finish();if(cast&&rangedCooldown==0&&hasLineOfSight(target))beginBlast();}
   return;
  }
  if(a==BLAST){
   getNavigation().stop();setDeltaMovement(getDeltaMovement().multiply(.15,1,.15));
   if(age==18)fireUzumaki(target);
   if(GetoVortexMotion.beamActive(vortexAge(0)))hitUzumaki(vortexAge(0));
   if(vortexAge(0)>=GetoVortexMotion.END)finish();return;
  }
  if(a==UZUMAKI){
   getNavigation().stop();setDeltaMovement(getDeltaMovement().multiply(.15,1,.15));
   if(age==GetoVortexMotion.COMPRESS)playSound(SoundEvents.END_PORTAL_FRAME_FILL,.9f,.65f);
   if(age==GetoVortexMotion.FIRE)fireUzumaki(target);
   if(GetoVortexMotion.beamActive(age))hitUzumaki(age);
   if(age>=GetoVortexMotion.END)finish();return;
  }
  if(a!=0){
   if(a==YutaBladeMotion.GUARD){if(age>=18)finish();return;}
   if(GetoCloudMotion.active(a)){
    float frame=actionAge(0);double step=GetoCloudMotion.travel(a,frame)-GetoCloudMotion.travel(a,Math.max(0,frame-1.5f));
    double gap=distanceTo(target)-(1.9+target.getBbWidth()*.25);
    if(step>0&&gap>0&&onGround()&&hasLineOfSight(target)){
     Vec3 forward=getLookAngle().multiply(1,0,1).normalize();move(MoverType.SELF,forward.scale(Math.min(step,gap)));hasImpulse=true;
    }
    if(age>=GetoCloudMotion.impact(a)&&!strikeSpent)strike(target,a);
    if(age>=GetoCloudMotion.DURATION){
     if(distanceTo(target)<=4.8&&hasLineOfSight(target)&&melee.advance()){
      // Keep the selected continuation through this tiny recovery; no spell can replace it.
      entityData.set(ACTION,0);entityData.set(START,level().getGameTime());cooldown=2;
     }else{meleeExchanges++;finish();}
    }
   }else finish();
   return;
  }
  if(melee.active()){
   if(distanceTo(target)>4.8||!hasLineOfSight(target))melee.cancel();
   else{getNavigation().stop();if(cooldown==0)start(melee.action());return;}
  }
  if(!YutaGetoDuel.manages(this)&&SpiritRules.milestone(getHealth(),getMaxHealth(),2,milestones)){milestones|=2;List<CursedSpirit> consumed=spirits();CompoundTag aim=new CompoundTag();aim.putInt("Spirits",consumed.size());entityData.set(AIM,aim);vortexHits.clear();vortexImpact=false;start(UZUMAKI);for(CursedSpirit spirit:consumed)spirit.absorb();playSound(SoundEvents.EVOKER_PREPARE_ATTACK,1,.55f);return;}
  if(SpiritRules.milestone(getHealth(),getMaxHealth(),1,milestones)){milestones|=1;summonCooldown=0;}
  if(summonCooldown==0&&spirits.size()<(milestones==0?1:SpiritRules.LIMIT)){start(SUMMON);summonCooldown=160;return;}
  double distance=distanceTo(target);
  boolean mayDisengage=GetoMeleeSequence.canDisengage(meleeExchanges,distance,target.getY()-getY());
  if(cooldown==0&&distance<=28&&rangedCooldown==0&&onGround()&&hasLineOfSight(target)
      &&(forcedRanged||mayDisengage&&(distance>=7||random.nextFloat()<.42f))){
   Vec3 away=position().subtract(target.position()).multiply(1,0,1).normalize();
   retreatDirection=away.add(new Vec3(-away.z,0,away.x).scale(random.nextBoolean()?.35:-.35)).normalize();
   retreatCast=true;retreatCooldown=100+random.nextInt(41);start(RETREAT);return;
  }
  if(distance<2.4&&meleeExchanges>=2&&hurtTime>0&&retreatCooldown==0&&onGround()&&cooldown==0&&random.nextFloat()<.25f){
   Vec3 away=position().subtract(target.position()).multiply(1,0,1).normalize();
   retreatDirection=away.add(new Vec3(-away.z,0,away.x).scale(random.nextBoolean()?.30:-.30)).normalize();
   retreatCast=false;retreatCooldown=100+random.nextInt(41);start(RETREAT);return;
  }
  if(distance>3.15){if(tickCount%6==0)getNavigation().moveTo(target,1.3);if(horizontalCollision&&onGround())getJumpControl().jump();}
  else {getNavigation().stop();if(cooldown==0){
   int variant=random.nextInt(4);if(variant==lastCombo)variant=(variant+1+random.nextInt(3))%4;
   lastCombo=variant;melee.begin(variant);start(melee.action());
  }}
 }
 public float vortexAge(float partial){return action()==BLAST?dev.jujutsuvfx.core.RangedCatRules.miniAge(actionAge(partial)):actionAge(partial);}
 public double vortexScale(){return action()==BLAST?.6:1;}
 private void beginBlast(){
  entityData.set(AIM,new CompoundTag());vortexHits.clear();vortexImpact=false;rangedCooldown=220+random.nextInt(81);meleeExchanges=0;forcedRanged=false;start(BLAST);
  playSound(SoundEvents.EVOKER_PREPARE_ATTACK,.7f,1.3f);
 }
 private void strike(LivingEntity target,int action){
  if(strikeSpent)return;strikeSpent=true;
  if(!valid(target)||distanceTo(target)>3.8+target.getBbWidth()*.3||!getSensing().hasLineOfSight(target))return;
  Vec3 offset=target.getBoundingBox().getCenter().subtract(position());
  double yaw=Math.toRadians(getYRot()),fx=-Math.sin(yaw),fz=Math.cos(yaw);
  if(!GetoCloudMotion.inReach(action,offset.x*fx+offset.z*fz,offset.x*fz-offset.z*fx,offset.y,target.getBbWidth()*.3))return;
  float damage=target instanceof Player?5:10;
  if(target.hurt(damageSources().mobAttack(this),damage)){
   Vec3 d=target.position().subtract(position()).normalize();target.knockback(melee.finisher()?.45:.12,-d.x,-d.z);
   playSound(SoundEvents.PLAYER_ATTACK_SWEEP,.9f,.8f+random.nextFloat()*.25f);
   ((ServerLevel)level()).sendParticles(ParticleTypes.SWEEP_ATTACK,target.getX(),target.getY()+target.getBbHeight()*.5,target.getZ(),1,0,0,0,0);
   for(CursedSpirit s:spirits())s.pressure(target);
  }
 }
 private void fireUzumaki(LivingEntity target){
  ServerLevel l=(ServerLevel)level();Vec3 origin=spellOrigin(),axis=target.getBoundingBox().getCenter().subtract(origin).normalize();
  if(axis.lengthSqr()<.5)axis=getLookAngle();
  Vec3 end=origin.add(axis.scale(GetoVortexMotion.RANGE*vortexScale()));var clip=l.clip(new net.minecraft.world.level.ClipContext(origin,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
  CompoundTag aim=entityData.get(AIM).copy();aim.putDouble("X",axis.x);aim.putDouble("Y",axis.y);aim.putDouble("Z",axis.z);
  aim.putDouble("OriginX",origin.x);aim.putDouble("OriginY",origin.y);aim.putDouble("OriginZ",origin.z);
  aim.putDouble("Length",Math.min(GetoVortexMotion.RANGE*vortexScale(),origin.distanceTo(clip.getLocation())));entityData.set(AIM,aim);
  playSound(SoundEvents.GENERIC_EXPLODE.value(),1.6f,.65f);
 }
 private void hitUzumaki(float age){
  ServerLevel l=(ServerLevel)level();Vec3 origin=spellOrigin(),axis=beamDirection();double limit=beamLength(),length=GetoVortexMotion.reach(age,limit/vortexScale())*vortexScale();
  if(axis.lengthSqr()<.5)return;
  var box=new net.minecraft.world.phys.AABB(origin,origin.add(axis.scale(length))).inflate(GetoVortexMotion.RADIUS+2);
  for(LivingEntity e:l.getEntitiesOfClass(LivingEntity.class,box,this::valid)){
   if(vortexHits.contains(e.getUUID())||e instanceof CursedSpirit s&&getUUID().equals(s.ownerId()))continue;
   Vec3 v=e.getBoundingBox().getCenter().subtract(origin);double t=v.dot(axis);
   if(GetoVortexMotion.reaches(t/vortexScale(),v.subtract(axis.scale(t)).length()/vortexScale(),e.getBbWidth()*.5/vortexScale(),age,limit/vortexScale())){
    // One damage event per victim for the whole travelling discharge, never every render/tick.
    vortexHits.add(e.getUUID());
    if(e.hurt(damageSources().indirectMagic(this,this),e instanceof Player?(action()==BLAST?7:14):(action()==BLAST?26:90))){
     e.setDeltaMovement(e.getDeltaMovement().add(axis.scale(1.1)).add(0,.2,0));e.hurtMarked=true;
     l.sendParticles(ParticleTypes.DRAGON_BREATH,e.getX(),e.getY()+e.getBbHeight()*.5,e.getZ(),22,.35,.5,.35,.08);
    }
   }
  }
  if(!vortexImpact&&length>=limit){vortexImpact=true;Vec3 end=origin.add(axis.scale(limit));
   l.sendParticles(ParticleTypes.EXPLOSION,end.x,end.y,end.z,3,.6,.6,.6,.02);
   l.sendParticles(ParticleTypes.DRAGON_BREATH,end.x,end.y,end.z,36,.7,.7,.7,.14);
   l.playSound(null,end.x,end.y,end.z,SoundEvents.GENERIC_EXPLODE.value(),net.minecraft.sounds.SoundSource.HOSTILE,1.4f,.8f);
  }
 }
 @Override public boolean hurt(DamageSource source,float amount){
  if(!YutaGetoDuel.acceptsDamage(this,source))return false;
  if(!level().isClientSide&&debugPose()==0&&source.getEntity() instanceof LivingEntity attacker&&valid(attacker)){
   setTarget(attacker);missingTarget=0;
   if(action()!=UZUMAKI&&action()!=BLAST&&action()!=RETREAT&&guardCooldown==0&&!DomainFreeze.hold(this)&&random.nextFloat()<.35f){
    if(spirits.size()<SpiritRules.LIMIT)summonChimeras(SpiritRules.GUARD,SpiritRules.ASSAULT);guardCooldown=120;start(YutaBladeMotion.GUARD);
   }
   CursedSpirit guard=spirits().stream().filter(s->s.role()==SpiritRules.GUARD&&distanceToSqr(s)<36).findFirst().orElse(null);
   if(guard!=null&&source.getEntity()!=guard){guard.hurt(source,amount*.7f);amount*=.3f;playSound(SoundEvents.SHIELD_BLOCK,.8f,.7f);}
  }
  amount=YutaGetoDuel.limitDamage(this,source,amount);
  return amount>0&&super.hurt(source,amount);
 }
 private void updateBoss(){
  if(boss==null)boss=new ServerBossEvent(getDisplayName(),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
  Set<ServerPlayer> desired=new HashSet<>();if(getTarget() instanceof ServerPlayer p&&valid(p))desired.add(p);
  for(ServerPlayer p:new ArrayList<>(boss.getPlayers()))if(!desired.contains(p))boss.removePlayer(p);
  desired.forEach(boss::addPlayer);boss.setProgress(Mth.clamp(getHealth()/getMaxHealth(),0,1));
 }
 @Override public void die(DamageSource source){YutaGetoDuel.cancel(this);recall();releasePrey();if(boss!=null)boss.removeAllPlayers();super.die(source);}
 @Override public void remove(RemovalReason reason){if(boss!=null)boss.removeAllPlayers();if(!level().isClientSide)releasePrey();if(reason.shouldDestroy()&&!level().isClientSide)recall();super.remove(reason);}
 @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(danceStyle()!=0||debugPose()!=0)tag.putBoolean("NoAI",super.isNoAi());tag.putInt("GetoDebugPose",debugPose());tag.putInt("GetoMilestones",milestones);int i=0;for(UUID id:spirits){tag.putUUID("GetoSpirit"+i,id);tag.putLong("GetoSpiritUntil"+(i++),spiritExpiry.getOrDefault(id,0L));}}
 @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);int pose=tag.getInt("GetoDebugPose");entityData.set(DEBUG_POSE,GetoDebugPose.valid(pose)?pose:0);entityData.set(START,level().getGameTime());milestones=tag.getInt("GetoMilestones")&3;spirits.clear();spiritExpiry.clear();for(int i=0;i<SpiritRules.LIMIT;i++)if(tag.hasUUID("GetoSpirit"+i)){UUID id=tag.getUUID("GetoSpirit"+i);spirits.add(id);spiritExpiry.put(id,Math.min(tag.getLong("GetoSpiritUntil"+i),level().getGameTime()+1200));}}
}
