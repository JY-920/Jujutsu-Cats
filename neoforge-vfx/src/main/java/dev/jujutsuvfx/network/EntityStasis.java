package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.FrozenPose;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Authoritative entity suspension, shared with observers independently of local domain visuals. */
public record EntityStasis(int entityId,UUID uuid,boolean frozen,Vec3 position,Vec3 velocity,
                           float yaw,float pitch,float fallDistance) implements CustomPacketPayload {
    public static final Type<EntityStasis> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"entity_stasis_v7"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EntityStasis> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.entityId);b.writeUUID(p.uuid);b.writeBoolean(p.frozen);
        write(b,p.position);write(b,p.velocity);b.writeFloat(p.yaw);b.writeFloat(p.pitch);b.writeFloat(p.fallDistance);
    },b->new EntityStasis(b.readVarInt(),b.readUUID(),b.readBoolean(),read(b),read(b),b.readFloat(),b.readFloat(),b.readFloat()));
    private static void write(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3 read(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public static EntityStasis of(Entity entity,FrozenPose pose,boolean frozen){
        return new EntityStasis(entity.getId(),entity.getUUID(),frozen,pose.position,pose.velocity,pose.yaw,pose.pitch,pose.fallDistance);
    }
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
