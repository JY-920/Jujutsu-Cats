package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Input edges only: no client-supplied target, location, duration, movement or damage. */
public record MartialInput(int sequence,int edge) implements CustomPacketPayload {
    public static final int PRESS=0,RELEASE=1,CANCEL=2;
    public static final Type<MartialInput> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"martial_input_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MartialInput> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.sequence);b.writeByte(p.edge);},b->new MartialInput(b.readVarInt(),b.readUnsignedByte()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
