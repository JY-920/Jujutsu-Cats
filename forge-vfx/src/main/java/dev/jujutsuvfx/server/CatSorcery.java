package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.*;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatCombatCue;
import dev.jujutsuvfx.network.CatSpellState;
import dev.jujutsuvfx.network.SukunaCut;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import dev.jujutsuvfx.platform.PacketDistributor;
import java.util.*;
import static dev.jujutsuvfx.core.CatSpell.*;

/** Low-frequency server-authored interludes between melee rounds. No fake player or client input. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class CatSorcery {
    private static final Map<MinecraftServer, State> STATES=new WeakHashMap<>();
    private static final class Rhythm {
        long skillAt,domainAt,lastCombat,heavyAt,reactionAt,punishAt;int seenCast,seenPressure;
        Rhythm(SorcererCat cat,long now) { skillAt=now+(cat.isSukuna()?90:120)+cat.getRandom().nextInt(cat.isSukuna()?70:100);domainAt=now+560+cat.getRandom().nextInt(280);lastCombat=now; }
    }
    private record Counter(SorcererCat cat,SorcererCat rival,long due) {}
    private record Scene(ResourceKey<Level> dimension,Vec3 center,long until) {}
    private static final class State {
        final Map<Integer,Cast> casts=new LinkedHashMap<>();
        final PurpleCarver.Budget carving=new PurpleCarver.Budget();
        final Map<UUID,Setup> positions=new HashMap<>();
        final Map<UUID,Rhythm> rhythms=new HashMap<>();
        final List<Counter> counters=new ArrayList<>();
        final List<Scene> scenes=new ArrayList<>();
        int serial=1000,poseSerial=1000000;
    }
    private static final class Setup {
        final SorcererCat cat;final LivingEntity target;final CatTactics.Maneuver movement;
        final int kind,pose;final boolean large,responding;
        boolean exploiting;
        Setup(State state,SorcererCat cat,LivingEntity target,int kind,boolean large,boolean responding,CatTactics.Maneuver movement) {
            this.cat=cat;this.target=target;this.kind=kind;this.large=large;this.responding=responding;this.movement=movement;pose=++state.poseSerial;
        }
        void publish(){cat.publishCombat(CatCombatMoves.pack(pose,CatCombatMoves.REPOSITION,movement.ticks,movement.hand,movement.style));}
    }
    private static final class Cast {
        final SorcererCat cat; final ServerLevel level; final UUID target; final int session,kind,pose;
        final boolean large,amplified; final float radius; final SorcererCat rival;
        final Set<UUID> hit=new HashSet<>(); final Map<UUID,Integer> nextHit=new HashMap<>();
        Vec3 point,previous,direction,origin;
        final Vec3 chargeAxis;
        Vec3 scriptedDestination;
        int scriptedPartner;
        java.util.function.Consumer<UUID> onFusion;
        CatCleave cleave;
        PurpleCarver carver;
        ShotChunks chunks;
        boolean hovering;
        int age,stage,phase,stalled,fullAt=-1; float traveled;
        boolean parked,releasedPose,canceled,responding,duelDomain,domainReleased,soloDomain;
        int revealTicks,revealAge;
        Cast(State state,SorcererCat cat,LivingEntity target,int kind,boolean large) {
            this(state,cat,target,kind,large,false);
        }
        Cast(State state,SorcererCat cat,LivingEntity target,int kind,boolean large,boolean amplified) {
            this.cat=cat;level=(ServerLevel)cat.level();this.target=target.getUUID();this.kind=kind;this.large=large;this.amplified=amplified;
            if(amplified){carver=new PurpleCarver();chunks=new ShotChunks(level);}
            rival=target instanceof SorcererCat other?other:null;
            session=PlayerSpellSync.nextId(cat.getServer());pose=++state.poseSerial;
            radius=kind==BLUE?(large?12:8):kind==RED?(large?3:LimitlessTechnique.shotRadius(RED,10))
                    :kind==PURPLE?(amplified?6:large?3:1):kind==FURNACE?12:CatSpell.domain(kind)?40:4;
            phase=kind==PURPLE?PREPARE:CHARGE;
            if(kind==VOID&&cat.beginFirstDomainReveal())revealTicks=SorcererCat.BLINDFOLD_TICKS+8;
            hovering=!cat.onGround(); if(hovering)cat.renewCombatHover();
            direction=aim(cat,target);chargeAxis=direction;point=previous=muzzle();origin=point;
        }
        Vec3 muzzle() {
            if(kind==PURPLE) {
                return LimitlessTechnique.purpleMuzzle(cat,radius,1,direction);
            }
            if(kind==FURNACE) return FurnaceFlight.bow(cat,1);
            if(kind==BLUE) return LimitlessTechnique.blueMuzzle(cat);
            return LimitlessTechnique.redMuzzle(cat,1,direction,cat.brokenArm()?-1:1);
        }
        boolean busy() { return !releasedPose && age<CatSpell.poseTicks(kind,large); }
        CatSpellState snapshot(int state) {
            return new CatSpellState(session,cat.getId(),cat.getUUID(),kind,large,state,age,stage,
                    Math.max(0,kind==BLUE?(scriptedDestination!=null?600:200)-age:CatSpell.poseTicks(kind,large)-age),radius,point,direction,origin,0);
        }
        void send() { if(kind<DISMANTLE)snapshot(phase).broadcast(level); }
    }
    private CatSorcery() {}
    private static Cast casting(State state,SorcererCat cat){
        if(state==null)return null;
        return state.casts.values().stream().filter(c->c.cat==cat&&!c.canceled&&c.busy()).findFirst().orElse(null);
    }
    public static boolean busy(SorcererCat cat){
        State state=STATES.get(cat.getServer());return state!=null&&(state.positions.containsKey(cat.getUUID())||casting(state,cat)!=null);
    }
    private static boolean threat(Cast c,SorcererCat target){
        if(c.canceled||c.kind==BLUE||CatSpell.domain(c.kind)||!c.target.equals(target.getUUID())||c.level!=target.level())return false;
        if(c.phase<=CHARGE)return DomainFreeze.controlTicks(c.cat)==0;
        if(c.phase!=FLIGHT)return false;
        if(c.kind==CLEAVE)return c.stage<18;
        if(c.kind==DISMANTLE)return false;
        Vec3 from=c.kind==RED&&c.large?c.origin:c.point,delta=target.getBoundingBox().getCenter().subtract(from);
        double along=delta.dot(c.direction),padding=c.radius+target.getBbWidth()*.5+1.4;
        double reach=c.kind==RED&&c.large?LimitlessTechnique.RED_BEAM_RANGE:Math.min(26,
                c.kind==FURNACE?FurnaceFlight.RANGE-c.traveled:LimitlessTechnique.shotRange(c.kind,c.large,c.amplified)-c.traveled);
        return along>=-padding&&along<=reach+padding&&delta.lengthSqr()-along*along<=padding*padding;
    }
    private static Cast threat(State state,SorcererCat caster,SorcererCat target){
        return state==null?null:state.casts.values().stream().filter(c->c.cat==caster&&threat(c,target))
                .min(Comparator.comparingDouble(c->c.point.distanceToSqr(target.getBoundingBox().getCenter()))).orElse(null);
    }
    public static boolean threatens(SorcererCat caster,SorcererCat target){return threat(STATES.get(caster.getServer()),caster,target)!=null;}
    private static int controlWindow(State state,LivingEntity target){
        int ticks=Math.max(DomainFreeze.controlTicks(target),LimitlessSkills.blueControlTicks(target));
        for(Cast c:state.casts.values())if(c.kind==BLUE&&c.phase==FLIGHT&&!c.canceled&&c.level==target.level()
                &&c.cat.canFight(target)&&CatAdaptation.controlScale(target,CatAdaptation.Phenomenon.BLUE)>0&&c.point.distanceToSqr(target.getBoundingBox().getCenter())
                    <Math.pow(LimitlessTechnique.blueCoreRadius(c.radius)+1.4,2)&&visible(c,target))ticks=Math.max(ticks,200-c.age);
        return ticks;
    }
    public static boolean exploitControl(SorcererCat cat,LivingEntity target){
        if(CatDuel.meleeOnly(cat)||GojoLastStand.active(cat))return false;
        if(!cat.isGojo()||target==null||!able(cat)||!cat.canFight(target)||busy(cat)
                ||cat.distanceToSqr(target)>SorcererCat.RIVAL_RANGE*SorcererCat.RIVAL_RANGE||DomainFreeze.controlTicks(cat)>0)return false;
        if(!CatDuel.manages(cat)&&cat.needsPlayerDomain())return false;
        State state=STATES.computeIfAbsent(cat.getServer(),key->new State());long now=cat.getServer().getTickCount();
        Rhythm rhythm=state.rhythms.computeIfAbsent(cat.getUUID(),key->new Rhythm(cat,now));
        if(now<rhythm.punishAt)return false;
        int window=controlWindow(state,target);if(window<42)return false;
        float roll=cat.getRandom().nextFloat();
        int kind=window>=82&&roll<.42f?RED:PURPLE;
        boolean large=kind==RED||window>=120&&roll<.78f;
        rhythm.punishAt=now+160+cat.getRandom().nextInt(70);rhythm.lastCombat=now;
        schedule(state,cat,target,kind,large,false);
        Setup setup=state.positions.get(cat.getUUID());if(setup!=null)setup.exploiting=true;
        state.scenes.add(new Scene(cat.level().dimension(),cat.position(),now+CatSpell.poseTicks(kind,large)+48));
        return true;
    }
    public static boolean tryPlayerDomain(SorcererCat cat){
        if(CatDuel.meleeOnly(cat)||GojoLastStand.active(cat))return false;
        LivingEntity target=cat.getTarget();
        if(CatDuel.manages(cat)||!cat.needsPlayerDomain()||target==null||DomainFreeze.catActive(cat)||!able(cat)
                ||!cat.canFight(target)||cat.level()!=target.level()||busy(cat)||DomainFreeze.hold(cat))return false;
        // Approach normally when the opponent is outside the domain; do not waste
        // the guaranteed opening on an empty 40-block sphere or an unreachable setup.
        if(cat.distanceToSqr(target)>36*36)return false;
        State state=STATES.computeIfAbsent(cat.getServer(),key->new State());
        long now=cat.getServer().getTickCount();
        begin(state,cat,target,cat.isSukuna()?SHRINE:VOID,false);
        Rhythm rhythm=state.rhythms.computeIfAbsent(cat.getUUID(),key->new Rhythm(cat,now));
        rhythm.lastCombat=now;rhythm.domainAt=now+1500;rhythm.skillAt=now+240;
        return true;
    }
    public static boolean tryStart(SorcererCat cat,LivingEntity target) {
        if(CatDuel.meleeOnly(cat)||GojoLastStand.active(cat))return false;
        if (cat.isMahoraga()) return false;
        if(!CatDuel.manages(cat)&&cat.needsPlayerDomain())return false;
        if(!(cat.level() instanceof ServerLevel level)||(!cat.onGround()&&!cat.combatHovering())||!cat.canFight(target)
                ||cat.distanceToSqr(target)>52*52||!cat.getSensing().hasLineOfSight(target))return false;
        long now=cat.getServer().getTickCount();State s=STATES.computeIfAbsent(cat.getServer(),key->new State());
        Rhythm r=s.rhythms.computeIfAbsent(cat.getUUID(),key->new Rhythm(cat,now));
        if(now-r.lastCombat>400){r.skillAt=now+100+cat.getRandom().nextInt(80);r.domainAt=Math.max(r.domainAt,now+440);}
        r.lastCombat=now;
        if(busy(cat))return false;
        boolean scene=s.scenes.stream().anyMatch(v->v.dimension==level.dimension()&&v.until>now&&v.center.distanceToSqr(cat.position())<72*72);
        if(scene)return false;
        if(!CatDuel.manages(cat)&&target instanceof SorcererCat rival&&cat.isRival(rival)&&now>=r.domainAt&&!DomainFreeze.catDomainNearby(cat)){
            schedule(s,cat,target,cat.isSukuna()?SHRINE:VOID,false,false);
            long next=now+1500+cat.getRandom().nextInt(600);r.domainAt=next;
            s.rhythms.computeIfAbsent(rival.getUUID(),key->new Rhythm(rival,now)).domainAt=next;
            s.scenes.add(new Scene(level.dimension(),cat.position(),now+328));
            return true;
        }
        if(now<r.skillAt)return false;
        boolean heavyReady=now>=r.heavyAt;
        int kind;
        if(cat.isSukuna()){
            float choice=cat.getRandom().nextFloat();
            kind=heavyReady&&choice>.78f?FURNACE:choice<.39f?DISMANTLE:CLEAVE;
        }
        else {float choice=cat.getRandom().nextFloat();kind=heavyReady?(choice<.42f?BLUE:choice<.78f?RED:PURPLE):(choice<.54f?BLUE:RED);}
        boolean large=(kind==BLUE||kind==RED||kind==PURPLE||kind==CLEAVE)&&heavyReady&&cat.getRandom().nextFloat()<.40f;
        schedule(s,cat,target,kind,large,false);
        s.scenes.add(new Scene(level.dimension(),cat.position(),now+48+CatSpell.poseTicks(kind,large)+24));
        return true;
    }
    public static boolean tryReact(SorcererCat cat,LivingEntity target) {
        if(CatDuel.meleeOnly(cat)||GojoLastStand.active(cat))return false;
        if(!(target instanceof SorcererCat rival)||!able(cat)||DomainFreeze.hold(cat))return false;
        State state=STATES.get(cat.getServer());if(state==null||busy(cat)||state.positions.size()>=12)return false;
        Cast threat=threat(state,rival,cat);
        if(threat==null||!threatens(rival,cat)||threat.age<3)return false;
        long now=cat.getServer().getTickCount();Rhythm rhythm=state.rhythms.computeIfAbsent(cat.getUUID(),key->new Rhythm(cat,now));
        if(now<rhythm.reactionAt||cat.distanceToSqr(rival)>SorcererCat.RIVAL_RANGE*SorcererCat.RIVAL_RANGE)return false;
        int remaining=Math.max(0,CatSpell.launchTick(threat.kind,threat.large)-threat.age);
        rhythm.reactionAt=now+30+cat.getRandom().nextInt(16);
        boolean dodge=(remaining>12||threat.phase==FLIGHT)&&cat.getRandom().nextFloat()<.65f;
        CatTactics.Maneuver movement=dodge?CatTactics.reaction(cat,rival,threat.direction,false):null;
        if(movement==null)return cat.combat().startGuard(rival,remaining+26);
        cat.combat().reset();cat.clearCombatEnergy();
        Setup setup=new Setup(state,cat,rival,-1,false,false,movement);state.positions.put(cat.getUUID(),setup);setup.publish();
        return true;
    }
    private static void schedule(State state,SorcererCat cat,LivingEntity target,int kind,boolean large,boolean responding) {
        cat.combat().reset();cancelCasting(cat);cat.clearCombatEnergy();
        if(responding){begin(state,cat,target,kind,large).responding=true;return;}
        CatTactics.Maneuver movement=CatTactics.forCast(cat,target,kind,large,false);
        if(movement==null){startWhenReady(state,cat,target,kind,large,responding);return;}
        Setup setup=new Setup(state,cat,target,kind,large,responding,movement);state.positions.put(cat.getUUID(),setup);setup.publish();
    }
    private static void startWhenReady(State state,SorcererCat cat,LivingEntity target,int kind,boolean large,boolean responding) {
        // Never silently turn a failed heavy retreat into a close-range cast.
        long now=cat.getServer().getTickCount();Rhythm rhythm=state.rhythms.computeIfAbsent(cat.getUUID(),key->new Rhythm(cat,now));
        if(!CatTactics.ready(cat,target,kind,large)){rhythm.skillAt=Math.max(rhythm.skillAt,now+70);cat.releaseCombatHover();cat.publishCombat(-1);return;}
        begin(state,cat,target,kind,large).responding=responding;
        boolean cut=cat.isSukuna()&&(kind==DISMANTLE||kind==CLEAVE);
        rhythm.skillAt=now+CatSpell.poseTicks(kind,large)+(cut?110:180)+cat.getRandom().nextInt(cut?80:120);
        if(large||kind==FURNACE||kind==PURPLE)rhythm.heavyAt=now+600;
    }
    private static Cast begin(State state,SorcererCat cat,LivingEntity target,int kind,boolean large) {
        cat.combat().reset();cat.clearCombatEnergy();
        Cast c=new Cast(state,cat,target,kind,large);state.casts.put(c.session,c);
        face(cat,c.direction);publish(c);c.send();return c;
    }
    static int startAmplified(SorcererCat cat,SorcererCat target) {
        cancelCasting(cat); cat.combat().reset(); cat.clearCombatEnergy();
        State state=STATES.computeIfAbsent(cat.getServer(),key->new State());
        Cast c=new Cast(state,cat,target,PURPLE,true,true);state.casts.put(c.session,c);
        c.hovering=true;cat.renewCombatHover();
        face(cat,c.direction);publish(c);c.send();return c.session;
    }
    static int startDuelDomain(SorcererCat cat,SorcererCat target){
        cancelCasting(cat);State state=STATES.computeIfAbsent(cat.getServer(),key->new State());
        Cast c=begin(state,cat,target,cat.isSukuna()?SHRINE:VOID,false);c.duelDomain=true;c.responding=true;return c.session;
    }
    static int startSoloDomain(SorcererCat cat,SorcererCat target){
        int id=startDuelDomain(cat,target);State state=STATES.get(cat.getServer());
        Cast cast=state.casts.get(id);cast.soloDomain=true;return id;
    }
    /** A scripted field cannot obscure its own prelude, even when a server tick arrives early. */
    static boolean domainPoseReady(SorcererCat cat,int session){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        return c!=null&&c.cat==cat&&c.duelDomain&&c.revealAge>=c.revealTicks
                &&c.age>=CatSpell.launchTick(c.kind,c.large);
    }
    static void releaseDuelDomain(SorcererCat cat,int session){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        if(c!=null&&c.cat==cat&&c.duelDomain)c.domainReleased=true;
    }
    static int startArmCut(SorcererCat cat,SorcererCat target){
        cancelCasting(cat);State state=STATES.computeIfAbsent(cat.getServer(),key->new State());
        return begin(state,cat,target,DISMANTLE,false).session;
    }
    static Vec3 point(SorcererCat cat,int session,Vec3 fallback){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        return c!=null&&c.cat==cat?c.point:fallback;
    }
    static boolean openingFused(SorcererCat cat,int session){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        return c!=null&&c.cat==cat&&c.age>=CatSpell.launchTick(PURPLE,true)-20;
    }
    static void freeFinaleBlue(SorcererCat cat,int session){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        if(c!=null&&c.cat==cat){releasePose(c);c.hovering=false;cat.releaseCombatHover();}
    }
    static boolean openingApproaching(SorcererCat cat,int session,LivingEntity target){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        if(c==null||c.cat!=cat||c.phase!=FLIGHT)return false;
        Vec3 delta=target.getBoundingBox().getCenter().subtract(c.point);double along=delta.dot(c.direction);
        double width=c.radius+target.getBbWidth()+2;
        return along>=-width&&along<=c.radius+20&&delta.lengthSqr()-along*along<=width*width;
    }
    static int startFinaleBlue(SorcererCat cat,LivingEntity target,Vec3 destination){
        State state=STATES.computeIfAbsent(cat.getServer(),key->new State());
        cat.combat().reset();cat.clearCombatEnergy();
        Cast c=new Cast(state,cat,target,BLUE,false);c.scriptedDestination=destination;c.hovering=true;
        c.direction=destination.subtract(cat.getEyePosition()).normalize();face(cat,c.direction);
        c.point=c.previous=c.origin=c.muzzle();state.casts.put(c.session,c);publish(c);c.send();return c.session;
    }
    static boolean finaleBlueLaunched(SorcererCat cat,int session){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        return c!=null&&c.cat==cat&&c.phase==FLIGHT;
    }
    static boolean finaleBlueReady(SorcererCat cat,int session){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        return c!=null&&c.cat==cat&&c.scriptedDestination!=null&&c.phase==FLIGHT
                &&c.parked&&c.point.distanceToSqr(c.scriptedDestination)<.36;
    }
    static int startFinaleRed(SorcererCat cat,LivingEntity target,int blueSession,java.util.function.Consumer<UUID> fused){
        State state=STATES.get(cat.getServer());Cast blue=state==null?null:state.casts.get(blueSession);
        if(blue==null||blue.cat!=cat||!finaleBlueReady(cat,blueSession))return 0;
        cat.combat().reset();cat.clearCombatEnergy();
        Cast c=new Cast(state,cat,target,RED,false);c.scriptedDestination=blue.point;c.scriptedPartner=blueSession;
        c.onFusion=fused;c.hovering=true;c.direction=blue.point.subtract(cat.getEyePosition()).normalize();face(cat,c.direction);
        c.point=c.previous=c.origin=c.muzzle();state.casts.put(c.session,c);
        publish(c);c.send();return c.session;
    }
    static void cancelSession(SorcererCat cat,int session){
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        if(c!=null&&c.cat==cat)cancel(state,c);
    }
    static boolean active(SorcererCat cat,int session) {
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        return c!=null&&c.cat==cat&&c.session==session&&current(c);
    }
    static boolean openingPassed(SorcererCat cat,int session) {
        State state=STATES.get(cat.getServer());Cast c=state==null?null:state.casts.get(session);
        if(c==null||c.cat!=cat||c.session!=session||c.phase==IMPACT)return true;
        if(!c.hit.contains(c.target))return false;
        var target=c.level.getEntity(c.target);
        return target==null||c.point.subtract(target.getBoundingBox().getCenter()).dot(c.direction)>c.radius+3;
    }
    private static void publish(Cast c) {
        if(!c.busy())return;
        if(c.revealAge<c.revealTicks){c.cat.publishCombat(CatCombatMoves.pack(c.pose,CatCombatMoves.DOMAIN_REVEAL,c.revealAge,SorcererCat.LEFT,0));return;}
        c.cat.publishCombat(CatCombatMoves.pack(c.pose,CatSpell.pose(c.kind),c.age,c.cat.brokenArm()?SorcererCat.LEFT:SorcererCat.RIGHT,
                (c.kind==VOID?2:c.large?1:0)|(c.hovering?4:0)|(c.scriptedDestination!=null||c.amplified?2:0)));
    }
    private static void releasePose(Cast c){
        if(c.releasedPose)return;c.releasedPose=true;
        if(!busy(c.cat)){c.cat.releaseCombatHover();c.cat.publishCombat(-1);}
    }
    private static void cancel(State state,Cast c){
        state.casts.remove(c.session,c);c.canceled=true;
        if(c.revealTicks>0)c.cat.finishFirstDomainReveal();
        if(c.carver!=null)c.carver.close();if(c.chunks!=null)c.chunks.close();
        if(c.kind<DISMANTLE)c.snapshot(END).broadcast(c.level);releasePose(c);
    }
    public static void cancelCasting(SorcererCat cat){
        State state=STATES.get(cat.getServer());if(state==null)return;
        Setup setup=state.positions.remove(cat.getUUID());if(setup!=null){setup.movement.stop();cat.publishCombat(-1);}
        for(Cast c:List.copyOf(state.casts.values()))if(c.cat==cat){
            if(c.phase<=CHARGE||c.kind>=DISMANTLE)cancel(state,c);else releasePose(c);
        }
        if(!busy(cat))cat.releaseCombatHover();
    }
    public static void cancelAll(SorcererCat cat){
        cancelCasting(cat);DomainFreeze.cancelCat(cat);
        State state=STATES.get(cat.getServer());if(state==null)return;
        for(Cast c:List.copyOf(state.casts.values()))if(c.cat==cat)cancel(state,c);
        state.counters.removeIf(v->v.cat==cat||v.rival==cat);
    }
    @SubscribeEvent public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();State s=STATES.get(server);if(s==null)return;
        long now=server.getTickCount();s.scenes.removeIf(v->now>=v.until);
        s.rhythms.entrySet().removeIf(e->now-e.getValue().lastCombat>2400&&s.casts.values().stream().noneMatch(c->c.cat.getUUID().equals(e.getKey()))&&!s.positions.containsKey(e.getKey()));
        for(Counter counter:List.copyOf(s.counters))if(now>=counter.due){
            s.counters.remove(counter);SorcererCat cat=counter.cat;
            if(able(cat)&&able(counter.rival)&&cat.level()==counter.rival.level()&&cat.canFight(counter.rival)
                    &&cat.distanceToSqr(counter.rival)<=SorcererCat.RIVAL_RANGE*SorcererCat.RIVAL_RANGE){
                schedule(s,cat,counter.rival,cat.isSukuna()?SHRINE:VOID,false,true);
                Rhythm rhythm=s.rhythms.computeIfAbsent(cat.getUUID(),id->new Rhythm(cat,now));
                rhythm.domainAt=Math.max(rhythm.domainAt,now+1500);rhythm.skillAt=now+360;
            }
        }
        for(Setup setup:List.copyOf(s.positions.values())) {
            if(s.positions.get(setup.cat.getUUID())!=setup)continue;
            if(!able(setup.cat)||setup.cat.level()!=setup.movement.level||!setup.cat.canFight(setup.target)
                    ||setup.target.level()!=setup.cat.level()||setup.cat.distanceToSqr(setup.target)>SorcererCat.RIVAL_RANGE*SorcererCat.RIVAL_RANGE) {
                cancelCasting(setup.cat);continue;
            }
            if(DomainFreeze.hold(setup.cat)||!CatDuel.tickActor(setup.cat))continue;
            boolean finished=setup.movement.tick();setup.publish();
            if(finished){
                s.positions.remove(setup.cat.getUUID(),setup);setup.movement.stop();
                if(setup.kind<0)setup.cat.publishCombat(-1);
                else if(setup.exploiting&&controlWindow(s,setup.target)<CatSpell.launchTick(setup.kind,setup.large)+10){
                    if(controlWindow(s,setup.target)>=42)startWhenReady(s,setup.cat,setup.target,PURPLE,false,false);
                    else{setup.cat.releaseCombatHover();setup.cat.publishCombat(-1);}
                }
                else startWhenReady(s,setup.cat,setup.target,setup.kind,setup.large,setup.responding);
            }
        }
        s.carving.reset((int)s.casts.values().stream().filter(c->c.amplified&&c.phase==FLIGHT).count());
        for(Cast c:List.copyOf(s.casts.values())){
            if(s.casts.get(c.session)!=c)continue;
            if(!able(c.cat)||c.cat.level()!=c.level){cancelAll(c.cat);continue;}
            if(DomainFreeze.hold(c.cat)||c.phase<=CHARGE&&!CatDuel.tickActor(c.cat))continue;
            if(c.revealAge<c.revealTicks){
                if(!(c.level.getEntity(c.target) instanceof LivingEntity target)||!c.cat.canFight(target)){cancel(s,c);continue;}
                c.revealAge++;c.cat.advanceFirstDomainReveal();
                face(c.cat,c.direction);c.cat.getNavigation().stop();
                if(c.hovering){c.cat.renewCombatHover();c.cat.setDeltaMovement(Vec3.ZERO);}
                else c.cat.setDeltaMovement(c.cat.getDeltaMovement().multiply(0,1,0));
                c.cat.publishCombat(CatCombatMoves.pack(c.pose,CatCombatMoves.DOMAIN_REVEAL,c.revealAge,SorcererCat.LEFT,0));
                continue;
            }
            // Keep the completed gesture on screen until CatDuel has shown both actors.
            if(c.duelDomain&&!c.domainReleased&&c.age>=CatSpell.launchTick(c.kind,c.large)){
                if(!(c.level.getEntity(c.target) instanceof LivingEntity target)||!c.cat.canFight(target)){cancel(s,c);continue;}
                c.cat.getNavigation().stop();c.cat.setDeltaMovement(Vec3.ZERO);if(c.hovering)c.cat.renewCombatHover();
                publish(c);continue;
            }
            c.age++;c.stage++;
            LivingEntity target=c.level.getEntity(c.target) instanceof LivingEntity e&&c.cat.canFight(e)?e:null;
            if(c.phase<=CHARGE){
                if(target==null&&c.scriptedDestination==null){cancel(s,c);continue;}
                boolean tracking=c.kind!=PURPLE&&!(c.kind==RED&&c.large&&c.age>=CatSpell.launchTick(c.kind,true)-6);
                if(c.kind==PURPLE && c.age>=CatSpell.launchTick(c.kind,c.large)-10) updatePurpleAim(c,target,false);
                if(tracking)c.direction=turn(c.direction,c.scriptedDestination!=null
                        ?c.scriptedDestination.subtract(c.cat.getEyePosition()).normalize():castAim(c,target),.24);
                face(c.cat,c.direction);c.previous=c.point;c.point=c.muzzle();c.origin=c.point;
                if(c.kind==PURPLE&&c.phase==PREPARE&&c.age>=CatSpell.prepareTicks(c.kind,c.large)){
                    c.phase=CHARGE;c.stage=0;c.send();
                }
                if(c.age>=CatSpell.launchTick(c.kind,c.large)&&(!c.duelDomain||c.domainReleased))launch(s,c,now);
            }else if(c.phase==FLIGHT){
                if(c.kind==BLUE)blue(c,target);
                else if(c.kind==FURNACE)furnace(c);
                else if(c.kind==RED||c.kind==PURPLE)shot(s,c);
                else if(c.kind==CLEAVE&&c.cleave!=null)c.cleave.tick();
            }
            if(!current(c))continue;
            if(c.busy()){
                c.cat.getNavigation().stop();
                if(c.hovering){c.cat.renewCombatHover();c.cat.setDeltaMovement(Vec3.ZERO);}
                else c.cat.setDeltaMovement(c.cat.getDeltaMovement().multiply(0,1,0));
                if(c.phase==FLIGHT&&c.kind==BLUE&&c.large&&target!=null)face(c.cat,aim(c.cat,target));
                publish(c);
            }else releasePose(c);
            if(c.phase==IMPACT&&c.stage>=afterglow(c)||c.kind>=DISMANTLE&&c.age>=CatSpell.poseTicks(c.kind,c.large)
                    &&(c.kind!=CLEAVE||c.cleave==null||c.cleave.finished()||c.stage>=80)){
                s.casts.remove(c.session,c);if(c.carver!=null)c.carver.close();if(c.chunks!=null)c.chunks.close();
                if(c.kind<DISMANTLE)c.snapshot(END).broadcast(c.level);
                releasePose(c);
            }else if(c.age%2==0)c.send();
        }
    }
    private static void launch(State s,Cast c,long now) {
        if(c.kind==PURPLE&&c.level.getEntity(c.target) instanceof LivingEntity target&&c.cat.canFight(target)) {
            updatePurpleAim(c,target,true);c.point=c.muzzle();face(c.cat,c.direction);
        }
        c.phase=FLIGHT;c.stage=0;c.previous=c.point;c.origin=c.point;
        if(CatSpell.domain(c.kind)){
            SorcererCat responder=able(c.rival)&&!c.rival.isMahoraga()&&c.rival.level()==c.level?c.rival:null;
            boolean counter=!c.soloDomain&&(c.duelDomain||c.responding||responder!=null&&c.cat.getRandom().nextBoolean());
            LivingEntity target=c.level.getEntity(c.target) instanceof LivingEntity e?e:null;
            int duration=dev.jujutsuvfx.core.DomainDuration.castTicks(
                    dev.jujutsuvfx.config.VfxConfig.domainSeconds(c.cat.isSukuna()?dev.jujutsuvfx.core.DomainVolume.SHRINE:dev.jujutsuvfx.core.DomainVolume.UNLIMITED_VOID),
                    c.soloDomain?300:100,c.soloDomain||c.duelDomain);
            DomainFreeze.openCat(c.cat,target,counter,c.session,duration);
            if(!c.duelDomain&&!CatDuel.manages(c.cat)&&target!=null&&c.cat.needsPlayerDomain())c.cat.markPlayerDomainUsed();
            // One coin toss per opening; a declined response receives the actual domain effects.
            if(!c.duelDomain&&!c.responding&&counter&&responder!=null)s.counters.add(new Counter(responder,c.cat,now+8));
        }else if(c.kind==DISMANTLE){dismantle(c);}
        else if(c.kind==CLEAVE){
            LivingEntity target=c.level.getEntity(c.target) instanceof LivingEntity e?e:null;
            if(target!=null){c.cleave=new CatCleave(c.cat,target,c.direction,c.large);c.cleave.tick();}
        }
        else {
            c.send();
            if(c.kind==FURNACE){
                var hit=LimitlessTechnique.trace(c.level,c.cat,c.cat.getEyePosition(),c.point);
                if(hit.hit()){c.point=hit.point();furnaceImpact(c);}
            }else if(c.kind==RED||c.kind==PURPLE)hitSweep(c,c.cat.getEyePosition(),c.point,c.radius,false);
        }
    }
    /** This bonus belongs to Gojo's ranged techniques, never fists, player casts or player targets. */
    static float gojoTechniqueDamage(LivingEntity owner,LivingEntity target,float base){
        return owner instanceof SorcererCat cat&&cat.isGojo()&&!(target instanceof Player)?base*1.20f:base;
    }
    private static void blue(Cast c,LivingEntity target) {
        if(c.scriptedDestination!=null&&!c.parked){
            Vec3 delta=c.scriptedDestination.subtract(c.point);c.previous=c.point;
            Vec3 end=c.point.add(delta.scale(Math.min(1,.65/Math.max(.001,delta.length()))));
            c.point=BlueTerrain.sweep(c.cat,c.point,end,LimitlessTechnique.blueCoreRadius(c.radius),()->current(c));
            if(c.point.distanceToSqr(c.scriptedDestination)<.04){c.point=c.scriptedDestination;c.parked=true;}
            c.direction=delta.normalize();
        }else if(!c.parked){
            if(c.large&&target!=null&&c.stage<60)c.direction=LimitlessTechnique.steerBlue(c.direction,aimFrom(c.point,target));
            double distance=target==null?100:c.point.distanceTo(target.getBoundingBox().getCenter());
            if(c.traveled>=(c.large?48:32)||distance<.8&&c.stage>3)c.parked=true;
            else{
                c.previous=c.point;
                double speed=LimitlessTechnique.blueSpeed(c.point.distanceTo(c.cat.getEyePosition()))/20;
                c.point=BlueTerrain.sweep(c.cat,c.point,c.point.add(c.direction.scale(speed)),LimitlessTechnique.blueCoreRadius(c.radius),()->current(c));
                double moved=c.previous.distanceTo(c.point);c.traveled+=moved;
                if(moved<.002&&++c.stalled>8)c.parked=true;else if(moved>=.002)c.stalled=0;
            }
        }
        int count=0;
        for(LivingEntity targetEntity:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(c.point,c.point).inflate(c.radius),c.cat::canFight)){
            if(++count>24||!current(c))break;
            Vec3 delta=c.point.subtract(targetEntity.getBoundingBox().getCenter());double distance=delta.length();
            if(distance>c.radius||!visible(c,targetEntity))continue;
            CatAdaptation.touch(targetEntity,CatAdaptation.Phenomenon.BLUE);
            float response=CatAdaptation.controlScale(targetEntity,CatAdaptation.Phenomenon.BLUE);
            if(response>0&&!DomainFreeze.hold(targetEntity)&&!CatDuel.pinned(targetEntity)){
                double strength=dev.jujutsuvfx.platform.Numbers.clamp((1-distance/c.radius)*.15,.018,.15)*response;
                targetEntity.setDeltaMovement(targetEntity.getDeltaMovement().scale(1-.15*response).add(delta.normalize().scale(strength)));
                targetEntity.hasImpulse=true;targetEntity.hurtMarked=true;
            }
            if(distance<LimitlessTechnique.blueCoreRadius(c.radius)+1.4&&c.age>=c.nextHit.getOrDefault(targetEntity.getUUID(),0)){
                c.nextHit.put(targetEntity.getUUID(),c.age+8);TechniqueDamageRules.hurt(targetEntity,damage(c),gojoTechniqueDamage(c.cat,targetEntity,6),TechniqueDamage.BLUE);
            }
        }
        if(c.age>=(c.scriptedDestination!=null?600:200))impact(c);
    }
    private static void shot(State state,Cast c) {
        boolean beam=c.kind==RED&&c.large;
        float range=c.amplified?300:beam?LimitlessTechnique.RED_BEAM_RANGE:LimitlessTechnique.shotRange(c.kind,c.large);
        if(c.chunks!=null)c.chunks.prepareBudgeted(c.point,c.direction,c.radius,Math.max(0,range-c.traveled));
        double step=Math.min((beam?LimitlessTechnique.RED_BEAM_SPEED:LimitlessTechnique.shotSpeed(c.kind))/20,Math.max(0,range-c.traveled));
        c.previous=c.point;
        if(step>.001){
            Vec3 end=c.point.add(c.direction.scale(step));
            c.point=c.amplified?c.carver.advance(c.cat,c.point,end,c.radius,state.carving,()->current(c))
                    :beam?BlueTerrain.beam(c.cat,c.point,end,c.radius,c.traveled,()->current(c))
                    :BlueTerrain.pierce(c.cat,c.point,end,c.radius,()->current(c));
            double moved=c.previous.distanceTo(c.point);c.traveled+=moved;
            c.stalled=c.amplified?(c.carver.waitingForChunks()?c.stalled+1:0):moved<.001?c.stalled+1:0;
        }
        if(c.kind==RED&&collideBlue(state,c,beam?c.origin:c.previous,c.point))return;
        hitSweep(c,beam?c.origin:c.previous,c.point,c.radius,beam);
        if(c.traveled>=range-.01&&c.fullAt<0)c.fullAt=c.stage;
        boolean blocked=c.amplified?c.carver.blocked()||c.stalled>200:c.stalled>30||c.stage>140;
        if(blocked||!beam&&c.fullAt>=0||beam&&c.fullAt>=0&&c.stage-c.fullAt>=LimitlessTechnique.RED_BEAM_HOLD_TICKS)impact(c);
    }
    private static void hitSweep(Cast c,Vec3 start,Vec3 end,float radius,boolean periodic) {
        TechniqueDamage technique=c.kind!=PURPLE?TechniqueDamage.RED:c.amplified?TechniqueDamage.PURPLE_AMPLIFIED
                :c.large?TechniqueDamage.PURPLE_LARGE:TechniqueDamage.PURPLE_SMALL;
        int count=0;
        for(LivingEntity target:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(radius),c.cat::canFight)){
            if(++count>48||!current(c))return;
            if(SweptSphere.distanceSquared(start,end,target.getBoundingBox())>radius*radius)continue;
            if(periodic?c.age<c.nextHit.getOrDefault(target.getUUID(),0):c.hit.contains(target.getUUID()))continue;
            c.hit.add(target.getUUID());c.nextHit.put(target.getUUID(),c.age+10);
            if(TechniqueDamageRules.hurt(target,damage(c),gojoTechniqueDamage(c.cat,target,c.kind==PURPLE?(c.amplified?400:200):periodic?18:70),technique)&&current(c)&&c.kind==RED)push(c,target,.60,.20);
        }
    }
    private static boolean collideBlue(State state,Cast red,Vec3 from,Vec3 to){
        Cast selected=null;double nearest=Double.POSITIVE_INFINITY;
        Vec3 step=to.subtract(from);double length=step.lengthSqr();
        for(Cast blue:state.casts.values()){
            if(blue.kind!=BLUE||blue.phase!=FLIGHT||blue.level!=red.level||!current(blue)
                    ||red.scriptedPartner!=0&&red.scriptedPartner!=blue.session
                    ||blue.scriptedDestination!=null&&red.scriptedPartner!=blue.session)continue;
            Vec3 offset=from.subtract(blue.point);double radius=LimitlessTechnique.blueCoreRadius(blue.radius)+red.radius;
            double t;
            if(offset.lengthSqr()<=radius*radius)t=0;
            else if(length<.000001)continue;
            else{double b=offset.dot(step),d=b*b-length*(offset.lengthSqr()-radius*radius);if(d<0)continue;
                t=(-b-Math.sqrt(d))/length;if(t<0||t>1)continue;}
            if(t<nearest){nearest=t;selected=blue;}
        }
        if(selected==null)return false;
        Vec3 contact=from.lerp(to,nearest);
        var blueSource=new dev.jujutsuvfx.network.UnlimitedPurple.Source(selected.cat.getUUID(),selected.session,
                selected.point,selected.direction,selected.point,selected.radius,LimitlessTechnique.blueCoreRadius(selected.radius),selected.age*.05f,false);
        var redSource=new dev.jujutsuvfx.network.UnlimitedPurple.Source(red.cat.getUUID(),red.session,
                contact,red.direction,red.large?red.origin:contact,red.radius,red.radius,red.age*.05f,red.large);
        UUID blast=UnlimitedPurpleBlast.triggerCat(red.cat,selected.point,blueSource,redSource);
        if(blast==null)return false;
        cancel(state,selected);cancel(state,red);if(red.onFusion!=null)red.onFusion.accept(blast);return true;
    }
    private static void furnace(Cast c) {
        c.previous=c.point;double step=Math.min(FurnaceFlight.SPEED/20,Math.max(0,FurnaceFlight.RANGE-c.traveled));
        Vec3 end=c.point.add(c.direction.scale(step));
        if(!LimitlessTechnique.loadedSegment(c.level,c.point,end)){impact(c);return;}
        var hit=LimitlessTechnique.trace(c.level,c.cat,c.point,end);
        c.point=hit.point();c.traveled+=c.previous.distanceTo(c.point);
        if(hit.hit()||c.traveled>=FurnaceFlight.RANGE-.01)furnaceImpact(c);
    }
    private static void furnaceImpact(Cast c) {
        impact(c);DamageSource damage=damage(c);
        for(LivingEntity target:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(c.point,c.point).inflate(c.radius),c.cat::canFight)){
            if(!current(c))return;
            double distance=Math.sqrt(SweptSphere.distanceSquared(c.point,c.point,target.getBoundingBox()));
            if(distance>c.radius||!visible(c,target))continue;
            if(TechniqueDamageRules.hurt(target,damage,60+140*(1-(float)distance/c.radius),TechniqueDamage.FURNACE))target.setSecondsOnFire(4);
        }
        SkillTerrain.furnace(c.cat,damage,c.point,c.radius,()->current(c));
    }
    private static void dismantle(Cast c) {
        Vec3 start=c.cat.getEyePosition();
        if(c.level.getEntity(c.target) instanceof LivingEntity aimed&&c.cat.canFight(aimed)){
            Vec3 actual=aimed.getBoundingBox().getCenter().subtract(start).normalize();
            if(actual.dot(c.chargeAxis)>.34)c.direction=actual;
        }
        Vec3 end=start.add(c.direction.scale(SukunaSlashSkills.REACH));int count=0;
        for(LivingEntity target:c.level.getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(1.3),c.cat::canFight)){
            if(count>=16||!current(c))break;
            if(SweptSphere.distanceSquared(start,end,target.getBoundingBox())>.55*.55)continue;
            Vec3 point=target.getBoundingBox().getCenter();
            if(c.level.clip(new ClipContext(start,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.cat)).getType()!=HitResult.Type.MISS)continue;
            TechniqueDamageRules.hurt(target,SukunaCleaveDamage.source(c.cat),SukunaSlashSkills.DISMANTLE_DAMAGE*1.18f,TechniqueDamage.DISMANTLE);
            {
                emitCut(c,point,dev.jujutsuvfx.platform.Numbers.clamp(Math.max(target.getBbHeight(),target.getBbWidth())*2.2f,3,8),true);count++;
            }
        }
        if(count==0)emitCut(c,start.add(c.direction.scale(6)),5,false);
    }
    private static void emitCut(Cast c,Vec3 point,float length,boolean hit) {
        int seed=c.cat.getRandom().nextInt();Vec3 right=FurnaceFlight.right(c.direction),up=right.cross(c.direction).normalize();
        SukunaCut packet=new SukunaCut(point,c.direction,SukunaSlashStyle.rotateStroke(seed,up,c.direction),length,0,hit,seed,SukunaSlashStyle.isCross(seed));
        for(ServerPlayer observer:c.level.players())if(observer.distanceToSqr(point)<128*128&&dev.jujutsuvfx.platform.PacketDistributor.hasChannel(observer.connection,SukunaCut.TYPE))PacketDistributor.sendToPlayer(observer,packet);
    }
    private static void impact(Cast c) { if(c.phase==IMPACT)return;c.phase=IMPACT;c.stage=0;c.previous=c.point;
        if(c.carver!=null)c.carver.close();if(c.chunks!=null)c.chunks.close();c.send(); }
    private static int afterglow(Cast c) { return c.kind==BLUE?16:c.kind==FURNACE?84:Math.round(LimitlessTechnique.afterglow(c.kind)*20); }
    private static boolean visible(Cast c,LivingEntity target) {
        return c.level.clip(new ClipContext(c.point,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.cat)).getType()==HitResult.Type.MISS;
    }
    private static boolean current(Cast c) {
        State s=STATES.get(c.cat.getServer());return !c.canceled&&s!=null&&s.casts.get(c.session)==c&&able(c.cat)&&c.cat.level()==c.level;
    }
    private static boolean able(SorcererCat cat) { return cat!=null&&cat.isAlive()&&!cat.isRemoved()&&!cat.isNoAi()&&!cat.isInSittingPose(); }
    private static Vec3 aim(SorcererCat cat,LivingEntity target) { return aimFrom(cat.getEyePosition(),target); }
    /** Refresh against the live target within 35 degrees of the original charge direction. */
    private static void updatePurpleAim(Cast cast,LivingEntity target,boolean firing) {
        Vec3 desired=target.getBoundingBox().getCenter().subtract(cast.cat.getEyePosition()).normalize();
        if(desired.lengthSqr()<.01||cast.chargeAxis.dot(desired)<Math.cos(Math.toRadians(35)))return;
        cast.direction=firing?desired:turn(cast.direction,desired,.32);
    }
    private static Vec3 castAim(Cast cast,LivingEntity target) {
        if(cast.kind>=DISMANTLE)return target.getBoundingBox().getCenter().subtract(cast.cat.getEyePosition()).normalize();
        if(cast.kind==BLUE)return aim(cast.cat,target);
        Vec3 source=cast.kind==PURPLE?cast.cat.getEyePosition():cast.point;
        double speed=cast.kind==FURNACE?FurnaceFlight.SPEED:cast.kind==RED&&cast.large?LimitlessTechnique.RED_BEAM_SPEED:LimitlessTechnique.shotSpeed(cast.kind);
        double lead=dev.jujutsuvfx.platform.Numbers.clamp(source.distanceTo(target.getBoundingBox().getCenter())/(speed/20),0,6);
        Vec3 velocity=target.getDeltaMovement();if(velocity.lengthSqr()>1)velocity=velocity.normalize();
        Vec3 desired=target.getBoundingBox().getCenter().add(velocity.scale(lead)).subtract(source).normalize();
        return desired.lengthSqr()<.01?cast.direction:desired;
    }
    private static Vec3 aimFrom(Vec3 point,LivingEntity target) {
        Vec3 velocity=target.getDeltaMovement();if(velocity.lengthSqr()>1)velocity=velocity.normalize();
        Vec3 direction=target.getBoundingBox().getCenter().add(velocity.scale(3)).subtract(point).normalize();
        return direction.lengthSqr()<.01?new Vec3(0,0,1):direction;
    }
    private static Vec3 turn(Vec3 old,Vec3 desired,double amount) { Vec3 next=old.lerp(desired,amount);return next.lengthSqr()<.01?desired:next.normalize(); }
    private static void face(SorcererCat cat,Vec3 direction) {
        float yaw=(float)(Math.atan2(-direction.x,direction.z)*180/Math.PI),pitch=(float)(-Math.asin(dev.jujutsuvfx.platform.Numbers.clamp(direction.y,-1,1))*180/Math.PI);
        cat.setYRot(Mth.approachDegrees(cat.getYRot(),yaw,18));cat.setXRot(Mth.approachDegrees(cat.getXRot(),pitch,12));
        cat.yHeadRot=cat.getYRot();cat.yBodyRot=cat.getYRot();cat.getNavigation().stop();
    }
    private static DamageSource damage(Cast c) {
        String id=c.kind==BLUE?"limitless_blue":c.kind==RED?"limitless_red":c.kind==PURPLE?"hollow_purple":"furnace";
        return new DamageSource(c.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(
                ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(JujutsuVfx.ID,id))),c.cat,c.cat);
    }
    private static void push(Cast c,LivingEntity target,double speed,double lift) {
        if(!target.isAlive()||DomainFreeze.hold(target)||CatDuel.pinned(target)||target instanceof SorcererCat cat&&cat.behavior()==SorcererCat.STILL)return;
        double resistance=1-dev.jujutsuvfx.platform.Numbers.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,1);
        if(c.kind==RED){CatAdaptation.touch(target,CatAdaptation.Phenomenon.RED);resistance*=CatAdaptation.controlScale(target,CatAdaptation.Phenomenon.RED);}
        if(resistance<=0)return;
        if(target instanceof SorcererCat guarding&&guarding.combat().guarding())resistance*=.18;
        Vec3 horizontal=new Vec3(c.direction.x,0,c.direction.z).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(horizontal.scale(speed*resistance)).add(0,lift*resistance,0));
        target.hurtMarked=true;target.hasImpulse=true;c.level.getChunkSource().broadcastAndSend(target,new ClientboundSetEntityMotionPacket(target));
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if(!(event.getEntity() instanceof ServerPlayer observer)||!(event.getTarget() instanceof SorcererCat))return;
        State s=STATES.get(observer.getServer());
        if(s!=null)for(Cast c:s.casts.values())if(c.level==observer.level()&&c.kind<DISMANTLE&&observer.distanceToSqr(c.origin)<160*160)c.snapshot(c.phase).send(observer);
        DomainFreeze.syncCats(observer);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        State state=STATES.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());if(state==null)return;
        for(Cast cast:List.copyOf(state.casts.values()))cancel(state,cast);
        for(Setup setup:List.copyOf(state.positions.values()))cancelCasting(setup.cat);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()); }
}
