package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.DomainDuration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed-size payload; values are validated on the server before saving. */
public record DomainSettingsRequest(int requestId, boolean update, int voidSeconds, int shrineSeconds) implements CustomPacketPayload {
    public static final Type<DomainSettingsRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "domain_settings_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DomainSettingsRequest> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.requestId); b.writeBoolean(p.update); b.writeVarInt(p.voidSeconds); b.writeVarInt(p.shrineSeconds); },
            b -> new DomainSettingsRequest(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt()));
    public boolean validValues() { return DomainDuration.valid(voidSeconds) && DomainDuration.valid(shrineSeconds); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
