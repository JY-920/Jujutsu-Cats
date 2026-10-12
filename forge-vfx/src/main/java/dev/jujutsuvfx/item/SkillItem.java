package dev.jujutsuvfx.item;

import dev.jujutsuvfx.server.LimitlessSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** A press starts one skill; using the item latches input until the button is released. */
public final class SkillItem extends Item {
    public enum Kind { BLUE, RED, PURPLE, FUGA, VOID, SHRINE }

    // Installed only by client setup. The common item never loads renderer/client classes on a server.
    public interface ClientActions {
        default void press(Player player, Kind kind, InteractionHand hand) {}
        default void release(Player player, Kind kind) {}
    }

    private static ClientActions client = new ClientActions() {};
    private final Kind kind;

    public SkillItem(Kind kind) {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
        this.kind = kind;
    }

    public static void setClientActions(ClientActions actions) { client = actions; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator()) return InteractionResultHolder.fail(stack);
        if (!player.isUsingItem()) {
            player.startUsingItem(hand);
            if (!player.isUsingItem()) return InteractionResultHolder.fail(stack);
            if (level.isClientSide) client.press(player, kind, hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Aiming at terrain must cast Blue too, rather than activating the targeted block first.
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        return player == null ? InteractionResult.PASS
                : use(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public int getUseDuration(ItemStack stack) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (kind != Kind.BLUE && kind != Kind.RED && kind != Kind.PURPLE) return;
        if (level.isClientSide && entity instanceof Player player) client.release(player, kind);
        else if (entity instanceof ServerPlayer player) {
            if (kind == Kind.BLUE) LimitlessSkills.releaseBlue(player, stack);
            else if (kind == Kind.RED) LimitlessSkills.releaseRed(player, stack);
            else LimitlessSkills.releasePurple(player, stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".use").withStyle(ChatFormatting.AQUA));
    }
}
