package dev.jujutsuvfx.item;

import dev.jujutsuvfx.server.MahoragaSummoning;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** Cat conversion catalysts; the wheel also equips Sukuna's adaptation wheel. */
public final class SummoningRelicItem extends Item {
    private final String key;
    public SummoningRelicItem(String id) {
        super(new Properties().rarity(Rarity.RARE));
        key = "item.jujutsuvfx." + id;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return MahoragaSummoning.use(stack, player, target);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(key + ".use").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.jujutsuvfx.gojo_teacher.keep").withStyle(ChatFormatting.GRAY));
    }
}
