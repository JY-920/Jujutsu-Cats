package dev.jujutsuvfx.item;

import dev.jujutsuvfx.core.YutaKatanaMotion;
import dev.jujutsuvfx.server.YutaKatanaCombat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class YutaKatanaItem extends Item {
 private static final String KEY="jujutsuvfx_katana_imbued";
 private static java.util.function.Consumer<LivingEntity> clientSwing=actor->{};
 public static void setSwing(java.util.function.Consumer<LivingEntity> callback){clientSwing=callback;}
 public YutaKatanaItem(){super(new Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(ItemAttributeModifiers.builder()
  .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_ID,11,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
  .add(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_ID,-2,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build()));}
 public static boolean imbued(ItemStack stack){
  if(!(stack.getItem() instanceof YutaKatanaItem))return false;
  CustomData data=stack.get(DataComponents.CUSTOM_DATA);return data!=null&&data.copyTag().getBoolean(KEY);
 }
 private static void imbue(ItemStack stack){CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putBoolean(KEY,true));}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
  ItemStack stack=player.getItemInHand(hand);
  if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResultHolder.fail(stack);
  player.startUsingItem(hand);return InteractionResultHolder.consume(stack);
 }
 @Override public int getUseDuration(ItemStack stack,LivingEntity actor){return 72000;}
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
 @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){return true;}
 @Override public void postHurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){YutaKatanaCombat.confirmed(stack,attacker,target);}
 @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
  lines.add(Component.translatable("item.jujutsuvfx.yuta_katana.wipe").withStyle(ChatFormatting.GRAY));
  lines.add(Component.translatable(imbued(stack)?"item.jujutsuvfx.yuta_katana.charged":"item.jujutsuvfx.yuta_katana.bonus").withStyle(ChatFormatting.LIGHT_PURPLE));
 }
}
