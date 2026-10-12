package dev.jujutsuvfx.mixin;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge 1.20.1 shader blend cache, restored together with each effect's GL state. */
@Mixin(BlendMode.class)
public interface EffectBlendModeAccessor {
    @Accessor("lastApplied")
    static BlendMode jujutsuvfx$getLastApplied() {
        throw new AssertionError("Mixin accessor was not transformed");
    }

    @Accessor("lastApplied")
    static void jujutsuvfx$setLastApplied(BlendMode blend) {
        throw new AssertionError("Mixin accessor was not transformed");
    }
}
