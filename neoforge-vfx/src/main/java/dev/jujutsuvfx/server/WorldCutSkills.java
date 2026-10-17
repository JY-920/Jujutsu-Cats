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
 private WorldCutSkills(){}
 public static boolean cast(ServerPlayer owner,InteractionHand hand){
  if(!owner.isAlive()||owner.isSpectator()||!owner.getItemInHand(hand).is(VfxItems.WORLD_CUT.get())||owner.getCooldowns().isOnCooldown(VfxItems.WORLD_CUT.get()))return false;
  owner.getCooldowns().addCooldown(VfxItems.WORLD_CUT.get(),40);
  QUEUES.computeIfAbsent(owner.server,k->new HashMap<>()).put(owner.getUUID(),new Pending(owner,hand,owner.server.getTickCount()+6));return true;
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){
  var queue=QUEUES.get(event.getServer());if(queue==null)return;
  for(var p:List.copyOf(queue.values())){
   if(!p.owner.isAlive()||p.owner.isRemoved()||!p.owner.getItemInHand(p.hand).is(VfxItems.WORLD_CUT.get())){queue.remove(p.owner.getUUID());continue;}
   if(event.getServer().getTickCount()<p.at)continue;queue.remove(p.owner.getUUID());strike(p.owner);p.owner.stopUsingItem();
  }
 }
 @SubscribeEvent public static void stopped(ServerStoppedEvent event){QUEUES.remove(event.getServer());}
 private static void strike(ServerPlayer owner){
  Vec3 from=owner.getEyePosition(),forward=owner.getLookAngle().normalize(),to=from.add(forward.scale(48));
  Vec3 right=forward.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.5)right=new Vec3(1,0,0);
  Vec3 up=right.cross(forward).normalize();int seed=owner.getRandom().nextInt();double angle=(seed&1)==0?.68:-.68;
  Vec3 stroke=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
  var targets=new ArrayList<>(owner.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(2),
   e->TechniquePlayerRules.canAffect(owner,e)&&WorldCutPath.intersects(e.getBoundingBox(),from,to)));
  targets.sort(Comparator.comparingDouble(e->e.position().distanceToSqr(from)));
  if(targets.isEmpty())broadcast(owner,new WorldCutEffect(-1,new UUID(0,0),from.add(forward.scale(12)),forward,stroke,seed,24));
  DamageSource source=new DamageSource(owner.damageSources().genericKill().typeHolder(),owner);
  for(LivingEntity target:targets){
   Vec3 center=target.getBoundingBox().getCenter();
   broadcast(owner,new WorldCutEffect(target.getId(),target.getUUID(),center,forward,stroke,seed,(float)Math.min(96,Math.max(16,target.getBbHeight()*5))));
   target.setLastHurtByPlayer(owner);target.setLastHurtByMob(owner);owner.setLastHurtMob(target);
   target.getCombatTracker().recordDamage(source,Math.max(1,target.getHealth()));
   // No hurt(amount), armor, totem check, or cinematic last-stand trigger.
   if(target instanceof SorcererCat cat)cat.executeWorldCut(source);
   else {target.setHealth(0);target.die(source);target.setHealth(0);if(target instanceof EnderDragon dragon)dragon.kill();else if(!(target instanceof ServerPlayer))target.remove(Entity.RemovalReason.KILLED);}
  }
 }
 private static void broadcast(ServerPlayer owner,WorldCutEffect packet){
  for(ServerPlayer viewer:owner.serverLevel().players())if((viewer==owner||viewer.position().distanceToSqr(packet.center())<128*128)&&viewer.connection.hasChannel(WorldCutEffect.TYPE))PacketDistributor.sendToPlayer(viewer,packet);
 }
}
