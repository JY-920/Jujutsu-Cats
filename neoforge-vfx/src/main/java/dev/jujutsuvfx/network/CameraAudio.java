package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Observer microphone lease; a negative actor stops it. Contains no actor-control input. */
public record CameraAudio(int actor,Vec3 point) implements CustomPacketPayload {
    public static final Type<CameraAudio> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"camera_audio_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CameraAudio> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.actor);b.writeDouble(p.point.x);b.writeDouble(p.point.y);b.writeDouble(p.point.z);},
            b->new CameraAudio(b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
