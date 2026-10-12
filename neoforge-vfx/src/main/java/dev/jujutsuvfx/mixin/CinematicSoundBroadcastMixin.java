package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.CinematicAudio;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class CinematicSoundBroadcastMixin {
    @Inject(method="broadcast",at=@At("HEAD"))
    private void jujutsu$observerSounds(Player excluded,double x,double y,double z,double range,ResourceKey<Level> dimension,Packet<?> packet,CallbackInfo ci){
        CinematicAudio.broadcast((PlayerList)(Object)this,excluded,x,y,z,range,dimension,packet);
    }
}
