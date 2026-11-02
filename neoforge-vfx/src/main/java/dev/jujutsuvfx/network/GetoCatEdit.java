package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record GetoCatEdit(UUID session,int action,int value) implements CustomPacketPayload {
 public static final int POSE=0,DANCE=1,CLOSE=2,QUERY=3;
 public static final Type<GetoCatEdit> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"geto_cat_edit"));
 public static final StreamCodec<RegistryFriendlyByteBuf,GetoCatEdit> CODEC=StreamCodec.of((b,p)->{
  b.writeUUID(p.session);b.writeVarInt(p.action);b.writeVarInt(p.value);
 },b->new GetoCatEdit(b.readUUID(),b.readVarInt(),b.readVarInt()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
