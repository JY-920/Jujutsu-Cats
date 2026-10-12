package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-confirmed Blue/Red punch; deliberately separate from the cursed-energy/Black Flash channel. */
public record TechniqueFistImpact(FistImpact impact, boolean red) implements CustomPacketPayload {
    public static final Type<TechniqueFistImpact> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "technique_fist_impact_v1"));
    public static final StreamCodec<FriendlyByteBuf, TechniqueFistImpact> CODEC = StreamCodec.of((b, p) -> {
        FistImpact.CODEC.encode(b, p.impact); b.writeBoolean(p.red);
    }, b -> new TechniqueFistImpact(FistImpact.CODEC.decode(b), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
