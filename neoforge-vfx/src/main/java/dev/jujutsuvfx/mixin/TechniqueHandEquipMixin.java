package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.item.SkillItem;
import dev.jujutsuvfx.item.SukunaSlashItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A cast starts from the currently visible bare hand, without the vanilla use-item re-equip dip. */
@Mixin(ItemInHandRenderer.class)
abstract class TechniqueHandEquipMixin {
    @Inject(method = "itemUsed(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void jujutsu$keepCastingHandRaised(InteractionHand hand, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var item = player.getItemInHand(hand).getItem();
        // Successful right-click use normally zeroes this hand's equip height. Our gestures
        // already animate from the empty-hand pose, so that reset pulls the wrist offscreen
        // just as the casting blend begins. Leave the existing height/interpolation intact;
        // ItemInHandRenderer.tick still animates real hotbar and offhand item changes.
        if (item instanceof SkillItem || item instanceof SukunaSlashItem) ci.cancel();
    }
}
