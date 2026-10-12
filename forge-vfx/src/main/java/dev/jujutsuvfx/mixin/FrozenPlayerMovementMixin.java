package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.DomainFreeze;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class FrozenPlayerMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private boolean clientVehicleIsFloating;
    @Inject(method="tick",at=@At("HEAD"))
    private void jujutsu$allowSuspension(CallbackInfo ci){
        if(DomainFreeze.hold(player))clientIsFloating=false;
        if(player.isPassenger()&&DomainFreeze.hold(player.getRootVehicle()))clientVehicleIsFloating=false;
    }
    @Inject(method="handleMovePlayer",at=@At("HEAD"),cancellable=true)
    private void jujutsu$holdPlayer(ServerboundMovePlayerPacket packet,CallbackInfo ci){
        // The first invocation may be on Netty; let vanilla enqueue it on the server thread.
        if(player.getServer().isSameThread()&&DomainFreeze.hold(player))ci.cancel();
    }
    @Inject(method="handleMoveVehicle",at=@At("HEAD"),cancellable=true)
    private void jujutsu$holdVehicle(ServerboundMoveVehiclePacket packet,CallbackInfo ci){
        if(player.getServer().isSameThread()&&DomainFreeze.hold(player.getRootVehicle()))ci.cancel();
    }
}
