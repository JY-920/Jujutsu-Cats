package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.platform.PacketDistributor;
import java.util.UUID;

/** Server-only snapshots; negative age removes the matching cast, never requests gameplay. */
public record UzumakiState(int actor,UUID owner,int session,int age,boolean fired,Vec3 origin,Vec3 direction,double length,
 int kind,int clashId,int clashAge,Vec3 otherOrigin,Vec3 contact)
 implements CustomPacketPayload {
 public static final Type<UzumakiState> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"uzumaki_state_v2"));
 public static final StreamCodec<FriendlyByteBuf,UzumakiState> CODEC=StreamCodec.of((b,p)->{
  b.writeVarInt(p.actor);b.writeUUID(p.owner);b.writeInt(p.session);b.writeInt(p.age);b.writeBoolean(p.fired);
  write(b,p.origin);write(b,p.direction);b.writeDouble(p.length);
  b.writeByte(p.kind);b.writeInt(p.clashId);b.writeInt(p.clashAge);write(b,p.otherOrigin);write(b,p.contact);
 },b->new UzumakiState(b.readVarInt(),b.readUUID(),b.readInt(),b.readInt(),b.readBoolean(),read(b),read(b),b.readDouble(),
 b.readUnsignedByte(),b.readInt(),b.readInt(),read(b),read(b)));
 private static void write(FriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
 private static Vec3 read(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
 public void send(ServerPlayer player){if(PacketDistributor.hasChannel(player.connection,TYPE))PacketDistributor.sendToPlayer(player,this);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
