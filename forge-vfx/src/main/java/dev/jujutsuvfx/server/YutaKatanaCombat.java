package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.YutaKatanaMotion;
import dev.jujutsuvfx.item.YutaKatanaItem;
import dev.jujutsuvfx.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.platform.PacketDistributor;
import java.util.WeakHashMap;

/** Bonus belongs to the same accepted vanilla contact, so shields and invulnerability cannot be bypassed. */
public final class YutaKatanaCombat {
 private static final WeakHashMap<Player,State> STATES=new WeakHashMap<>();
 private static final class State {int tick=-100,step=-1,prepared=-100;float strength=1;}
 private static State state(Player p){return STATES.computeIfAbsent(p,k->new State());}
 public static void prepare(Player p){State s=state(p);s.prepared=p.tickCount;s.strength=p.getAttackStrengthScale(.5f);}
 public static float bonus(Player p){
  if(p.level().isClientSide||!YutaKatanaItem.imbued(p.getMainHandItem()))return 0;
  State s=state(p);return YutaKatanaMotion.bonus(s.prepared==p.tickCount?s.strength:1);
 }
 public static void swing(LivingEntity actor){
  if(!(actor instanceof ServerPlayer p)||!(p.getMainHandItem().getItem() instanceof YutaKatanaItem))return;
  State s=state(p);if(p.tickCount-s.tick<3)return;
  s.step=p.tickCount-s.tick>24?0:(s.step+1)%3;s.tick=p.tickCount;
  var packet=new YutaKatanaState(p.getId(),s.step);
  for(var observer:p.serverLevel().players())if(observer.distanceToSqr(p)<128*128&&PacketDistributor.hasChannel(observer.connection,YutaKatanaState.TYPE))PacketDistributor.sendToPlayer(observer,packet);
 }
 public static void confirmed(ItemStack stack,LivingEntity actor,LivingEntity target){
  if(!(actor instanceof ServerPlayer p)||p.getMainHandItem()!=stack||!YutaKatanaItem.imbued(stack))return;
  State s=state(p);Vec3 direction=target.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize();
  Vec3 point=target.getBoundingBox().clip(p.getEyePosition(),target.getBoundingBox().getCenter()).orElse(target.getBoundingBox().getCenter());
  var packet=new FistImpact(p.getId(),target.getId(),point,direction,.4f+.6f*s.strength);
  for(var observer:p.serverLevel().players())if(CinematicAudio.visualRange(observer,point,128)&&PacketDistributor.hasChannel(observer.connection,FistImpact.TYPE))PacketDistributor.sendToPlayer(observer,packet);
 }
}
