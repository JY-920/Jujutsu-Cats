package dev.jujutsuvfx.entity;

import dev.jujutsuvfx.core.SpiritRules;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.joml.Vector3f;

/** Server-owned summon; vanilla mobs exist only as client-side rendering stand-ins. */
public final class CursedSpirit extends PathfinderMob {
 private static final EntityDataAccessor<Integer> SKIN=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.INT),ROLE=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.INT),PALETTE=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.INT),OWNER=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Float> SIZE=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Long> BORN=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.LONG),RETURN=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.LONG);
 private static final EntityDataAccessor<Boolean> ABSORB=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.BOOLEAN);
 /** Donor species per organ slot (body, head, legs, limbs, tail); empty for legacy single-skin spirits. */
 private static final EntityDataAccessor<CompoundTag> RECIPE=SynchedEntityData.defineId(CursedSpirit.class,EntityDataSerializers.COMPOUND_TAG);
 public static final float CHIMERA_WIDTH=.85f,CHIMERA_HEIGHT=1.25f;
 private UUID owner;private long expires;private int cooldown=20,missingOwner,pressure;
 /** Spawn-egg spirits: no summoner, a random stitched body and 15-50 health; they hunt players on their own. */
 private boolean wild;private float wildHealth;private int wander;
 public static final String[] WILD_DONORS={"cow","pig","sheep","chicken","wolf","spider","zombie","skeleton","creeper","bee","phantom","iron_golem",
   "squid","horse","fox","polar_bear","ravager","enderman","goat","parrot","llama","panda","rabbit","frog","hoglin","witch","villager","cat","turtle","axolotl"};
 public boolean wild(){return wild;}
 public CursedSpirit(EntityType<? extends PathfinderMob> type,Level level){super(type,level);xpReward=0;}
 public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,SpiritRules.MAX_HEALTH).add(Attributes.MOVEMENT_SPEED,.36).add(Attributes.FOLLOW_RANGE,128).add(Attributes.ATTACK_DAMAGE,8).add(Attributes.KNOCKBACK_RESISTANCE,.25);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(ABSORB,false);b.define(SKIN,0);b.define(ROLE,0);b.define(PALETTE,0);b.define(OWNER,-1);b.define(SIZE,1f);b.define(BORN,0L);b.define(RETURN,-1L);b.define(RECIPE,new CompoundTag());}
 public UUID ownerId(){return owner;} public int ownerEntity(){return entityData.get(OWNER);} public long expires(){return expires;}
 public int skin(){return entityData.get(SKIN);} public int role(){return entityData.get(ROLE);} public int palette(){return entityData.get(PALETTE);} public float modelScale(){return entityData.get(SIZE);}
 public EntityType<?> skinType(){return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("minecraft",SpiritRules.SKINS[SpiritRules.bounded(skin(),SpiritRules.SKINS.length-1)]));}
 public boolean chimera(){return entityData.get(RECIPE).contains("Body");}
 public String[] recipe(){
  CompoundTag t=entityData.get(RECIPE);if(!t.contains("Body"))return new String[0];
  String[] out=new String[dev.jujutsuvfx.core.ChimeraAnatomy.SLOTS];
  for(int i=0;i<out.length;i++)out[i]=t.getString(dev.jujutsuvfx.core.ChimeraAnatomy.SLOT_KEYS[i]);
  return out;
 }
 public int recipeSeed(){return entityData.get(RECIPE).getInt("Seed");}
 public void configure(GetoCat summoner,SpiritRules.Spec spec,String[] recipe,int seed){
  CompoundTag t=new CompoundTag();
  for(int i=0;i<recipe.length&&i<dev.jujutsuvfx.core.ChimeraAnatomy.SLOTS;i++)t.putString(dev.jujutsuvfx.core.ChimeraAnatomy.SLOT_KEYS[i],recipe[i]);
  t.putInt("Seed",seed);entityData.set(RECIPE,t);
  configure(summoner,spec);
 }
 public boolean recalling(){return entityData.get(RETURN)>=0;}
 public float recallAge(float partial){return recalling()?Math.max(0,level().getGameTime()-entityData.get(RETURN)+partial):0;}
 public float materialize(float partial){return Mth.clamp((level().getGameTime()-entityData.get(BORN)+partial)/12f,0,1)*(1-Mth.clamp(recallAge(partial)/(entityData.get(ABSORB)?28f:16f),0,1));}
 public void configure(GetoCat summoner,SpiritRules.Spec spec){
  owner=summoner.getUUID();entityData.set(OWNER,summoner.getId());entityData.set(SKIN,spec.skin());entityData.set(ROLE,spec.role());entityData.set(PALETTE,spec.palette());
  entityData.set(SIZE,SpiritRules.scale(spec.height(),chimera()?CHIMERA_HEIGHT:skinType().getDimensions().height()));entityData.set(BORN,level().getGameTime());
  expires=level().getGameTime()+(role()==SpiritRules.GUARD?240:1200);getAttribute(Attributes.MAX_HEALTH).setBaseValue(SpiritRules.MAX_HEALTH);setHealth(getMaxHealth());setNoGravity(role()==SpiritRules.AIR);refreshDimensions();
 }
 @Override protected EntityDimensions getDefaultDimensions(Pose pose){return (chimera()?EntityDimensions.scalable(CHIMERA_WIDTH,CHIMERA_HEIGHT):skinType().getDimensions()).scale(modelScale());}
 @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(SKIN)||key.equals(SIZE)||key.equals(RECIPE))refreshDimensions();}
 @Override public boolean removeWhenFarAway(double distance){return false;}
 @Override protected boolean shouldDespawnInPeaceful(){return true;}
 @Override protected boolean shouldDropLoot(){return false;}
 @Override public boolean causeFallDamage(float d,float m,DamageSource source){return false;}
 public void absorb(){entityData.set(ABSORB,true);recall();}
 public void recall(){if(!level().isClientSide&&!recalling()){entityData.set(RETURN,level().getGameTime());GetoCat cat=summoner();if(cat!=null)cat.forgetSpirit(getUUID());getNavigation().stop();setTarget(null);setNoGravity(true);}}
 public void pressure(LivingEntity target){setTarget(target);if(cooldown<=0)pressure=16;}
 private GetoCat summoner(){return level() instanceof ServerLevel l&&owner!=null&&l.getEntity(owner) instanceof GetoCat c?c:null;}
 @Override public boolean isAlliedTo(Entity e){return owner!=null&&(owner.equals(e.getUUID())||e instanceof CursedSpirit s&&owner.equals(s.owner))||super.isAlliedTo(e);}
 @Override public void tick(){
  if(!level().isClientSide&&level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){discard();return;}
  super.tick();if(level().isClientSide){
   if(tickCount%5==0){int tint=SpiritRules.tint(palette());level().addParticle(new DustParticleOptions(new Vector3f(((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f),1f),getX()+(random.nextDouble()-.5)*getBbWidth(),getY()+random.nextDouble()*getBbHeight(),getZ()+(random.nextDouble()-.5)*getBbWidth(),0,.025,0);}return;
  }
  if(owner==null&&!wild&&!recalling()&&!chimera())wildSpawn();
  if(wild){wildTick();return;}
  GetoCat cat=summoner();
  if(cat!=null){missingOwner=0;entityData.set(OWNER,cat.getId());if(tickCount%20==0)cat.rememberSpirit(this);}else missingOwner++;
  if(owner==null||missingOwner>80||expires<=level().getGameTime()||cat!=null&&(!cat.isAlive()||cat.danceStyle()!=0))recall();
  if(recalling()){
   noPhysics=true;
   if(cat!=null){
    Vec3 center=entityData.get(ABSORB)?cat.spellOrigin():cat.position().add(0,.8,0);
    float progress=Mth.clamp(recallAge(0)/(entityData.get(ABSORB)?28f:16f),0,1);
    Vec3 d=center.subtract(position());Vec3 tangent=new Vec3(-d.z,0,d.x).normalize().scale((1-progress)*.24);
    Vec3 velocity=d.scale(.20+progress*.55).add(tangent);double speed=velocity.length();
    setDeltaMovement(speed>2?velocity.scale(2/speed):velocity);
   }
   if(recallAge(0)>=(entityData.get(ABSORB)?28:16))discard();return;
  }
  if(cat==null)return;if(distanceToSqr(cat)>80*80){recall();return;}
  if(materialize(0)<1){getNavigation().stop();return;}
  LivingEntity target=cat.getTarget();if(!cat.valid(target)){if(tickCount%10==0)getNavigation().moveTo(cat,1);return;}setTarget(target);
  fight(target,cat.position());
 }
 private void fight(LivingEntity target,Vec3 home){
  if(cooldown>0)cooldown--;if(pressure>0)pressure--;
  boolean engage=role()!=SpiritRules.GUARD;
  Vec3 goal=target.position();Vec3 axis=target.position().subtract(home).normalize();
  if(role()==SpiritRules.GUARD)goal=home.add(axis.scale(2.1));
  else if(role()==SpiritRules.AIR){double angle=tickCount*.065+(getId()%7);goal=target.position().add(Math.cos(angle)*3,2.3+Math.sin(angle*.5)*.6,Math.sin(angle)*3);if(cooldown<12||pressure>0)goal=target.getBoundingBox().getCenter();}
  if(role()==SpiritRules.AIR){getNavigation().stop();setNoGravity(true);Vec3 d=goal.subtract(position());setDeltaMovement(getDeltaMovement().scale(.65).add(d.normalize().scale(Math.min(.17,d.length()*.04))));fallDistance=0;}
  else {if(tickCount%5==0)getNavigation().moveTo(goal.x,goal.y,goal.z,engage?1.55:1.2);if(horizontalCollision&&onGround())getJumpControl().jump();}
  getLookControl().setLookAt(target,30,30);setYRot((float)(Mth.atan2(target.getZ()-getZ(),target.getX()-getX())*180/Math.PI)-90);yBodyRot=getYRot();
  double reach=Math.max(1.6,Math.min(3.6,(getBbWidth()+target.getBbWidth())*.5+1));
  if(cooldown==0&&(engage||role()==SpiritRules.GUARD)&&distanceToSqr(target)<reach*reach&&getSensing().hasLineOfSight(target)){
   if(target.hurt(damageSources().mobAttack(this),target instanceof Player?(wild?4:2):role()==SpiritRules.GUARD?6:5)){
    target.knockback(role()==SpiritRules.GUARD?.65:.20,getX()-target.getX(),getZ()-target.getZ());playSound(SoundEvents.PLAYER_ATTACK_STRONG,.65f,.65f);
    ((ServerLevel)level()).sendParticles(ParticleTypes.DRAGON_BREATH,target.getX(),target.getY()+.7,target.getZ(),9,.18,.25,.18,.025);
   }swing(net.minecraft.world.InteractionHand.MAIN_HAND);cooldown=48+random.nextInt(25);pressure=0;
  }
 }
 private void wildSpawn(){
  List<String> pool=new ArrayList<>(Arrays.asList(WILD_DONORS));Collections.shuffle(pool,new Random(random.nextLong()));
  List<String> donors=new ArrayList<>();for(String id:pool.subList(0,3+random.nextInt(3)))donors.add("minecraft:"+id);
  String[] recipe=dev.jujutsuvfx.core.ChimeraAnatomy.recipes(donors,1,random.nextLong())[0];
  int role=random.nextFloat()<.25f?SpiritRules.AIR:SpiritRules.ASSAULT;
  var spec=SpiritRules.choose(role,random.nextInt(3),random.nextInt(3),random.nextFloat());
  CompoundTag t=new CompoundTag();
  for(int i=0;i<recipe.length;i++)t.putString(dev.jujutsuvfx.core.ChimeraAnatomy.SLOT_KEYS[i],recipe[i]);
  t.putInt("Seed",random.nextInt());entityData.set(RECIPE,t);
  entityData.set(ROLE,spec.role());entityData.set(PALETTE,spec.palette());entityData.set(SKIN,spec.skin());
  entityData.set(SIZE,SpiritRules.scale(spec.height(),CHIMERA_HEIGHT));entityData.set(BORN,level().getGameTime());
  wild=true;wildHealth=15+random.nextInt(36);
  getAttribute(Attributes.MAX_HEALTH).setBaseValue(wildHealth);setHealth(wildHealth);
  setNoGravity(role==SpiritRules.AIR);refreshDimensions();
 }
 private boolean prey(LivingEntity e){
  return e!=null&&e!=this&&e.isAlive()&&e.level()==level()&&!(e instanceof CursedSpirit s&&s.wild())
    &&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()))&&distanceToSqr(e)<=48*48;
 }
 private void wildTick(){
  if(materialize(0)<1){getNavigation().stop();return;}
  LivingEntity target=getTarget();
  if(!prey(target)){
   target=prey(getLastHurtByMob())?getLastHurtByMob():level().getNearestPlayer(getX(),getY(),getZ(),24,e->e instanceof Player p&&prey(p)&&getSensing().hasLineOfSight(p));
   setTarget(target);
  }
  if(target!=null){fight(target,position());return;}
  if(role()==SpiritRules.AIR){setDeltaMovement(getDeltaMovement().scale(.8).add(0,Math.sin(tickCount*.08)*.01,0));fallDistance=0;}
  else if(--wander<=0){wander=80+random.nextInt(80);Vec3 to=position().add(random.nextGaussian()*6,0,random.nextGaussian()*6);getNavigation().moveTo(to.x,to.y,to.z,.9);}
 }
 @Override public void remove(RemovalReason reason){if(reason.shouldDestroy()&&!level().isClientSide){GetoCat cat=summoner();if(cat!=null)cat.forgetSpirit(getUUID());}super.remove(reason);}
 @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);if(owner!=null)t.putUUID("SpiritOwner",owner);t.putInt("SpiritSkin",skin());t.putInt("SpiritRole",role());t.putInt("SpiritPalette",palette());t.putFloat("SpiritSize",modelScale());t.putLong("SpiritExpires",expires);t.putLong("SpiritBorn",entityData.get(BORN));t.putLong("SpiritReturn",entityData.get(RETURN));t.putBoolean("SpiritAbsorb",entityData.get(ABSORB));if(chimera())t.put("SpiritRecipe",entityData.get(RECIPE).copy());if(wild){t.putBoolean("SpiritWild",true);t.putFloat("SpiritWildHealth",wildHealth);}}
 @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);getAttribute(Attributes.MAX_HEALTH).setBaseValue(SpiritRules.MAX_HEALTH);setHealth(SpiritRules.loadedHealth(getHealth()));owner=t.hasUUID("SpiritOwner")?t.getUUID("SpiritOwner"):null;entityData.set(SKIN,SpiritRules.bounded(t.getInt("SpiritSkin"),7));entityData.set(ROLE,SpiritRules.bounded(t.getInt("SpiritRole"),2));entityData.set(PALETTE,SpiritRules.bounded(t.getInt("SpiritPalette"),2));float size=t.getFloat("SpiritSize");entityData.set(SIZE,Float.isFinite(size)?Mth.clamp(size,.35f,3.5f):1f);expires=Math.min(t.getLong("SpiritExpires"),level().getGameTime()+1200);entityData.set(BORN,t.getLong("SpiritBorn"));entityData.set(RETURN,t.contains("SpiritReturn")?t.getLong("SpiritReturn"):-1L);entityData.set(ABSORB,t.getBoolean("SpiritAbsorb"));entityData.set(RECIPE,t.contains("SpiritRecipe")?t.getCompound("SpiritRecipe").copy():new CompoundTag());wild=t.getBoolean("SpiritWild");if(wild){wildHealth=net.minecraft.util.Mth.clamp(t.getFloat("SpiritWildHealth"),15,50);getAttribute(Attributes.MAX_HEALTH).setBaseValue(wildHealth);setHealth(Math.min(Math.max(t.getFloat("Health"),0),wildHealth));}setNoGravity(role()==SpiritRules.AIR||recalling());refreshDimensions();}
}
