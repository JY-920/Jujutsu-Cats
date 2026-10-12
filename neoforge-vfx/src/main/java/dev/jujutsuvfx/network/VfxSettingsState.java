package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VfxSettingsState(boolean destruction, boolean editable, boolean physicsAvailable, boolean cleavePhysics, boolean cinematic, boolean unlimitedDestruction) implements CustomPacketPayload {
    public static final Type<VfxSettingsState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "settings_state_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VfxSettingsState> CODEC = StreamCodec.of(
            (b,p) -> { b.writeBoolean(p.destruction); b.writeBoolean(p.editable); b.writeBoolean(p.physicsAvailable); b.writeBoolean(p.cleavePhysics); b.writeBoolean(p.cinematic); b.writeBoolean(p.unlimitedDestruction); },
            b -> new VfxSettingsState(b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
