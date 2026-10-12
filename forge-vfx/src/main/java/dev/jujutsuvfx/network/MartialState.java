package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Compact authoritative action snapshots for actor, spectators and late tracking clients. */
public record MartialState(int actor,UUID identity,int sequence,int action,int move,int age,int next,int held,int flags)
        implements CustomPacketPayload {
    public static final int HOLD=1,BLACK=2,REJECT=4;
    public static final Type<MartialState> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"martial_state_v2"));
    public static final StreamCodec<FriendlyByteBuf,MartialState> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.actor);b.writeUUID(p.identity);b.writeVarInt(p.sequence);b.writeVarInt(p.action);b.writeByte(p.move);
        b.writeVarInt(p.age);b.writeByte(p.next);b.writeVarInt(p.held);b.writeByte(p.flags);
    },b->new MartialState(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readUnsignedByte(),b.readVarInt(),
            b.readUnsignedByte(),b.readVarInt(),b.readUnsignedByte()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
