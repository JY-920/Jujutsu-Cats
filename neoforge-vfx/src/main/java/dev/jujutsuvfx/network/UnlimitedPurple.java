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

/** One absolute blast timeline. A negative age cancels this UUID without replaying it. */
public record UnlimitedPurple(UUID id, Vec3 center, float radius, int ageTicks, Source blue, Source red, boolean slowFusion) implements CustomPacketPayload {
    public UnlimitedPurple(UUID id,Vec3 center,float radius,int ageTicks,Source blue,Source red){
        this(id,center,radius,ageTicks,blue,red,false);
    }
    /** Actual pre-collision geometry, allowing every observer to continue the incoming shapes. */
    public record Source(UUID caster, int session, Vec3 point, Vec3 direction, Vec3 beamOrigin,
                         float influenceRadius, float coreRadius, float ageSeconds, boolean beam) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(caster); buffer.writeVarInt(session);
            vector(buffer, point); vector(buffer, direction); vector(buffer, beamOrigin);
            buffer.writeFloat(influenceRadius); buffer.writeFloat(coreRadius);
            buffer.writeFloat(ageSeconds); buffer.writeBoolean(beam);
        }
        private static Source read(RegistryFriendlyByteBuf buffer) {
            return new Source(buffer.readUUID(), buffer.readVarInt(), vector(buffer), vector(buffer), vector(buffer),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean());
        }
        private static void vector(RegistryFriendlyByteBuf buffer, Vec3 v) {
            buffer.writeDouble(v.x); buffer.writeDouble(v.y); buffer.writeDouble(v.z);
        }
        private static Vec3 vector(RegistryFriendlyByteBuf buffer) {
            return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
    }
    public static final Type<UnlimitedPurple> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "unlimited_purple_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlimitedPurple> CODEC = StreamCodec.of((buffer, packet) -> {
        buffer.writeUUID(packet.id);
        buffer.writeDouble(packet.center.x); buffer.writeDouble(packet.center.y); buffer.writeDouble(packet.center.z);
        buffer.writeFloat(packet.radius); buffer.writeVarInt(packet.ageTicks);
        packet.blue.write(buffer); packet.red.write(buffer); buffer.writeBoolean(packet.slowFusion);
    }, buffer -> new UnlimitedPurple(buffer.readUUID(),
            new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readFloat(), buffer.readVarInt(),
            Source.read(buffer), Source.read(buffer), buffer.readBoolean()));

    public void send(ServerPlayer observer) {
        if (observer.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(observer, this);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
