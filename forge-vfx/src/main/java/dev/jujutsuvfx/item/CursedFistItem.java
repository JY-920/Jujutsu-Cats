package dev.jujutsuvfx.item;

import dev.jujutsuvfx.server.FistCombat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Ordinary melee input owns damage, cooldown, critical hits and resistance-aware knockback. */
public final class CursedFistItem extends Item {
    public CursedFistItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,AttributeModifier>
            getDefaultAttributeModifiers(EquipmentSlot slot) {
        if(slot!=EquipmentSlot.MAINHAND)return super.getDefaultAttributeModifiers(slot);
        return com.google.common.collect.ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Cursed fist damage",5,AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Cursed fist speed",-1.6,AttributeModifier.Operation.ADDITION));
    }
    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // In 1.20.1 this hook already runs after native knockback and confirmed damage.
        FistCombat.confirmed(stack,attacker,target);
        return true;
    }

    // Attack edges are handled by the martial timeline; prevent duplicate vanilla damage/mining.
    @Override public boolean onLeftClickEntity(ItemStack stack,net.minecraft.world.entity.player.Player player,
                                               net.minecraft.world.entity.Entity target) {
        return player.getMainHandItem()==stack;
    }
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.entity.player.Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.aura").withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.strike").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.derive").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.air").withStyle(ChatFormatting.GRAY));
        if (FistCombo.ready(stack)) lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.charged")
                .withStyle(ChatFormatting.RED));
    }
}
