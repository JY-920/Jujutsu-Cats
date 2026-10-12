package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Containers.class)
abstract class SkillContainerDropsMixin {
    @Inject(method = "dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/Container;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jujutsu$skipBlockContainerDrops(Level level, BlockPos pos, Container container,
                                                       CallbackInfo ci) {
        // Do not clear the inventory: Sable may be transferring this intact block entity.
        if (SkillBlockDrops.suppressed(level)) ci.cancel();
    }

    @Inject(method = "dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/NonNullList;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jujutsu$skipBlockItemListDrops(Level level, BlockPos pos, NonNullList<ItemStack> items,
                                                      CallbackInfo ci) {
        // Entity-container drops use a different overload and remain untouched.
        if (SkillBlockDrops.suppressed(level)) ci.cancel();
    }
}
