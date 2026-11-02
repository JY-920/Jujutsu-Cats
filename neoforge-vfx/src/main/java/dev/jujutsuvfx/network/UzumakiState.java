package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Server-only snapshots; negative age removes the matching cast, never requests gameplay. */
public record UzumakiState(int actor,UUID owner,int session,int age,boolean fired,Vec3 origin,Vec3 direction,double length,
 int kind,int clashId,int clashAge,Vec3 otherOrigin,Vec3 contact,List<Vec3> path,List<Vec3> otherPath)
 implements CustomPacketPayload {
 public static final Type<UzumakiState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"uzumaki_state_v3"));
 public UzumakiState { path=List.copyOf(path);otherPath=List.copyOf(otherPath); }
 public static final StreamCodec<RegistryFriendlyByteBuf,UzumakiState> CODEC=StreamCodec.of((b,p)->{
  b.writeVarInt(p.actor);b.writeUUID(p.owner);b.writeInt(p.session);b.writeInt(p.age);b.writeBoolean(p.fired);
  write(b,p.origin);write(b,p.direction);b.writeDouble(p.length);
  b.writeByte(p.kind);b.writeInt(p.clashId);b.writeInt(p.clashAge);write(b,p.otherOrigin);write(b,p.contact);
  writePath(b,p.path,p.origin);writePath(b,p.otherPath,p.otherOrigin);
 },b->{
  int actor=b.readVarInt();UUID owner=b.readUUID();int session=b.readInt(),age=b.readInt();boolean fired=b.readBoolean();
  Vec3 origin=read(b),direction=read(b);double length=b.readDouble();int kind=b.readUnsignedByte(),clashId=b.readInt(),clashAge=b.readInt();
  Vec3 other=read(b),contact=read(b);
  return new UzumakiState(actor,owner,session,age,fired,origin,direction,length,kind,clashId,clashAge,other,contact,readPath(b,origin,false),readPath(b,other,true));
 });
 private static void writePath(RegistryFriendlyByteBuf b,List<Vec3> path,Vec3 origin){
  b.writeVarInt(path.size());for(Vec3 point:path){Vec3 offset=point.subtract(origin);b.writeFloat((float)offset.x);b.writeFloat((float)offset.y);b.writeFloat((float)offset.z);}
 }
 private static List<Vec3> readPath(RegistryFriendlyByteBuf b,Vec3 origin,boolean optional){
  int count=b.readVarInt();if(count<(optional?0:1)||count>dev.jujutsuvfx.core.BeamPath.MAX_POINTS)throw new IllegalArgumentException("Invalid beam point count");
  List<Vec3> points=new ArrayList<>();for(int i=0;i<count;i++)points.add(origin.add(b.readFloat(),b.readFloat(),b.readFloat()));return points;
 }
 private static void write(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
 private static Vec3 read(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
 public void send(ServerPlayer player){if(player.connection.hasChannel(TYPE))PacketDistributor.sendToPlayer(player,this);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
