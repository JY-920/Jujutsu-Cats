package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.DomainDuration;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed-size payload; values are validated on the server before saving. */
public record DomainSettingsState(int requestId, int result, boolean editable, int voidSeconds, int shrineSeconds) implements CustomPacketPayload {
    public static final int SNAPSHOT = 0, SAVED = 1, DENIED = 2, INVALID = 3, FAILED = 4;
    public static final Type<DomainSettingsState> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "domain_settings_state_v1"));
    public static final StreamCodec<FriendlyByteBuf, DomainSettingsState> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.requestId); b.writeVarInt(p.result); b.writeBoolean(p.editable); b.writeVarInt(p.voidSeconds); b.writeVarInt(p.shrineSeconds); },
            b -> new DomainSettingsState(b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt()));
    public boolean validValues() { return DomainDuration.valid(voidSeconds) && DomainDuration.valid(shrineSeconds); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
