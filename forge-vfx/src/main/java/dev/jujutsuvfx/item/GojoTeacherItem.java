package dev.jujutsuvfx.item;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.GojoCat;
import dev.jujutsuvfx.entity.VfxEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/** The supplied blindfold replaces the original dye prop; retain its ID for saved stacks. */
@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class GojoTeacherItem extends Item {
    public GojoTeacherItem() { super(new Properties().rarity(Rarity.RARE)); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactCat(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(VfxItems.GOJO_TEACHER.get()) || !(event.getTarget() instanceof Cat cat)) return;
        if (cat.getType() != EntityType.CAT) return;
        // Cat consumes its owner's normal interaction to sit, before Item's callback.
        // Route this particular prop first on both sides, including already transformed cats.
        event.setCancellationResult(event.getItemStack().interactLivingEntity(event.getEntity(), cat, event.getHand()));
        event.setCanceled(true);
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof GojoCat) return InteractionResult.sidedSuccess(player.level().isClientSide);
        if (!(target instanceof Cat cat) || cat.getType() != EntityType.CAT) return InteractionResult.PASS;
        if (!cat.isAlive() || cat.isRemoved() || stack.isEmpty()) return InteractionResult.FAIL;
        if (!(cat.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        GojoCat gojo = VfxEntities.GOJO_CAT.get().create(level);
        if (gojo == null) return InteractionResult.FAIL;

        // Carry pet state and serialized mod attachments across, but give the new entity
        // its own identity. Connections are transferred only after spawning succeeds.
        CompoundTag saved = cat.saveWithoutId(new CompoundTag());
        saved.remove("UUID");
        saved.remove("Passengers");
        saved.remove("Leash");
        gojo.load(saved);
        gojo.copyPosition(cat);
        gojo.setYHeadRot(cat.getYHeadRot());
        gojo.yHeadRotO = cat.getYHeadRot();
        gojo.yBodyRot = cat.yBodyRot;
        gojo.yBodyRotO = cat.yBodyRot;
        gojo.setOnGround(cat.onGround());
        gojo.setPersistenceRequired();
        gojo.beginTransformation();
        if (!level.addFreshEntity(gojo)) return InteractionResult.FAIL;

        Entity leashHolder = cat.getLeashHolder();
        if (leashHolder != null) {
            cat.dropLeash(true, false);
            gojo.setLeashedTo(leashHolder, true);
        } else if (((dev.jujutsuvfx.mixin.MobLeashAccessor)cat).jujutsuvfx$getLeash() != null) {
            // A saved leash may still be waiting for its holder's chunk to load.
            ((dev.jujutsuvfx.mixin.MobLeashAccessor)(Object)gojo).jujutsuvfx$setLeash(((dev.jujutsuvfx.mixin.MobLeashAccessor)cat).jujutsuvfx$getLeash());
            ((dev.jujutsuvfx.mixin.MobLeashAccessor)cat).jujutsuvfx$setLeash(null);
        }
        Entity vehicle = cat.getVehicle();
        if (vehicle != null) {
            cat.stopRiding();
            gojo.startRiding(vehicle, true);
        }
        for (Entity passenger : List.copyOf(cat.getPassengers())) passenger.startRiding(gojo, true);
        cat.discard();
        gojo.gameEvent(GameEvent.ENTITY_PLACE, player);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.CONSUME;
    }

    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.jujutsuvfx.gojo_teacher.use").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.jujutsuvfx.gojo_teacher.keep").withStyle(ChatFormatting.GRAY));
    }
}
