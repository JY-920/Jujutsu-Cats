package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.core.SlashBlade;
import dev.jujutsuvfx.network.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Observer presentation only. Source handles remain scoped to caster + technique + session. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class PlayerSpellSync {
    public static final int RANGE=384;
    private record Key(UUID owner,int kind,int session) {}
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    private static final class State {
        int serial;
        final Map<Key,Entry> casts=new HashMap<>();
    }
    private static final class Entry {
        final int id;final ServerPlayer owner;final ServerLevel level;
        CatSpellState packet;long updated;
        final Set<UUID> watchers=new HashSet<>();
        final Map<Integer,SlashBlade> scars=new LinkedHashMap<>();
        ShrineFocus focus;long focusedAt;
        Entry(int id,ServerPlayer owner){this.id=id;this.owner=owner;level=owner.serverLevel();}
        CatSpellState snapshot(long now){return packet.advance((int)Math.max(0,now-updated));}
    }
    private PlayerSpellSync() {}
    /** One namespace for NPC and player observers; never reuse a live or completed cast ID. */
    static int nextId(MinecraftServer server){return --STATES.computeIfAbsent(server,s->new State()).serial;}
    public static int id(ServerPlayer owner,int kind,int session){
        State state=STATES.computeIfAbsent(owner.getServer(),s->new State());Key key=new Key(owner.getUUID(),kind,session);
        Entry entry=state.casts.get(key);
        if(entry==null){entry=new Entry(nextId(owner.getServer()),owner);state.casts.put(key,entry);}
        return entry.id;
    }
    public static void publish(ServerPlayer owner,int source,int kind,boolean large,int phase,int age,int stage,
                               int remaining,float radius,Vec3 point,Vec3 direction,Vec3 origin,int peer,
                               int prepare,InteractionHand hand,float influenceRadius){
        int id=id(owner,kind,source);Entry entry=find(owner,kind,source);
        boolean changed=entry.packet==null||entry.packet.phase()!=phase||entry.packet.large()!=large||entry.packet.peer()!=peer;
        entry.packet=new CatSpellState(id,owner.getId(),owner.getUUID(),kind,large,phase,Math.max(0,age),Math.max(0,stage),
                Math.max(0,remaining),radius,point,direction,origin,peer,true,source,prepare,hand==InteractionHand.OFF_HAND?1:0,entry.level.dimension().location(),influenceRadius);
        entry.updated=owner.getServer().getTickCount();
        if(changed)broadcast(entry,entry.updated);
    }
    private static Entry find(ServerPlayer owner,int kind,int session){
        State state=STATES.get(owner.getServer());return state==null?null:state.casts.get(new Key(owner.getUUID(),kind,session));
    }
    public static void end(ServerPlayer owner,int kind,int source){
        State state=STATES.get(owner.getServer());if(state==null)return;
        Entry entry=state.casts.remove(new Key(owner.getUUID(),kind,source));if(entry!=null)close(entry);
    }
    private static boolean visible(Entry e,ServerPlayer observer){
        return observer.level()==e.level&&observer.connection.hasChannel(CatSpellState.TYPE)
                &&(observer!=e.owner||CatSpell.domain(e.packet.kind()))
                &&(observer==e.owner||Math.min(observer.distanceToSqr(e.packet.point()),
                observer.distanceToSqr(e.packet.origin()))<=RANGE*RANGE);
    }
    private static void close(Entry e){
        if(e.packet==null)return;
        for(ServerPlayer p:e.level.players())if(e.watchers.contains(p.getUUID()))e.packet.ended().send(p);
        e.watchers.clear();e.scars.clear();
    }
    private static void send(Entry e,ServerPlayer observer,long now){
        e.snapshot(now).send(observer);
        if(e.watchers.add(observer.getUUID())&&observer!=e.owner){
            if(e.focus!=null){ShrineFocus f=e.focus;int elapsed=(int)Math.max(0,now-e.focusedAt);
                send(observer,new ShrineFocus(e.id,f.owner(),f.generation(),f.active(),f.target(),f.targetUuid(),f.center(),f.radius(),f.age()+elapsed,Math.max(0,f.remaining()-elapsed)));}
            List<SlashBlade> scars=List.copyOf(e.scars.values());
            for(int i=0;i<scars.size();i+=64)send(observer,new BladeBatch(e.id,e.level.getGameTime(),scars.subList(i,Math.min(i+64,scars.size()))));
        }
    }
    private static void broadcast(Entry e,long now){
        if(e.packet==null)return;
        for(ServerPlayer observer:e.level.players()){
            if(visible(e,observer))send(e,observer,now);
            else if(e.watchers.remove(observer.getUUID()))e.packet.ended().send(observer);
        }
        e.watchers.removeIf(uuid->e.level.getServer().getPlayerList().getPlayer(uuid)==null);
    }
    /** Remap the owner-only blade IDs before delivery; another player's session 1 is unrelated. */
    static void domainEffect(ServerPlayer owner,int source,int kind,CustomPacketPayload packet){
        Entry e=find(owner,kind,source);if(e==null||e.packet==null)return;
        CustomPacketPayload outgoing;
        if(packet instanceof BladeBatch batch){
            outgoing=new BladeBatch(e.id,batch.gameTick(),batch.blades());
        }else if(packet instanceof BladeImpact hit)outgoing=new BladeImpact(e.id,hit.point(),hit.direction());
        else if(packet instanceof ShrineFocus f){
            outgoing=new ShrineFocus(e.id,f.owner(),f.generation(),f.active(),f.target(),f.targetUuid(),f.center(),f.radius(),f.age(),f.remaining());
            if(e.focus==null||e.focus.generation()!=f.generation()||e.focus.active()!=f.active())e.scars.clear();
        }else return;
        long now=owner.getServer().getTickCount();
        for(ServerPlayer observer:e.level.players())if(observer!=owner&&visible(e,observer)){
            if(!e.watchers.contains(observer.getUUID()))send(e,observer,now);
            send(observer,outgoing);
        }
        if(packet instanceof ShrineFocus f){e.focus=f;e.focusedAt=now;}
        if(packet instanceof BladeBatch batch)for(SlashBlade b:batch.blades())if(b.persistent())
            e.scars.put(b.id(),new SlashBlade(b.id(),b.center(),b.right(),b.up(),b.length(),b.lifetime(),b.seed(),-20,true));
    }
    private static void send(ServerPlayer p,CustomPacketPayload packet){
        if(p.connection.hasChannel(packet.type()))PacketDistributor.sendToPlayer(p,packet);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        State state=STATES.get(event.getServer());if(state==null)return;long now=event.getServer().getTickCount();
        for(var it=state.casts.values().iterator();it.hasNext();){Entry e=it.next();
            if(e.packet==null)continue;
            boolean ending=e.packet.phase()==CatSpell.IMPACT;
            if(!e.owner.isAlive()||e.owner.isRemoved()||e.owner.isSpectator()||e.owner.level()!=e.level
                    ||event.getServer().getPlayerList().getPlayer(e.owner.getUUID())!=e.owner
                    ||ending&&now-e.updated>=e.packet.remaining()||!ending&&now-e.updated>100){close(e);it.remove();continue;}
            if(now%2==0)broadcast(e,now);
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
        if(!(event.getEntity() instanceof ServerPlayer observer)||!(event.getTarget() instanceof ServerPlayer caster))return;
        State state=STATES.get(observer.getServer());if(state==null)return;
        for(Entry e:state.casts.values())if(e.packet!=null&&e.owner==caster&&visible(e,observer))send(e,observer,observer.getServer().getTickCount());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){STATES.remove(event.getServer());}
}
