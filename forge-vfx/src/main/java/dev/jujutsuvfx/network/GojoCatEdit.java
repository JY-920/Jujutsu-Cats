package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** The server-issued session selects the entity; the client cannot submit arbitrary entity edits. */
public record GojoCatEdit(UUID session, int action, int value) implements CustomPacketPayload {
    public static final int APPEARANCE = 0, ARM = 1, BLUE = 2, RED = 3, BEHAVIOR = 4, CLOSE = 5, QUERY = 6;
    public static final int CURSED = 7, BLACK_FLASH = 8, AUTOMATIC = 9;
    public static final Type<GojoCatEdit> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "gojo_cat_edit_v2"));
    public static final StreamCodec<FriendlyByteBuf, GojoCatEdit> CODEC = StreamCodec.of((b,p)->{
        b.writeUUID(p.session); b.writeVarInt(p.action); b.writeVarInt(p.value);
    },b->new GojoCatEdit(b.readUUID(), b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
