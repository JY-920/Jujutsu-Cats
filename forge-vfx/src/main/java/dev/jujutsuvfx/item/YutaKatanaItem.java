package dev.jujutsuvfx.item;

import dev.jujutsuvfx.core.YutaKatanaMotion;
import dev.jujutsuvfx.server.YutaKatanaCombat;
import net.minecraft.ChatFormatting;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

import net.minecraft.world.level.Level;
import java.util.List;

public final class YutaKatanaItem extends Item {
 private static final String KEY="jujutsuvfx_katana_imbued";
 private static java.util.function.Consumer<LivingEntity> clientSwing=actor->{};
 public static void setSwing(java.util.function.Consumer<LivingEntity> callback){clientSwing=callback;}
 public YutaKatanaItem(){super(new Properties().stacksTo(1).rarity(Rarity.EPIC));}
 @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot){
  if(slot!=EquipmentSlot.MAINHAND)return super.getDefaultAttributeModifiers(slot);
  return com.google.common.collect.ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Yuta katana damage",11,AttributeModifier.Operation.ADDITION),Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Yuta katana speed",-2,AttributeModifier.Operation.ADDITION));
 }
 @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
  consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){
   private dev.jujutsuvfx.client.YutaKatanaRenderer renderer;
   @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new dev.jujutsuvfx.client.YutaKatanaRenderer();return renderer;}
  });
 }
 public static boolean imbued(ItemStack stack){
  if(!(stack.getItem() instanceof YutaKatanaItem))return false;
  return stack.hasTag()&&stack.getTag().getBoolean(KEY);
 }
 private static void imbue(ItemStack stack){stack.getOrCreateTag().putBoolean(KEY,true);}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
  ItemStack stack=player.getItemInHand(hand);
  if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResultHolder.fail(stack);
  player.startUsingItem(hand);return InteractionResultHolder.consume(stack);
 }
 @Override public int getUseDuration(ItemStack stack){return 72000;}
 @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
 @Override public void onUseTick(Level level,LivingEntity actor,ItemStack stack,int remaining){
  if(!level.isClientSide&&72000-remaining==YutaKatanaMotion.CHARGE){
   imbue(stack);
   if(actor instanceof Player p)p.inventoryMenu.broadcastChanges();
   level.playSound(null,actor.blockPosition(),net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.PLAYERS,.65f,1.4f);
  }
 }
 @Override public boolean onLeftClickEntity(ItemStack stack,Player player,net.minecraft.world.entity.Entity target){
  if(!player.level().isClientSide)YutaKatanaCombat.prepare(player);
  return player.isUsingItem();
 }
 @Override public boolean onEntitySwing(ItemStack stack,LivingEntity actor){
  if(actor.getMainHandItem()==stack&&!actor.isUsingItem()){
   if(actor.level().isClientSide)clientSwing.accept(actor);else YutaKatanaCombat.swing(actor);
  }return false;
 }
 @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){YutaKatanaCombat.confirmed(stack,attacker,target);return true;}
 @Override public void appendHoverText(ItemStack stack,Level context,List<Component> lines,TooltipFlag flag){
  lines.add(Component.translatable("item.jujutsuvfx.yuta_katana.wipe").withStyle(ChatFormatting.GRAY));
  lines.add(Component.translatable(imbued(stack)?"item.jujutsuvfx.yuta_katana.charged":"item.jujutsuvfx.yuta_katana.bonus").withStyle(ChatFormatting.LIGHT_PURPLE));
 }
}
