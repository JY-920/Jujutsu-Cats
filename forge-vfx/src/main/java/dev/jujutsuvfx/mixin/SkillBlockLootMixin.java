package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class SkillBlockLootMixin {
    @Inject(method = "getDrops(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void jujutsu$skipTerrainLoot(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        // The state wrapper also covers blocks overriding getDrops and explosion loot collection.
        if (SkillBlockDrops.suppressed(params.getLevel())) cir.setReturnValue(List.of());
    }
}
