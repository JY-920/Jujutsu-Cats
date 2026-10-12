package dev.jujutsuvfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.client.CatCinematicCamera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hurt tilt is applied to the view matrix after Camera setup, outside angle events. */
@Mixin(GameRenderer.class)
abstract class CinematicCameraShakeMixin {
    @Inject(method={"bobHurt","bobView"},at=@At("HEAD"),cancellable=true)
    private void jujutsu$steadyShrineInsert(PoseStack poses,float partial,CallbackInfo ci){
        if(CatCinematicCamera.steadyGojoView())ci.cancel();
    }
}
