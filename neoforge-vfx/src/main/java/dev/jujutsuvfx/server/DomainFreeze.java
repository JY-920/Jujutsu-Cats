package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.network.CatSpellState;
import net.minecraft.server.level.ServerLevel;
import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.core.FrozenPose;
import dev.jujutsuvfx.network.DomainControl;
import dev.jujutsuvfx.network.DomainState;
import dev.jujutsuvfx.network.EntityStasis;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Independent cast-owned tick suspension, settlement and acknowledged release. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class DomainFreeze {
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    private static final int RELEASE_GRACE_TICKS=100;
    public static final int CAT_DUEL_CLASH_TICKS=100;
    private record Key(UUID owner,int id) {}
    private static final class Session {
        final Key key;final int kind;final Vec3 center;final float radius;final long born;
        int visualId;
        long settlesAt,nextScan,catClashSince=-1;final ResourceKey<Level> dimension;
        boolean settled;long releaseAt=Long.MAX_VALUE; UUID soleTarget;
        final ShrineBlades blades;
        final Set<UUID> fragmentRecipients=new HashSet<>();
        final Map<UUID,Long> adaptationContacts=new HashMap<>();
        SorcererCat cat; ServerPlayer player; Set<UUID> spared = Set.of(); Vec3 axis = new Vec3(0,0,1), visualOrigin; int peer;
        Session(Key key,int kind,Vec3 center,float radius,long now,int duration,ResourceKey<Level> dimension){
            this.key=key;this.kind=kind;this.center=center;this.radius=radius;born=now;settlesAt=now+duration;this.dimension=dimension;
            blades=kind==DomainVolume.UNLIMITED_VOID?null:new ShrineBlades(key.id,kind,center,radius,now);
        }
        float currentRadius(long now){return DomainVolume.freezeRadius(kind,radius,(Math.min(now,settlesAt)-born)/20f);}
    }
    private record BriefControl(long start,int ticks) {}
    private static final class Lock {
        final Entity entity;final FrozenPose pose;final ResourceKey<Level> dimension;final Set<Key> casts=new HashSet<>();
        long stunUntil = -1,damageClock;
        final java.util.EnumMap<CatAdaptation.Phenomenon,BriefControl> adaptiveStuns=new java.util.EnumMap<>(CatAdaptation.Phenomenon.class);
        long stunDeadline(){
            long end=stunUntil;
            for(var entry:adaptiveStuns.entrySet())end=Math.max(end,entry.getValue().start+
                    CatAdaptation.controlDuration(entity,entry.getKey(),entry.getValue().ticks));
            return end;
        }
        Lock(Entity entity){this.entity=entity;pose=new FrozenPose(entity);dimension=entity.level().dimension();damageClock=entity.getServer().getTickCount();}
        void advanceDamageClock(long now){
            int elapsed=(int)Math.clamp(now-damageClock,0,100000);damageClock=now;
            if(entity instanceof LivingEntity living&&elapsed>0)
                living.invulnerableTime=Math.max(0,living.invulnerableTime-elapsed);
        }
        void notifyClients(boolean frozen){
            EntityStasis packet=EntityStasis.of(entity,pose,frozen);
            if(entity.getServer()!=null)for(ServerPlayer observer:entity.getServer().getPlayerList().getPlayers())
                if(observer.level().dimension()==dimension&&observer.connection.hasChannel(EntityStasis.TYPE))
                    PacketDistributor.sendToPlayer(observer,packet);
        }
        void release(){
            if(entity.level().dimension()==dimension)pose.release(entity);
            notifyClients(false);
        }
    }
    private record DuelProtection(UUID peer,long until) {}
    private static final class State {
        final Map<UUID,DuelProtection> rehearsed=new HashMap<>();
        final Map<Key,Session> domains=new HashMap<>();final Map<UUID,Lock> locks=new HashMap<>();
        void release(Key key){
            Session removed=domains.remove(key);if(removed!=null&&removed.blades!=null)removed.blades.close();
            if(removed!=null&&removed.player!=null&&removed.kind!=DomainVolume.BARRAGE)TechniqueGestures.cancel(removed.player,key.id,
                    removed.kind==DomainVolume.UNLIMITED_VOID?dev.jujutsuvfx.network.SkillGesture.VOID:dev.jujutsuvfx.network.SkillGesture.SHRINE);
            if(removed!=null&&removed.cat!=null){
                ServerLevel level=removed.cat.getServer().getLevel(removed.dimension);
                if(level!=null){
                    catSnapshot(removed,CatSpell.END).broadcast(level);
                    if(removed.kind==DomainVolume.UNLIMITED_VOID&&removed.settled
                            &&level.getServer().getTickCount()>=removed.releaseAt){
                        for(LivingEntity enemy:level.getEntitiesOfClass(LivingEntity.class,new AABB(removed.center,removed.center).inflate(removed.radius),
                                removed.cat::canFight))
                            if(eligible(enemy,removed)&&DomainVolume.intersects(removed.center,removed.radius,enemy.getBoundingBox()))brieflyFreeze(enemy,100,CatAdaptation.Phenomenon.VOID);
                    }
                }
            }
            if(removed!=null&&removed.player!=null)PlayerSpellSync.end(removed.player,visualKind(removed),key.id);
            locks.values().forEach(lock->lock.casts.remove(key));
            for(var iterator=locks.values().iterator();iterator.hasNext();){
                Lock lock=iterator.next();if(lock.casts.isEmpty() && (lock.entity.getServer()==null
                        || lock.entity.getServer().getTickCount()>=lock.stunDeadline())){lock.release();iterator.remove();}
            }
        }
        void cancel(Key key,ServerPlayer player){
            Session session=domains.get(key);if(session==null)return;
            release(key);
            if(player!=null)PacketDistributor.sendToPlayer(player,new DomainState(key.id,DomainState.CANCELED));
        }
        void acquire(LivingEntity owner,Session session,long now){
            float radius=session.currentRadius(now);session.nextScan=now+(session.kind==DomainVolume.UNLIMITED_VOID?1:2);
            if(radius<=0)return;
            for(Entity target:((ServerLevel)owner.level()).getEntities((Entity)null,
                    new AABB(session.center,session.center).inflate(radius),entity->eligible(entity,session)
                            &&DomainVolume.intersects(session.center,radius,entity.getBoundingBox()))){
                capture(session,target);
            }
        }
        void capture(Session session,Entity target){
            if(target instanceof SorcererCat cat&&cat.finaleWard())return;
            if(!eligible(target,session))return;
            if(session.kind==DomainVolume.UNLIMITED_VOID&&CatAdaptation.enabled(target)){
                CatAdaptation.touch(target,CatAdaptation.Phenomenon.VOID);
                long now=target.getServer().getTickCount();
                session.adaptationContacts.putIfAbsent(target.getUUID(),now);
                if(adaptationDeadline(session,target)<=now)return;
            }
            if(target instanceof dev.jujutsuvfx.entity.SorcererCat cat)cat.combat().interruptRecovery();
            if(target instanceof ServerPlayer player&&session.kind==DomainVolume.UNLIMITED_VOID
                    &&(!locks.containsKey(target.getUUID())||!locks.get(target.getUUID()).casts.contains(session.key))){
                if(player.isPassenger())player.stopRiding();
                // One allocation per player per cast, even if their freeze lock is recreated.
                // A full inventory still consumes this allocation; later empty slots stay empty.
                if(session.fragmentRecipients.add(player.getUUID()))InformationFragments.fill(player);
            }
            Lock lock=locks.get(target.getUUID());
            if(lock!=null&&(lock.entity!=target||lock.dimension!=target.level().dimension())){
                lock.release();locks.remove(target.getUUID());lock=null;
            }
            if(lock==null){
                lock=new Lock(target);locks.put(target.getUUID(),lock);lock.casts.add(session.key);
                lock.pose.hold(target);target.hasImpulse=true;
                if(target instanceof ServerPlayer player)
                    player.connection.teleport(lock.pose.position.x,lock.pose.position.y,lock.pose.position.z,lock.pose.yaw,lock.pose.pitch);
                lock.notifyClients(true);
            }else lock.casts.add(session.key);
        }
        void protectClash(Session first,Session second){
            Set<UUID> parties=new HashSet<>(java.util.List.of(first.key.owner,second.key.owner));
            for(Session session:domains.values())if(parties.contains(session.key.owner)&&session.dimension==first.dimension){
                Set<UUID> spared=new HashSet<>(session.spared);spared.addAll(parties);session.spared=Set.copyOf(spared);
                for(UUID id:parties){
                    Lock lock=locks.get(id);if(lock==null)continue;
                    lock.casts.remove(session.key);
                    if(lock.casts.isEmpty()&&lock.entity.getServer().getTickCount()>=lock.stunDeadline()){lock.release();locks.remove(id);}
                }
            }
        }
        void clash(ServerPlayer player,Session started,long now){
            if(started.kind==DomainVolume.BARRAGE)return;
            for(Session other:domains.values()){
                if(other==started||other.kind==started.kind
                        ||other.kind==DomainVolume.BARRAGE||other.settled||now>=other.settlesAt||other.dimension!=started.dimension)continue;
                if(!DomainVolume.overlaps(started.center,started.radius,other.center,other.radius))continue;
                // Declared volumes meet as the second domain unfolds. The cast pair gets one new deadline.
                started.settlesAt=other.settlesAt=now+DomainControl.CLASH_TICKS;
                started.peer=other.visualId;other.peer=started.visualId;
                protectClash(started,other);
                if(other.player==player){
                    PacketDistributor.sendToPlayer(player,new DomainState(started.key.id,DomainState.CLASH,DomainControl.CLASH_TICKS,other.key.id));
                    PacketDistributor.sendToPlayer(player,new DomainState(other.key.id,DomainState.CLASH,DomainControl.CLASH_TICKS,started.key.id));
                }else{
                    PacketDistributor.sendToPlayer(player,new DomainState(started.key.id,DomainState.ACCEPTED,DomainControl.CLASH_TICKS,-1));
                    if(other.player!=null)PacketDistributor.sendToPlayer(other.player,new DomainState(other.key.id,DomainState.ACCEPTED,DomainControl.CLASH_TICKS,-1));
                }
                if(other.cat!=null)catSnapshot(other,CatSpell.CHARGE).broadcast((ServerLevel)other.cat.level());
                else observe(other);
                observe(started);break;
            }
        }
        void settle(ServerPlayer owner,Session session,long now){
            if(session.settled||domains.get(session.key)!=session)return;
            acquire(owner,session,now);
            session.settled=true;session.releaseAt=now+DomainControl.CLOSE_TICKS+RELEASE_GRACE_TICKS;
            if(session.blades!=null)session.blades.finish(owner,now);
            observe(session);
            if(domains.get(session.key)==session)
                PacketDistributor.sendToPlayer(owner,new DomainState(session.key.id,DomainState.SETTLED));
        }
    }
    private static Session activeCatSession(State state,SorcererCat cat){
        if(state==null||cat==null)return null;
        return state.domains.values().stream().filter(s->s.cat==cat&&!s.settled
                &&s.dimension==cat.level().dimension()).findFirst().orElse(null);
    }
    public static void releaseFinaleActor(SorcererCat cat){
        State state=STATES.get(cat.getServer());if(state==null)return;
        Lock lock=state.locks.remove(cat.getUUID());if(lock!=null)lock.release();
    }
    public static boolean catActive(SorcererCat cat){return cat!=null&&activeCatSession(STATES.get(cat.getServer()),cat)!=null;}
    public static boolean catClashing(SorcererCat gojo,SorcererCat sukuna){
        if(gojo==null||sukuna==null||gojo.getServer()!=sukuna.getServer())return false;
        State state=STATES.get(gojo.getServer());Session a=activeCatSession(state,gojo),b=activeCatSession(state,sukuna);
        return a!=null&&b!=null&&a.peer==b.visualId&&b.peer==a.visualId;
    }
    /** Protect both actors while their separately timed hand gestures complete. */
    public static void prepareCatDuel(SorcererCat gojo,SorcererCat sukuna){
        if(gojo==null||sukuna==null||gojo.getServer()==null||gojo.level()!=sukuna.level())return;
        State state=STATES.computeIfAbsent(gojo.getServer(),key->new State());long until=gojo.getServer().getTickCount()+400;
        state.rehearsed.put(gojo.getUUID(),new DuelProtection(sukuna.getUUID(),until));
        state.rehearsed.put(sukuna.getUUID(),new DuelProtection(gojo.getUUID(),until));
        for(Session s:state.domains.values())if(s.cat==gojo||s.cat==sukuna){
            Set<UUID> spared=new HashSet<>(s.spared);spared.add(gojo.getUUID());spared.add(sukuna.getUUID());s.spared=Set.copyOf(spared);
            for(UUID id:java.util.List.of(gojo.getUUID(),sukuna.getUUID())){
                Lock lock=state.locks.get(id);if(lock==null)continue;lock.casts.remove(s.key);
                if(lock.casts.isEmpty()&&gojo.getServer().getTickCount()>=lock.stunDeadline()){lock.release();state.locks.remove(id);}
            }
        }
    }
    public static void clearCatDuel(SorcererCat gojo,SorcererCat sukuna){
        if(gojo==null||gojo.getServer()==null)return;State state=STATES.get(gojo.getServer());if(state==null)return;
        state.rehearsed.remove(gojo.getUUID());if(sukuna!=null)state.rehearsed.remove(sukuna.getUUID());
        for(Session s:state.domains.values())if(s.cat==gojo||s.cat==sukuna){
            s.spared=Set.of(s.key.owner);s.peer=0;s.catClashSince=-1;s.nextScan=0;
        }
    }
    /** The losing field is destroyed, rather than normally closed (which would apply Void's after-stun). */
    public static void resolveCatDuel(SorcererCat gojo,SorcererCat sukuna,boolean gojoWins){
        if(!catClashing(gojo,sukuna))return;
        State state=STATES.get(gojo.getServer());Session winner=activeCatSession(state,gojoWins?gojo:sukuna);
        Session loser=activeCatSession(state,gojoWins?sukuna:gojo);long now=gojo.getServer().getTickCount();
        // The server owns the five seconds of real coexistence, not just the camera shot.
        if(winner.catClashSince<0||loser.catClashSince<0
                ||now-Math.max(winner.catClashSince,loser.catClashSince)<CAT_DUEL_CLASH_TICKS)return;
        state.rehearsed.remove(gojo.getUUID());state.rehearsed.remove(sukuna.getUUID());
        loser.settled=false;state.release(loser.key);
        winner.peer=0;winner.catClashSince=-1;winner.spared=Set.of(winner.key.owner);winner.settlesAt=now+(gojoWins?100:80);winner.nextScan=now;
        if(!gojoWins){winner.soleTarget=gojo.getUUID();winner.blades.forceFocus(sukuna,gojo,now,winner.settlesAt);}
        state.acquire(winner.cat,winner,now);
        if(!dev.jujutsuvfx.config.VfxConfig.cinematic())new dev.jujutsuvfx.network.CatCombatCue(gojo.getId(),sukuna.getId(),
                dev.jujutsuvfx.network.CatCombatCue.DOMAIN_RESULT,winner.center,winner.center,gojoWins?1:0,0,0).broadcast((ServerLevel)gojo.level());
        catSnapshot(winner,CatSpell.CHARGE).broadcast((ServerLevel)winner.cat.level());
    }
    private static void linkCatClash(State state,Session started,Session other,long now){
        // Both domains get a fresh lease when they actually meet. The spare ticks avoid
        // expiring in another server-event callback before the director resolves the pair.
        long deadline=now+Math.max(DomainControl.CLASH_TICKS,CAT_DUEL_CLASH_TICKS+20);
        started.settlesAt=other.settlesAt=deadline;
        started.catClashSince=other.catClashSince=now;
        started.peer=other.visualId;other.peer=started.visualId;
        state.protectClash(started,other);
    }
    public static boolean catDomainNearby(SorcererCat cat) {
        State state=STATES.get(cat.getServer());if(state==null)return false;
        return state.domains.values().stream().anyMatch(s->s.cat!=null&&s.dimension==cat.level().dimension()
                &&s.center.distanceToSqr(cat.position())<(s.radius+48)*(s.radius+48));
    }
    public static void openCat(SorcererCat cat, LivingEntity target, boolean counter, int sessionId) {
        openCat(cat,target,counter,sessionId,dev.jujutsuvfx.core.DomainDuration.ticks(
                dev.jujutsuvfx.config.VfxConfig.domainSeconds(cat.isSukuna()?DomainVolume.SHRINE:DomainVolume.UNLIMITED_VOID)));
    }
    public static void openCat(SorcererCat cat,LivingEntity target,boolean counter,int sessionId,int duration) {
        if(!(cat.level() instanceof ServerLevel level)||!cat.isAlive())return;
        long now=cat.getServer().getTickCount();
        State state=STATES.computeIfAbsent(cat.getServer(),key->new State());
        int kind=cat.isSukuna()?DomainVolume.SHRINE:DomainVolume.UNLIMITED_VOID;
        if(activeCatSession(state,cat)!=null)return;
        SorcererCat rival=target instanceof SorcererCat other?other:null;
        Vec3 center=(cat.isSukuna()||target==null?cat.position():target.position()).add(0,.025,0);
        Vec3 axis=target==null?cat.getLookAngle().scale(-1):cat.position().subtract(target.position());
        axis=new Vec3(axis.x,0,axis.z);if(axis.lengthSqr()<.001)axis=new Vec3(0,0,1);else axis=axis.normalize();
        if(rival!=null)for(Session other:state.domains.values())
            if(other.cat==rival&&other.kind!=kind&&!other.settled&&other.dimension==level.dimension()
                    &&other.spared.contains(cat.getUUID())){center=other.center;axis=other.axis.scale(-1);break;}
        // Shrine's open territory extends beyond Void's barrier; their interior still clashes.
        float radius=cat.isSukuna()?DomainVolume.DEFAULT_SHRINE_RADIUS:DomainVolume.DEFAULT_VOID_RADIUS;
        Session started=new Session(new Key(cat.getUUID(),sessionId),kind,center,radius,now,duration,level.dimension());
        started.cat=cat;started.axis=axis;started.visualId=sessionId;
        double behind=7+Math.max(0,-cat.position().subtract(center).dot(axis));
        started.visualOrigin=cat.isSukuna()?new Vec3(cat.getX()+axis.x*behind,center.y+.065,cat.getZ()+axis.z*behind):center;
        started.spared=!counter||rival==null?Set.of(cat.getUUID()):Set.of(cat.getUUID(),rival.getUUID());
        DuelProtection protection=state.rehearsed.get(cat.getUUID());
        if(protection!=null&&protection.until>=now)started.spared=Set.of(cat.getUUID(),protection.peer);
        if(started.blades!=null)started.blades.filter(victim->eligible(victim,started));
        if(state.domains.containsKey(started.key))return;
        state.domains.put(started.key,started);
        for(Session other:state.domains.values()){
            if(other==started||other.cat==null||rival!=null&&other.cat!=rival||other.kind==kind||other.settled||other.dimension!=level.dimension()
                    ||!DomainVolume.overlaps(center,started.radius,other.center,other.radius))continue;
            linkCatClash(state,started,other,now);
            catSnapshot(other,CatSpell.CHARGE).broadcast(level);break;
        }
        state.acquire(cat,started,now);
        catSnapshot(started,CatSpell.CHARGE).broadcast(level);
    }
    /** Rescue destroys only this cast; unrelated domains/brief stuns retain ownership of their locks. */
    static void shatterCat(SorcererCat cat,int id){
        State state=STATES.get(cat.getServer());if(state==null)return;
        Session session=state.domains.get(new Key(cat.getUUID(),id));if(session==null)return;
        session.settled=false;state.release(session.key);
    }
    static void spareRescuer(SorcererCat cat,int id,SorcererCat rescuer){
        State state=STATES.get(cat.getServer());if(state==null)return;
        Session session=state.domains.get(new Key(cat.getUUID(),id));if(session==null)return;
        Set<UUID> spared=new HashSet<>(session.spared);spared.add(rescuer.getUUID());session.spared=Set.copyOf(spared);
    }
    static Vec3 catCenter(SorcererCat cat,Vec3 fallback){
        Session session=activeCatSession(STATES.get(cat.getServer()),cat);return session==null?fallback:session.center;
    }
    public static void cancelCat(SorcererCat cat) {
        State state=STATES.get(cat.getServer());if(state==null)return;
        for(Session session:Map.copyOf(state.domains).values())if(session.cat==cat)state.release(session.key);
    }
    public static void syncCats(ServerPlayer observer) {
        State state=STATES.get(observer.getServer());if(state==null)return;
        for(Session s:state.domains.values())if(s.cat!=null&&s.dimension==observer.level().dimension()&&observer.distanceToSqr(s.center)<160*160)
            catSnapshot(s,s.settled?CatSpell.IMPACT:CatSpell.CHARGE).send(observer);
    }
    private static CatSpellState catSnapshot(Session s,int phase) {
        long now=s.cat.getServer().getTickCount();
        return new CatSpellState(s.key.id,s.cat.getId(),s.key.owner,s.kind==DomainVolume.SHRINE?CatSpell.SHRINE:CatSpell.VOID,false,
                phase,(int)(now-s.born),s.settled?(int)(now-s.releaseAt+DomainControl.CLOSE_TICKS):(int)(now-s.born),
                (int)Math.max(0,(s.settled?s.releaseAt:s.settlesAt)-now),s.radius,s.center,s.axis,s.visualOrigin==null?s.center:s.visualOrigin,s.peer);
    }
    private static int visualKind(Session s){return s.kind==DomainVolume.UNLIMITED_VOID?CatSpell.VOID:s.kind==DomainVolume.BARRAGE?CatSpell.BARRAGE:CatSpell.SHRINE;}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static void observe(Session s){
        if(s.player==null)return;long now=s.player.getServer().getTickCount();
        // The caster acknowledgement grace is a server release safeguard, not an extra visual outro.
        long visualEnd=s.settled?s.releaseAt-RELEASE_GRACE_TICKS:s.settlesAt;
        PlayerSpellSync.publish(s.player,s.key.id,visualKind(s),false,s.settled?CatSpell.IMPACT:CatSpell.CHARGE,
                (int)(now-s.born),s.settled?(int)Math.max(0,now-visualEnd+DomainControl.CLOSE_TICKS):(int)(now-s.born),
                (int)Math.max(0,visualEnd-now),s.radius,s.center,s.axis,s.visualOrigin,s.peer,0,null,s.radius);
    }
    /** Brief punches share the domain lock so one effect cannot release another effect's victim. */
    public static void brieflyFreeze(LivingEntity entity,int ticks){brieflyFreeze(entity,ticks,null);}
    public static void brieflyFreeze(LivingEntity entity,int ticks,CatAdaptation.Phenomenon phenomenon){
        MinecraftServer server=entity.getServer();
        if(server==null||!server.isSameThread()||!entity.isAlive()||entity.isRemoved()||ticks<=0)return;
        if(phenomenon!=null){
            CatAdaptation.touch(entity,phenomenon);
            if(CatAdaptation.controlDuration(entity,phenomenon,ticks)<=0)return;
        }
        if(entity instanceof dev.jujutsuvfx.entity.SorcererCat cat)cat.combat().interruptRecovery();
        State state=STATES.computeIfAbsent(server,key->new State());
        Lock lock=state.locks.get(entity.getUUID());
        if(lock!=null&&(lock.entity!=entity||lock.dimension!=entity.level().dimension())){
            lock.release();state.locks.remove(entity.getUUID());lock=null;
        }
        boolean fresh=lock==null;
        if(fresh){lock=new Lock(entity);state.locks.put(entity.getUUID(),lock);}
        if(phenomenon==null)lock.stunUntil=Math.max(lock.stunUntil,server.getTickCount()+Math.min(ticks,100));
        else lock.adaptiveStuns.put(phenomenon,new BriefControl(server.getTickCount(),Math.min(ticks,100)));
        lock.pose.hold(entity);entity.hasImpulse=true;
        if(fresh)lock.notifyClients(true);
    }
    public static void handle(DomainControl packet,Player player){
        if(!(player instanceof ServerPlayer owner))return;
        MinecraftServer server=owner.getServer();if(server==null)return;
        State state=STATES.computeIfAbsent(server,key->new State());long now=server.getTickCount();
        Key key=new Key(owner.getUUID(),packet.session());
        if(packet.action()==DomainControl.START||packet.action()==DomainControl.START_TIMED){
            if(packet.action()==DomainControl.START_TIMED&&!owner.hasPermissions(2)){
                PacketDistributor.sendToPlayer(owner,new DomainState(packet.session(),DomainState.CANCELED));return;
            }
            if(packet.session()<=0||!Double.isFinite(packet.x())||!Double.isFinite(packet.y())||!Double.isFinite(packet.z())
                    ||!Float.isFinite(packet.radius())||packet.radius()<3||packet.radius()>DomainVolume.maxRadius(packet.kind())
                    ||packet.ticks()<100||packet.ticks()>2400||!DomainVolume.validKind(packet.kind())
                    ||!finite(packet.direction())||packet.direction().lengthSqr()<.5||packet.direction().lengthSqr()>1.5
                    ||!owner.isAlive()||owner.isSpectator()){
                PacketDistributor.sendToPlayer(owner,new DomainState(packet.session(),DomainState.CANCELED));return;
            }
            Vec3 center=new Vec3(packet.x(),packet.y(),packet.z());
            if(owner.position().distanceToSqr(center)>64*64){
                PacketDistributor.sendToPlayer(owner,new DomainState(packet.session(),DomainState.CANCELED));return;
            }
            if(state.domains.containsKey(key))return;
            int duration=packet.kind()==DomainVolume.BARRAGE||packet.action()==DomainControl.START_TIMED?packet.ticks():dev.jujutsuvfx.core.DomainDuration.castTicks(
                    dev.jujutsuvfx.config.VfxConfig.domainSeconds(packet.kind()),packet.ticks(),false);
            Session started=new Session(key,packet.kind(),center,packet.radius(),now,duration,owner.level().dimension());
            started.player=owner;started.axis=packet.direction().normalize();
            started.visualId=PlayerSpellSync.id(owner,visualKind(started),key.id);
            Vec3 flat=new Vec3(started.axis.x,0,started.axis.z);if(flat.lengthSqr()<.001)flat=new Vec3(0,0,1);else flat=flat.normalize();
            started.visualOrigin=started.kind==DomainVolume.SHRINE?center.add(flat.scale(Math.clamp(started.radius*.43,2.5,9))).add(0,.065,0):center;
            if(started.blades!=null)started.blades.filter(victim->eligible(victim,started));
            state.domains.put(key,started);observe(started);state.acquire(owner,started,now);
            if(packet.kind()!=DomainVolume.BARRAGE)TechniqueGestures.start(owner,key.id,packet.kind()==DomainVolume.UNLIMITED_VOID
                    ?dev.jujutsuvfx.network.SkillGesture.VOID:dev.jujutsuvfx.network.SkillGesture.SHRINE,null,false);
            PacketDistributor.sendToPlayer(owner,new DomainState(key.id,DomainState.ACCEPTED,duration,-1));
            state.clash(owner,started,now);return;
        }
        Session session=state.domains.get(key);if(session==null)return;
        if(packet.action()==DomainControl.STOP){state.release(key);return;}
        if(packet.action()==DomainControl.FINISH){if(session.settled)state.release(key);return;}
        if(packet.action()==DomainControl.CLOSE&&!session.settled)session.settlesAt=Math.min(session.settlesAt,now);
    }
    private static long adaptationDeadline(Session session,Entity entity){
        if(session.kind!=DomainVolume.UNLIMITED_VOID||!CatAdaptation.enabled(entity))return Long.MAX_VALUE;
        int stage=CatAdaptation.stage(entity,CatAdaptation.Phenomenon.VOID);
        if(stage==4)return 0;
        Long first=session.adaptationContacts.get(entity.getUUID());
        if(stage==0||first==null)return Long.MAX_VALUE;
        int duration=(int)Math.clamp(session.settlesAt-session.born+DomainControl.CLOSE_TICKS,1,2600);
        return first+CatAdaptation.controlDuration(entity,CatAdaptation.Phenomenon.VOID,duration);
    }
    private static void pruneAdapted(State state,Lock lock,long now){
        lock.casts.removeIf(key->{Session session=state.domains.get(key);
            return session!=null&&adaptationDeadline(session,lock.entity)<=now;});
        lock.adaptiveStuns.entrySet().removeIf(entry->entry.getValue().start+
                CatAdaptation.controlDuration(lock.entity,entry.getKey(),entry.getValue().ticks)<=now);
    }
    private static boolean eligible(Entity entity,Session session){
        if(session.soleTarget!=null&&!session.soleTarget.equals(entity.getUUID()))return false;
        if(session.cat!=null&&(session.spared.contains(entity.getUUID())||!TechniquePlayerRules.canAffect(session.cat,entity)))return false;
        if(session.spared.contains(entity.getUUID()))return false;
        if(session.player!=null&&!TechniquePlayerRules.canAffect(session.player,entity))return false;
        if(!entity.isAlive()||entity.isRemoved()||entity.isSpectator()||entity.getUUID().equals(session.key.owner))return false;
        return session.kind==DomainVolume.UNLIMITED_VOID||entity instanceof LivingEntity;
    }
    static boolean inVoid(ServerPlayer player){
        State state=STATES.get(player.getServer());if(state==null)return false;
        Lock lock=state.locks.get(player.getUUID());if(lock==null||lock.entity!=player)return false;
        return lock.casts.stream().anyMatch(key->{Session session=state.domains.get(key);
            return session!=null&&session.kind==DomainVolume.UNLIMITED_VOID&&session.dimension==player.level().dimension();});
    }
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event){
        MinecraftServer server=event.getServer();State state=STATES.get(server);if(state==null)return;
        long now=server.getTickCount();
        if(!dev.jujutsuvfx.config.VfxConfig.cinematic())for(Session a:java.util.List.copyOf(state.domains.values())){
            if(a.cat==null||!a.cat.isGojo()||a.catClashSince<0||now-a.catClashSince<CAT_DUEL_CLASH_TICKS)continue;
            Session b=state.domains.values().stream().filter(s->s.cat!=null&&s.cat.isSukuna()&&s.visualId==a.peer&&s.peer==a.visualId).findFirst().orElse(null);
            if(b!=null)resolveCatDuel(a.cat,b.cat,a.cat.getRandom().nextBoolean());
        }
        state.rehearsed.entrySet().removeIf(entry->entry.getValue().until<now);
        for(Session session:Map.copyOf(state.domains).values()){
            if(state.domains.get(session.key)!=session)continue;
            if(session.cat!=null){
                SorcererCat cat=session.cat;
                if(!cat.isAlive()||cat.isRemoved()||cat.isNoAi()||cat.level().dimension()!=session.dimension){state.release(session.key);continue;}
                if(!session.settled&&now>=session.settlesAt){
                    session.settled=true;session.releaseAt=now+DomainControl.CLOSE_TICKS;
                    if(session.blades!=null)session.blades.finish(cat,now);
                    catSnapshot(session,CatSpell.IMPACT).broadcast((ServerLevel)cat.level());
                }
                if(session.settled&&now>=session.releaseAt){state.release(session.key);continue;}
                if(!session.settled){
                    if(now>=session.nextScan)state.acquire(cat,session,now);
                    if(session.blades!=null)session.blades.tick(cat,now,session.settlesAt,session.currentRadius(now),
                            target->state.capture(session,target),()->state.domains.get(session.key)==session&&!session.settled&&cat.isAlive());
                }
                if(now%20==0)catSnapshot(session,session.settled?CatSpell.IMPACT:CatSpell.CHARGE).broadcast((ServerLevel)cat.level());
                continue;
            }
            ServerPlayer owner=server.getPlayerList().getPlayer(session.key.owner);
            if(owner==null||!owner.isAlive()||owner.isSpectator()||owner.level().dimension()!=session.dimension)
                state.cancel(session.key,owner);
            else if(!session.settled&&now>=session.settlesAt)state.settle(owner,session,now);
            else if(session.settled&&now>=session.releaseAt)state.cancel(session.key,owner);
            else if(!session.settled&&now>=session.nextScan)state.acquire(owner,session,now);
            if(owner!=null&&!session.settled&&session.blades!=null&&state.domains.get(session.key)==session)
                session.blades.tick(owner,now,session.settlesAt,session.currentRadius(now),
                        target->state.capture(session,target),
                        ()->state.domains.get(session.key)==session&&!session.settled&&owner.isAlive());
            if(owner!=null&&state.domains.get(session.key)==session&&!session.settled)observe(session);
        }
        for(var iterator=state.locks.values().iterator();iterator.hasNext();){
            Lock lock=iterator.next();
            pruneAdapted(state,lock,now);
            if(!lock.entity.isAlive()||lock.entity.isRemoved()||lock.entity.level().dimension()!=lock.dimension
                    ||lock.casts.isEmpty()&&now>=lock.stunDeadline()){
                lock.release();iterator.remove();
            }else {lock.advanceDamageClock(now);lock.pose.hold(lock.entity);}
        }
    }
    /** Read-only duration estimate for combat decisions; does not acquire a new freeze. */
    static int controlTicks(Entity entity){
        MinecraftServer server=entity.getServer();State state=STATES.get(server);
        if(server==null||state==null||!entity.isAlive())return 0;
        Lock lock=state.locks.get(entity.getUUID());if(lock==null||lock.entity!=entity)return 0;
        long now=server.getTickCount(),end=lock.stunDeadline();
        for(Key key:lock.casts){Session session=state.domains.get(key);if(session==null||session.dimension!=entity.level().dimension())continue;
            long release=session.settled?session.releaseAt:session.settlesAt+DomainControl.CLOSE_TICKS;
            long adapted=adaptationDeadline(session,entity);
            if(session.kind==DomainVolume.UNLIMITED_VOID&&!session.spared.contains(entity.getUUID()))
                release+=CatAdaptation.controlDuration(entity,CatAdaptation.Phenomenon.VOID,100);
            end=Math.max(end,Math.min(release,adapted));
        }
        return (int)Math.clamp(end-now,0,2600);
    }
    /** Called before tickCount increments, including the passenger tick path which has no Pre event. */
    public static boolean hold(Entity entity){
        if(entity.level().isClientSide||!entity.isAlive()||entity.isRemoved())return false;
        if(entity instanceof SorcererCat cat&&cat.finaleWard()){releaseFinaleActor(cat);return false;}
        MinecraftServer server=entity.getServer();State state=STATES.get(server);if(state==null)return false;
        // New entities inside the white field are caught before their first movement/explosion tick.
        for(Session session:state.domains.values())
            if(session.kind==DomainVolume.UNLIMITED_VOID&&!session.settled&&session.dimension==entity.level().dimension()
                    &&eligible(entity,session)&&DomainVolume.intersects(session.center,session.radius,entity.getBoundingBox()))
                state.capture(session,entity);
        Lock lock=state.locks.get(entity.getUUID());if(lock==null)return false;
        pruneAdapted(state,lock,server.getTickCount());
        boolean active=server.getTickCount()<lock.stunDeadline()||lock.casts.stream().anyMatch(key->{Session session=state.domains.get(key);
            return session!=null&&entity.level().dimension()==session.dimension;});
        if(!active){state.locks.remove(entity.getUUID());lock.release();return false;}
        lock.advanceDamageClock(server.getTickCount());lock.pose.hold(entity);return true;
    }
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event){
        if(!(event.getEntity() instanceof ServerPlayer observer)||!observer.connection.hasChannel(EntityStasis.TYPE))return;
        State state=STATES.get(observer.getServer());if(state==null)return;
        Lock lock=state.locks.get(event.getTarget().getUUID());
        if(lock!=null)PacketDistributor.sendToPlayer(observer,EntityStasis.of(lock.entity,lock.pose,true));
    }
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event){
        State state=STATES.remove(event.getServer());
        if(state!=null)for(Lock lock:state.locks.values())lock.release();
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event){STATES.remove(event.getServer());}
}
