package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.client.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class PurpleClientMovementMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 jujutsu$slowPurpleFlight(Vec3 movement) {
        var player = Minecraft.getInstance().player;
        if ((Object) this == player && player != null && player.getAbilities().flying && ClientEvents.purplePreparing())
            return new Vec3(movement.x, movement.y * .2, movement.z);
        return movement;
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void jujutsu$holdPurpleAim(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && (ClientEvents.purpleInputLocked()
                ||dev.jujutsuvfx.client.ClientEntityStasis.localLocked())) ci.cancel();
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void jujutsu$holdPurplePosition(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && (ClientEvents.purpleInputLocked()
                ||dev.jujutsuvfx.client.ClientEntityStasis.localLocked())) {
            ((Entity) (Object) this).setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }
}
