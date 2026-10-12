package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.platform.RegisterPayloadHandlersEvent;

/** Cosmetic notification emitted only after the server accepts a real melee hit. */
public record FistImpact(int attackerId, int targetId, Vec3 point, Vec3 direction, float strength)
        implements CustomPacketPayload {
    public static final Type<FistImpact> TYPE = new Type<>(new ResourceLocation(
            JujutsuVfx.ID, "fist_impact_v1"));
    public static final StreamCodec<FriendlyByteBuf, FistImpact> CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.attackerId); b.writeVarInt(p.targetId);
        write(b, p.point); write(b, p.direction); b.writeFloat(p.strength);
    }, b -> new FistImpact(b.readVarInt(), b.readVarInt(), read(b), read(b), b.readFloat()));

    private static void write(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
    }

    private static Vec3 read(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1011").playToServer(MartialInput.TYPE,MartialInput.CODEC,
                (packet,context)->context.enqueueWork(()->dev.jujutsuvfx.server.MartialCombat.input(packet,context.player())));
        event.registrar("1011").playToClient(MartialState.TYPE,MartialState.CODEC,
                (packet,context)->context.enqueueWork(()->dev.jujutsuvfx.client.MartialClient.receive(packet)));
        event.registrar("1011").playToClient(MartialHit.TYPE,MartialHit.CODEC,
                (packet,context)->context.enqueueWork(()->dev.jujutsuvfx.client.MartialClient.receive(packet)));
        event.registrar("1").optional().playToClient(PlayfulCloudState.TYPE,PlayfulCloudState.CODEC,
                (packet,context)->context.enqueueWork(()->dev.jujutsuvfx.client.PlayfulCloudHands.receive(packet)));
        event.registrar("1").optional().playToClient(UnlimitedPurple.TYPE, UnlimitedPurple.CODEC,
                (packet, context) -> context.enqueueWork(() -> dev.jujutsuvfx.client.UnlimitedPurpleEffects.receive(packet)));
        event.registrar("1").optional().playToClient(SkillGesture.TYPE,SkillGesture.CODEC,(packet,context)->context.enqueueWork(
                ()->dev.jujutsuvfx.client.TechniqueHands.receive(packet)));
        event.registrar("1").optional().playToClient(CatSpellState.TYPE,CatSpellState.CODEC,(packet,context)->context.enqueueWork(
                ()->dev.jujutsuvfx.client.CatSpells.receive(packet)));
        event.registrar("1").optional().playToClient(CatCombatCue.TYPE, CatCombatCue.CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.client.CatCombatEffects.receive(packet)));
        event.registrar("1").optional().playToClient(TechniqueFistImpact.TYPE, TechniqueFistImpact.CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.client.CatTechniqueEffects.receive(packet)));
        event.registrar("1").optional().playToClient(TYPE, CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.client.ClientFistEffects.receive(packet)));
        event.registrar("1").optional().playToClient(FistStrike.TYPE, FistStrike.CODEC, (packet, context) -> context.enqueueWork(
                () -> dev.jujutsuvfx.client.ClientFistEffects.receive(packet.impact(), packet.empowered())));
    }
}
