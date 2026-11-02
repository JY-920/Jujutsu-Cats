package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record YutaCatView(int entityId, UUID entityUuid, UUID session, boolean open,
                          int dancing, boolean drawn, int energy, int behavior) implements CustomPacketPayload {
    public static final Type<YutaCatView> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "yuta_cat_view_v2"));
    public static final StreamCodec<FriendlyByteBuf, YutaCatView> CODEC = StreamCodec.of((b,p)->{
        b.writeVarInt(p.entityId); b.writeUUID(p.entityUuid); b.writeUUID(p.session); b.writeBoolean(p.open);
        b.writeVarInt(p.dancing); b.writeBoolean(p.drawn); b.writeVarInt(p.energy); b.writeVarInt(p.behavior);
    },b->new YutaCatView(b.readVarInt(), b.readUUID(), b.readUUID(), b.readBoolean(),
            b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt()));
    public static YutaCatView of(YutaCat cat, UUID session, boolean open) {
        return new YutaCatView(cat.getId(), cat.getUUID(), session, open, cat.danceStyle(), cat.bladeDrawn(), cat.bladeMode(), cat.behavior());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
