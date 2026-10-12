package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Requests carry bounded configuration and an optional look sample, never an origin or target. */
public record TechniqueControl(int action, int session, int kind, float radius, int ticks,
                               boolean hasAim, float yaw, float pitch)
        implements CustomPacketPayload {
    public static final int CAST = 0, CANCEL = 1;
    public static final int GUIDE_MAIN = 2, GUIDE_OFF = 3, PARK = 4;
    public static final int CHARGE_MAIN = 5, CHARGE_OFF = 6, RELEASE = 7;
    public static final int COMMIT_SMALL = 8, COMMIT_LARGE = 9;
    public static final int RED_FIRE = 10;
    public static final Type<TechniqueControl> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            JujutsuVfx.ID, "technique_control_v8"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechniqueControl> CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.action); b.writeVarInt(p.session); b.writeVarInt(p.kind);
        b.writeFloat(p.radius); b.writeVarInt(p.ticks);
        b.writeBoolean(p.hasAim);
        if (p.hasAim) { b.writeFloat(p.yaw); b.writeFloat(p.pitch); }
    }, b -> {
        int action = b.readVarInt(), session = b.readVarInt(), kind = b.readVarInt();
        float radius = b.readFloat(); int ticks = b.readVarInt();
        return b.readBoolean() ? new TechniqueControl(action, session, kind, radius, ticks, true, b.readFloat(), b.readFloat())
                : new TechniqueControl(action, session, kind, radius, ticks);
    });

    public TechniqueControl(int action, int session, int kind, float radius, int ticks) {
        this(action, session, kind, radius, ticks, false, 0, 0);
    }
    public TechniqueControl withAim(float yaw, float pitch) {
        return new TechniqueControl(action, session, kind, radius, ticks, true, Mth.wrapDegrees(yaw), pitch);
    }
    public boolean validAim() {
        return hasAim && Float.isFinite(yaw) && Float.isFinite(pitch)
                && yaw >= -180 && yaw <= 180 && pitch >= -90 && pitch <= 90;
    }

    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("0984").optional();
        registrar.executesOn(HandlerThread.NETWORK).playToServer(TYPE, CODEC, (packet, context) -> {
            var player = context.player();
            // Timestamp receipt before a busy server queues the action. Do not count queued work as
            // extra time holding the item; all world mutations still run on the server thread.
            long received = dev.jujutsuvfx.server.LimitlessSkills.purpleInputTime(player);
            context.enqueueWork(() -> dev.jujutsuvfx.server.LimitlessSkills.handle(packet, player, received));
        });
        registrar.playToClient(TechniqueState.TYPE, TechniqueState.CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.client.ClientEvents.techniqueState(packet)));
    }
}
