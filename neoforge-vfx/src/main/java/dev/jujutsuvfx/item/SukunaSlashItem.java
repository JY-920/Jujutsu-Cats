package dev.jujutsuvfx.item;

import dev.jujutsuvfx.server.SukunaSlashSkills;
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

/** Dismantle strikes on press; Cleave charges while held and releases one finite attack on mouse-up. */
public final class SukunaSlashItem extends Item {
    public enum Kind { DISMANTLE, CLEAVE }

    // Installed only by client setup; this common item cannot load a client renderer on a server.
    public interface ClientActions {
        void press(Player player, Kind kind, InteractionHand hand);
    }

    private static ClientActions client = (player, kind, hand) -> {};
    private final Kind kind;

    public SukunaSlashItem(Kind kind) {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
        this.kind = kind;
    }

    public Kind kind() { return kind; }
    public static void setClientActions(ClientActions actions) { client = actions; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator() || player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);
        if (player.isUsingItem()) return InteractionResultHolder.consume(stack);
        if (level.isClientSide) {
            player.startUsingItem(hand);
            if (player.isUsingItem()) client.press(player, kind, hand);
        } else if (player instanceof ServerPlayer serverPlayer) {
            if (!SukunaSlashSkills.cast(serverPlayer, hand, kind)) return InteractionResultHolder.fail(stack);
            player.startUsingItem(hand);
            // Vanilla broadcasts this to tracking players, allowing a short remote gesture
            // without adding a second skill-control channel.
            player.swing(hand, true);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return useOn(context); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        return player == null ? InteractionResult.PASS
                : use(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseDuration) {
        if (kind == Kind.CLEAVE && entity instanceof ServerPlayer player) SukunaSlashSkills.release(player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".use").withStyle(ChatFormatting.RED));
    }
}
