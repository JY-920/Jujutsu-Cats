package dev.jujutsuvfx.item;
import dev.jujutsuvfx.core.CursedSpeechShape;
import dev.jujutsuvfx.network.SkillGesture;
import dev.jujutsuvfx.server.CursedSpeech;
import dev.jujutsuvfx.server.TechniqueGestures;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Hold briefly to speak; the server validates targeting and owns the release. */
public final class CursedSpeechItem extends Item {
 public CursedSpeechItem(){super(new Properties().stacksTo(1).rarity(Rarity.RARE));}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
  ItemStack stack=player.getItemInHand(hand);
  if(!player.isAlive()||player.isSpectator()||player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
  if(!player.isUsingItem()){
   player.startUsingItem(hand);
   if(!player.isUsingItem())return InteractionResultHolder.fail(stack);
   if(player instanceof ServerPlayer server){
    TechniqueGestures.start(server,server.tickCount,SkillGesture.SPEECH,hand,false);
    CursedSpeech.charge(server);
   }
  }
  return InteractionResultHolder.consume(stack);
 }
 @Override public int getUseDuration(ItemStack stack){return CursedSpeechShape.RELEASE;}
 @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
 @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
  if(entity instanceof ServerPlayer player&&player.isAlive()&&!player.isSpectator()&&!player.getCooldowns().isOnCooldown(this)){
   CursedSpeech.release(player);
   TechniqueGestures.releaseCurrent(player,SkillGesture.SPEECH);
   player.getCooldowns().addCooldown(this,CursedSpeechShape.COOLDOWN);
  }
  return stack;
 }
 @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
  if(entity instanceof ServerPlayer player&&remaining>0){
   TechniqueGestures.cancelCurrent(player,SkillGesture.SPEECH);
   player.getCooldowns().addCooldown(this,10);
  }
 }
 @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
 @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand()).getResult();}
}
