package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record TechniqueState(int session, int kind, int phase, long tick, int remainingTicks,
                             Vec3 point, Vec3 direction, float radius, int chargedTicks) implements CustomPacketPayload {
    public static final int ACCEPTED = 0, LAUNCHED = 1, IMPACT = 2, CLOSING = 3, CANCELED = 4;
    public static final int BLUE_MOVING = 5, BLUE_PARKED = 6;
    public static final int SHOT_MOVING = 7;
    public static final int PURPLE_SMALL = 8, PURPLE_LARGE = 9;
    public static final int RED_BEAM = 10;
    public static final Type<TechniqueState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            JujutsuVfx.ID, "technique_state_v8"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechniqueState> CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.session); b.writeVarInt(p.kind); b.writeVarInt(p.phase); b.writeLong(p.tick);
        b.writeVarInt(p.remainingTicks); write(b, p.point); write(b, p.direction);
        b.writeFloat(p.radius); b.writeVarInt(p.chargedTicks);
    }, b -> new TechniqueState(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong(),
            b.readVarInt(), read(b), read(b), b.readFloat(), b.readVarInt()));
    private static void write(RegistryFriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
    }
    private static Vec3 read(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
