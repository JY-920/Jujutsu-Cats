package dev.jujutsuvfx.item;

import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.jujutsuvfx.server.GojoCatControl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class GojoCatDebugStick extends Item {
    public GojoCatDebugStick() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof SorcererCat cat)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) GojoCatControl.open(serverPlayer, cat);
        // One physical click opens one session, even while the use button is still held.
        player.startUsingItem(hand);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.jujutsuvfx.gojo_cat_debug_stick.toggle").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.jujutsuvfx.gojo_cat_debug_stick.hold").withStyle(ChatFormatting.GRAY));
    }
}
