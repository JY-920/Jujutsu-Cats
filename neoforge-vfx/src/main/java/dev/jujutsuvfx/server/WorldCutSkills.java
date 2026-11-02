package dev.jujutsuvfx.server;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.WorldCutPath;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.WorldCutEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class WorldCutSkills {
 private record Pending(ServerPlayer owner,InteractionHand hand,long at){}
 private static final Map<MinecraftServer,Map<UUID,Pending>> QUEUES=new WeakHashMap<>();
 private static final Map<MinecraftServer,List<Flight>> FLIGHTS=new WeakHashMap<>();
 private static final Map<MinecraftServer,List<Execution>> EXECUTIONS=new WeakHashMap<>();
 private record Execution(ServerPlayer owner,LivingEntity target,long at){}
 private static final class Flight {
  final ServerPlayer owner;final net.minecraft.server.level.ServerLevel level;final Vec3 origin,forward,stroke;final int seed;
  final Set<UUID> hit=new HashSet<>();int tick;
  Flight(ServerPlayer owner,Vec3 origin,Vec3 forward,Vec3 stroke,int seed){this.owner=owner;level=owner.serverLevel();this.origin=origin;this.forward=forward;this.stroke=stroke;this.seed=seed;}
 }
 private static long terrainFailureAt;
 private WorldCutSkills(){}
 public static boolean cast(ServerPlayer owner,InteractionHand hand){
  if(!owner.isAlive()||owner.isSpectator()||!owner.getItemInHand(hand).is(VfxItems.WORLD_CUT.get())||owner.getCooldowns().isOnCooldown(VfxItems.WORLD_CUT.get()))return false;
  owner.getCooldowns().addCooldown(VfxItems.WORLD_CUT.get(),40);
  QUEUES.computeIfAbsent(owner.server,k->new HashMap<>()).put(owner.getUUID(),new Pending(owner,hand,owner.server.getTickCount()+6));return true;
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){
  MinecraftServer server=event.getServer();
  var executions=EXECUTIONS.get(server);
  if(executions!=null)for(var execution:List.copyOf(executions))if(server.getTickCount()>=execution.at){
   executions.remove(execution);execute(execution.owner,execution.target);
  }
  var flights=FLIGHTS.get(server);
  if(flights!=null)for(var flight:List.copyOf(flights)){
   if(flight.owner.isRemoved()||flight.owner.level()!=flight.level){flights.remove(flight);continue;}
   advance(flight);if(flight.tick*WorldCutPath.SPEED>=WorldCutPath.RANGE)flights.remove(flight);
  }
  var queue=QUEUES.get(server);if(queue==null)return;
  for(var p:List.copyOf(queue.values())){
   if(!p.owner.isAlive()||p.owner.isRemoved()||!p.owner.getItemInHand(p.hand).is(VfxItems.WORLD_CUT.get())){queue.remove(p.owner.getUUID());continue;}
   if(event.getServer().getTickCount()<p.at)continue;queue.remove(p.owner.getUUID());strike(p.owner);p.owner.stopUsingItem();
  }
 }
 @SubscribeEvent public static void stopped(ServerStoppedEvent event){QUEUES.remove(event.getServer());FLIGHTS.remove(event.getServer());EXECUTIONS.remove(event.getServer());}
 private static void strike(ServerPlayer owner){
  Vec3 forward=owner.getLookAngle().normalize();
  Vec3 from=owner.getEyePosition().add(0,-.22,0).add(forward.scale(.8));
  int seed=owner.getRandom().nextInt();Vec3 stroke=WorldCutPath.stroke(forward,seed);
  // Every cast launches one visible blade, even when no entity is hit.
  broadcast(owner,new WorldCutEffect(-1,new UUID(0,0),from,forward,stroke,seed,(float)WorldCutPath.WIDTH));
  FLIGHTS.computeIfAbsent(owner.server,k->new ArrayList<>()).add(new Flight(owner,from,forward,stroke,seed));
 }
 private static void advance(Flight f){
  Vec3 from=f.origin.add(f.forward.scale(f.tick*WorldCutPath.SPEED));
  Vec3 to=f.origin.add(f.forward.scale(Math.min(WorldCutPath.RANGE,++f.tick*WorldCutPath.SPEED)));
  var targets=f.level.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(WorldCutPath.WIDTH*.5+1),
   e->!f.hit.contains(e.getUUID())&&TechniquePlayerRules.canAffect(f.owner,e)&&WorldCutPath.intersectsBlade(e.getBoundingBox(),from,to,f.stroke,WorldCutPath.WIDTH));
  for(LivingEntity target:targets){
   f.hit.add(target.getUUID());
   // Keep the live posed model available until the following server tick, instead of
   // removing it in the same batch as the visual packet. No ordinary hurt/armor path.
   broadcast(f.owner,new WorldCutEffect(target.getId(),target.getUUID(),target.getBoundingBox().getCenter(),f.forward,f.stroke,f.seed,(float)Math.min(96,Math.max(3,target.getBbWidth()*2.5))));
   EXECUTIONS.computeIfAbsent(f.owner.server,k->new ArrayList<>()).add(new Execution(f.owner,target,f.owner.server.getTickCount()+1));
  }
  if(TechniqueTerrainRules.enabled())try{CleaveTerrain.sweep(f.owner,from,to,f.stroke);}
  catch(RuntimeException|LinkageError failure){
   long time=System.nanoTime();if(terrainFailureAt==0||time-terrainFailureAt>30_000_000_000L){terrainFailureAt=time;
    com.mojang.logging.LogUtils.getLogger().warn("Space-cut terrain failed; blade flight and creature hits continue",failure);}
  }
 }
 private static void execute(ServerPlayer owner,LivingEntity target){
  if(!owner.isAlive()||owner.isRemoved()||target.level()!=owner.level()||!TechniquePlayerRules.canAffect(owner,target))return;
  DamageSource source=new DamageSource(owner.damageSources().genericKill().typeHolder(),owner);
  target.setLastHurtByPlayer(owner);target.setLastHurtByMob(owner);owner.setLastHurtMob(target);
  target.getCombatTracker().recordDamage(source,Math.max(1,target.getHealth()));
  if(target instanceof SorcererCat cat)cat.executeWorldCut(source);
  else {target.setHealth(0);target.die(source);target.setHealth(0);if(target instanceof EnderDragon dragon)dragon.kill();else if(!(target instanceof ServerPlayer))target.remove(Entity.RemovalReason.KILLED);}
 }
 private static void broadcast(ServerPlayer owner,WorldCutEffect packet){
  for(ServerPlayer viewer:owner.serverLevel().players())if((viewer==owner||viewer.position().distanceToSqr(packet.center())<128*128)&&viewer.connection.hasChannel(WorldCutEffect.TYPE))PacketDistributor.sendToPlayer(viewer,packet);
 }
}
