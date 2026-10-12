package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HangingEntity.class)
abstract class SkillHangingDropsMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void jujutsu$skipBrokenAttachmentItems(ItemStack stack, float offset,
                                                  CallbackInfoReturnable<ItemEntity> cir) {
        if (SkillBlockDrops.attachmentSuppressed((HangingEntity) (Object) this)) cir.setReturnValue(null);
    }
}
