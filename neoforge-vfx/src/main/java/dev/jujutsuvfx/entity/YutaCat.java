package dev.jujutsuvfx.entity;

import dev.jujutsuvfx.core.YutaBladeMotion;
import dev.jujutsuvfx.core.YutaActionClock;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.core.CursedSpeechShape;
import dev.jujutsuvfx.server.CursedSpeech;
import dev.jujutsuvfx.server.YutaGetoDuel;
import dev.jujutsuvfx.server.CatTerrain;
import dev.jujutsuvfx.server.YutaDomain;
import dev.jujutsuvfx.core.YutaDomainRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.server.DomainFreeze;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.*;
import static dev.jujutsuvfx.core.YutaBladeMotion.*;

/** A separate swordsman: independent copied techniques and sword domain, without scripted Shinjuku scenes. */
public final class YutaCat extends TamableAnimal implements DancingCat {
 private static final EntityDataAccessor<CompoundTag> DUEL=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.COMPOUND_TAG);
 public CompoundTag duelState(){return entityData.get(DUEL);}
 public void setDuelState(CompoundTag state){if(!level().isClientSide)entityData.set(DUEL,state.copy());}
 public void cinematicPose(int action,int age){
  if(entityData.get(ACTION)!=action){finish();start(action);}
  entityData.set(AGE,age);entityData.set(HIT_STOP,0);getNavigation().stop();
  setDeltaMovement(0,getDeltaMovement().y,0);setXxa(0);setZza(0);
 }
 public void beginCinematicDraw(){finish();drawCommitted=true;start(DRAW);}
 public void cinematicRing(){finish();rika.scriptedRing();start(RIKA_SUMMON);}
 public void cinematicRika(boolean active){if(active)rika.sustainScripted();else rika.cancel();}
 public void cinematicRelease(){rika.cancel();cancelCopiedTechnique();finish();getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);}
 private static final EntityDataAccessor<CompoundTag> DEBUG_DANCE=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.COMPOUND_TAG);
 public CompoundTag danceState(){return entityData.get(DEBUG_DANCE);}
 public void danceState(CompoundTag tag){entityData.set(DEBUG_DANCE,tag);}
 public void setDanceStyle(int style){
  if(level().isClientSide||style<0||style>2)return;
  YutaGetoDuel.cancel(this);rika.cancel();cancelCopiedTechnique();finish();entityData.set(FORCED_DANCE,false);
  dev.jujutsuvfx.server.CatDance.start(this,style);
 }

 private static final EntityDataAccessor<Boolean> BLADE_POWER=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Boolean> DOMAIN_POWER=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Boolean> DRAWN=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Integer> CAMERA_TARGET=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> CYCLE=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> HAND=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> HIT_STOP=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Float> DANCE_YAW=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Boolean> FORCED_DANCE=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Integer> BLADE_MODE=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> BEHAVIOR=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> COPIED_KIND=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> COPIED_AGE=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Boolean> COPIED_LARGE=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.BOOLEAN);
 public static final int STILL=0,NORMAL=1,AGGRESSIVE=2,AUTO=0,ON=1,OFF=2;
 private static final EntityDataAccessor<CompoundTag> RIKA=SynchedEntityData.defineId(YutaCat.class,EntityDataSerializers.COMPOUND_TAG);
 private final dev.jujutsuvfx.server.RikaSupport rika=new dev.jujutsuvfx.server.RikaSupport(this);
 private int rikaFollowTicks;
 private UUID rikaFollowTarget;
 public CompoundTag rikaState(){return entityData.get(RIKA);}
 public void setRikaState(CompoundTag state){if(!level().isClientSide)entityData.set(RIKA,state.copy());}
 public void queueRikaFollow(LivingEntity target){if(canFight(target)){rikaFollowTarget=target.getUUID();rikaFollowTicks=30;travelCooldown=0;}}
 private int copiedDuration;
 private final YutaActionClock visualClock=new YutaActionClock();
 private boolean drawCommitted;
 private int getoEngagedTicks;
 private int cooldown,sequence,travelCooldown,completedAttacks,guardCooldown,guardQuiet,areaCooldown,areaSequence;
 private int guardContact=-100;
 private int danceCooldown=300;
 private Vec3 danceOrigin=Vec3.ZERO;
 private int speechCooldown=80,speechFollowTicks,domainMilestones,pendingDomainMilestone;
 private Vec3 travelVector=Vec3.ZERO,travelOrigin=Vec3.ZERO;
 private boolean controlledLeap,travelHadNoGravity;
 private Vec3 dashDirection=Vec3.ZERO;
 private final Set<UUID> struck=new HashSet<>();
 private UUID provoker;
 private long provokedUntil;
 public YutaCat(EntityType<? extends TamableAnimal> type,Level level){super(type,level);setPersistenceRequired();xpReward=50;danceCooldown=300+random.nextInt(401);}
 public static AttributeSupplier.Builder createAttributes(){return SorcererCat.createAttributes();}
 @Override protected void defineSynchedData(SynchedEntityData.Builder builder){
  super.defineSynchedData(builder);builder.define(DUEL,new CompoundTag());builder.define(DEBUG_DANCE,new CompoundTag());builder.define(BLADE_POWER,false);builder.define(DOMAIN_POWER,false);builder.define(DRAWN,false);builder.define(ACTION,IDLE);builder.define(AGE,0);builder.define(CYCLE,0);builder.define(HAND,2);builder.define(HIT_STOP,0);builder.define(DANCE_YAW,0f);builder.define(FORCED_DANCE,false);builder.define(BLADE_MODE,AUTO);builder.define(BEHAVIOR,AGGRESSIVE);builder.define(COPIED_KIND,-1);builder.define(COPIED_AGE,0);builder.define(COPIED_LARGE,false);builder.define(RIKA,new CompoundTag());builder.define(CAMERA_TARGET,-1);
 }
 public float bladePower(float partial){return !bladeDrawn()||bladeMode()==OFF?0:bladeMode()==ON?1:entityData.get(BLADE_POWER)?dev.jujutsuvfx.core.YutaCombatRules.bladeFlame(bladeAction(),bladeAge(partial)):0;}
 public boolean forcedDance(){return danceStyle()!=0;}
 public int bladeMode(){return entityData.get(BLADE_MODE);}
 public int behavior(){return entityData.get(BEHAVIOR);}
 public boolean copiedSpellActive(){return entityData.get(COPIED_KIND)>=0;}
 public int copiedSpellKind(){return entityData.get(COPIED_KIND);}
 public boolean copiedSpellLarge(){return entityData.get(COPIED_LARGE);}
 public float copiedSpellAge(){return entityData.get(COPIED_AGE);}
 public void prepareCopiedTechnique(int kind,boolean large,int duration){
  if(level().isClientSide)return;
  finish();getNavigation().stop();setDeltaMovement(Vec3.ZERO);
  entityData.set(COPIED_KIND,kind);entityData.set(COPIED_LARGE,large);entityData.set(COPIED_AGE,0);copiedDuration=Math.max(1,duration);
 }
 private void cancelCopiedTechnique(){dev.jujutsuvfx.server.YutaCopiedTechniques.cancel(this);entityData.set(COPIED_KIND,-1);}
 public void setBladeMode(int mode){if(!level().isClientSide&&mode>=AUTO&&mode<=OFF)entityData.set(BLADE_MODE,mode);}
 public void setBladeDrawn(boolean drawn){
  if(level().isClientSide)return;
  if(bladeAction()!=DANCE)finish();
  drawCommitted=true;entityData.set(DRAWN,drawn);
 }
 public void setForcedDance(boolean forced){setDanceStyle(forced?1:0);}
 public void setBehavior(int mode){
  if(level().isClientSide||mode<STILL||mode>AGGRESSIVE)return;
  rika.cancel();setForcedDance(false);cancelCopiedTechnique();finish();setTarget(null);provoker=null;provokedUntil=0;
  entityData.set(BEHAVIOR,mode);getNavigation().stop();setDeltaMovement(Vec3.ZERO);
 }
 @Override public boolean isNoAi(){return danceStyle()!=0||super.isNoAi()||behavior()==STILL;}
 @Override public boolean isPushable(){return behavior()!=STILL&&!dancing()&&super.isPushable();}
 @Override public void knockback(double strength,double x,double z){if(behavior()!=STILL&&!dancing())super.knockback(strength,x,z);}
 private static float loopDanceAge(float age){return age<10?age:10+(age-10)%176;}

 public LivingEntity cameraTarget(){return level().getEntity(entityData.get(CAMERA_TARGET)) instanceof LivingEntity target?target:null;}
 public boolean domainEmpowered(){return entityData.get(DOMAIN_POWER);}
 public boolean bladeDrawn(){return entityData.get(DRAWN);}
 public int bladeAction(){return level().isClientSide&&visualClock.ready()?visualClock.action():entityData.get(ACTION);}
 public int bladeHand(){return level().isClientSide&&visualClock.ready()?visualClock.hand():entityData.get(HAND);}
 public float bladeAge(float partial){float age=level().isClientSide&&visualClock.ready()?visualClock.age(partial):entityData.get(AGE);return bladeAction()==DANCE&&forcedDance()?loopDanceAge(age):age;}
 public int previousBladeAction(){return visualClock.oldAction();}
 public int previousBladeHand(){return visualClock.oldHand();}
 public float previousBladeAge(){return visualClock.oldAction()==DANCE&&visualClock.oldAge()>200?loopDanceAge(visualClock.oldAge()):visualClock.oldAge();}
 public float bladeBlend(float partial){return visualClock.blend(partial);}
 private void start(int action){
  entityData.set(BLADE_POWER,(dev.jujutsuvfx.core.YutaAreaMotion.active(action)||(action==DRAW||action>=CUT_LEFT&&action<=RISING)&&random.nextFloat()<.60f));
  entityData.set(ACTION,action);entityData.set(AGE,0);entityData.set(HIT_STOP,0);
  entityData.set(HAND,entityData.get(HAND)==1?2:1);entityData.set(CYCLE,entityData.get(CYCLE)+1);
  struck.clear();getNavigation().stop();
 }
 private void finish(){
  if(entityData.get(ACTION)==IDLE)return;
  boolean moving=isTravel(entityData.get(ACTION));
  if(entityData.get(ACTION)==DANCE){entityData.set(FORCED_DANCE,false);danceCooldown=900+random.nextInt(901);}
  if(entityData.get(ACTION)==GUARD)guardCooldown=18+random.nextInt(17);
  entityData.set(BLADE_POWER,false);
  if(controlledLeap){setNoGravity(travelHadNoGravity);controlledLeap=false;setDeltaMovement(Vec3.ZERO);}
  if(rate(entityData.get(ACTION))==2)completedAttacks++;
  entityData.set(ACTION,IDLE);entityData.set(AGE,0);entityData.set(HIT_STOP,0);entityData.set(CYCLE,entityData.get(CYCLE)+1);
  cooldown=moving?0:((bladeDrawn()?3+random.nextInt(4):2+random.nextInt(3))+1)/2;struck.clear();
 }
 public boolean wantsDomain(){
  // Screenplay: the pair's average health stage; ordinary fights: Yuta's own 50%/10% milestones.
  boolean cue=YutaGetoDuel.domainCue(this);
  return (cue||!YutaGetoDuel.manages(this)&&YutaDomainRules.milestone(getHealth(),getMaxHealth(),domainMilestones)!=0)
   &&!isNoAi()&&canFight(getTarget())&&distanceToSqr(getTarget())<=36*36&&!YutaDomain.active(this);
 }
 public void markDomainOpened(){
  domainMilestones=YutaDomainRules.consume(domainMilestones,pendingDomainMilestone);pendingDomainMilestone=0;
 }
 @Override public void setHealth(float health){
  super.setHealth(health);
  if(dev.jujutsuvfx.core.CatDomainRules.fullyRecovered(getHealth(),getMaxHealth()))domainMilestones=0;
 }
 public void beginDomain(){
  if(level().isClientSide||!wantsDomain())return;
  pendingDomainMilestone=YutaDomainRules.milestone(getHealth(),getMaxHealth(),domainMilestones);
  cancelCopiedTechnique();finish();start(DOMAIN);setDeltaMovement(Vec3.ZERO);YutaDomain.prepare(this);
 }
 @Override protected void registerGoals(){
  goalSelector.addGoal(0,new FloatGoal(this));
  goalSelector.addGoal(1,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
   @Override public boolean canUse(){return !isNoAi()&&(bladeAction()!=IDLE||canFight(getTarget()));}
   @Override public boolean canContinueToUse(){return canUse();}
   @Override public boolean requiresUpdateEveryTick(){return true;}
  });
  goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.8));
  goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,8));
  goalSelector.addGoal(8,new RandomLookAroundGoal(this));
  targetSelector.addGoal(0,new HurtByTargetGoal(this){
   @Override public boolean canUse(){return canFight(getLastHurtByMob())&&super.canUse();}
   @Override public boolean canContinueToUse(){return canFight(getTarget());}
  });
  targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(this,LivingEntity.class,5,false,false,t->(t instanceof SukunaCat||t instanceof GetoCat)&&canFight(t)){
   @Override protected AABB getTargetSearchArea(double range){return getBoundingBox().inflate(range);}
   @Override public boolean canContinueToUse(){return canFight(getTarget());}
  });
 }
 public boolean canFight(LivingEntity target){
  if(YutaGetoDuel.suppressed(this,target)||forcedDance()||behavior()==STILL||target==null||target==this||!target.isAlive()||target.isRemoved()||target.isSpectator()||target instanceof ArmorStand
    ||target.level()!=level()||isAlliedTo(target)||distanceToSqr(target)>128*128)return false;
  if(target instanceof Player p&&(p.isCreative()||p.getAbilities().invulnerable))return false;
  if(target instanceof TamableAnimal pet&&getOwnerUUID()!=null&&getOwnerUUID().equals(pet.getOwnerUUID()))return false;
  if(target instanceof CursedSpirit spirit&&!spirit.recalling()
    &&level() instanceof ServerLevel server&&spirit.ownerId()!=null&&server.getEntity(spirit.ownerId()) instanceof GetoCat owner
    &&canFight(owner))return true;
  return (target instanceof SukunaCat||target instanceof GetoCat)&&behavior()==AGGRESSIVE&&VfxConfig.cinematic()||target.getUUID().equals(provoker)&&level().getGameTime()<provokedUntil;
 }
 @Override public boolean hurt(DamageSource source,float amount){
  boolean bypass=source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)||source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD);
  Vec3 from=source.getSourcePosition();
  boolean frontal=from!=null&&from.subtract(position()).normalize().dot(getLookAngle())>-.10;
  float reduction=dev.jujutsuvfx.core.YutaCombatRules.guardMultiplier(bladeDrawn()&&bladeAction()==GUARD&&entityData.get(AGE)>=2,frontal,bypass);
  amount=YutaGetoDuel.limitDamage(this,source,amount*reduction*rika.guardMultiplier(source,bypass));
  if(amount<=0)return false;
  boolean accepted=super.hurt(source,amount);
  if(accepted&&!level().isClientSide){danceCooldown=Math.max(danceCooldown,200);if(bladeAction()==DANCE)finish();}
  if(accepted&&!level().isClientSide&&reduction<1){
   guardContact=tickCount;guardQuiet=0;playSound(SoundEvents.SHIELD_BLOCK,.8f,1.35f);
   if(level() instanceof ServerLevel level)level.sendParticles(ParticleTypes.CRIT,getX(),getEyeY(),getZ(),6,.25,.15,.25,.12);
  }
  if(accepted&&!level().isClientSide&&source.getEntity() instanceof LivingEntity attacker&&attacker!=this){
   if(bladeAction()==SPEECH&&entityData.get(AGE)<CursedSpeechShape.RELEASE){speechCooldown=Math.max(speechCooldown,100);finish();}
   if(attacker instanceof CursedSpirit spirit&&level().getEntity(spirit.ownerEntity()) instanceof GetoCat owner)attacker=owner;
   provoker=attacker.getUUID();provokedUntil=level().getGameTime()+600;if(canFight(attacker)){setTarget(attacker);rika.hurtBy(attacker,bypass);}
  }
  return accepted;
 }
 private boolean guardPressure(LivingEntity target){
  if(target==null||distanceToSqr(target)>24*24||!getSensing().hasLineOfSight(target))return false;
  if(target instanceof SorcererCat rival){
   if(dev.jujutsuvfx.server.CatSorcery.busy(rival))return true;
   return distanceToSqr(target)<5*5&&rival.combat().threatens(this);
  }
  return target instanceof Player player&&(player.isUsingItem()||player.swinging&&distanceToSqr(player)<5*5)
   ||distanceToSqr(target)<4*4&&target.swinging;
 }
 private boolean tryGuard(LivingEntity target){
  if(!bladeDrawn()||guardCooldown>0||!onGround()||!guardPressure(target))return false;
  guardCooldown=8;
  if(random.nextFloat()>.60f)return false;
  guardQuiet=0;start(GUARD);return true;
 }
 private boolean dancing(){return isAlive()&&entityData.get(ACTION)==DANCE;}
 private void holdDance(){
  float yaw=entityData.get(DANCE_YAW);
  setDeltaMovement(Vec3.ZERO);setSpeed(0);setXxa(0);setYya(0);setZza(0);
  setYRot(yaw);yRotO=yaw;yBodyRot=yBodyRotO=yaw;setYHeadRot(yaw);yHeadRotO=yaw;setXRot(0);xRotO=0;
  if(!level().isClientSide){getNavigation().stop();setPos(danceOrigin.x,danceOrigin.y,danceOrigin.z);}
 }
 @Override public void travel(Vec3 input){if(dancing()||behavior()==STILL){setDeltaMovement(Vec3.ZERO);return;}super.travel(input);}
 @Override public void tick(){
  if(dev.jujutsuvfx.server.CatDance.hold(this)){if(!level().isClientSide)entityData.set(CAMERA_TARGET,-1);super.tick();dev.jujutsuvfx.server.CatDance.hold(this);return;}
  if(dancing()){
   if(!level().isClientSide&&level().noCollision(this,getBoundingBox().move(0,-.08,0)))finish();
   else holdDance();
  }
  if(!level().isClientSide&&controlledLeap)setDeltaMovement(Vec3.ZERO);
  super.tick();if(dancing())holdDance();if(level().isClientSide){
   visualClock.tick(entityData.get(CYCLE),entityData.get(ACTION),entityData.get(HAND),entityData.get(AGE),entityData.get(HIT_STOP)>0);return;
  }
  if(!isNoAi()&&bladeAction()==IDLE&&!copiedSpellActive()&&tickCount%8==0)prioritizeSpirits();
  LivingEntity cameraOpponent=getTarget();
  if(cameraOpponent instanceof CursedSpirit spirit&&level() instanceof ServerLevel server&&spirit.ownerId()!=null
    &&server.getEntity(spirit.ownerId()) instanceof GetoCat owner)cameraOpponent=owner;
  boolean getoPair=VfxConfig.cinematic()&&cameraOpponent instanceof GetoCat&&canFight(cameraOpponent);
  if(getoPair)getoEngagedTicks=Math.min(160,getoEngagedTicks+1);else if(cameraOpponent!=null)getoEngagedTicks=0;
  entityData.set(CAMERA_TARGET,canFight(cameraOpponent)&&!isNoAi()?cameraOpponent.getId():-1);
  entityData.set(DOMAIN_POWER,YutaDomain.damageMultiplier(this)>1f);
  if(!isAlive()||isNoAi()){rika.cancel();if(bladeAction()==DRAW){entityData.set(DRAWN,true);}finish();if(copiedSpellActive())cancelCopiedTechnique();return;}
  if(YutaGetoDuel.control(this))return;
  if(DomainFreeze.hold(this)){rika.cancel();if(bladeAction()==DANCE||bladeAction()==RIKA_SUMMON)finish();return;}
  rika.tick();if(rikaFollowTicks>0)rikaFollowTicks--;
  if(areaCooldown>0)areaCooldown--;
  if(guardCooldown>0)guardCooldown--;
  if(travelCooldown>0)travelCooldown--;
  if(speechCooldown>0)speechCooldown--;if(speechFollowTicks>0)speechFollowTicks--;
  if(entityData.get(HIT_STOP)>0){entityData.set(HIT_STOP,entityData.get(HIT_STOP)-1);return;}
  LivingEntity target=getTarget();
  if(!canFight(target)){setTarget(null);target=null;}
  if(target!=null&&target.getUUID().equals(provoker))provokedUntil=level().getGameTime()+600;
  if(dev.jujutsuvfx.server.YutaCopiedTechniques.busy(this)){
   getNavigation().stop();if(target!=null)face(target);
   entityData.set(COPIED_AGE,Math.min(copiedDuration,entityData.get(COPIED_AGE)+1));return;
  }
  if(copiedSpellActive())entityData.set(COPIED_KIND,-1);
  int action=bladeAction(),age=entityData.get(AGE);
  boolean danceSafe=dev.jujutsuvfx.core.YutaDanceMotion.safe(target!=null,onGround(),isInWaterOrBubble()||isInLava(),isPassenger()||isVehicle()||isInSittingPose(),hurtTime>0||isOnFire(),YutaDomain.active(this));
  if(!danceSafe)danceCooldown=Math.max(danceCooldown,200);
  else if(danceCooldown>0)danceCooldown--;
  if(action==DANCE){
   if(!danceSafe){finish();return;}
   holdDance();
   if(!forcedDance()&&age+1>=duration(DANCE))finish();else entityData.set(AGE,age+1);
   return;
  }
  if(action==IDLE){
   if(cooldown>0)cooldown--;
   if(target!=null)face(target);
   if(!YutaGetoDuel.manages(this)&&(shouldDraw(getHealth(),getMaxHealth(),drawCommitted)||cinematicDraw(getoPair,getoEngagedTicks,bladeDrawn()))){
    drawCommitted=true;start(DRAW);return;
   }
   if(target==null){
    if(danceSafe&&danceCooldown==0){
     if(danceSpace()){danceOrigin=position();entityData.set(DANCE_YAW,getYRot());start(DANCE);holdDance();}
     else danceCooldown=60+random.nextInt(61);
    }
    return;
   }
   if(wantsDomain()){beginDomain();return;}
   if(rika.ranged(target)){getNavigation().stop();return;}
   if(!YutaGetoDuel.manages(this)&&rika.beginSummon()){start(RIKA_SUMMON);return;}
   if(YutaDomain.containChase(this,target))return;
   if(YutaDomain.needsSword(this)){start(PICK_SWORD);return;}
   if(tryAreaAttack(target))return;
   if(!YutaDomain.opened(this)&&speechCooldown==0&&distanceToSqr(target)>=2.5*2.5&&distanceToSqr(target)<=11*11
     &&target.getPersistentData().getLong("JujutsuSpeechResistUntil")<=level().getGameTime()&&getSensing().hasLineOfSight(target)){
    start(SPEECH);speechCooldown=CursedSpeechShape.COOLDOWN+random.nextInt(60);CursedSpeech.charge(this,target);return;
   }
   if(rikaFollowTicks>0&&target.getUUID().equals(rikaFollowTarget)&&bladeDrawn()&&distanceToSqr(target)<7*7&&hasLineOfSight(target)){
    rikaFollowTicks=0;start(LUNGE);return;
   }
   if(tryGuard(target))return;
   if(tryTravel(target))return;
   double reach=bladeDrawn()?2.8:1.8;
   if(distanceTo(target)>reach||!getSensing().hasLineOfSight(target)){
    if(tickCount%5==0)getNavigation().moveTo(target,1.22);return;
   }
   getNavigation().stop();
   if(cooldown==0){
    int next=speechFollowTicks>0?(bladeDrawn()?LUNGE:BRAWL_BASE+CatCombatMoves.UPPERCUT):!bladeDrawn()?chooseBrawl(sequence++):chooseSword(sequence++);
    speechFollowTicks=0;start(next);
   }
   return;
  }
  getNavigation().stop();setDeltaMovement(getDeltaMovement().multiply(.15,1,.15));
  if(action==GUARD){
   if(target!=null)face(target);
   boolean pressure=guardPressure(target)||tickCount-guardContact<5;
   guardQuiet=pressure?0:guardQuiet+1;
   if(target==null||age>=46||age>=7&&guardQuiet>=5){finish();return;}
  }else if(dev.jujutsuvfx.core.YutaAreaMotion.active(action)){
   if(age<5&&target!=null)face(target);
   if(age==impact(action)-3)playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.4f,.65f);
   if(age==impact(action))strikeArea(action);
  }else if(action==RIKA_SUMMON){
   if(!rika.preparing()){finish();return;}
   if(age<8&&target!=null)face(target);
   if(age==22)playSound(SoundEvents.AMETHYST_BLOCK_CHIME,.45f,1.45f);
   if(age+1>=duration(RIKA_SUMMON)){rika.manifest();finish();return;}
  }else if(action==DOMAIN){
   if(age<8&&target!=null)face(target);
  }else if(action==PICK_SWORD){
   if(age==YutaDomainRules.PICK_CONTACT)YutaDomain.pickSword(this);
  }else if(action==SPEECH){
   if(age<=CursedSpeechShape.RELEASE&&target!=null)face(target);
   if(age==CursedSpeechShape.RELEASE&&CursedSpeech.release(this,target)){rika.speech(target);speechFollowTicks=40;travelCooldown=0;completedAttacks=0;}
  }else if(isTravel(action)){
   if(target!=null)face(target);
   Vec3 before=position();
   Vec3 desired=YutaDomain.containMovement(this,travelPoint(travelOrigin,travelVector,action,age+1));
   CatTerrain.clearStep(this,position(),desired);
   move(MoverType.SELF,desired.subtract(position()));hasImpulse=true;hurtMarked=true;
   if(age%2==0&&level() instanceof ServerLevel level)level.sendParticles(ParticleTypes.CLOUD,getX(),getY()+.2,getZ(),2,.13,.07,.13,.025);
   if(horizontalCollision&&position().distanceToSqr(before)<.001){finish();return;}
  }else if(action==DRAW){
   if(age<DASH_START&&target!=null)face(target);
   if(age==THROW)playSound(SoundEvents.PLAYER_ATTACK_SWEEP,.8f,.85f);
   if(age==CATCH){entityData.set(DRAWN,true);playSound(SoundEvents.ARMOR_EQUIP_IRON.value(),.6f,1.5f);}
   if(age==DASH_START){
    Vec3 direction=target==null?getLookAngle():target.position().subtract(position());
    dashDirection=new Vec3(direction.x,0,direction.z).normalize();
    if(dashDirection.lengthSqr()<.01)dashDirection=new Vec3(0,0,1);
    playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1f,.7f);
   }
   double step=dashStep(age);
   if(step>0){
    Vec3 before=position();setDeltaMovement(0,getDeltaMovement().y,0);
    Vec3 next=YutaDomain.containMovement(this,position().add(dashDirection.scale(step)));
    CatTerrain.clearStep(this,position(),next);
    move(MoverType.SELF,next.subtract(position()));hasImpulse=true;hurtMarked=true;
    strikePath(before,position(),24);
    if(level() instanceof ServerLevel level)level.sendParticles(ParticleTypes.CLOUD,getX(),getY()+.15,getZ(),2,.13,.05,.13,.025);
   }
  }else{
   // Process every crossed animation frame, including odd contacts at double speed.
   int end=Math.min(age+rate(action),duration(action)+1);
   for(int frame=age;frame<end;frame++){
    entityData.set(AGE,frame);
    if(frame<impact(action)-2&&target!=null)face(target);
    Vec3 f=new Vec3(getLookAngle().x,0,getLookAngle().z).normalize();
    double step=attackStep(action,frame);
    if(step>0){Vec3 next=YutaDomain.containMovement(this,position().add(f.scale(step)));if(action==LUNGE)CatTerrain.clearStep(this,position(),next);move(MoverType.SELF,next.subtract(position()));hasImpulse=true;hurtMarked=true;}
    if(frame==impact(action)-2)playSound(SoundEvents.PLAYER_ATTACK_SWEEP,.75f,action==CHOP?.7f:1.15f);
    if(contact(action,frame)){
     if(multiple(action))struck.clear();
     int move=brawlMove(action);
     float damage=move==CatCombatMoves.RAPID?4:move!=0?move==CatCombatMoves.KICK?18:12:action==CROSS?12:action==CHOP?24:20;
     double reach=bladeDrawn()?3.2:2.2;
     strikePath(position(),position().add(f.scale(reach)),damage);
    }
   }
  }
  if(age+rate(action)>duration(action))finish();else entityData.set(AGE,age+rate(action));
 }
 private void prioritizeSpirits(){
  if(!canFight(getTarget()))return;
  CursedSpirit nearest=level().getEntitiesOfClass(CursedSpirit.class,getBoundingBox().inflate(14),
    s->canFight(s)&&!s.recalling()&&distanceToSqr(s)<=14*14&&hasLineOfSight(s)).stream()
    .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
  if(nearest!=null&&(!(getTarget() instanceof CursedSpirit)||!canFight(getTarget())
    ||distanceToSqr(nearest)<distanceToSqr(getTarget())*.65))setTarget(nearest);
 }
 private boolean tryAreaAttack(LivingEntity target){
  if(!bladeDrawn()||areaCooldown>0||!onGround()||target==null||distanceToSqr(target)>10*10||!hasLineOfSight(target))return false;
  int nearby=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(9),this::canFight).size();
  if(!(target instanceof CursedSpirit)&&nearby<2)return false;
  int action=dev.jujutsuvfx.core.YutaAreaMotion.SWEEP+Math.floorMod(areaSequence++,3);
  if(distanceToSqr(target)>6*6)action=dev.jujutsuvfx.core.YutaAreaMotion.FAN;
  if(distanceToSqr(target)>dev.jujutsuvfx.core.YutaAreaMotion.radius(action)*dev.jujutsuvfx.core.YutaAreaMotion.radius(action))return false;
  start(action);areaCooldown=65+random.nextInt(26);return true;
 }
 private void strikeArea(int action){
  if(!(level() instanceof ServerLevel server))return;
  float radius=dev.jujutsuvfx.core.YutaAreaMotion.radius(action);
  Vec3 forward=getLookAngle().multiply(1,0,1).normalize(),origin=position().add(0,.7,0);
  DamageSource source=new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
    .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("jujutsuvfx","yuta_melee"))),this);
  for(LivingEntity victim:server.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius,5,radius),this::canFight)){
   Vec3 d=victim.getBoundingBox().getCenter().subtract(origin);
   if(struck.contains(victim.getUUID())||!dev.jujutsuvfx.core.YutaAreaMotion.reaches(action,d.x,d.y,d.z,forward.x,forward.z,victim.getBbWidth()*.5)
      ||!hasLineOfSight(victim))continue;
   struck.add(victim.getUUID());
   if(!dev.jujutsuvfx.server.TechniqueDamageRules.hurtYutaMelee(victim,source,dev.jujutsuvfx.core.YutaAreaMotion.damage(action)))continue;
   YutaDomain.melee(this,victim);
   victim.knockback(.4,-d.x,-d.z);
   if(action==dev.jujutsuvfx.core.YutaAreaMotion.SLAM&&!DomainFreeze.hold(victim)){victim.setDeltaMovement(victim.getDeltaMovement().add(0,.20,0));victim.hurtMarked=true;}
   server.sendParticles(ParticleTypes.CRIT,victim.getX(),victim.getY()+.7,victim.getZ(),8,.3,.35,.3,.18);
  }
  entityData.set(HIT_STOP,2);
  playSound(SoundEvents.PLAYER_ATTACK_STRONG,1.5f,.6f);
  if(action==dev.jujutsuvfx.core.YutaAreaMotion.SLAM)server.sendParticles(ParticleTypes.CLOUD,getX(),getY()+.1,getZ(),36,2.3,.08,2.3,.08);
 }
 private boolean danceSpace(){
  // Production mesh envelope over the whole loop, including renderer scale and facing.
  double angle=Math.toRadians(getYRot()),c=Math.cos(angle),s=Math.sin(angle);
  double minX=0,minZ=0,maxX=0,maxZ=0;
  for(double x:new double[]{-.7,2.9})for(double z:new double[]{-1.65,3.1}){
   double dx=1.5*(x*c+z*s),dz=1.5*(x*s-z*c);
   minX=Math.min(minX,dx);maxX=Math.max(maxX,dx);minZ=Math.min(minZ,dz);maxZ=Math.max(maxZ,dz);
  }
  return level().noCollision(this,new AABB(getX()+minX,getY()+.02,getZ()+minZ,getX()+maxX,getY()+4.1,getZ()+maxZ));
 }
 private boolean tryTravel(LivingEntity target){
  if(travelCooldown>0||target==null)return false;
  Vec3 delta=target.position().subtract(position());double distance=delta.horizontalDistance();
  boolean blocked=horizontalCollision||!getSensing().hasLineOfSight(target);
  int action=chooseTravel(distance,delta.y,onGround(),blocked,speechFollowTicks==0&&hurtTime>0,speechFollowTicks==0&&completedAttacks>=3);
  if(action==IDLE)return false;
  Vec3 forward=new Vec3(delta.x,0,delta.z).normalize();if(forward.lengthSqr()<.01)return false;
  double side=random.nextBoolean()?1:-1;
  Vec3 direction=action==RETREAT?forward.scale(-1):action==SIDESTEP?new Vec3(-forward.z*side,0,forward.x*side).add(forward.scale(-.18)).normalize():forward;
  double length=action==RETREAT?3:action==SIDESTEP?3.2:Math.min(7.5,Math.max(0,distance-(bladeDrawn()?2.4:1.6)));
  Vec3 goal=safeLanding(YutaDomain.containMovement(this,position().add(direction.scale(length))));
  if(goal==null&&action==SIDESTEP){direction=direction.scale(-1);goal=safeLanding(YutaDomain.containMovement(this,position().add(direction.scale(length))));}
  if(goal==null){travelCooldown=10;return false;}
  Vec3 displacement=goal.subtract(position());
  if(action!=LEAP&&Math.abs(displacement.y)>.7){action=LEAP;}
  // Preflight and execution use the identical eased trajectory, including vertical movement.
  for(float frame=.25f;frame<=duration(action);frame+=.25f){
   Vec3 point=travelPoint(position(),displacement,action,frame);
   if(!level().noCollision(this,getBoundingBox().move(point.subtract(position())).deflate(.03))
     &&!CatTerrain.route(this,point,point)){travelCooldown=10;return false;}
  }
  start(action);travelOrigin=position();travelVector=displacement;
  if(action==LEAP){controlledLeap=true;travelHadNoGravity=isNoGravity();setNoGravity(true);setDeltaMovement(Vec3.ZERO);}
  travelCooldown=action==CHASE?24:36;completedAttacks=0;
  playSound(SoundEvents.PLAYER_ATTACK_SWEEP,.5f,1.65f);return true;
 }
 private static Vec3 travelPoint(Vec3 origin,Vec3 displacement,int action,float frame){
  return origin.add(displacement.scale(travelProgress(action,frame))).add(0,travelLift(action,frame),0);
 }
 private Vec3 safeLanding(Vec3 intended){
  BlockPos base=BlockPos.containing(intended);
  for(int offset=2;offset>=-2;offset--){
   BlockPos floor=base.offset(0,offset-1,0);
   if(!level().hasChunkAt(floor)||!level().getFluidState(floor).isEmpty()
      ||level().getBlockState(floor).getCollisionShape(level(),floor).isEmpty())continue;
   Vec3 candidate=new Vec3(intended.x,floor.getY()+1,intended.z);
   if(!level().getFluidState(BlockPos.containing(candidate)).isEmpty())continue;
   if(level().noCollision(this,getBoundingBox().move(candidate.subtract(position())).deflate(.03))
     ||CatTerrain.route(this,candidate,candidate))return candidate;
  }
  return null;
 }
 private void face(LivingEntity target){
  Vec3 d=target.position().subtract(position());float yaw=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90);
  setYRot(Mth.rotLerp(.45f,getYRot(),yaw));yBodyRot=getYRot();setYHeadRot(getYRot());
  // During a committed strike only this facing controller owns the yaw.
  if(bladeAction()==IDLE)getLookControl().setLookAt(target,35,25);
 }
 private void strikePath(Vec3 from,Vec3 to,float damage){
  if(!(level() instanceof ServerLevel level))return;
  Vec3 a=from.add(0,.65,0),b=to.add(0,.65,0);
  for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(a,b).inflate(1.1),this::canFight)){
   AABB box=target.getBoundingBox().inflate(.65,.35,.65);
   if(struck.contains(target.getUUID())||!box.contains(a)&&box.clip(a,b).isEmpty()||!getSensing().hasLineOfSight(target))continue;
   struck.add(target.getUUID());
   int action=bladeAction();
   String type=multiple(action)?"sorcerer_combo":"yuta_melee";
   DamageSource source=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
    .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("jujutsuvfx",type))),this);
   Vec3 velocityBeforeHit=target.getDeltaMovement();
   if(!dev.jujutsuvfx.server.TechniqueDamageRules.hurtYutaMelee(target,source,damage))continue;
   YutaDomain.melee(this,target);rika.melee(target,action);
   if(!multiple(action)&&action!=DRAW)entityData.set(HIT_STOP,1);
   Vec3 hit=target.getBoundingBox().getCenter();
   level.sendParticles(ParticleTypes.SWEEP_ATTACK,hit.x,hit.y,hit.z,1,0,0,0,0);
   level.sendParticles(ParticleTypes.CRIT,hit.x,hit.y,hit.z,7,.2,.25,.2,.16);
   playSound(SoundEvents.PLAYER_ATTACK_STRONG,.85f,bladeDrawn()?1.35f:1);
   Vec3 d=target.position().subtract(position());
   boolean rapid=brawlMove(action)==CatCombatMoves.RAPID;
   if(rapid&&entityData.get(AGE)<28){target.setDeltaMovement(velocityBeforeHit);target.hurtMarked=true;}
   if(!rapid||entityData.get(AGE)>=28)target.knockback(action==SPIN_CUT?.65:.35,-d.x,-d.z);
   if(action==RISING||brawlMove(action)==CatCombatMoves.UPPERCUT){target.setDeltaMovement(target.getDeltaMovement().add(0,.25,0));target.hurtMarked=true;}
  }
 }
 @Override public void die(DamageSource source){YutaGetoDuel.cancel(this);rika.cancel();super.die(source);}
 @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(danceStyle()!=0)tag.putBoolean("NoAI",super.isNoAi());tag.putInt("YutaBehavior",behavior());tag.putInt("YutaBladeMode",bladeMode());tag.putInt("YutaDomainMilestones",domainMilestones);tag.putBoolean("YutaBladeCommitted",drawCommitted);tag.putBoolean("YutaBladeDrawn",bladeDrawn());if(controlledLeap)tag.putBoolean("NoGravity",travelHadNoGravity);}
 @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(BEHAVIOR,tag.contains("YutaBehavior")?Mth.clamp(tag.getInt("YutaBehavior"),STILL,AGGRESSIVE):AGGRESSIVE);entityData.set(BLADE_MODE,Mth.clamp(tag.getInt("YutaBladeMode"),AUTO,OFF));domainMilestones=tag.getInt("YutaDomainMilestones")&3;drawCommitted=tag.getBoolean("YutaBladeDrawn");entityData.set(DRAWN,tag.getBoolean("YutaBladeDrawn"));entityData.set(FORCED_DANCE,false);entityData.set(COPIED_KIND,-1);entityData.set(ACTION,IDLE);}
 @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){fallDistance=0;return false;}
 @Override protected SoundEvent getAmbientSound(){return null;}
 @Override protected SoundEvent getHurtSound(DamageSource source){return null;}
 @Override protected SoundEvent getDeathSound(){return null;}
 @Override public boolean isFood(ItemStack stack){return false;}
 @Override public boolean canMate(Animal other){return false;}
 @Override public YutaCat getBreedOffspring(ServerLevel level,AgeableMob other){return null;}
 @Override public boolean removeWhenFarAway(double distance){return false;}
 @Override public InteractionResult mobInteract(Player player,InteractionHand hand){return InteractionResult.PASS;}
}
