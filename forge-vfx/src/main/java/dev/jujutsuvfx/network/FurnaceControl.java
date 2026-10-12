package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.jujutsuvfx.platform.RegisterPayloadHandlersEvent;

/** The server derives all origins, aim directions, hit points and damage from its own world. */
public record FurnaceControl(int action, int session, float radius) implements CustomPacketPayload {
    public static final int CAST = 0, CANCEL = 1;
    public static final Type<FurnaceControl> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "furnace_control_v1"));
    public static final StreamCodec<FriendlyByteBuf, FurnaceControl> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.action); b.writeVarInt(p.session); b.writeFloat(p.radius); },
            b -> new FurnaceControl(b.readVarInt(), b.readVarInt(), b.readFloat()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(TYPE, CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.server.FurnaceSkill.handle(packet, context.player())));
        registrar.playToClient(FurnaceState.TYPE, FurnaceState.CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.client.ClientEvents.furnaceState(packet)));
    }
}
