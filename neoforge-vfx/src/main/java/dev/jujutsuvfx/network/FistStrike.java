package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Optional richer impact notification; legacy observers can still receive the original blue impact. */
public record FistStrike(FistImpact impact, boolean empowered) implements CustomPacketPayload {
    public static final Type<FistStrike> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            JujutsuVfx.ID, "fist_strike_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FistStrike> CODEC = StreamCodec.of((b, p) -> {
        FistImpact.CODEC.encode(b, p.impact); b.writeBoolean(p.empowered);
    }, b -> new FistStrike(FistImpact.CODEC.decode(b), b.readBoolean()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
