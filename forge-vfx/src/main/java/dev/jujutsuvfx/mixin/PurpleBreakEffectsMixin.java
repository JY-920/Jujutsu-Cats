package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep real block updates and destruction hooks, suppress only the per-block cosmetic broadcast. */
@Mixin(Level.class)
public abstract class PurpleBreakEffectsMixin {
    @Redirect(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;levelEvent(ILnet/minecraft/core/BlockPos;I)V"))
    private void jujutsuvfx$quietPurpleDebris(Level level, int event, BlockPos pos, int data) {
        if (event != 2001 || !SkillBlockDrops.quietBreakEffects(level)) level.levelEvent(event, pos, data);
    }
}
