package dev.jujutsuvfx.item;

import dev.jujutsuvfx.server.FistCombat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import java.util.List;

/** Ordinary melee input owns damage, cooldown, critical hits and resistance-aware knockback. */
public final class CursedFistItem extends Item {
    public CursedFistItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 5,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -1.6,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build()));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Enables the vanilla successful-melee post hook; there is no second damage call.
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // This hook follows native knockback and post-attack enchantments, before cooldown resets.
        FistCombat.confirmed(stack, attacker, target);
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.aura").withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.strike").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.derive").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.air").withStyle(ChatFormatting.GRAY));
        if (FistCombo.ready(stack)) lines.add(Component.translatable("item.jujutsuvfx.cursed_fist.charged")
                .withStyle(ChatFormatting.RED));
    }
}
