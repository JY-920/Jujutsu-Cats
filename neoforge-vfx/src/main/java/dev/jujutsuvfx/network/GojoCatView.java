package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record GojoCatView(int entityId, UUID entityUuid, UUID session, boolean open,
                          int appearance, boolean broken, int blue, int red, int cursed, int blackFlash,
                          boolean automatic, int behavior) implements CustomPacketPayload {
    public static final Type<GojoCatView> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "gojo_cat_view_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GojoCatView> CODEC = StreamCodec.of((b,p)->{
        b.writeVarInt(p.entityId); b.writeUUID(p.entityUuid); b.writeUUID(p.session); b.writeBoolean(p.open);
        b.writeVarInt(p.appearance); b.writeBoolean(p.broken); b.writeVarInt(p.blue); b.writeVarInt(p.red);
        b.writeVarInt(p.cursed); b.writeVarInt(p.blackFlash); b.writeBoolean(p.automatic); b.writeVarInt(p.behavior);
    },b->new GojoCatView(b.readVarInt(), b.readUUID(), b.readUUID(), b.readBoolean(),
            b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt()));
    public static GojoCatView of(SorcererCat cat, UUID session, boolean open) {
        return new GojoCatView(cat.getId(), cat.getUUID(), session, open, cat.appearance(), cat.brokenArm(), cat.blueHands(), cat.redHands(),
                cat.cursedHands(), cat.blackFlashHands(), cat.automaticEnergy(), cat.behavior());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
