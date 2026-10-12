package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.core.DuelProgress;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.entity.VfxEntities;
import dev.jujutsuvfx.network.DuelScene;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import dev.jujutsuvfx.platform.PacketDistributor;
import java.util.*;
import static dev.jujutsuvfx.network.DuelScene.*;

/** Authoritative, once-per-pair story beats. Between beats the existing combat AI owns the actors. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class CatDuel {
    private static final int RETREAT=-1,FIGHT=0,RISE=-2,STRUCTURE_APPROACH=-3,DOMAIN_APPROACH=-4,OPENING_WAIT=-5;
    private static final int OPENING_WAIT_TICKS=40;
    private static final int DOMAINS=4,SUMMON=8,SEVER=16,FINAL_PENDING=32,FINAL_DONE=64,STRUCTURES=128,RESCUE=256;
    private static final UUID ANY_OPPONENT=new UUID(0,0);
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    private static final ThreadLocal<LivingEntity> EXECUTION=new ThreadLocal<>();
    private static final class State {final Map<UUID,Duel> byCat=new HashMap<>();int serial=2000000;}
    private static final class Duel {
        final SorcererCat gojo,sukuna;final ServerLevel level;final int pose;
        SorcererCat mahoraga,combatOpponent;
        int phase,shot,blue,red,domain,counterDomain,sceneDuration,strikeFrame=-1;
        long lastTick=-1,started,approachAt,retryAt,poseReadyAt=-1,domainReleaseAt=-1,recoveryAfter;
        Vec3 anchor,gojoAnchor,sky,arena,openingRise,arrival,fallFrom;
        CatTactics.Maneuver movement,mahoragaMove;UUID blast;
        CatStructureClash structure;float progress;
        boolean soloScene,temporaryMahoraga,closing,finalRequested,finalStarted,openingGuard,severed,repairQueued,arrivalLanded,recoveryBegan;
        Duel(State state,SorcererCat gojo,SorcererCat sukuna){
            this.gojo=gojo;this.sukuna=sukuna;level=(ServerLevel)gojo.level();pose=++state.serial;
            anchor=sukuna.position();gojoAnchor=gojo.position();started=level.getServer().getTickCount();
            phase=gojo.duelPlayed(sukuna.getUUID(),false)||sukuna.duelPlayed(gojo.getUUID(),false)
                    ||played(STRUCTURES)||played(DOMAINS)||played(RESCUE)||played(SUMMON)||played(FINAL_PENDING)||played(FINAL_DONE)
                    ||Math.min(gojo.getHealth(),sukuna.getHealth())<=1.1f?FIGHT:OPENING_WAIT;
            finalRequested=played(FINAL_PENDING);
            // Migrate older fights without replaying a newly-added earlier scene after their domain/wheel.
            if(played(DOMAINS)||played(RESCUE)||played(SUMMON))remember(STRUCTURES);
            if(played(SUMMON))remember(RESCUE); // Old two-on-one saves never replay the rescue interlude.
            progress=DuelProgress.restored(Math.max(gojo.duelProgress(sukuna.getUUID()),sukuna.duelProgress(gojo.getUUID())),
                    played(STRUCTURES),played(DOMAINS),played(RESCUE),played(SUMMON),played(FINAL_DONE));
            gojo.rememberDuelProgress(sukuna.getUUID(),progress);sukuna.rememberDuelProgress(gojo.getUUID(),progress);
            if(!played(SUMMON))sukuna.attachWheel();
            else{
                mahoraga=level.getEntitiesOfClass(SorcererCat.class,gojo.getBoundingBox().inflate(128),
                        other->other.isMahoraga()&&other.isAlive()&&sukuna.getUUID().equals(other.summoner()))
                        .stream().findFirst().orElse(null);
                if(mahoraga!=null)state.byCat.put(mahoraga.getUUID(),this);
            }
        }
        boolean staged(){return phase!=FIGHT&&phase!=FINALE_COMBAT&&!closing;}
        boolean played(int bit){return gojo.duelScenePlayed(sukuna.getUUID(),bit)||sukuna.duelScenePlayed(gojo.getUUID(),bit);}
        void remember(int bit){gojo.rememberDuelScene(sukuna.getUUID(),bit);sukuna.rememberDuelScene(gojo.getUUID(),bit);}
    }
    private CatDuel(){}
    private static Duel duel(LivingEntity cat){
        if(cat==null||cat.level().isClientSide)return null;
        State s=STATES.get(cat.getServer());return s==null?null:s.byCat.get(cat.getUUID());
    }
    public static boolean manages(SorcererCat cat){Duel d=duel(cat);return d!=null&&!d.closing;}
    public static boolean meleeOnly(SorcererCat cat){Duel d=duel(cat);return d!=null&&d.phase==FINALE_COMBAT;}
    /** Only the authored arm-repair close-up deflects the two scene opponents' attacks. */
    public static boolean recoveryProtected(SorcererCat cat,DamageSource source){
        Duel d=duel(cat);if(d==null||d.closing||d.phase!=ARM_RECOVER||cat!=d.gojo)return false;
        var attacker=source.getEntity()!=null?source.getEntity():source.getDirectEntity();
        return attacker!=null&&(attacker==d.sukuna||attacker==d.mahoraga);
    }
    public static boolean delayRecovery(SorcererCat cat){
        Duel d=duel(cat);return d!=null&&cat==d.gojo&&(cat.getServer().getTickCount()<d.recoveryAfter||repairOnly(cat)&&d.phase!=ARM_RECOVER);
    }
    /** The formal two-on-one grants one completed arm repair; interrupted repairs may retry. */
    public static boolean repairOnly(SorcererCat cat){
        Duel d=duel(cat);return d!=null&&!d.closing&&cat==d.gojo&&d.played(SUMMON);
    }
    /** Shared by entity physics/AI and the caster's charge, so a frame cannot advance twice. */
    public static boolean tickActor(net.minecraft.world.entity.Entity entity){
        if(!(entity instanceof SorcererCat cat)||cat.level().isClientSide)return true;
        Duel d=duel(cat);
        boolean slow=dev.jujutsuvfx.config.VfxConfig.cinematic()&&d!=null&&reacts(d,cat);
        return cat.cinematicStep(cat.getServer().getTickCount(),slow);
    }
    public static boolean pinned(LivingEntity cat){
        if(cat instanceof SorcererCat caster&&GojoLastStand.active(caster))return true;
        Duel d=duel(cat);if(d==null||!d.staged()||cat instanceof SorcererCat actor&&reacts(d,actor))return false;
        return d.phase==OPENING_WAIT&&cat==d.gojo||cat==d.sukuna
                ||cat==d.mahoraga&&d.phase!=MAHORAGA_DESCENT&&d.phase!=MAHORAGA_STRIKE;
    }
    /** Only the exact scripted finishing hit bypasses the cats' defensive layers. */
    public static boolean finaleDamage(SorcererCat cat,DamageSource source){return EXECUTION.get()==cat;}
    public static float limitDamage(SorcererCat cat,DamageSource source,float amount){
        if(EXECUTION.get()==cat||!dev.jujutsuvfx.config.VfxConfig.cinematic())return amount;
        Duel d=duel(cat);if(d==null||d.closing||cat==d.mahoraga||d.played(FINAL_DONE))return amount;
        if(amount>0&&amount>=Math.max(0,cat.getHealth()-1)){
            // Latch the finale even if recovery subsequently heals the actor; never restart the scene.
            d.finalRequested=true;d.remember(FINAL_PENDING);cat.setHealth(1);cat.finaleWard(true);return 0;
        }
        return amount;
    }
    static boolean isFinale(SorcererCat owner){Duel d=duel(owner);return d!=null&&d.gojo==owner&&d.finalStarted&&!d.closing;}
    static boolean finishingHit(LivingEntity owner,LivingEntity target,DamageSource source){
        Duel d=duel(owner);if(d==null||!d.finalStarted||!(target instanceof SorcererCat cat)
                ||target!=d.sukuna&&!cat.isMahoraga())return false;
        EXECUTION.set(target);
        try{target.invulnerableTime=0;target.hurt(source,999);
            // Adaptation, absorption and armor must not turn the authored last hit into another round.
            if(target.isAlive()){target.setHealth(0);target.die(source);}return true;
        }finally{EXECUTION.remove();}
    }

    public static boolean control(SorcererCat cat){
        // A temporary guest is never allowed to survive a server reload as a permanent third fighter.
        if(cat.isMahoraga()&&cat.getPersistentData().getBoolean("JujutsuShadowGuest")&&!manages(cat)){
            cat.discard();return true;
        }
        if(GojoLastStand.control(cat))return true;
        if(!dev.jujutsuvfx.config.VfxConfig.cinematic()||!(cat.level() instanceof ServerLevel level)||!able(cat))return false;
        State state=STATES.computeIfAbsent(level.getServer(),key->new State());Duel d=state.byCat.get(cat.getUUID());
        if(d!=null&&d.phase==FINALE_COMBAT&&returnToArena(d,cat))return true;
        if(cat.isMahoraga()){
            if(d==null)return false;
            if(d.phase==RESCUE_EMERGE){publishShadow(d,(int)(level.getServer().getTickCount()-d.started),false);return true;}
            if(d.phase==RESCUE_EXIT){publishShadow(d,(int)(level.getServer().getTickCount()-d.started),true);return true;}
            if(d.phase==RESCUE_CUT){publishRescueBlade(d,(int)(level.getServer().getTickCount()-d.started));return true;}
            if(d.phase==MAHORAGA_DESCENT){publishArrival(d,(int)(level.getServer().getTickCount()-d.started));return true;}
            if(d.phase==MAHORAGA_STRIKE){publishMahoraga(d);return true;}
            if(reacts(d,cat))return false;
            if(d.staged()){
                cat.combat().reset();cat.getNavigation().stop();cat.renewCombatHover();cat.setDeltaMovement(Vec3.ZERO);return true;
            }return false;
        }
        if(cat.retaliationAttacker()!=null){if(d!=null)close(state,d,true);return false;}
        if(d==null&&(cat.tickCount%10==1||cat.getTarget() instanceof SorcererCat)){
            SorcererCat rival=level.getEntitiesOfClass(SorcererCat.class,cat.getBoundingBox().inflate(128),
                    other->other!=cat&&!other.isMahoraga()&&!GojoLastStand.active(other)&&able(other)&&other.retaliationAttacker()==null&&cat.isRival(other)&&cat.canFight(other)
                            &&cat.distanceToSqr(other)<=128*128&&!state.byCat.containsKey(other.getUUID()))
                    .stream().min(Comparator.comparingDouble(cat::distanceToSqr)).orElse(null);
            if(rival!=null&&!DomainFreeze.hold(cat)&&!DomainFreeze.hold(rival)){
                d=new Duel(state,cat.isSukuna()?rival:cat,cat.isSukuna()?cat:rival);
                state.byCat.put(d.gojo.getUUID(),d);state.byCat.put(d.sukuna.getUUID(),d);
                if(d.phase==OPENING_WAIT)resetActors(d);
                snapshot(d);
            }
        }
        if(d==null)return false;advance(state,d);if(d.closing)return false;
        if(d.staged()){
            SorcererCat partner=d.phase==MAHORAGA_STRIKE&&able(d.mahoraga)?d.mahoraga:d.sukuna;
            if(d.gojo.canFight(partner))d.gojo.setTarget(partner);
        }else chooseCombatOpponent(d);
        if(d.sukuna.canFight(d.gojo))d.sukuna.setTarget(d.gojo);
        if(!d.staged())return cat==d.gojo&&d.movement!=null;
        if(d.phase==OPENING_WAIT){
            cat.combat().cinematicWait(cat==d.gojo?d.sukuna:d.gojo,cat==d.gojo?d.gojoAnchor:d.anchor);
            return true;
        }
        if(d.phase==ARM_RECOVER&&cat==d.gojo)return false;
        if(reacts(d,cat))return false;
        if(d.phase==DOMAIN_APPROACH){
            if(cat==d.sukuna)holdDomainPosition(cat,d.anchor);
            else if(d.movement!=null)publishMovement(d);
            return true;
        }
        if(domainScene(d.phase)){
            holdDomainPosition(cat,cat==d.gojo?d.gojoAnchor:d.anchor);
            // Do not overwrite the caster's reveal or gesture with cinematicGuard.
            if(!CatSorcery.busy(cat)&&!DomainFreeze.hold(cat))cat.publishCombat(-1);
            return true;
        }
        if(d.phase==STRUCTURE_CLASH){
            cat.getNavigation().stop();cat.setDeltaMovement(Vec3.ZERO);
            cat.setPos(cat==d.gojo?d.gojoAnchor:d.anchor);cat.fallDistance=0;
            if(d.structure!=null)d.structure.pose(cat);
            return true;
        }
        if(cat==d.sukuna){
            if(d.phase==ARM_CUT){
                cat.getNavigation().stop();cat.setDeltaMovement(Vec3.ZERO);CatTactics.face(cat,d.gojo);
            }else if(d.phase==OPENING_GUARD||d.phase==OPENING_RETURN||d.phase==FINALE_RED||d.phase==FINALE_FUSION||d.phase==FINALE_BLAST)
                cat.combat().cinematicGuard(d.gojo,d.anchor);
            else cat.combat().cinematicWait(d.gojo,d.anchor);
            return true;
        }
        if(d.movement!=null)publishMovement(d);
        else if(d.phase==FINALE_BLUE||d.phase==FINALE_RED||d.phase==FINALE_FUSION||d.phase==FINALE_BLAST){
            cat.getNavigation().stop();cat.setDeltaMovement(Vec3.ZERO);
            if(d.phase!=FINALE_BLUE){cat.renewCombatHover();cat.setPos(d.gojoAnchor);}
        }else if(d.phase==WHEEL_SUMMON||d.phase==MAHORAGA_DESCENT||d.phase==MAHORAGA_STRIKE||d.phase==ARM_CUT){
            cat.getNavigation().stop();cat.setDeltaMovement(Vec3.ZERO);
            CatTactics.face(cat,d.phase==MAHORAGA_STRIKE&&d.mahoraga!=null?d.mahoraga:d.sukuna);
        }
        return true;
    }

    private static void advance(State state,Duel d){
        if(d.closing)return;long now=d.level.getServer().getTickCount();
        if(!able(d.gojo)||d.gojo.level()!=d.level||d.gojo.retaliationAttacker()!=null||d.sukuna.retaliationAttacker()!=null
                ||d.phase!=FINALE_BLAST&&(!able(d.sukuna)||d.sukuna.level()!=d.level)) {close(state,d,true);return;}
        if(d.lastTick==now)return;d.lastTick=now;updateProgress(d);int age=(int)(now-d.started);
        if(reacts(d,d.gojo))d.gojoAnchor=d.gojo.position();
        if(reacts(d,d.sukuna))d.anchor=d.sukuna.position();
        if(now%4==0)snapshot(d);
        if(domainScene(d.phase)){
            if(!reacts(d,d.gojo))holdDomainPosition(d.gojo,d.gojoAnchor);
            if(!reacts(d,d.sukuna))holdDomainPosition(d.sukuna,d.anchor);
        }
        if(d.phase==OPENING_WAIT){
            waitOpening(state,d,age);return;
        }
        if(d.phase==DOMAIN_APPROACH){
            holdDomainPosition(d.sukuna,d.anchor);
            if(d.movement!=null&&!d.movement.tick()){publishMovement(d);return;}
            if(d.movement!=null){d.movement.stop();d.movement=null;d.gojo.publishCombat(-1);}
            if(domainReady(d))startDomain(d);else{resume(d);d.retryAt=now+40;}
        }else if(d.phase==RETREAT||d.phase==RISE||d.phase==STRUCTURE_APPROACH){
            if(d.movement!=null&&!d.movement.tick()){publishMovement(d);return;}
            if(d.movement!=null){boolean ok=d.movement.arrived;d.movement.stop();d.movement=null;
                if(!ok){if(d.phase==STRUCTURE_APPROACH)startStructure(d);else{resume(d);d.retryAt=now+40;}return;}}
            if(d.phase==RETREAT){
                if(d.openingRise!=null){d.movement=new CatTactics.Maneuver(d.gojo,d.sukuna,d.openingRise,CatCombatMoves.AIR_RETREAT,36,true);d.openingRise=null;return;}
                d.shot=CatSorcery.startAmplified(d.gojo,d.sukuna);d.gojo.rememberDuel(d.sukuna.getUUID(),false);d.sukuna.rememberDuel(d.gojo.getUUID(),false);
                scene(d,OPENING_CHARGE,100);
            }else if(d.phase==STRUCTURE_APPROACH)startStructure(d);else startRed(d);
        }else if(d.phase==OPENING_CHARGE){
            if(CatSorcery.openingFused(d.gojo,d.shot)){d.openingGuard=true;scene(d,OPENING_GUARD,170);}
            else if(!CatSorcery.active(d.gojo,d.shot)||age>240)resume(d);
        }else if(d.phase==OPENING_GUARD){
            if(CatSorcery.openingPassed(d.gojo,d.shot)||!CatSorcery.active(d.gojo,d.shot)||age>150)resume(d);
        }else if(d.phase==OPENING_RETURN){if(age>=22)resume(d);
        }else if(d.phase==FIGHT){
            if(d.gojo.distanceToSqr(d.sukuna)>128*128){close(state,d,false);return;}
            if(DomainFreeze.hold(d.gojo)||DomainFreeze.hold(d.sukuna))return;
            if(d.movement!=null){if(d.movement.tick()){d.movement.stop();d.movement=null;d.gojo.publishCombat(-1);}else publishMovement(d);}
            if(d.played(SUMMON)&&d.gojo.brokenLeftArm()&&!d.finalRequested)d.repairQueued=true;
            if(d.repairQueued&&now>=d.recoveryAfter&&!d.finalRequested&&d.movement==null
                    &&d.gojo.combat().readyForRecoveryBeat()){
                if(d.gojo.brokenLeftArm()){
                    d.recoveryBegan=false;d.gojo.forceReverseRecovery();scene(d,ARM_RECOVER,180);return;
                }
                d.repairQueued=false;
            }
            if(now<d.retryAt)return;
            DuelProgress.Beat beat=DuelProgress.next(d.progress,d.played(STRUCTURES),d.played(DOMAINS),d.played(RESCUE),d.played(SUMMON),
                    d.finalRequested||Math.min(d.gojo.getHealth(),d.sukuna.getHealth())<=1.1f,d.played(FINAL_DONE));
            if(beat==DuelProgress.Beat.FINALE){
                beginFinale(d);
            }else if(beat==DuelProgress.Beat.STRUCTURE){
                d.remember(STRUCTURES);beginStructure(d);
            }else if(beat==DuelProgress.Beat.DOMAIN){
                d.soloScene=false;beginDomain(d);
            }else if(beat==DuelProgress.Beat.SOLO_DOMAIN){
                d.soloScene=true;beginDomain(d);
            }else if(beat==DuelProgress.Beat.WHEEL){
                d.anchor=d.sukuna.position();d.remember(SUMMON);d.arrivalLanded=false;
                d.arrival=arrival(d);scene(d,WHEEL_SUMMON,60);
            }else if(d.movement==null&&now>=d.approachAt&&d.gojo.distanceToSqr(combatOpponent(d))>52*52){
                d.approachAt=now+60;d.movement=approach(d);if(d.movement!=null){CatSorcery.cancelCasting(d.gojo);d.gojo.combat().reset();}
            }
        }else if(d.phase==STRUCTURE_CLASH){
            if(d.structure==null){resume(d);return;}
            d.structure.tick();
            if(d.structure.finished()||age>CatStructureClash.DURATION+20)resume(d);
        }else if(d.phase==DOMAIN_GOJO){
            if(CatSorcery.domainPoseReady(d.gojo,d.domain)){
                if(d.poseReadyAt<0)d.poseReadyAt=now;
                if(now-d.poseReadyAt>=12){
                    d.counterDomain=CatSorcery.startDuelDomain(d.sukuna,d.gojo);d.poseReadyAt=-1;
                    scene(d,DOMAIN_SUKUNA,80);
                }
            }else if(age>100){cancelDomainPrelude(d);resume(d);}
        }else if(d.phase==DOMAIN_SUKUNA){
            if(CatSorcery.domainPoseReady(d.sukuna,d.counterDomain)){
                if(d.poseReadyAt<0)d.poseReadyAt=now;
                if(now-d.poseReadyAt>=12&&d.domainReleaseAt<0){
                    d.domainReleaseAt=now;CatSorcery.releaseDuelDomain(d.gojo,d.domain);
                }
                if(d.domainReleaseAt>=0&&now-d.domainReleaseAt>=8)CatSorcery.releaseDuelDomain(d.sukuna,d.counterDomain);
            }
            if(DomainFreeze.catClashing(d.gojo,d.sukuna))scene(d,DOMAIN_CLASH,DomainFreeze.CAT_DUEL_CLASH_TICKS);
            else if(age>70){cancelDomainPrelude(d);resume(d);}
        }else if(d.phase==DOMAIN_CLASH){
            if(age>=DomainFreeze.CAT_DUEL_CLASH_TICKS)scene(d,DOMAIN_BREAK,40);
        }else if(d.phase==DOMAIN_BREAK){
            if(age>=40){DomainFreeze.resolveCatDuel(d.gojo,d.sukuna,false);scene(d,DOMAIN_SUKUNA_WIN,80);}
        }else if(d.phase==SOLO_GOJO){
            if(CatSorcery.domainPoseReady(d.gojo,d.domain)){
                if(d.poseReadyAt<0)d.poseReadyAt=now;
                if(now-d.poseReadyAt>=12)CatSorcery.releaseDuelDomain(d.gojo,d.domain);
            }
            if(DomainFreeze.catActive(d.gojo))scene(d,SOLO_HOLD,80);
            else if(age>120){cancelDomainPrelude(d);d.soloScene=false;resume(d);}
        }else if(d.phase==SOLO_HOLD){
            if(age>=80){d.arrival=rescueArrival(d);scene(d,RESCUE_WHEEL,60);}
        }else if(d.phase==RESCUE_WHEEL){
            if(age>=60){
                d.temporaryMahoraga=true;summon(state,d);
                if(d.mahoraga!=null){DomainFreeze.spareRescuer(d.gojo,d.domain,d.mahoraga);
                    scene(d,RESCUE_EMERGE,CatCombatMoves.duration(CatCombatMoves.MAHORAGA_ARRIVAL));publishShadow(d,0,false);}
                else finishRescue(state,d);
            }
        }else if(d.phase==RESCUE_EMERGE){
            if(!able(d.mahoraga)){finishRescue(state,d);return;}
            publishShadow(d,age,false);
            if(age>=CatCombatMoves.duration(CatCombatMoves.MAHORAGA_ARRIVAL))scene(d,RESCUE_CUT,34);
        }else if(d.phase==RESCUE_CUT){
            if(!able(d.mahoraga)){finishRescue(state,d);return;}
            publishRescueBlade(d,age);
            if(age==CatCombatMoves.contact(CatCombatMoves.BLADE_CHOP)){
                DomainFreeze.shatterCat(d.gojo,d.domain);
                d.mahoraga.playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.6f,.72f);
                Vec3 origin=d.mahoraga.getBoundingBox().getCenter();
                new CatCombatCue(d.mahoraga.getId(),d.gojo.getId(),CatCombatCue.BLADE,origin,
                        origin.add(d.mahoraga.getLookAngle().scale(16)).add(0,10,0),CatCombatMoves.BLADE_CHOP,8,d.pose).broadcast(d.level);
            }
            if(age>=34)scene(d,RESCUE_EXIT,CatCombatMoves.SHADOW_EXIT_TICKS);
        }else if(d.phase==RESCUE_EXIT){
            publishShadow(d,age,true);
            if(age>=CatCombatMoves.SHADOW_EXIT_TICKS)finishRescue(state,d);
        }else if(d.phase==DOMAIN_GOJO_WIN||d.phase==DOMAIN_SUKUNA_WIN){if(age>=(d.phase==DOMAIN_SUKUNA_WIN?82:102))resume(d);
        }else if(d.phase==WHEEL_SUMMON){
            if(age>=20)d.sukuna.detachWheel();
            if(age>=60){summon(state,d);scene(d,MAHORAGA_DESCENT,CatCombatMoves.duration(CatCombatMoves.MAHORAGA_ARRIVAL));publishArrival(d,0);}
        }else if(d.phase==MAHORAGA_DESCENT){
            if(!able(d.mahoraga)){resume(d);return;}
            d.mahoraga.setPos(d.arrival);d.mahoraga.fallDistance=0;
            publishArrival(d,age);
            if(age>=CatCombatMoves.ARRIVAL_LAND_TICK&&!d.arrivalLanded){
                d.arrivalLanded=true;arrivalImpact(d);d.mahoraga.setTarget(d.gojo);
            }
            if(age>=CatCombatMoves.duration(CatCombatMoves.MAHORAGA_ARRIVAL))beginMahoragaStrike(d);
        }else if(d.phase==MAHORAGA_STRIKE){
            tickMahoragaStrike(d,now,age);
        }else if(d.phase==ARM_RECOVER){
            if(d.gojo.combat().recoveryChannel())d.recoveryBegan=true;
            boolean repaired=!d.gojo.brokenLeftArm();
            if(repaired||(d.recoveryBegan||age>1)&&!d.gojo.combat().recoveryPending()||age>=180||d.finalRequested){
                d.gojo.combat().endRecoveryBeat();d.repairQueued=!repaired;
                d.recoveryAfter=now+(repaired?240:80);
                // Preserve the opponents' current moves/casts when returning to full speed.
                scene(d,FIGHT,0);
            }
        }else if(d.phase==ARM_CUT){resume(d);
        }else if(d.phase==FINALE_BLUE){
            if(CatSorcery.finaleBlueReady(d.gojo,d.blue)){
                CatSorcery.freeFinaleBlue(d.gojo,d.blue);releaseActors(d);scene(d,FINALE_COMBAT,100);
            }else if(age>100||!CatSorcery.active(d.gojo,d.blue)){abortFinale(d);}
        }else if(d.phase==FINALE_COMBAT){
            if(age>=100){
                resetActors(d);d.anchor=d.sukuna.position();
                Vec3 destination=finaleFlightPoint(d);
                if(destination!=null){
                    d.movement=new CatTactics.Maneuver(d.gojo,d.sukuna,destination,CatCombatMoves.AIR_RETREAT,32,true);
                    scene(d,RISE,32);
                }else{d.gojoAnchor=d.gojo.position();startRed(d);}
            }
        }else if(d.phase==FINALE_RED){
            if(age>100||!CatSorcery.active(d.gojo,d.red))abortFinale(d);
        }else if(d.phase==FINALE_FUSION){
            if(UnlimitedPurpleBlast.age(d.gojo,d.blast)>=UnlimitedPurpleBlast.CHARGE_TICKS)scene(d,FINALE_BLAST,100);
            else if(age>160||!UnlimitedPurpleBlast.active(d.gojo,d.blast))abortFinale(d);
        }else if(d.phase==FINALE_BLAST){
            if(!UnlimitedPurpleBlast.active(d.gojo,d.blast)||age>180){d.gojo.rememberDuel(d.sukuna.getUUID(),true);d.sukuna.rememberDuel(d.gojo.getUUID(),true);d.remember(FINAL_DONE);close(state,d,false);}
        }
    }

    /** Slow the existing fight; foreground casters and the shadow guest retain their authored timing. */
    private static boolean reacts(Duel d,SorcererCat cat){
        if(!d.staged()||cat==null||!able(cat))return false;
        return switch(d.phase){
            case WHEEL_SUMMON,MAHORAGA_DESCENT -> cat==d.gojo||cat==d.sukuna;
            case MAHORAGA_STRIKE,ARM_CUT -> cat==d.sukuna;
            case ARM_RECOVER -> cat==d.sukuna||cat==d.mahoraga;
            case RESCUE_WHEEL,RESCUE_EMERGE,RESCUE_CUT,RESCUE_EXIT -> cat==d.gojo||cat==d.sukuna;
            case FINALE_BLUE,RISE,FINALE_RED,FINALE_FUSION,FINALE_BLAST -> cat!=d.gojo;
            default -> false;
        };
    }
    private static boolean domainScene(int phase){return phase>=DOMAIN_GOJO&&phase<=DOMAIN_SUKUNA_WIN||phase==DOMAIN_BREAK||phase>=SOLO_GOJO&&phase<=RESCUE_EXIT;}
    private static boolean domainReady(Duel d){
        // Gojo's field is centred on Sukuna; leave room for both bodies and the rear shrine.
        return d.gojo.distanceToSqr(d.sukuna)<=14*14;
    }
    private static void holdDomainPosition(SorcererCat cat,Vec3 anchor){
        if(anchor==null)return;
        cat.getNavigation().stop();cat.renewCombatHover();cat.setDeltaMovement(Vec3.ZERO);cat.fallDistance=0;
        if(cat.position().distanceToSqr(anchor)>1e-8){cat.setPos(anchor);cat.hasImpulse=true;cat.hurtMarked=true;}
    }
    private static void beginDomain(Duel d){
        resetActors(d);d.anchor=d.sukuna.position();d.gojoAnchor=d.gojo.position();
        if(domainReady(d)){startDomain(d);return;}
        Vec3 away=CatTactics.horizontal(d.gojoAnchor.subtract(d.anchor));if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        for(int i=0;i<16;i++){
            Vec3 side=away.yRot((i%2==0?1:-1)*(i/2)*.25f).scale(12);
            Vec3 base=d.anchor.add(side),floor=CatArena.surface(d.level,base);
            Vec3 point=i<8&&floor!=null?floor.add(0,.15,0):new Vec3(base.x,d.anchor.y+3,base.z);
            if(point.distanceToSqr(d.anchor)>14*14||!CatTactics.empty(d.gojo,d.level,point)
                    ||!CatTactics.airRoute(d.gojo,d.level,d.gojo.position(),point))continue;
            d.movement=new CatTactics.Maneuver(d.gojo,d.sukuna,point,CatCombatMoves.AIR_RETREAT,50,true);
            scene(d,DOMAIN_APPROACH,50);return;
        }
        // No clear flight path: continue ordinary pursuit instead of opening outside the field.
        resume(d);d.retryAt=d.level.getServer().getTickCount()+40;CatArena.navigate(d.gojo,d.sukuna,1.3);
    }
    private static void startDomain(Duel d){
        if(!domainReady(d)){resume(d);return;}
        d.anchor=d.sukuna.position();d.gojoAnchor=d.gojo.position();
        holdDomainPosition(d.gojo,d.gojoAnchor);holdDomainPosition(d.sukuna,d.anchor);
        if(d.soloScene){
            DomainFreeze.cancelCat(d.gojo);DomainFreeze.cancelCat(d.sukuna);DomainFreeze.clearCatDuel(d.gojo,d.sukuna);
            d.domain=CatSorcery.startSoloDomain(d.gojo,d.sukuna);d.poseReadyAt=d.domainReleaseAt=-1;d.counterDomain=0;
            d.remember(RESCUE);scene(d,SOLO_GOJO,130);return;
        }
        DomainFreeze.prepareCatDuel(d.gojo,d.sukuna);d.domain=CatSorcery.startDuelDomain(d.gojo,d.sukuna);
        d.poseReadyAt=d.domainReleaseAt=-1;d.counterDomain=0;d.remember(DOMAINS);scene(d,DOMAIN_GOJO,110);
    }
    private static void updateProgress(Duel d){
        float next=d.finalStarted||d.played(FINAL_DONE)?1:DuelProgress.observe(d.progress,
                d.gojo.getHealth(),d.gojo.getMaxHealth(),d.sukuna.getHealth(),d.sukuna.getMaxHealth());
        if(next<=d.progress)return;
        d.progress=next;d.gojo.rememberDuelProgress(d.sukuna.getUUID(),next);d.sukuna.rememberDuelProgress(d.gojo.getUUID(),next);
    }
    private static void beginStructure(Duel d){
        resetActors(d);d.anchor=d.sukuna.position();d.gojoAnchor=d.gojo.position();
        Vec3 away=CatTactics.horizontal(d.gojoAnchor.subtract(d.anchor));if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        double gap=d.gojoAnchor.subtract(d.anchor).multiply(1,0,1).length();
        if(gap<14||gap>28)for(int i=0;i<12;i++){
            Vec3 base=d.anchor.add(away.yRot((i%2==0?1:-1)*(i/2)*.28f).scale(18));
            Vec3 floor=CatArena.surface(d.level,base);if(floor==null||!CatArena.walkable(d.gojo,d.level,floor))continue;
            Vec3 point=floor.add(0,.15,0);
            if(!CatTactics.airRoute(d.gojo,d.level,d.gojo.position(),point))continue;
            d.movement=new CatTactics.Maneuver(d.gojo,d.sukuna,point,CatCombatMoves.AIR_RETREAT,40,true);
            scene(d,STRUCTURE_APPROACH,40);return;
        }
        startStructure(d);
    }
    private static void startStructure(Duel d){
        d.anchor=d.sukuna.position();d.gojoAnchor=d.gojo.position();
        d.structure=CatStructureClash.begin(d.gojo,d.sukuna);
        scene(d,STRUCTURE_CLASH,CatStructureClash.DURATION);
        if(d.structure!=null){d.structure.pose(d.gojo);d.structure.pose(d.sukuna);}
    }
    private static boolean opponentAvailable(Duel d,SorcererCat candidate){
        return able(candidate)&&candidate.level()==d.level&&d.gojo.canFight(candidate)
                &&d.gojo.distanceToSqr(candidate)<=SorcererCat.RIVAL_RANGE*SorcererCat.RIVAL_RANGE;
    }
    private static SorcererCat combatOpponent(Duel d){
        return opponentAvailable(d,d.combatOpponent)?d.combatOpponent:d.sukuna;
    }
    private static SorcererCat nearestOpponent(Duel d){
        boolean sukuna=opponentAvailable(d,d.sukuna),mahoraga=opponentAvailable(d,d.mahoraga);
        if(!sukuna)return mahoraga?d.mahoraga:null;
        if(!mahoraga)return d.sukuna;
        double sukunaDistance=d.gojo.distanceTo(d.sukuna),mahoragaDistance=d.gojo.distanceTo(d.mahoraga);
        // Only keep the existing target for almost indistinguishable distances, without a timed lock.
        if(Math.abs(sukunaDistance-mahoragaDistance)<.20&&opponentAvailable(d,d.combatOpponent))return d.combatOpponent;
        return sukunaDistance<=mahoragaDistance?d.sukuna:d.mahoraga;
    }
    private static void chooseCombatOpponent(Duel d){
        SorcererCat gojo=d.gojo,nearest=nearestOpponent(d);
        if(d.movement!=null){
            // Break off an ordinary long approach if the other rival has already reached our flank.
            // Opening/finale moves are authored phases and never enter this method.
            if(nearest==null||nearest==d.combatOpponent||gojo.distanceToSqr(nearest)>8*8)return;
            d.movement.stop();d.movement=null;gojo.releaseCombatHover();gojo.publishCombat(-1);
        }
        // A committed hit or cast finishes normally; an idle/guarding actor can immediately face the nearer rival.
        if(!gojo.combat().canRetargetForDuel())return;
        d.combatOpponent=nearest;gojo.combat().retargetForDuel(nearest);
    }
    private static boolean returnToArena(Duel d,SorcererCat cat){
        if(d.arena==null||DomainFreeze.hold(cat)||CatCombatMoves.defense(cat.combatMove())||!cat.combat().canRetargetForDuel())return false;
        Vec3 offset=cat.position().subtract(d.arena).multiply(1,0,1);
        if(offset.lengthSqr()<=23*23)return false;
        // Keep the melee below the elevated Blue. Its height must not pull grounded cats skyward.
        Vec3 edge=d.arena.add(offset.normalize().scale(18));
        Vec3 floor=CatArena.surface(d.level,edge);
        if(floor!=null&&CatArena.walkable(cat,d.level,floor))cat.getNavigation().moveTo(floor.x,floor.y,floor.z,1.3);
        else cat.getNavigation().moveTo(d.arena.x,cat.getY(),d.arena.z,1.3);
        return true;
    }

    private static void cancelDomainPrelude(Duel d){
        CatSorcery.cancelSession(d.gojo,d.domain);CatSorcery.cancelSession(d.sukuna,d.counterDomain);
    }
    private static void beginMahoragaStrike(Duel d){
        if(!able(d.mahoraga)){resume(d);return;}
        resetActors(d);d.strikeFrame=-1;d.severed=false;
        Vec3 away=CatTactics.horizontal(d.mahoraga.position().subtract(d.gojo.position()));
        if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        double gap=CatSpacing.distance(d.mahoraga,d.gojo)+.3;
        for(int i=0;i<12;i++){
            Vec3 offset=away.yRot((i%2==0?1:-1)*(i/2)*.25f).scale(gap);
            Vec3 point=d.gojo.position().add(offset).add(0,.12,0);
            if(!CatTactics.empty(d.mahoraga,d.level,point)||!CatTactics.sight(d.mahoraga,d.level,point,d.gojo)
                    ||!CatTactics.airRoute(d.mahoraga,d.level,d.mahoraga.position(),point))continue;
            d.mahoragaMove=new CatTactics.Maneuver(d.mahoraga,d.gojo,point,CatCombatMoves.SPACE_DASH,64,true);break;
        }
        // If the straight approach is obstructed, normal navigation can still reach the exposed flank.
        scene(d,MAHORAGA_STRIKE,100);
    }
    private static void publishArrival(Duel d,int age){publishShadow(d,age,false);}
    private static void publishShadow(Duel d,int age,boolean exiting){
        SorcererCat cat=d.mahoraga;if(cat==null)return;
        cat.getNavigation().stop();cat.renewCombatHover();cat.setDeltaMovement(Vec3.ZERO);cat.setPos(d.arrival);
        CatTactics.face(cat,d.gojo);cat.fallDistance=0;
        cat.publishCombat(CatCombatMoves.pack(d.pose+(exiting?4:2),CatCombatMoves.MAHORAGA_ARRIVAL,
                Math.max(0,Math.min(49,age)),SorcererCat.RIGHT,exiting?CatCombatMoves.SHADOW_RETREAT:CatCombatMoves.SHADOW_EMERGE));
    }
    private static void publishRescueBlade(Duel d,int age){
        SorcererCat cat=d.mahoraga;if(cat==null)return;
        cat.getNavigation().stop();cat.renewCombatHover();cat.setDeltaMovement(Vec3.ZERO);CatTactics.face(cat,d.gojo);
        cat.publishCombat(CatCombatMoves.pack(d.pose+3,CatCombatMoves.BLADE_CHOP,Math.min(34,age),SorcererCat.RIGHT,SorcererCat.PLAIN));
    }
    private static void removeGuest(State state,Duel d){
        if(!d.temporaryMahoraga)return;
        if(d.mahoraga!=null){state.byCat.remove(d.mahoraga.getUUID(),d);d.mahoraga.discard();d.mahoraga=null;}
        d.temporaryMahoraga=false;d.combatOpponent=null;
    }
    private static void finishRescue(State state,Duel d){
        DomainFreeze.shatterCat(d.gojo,d.domain);removeGuest(state,d);
        d.soloScene=false;resume(d);d.retryAt=d.level.getServer().getTickCount()+60;
    }
    private static void arrivalImpact(Duel d){
        d.level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,d.arrival.x,d.arrival.y+.08,d.arrival.z,24,.80,.12,.80,.055);
        d.level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,d.arrival.x,d.arrival.y+.12,d.arrival.z,8,.58,.18,.58,.025);
        d.mahoraga.playSound(SoundEvents.IRON_GOLEM_STEP,.80f,.68f);
    }
    private static void publishMahoraga(Duel d){
        SorcererCat cat=d.mahoraga;if(cat==null)return;
        if(d.mahoragaMove!=null){
            cat.publishCombat(CatCombatMoves.pack(d.pose,CatCombatMoves.REPOSITION,d.mahoragaMove.ticks,SorcererCat.RIGHT,CatCombatMoves.SPACE_DASH));
        }else if(d.strikeFrame>=0){
            cat.renewCombatHover();cat.getNavigation().stop();cat.setDeltaMovement(Vec3.ZERO);
            cat.publishCombat(CatCombatMoves.pack(d.pose+1,CatCombatMoves.BLADE_CHOP,d.strikeFrame,SorcererCat.RIGHT,SorcererCat.PLAIN));
        }else cat.publishCombat(-1);
    }
    private static boolean bladeReach(Duel d){
        return d.mahoraga.distanceToSqr(d.gojo)<Math.pow(CatSpacing.distance(d.mahoraga,d.gojo)+1,2)
                &&d.mahoraga.getSensing().hasLineOfSight(d.gojo);
    }
    private static void tickMahoragaStrike(Duel d,long now,int age){
        if(!able(d.mahoraga)||age>98){resume(d);return;}
        CatTactics.face(d.mahoraga,d.gojo);
        if(d.mahoragaMove!=null){
            boolean done=d.mahoragaMove.tick();publishMahoraga(d);
            if(!done)return;
            d.mahoragaMove.stop();d.mahoragaMove=null;d.mahoraga.releaseCombatHover();
        }
        if(d.strikeFrame<0){
            if(!bladeReach(d)){
                d.mahoraga.releaseCombatHover();d.mahoraga.getNavigation().moveTo(d.gojo,1.55);return;
            }
            d.mahoraga.getNavigation().stop();d.strikeFrame=0;
        }
        d.strikeFrame++;publishMahoraga(d);
        int contact=CatCombatMoves.contact(CatCombatMoves.BLADE_CHOP);
        if(d.strikeFrame==contact-2){
            bladeCue(d,0);d.mahoraga.playSound(SoundEvents.PLAYER_ATTACK_SWEEP,.90f,.68f);
        }
        if(d.strikeFrame==contact&&bladeReach(d)){
            d.mahoraga.performCombatHit(d.level,d.gojo,SorcererCat.PLAIN,CatCombatMoves.BLADE_CHOP);
            bladeCue(d,8);d.mahoraga.playSound(SoundEvents.TRIDENT_HIT,.65f,.75f);
            if(!d.gojo.duelScenePlayed(ANY_OPPONENT,SEVER)){
                d.gojo.setBrokenLeftArm(true);d.gojo.rememberDuelScene(ANY_OPPONENT,SEVER);
                d.recoveryAfter=now+120;d.repairQueued=true;d.severed=true;
            }
        }
        if(d.strikeFrame>=CatCombatMoves.duration(CatCombatMoves.BLADE_CHOP)+8)resume(d);
    }
    private static void bladeCue(Duel d,int stroke){
        new CatCombatCue(d.mahoraga.getId(),d.gojo.getId(),CatCombatCue.BLADE,d.mahoraga.getBoundingBox().getCenter(),
                d.gojo.getBoundingBox().getCenter(),CatCombatMoves.BLADE_CHOP,stroke,d.mahoraga.getRandom().nextInt()).broadcast(d.level);
    }
    private static void beginFinale(Duel d){
        resetActors(d);d.finalRequested=d.finalStarted=true;d.anchor=d.sukuna.position();d.gojoAnchor=d.gojo.position();
        // Blue remains above the arena while five seconds of actual melee continue below it.
        d.arena=d.gojo.position().lerp(d.sukuna.position(),.4);
        Vec3 floor=CatArena.surface(d.level,d.arena);if(floor!=null)d.arena=floor;
        d.sky=d.arena.add(0,19,0);updateProgress(d);
        d.blue=CatSorcery.startFinaleBlue(d.gojo,d.sukuna,d.sky);scene(d,FINALE_BLUE,100);
    }
    private static Vec3 finaleFlightPoint(Duel d){
        Vec3 away=CatTactics.horizontal(d.sky.subtract(d.anchor));if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        for(int i=0;i<9;i++){
            Vec3 side=away.yRot((i%2==0?1:-1)*(i/2)*.30f).scale(7);
            Vec3 base=d.arena.add(side),floor=CatArena.surface(d.level,base);
            // The caster's altitude follows the ground, independently of the much higher fusion Blue.
            Vec3 point=new Vec3(base.x,(floor==null?d.arena.y:floor.y)+6,base.z);
            if(CatTactics.empty(d.gojo,d.level,point)&&CatTactics.airRoute(d.gojo,d.level,d.gojo.position(),point))return point;
        }
        return null;
    }
    private static void startRed(Duel d){
        d.gojoAnchor=d.gojo.position();scene(d,FINALE_RED,100);
        d.red=CatSorcery.startFinaleRed(d.gojo,d.sukuna,d.blue,id->{
            if(d.closing)return;d.blast=id;scene(d,FINALE_FUSION,128);
        });if(d.red==0)abortFinale(d);
    }
    private static void abortFinale(Duel d){
        CatSorcery.cancelSession(d.gojo,d.blue);CatSorcery.cancelSession(d.gojo,d.red);
        if(d.blast!=null)UnlimitedPurpleBlast.cancelCat(d.gojo,d.blast);
        d.blast=null;d.finalStarted=false;resume(d);d.retryAt=d.level.getServer().getTickCount()+40;
    }
    private static Vec3 rescueArrival(Duel d){
        Vec3 away=CatTactics.horizontal(d.sukuna.position().subtract(d.gojo.position()));
        if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        // The shadow is a suspended portal at Sukuna's height, not the terrain below his domain.
        for(int i=0;i<18;i++){
            Vec3 offset=away.yRot((i%2==0?1:-1)*(i/2)*.28f).scale(4+i%3);
            Vec3 point=new Vec3(d.sukuna.getX()+offset.x,d.sukuna.getY()+.12,d.sukuna.getZ()+offset.z);
            var box=new net.minecraft.world.phys.AABB(point.x-1,point.y,point.z-1,point.x+1,point.y+3.5,point.z+1);
            if(d.level.hasChunkAt(net.minecraft.core.BlockPos.containing(point))&&d.level.getWorldBorder().isWithinBounds(box)
                    &&d.level.noCollision(box)&&!d.level.containsAnyLiquid(box))return point;
        }
        return d.sukuna.position().add(away.scale(4)).add(0,.12,0);
    }
    private static Vec3 arrival(Duel d){
        Vec3 away=CatTactics.horizontal(d.sukuna.position().subtract(d.gojo.position()));if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        for(int i=0;i<12;i++){
            Vec3 p=CatArena.surface(d.level,d.sukuna.position().add(away.yRot((i-5)*.32f).scale(4+i%3)));
            if(p!=null&&CatArena.landing(d.sukuna,d.level,p))return p;
        }return d.sukuna.position().add(away.scale(4)).add(0,2,0);
    }
    private static void summon(State state,Duel d){
        SorcererCat cat=VfxEntities.MAHORAGA_CAT.get().create(d.level);if(cat==null)return;
        cat.setSummoner(d.sukuna.getUUID());cat.setBehavior(SorcererCat.WANDER);
        d.fallFrom=d.arrival;cat.setPos(d.arrival);cat.renewCombatHover();
        if(d.temporaryMahoraga)cat.getPersistentData().putBoolean("JujutsuShadowGuest",true);
        else cat.setTarget(d.gojo);
        if(d.level.addFreshEntity(cat)){d.mahoraga=cat;state.byCat.put(cat.getUUID(),d);}
    }
    private static void resetActors(Duel d){
        if(d.movement!=null){d.movement.stop();d.movement=null;}
        CatSorcery.cancelCasting(d.gojo);CatSorcery.cancelCasting(d.sukuna);d.gojo.combat().reset();d.sukuna.combat().reset();
        if(d.mahoraga!=null)d.mahoraga.combat().reset();
    }
    private static void releaseActors(Duel d){
        if(d.structure!=null){d.structure.cleanup();d.structure=null;}
        if(!reacts(d,d.sukuna)){d.sukuna.combat().releaseCinematicGuard();d.sukuna.releaseCombatHover();}
        if(!reacts(d,d.gojo)){d.gojo.combat().releaseCinematicGuard();d.gojo.releaseCombatHover();d.gojo.publishCombat(-1);}
        if(d.mahoragaMove!=null){d.mahoragaMove.stop();d.mahoragaMove=null;}
        if(d.mahoraga!=null){d.mahoraga.combat().releaseCinematicGuard();d.mahoraga.releaseCombatHover();d.mahoraga.publishCombat(-1);}
    }
    private static void resume(Duel d){DomainFreeze.clearCatDuel(d.gojo,d.sukuna);releaseActors(d);scene(d,FIGHT,0);}
    private static void scene(Duel d,int phase,int duration){
        boolean gojoSlow=reacts(d,d.gojo),sukunaSlow=reacts(d,d.sukuna);
        d.phase=phase;d.started=d.level.getServer().getTickCount();d.sceneDuration=duration;
        if(!gojoSlow&&reacts(d,d.gojo))d.gojo.releaseCombatHover();
        if(!sukunaSlow&&reacts(d,d.sukuna))d.sukuna.releaseCombatHover();
        snapshot(d);
    }
    private static void snapshot(Duel d){
        // No scene packet means no automatic camera claim or soundtrack during the lead-in pause.
        if(!d.closing&&d.phase==OPENING_WAIT)return;
        int phase=d.closing?NONE:(d.phase==RETREAT||d.phase==STRUCTURE_APPROACH||d.phase==DOMAIN_APPROACH)?APPROACH:d.phase==FIGHT||d.phase==RISE?BATTLE:d.phase;
        int age=Math.max(0,(int)(d.level.getServer().getTickCount()-d.started));
        int duration=phase==BATTLE?12000:phase==APPROACH?240:d.sceneDuration;
        if(phase==BATTLE)age=Math.min(age,11999);
        Vec3 focus=d.gojo.getBoundingBox().getCenter();double radius=2;
        if(phase>=OPENING_CHARGE&&phase<=OPENING_GUARD){focus=CatSorcery.point(d.gojo,d.shot,focus);radius=7;}
        else if(phase>=FINALE_BLUE&&phase<=FINALE_BLAST){focus=d.sky==null?focus:d.sky;radius=phase>=FINALE_FUSION?33:10;}
        else if(phase==MAHORAGA_DESCENT||phase==RESCUE_EMERGE||phase==RESCUE_EXIT){focus=d.arrival;radius=4;}
        else if(phase==MAHORAGA_STRIKE&&d.mahoraga!=null){focus=d.mahoraga.position().lerp(d.gojo.position(),.5).add(0,.8,0);radius=3;}
        else if(phase==STRUCTURE_CLASH){
            if(d.structure!=null){focus=d.structure.cameraFocus();radius=d.structure.cameraRadius();}
        }
        else if(domainScene(phase)){focus=DomainFreeze.catCenter(d.gojo,d.anchor.add(0,.025,0));radius=40;}
        DuelScene packet=new DuelScene(d.pose,d.gojo.getId(),d.sukuna.getId(),d.mahoraga==null?-1:d.mahoraga.getId(),
                phase,age,duration,focus,radius,
                phase==FINALE_FUSION?SLOW_MOTION:0);
        for(var player:d.level.players())if(Math.min(player.distanceToSqr(d.gojo),player.distanceToSqr(d.sukuna))<160*160&&dev.jujutsuvfx.platform.PacketDistributor.hasChannel(player.connection,DuelScene.TYPE))
            PacketDistributor.sendToPlayer(player,packet);
    }
    private static void waitOpening(State state,Duel d,int age){
        if(age<OPENING_WAIT_TICKS)return;
        if(d.gojo.distanceToSqr(d.sukuna)>128*128){close(state,d,false);return;}
        if(d.finalRequested){resume(d);return;}
        if(prepareOpening(d))scene(d,RETREAT,240);else resume(d);
    }
    private static boolean prepareOpening(Duel duel){
        Vec3 start=duel.gojo.position(),away=CatTactics.horizontal(start.subtract(duel.anchor));
        if(away.lengthSqr()<.001)away=new Vec3(1,0,0);
        boolean near=Math.hypot(start.x-duel.anchor.x,start.z-duel.anchor.z)<28;
        // Keep X/Z when already far apart. Only a close encounter adds a backward teleport.
        for(int i=0;i<(near?8:3);i++){
            Vec3 base=near?new Vec3(duel.anchor.x+away.yRot((i%2==0?1:-1)*(i/2)*.27f).x*(36+(i/4)*6),start.y,
                    duel.anchor.z+away.yRot((i%2==0?1:-1)*(i/2)*.27f).z*(36+(i/4)*6)):start;
            Vec3 rise=base.add(0,near?10:i==0?10:i==1?8:12,0);
            if(!CatTactics.empty(duel.gojo,duel.level,base)||!CatTactics.empty(duel.gojo,duel.level,rise)
                    ||!CatTactics.airRoute(duel.gojo,duel.level,base,rise))continue;
            if(near){
                boolean blink=duel.gojo.canCombatBlink();
                if(!blink&&!CatTactics.airRoute(duel.gojo,duel.level,start,base))continue;
                duel.openingRise=rise;duel.movement=new CatTactics.Maneuver(duel.gojo,duel.sukuna,base,
                        blink?CatCombatMoves.SPACE_BLINK:CatCombatMoves.AIR_RETREAT,blink?12:60,true).escaping();
            }else duel.movement=new CatTactics.Maneuver(duel.gojo,duel.sukuna,rise,CatCombatMoves.AIR_RETREAT,36,true);
            return true;
        }
        return false;
    }

    private static CatTactics.Maneuver approach(Duel duel){
        SorcererCat target=combatOpponent(duel);
        Vec3 away=CatTactics.horizontal(duel.gojo.position().subtract(target.position()));
        Vec3 best=null;double score=-Double.MAX_VALUE;
        for(int i=0;i<12;i++){
            Vec3 p=CatArena.surface(duel.level,target.position().add(away.yRot((i-5.5f)*.22f).scale(30)));
            if(p==null||!CatArena.landing(duel.gojo,duel.level,p)||!CatTactics.sight(duel.gojo,duel.level,p,target))continue;
            double q=CatArena.quality(duel.gojo,duel.level,p)-p.distanceTo(duel.gojo.position())*.1;
            if(q>score){score=q;best=p;}
        }
        if(best==null)return null;
        if(duel.gojo.canCombatBlink())return new CatTactics.Maneuver(duel.gojo,target,best,CatCombatMoves.SPACE_BLINK,16,false);
        Vec3 flight=best.add(0,.12,0);
        return CatTactics.airRoute(duel.gojo,duel.level,duel.gojo.position(),flight)
                ?new CatTactics.Maneuver(duel.gojo,target,flight,CatCombatMoves.AIR_RETREAT,60,true):null;
    }
    private static void publishMovement(Duel duel){
        CatTactics.Maneuver move=duel.movement;if(move==null)return;
        duel.gojo.publishCombat(CatCombatMoves.pack(duel.pose,CatCombatMoves.REPOSITION,move.ticks,move.hand,move.style));
    }
    private static boolean able(SorcererCat cat){
        return cat!=null&&cat.isAlive()&&!cat.isRemoved()&&!cat.isNoAi()&&!cat.isInSittingPose()
                &&cat.behavior()!=SorcererCat.STILL&&!cat.isTransforming()
                &&cat.level() instanceof ServerLevel level&&level.getEntity(cat.getUUID())==cat;
    }

    public static void cancelFor(SorcererCat cat){
        if(cat.getServer()==null)return;State state=STATES.get(cat.getServer());Duel d=state==null?null:state.byCat.get(cat.getUUID());
        if(d!=null)close(state,d,true);
    }
    private static final Map<MinecraftServer,Boolean> MODES=new WeakHashMap<>();
    public static void configurationChanged(MinecraftServer server){
        if(server==null)return;
        boolean enabled=dev.jujutsuvfx.config.VfxConfig.cinematic();
        Boolean previous=MODES.put(server,enabled);
        if(enabled||Boolean.FALSE.equals(previous))return;
        State state=STATES.get(server);
        if(state!=null)for(Duel d:new HashSet<>(state.byCat.values()))close(state,d,true);
        for(ServerLevel level:server.getAllLevels())for(var entity:level.getAllEntities())
            if(entity instanceof SorcererCat cat){cat.clearCatHostility();}
    }
    private static void close(State state,Duel d,boolean cancel){
        if(d.closing)return;d.closing=true;
        if(d.soloScene)DomainFreeze.shatterCat(d.gojo,d.domain);removeGuest(state,d);
        d.gojo.finaleWard(false);d.sukuna.finaleWard(false);
        state.byCat.remove(d.gojo.getUUID(),d);state.byCat.remove(d.sukuna.getUUID(),d);
        if(d.mahoraga!=null)state.byCat.remove(d.mahoraga.getUUID(),d);
        if(d.movement!=null)d.movement.stop();
        if(cancel){CatSorcery.cancelAll(d.gojo);CatSorcery.cancelAll(d.sukuna);
            CatSorcery.cancelSession(d.gojo,d.blue);CatSorcery.cancelSession(d.gojo,d.red);
            if(d.blast!=null)UnlimitedPurpleBlast.cancelCat(d.gojo,d.blast);}
        DomainFreeze.clearCatDuel(d.gojo,d.sukuna);releaseActors(d);scene(d,NONE,0);
    }
    @SubscribeEvent public static void tick(ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        configurationChanged(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        State state=STATES.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());if(state!=null)for(Duel d:new HashSet<>(state.byCat.values()))advance(state,d);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        State state=STATES.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());if(state!=null)for(Duel d:new HashSet<>(state.byCat.values()))close(state,d,true);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){STATES.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());}
}
