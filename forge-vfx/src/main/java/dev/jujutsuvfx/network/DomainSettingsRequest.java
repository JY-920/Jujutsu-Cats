package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.DomainDuration;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed-size payload; values are validated on the server before saving. */
public record DomainSettingsRequest(int requestId, boolean update, int voidSeconds, int shrineSeconds, int yutaSeconds) implements CustomPacketPayload {
    public static final Type<DomainSettingsRequest> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "domain_settings_request_v2"));
    public static final StreamCodec<FriendlyByteBuf, DomainSettingsRequest> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.requestId); b.writeBoolean(p.update); b.writeVarInt(p.voidSeconds); b.writeVarInt(p.shrineSeconds); b.writeVarInt(p.yutaSeconds); },
            b -> new DomainSettingsRequest(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
    public boolean validValues() { return DomainDuration.valid(voidSeconds) && DomainDuration.valid(shrineSeconds) && DomainDuration.valid(yutaSeconds); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
