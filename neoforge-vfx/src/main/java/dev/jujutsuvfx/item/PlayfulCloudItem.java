package dev.jujutsuvfx.item;

import dev.jujutsuvfx.core.PlayfulCloudMotion;
import dev.jujutsuvfx.server.PlayfulCloudCombat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import java.util.List;

/** A physical cursed tool. Vanilla melee owns ordinary hit checks, shields and attack cooldown. */
public final class PlayfulCloudItem extends Item {
    public interface ClientActions {
        default void swing(LivingEntity actor) {}
        default void release(LivingEntity actor,int charge) {}
        default void cancel(LivingEntity actor) {}
    }
    private static ClientActions client = new ClientActions() {};
    public static void setClientActions(ClientActions actions) { client=actions; }
    public PlayfulCloudItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_ID,10,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_ID,-1.8,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build()));
    }
    @Override public boolean onLeftClickEntity(ItemStack stack,Player player,net.minecraft.world.entity.Entity target) {
        if (!player.level().isClientSide) PlayfulCloudCombat.prepare(player);
        return player.isUsingItem() || player.getCooldowns().isOnCooldown(this);
    }
    @Override public boolean onEntitySwing(ItemStack stack,LivingEntity actor) {
        if (actor.getMainHandItem()!=stack || actor.isUsingItem()) return false;
        if (actor.level().isClientSide) client.swing(actor);
        else PlayfulCloudCombat.swing(actor);
        return false;
    }
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker) { return true; }
    @Override public void postHurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker) {
        PlayfulCloudCombat.confirmed(stack,attacker,target);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND || player.isSpectator() || !player.isAlive()
                || player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity actor) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity actor,int left) {
        int ticks=Math.max(0,Math.min(PlayfulCloudMotion.CHARGE_TICKS,72000-left));
        if(ticks<4){if(level.isClientSide)client.cancel(actor);return;}
        if(level.isClientSide){
            client.release(actor,ticks);
            if(actor instanceof Player p)p.getCooldowns().addCooldown(this,PlayfulCloudMotion.HEAVY_RECOVERY);
        }
        else PlayfulCloudCombat.release(stack,actor,ticks);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("item.jujutsuvfx.playful_cloud.combo").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.jujutsuvfx.playful_cloud.heavy").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.jujutsuvfx.playful_cloud.physical").withStyle(ChatFormatting.DARK_GRAY));
    }
}
