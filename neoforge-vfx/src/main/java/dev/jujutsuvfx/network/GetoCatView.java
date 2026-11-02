package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.GetoCat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record GetoCatView(int entityId,UUID entityUuid,UUID session,boolean open,int pose,int dancing) implements CustomPacketPayload {
 public static final Type<GetoCatView> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"geto_cat_view"));
 public static final StreamCodec<RegistryFriendlyByteBuf,GetoCatView> CODEC=StreamCodec.of((b,p)->{
  b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid);b.writeUUID(p.session);b.writeBoolean(p.open);b.writeVarInt(p.pose);b.writeVarInt(p.dancing);
 },b->new GetoCatView(b.readVarInt(),b.readUUID(),b.readUUID(),b.readBoolean(),b.readVarInt(),b.readVarInt()));
 public static GetoCatView of(GetoCat cat,UUID session,boolean open){return new GetoCatView(cat.getId(),cat.getUUID(),session,open,cat.debugPose(),cat.danceStyle());}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
