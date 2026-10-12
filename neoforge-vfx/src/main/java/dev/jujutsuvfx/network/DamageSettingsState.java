package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Acknowledgement includes authoritative values and fresh permissions, including failed saves. */
public record DamageSettingsState(int requestId, int result, boolean editable, int[] percents) implements CustomPacketPayload {
    public static final int SNAPSHOT = 0, SAVED = 1, DENIED = 2, INVALID = 3, FAILED = 4;
    public static final Type<DamageSettingsState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "damage_settings_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DamageSettingsState> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.requestId); b.writeVarInt(p.result); b.writeBoolean(p.editable); for (int value : p.percents) b.writeVarInt(value); },
            b -> new DamageSettingsState(b.readVarInt(), b.readVarInt(), b.readBoolean(), DamageSettingsRequest.readValues(b)));
    public DamageSettingsState {
        if (percents == null || percents.length != DamageSettingsRequest.COUNT) throw new IllegalArgumentException("Expected ten technique percentages");
        percents = percents.clone();
    }
    @Override public int[] percents() { return percents.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
