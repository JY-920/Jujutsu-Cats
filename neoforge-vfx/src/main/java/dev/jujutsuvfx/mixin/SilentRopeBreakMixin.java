package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Simulated creates its rope-coupling ItemEntity directly, outside vanilla Block.popResource. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior", remap = false)
abstract class SilentRopeBreakMixin {
    @Shadow private Level getLevel() { throw new AssertionError(); }

    @ModifyVariable(method = "destroyRope", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean jujutsu$skipRopeCouplingDrop(boolean returnItem) {
        return returnItem && !SkillBlockDrops.suppressed(getLevel());
    }
}
