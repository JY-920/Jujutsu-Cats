package dev.jujutsuvfx.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The real lightmap remains valid even when a shader pack reuses texture unit two. */
@Mixin(LightTexture.class)
public interface HandLightTextureAccessor {
    @Accessor("lightTexture")
    DynamicTexture jujutsuvfx$handLightTexture();
}
