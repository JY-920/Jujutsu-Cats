package dev.jujutsuvfx.server;
import dev.jujutsuvfx.item.DanceItem;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
/** Consume before pet sitting, trading or armor-stand equipment handling. */
@EventBusSubscriber(modid="jujutsuvfx")
public final class DanceInteraction {
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void specific(PlayerInteractEvent.EntityInteractSpecific e){
  if(e.getItemStack().getItem() instanceof DanceItem&&e.getTarget() instanceof LivingEntity target){e.setCancellationResult(e.getItemStack().interactLivingEntity(e.getEntity(),target,e.getHand()));e.setCanceled(true);}
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void ordinary(PlayerInteractEvent.EntityInteract e){
  if(e.getItemStack().getItem() instanceof DanceItem&&e.getTarget() instanceof LivingEntity target){e.setCancellationResult(e.getItemStack().interactLivingEntity(e.getEntity(),target,e.getHand()));e.setCanceled(true);}
 }
}
