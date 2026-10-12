package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.LimitlessSkills;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PurplePlayerMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private boolean clientVehicleIsFloating;

    @Inject(method = "tick", at = @At("HEAD"))
    private void jujutsu$purpleSuspension(CallbackInfo ci) {
        if (LimitlessSkills.holdPurplePlayer(player)) {
            clientIsFloating = false;
            clientVehicleIsFloating = false;
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void jujutsu$purplePositionAndAim(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // The helper returns false on Netty, so vanilla can enqueue the packet on the server thread.
        if (LimitlessSkills.holdPurplePlayer(player)) ci.cancel();
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void jujutsu$purpleMountMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (LimitlessSkills.holdPurplePlayer(player)) ci.cancel();
    }

    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void jujutsu$purpleInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (LimitlessSkills.holdPurplePlayer(player)) ci.cancel();
    }
}
