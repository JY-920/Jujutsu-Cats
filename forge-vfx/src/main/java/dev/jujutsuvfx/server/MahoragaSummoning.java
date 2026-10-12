package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.entity.VfxEntities;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.util.List;

/** Relic interactions precede vanilla pet sitting; sunflowers keep their normal use. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class MahoragaSummoning {
    private MahoragaSummoning() {}
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack=event.getItemStack();
        boolean wheel=stack.is(VfxItems.MAHORAGA_WHEEL.get()),finger=stack.is(VfxItems.SUKUNA_FINGER.get());
        if ((!wheel&&!finger)||!(event.getTarget() instanceof LivingEntity target)) return;
        if (!(target instanceof Cat&&target.getType()==EntityType.CAT)
                && !(wheel&&target instanceof SorcererCat cat&&cat.isSukuna())) return;
        event.setCancellationResult(use(stack,event.getEntity(),target));
        event.setCanceled(true);
    }
    public static InteractionResult use(ItemStack stack,Player player,LivingEntity target) {
        boolean wheel=stack.is(VfxItems.MAHORAGA_WHEEL.get()),finger=stack.is(VfxItems.SUKUNA_FINGER.get());
        if (!wheel&&!finger) return InteractionResult.PASS;
        if (!target.isAlive()||target.isRemoved()||stack.isEmpty()||player.isSpectator()||target.level()!=player.level()) return InteractionResult.FAIL;
        boolean attach=wheel&&target instanceof SorcererCat cat&&cat.isSukuna();
        if (!attach&&(!(target instanceof Cat)||target.getType()!=EntityType.CAT)) return InteractionResult.PASS;
        if (!(target.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Item used=stack.getItem();
        boolean changed=attach?((SorcererCat)target).attachWheel():convert(level,(Cat)target,player,wheel);
        if (!changed) return attach?InteractionResult.CONSUME:InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(used));
        return InteractionResult.CONSUME;
    }
    private static boolean convert(ServerLevel level,Cat original,Player player,boolean mahoraga) {
        SorcererCat summoned=mahoraga?VfxEntities.MAHORAGA_CAT.get().create(level):VfxEntities.SUKUNA_CAT.get().create(level);
        if (summoned==null) return false;
        CompoundTag saved=original.saveWithoutId(new CompoundTag());
        saved.remove("UUID"); saved.remove("Passengers"); saved.remove("Leash");
        // Pet identity is retained; vanilla Cat's 10 HP/speed must not replace fighter attributes.
        saved.remove("Attributes"); saved.remove("Health"); saved.remove("HurtTime"); saved.remove("DeathTime");
        summoned.load(saved); summoned.setHealth(summoned.getMaxHealth());
        summoned.setOrderedToSit(false); summoned.setInSittingPose(false);
        summoned.copyPosition(original);summoned.setYHeadRot(original.getYHeadRot());summoned.yHeadRotO=original.getYHeadRot();
        summoned.yBodyRot=summoned.yBodyRotO=original.yBodyRot;summoned.setOnGround(original.onGround());
        summoned.setPersistenceRequired();
        if (!mahoraga) summoned.beginTransformation();
        if (!level.addFreshEntity(summoned)) return false;
        Entity holder=original.getLeashHolder();
        if (holder!=null) { original.dropLeash(true,false);summoned.setLeashedTo(holder,true); }
        else if (((dev.jujutsuvfx.mixin.MobLeashAccessor)original).jujutsuvfx$getLeash()!=null) { ((dev.jujutsuvfx.mixin.MobLeashAccessor)summoned).jujutsuvfx$setLeash(((dev.jujutsuvfx.mixin.MobLeashAccessor)original).jujutsuvfx$getLeash());((dev.jujutsuvfx.mixin.MobLeashAccessor)original).jujutsuvfx$setLeash(null); }
        Entity vehicle=original.getVehicle();
        if (vehicle!=null) { original.stopRiding();summoned.startRiding(vehicle,true); }
        for (Entity passenger:List.copyOf(original.getPassengers())) passenger.startRiding(summoned,true);
        original.discard();summoned.gameEvent(GameEvent.ENTITY_PLACE,player);return true;
    }
}
