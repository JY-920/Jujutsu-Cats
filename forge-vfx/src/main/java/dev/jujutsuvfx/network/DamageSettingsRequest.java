package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed-size wire format: clients cannot request allocation from an untrusted array length. */
public record DamageSettingsRequest(int requestId, boolean update, int[] percents) implements CustomPacketPayload {
    public static final int COUNT = 10;
    public static final Type<DamageSettingsRequest> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "damage_settings_request_v1"));
    public static final StreamCodec<FriendlyByteBuf, DamageSettingsRequest> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.requestId); b.writeBoolean(p.update); for (int value : p.percents) b.writeVarInt(value); },
            b -> new DamageSettingsRequest(b.readVarInt(), b.readBoolean(), readValues(b)));
    public DamageSettingsRequest {
        if (percents == null || percents.length != COUNT) throw new IllegalArgumentException("Expected ten technique percentages");
        percents = percents.clone();
    }
    @Override public int[] percents() { return percents.clone(); }
    public static boolean validValues(int[] values) {
        if (values == null || values.length != COUNT) return false;
        for (int value : values) if (value < 0 || value > 1000) return false;
        return true;
    }
    static int[] readValues(FriendlyByteBuf buffer) {
        int[] values = new int[COUNT];
        for (int i = 0; i < COUNT; i++) values[i] = buffer.readVarInt();
        return values;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
