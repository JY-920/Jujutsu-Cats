package dev.jujutsuvfx.server;
import dev.jujutsuvfx.entity.YutaCat;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.TechniqueDamage;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import dev.jujutsuvfx.core.CursedSpeechShape;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Hostile-only cone; uses the existing multi-owner lock and its multiplayer release protocol. */
public final class CursedSpeech {
 private CursedSpeech(){}
 public static Vec3 mouth(LivingEntity cat){return cat.getEyePosition().add(cat.getLookAngle().scale(cat instanceof YutaCat?.65:.25)).add(0,-.15,0);}
 public static void charge(YutaCat cat,LivingEntity target){
  if(cat.level() instanceof ServerLevel level)new CatCombatCue(cat.getId(),target.getId(),CatCombatCue.SPEECH,mouth(cat),target.getBoundingBox().getCenter(),0,0,cat.tickCount).broadcast(level);
 }
 public static void charge(ServerPlayer player){
  Vec3 origin=mouth(player);
  new CatCombatCue(player.getId(),-1,CatCombatCue.SPEECH,origin,origin.add(player.getLookAngle().scale(CursedSpeechShape.RANGE)),0,0,player.tickCount).broadcast(player.serverLevel());
 }
 public static boolean release(YutaCat cat,LivingEntity primary){return !YutaDomain.opened(cat)&&release(cat,primary,false);}
 public static boolean release(ServerPlayer player){return release(player,null,false);}
 /** Domain copies damage targets and never freeze them or show the restraint cue. */
 public static boolean releaseCopied(YutaCat cat,LivingEntity primary){return release(cat,primary,true);}
 private static boolean release(LivingEntity cat,LivingEntity primary,boolean copied){
  if(!(cat.level() instanceof ServerLevel level)||!cat.isAlive()||cat.isSpectator())return false;
  Vec3 origin=mouth(cat),forward=cat.getLookAngle().normalize();
  new CatCombatCue(cat.getId(),-1,CatCombatCue.SPEECH,origin,origin.add(forward.scale(CursedSpeechShape.RANGE)),1,0,cat.tickCount).broadcast(level);
  boolean caught=false;long now=level.getGameTime();
  for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,cat.getBoundingBox().inflate(CursedSpeechShape.RANGE),target->TechniquePlayerRules.canAffect(cat,target)&&(!(cat instanceof YutaCat yuta)||yuta.canFight(target)))){
   Vec3 d=target.getBoundingBox().getCenter().subtract(origin);
   if(!CursedSpeechShape.inCone(d.x,d.y,d.z,forward.x,forward.y,forward.z)||!cat.hasLineOfSight(target))continue;
   if(copied){
    DamageSource source=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
      .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(JujutsuVfx.ID,"yuta_copy"))),cat);
    boolean hit=TechniqueDamageRules.hurt(target,source,18,TechniqueDamage.YUTA_DOMAIN);
    if(hit&&(primary==null||target==primary))caught=true;
    continue;
   }
   if(target.getPersistentData().getLong("JujutsuSpeechResistUntil")>now)continue;
   int ticks=CursedSpeechShape.stunTicks(target instanceof Player);
   target.getPersistentData().putLong("JujutsuSpeechResistUntil",now+CursedSpeechShape.IMMUNITY);
   DomainFreeze.brieflyFreeze(target,ticks,CatAdaptation.Phenomenon.SPEECH);
   int actual=CatAdaptation.controlDuration(target,CatAdaptation.Phenomenon.SPEECH,ticks);
   if(actual<=0)continue;
   if(cat instanceof ServerPlayer)YutaGetoDuel.cancel(target);
   new CatCombatCue(cat.getId(),target.getId(),CatCombatCue.SPEECH,origin,target.getBoundingBox().getCenter(),2,actual,cat.tickCount+target.getId()).broadcast(level);
   if(primary==null||target==primary)caught=true;
  }
  return caught;
 }
}
