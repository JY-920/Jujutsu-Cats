package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class SkillFurnaceExperienceMixin {
    @Inject(method = "createExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;IF)V",
            at = @At("HEAD"), cancellable = true)
    private static void jujutsu$skipBrokenFurnaceExperience(ServerLevel level, Vec3 pos, int count,
                                                           float experience, CallbackInfo ci) {
        // Suppress only creation during terrain edits, preserving recipes and transferred inventory.
        if (SkillBlockDrops.suppressed(level)) ci.cancel();
    }
}
