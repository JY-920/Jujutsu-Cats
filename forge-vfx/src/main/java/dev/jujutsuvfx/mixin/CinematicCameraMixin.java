package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.client.CatCinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CinematicCameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="setup",at=@At("TAIL"))
    private void jujutsu$composeCatShot(BlockGetter level,Entity entity,boolean thirdPerson,boolean reverse,float partial,CallbackInfo ci){
        CatCinematicCamera.Frame frame=CatCinematicCamera.sample(entity,partial);
        if(frame==null)return;
        setPosition(frame.eye());detached=frame.detached();
        // Forge posts ComputeCameraAngles in GameRenderer after setup.
        setRotation(frame.yaw(),frame.pitch());
    }
}
