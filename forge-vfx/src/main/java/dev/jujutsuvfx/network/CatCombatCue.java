package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.platform.PacketDistributor;

/** Cosmetic, server-authored movement/contact cue. It cannot request movement or damage. */
public record CatCombatCue(int actor, int other, int effect, Vec3 from, Vec3 to, int first, int second, int seed)
        implements CustomPacketPayload {
    public static final int BLINK = 0, DASH = 1, CLASH = 2, KICK = 3, GUARD = 4, BARRAGE = 5, DIVE = 6, IMPACT = 7, INFINITY = 8, BLADE = 9, BLAST_WARNING = 10, DOMAIN_RESULT = 11;
    public static final Type<CatCombatCue> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "cat_combat_cue_v1"));
    public static final StreamCodec<FriendlyByteBuf, CatCombatCue> CODEC = StreamCodec.of((b,p)->{
        b.writeVarInt(p.actor); b.writeVarInt(p.other); b.writeVarInt(p.effect);
        write(b,p.from); write(b,p.to); b.writeVarInt(p.first); b.writeVarInt(p.second); b.writeInt(p.seed);
    },b->new CatCombatCue(b.readVarInt(),b.readVarInt(),b.readVarInt(),read(b),read(b),b.readVarInt(),b.readVarInt(),b.readInt()));
    private static void write(FriendlyByteBuf b, Vec3 p) { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); }
    private static Vec3 read(FriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
    public void broadcast(ServerLevel level) {
        for (var player : level.players()) if ((dev.jujutsuvfx.server.CinematicAudio.visualRange(player,from,160)
                ||dev.jujutsuvfx.server.CinematicAudio.visualRange(player,to,160))
                && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(player.connection,TYPE)) PacketDistributor.sendToPlayer(player,this);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
