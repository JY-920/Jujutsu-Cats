package dev.jujutsuvfx.item;

import dev.jujutsuvfx.core.SustainedBeam;
import dev.jujutsuvfx.server.PlayerUzumaki;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Vanilla held-use lifecycle; only the server may commit the discharge. */
public final class MaximumUzumakiItem extends Item {
 private final boolean love;
 public MaximumUzumakiItem(){this(false);}
 public MaximumUzumakiItem(boolean love){super(new Properties().stacksTo(1).rarity(Rarity.EPIC));this.love=love;}
 @Override public void appendHoverText(ItemStack stack,Level context,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){
  lines.add(net.minecraft.network.chat.Component.translatable("item.jujutsuvfx."+(love?"love_beam":"maximum_uzumaki")+".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
 }
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
  ItemStack stack=player.getItemInHand(hand);
  if(!player.isAlive()||player.isSpectator()||player.isUsingItem()||player.getCooldowns().isOnCooldown(this))
   return InteractionResultHolder.fail(stack);
  player.startUsingItem(hand);
  if(!player.isUsingItem())return InteractionResultHolder.fail(stack);
  if(player instanceof ServerPlayer server)PlayerUzumaki.start(server,hand);
  return InteractionResultHolder.consume(stack);
 }
 @Override public int getUseDuration(ItemStack stack){return SustainedBeam.USE_TICKS;}
 @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
 @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
  if(entity instanceof ServerPlayer player)PlayerUzumaki.cancel(player);
  return stack;
 }
 @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
  if(entity instanceof ServerPlayer player)PlayerUzumaki.cancel(player);
 }
 @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
 @Override public InteractionResult useOn(UseOnContext context){
  return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand()).getResult();
 }
}
