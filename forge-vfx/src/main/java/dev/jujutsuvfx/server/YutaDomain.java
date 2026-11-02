package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.core.YutaCopiedDeck;
import dev.jujutsuvfx.core.YutaDomainRules;
import dev.jujutsuvfx.entity.YutaCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Server-owned sword field: no terrain entities, no blanket entity freeze. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class YutaDomain {
 private static final AtomicInteger SERIAL=new AtomicInteger(6_000_000);
 private static final Map<MinecraftServer,Map<UUID,Cast>> STATES=new WeakHashMap<>();
 private static final class Cast {
  final LivingEntity cat;final ServerLevel level;final int id,seed;final Vec3 center,axis;final long opens;
  long ends,clashAt=-1;boolean opened;int sword=-1;long nextSword,nextCopy,copyDeadline;
  YutaCopiedDeck copies;
  DomainFreeze.YutaRival rival;final Set<DomainFreeze.YutaRival> resolved=new HashSet<>();
  Cast(LivingEntity cat,ServerLevel level){
   this.cat=cat;this.level=level;id=SERIAL.incrementAndGet();seed=cat.getRandom().nextInt();
   // The AI approaches to within 36 blocks first; center the field on its enemy, including players.
   Vec3 focus=cat instanceof YutaCat y&&y.getTarget()!=null?y.getTarget().position():cat.position();
   center=focus.add(0,.03,0);
   Vec3 look=cat.getLookAngle();axis=new Vec3(look.x,0,look.z).normalize();
   // Players have already held the seal; mobs keep their existing AI windup.
   opens=cat.getServer().getTickCount()+(cat instanceof net.minecraft.server.level.ServerPlayer?0:YutaDomainRules.WINDUP);ends=opens+VfxConfig.yutaSeconds()*20L;
  }
  void send(long now,boolean end){
   new CatCombatCue(cat.getId(),id,CatCombatCue.YUTA_DOMAIN,center,center.add(axis.scale(rival==null?1:2)),
    (int)Math.max(0,now-opens),end?0:(int)Math.max(1,ends-now),seed).broadcast(level);
  }
 }
 private YutaDomain(){}
 private static boolean enabled(LivingEntity owner){
  return !(owner instanceof net.minecraft.world.entity.Mob mob)||!mob.isNoAi();
 }
 public static boolean canAffect(LivingEntity owner,LivingEntity target){
  return target!=null&&!(target instanceof net.minecraft.world.entity.decoration.ArmorStand)
   &&(owner instanceof YutaCat y?y.canFight(target):TechniquePlayerRules.canAffect(owner,target));
 }
 public static boolean playerCast(net.minecraft.server.level.ServerPlayer player){
  if(!player.isAlive()||player.isSpectator()||active(player))return false;
  prepare(player);return active(player);
 }
 private static Cast cast(LivingEntity cat){var state=STATES.get(cat.getServer());return state==null?null:state.get(cat.getUUID());}
 public static boolean active(LivingEntity cat){return cast(cat)!=null;}
 public static boolean opened(LivingEntity cat){Cast c=cast(cat);return c!=null&&c.opened&&cat.isAlive()&&enabled(cat)&&cat.level()==c.level&&cat.getServer().getTickCount()<c.ends;}
 public static float damageMultiplier(YutaCat cat){Cast c=cast(cat);return YutaDomainRules.damageMultiplier(opened(cat),c!=null&&inside(c,cat));}
 public static void prepare(LivingEntity cat){
  if(!(cat.level() instanceof ServerLevel level)||!cat.isAlive()||active(cat))return;
  STATES.computeIfAbsent(cat.getServer(),s->new HashMap<>()).put(cat.getUUID(),new Cast(cat,level));
  DomainFreeze.releaseBlockedYuta(cat);
 }
 /** Called by the freeze hook before it suspends the entity's own AI tick. */
 public static void beforeFreeze(YutaCat cat){
  if(!active(cat)&&cat.wantsDomain()&&DomainFreeze.findYutaRival(cat,cat.position(),YutaDomainRules.RADIUS)!=null)
   cat.beginDomain();
 }
 public static boolean blocksDomain(UUID owner,Entity victim){
  if(!(victim instanceof LivingEntity cat))return false;Cast c=cast(cat);if(c==null)return false;
  if(!c.opened)return cat.level()==c.level&&canAffect(cat,c.level.getEntity(owner) instanceof LivingEntity e?e:null);
  return c.rival!=null&&c.rival.owner().equals(owner);
 }
 public static Vec3 containMovement(YutaCat cat,Vec3 desired){
  Cast c=cast(cat);if(c==null||!c.opened)return desired;
  Vec3 delta=desired.subtract(c.center);double limit=YutaDomainRules.RADIUS-3;
  return delta.lengthSqr()<=limit*limit?desired:c.center.add(delta.normalize().scale(limit));
 }
 public static boolean containChase(YutaCat cat,LivingEntity target){
  Cast c=cast(cat);if(c==null||!c.opened||inside(c,target)&&inside(c,cat))return false;
  Vec3 goal=containMovement(cat,target.position());
  if(cat.position().distanceToSqr(goal)>4){if(cat.tickCount%5==0)cat.getNavigation().moveTo(goal.x,goal.y,goal.z,1.1);}
  else cat.getNavigation().stop();
  return true;
 }
 public static boolean needsSword(YutaCat cat){Cast c=cast(cat);return c!=null&&c.opened&&c.sword<0&&cat.getServer().getTickCount()>=c.nextSword&&inside(c,cat);}
 public static void pickSword(YutaCat cat){
  Cast c=cast(cat);if(c==null||!c.opened||!inside(c,cat))return;
  c.sword=c.copies==null?0:c.copies.current();
  new CatCombatCue(cat.getId(),c.id,CatCombatCue.YUTA_SWORD,cat.position(),cat.getEyePosition(),3,c.sword,c.seed+cat.tickCount).broadcast(c.level);
  cat.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE,.65f,1.25f);
 }
 /** Swords mark the selected technique; actual copied damage is owned by its released spell. */
 public static void melee(YutaCat cat,LivingEntity target){
  Cast c=cast(cat);if(c==null||!c.opened||c.sword<0||!inside(c,cat)||!inside(c,target))return;
  c.sword=-1;c.nextSword=cat.getServer().getTickCount()+60;
 }
 private static boolean inside(Cast c,Entity target){return target.level()==c.level&&DomainVolume.intersects(c.center,YutaDomainRules.RADIUS,target.getBoundingBox());}
 private static void stop(Cast c,long now){if(c.opened)c.send(now,true);if(c.cat instanceof YutaCat y)YutaCopiedTechniques.cancel(y);c.rival=null;}
 @SubscribeEvent public static void tick(ServerTickEvent event){
  if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
  MinecraftServer server=event.getServer();var state=STATES.get(server);if(state==null)return;long now=server.getTickCount();
  for(Cast c:List.copyOf(state.values())){
   LivingEntity cat=c.cat;
   if(cat instanceof YutaCat y&&c.opened&&c.copies!=null&&!c.copies.complete()&&enabled(cat)&&cat.isAlive()&&cat.level()==c.level
     &&inside(c,cat)&&now>=c.ends-2&&now<c.copyDeadline
     &&(YutaCopiedTechniques.busy(y)||YutaCopiedTechniques.viable(y,copyTarget(c,y),c.copies.current())))c.ends=Math.max(c.ends,now+2);
   if(cat instanceof net.minecraft.server.level.ServerPlayer p&&server.getPlayerList().getPlayer(p.getUUID())!=p||!cat.isAlive()||cat.isRemoved()||cat.isSpectator()||!enabled(cat)||cat.level()!=c.level||now>=c.ends){stop(c,now);state.remove(cat.getUUID());continue;}
   if(now<c.opens)continue;
   if(!c.opened){
    c.opened=true;if(cat instanceof YutaCat y)y.markDomainOpened();
    if(cat instanceof YutaCat y){c.copies=new YutaCopiedDeck(y.getRandom().nextInt(YutaCopiedDeck.COUNT),y.getRandom().nextInt(YutaCopiedDeck.COUNT-1));c.nextCopy=now+20;c.copyDeadline=c.ends+300;}
    c.send(now,false);
   }
   if(cat instanceof net.minecraft.server.level.ServerPlayer player){
    // A two-tick lease never removes/replaces a longer potion, including its hidden stronger effect.
    var effect=dev.jujutsuvfx.effect.VfxEffects.TECHNIQUE_AMPLIFICATION.get();
    var old=player.getEffect(effect);
    if(old==null||old.getDuration()>=0&&old.getDuration()<2)player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,2,0,false,false,true));
   }
   if(c.rival!=null&&!DomainFreeze.yutaRivalActive(cat,c.rival)){c.resolved.add(c.rival);c.rival=null;}
   if(c.rival==null){
    var rival=DomainFreeze.findYutaRival(cat,c.center,YutaDomainRules.RADIUS);
    if(rival!=null&&!c.resolved.contains(rival)){
     c.rival=rival;c.clashAt=now;c.ends=Math.max(c.ends,now+YutaDomainRules.CLASH_TICKS+80);
     DomainFreeze.extendYutaRival(cat,rival,now+YutaDomainRules.CLASH_TICKS+80);DomainFreeze.releaseBlockedYuta(cat);
    }
   }
   if(c.rival!=null&&now-c.clashAt>=YutaDomainRules.CLASH_TICKS){
    boolean wins=cat.getRandom().nextBoolean();c.resolved.add(c.rival);
    if(wins){DomainFreeze.shatterYutaRival(cat,c.rival);c.rival=null;c.send(now,false);}
    else{stop(c,now);state.remove(cat.getUUID());continue;}
   }
   if(now%20==0)c.send(now,false);
   if(cat instanceof YutaCat y){
    YutaCopiedTechniques.tick(y);
    if(c.rival==null&&now>=c.nextCopy&&inside(c,y)&&!YutaCopiedTechniques.busy(y)){
     LivingEntity target=copyTarget(c,y);
     if(target!=null){
      if(YutaCopiedTechniques.viable(y,target,c.copies.current()))
       YutaCopiedTechniques.start(y,target,c.copies.current(),()->{boolean required=!c.copies.complete();c.copies.launched();c.nextCopy=server.getTickCount()+50;
        if(required&&c.copies.complete())c.ends=Math.max(c.ends,server.getTickCount()+80);});
      else if(now%5==0){Vec3 goal=containMovement(y,target.position());y.getNavigation().moveTo(goal.x,goal.y,goal.z,1.2);}
     }
    }
   }
  }
  if(state.isEmpty())STATES.remove(server);
 }
 private static LivingEntity copyTarget(Cast c,YutaCat cat){
  LivingEntity current=cat.getTarget();
  if(current!=null&&canAffect(cat,current)&&inside(c,current)&&YutaCopiedTechniques.viable(cat,current,c.copies.current()))return current;
  return c.level.getEntitiesOfClass(LivingEntity.class,new AABB(c.center,c.center).inflate(YutaDomainRules.RADIUS),
    target->canAffect(cat,target)&&inside(c,target)&&cat.getSensing().hasLineOfSight(target)).stream()
    .min(Comparator.<LivingEntity>comparingInt(target->YutaCopiedTechniques.viable(cat,target,c.copies.current())?0:1)
     .thenComparingDouble(cat::distanceToSqr)).orElse(null);
 }
 @SubscribeEvent public static void stopped(ServerStoppedEvent event){
  var state=STATES.remove(event.getServer());if(state!=null)for(Cast c:state.values())if(c.cat instanceof YutaCat y)YutaCopiedTechniques.cancel(y);
 }
}
