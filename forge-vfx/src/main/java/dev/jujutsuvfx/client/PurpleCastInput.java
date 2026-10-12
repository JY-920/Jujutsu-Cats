package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;

/** Lock only the caster during the committed synthesis; other entities keep ticking normally. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class PurpleCastInput {
    private PurpleCastInput() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void movement(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player) return;
        if(ClientEntityStasis.localLocked()){
            var input=event.getInput();input.forwardImpulse=input.leftImpulse=0;
            input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;
            ClientEntityStasis.hold(mc.player);return;
        }
        LimitlessCast cast = ClientEvents.purpleCast();
        if ((cast == null || cast.phase() != 0) && mc.player.isUsingItem()
                && VfxItems.isPurple(mc.player.getUseItem()) && !mc.player.isPassenger()) {
            // Keep use latched until mouse-up so auto-launch cannot recast. Vanilla applies its
            // 0.2 horizontal use multiplier immediately after this event; undo only that slowdown.
            event.getInput().forwardImpulse *= 5;
            event.getInput().leftImpulse *= 5;
            return;
        }
        if (cast == null) return;
        if (cast.preparingPurple()) {
            // The held item's vanilla use slowdown remains in effect; do not multiply it a second time.
            mc.player.setSprinting(false);
        } else if (cast.committedPurple()) {
            var input = event.getInput();
            input.forwardImpulse = input.leftImpulse = 0;
            input.up = input.down = input.left = input.right = false;
            input.jumping = input.shiftKeyDown = false;
            cast.holdPlayer(mc);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LimitlessCast cast = ClientEvents.purpleCast();
        if (mc.level != null && !mc.isPaused() && cast != null) cast.holdPlayer(mc);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientEvents.purpleInputLocked()||ClientEntityStasis.localLocked()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
