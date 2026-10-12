package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.platform.RegisterPayloadHandlersEvent;

/** One server-confirmed cut, shared by observers; no client-supplied damage or block lists. */
public record SukunaCut(Vec3 center, Vec3 forward, Vec3 stroke, float length, int kind, boolean impact,
                        int styleSeed, boolean cross)
        implements CustomPacketPayload {
    public static final Type<SukunaCut> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "sukuna_cut_v2"));
    public static final StreamCodec<FriendlyByteBuf, SukunaCut> CODEC = StreamCodec.of((b, p) -> {
        write(b, p.center); write(b, p.forward); write(b, p.stroke);
        b.writeFloat(p.length); b.writeVarInt(p.kind); b.writeBoolean(p.impact);
        b.writeInt(p.styleSeed); b.writeBoolean(p.cross);
    }, b -> new SukunaCut(read(b), read(b), read(b), b.readFloat(), b.readVarInt(), b.readBoolean(),
            b.readInt(), b.readBoolean()));

    public SukunaCut(Vec3 center, Vec3 forward, Vec3 stroke, float length, int kind, boolean impact) {
        this(center, forward, stroke, length, kind, impact, 0, false);
    }

    private static void write(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
    }
    private static Vec3 read(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.client.ClientSukunaSlashes.receive(packet)));
    }
}
