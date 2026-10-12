package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** One confirmed contact beat, not one pause per victim in a circular attack. */
public record MartialHit(int actor,UUID identity,int action,int move,int age,boolean black) implements CustomPacketPayload {
    public static final Type<MartialHit> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"martial_hit_v1"));
    public static final StreamCodec<FriendlyByteBuf,MartialHit> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.actor);b.writeUUID(p.identity);b.writeVarInt(p.action);b.writeByte(p.move);b.writeByte(p.age);b.writeBoolean(p.black);
    },b->new MartialHit(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
