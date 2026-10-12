package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.CameraAudioPolicy;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CameraAudio;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Complements vanilla sound routing for stationary observers whose cameras follow an actor. */
public final class CinematicAudio {
    private record Watch(UUID actor,ResourceKey<Level> dimension,Vec3 offset,long updated) {}
    private static final Map<MinecraftServer,Map<UUID,Watch>> WATCHERS=new WeakHashMap<>();
    private CinematicAudio() {}
    public static void handle(CameraAudio packet,Player sender){
        if(!(sender instanceof ServerPlayer player))return;
        MinecraftServer server=player.getServer();if(server==null)return;
        Map<UUID,Watch> watchers=WATCHERS.computeIfAbsent(server,key->new HashMap<>());
        if(packet.actor()<0){watchers.remove(player.getUUID());return;}
        Vec3 point=packet.point();long now=server.getTickCount();Watch previous=watchers.get(player.getUUID());
        if(!player.isAlive()||!finite(point)||!(player.serverLevel().getEntity(packet.actor()) instanceof SorcererCat cat)
                ||!cat.isAlive()||cat.isRemoved()||cat.distanceToSqr(player)>128*128||cat.getEyePosition().distanceToSqr(point)>16*16){
            watchers.remove(player.getUUID());return;
        }
        if(previous!=null&&previous.actor.equals(cat.getUUID())&&now-previous.updated<2)return;
        Vec3 offset=point.subtract(cat.getEyePosition());
        if(offset.length()>6)offset=offset.normalize().scale(6);
        watchers.put(player.getUUID(),new Watch(cat.getUUID(),player.level().dimension(),offset,now));
    }
    public static void broadcast(PlayerList list,Player excluded,double x,double y,double z,double range,ResourceKey<Level> dimension,Packet<?> packet){
        if(!(packet instanceof ClientboundSoundPacket)&&!(packet instanceof ClientboundSoundEntityPacket))return;
        MinecraftServer server=list.getServer();Map<UUID,Watch> watchers=WATCHERS.get(server);
        if(watchers==null||watchers.isEmpty())return;
        long now=server.getTickCount();watchers.values().removeIf(w->now-w.updated>40||now<w.updated);
        Vec3 source=new Vec3(x,y,z);
        for(ServerPlayer player:list.getPlayers()){
            Watch watch=watchers.get(player.getUUID());
            if(watch==null||player==excluded)continue;
            if(!player.isAlive()||!player.level().dimension().equals(watch.dimension)){
                watchers.remove(player.getUUID());continue;
            }
            if(!dimension.equals(watch.dimension))continue;
            if(!(player.serverLevel().getEntity(watch.actor) instanceof SorcererCat cat)||!cat.isAlive()||cat.distanceToSqr(player)>128*128){
                watchers.remove(player.getUUID());continue;
            }
            Vec3 microphone=cat.getEyePosition().add(watch.offset);
            if(CameraAudioPolicy.needsRelay(player.distanceToSqr(source),microphone.distanceToSqr(source),range))player.connection.send(packet);
        }
    }
    /** Cosmetic contacts follow the validated camera lease, not just the observer's stationary body. */
    public static boolean visualRange(ServerPlayer player,Vec3 point,double range){
        double limit=range*range;if(player.distanceToSqr(point)<=limit)return true;
        MinecraftServer server=player.getServer();if(server==null)return false;
        Map<UUID,Watch> watchers=WATCHERS.get(server);Watch watch=watchers==null?null:watchers.get(player.getUUID());
        long now=server.getTickCount();
        if(watch==null||now<watch.updated||now-watch.updated>40||!player.isAlive()
                ||!player.level().dimension().equals(watch.dimension))return false;
        if(!(player.serverLevel().getEntity(watch.actor) instanceof SorcererCat cat)||!cat.isAlive()
                ||cat.isRemoved()||cat.distanceToSqr(player)>128*128)return false;
        return cat.getEyePosition().add(watch.offset).distanceToSqr(point)<=limit;
    }
    private static boolean finite(Vec3 point){return Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z);}
}
