package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** The server-issued session selects the entity; the client cannot submit arbitrary entity edits. */
public record YutaCatEdit(UUID session, int action, int value) implements CustomPacketPayload {
    public static final int DANCE = 0, DRAWN = 1, ENERGY = 2, BEHAVIOR = 3, CLOSE = 4, QUERY = 5;
    public static final Type<YutaCatEdit> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "yuta_cat_edit"));
    public static final StreamCodec<FriendlyByteBuf, YutaCatEdit> CODEC = StreamCodec.of((b,p)->{
        b.writeUUID(p.session); b.writeVarInt(p.action); b.writeVarInt(p.value);
    },b->new YutaCatEdit(b.readUUID(), b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
