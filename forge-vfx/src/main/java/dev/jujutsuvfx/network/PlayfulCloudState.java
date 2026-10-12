package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Server-to-client cosmetics only; clients never submit damage, targets or charge power. */
public record PlayfulCloudState(int actor,int event,int step,Vec3 point,Vec3 direction,float power) implements CustomPacketPayload {
    public static final int SWING=0,HIT=1,HEAVY=2;
    public static final Type<PlayfulCloudState> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"playful_cloud_v1"));
    public static final StreamCodec<FriendlyByteBuf,PlayfulCloudState> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.actor);b.writeByte(p.event);b.writeByte(p.step);
        b.writeDouble(p.point.x);b.writeDouble(p.point.y);b.writeDouble(p.point.z);
        b.writeFloat((float)p.direction.x);b.writeFloat((float)p.direction.y);b.writeFloat((float)p.direction.z);b.writeFloat(p.power);
    },b->new PlayfulCloudState(b.readVarInt(),b.readUnsignedByte(),b.readUnsignedByte(),
            new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readFloat(),b.readFloat(),b.readFloat()),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
