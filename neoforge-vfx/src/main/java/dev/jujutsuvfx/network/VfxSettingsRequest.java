package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** -1 queries; 0/1 disable/enable block destruction; 2/3 Cleave physics; 4/5 cinematic mode; 6/7 Unlimited Purple terrain. */
public record VfxSettingsRequest(int action) implements CustomPacketPayload {
    public static final Type<VfxSettingsRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "settings_request_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VfxSettingsRequest> CODEC = StreamCodec.of(
            (b,p) -> b.writeVarInt(p.action), b -> new VfxSettingsRequest(b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
