package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-confirmed Blue/Red punch; deliberately separate from the cursed-energy/Black Flash channel. */
public record TechniqueFistImpact(FistImpact impact, boolean red) implements CustomPacketPayload {
    public static final Type<TechniqueFistImpact> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "technique_fist_impact_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechniqueFistImpact> CODEC = StreamCodec.of((b, p) -> {
        FistImpact.CODEC.encode(b, p.impact); b.writeBoolean(p.red);
    }, b -> new TechniqueFistImpact(FistImpact.CODEC.decode(b), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
