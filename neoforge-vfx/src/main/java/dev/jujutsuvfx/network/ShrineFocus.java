package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** A single authoritative concentration timeline; signed age includes a short scheduled lead-in. */
public record ShrineFocus(int session,UUID owner,int generation,boolean active,int target,UUID targetUuid,
                          Vec3 center,float radius,int age,int remaining) implements CustomPacketPayload {
    public static final Type<ShrineFocus> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"shrine_focus_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ShrineFocus> CODEC=StreamCodec.of((b,p)->{
        b.writeInt(p.session);b.writeUUID(p.owner);b.writeVarInt(p.generation);b.writeBoolean(p.active);
        b.writeVarInt(p.target);b.writeUUID(p.targetUuid);b.writeDouble(p.center.x);b.writeDouble(p.center.y);b.writeDouble(p.center.z);
        b.writeFloat(p.radius);b.writeVarInt(p.age);b.writeVarInt(p.remaining);
    },b->new ShrineFocus(b.readInt(),b.readUUID(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readUUID(),
            new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt(),b.readVarInt()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
