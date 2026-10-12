package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.platform.PacketDistributor;
import java.util.UUID;

/** Server-to-observer snapshot. It never acts as a player's cast or input acknowledgement. */
public record CatSpellState(int session, int actor, UUID owner, int kind, boolean large, int phase,
                            int age, int stageAge, int remaining, float radius,
                            Vec3 point, Vec3 direction, Vec3 origin, int peer,
                            boolean playerCast, int sourceSession, int prepareTicks, int hand,
                            ResourceLocation dimension, float influenceRadius) implements CustomPacketPayload {
    public static final Type<CatSpellState> TYPE = new Type<>(new ResourceLocation(JujutsuVfx.ID, "actor_spell_v2"));
    public static final StreamCodec<FriendlyByteBuf, CatSpellState> CODEC = StreamCodec.of((b,p)->{
        b.writeInt(p.session); b.writeVarInt(p.actor); b.writeUUID(p.owner); b.writeVarInt(p.kind); b.writeBoolean(p.large);
        b.writeVarInt(p.phase); b.writeVarInt(p.age); b.writeVarInt(p.stageAge); b.writeVarInt(p.remaining); b.writeFloat(p.radius);
        write(b,p.point); write(b,p.direction); write(b,p.origin); b.writeInt(p.peer);
        b.writeBoolean(p.playerCast); b.writeInt(p.sourceSession); b.writeVarInt(p.prepareTicks); b.writeByte(p.hand);
        b.writeBoolean(p.dimension!=null); if(p.dimension!=null)b.writeResourceLocation(p.dimension); b.writeFloat(p.influenceRadius);
    },b->new CatSpellState(b.readInt(),b.readVarInt(),b.readUUID(),b.readVarInt(),b.readBoolean(),b.readVarInt(),
            b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),read(b),read(b),read(b),b.readInt(),
            b.readBoolean(),b.readInt(),b.readVarInt(),b.readUnsignedByte(),b.readBoolean()?b.readResourceLocation():null,b.readFloat()));
    public CatSpellState(int session,int actor,UUID owner,int kind,boolean large,int phase,int age,int stageAge,
                         int remaining,float radius,Vec3 point,Vec3 direction,Vec3 origin,int peer) {
        this(session,actor,owner,kind,large,phase,age,stageAge,remaining,radius,point,direction,origin,peer,
                false,session,dev.jujutsuvfx.core.CatSpell.prepareTicks(kind,large),0,null,radius);
    }
    public CatSpellState advance(int ticks) {
        return new CatSpellState(session,actor,owner,kind,large,phase,age+ticks,stageAge+ticks,
                Math.max(0,remaining-ticks),radius,point,direction,origin,peer,playerCast,sourceSession,prepareTicks,hand,dimension,influenceRadius);
    }
    public CatSpellState ended() {
        return new CatSpellState(session,actor,owner,kind,large,dev.jujutsuvfx.core.CatSpell.END,age,stageAge,
                0,radius,point,direction,origin,peer,playerCast,sourceSession,prepareTicks,hand,dimension,influenceRadius);
    }
    private static void write(FriendlyByteBuf b, Vec3 p) { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); }
    private static Vec3 read(FriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
    public void send(ServerPlayer player) { if(dev.jujutsuvfx.platform.PacketDistributor.hasChannel(player.connection,TYPE)) PacketDistributor.sendToPlayer(player,this); }
    public void broadcast(ServerLevel level) {
        for(var player:level.players()) if(Math.min(player.distanceToSqr(point),player.distanceToSqr(origin)) < 160*160) send(player);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
