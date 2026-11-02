package dev.jujutsuvfx.network;
import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
public record YutaKatanaState(int actor,int step) implements CustomPacketPayload {
 public static final Type<YutaKatanaState> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"yuta_katana_v1"));
 public static final StreamCodec<FriendlyByteBuf,YutaKatanaState> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.actor);b.writeByte(p.step);},b->new YutaKatanaState(b.readVarInt(),b.readUnsignedByte()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
