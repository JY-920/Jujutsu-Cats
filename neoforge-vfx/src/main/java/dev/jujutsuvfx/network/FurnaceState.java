package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record FurnaceState(int session, int phase, long tick, Vec3 point, Vec3 direction) implements CustomPacketPayload {
    public static final int ACCEPTED = 0, LAUNCHED = 1, IMPACT = 2, CANCELED = 3;
    public static final Type<FurnaceState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "furnace_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FurnaceState> CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.session); b.writeVarInt(p.phase); b.writeLong(p.tick);
        write(b, p.point); write(b, p.direction);
    }, b -> new FurnaceState(b.readVarInt(), b.readVarInt(), b.readLong(), read(b), read(b)));
    private static void write(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 read(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
