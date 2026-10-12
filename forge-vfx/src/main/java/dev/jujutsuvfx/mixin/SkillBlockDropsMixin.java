package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Block.class)
abstract class SkillBlockDropsMixin {
    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jujutsu$skipBlockDrops(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (SkillBlockDrops.suppressed(level)) ci.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jujutsu$skipBlockEntityDrops(BlockState state, LevelAccessor accessor, BlockPos pos,
                                                    BlockEntity blockEntity, CallbackInfo ci) {
        if (accessor instanceof Level level && SkillBlockDrops.suppressed(level)) ci.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jujutsu$skipAttributedBlockDrops(BlockState state, Level level, BlockPos pos,
                                                        BlockEntity blockEntity, Entity breaker,
                                                        ItemStack tool, CallbackInfo ci) {
        if (SkillBlockDrops.suppressed(level)) ci.cancel();
    }

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jujutsu$skipBlockItemCreation(Level level, Supplier<ItemEntity> factory,
                                                     ItemStack stack, CallbackInfo ci) {
        // Runs before Supplier.get(), including Sable's transformed block-drop supplier.
        if (SkillBlockDrops.suppressed(level)) ci.cancel();
    }

    @Inject(method = "popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"), cancellable = true)
    private void jujutsu$skipBlockExperience(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        // Explosion spawnAfterBreak can award ore experience independently of the loot list.
        if (SkillBlockDrops.suppressed(level)) ci.cancel();
    }
}
