package dev.jujutsuvfx.item;
import dev.jujutsuvfx.server.YutaDomain;
import dev.jujutsuvfx.server.TechniqueGestures;
import dev.jujutsuvfx.network.SkillGesture;
import dev.jujutsuvfx.core.YutaDomainRules;
import dev.jujutsuvfx.config.VfxConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Hold the seal until commitment. Releasing early never schedules a delayed domain. */
public final class YutaDomainItem extends Item {
 private static final int USE_TICKS=72000;
 public YutaDomainItem(){super(new Properties().stacksTo(1).rarity(Rarity.RARE));}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
  var stack=player.getItemInHand(hand);
  if(!player.isAlive()||player.isSpectator()||player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
  if(player instanceof ServerPlayer server&&YutaDomain.active(server))return InteractionResultHolder.fail(stack);
  if(!player.isUsingItem()){
   player.startUsingItem(hand);
   if(!player.isUsingItem())return InteractionResultHolder.fail(stack);
   if(player instanceof ServerPlayer server)TechniqueGestures.start(server,server.tickCount,SkillGesture.YUTA,hand,false);
  }
  return InteractionResultHolder.consume(stack);
 }
 @Override public int getUseDuration(ItemStack stack){return USE_TICKS;}
 @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
 @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining){
  if(!(entity instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator())return;
  if(!YutaDomainRules.playerChargeComplete(USE_TICKS-remaining))return;
  if(!player.getCooldowns().isOnCooldown(this)&&YutaDomain.playerCast(player))
   player.getCooldowns().addCooldown(this,VfxConfig.yutaSeconds()*20+100);
  TechniqueGestures.releaseCurrent(player,SkillGesture.YUTA);
  player.stopUsingItem();
 }
 @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
  if(entity instanceof ServerPlayer player)TechniqueGestures.releaseCurrent(player,SkillGesture.YUTA);
 }
 @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
 @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand()).getResult();}
}
