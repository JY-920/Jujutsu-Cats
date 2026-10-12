package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** Observer-only hand timeline; release is an age in ticks, -1 while held, -2 for cancellation. */
public record SkillGesture(int actor,UUID owner,int session,int kind,int hand,int age,int release) implements CustomPacketPayload {
    public static final int BLUE=0,FURNACE=1,VOID=2,SHRINE=3,RETURN_TICKS=12;
    public static final Type<SkillGesture> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"skill_gesture_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkillGesture> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.actor);b.writeUUID(p.owner);b.writeInt(p.session);b.writeByte(p.kind);b.writeByte(p.hand);b.writeVarInt(p.age);b.writeInt(p.release);
    },b->new SkillGesture(b.readVarInt(),b.readUUID(),b.readInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readVarInt(),b.readInt()));
    public void send(ServerPlayer player){if(player.connection.hasChannel(TYPE))PacketDistributor.sendToPlayer(player,this);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
