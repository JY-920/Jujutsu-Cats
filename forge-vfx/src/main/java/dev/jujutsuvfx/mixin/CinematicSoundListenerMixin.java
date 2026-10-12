package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.client.CatCinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundEngine.class)
abstract class CinematicSoundListenerMixin {
    @Redirect(method="updateSource",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 jujutsu$microphone(Camera camera){return CatCinematicCamera.audioPosition(camera.getPosition());}
}
