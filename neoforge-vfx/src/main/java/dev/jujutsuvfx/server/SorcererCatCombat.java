package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.UseAnim;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static dev.jujutsuvfx.core.CatCombatMoves.*;
import static dev.jujutsuvfx.entity.SorcererCat.*;

/** Server-owned duelling choreography. A prepared hand/energy is committed before its contact. */
public final class SorcererCatCombat {
    private record Plan(int move, int hand, int kind, int strikes, boolean kick) {}
    private final SorcererCat cat;
    private final CatPursuit pursuit;
    private int move, frame, sequence, hand = RIGHT, kind, remaining, cooldown = 8;
    private int mobilityCooldown, kickCooldown, clashCooldown, idleTicks, guardCooldown, airCooldown;
    private Vec3 divePoint = Vec3.ZERO, kickPrevious = Vec3.ZERO;
    private CatTactics.Maneuver arenaMove;
    private CatTactics.Maneuver recoveryMove, recoveryLanding;
    private long recoveryLandingAt;
    private long recoveryCheckAt, recoveryReadyAt, lastAttackAt=-1000;
    private int arenaCheck, guardQuiet, guardLastContact = -100, chainLinks, knockbackTicks;
    private long nextGuardCue;
    private long playerReadAt, playerHitAt=-1000, projectileThreatUntil=-1000, projectileScanAt;
    private UUID playerHitOwner;
    private boolean followKick, contacted, techniqueGuardChosen, recoveryRequested;
    private UUID opponent;
    private Plan pending;
    private Vec3 movementStart = Vec3.ZERO;

    public SorcererCatCombat(SorcererCat cat) { this.cat = cat; this.pursuit = new CatPursuit(cat); }
    /** Choose a nearer rival between attacks or while guarding, without resetting a committed hit. */
    public boolean canRetargetForDuel(){
        return !cat.level().isClientSide&&cat.isAlive()&&!cat.isNoAi()&&!cat.isTransforming()&&!cat.isInSittingPose()
                &&(move==IDLE||cat.isGojo()&&defense(move))&&!CatSorcery.busy(cat)&&arenaMove==null&&!recovering()&&!recoveryRequested
                &&knockbackTicks==0&&!cat.hasPendingLandingSmash()&&DomainFreeze.controlTicks(cat)==0
                &&LimitlessSkills.blueControlTicks(cat)==0&&!CatDuel.pinned(cat);
    }
    public void retargetForDuel(LivingEntity target){
        if(!canRetargetForDuel()||target!=null&&(!cat.canFight(target)||target.level()!=cat.level()
                ||cat.distanceToSqr(target)>SorcererCat.RIVAL_RANGE*SorcererCat.RIVAL_RANGE))return;
        UUID next=target==null?null:target.getUUID();
        // Repeated director updates preserve the prepared hand for an unchanged opponent; guard itself also remains intact.
        if(!java.util.Objects.equals(opponent,next)){opponent=next;pending=null;}
        if(cat.getTarget()!=target)cat.setTarget(target);
    }
    public void reset() {
        pursuit.reset();
        stopRecovery();
        if(CatSorcery.busy(cat))CatSorcery.cancelCasting(cat);
        if(arenaMove!=null){arenaMove.stop();arenaMove=null;}
        move = IDLE; frame = 0; remaining = 0; chainLinks = 0; pending = null; opponent = null; followKick = false;
        cooldown = Math.max(cooldown, 8);
        cat.releaseCombatHover(); cat.publishCombat(-1); cat.clearCombatEnergy();
    }
    public void tick() {
        if (!(cat.level() instanceof ServerLevel level)) return;
        if (!cat.isAlive() || cat.isNoAi() || cat.isTransforming() || cat.isInSittingPose() || cat.behavior() == STILL) { reset(); return; }
        if(cat.hasPendingLandingSmash()){cat.getNavigation().stop();cat.releaseCombatHover();return;}
        if(CatDuel.control(cat))return;
        var revenge=cat.retaliationAttacker();
        if(revenge!=null&&(cat.getTarget()!=revenge||opponent!=null&&!opponent.equals(revenge.getUUID()))){reset();cat.setTarget(revenge);}
        if(knockbackTicks>0){knockbackTicks--;cat.getNavigation().stop();cat.releaseCombatHover();return;}
        if(tickRequestedRecovery(level))return;
        if(CatSorcery.busy(cat))return;
        if(CatSorcery.tryPlayerDomain(cat))return;
        if(tickRecovery(level))return;
        if(arenaMove!=null){
            boolean done=arenaMove.tick();cat.publishCombat(CatCombatMoves.pack(sequence,REPOSITION,arenaMove.ticks,hand,arenaMove.style));
            if(done){boolean failed=arenaMove.failed;arenaMove.stop();arenaMove=null;move=IDLE;cat.publishCombat(-1);arenaCheck=cat.tickCount+(failed?60:30);
                if(cat.onGround())cat.releaseCombatHover();}
            return;
        }
        if (guardCooldown > 0) guardCooldown--;
        if (airCooldown > 0) airCooldown--;
        if (cooldown > 0) cooldown--;
        if (mobilityCooldown > 0) mobilityCooldown--;
        if (kickCooldown > 0) kickCooldown--;
        if (clashCooldown > 0) clashCooldown--;
        LivingEntity target = opponent == null ? cat.getTarget() : level.getEntity(opponent) instanceof LivingEntity living ? living : null;
        boolean valid = target != null && cat.canFight(target) && cat.distanceToSqr(target) < SorcererCat.RIVAL_RANGE * SorcererCat.RIVAL_RANGE;
        if(valid&&!cat.finaleWard()&&target instanceof Player player&&tryPlayerGuard(player))return;
        if(valid&&(move==IDLE||defense(move))&&CatSorcery.exploitControl(cat,target))return;
        if (defense(move)) {
            frame++; cat.getNavigation().stop();
            if (valid) face(target);
            if (cat.combatHovering()) { cat.renewCombatHover(); cat.setDeltaMovement(Vec3.ZERO); }
            else cat.setDeltaMovement(cat.getDeltaMovement().multiply(.25, 1, .25));
            boolean pressure=valid&&!DomainFreeze.hold(target)&&(target instanceof SorcererCat rival
                    ? CatSorcery.threatens(rival,cat)||inReach(target,KICK)&&cat.getSensing().hasLineOfSight(target)&&rival.combat().threatens(cat)
                    : target instanceof Player player?playerPressure(player)>0
                    : cat.distanceToSqr(target)<16&&frame-guardLastContact<12);
            // Holding a bow/skill indefinitely is a feint, not a reason to freeze in guard.
            if(target instanceof Player player&&frame>28&&frame-guardLastContact>22&&playerPressure(player)<=2)pressure=false;
            // A wall, a stalled cast, or an old threat must not suspend both actors in air forever.
            // Real repeated impacts and visible casts still renew a guard naturally.
            if(frame>45&&frame-guardLastContact>35&&(!valid||!cat.getSensing().hasLineOfSight(target)))pressure=false;
            if(frame>180&&frame-guardLastContact>50)pressure=false;
            guardQuiet=pressure?0:guardQuiet+1;
            if(!valid||frame>=6&&guardQuiet>=5+cat.getId()%4){
                finish(level,target,valid);cat.releaseCombatHover();guardCooldown=8+cat.getRandom().nextInt(13);
            }
            publish(); return;
        }
        if (move != IDLE && !defense(move) && move != KICK && frame > 3 && cat.tickCount % 4 == 0 && valid
                && CatSorcery.tryReact(cat, target)) return;
        if (move == RAPID) { barrage(level, target, valid); publish(); return; }
        if (move == AIR_CHASE || move == DIVE_KICK) { aerial(level, target, valid); publish(); return; }
        if (move == IDLE) {
            target = cat.getTarget(); valid = target != null && cat.canFight(target) && cat.distanceToSqr(target) < SorcererCat.RIVAL_RANGE * SorcererCat.RIVAL_RANGE;
            if(tryRecovery(level,valid?target:null))return;
            if (!valid) {
                pursuit.reset();
                pending = null; opponent = null;
                if (++idleTicks > 12) cat.clearCombatEnergy();
                return;
            }
            idleTicks = 0;
            if (!target.getUUID().equals(opponent)) { opponent = target.getUUID(); pending = null; }
            face(target);
            if(pursuit.needed(target)){
                arenaMove=pursuit.blink(level,target);
                if(arenaMove!=null){pending=null;startMove(REPOSITION);return;}
                pursuit.advance(level,target);
                return;
            }
            if(cat.tickCount>=arenaCheck){
                arenaCheck=cat.tickCount+30;arenaMove=CatArena.escape(cat,target);
                if(arenaMove!=null){pending=null;startMove(REPOSITION);return;}
            }
            if(kickCooldown==0&&inReach(target,KICK)&&cat.getSensing().hasLineOfSight(target)
                    &&cat.getRandom().nextFloat()<.20f){
                startPlan(new Plan(KICK,nextHand(hand),PLAIN,1,false));return;
            }
            if(CatSorcery.tryReact(cat,target))return;
            // A charging opponent is a threat for the entire cast, including its preparation.
            if (!cat.finaleWard() && target instanceof SorcererCat caster && CatSorcery.threatens(caster, cat)) {
                cat.getNavigation().stop();
                if (guardCooldown == 0) startGuard(target, 40);
                else {
                    Vec3 away = horizontal(cat.position().subtract(target.position())).scale(.5);
                    if (CatTactics.airRoute(cat, level, cat.position(), cat.position().add(away)))
                        motion(level, new Vec3(away.x, cat.getDeltaMovement().y, away.z));
                }
                return;
            }
            if (target instanceof SorcererCat rival && inReach(target, JAB) && cat.getSensing().hasLineOfSight(target) && rival.combat().threatens(cat) && guardCooldown == 0) {
                if (rival.combat().barraging() && cat.getRandom().nextFloat() < .52f) {
                    int selected = nextHand(hand), energy = cat.automaticEnergy() ? nextKind(false) : configuredKind(selected);
                    cat.prepareEnergy(selected, energy); startPlan(new Plan(RAPID, selected, energy, 1, false)); return;
                }
                if (cat.getRandom().nextFloat() < .55f) { startGuard(target, 26 + cat.getRandom().nextInt(14)); return; }
            }
            if(CatSorcery.tryStart(cat,target))return;
            if ((!cat.isMahoraga()||target.getY()-cat.getY()>1.4) && airCooldown == 0 && (target.getY() - cat.getY() > 1.4 || cat.combatHovering()
                    || cat.distanceToSqr(target) < 12 * 12 && cat.getRandom().nextFloat() < .045f)) {
                Vec3 rise = cat.position().add(0, 1.2, 0);
                CatTerrain.clearStep(cat,cat.position(),rise);
                if (CatTactics.airRoute(cat, level, cat.position(), rise)) {
                    pending = null; cat.renewCombatHover(); startMove(AIR_CHASE); airCooldown = 90;
                    movementStart = cat.position(); return;
                }
            }
            if (pending == null) prepareRound();
            double distance = cat.distanceTo(target);
            boolean threatened = target instanceof SorcererCat rival && (rival.combat().threatens(cat)||CatSorcery.threatens(rival,cat));
            if (!cat.isSukuna() && mobilityCooldown == 0 && (!cat.isGojo()||cat.canCombatBlink()) && cat.onGround() && cat.getSensing().hasLineOfSight(target)
                    && (distance > 3.4 && distance < 14 || cat.isGojo() && threatened && distance < 4)) {
                start(cat.isGojo() ? BLINK : DASH, pending.hand, pending.kind, 0, false);
                mobilityCooldown = cat.isSukuna() ? 42 + cat.getRandom().nextInt(18) : 120 + cat.getRandom().nextInt(60);
                return;
            }
            if (inReach(target, pending.move) && cat.getSensing().hasLineOfSight(target)) {
                cat.getNavigation().stop();
                if (cooldown == 0) start(PREPARE, pending.hand, pending.kind, 0, false);
            } else if(!pursuit.closeGap(level,target)&&cat.tickCount%8==0)
                CatArena.navigate(cat,target,cat.isSukuna()?1.3:1.16);
            return;
        }

        if (valid) face(target);
        cat.getNavigation().stop();
        frame++;
        if ((move == DASH || move == BLINK || move == PREPARE) && !valid) { reset(); return; }
        if (move == DASH && valid) dash(level, target);
        if (move == BLINK && frame == 4 && valid) blink(level, target);
        if(move==KICK&&valid&&frame>=2&&frame<=contact(KICK)+2&&!contacted){
            Vec3 aim=dev.jujutsuvfx.core.CatKickMotion.lead(target.position(),target.getDeltaMovement(),2);
            Vec3 towards=horizontal(aim.subtract(cat.position()));
            double gap=target instanceof SorcererCat?CatSpacing.distance(cat,target):1.65+(cat.getBbWidth()+target.getBbWidth())*.35;
            Vec3 goal=aim.subtract(towards.scale(gap)).add(0,frame<6?.30:0,0);
            Vec3 step=dev.jujutsuvfx.core.CatKickMotion.approach(cat.position(),goal,.68,.30);
            if(CatTactics.airRoute(cat,level,cat.position(),cat.position().add(step))){
                cat.renewCombatHover();motion(level,step);
            }
        }
        if(move==KICK&&frame==contact(KICK)+4){cat.releaseCombatHover();
            if(valid)motion(level,horizontal(cat.position().subtract(target.position())).scale(.30).add(0,.12,0));}
        if(blade(move)) {
            bladeSequence(level,target,valid);
            return;
        }
        int before = sequence;
        boolean kickWindow=move==KICK&&frame>=contact(KICK)-2&&frame<=contact(KICK)+3;
        boolean touching=valid&&(move==KICK?kickContact(target):inReach(target,move))&&cat.getSensing().hasLineOfSight(target);
        if (melee(move) && (move==KICK?kickWindow&&(touching||frame==contact(KICK)+3):frame==contact(move)) && !contacted) {
            contacted = true;
            if (touching) {
                if (tryClash(level, target)) return;
                boolean hit = cat.performCombatHit(level, target, kind, move);
                if (hit && move == KICK) cue(level, CatCombatCue.KICK, target,
                        cat.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(), CURSED, kind);
            }
            // Telegraph the next chain link during this strike's follow-through.
            if (followKick && kickCooldown == 0 && valid) {
                pending = new Plan(KICK, hand, PLAIN, 1, false);
            } else prepareRound();
        }
        kickPrevious=cat.position();
        if (before != sequence) return;
        if (frame >= duration(move)) {
            finish(valid ? target : null);
            return;
        }
        publish();
    }

    private void bladeSequence(ServerLevel level,LivingEntity target,boolean valid){
        for(int stroke=0;stroke<bladeStrokes(move);stroke++){
            int hitFrame=bladeContact(move,stroke);
            if(frame==hitFrame-2){
                Vec3 from=cat.getBoundingBox().getCenter();
                Vec3 to=valid?target.getBoundingBox().getCenter():from.add(cat.getLookAngle().scale(3));
                new CatCombatCue(cat.getId(),valid?target.getId():-1,CatCombatCue.BLADE,
                        from,to,move,stroke,cat.getRandom().nextInt()).broadcast(level);
                cat.playSound(SoundEvents.PLAYER_ATTACK_SWEEP,.90f,move==BLADE_CHOP?.68f:1.15f+stroke*.18f);
            }
            if(frame!=hitFrame)continue;
            contacted=stroke==bladeStrokes(move)-1;
            if(valid&&inReach(target,move)&&cat.getSensing().hasLineOfSight(target)){
                if(tryClash(level,target))return;
                if(cat.performCombatHit(level,target,PLAIN,move)){
                    cue(level,CatCombatCue.BLADE,target,cat.getBoundingBox().getCenter(),
                            target.getBoundingBox().getCenter(),move,stroke|8);
                    cat.playSound(SoundEvents.TRIDENT_HIT,.65f,move==BLADE_CHOP?.75f:1.05f);
                }
            }
            if(contacted)prepareRound();
        }
        if(frame>=duration(move)){finish(valid?target:null);return;}
        publish();
    }

    /** An urgent request still uses grounded, interruptible reverse cursed recovery. */
    public boolean forceReverseRecovery(){
        if(cat.level().isClientSide||cat.isMahoraga()||!cat.isAlive())return false;
        recoveryRequested=true;recoveryCheckAt=0;recoveryReadyAt=0;return true;
    }
    private boolean tickRequestedRecovery(ServerLevel level){
        if(!recoveryRequested||CatDuel.meleeOnly(cat)||CatDuel.delayRecovery(cat))return false;
        if(cat.getHealth()>=cat.getMaxHealth()&&!cat.brokenLeftArm()){recoveryRequested=false;return false;}
        if(DomainFreeze.controlTicks(cat)>0||LimitlessSkills.blueControlTicks(cat)>0)return false;
        if(recoveryLanding!=null){
            boolean done=recoveryLanding.tick();
            cat.publishCombat(CatCombatMoves.pack(sequence,REPOSITION,recoveryLanding.ticks,hand,AIR_RETREAT));
            if(done){recoveryLanding.stop();recoveryLanding=null;cat.releaseCombatHover();move=IDLE;cat.publishCombat(-1);}
            return true;
        }
        if(recovering())return false;
        // Recovery queues behind the current cast, including its release pose.
        if(CatSorcery.busy(cat))return false;
        LivingEntity target=cat.getTarget();
        if(!recoveryGround()||CatArena.hazardous(level,cat.position())){
            long now=level.getServer().getTickCount();
            if(target!=null&&now>=recoveryLandingAt){
                recoveryLandingAt=now+20;
                recoveryLanding=CatTactics.forRecoveryLanding(cat,target);
                if(recoveryLanding!=null){pending=null;start(REPOSITION,hand,AIR_RETREAT,0,false);return true;}
            }
            cat.releaseCombatHover();cat.getNavigation().stop();
            if(move!=IDLE){move=IDLE;cat.publishCombat(-1);}
            return true;
        }
        if(move!=IDLE){move=IDLE;frame=0;pending=null;cat.publishCombat(-1);}
        tryRecovery(level,target!=null&&cat.canFight(target)?target:null);return true;
    }
    public boolean readyForRecoveryBeat(){
        return !CatSorcery.busy(cat)&&arenaMove==null&&knockbackTicks==0&&!cat.hasPendingLandingSmash()
                &&(move==IDLE||defense(move)||recovering())&&DomainFreeze.controlTicks(cat)==0
                &&LimitlessSkills.blueControlTicks(cat)==0;
    }
    public boolean recoveryChannel(){return move==REVERSE_CURSED;}
    public boolean recoveryPending(){return recoveryRequested||recovering();}
    public void endRecoveryBeat(){recoveryRequested=false;stopRecovery();}
    private boolean kickContact(LivingEntity target){
        if(cat.position().distanceToSqr(kickPrevious)>16)return inReach(target,KICK);
        return dev.jujutsuvfx.core.CatKickMotion.touches(kickPrevious,cat.position(),cat.getBbHeight(),
                cat.getBbWidth(),cat.getLookAngle(),target.getBoundingBox());
    }
    private boolean recovering(){return move==REVERSE_CURSED||recoveryMove!=null||recoveryLanding!=null;}
    private boolean recoveryGround(){
        return cat.onGround()&&!cat.combatHovering()&&!cat.isNoGravity()&&!cat.isPassenger()
                &&!cat.isInWaterOrBubble()&&!cat.isInLava()&&!cat.isOnFire()
                &&DomainFreeze.controlTicks(cat)==0&&LimitlessSkills.blueControlTicks(cat)==0;
    }
    private boolean tryRecovery(ServerLevel level,LivingEntity target){
        if (cat.isMahoraga() || GojoLastStand.active(cat) || CatDuel.meleeOnly(cat) || CatDuel.delayRecovery(cat)) return false;
        long now=level.getServer().getTickCount();
        boolean repair=cat.isGojo()&&cat.brokenLeftArm();
        boolean urgent=recoveryRequested||repair;
        if(now<recoveryCheckAt||now<recoveryReadyAt&&!urgent||now-lastAttackAt<(urgent?8:16))return false;
        recoveryCheckAt=now+(urgent?12:30+cat.getRandom().nextInt(21));
        float missing=cat.getMaxHealth()-cat.getHealth();
        if(!repair&&missing<(target==null?1:40)||!recoveryGround()||CatArena.hazardous(level,cat.position()))return false;
        float urgency=1-cat.getHealth()/cat.getMaxHealth();
        if(!urgent&&target!=null&&cat.getRandom().nextFloat()>.38f+urgency*.42f)return false;
        CatTactics.Maneuver retreat=null;
        if(target!=null&&cat.distanceToSqr(target)<8*8){
            retreat=CatTactics.forRecovery(cat,target);
            if(retreat==null)return false;
        }
        pending=null;remaining=0;chainLinks=0;opponent=target==null?null:target.getUUID();
        cat.clearCombatEnergy();cat.releaseCombatHover();recoveryRequested=false;
        if(retreat!=null){
            recoveryMove=retreat;start(REPOSITION,hand,STEP_BACK,0,false);
        }else start(REVERSE_CURSED,hand,PLAIN,0,false);
        return true;
    }
    private boolean tickRecovery(ServerLevel level){
        if(!recovering())return false;
        if(CatDuel.repairOnly(cat)&&!cat.brokenLeftArm()||!recoveryGround()||cat.getHealth()>=cat.getMaxHealth()&&!cat.brokenLeftArm()
                ||cat.tickCount%10==0&&CatArena.hazardous(level,cat.position())){
            stopRecovery();return true;
        }
        if(recoveryMove!=null){
            LivingEntity target=recoveryMove.target;
            if(!target.isAlive()||target.level()!=level||!cat.canFight(target)){
                stopRecovery();return true;
            }
            boolean done=recoveryMove.tick();
            cat.publishCombat(CatCombatMoves.pack(sequence,REPOSITION,recoveryMove.ticks,hand,STEP_BACK));
            if(done){
                boolean ready=recoveryMove.arrived&&!recoveryMove.failed&&cat.distanceToSqr(target)>=5.5*5.5;
                recoveryMove.stop();
                // Keep the movement until cancellation has cleared the recovery state.
                if(!ready){stopRecovery();return true;}
                recoveryMove=null;start(REVERSE_CURSED,hand,PLAIN,0,false);
            }
            return true;
        }
        cat.getNavigation().stop();cat.releaseCombatHover();
        cat.setDeltaMovement(cat.getDeltaMovement().multiply(0,1,0));
        LivingEntity target=cat.getTarget();
        if(target!=null&&cat.canFight(target))face(target);
        // One authoritative server tick: 0.5 HP x 20 ticks = 10 HP per second.
        // The finite channel and cooldown leave openings instead of an endless heal loop.
        cat.heal(.5f);frame++;
        if(frame>=(CatDuel.repairOnly(cat)?20:40)&&cat.isGojo()&&cat.brokenLeftArm()){
            cat.regrowLeftArm();
            if(CatDuel.repairOnly(cat)){stopRecovery();return true;}
        }
        if(frame>=duration(REVERSE_CURSED)||cat.getHealth()>=cat.getMaxHealth()&&!cat.brokenLeftArm())stopRecovery();
        else publish();
        return true;
    }
    private void stopRecovery(){
        if(!recovering())return;
        if(recoveryMove!=null){recoveryMove.stop();recoveryMove=null;}
        if(recoveryLanding!=null){recoveryLanding.stop();recoveryLanding=null;}
        move=IDLE;frame=0;pending=null;remaining=0;chainLinks=0;followKick=false;
        cat.getNavigation().stop();cat.releaseCombatHover();cat.clearCombatEnergy();cat.publishCombat(-1);
        cooldown=Math.max(cooldown,8);
        if(cat.getServer()!=null)recoveryReadyAt=cat.getServer().getTickCount()+200+cat.getRandom().nextInt(81);
    }
    public void interruptRecovery(){cat.cancelLandingSmash();stopRecovery();}
    /** Even a hit rejected by passive Infinity interrupts recovery before damage is resolved. */
    public void attacked(){
        if(cat.getServer()==null)return;
        lastAttackAt=cat.getServer().getTickCount();
        if(!recovering())return;
        stopRecovery();
        // Do not immediately turn the interrupting melee hit into a reactive guard.
        guardCooldown=Math.max(guardCooldown,12);
    }

    private int nextHand(int previous) {
        if (cat.handUnavailable(RIGHT)) return LEFT;
        if (cat.handUnavailable(LEFT)) return RIGHT;
        return cat.getRandom().nextFloat() < .78f ? (previous == LEFT ? RIGHT : LEFT) : previous;
    }
    private int nextKind(boolean finisher) {
        if (cat.isMahoraga() || !cat.automaticEnergy()) return PLAIN; // Selected from this hand's manual channels below.
        float roll = cat.getRandom().nextFloat();
        if (cat.isSukuna()) return roll < .80f ? CURSED : BLACK_FLASH;
        if (roll < .28f) return CURSED;
        if (roll < .62f) return BLUE;
        if (roll < (finisher ? .88f : .85f)) return RED;
        return BLACK_FLASH;
    }
    private int configuredKind(int nextHand) {
        if (cat.isMahoraga() || cat.handUnavailable(nextHand)) return PLAIN;
        int[] available = new int[4]; int size = 0;
        for (int energy = BLUE; energy <= BLACK_FLASH; energy++)
            if ((cat.configuredHands(energy) & nextHand) != 0) available[size++] = energy;
        return size == 0 ? PLAIN : available[cat.getRandom().nextInt(size)];
    }
    private void prepareRound() {
        int nextHand = nextHand(hand), nextKind = cat.automaticEnergy() ? nextKind(true) : configuredKind(nextHand);
        float choice = cat.getRandom().nextFloat();
        int nextMove = kickCooldown==0&&choice>.82f?KICK:choice < .38f ? JAB : choice < .68f ? HOOK : RAPID;
        if (cat.isSukuna()) {
            nextMove = choice < .18f ? JAB : choice < .32f ? HOOK : choice < .47f ? ELBOW
                    : choice < .61f ? UPPERCUT : choice < .74f ? DOUBLE_PALM : choice < .86f ? SWEEP
                    : kickCooldown == 0 && choice > .94f ? KICK : RAPID;
        }
        if(cat.isMahoraga()) {
            nextMove=choice<.24f?BLADE_SWEEP:choice<.43f?BLADE_RISE:choice<.61f?BLADE_CHOP:
                    choice<.80f?BLADE_CHAIN:choice<.88f?ELBOW:choice<.94f?UPPERCUT:SWEEP;
            if(blade(nextMove))nextHand=RIGHT;
        }
        if(cat.handUnavailable(LEFT)&&cat.handUnavailable(RIGHT))nextMove=KICK;
        if(nextMove==DOUBLE_PALM&&(cat.handUnavailable(LEFT)||cat.handUnavailable(RIGHT)))nextMove=HOOK;
        int strikes = 1;
        boolean kick = nextMove != RAPID && kickCooldown == 0 && cat.getRandom().nextFloat() < .30f;
        int energy=cat.isMahoraga()?PLAIN:nextMove==KICK?CURSED:nextKind;
        pending = new Plan(nextMove, nextHand, energy, strikes, kick);
        cat.prepareEnergy(nextHand, energy);
    }
    private void startPlan(Plan plan) {
        pending = null;
        chainLinks++;
        // The preview is immutable once committed, including in fixed-energy mode.
        start(plan.move, plan.hand, plan.kind, plan.strikes, plan.kick);
        if (plan.move == KICK) kickCooldown = 55;
    }
    private void start(int action, int selectedHand, int energy, int strikes, boolean kick) {
        if(melee(action)&&action!=KICK&&action!=SWEEP&&action!=DIVE_KICK&&cat.handUnavailable(selectedHand)) {
            selectedHand=nextHand(selectedHand);
            if(cat.handUnavailable(selectedHand)){action=KICK;energy=PLAIN;}
        }
        move = action; frame = 0; hand = selectedHand; kind = energy;
        remaining = strikes; followKick = kick; contacted = false;
        sequence = (sequence + 1) & 0x7fffffff;
        movementStart = cat.position();kickPrevious=cat.position();
        cat.getNavigation().stop(); publish();
        if (melee(move)&&!blade(move)) cat.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, move == RAPID ? .42f : .65f,
                move == RAPID ? 1.5f : move == KICK ? .8f : 1.05f);
    }
    private void finish(LivingEntity target) {
        int ended = move;
        if (ended == PREPARE && target != null && pending != null && inReach(target, pending.move)) {
            startPlan(pending); return;
        }
        if (melee(ended) && target != null && pending != null
                && (pending.move == KICK || chainLinks<3 && cat.getRandom().nextFloat()<.72f)
                && inReach(target, pending.move)) {
            startPlan(pending); return;
        }
        move = IDLE; frame = 0; chainLinks=0; cat.publishCombat(-1);
        if (target == null) { pending = null; opponent = null; cat.clearCombatEnergy(); return; }
        if (pending != null && pending.move == KICK) prepareRound();
        if (ended == CLASH) { pending = null; prepareRound(); }
        if (ended == DASH || ended == BLINK) cooldown = 0;
        else cooldown = 2 + cat.getRandom().nextInt(4);
    }
    private void startMove(int action) { start(action, hand, kind, 1, false); }
    private void finish(ServerLevel level, LivingEntity target, boolean valid) { finish(valid ? target : null); }
    private void publish() { cat.publishCombat(move == IDLE ? -1 : CatCombatMoves.pack(sequence, move, Math.min(frame,250), hand, kind)); }
    private void face(LivingEntity target) {
        cat.getLookControl().setLookAt(target, 55, 45);
        Vec3 direction = target.position().subtract(cat.position());
        float yaw = (float) (Math.atan2(-direction.x, direction.z) * 180 / Math.PI);
        cat.setYRot(Mth.approachDegrees(cat.getYRot(), yaw, 35));
        cat.yBodyRot = Mth.approachDegrees(cat.yBodyRot, yaw, 30);
    }
    private boolean inReach(LivingEntity target, int action) {
        double reach = (action == KICK || action == SWEEP ? 2.15 : 1.65) + (cat.getBbWidth() + target.getBbWidth()) * .5;
        if(target instanceof SorcererCat)reach=Math.max(reach,CatSpacing.distance(cat,target)+.50);
        if(cat.isMahoraga())reach+=blade(action)?1.05:.65;
        return cat.distanceToSqr(target) <= reach * reach;
    }
    public boolean threatens(SorcererCat target) {
        return !DomainFreeze.hold(cat) && opponent != null && opponent.equals(target.getUUID()) && melee(move) && (move == RAPID || !contacted) && frame >= 1;
    }
    public boolean guarding() { return defense(move) && frame >= 2; }
    private boolean canReadPlayer(){
        return cat.isAlive()&&!cat.isNoAi()&&!cat.isInSittingPose()&&!CatSorcery.busy(cat)
                &&!CatDuel.pinned(cat)&&DomainFreeze.controlTicks(cat)==0&&guardCooldown==0&&!defense(move)
                &&(move==IDLE||move==PREPARE||melee(move)&&move!=RAPID&&contacted||move==RAPID&&frame>=12);
    }
    private int playerPressure(Player player){
        if(!cat.canFight(player)||DomainFreeze.controlTicks(player)>0)return 0;
        long now=cat.getServer().getTickCount();
        if(now>=projectileScanAt&&cat.level() instanceof ServerLevel level){
            projectileScanAt=now+4;int scanned=0;
            for(Projectile shot:level.getEntitiesOfClass(Projectile.class,cat.getBoundingBox().inflate(12),
                    p->p.isAlive()&&(p.getOwner()==player||p.getOwner()==null))){
                if(++scanned>32)break;
                Vec3 velocity=shot.getDeltaMovement();
                if(velocity.lengthSqr()>.01&&dev.jujutsuvfx.core.SweptSphere.distanceSquared(shot.position(),
                        shot.position().add(velocity.scale(4)),cat.getBoundingBox().inflate(.4))<.16){projectileThreatUntil=now+6;break;}
            }
        }
        if(now<projectileThreatUntil)return 3;
        if(player.getUUID().equals(playerHitOwner)&&now-playerHitAt<12&&cat.distanceToSqr(player)<36)return 2;
        Vec3 toward=cat.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        if(player.getLookAngle().dot(toward)<.48||!cat.getSensing().hasLineOfSight(player))return 0;
        double distance=cat.distanceToSqr(player);
        if(player.isUsingItem()&&distance<52*52){
            var stack=player.getUseItem();UseAnim animation=stack.getUseAnimation();
            if(animation==UseAnim.BOW||animation==UseAnim.CROSSBOW||animation==UseAnim.SPEAR
                    ||stack.getItem() instanceof dev.jujutsuvfx.item.SkillItem
                    ||stack.getItem() instanceof dev.jujutsuvfx.item.SukunaSlashItem)return 2;
        }
        double reach=player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE)+.6;
        if(cat.getBoundingBox().distanceToSqr(player.getEyePosition())>reach*reach)return 0;
        if(player.swinging&&player.swingTime<5)return 3;
        return player.getAttackStrengthScale(.5f)>.7f&&player.getDeltaMovement().dot(toward)>.045?1:0;
    }
    private boolean tryPlayerGuard(Player player){
        if(!canReadPlayer())return false;
        long now=cat.getServer().getTickCount();if(now<playerReadAt)return false;
        int pressure=playerPressure(player);if(pressure==0)return false;
        playerReadAt=now+8+cat.getRandom().nextInt(7);
        float chance=pressure==3?.78f:pressure==2?.64f:.45f;
        return cat.getRandom().nextFloat()<chance&&startGuard(player,0);
    }
    public void reactToPlayerHit(net.minecraft.world.damagesource.DamageSource source){
        if(!(source.getEntity() instanceof Player player)||!cat.canFight(player)
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        playerHitOwner=player.getUUID();playerHitAt=cat.getServer().getTickCount();
        if(!canReadPlayer()||playerHitAt<playerReadAt)return;
        playerReadAt=playerHitAt+10+cat.getRandom().nextInt(7);
        if(cat.getRandom().nextFloat()<.60f&&startGuard(player,0,GojoInfinity.technique(source))){
            // The impact activates the ward immediately; the visible arms still blend into place.
            frame=2;publish();
        }
    }
    public boolean infinityBlocks(net.minecraft.world.damagesource.DamageSource source){
        if (!cat.isGojo() || GojoInfinity.shrine(source) || CatAdaptation.piercesInfinity(source.getEntity())) return false;
        // One choice for the whole stance, never a fresh coin toss per damage tick.
        if (GojoInfinity.technique(source) && guarding() && !techniqueGuardChosen) {
            techniqueGuardChosen=true;
            int selected=cat.getRandom().nextBoolean()?INFINITY:GUARD;
            if (selected!=move) { move=selected; sequence=(sequence+1)&0x7fffffff; frame=2; publish(); }
        }
        return (GojoInfinity.projectile(source)&&!GojoInfinity.technique(source)||move==INFINITY&&guarding())
                &&!source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                &&!source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }
    public void stagger(int ticks){reset();knockbackTicks=Math.max(knockbackTicks,ticks);}
    public boolean barraging() { return move == RAPID && frame >= 3 && frame < duration(RAPID) - 3; }
    public boolean blocks(net.minecraft.world.damagesource.DamageSource source) {
        if(infinityBlocks(source))return true;
        if (move != GUARD || !guarding() || cat.isGojo() && GojoInfinity.shrine(source) || source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) return false;
        var attacker = source.getEntity();
        if (attacker == null || attacker == cat) return false;
        Vec3 towards = attacker.getBoundingBox().getCenter().subtract(cat.getBoundingBox().getCenter()).normalize();
        return cat.getLookAngle().dot(towards) > -.25;
    }
    public boolean startGuard(LivingEntity target, int ignoredLegacyDuration) {
        boolean technique=target instanceof SorcererCat caster&&CatSorcery.threatens(caster,cat)
                ||target instanceof Player player&&player.isUsingItem()
                    &&(player.getUseItem().getItem() instanceof dev.jujutsuvfx.item.SkillItem
                        ||player.getUseItem().getItem() instanceof dev.jujutsuvfx.item.SukunaSlashItem);
        return startGuard(target,ignoredLegacyDuration,technique);
    }
    private boolean startGuard(LivingEntity target, int ignoredLegacyDuration, boolean technique) {
        if(defense(move))return true;
        if (guardCooldown > 0 || target == null || !cat.canFight(target) || CatSorcery.busy(cat)) return false;
        stopRecovery();
        pending = null; opponent = target.getUUID(); cat.clearCombatEnergy();
        guardQuiet=0;guardLastContact=-100;techniqueGuardChosen=technique;
        startMove(cat.isGojo()&&(!technique||cat.getRandom().nextBoolean())?INFINITY:GUARD);guardCooldown=0;
        if (cat.combatHovering()) cat.renewCombatHover(); face(target); return true;
    }
    void cinematicGuard(LivingEntity target,Vec3 anchor){
        stopRecovery();
        if(!defense(move)){pending=null;opponent=target.getUUID();techniqueGuardChosen=true;startMove(cat.isGojo()&&cat.getRandom().nextBoolean()?INFINITY:GUARD);frame=3;}
        frame++;guardQuiet=0;cat.renewCombatHover();cat.getNavigation().stop();
        cat.setPos(anchor.x,anchor.y,anchor.z);cat.setDeltaMovement(Vec3.ZERO);cat.fallDistance=0;
        CatTactics.face(cat,target);publish();
    }
    void cinematicWait(LivingEntity target,Vec3 anchor){
        if(move!=IDLE)reset();
        cat.renewCombatHover();cat.getNavigation().stop();cat.setPos(anchor);cat.setDeltaMovement(Vec3.ZERO);cat.fallDistance=0;
        CatTactics.face(cat,target);cat.publishCombat(-1);
    }
    void releaseCinematicGuard(){if(defense(move)){guardCooldown=12;finish(null);}cat.releaseCombatHover();}
    public void blockCue(net.minecraft.world.damagesource.DamageSource source) {
        // Fire blocks call hurt every tick, even when Infinity rejects the damage. They must
        // not continually reset guardQuiet/guardLastContact and prevent the existing escape AI.
        if(GojoInfinity.ambientFire(source))return;
        Entity attacker=source.getEntity()!=null?source.getEntity():source.getDirectEntity();
        Vec3 point=source.getSourcePosition();
        if(point==null)point=cat.getBoundingBox().getCenter().add(cat.getLookAngle());
        boolean infinity=infinityBlocks(source);
        if(infinity)CatAdaptation.infinityContact(attacker);
        emitBlock(attacker,point,infinity);
    }
    public void projectileBlocked(Projectile projectile,Vec3 point){
        CatAdaptation.infinityContact(projectile.getOwner());
        emitBlock(projectile.getOwner()!=null?projectile.getOwner():projectile,point,true);
    }
    private void emitBlock(Entity attacker,Vec3 point,boolean infinity){
        guardLastContact=frame;guardQuiet=0;
        if (!(cat.level() instanceof ServerLevel level)) return;
        long now=level.getServer().getTickCount();if(now<nextGuardCue)return;nextGuardCue=now+3;
        Vec3 center = cat.getBoundingBox().getCenter(), toward = point.subtract(center).normalize();
        if(toward.lengthSqr()<.001)toward=cat.getLookAngle();
        new CatCombatCue(cat.getId(), attacker==null?-1:attacker.getId(), infinity?CatCombatCue.INFINITY:barraging() ? CatCombatCue.BARRAGE : CatCombatCue.GUARD,
                center, center.add(toward.scale(.8)), cat.isMahoraga() ? PLAIN : cat.isSukuna() ? CURSED : BLUE,
                attacker instanceof SorcererCat other ? other.punchKind() : PLAIN, cat.getRandom().nextInt()).broadcast(level);
    }
    private void barrage(ServerLevel level, LivingEntity target, boolean valid) {
        frame++; cat.getNavigation().stop();
        if (!valid) { finish(level, target, false); cat.releaseCombatHover(); return; }
        face(target);
        if (cat.combatHovering()) { cat.renewCombatHover(); cat.setDeltaMovement(Vec3.ZERO); }
        if (frame >= 4 && frame < duration(RAPID) - 4 && (frame - 4) % 3 == 0) {
            hand = nextHand(hand); kind = cat.automaticEnergy() ? nextKind(false) : configuredKind(hand); cat.prepareEnergy(hand, kind);
            // startPlan consumes pending; this contact belongs to the active barrage.
            if (inReach(target, RAPID) && cat.getSensing().hasLineOfSight(target)) {
                cat.performCombatHit(level, target, kind, RAPID);
                if (target instanceof SorcererCat rival && rival.combat().barraging() && frame % 2 == 0)
                    cue(level, CatCombatCue.BARRAGE, target, cat.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(), kind, rival.punchKind());
            }
        }
        double distance = cat.distanceTo(target);
        double preferred=target instanceof SorcererCat?CatSpacing.distance(cat,target)+.18:2.1;
        if (distance > preferred && distance < 4.8) {
            Vec3 step = target.position().subtract(cat.position()).normalize().scale(.22);
            if (CatTactics.airRoute(cat, level, cat.position(), cat.position().add(step))
                    &&(cat.combatHovering()||CatArena.walkable(cat,level,cat.position().add(step))))
                motion(level, new Vec3(step.x, cat.combatHovering() ? step.y : cat.getDeltaMovement().y, step.z));
        } else cat.setDeltaMovement(cat.getDeltaMovement().multiply(.2, cat.combatHovering() ? 0 : 1, .2));
        if (frame >= duration(RAPID)) { pending = null; finish(level, target, valid); cat.releaseCombatHover(); cooldown = 5; }
    }
    private void aerial(ServerLevel level, LivingEntity target, boolean valid) {
        frame++; cat.getNavigation().stop();
        if (!valid) { finish(level, target, false); cat.releaseCombatHover(); return; }
        face(target);
        if (move == AIR_CHASE) {
            if(kickCooldown==0&&inReach(target,KICK)&&Math.abs(cat.getY()-target.getY())<1.8
                    &&cat.getSensing().hasLineOfSight(target)&&cat.getRandom().nextFloat()<.36f){
                startPlan(new Plan(KICK,nextHand(hand),PLAIN,1,false));return;
            }
            // Respond to a new charge even after beginning a pursuit.
            if (target instanceof SorcererCat rival && CatSorcery.threatens(rival, cat)) {
                finish(level, target, true); startGuard(target, 50); return;
            }
            cat.renewCombatHover();
            Vec3 floor=CatArena.surface(level,target.position());
            if(floor==null||CatArena.hazardous(level,floor)||target.getY()<floor.y-2){finish(level,target,true);cat.releaseCombatHover();return;}
            Vec3 goal = target.position().add(0, 3.3, 0), delta = goal.subtract(cat.position());
            if (cat.getY() > target.getY() + 1.8 && horizontalDistance(cat.position(), target.position()) < 2.8) {
                divePoint = target.position(); startMove(DIVE_KICK); cat.prepareEnergy(hand, kind); return;
            }
            Vec3 step = delta.normalize().scale(Math.min(delta.length(), cat.isSukuna() ? 1.45 : 1.28));
            CatTerrain.clearStep(cat,cat.position(),cat.position().add(step));
            if (!CatTactics.airRoute(cat, level, cat.position(), cat.position().add(step)) || frame >= duration(AIR_CHASE)) {
                finish(level, target, true); cat.releaseCombatHover(); return;
            }
            motion(level, step);
            if (frame % 5 == 0) { cue(level, CatCombatCue.DASH, target, movementStart.add(0,.6,0), cat.position().add(0,.6,0), cat.isSukuna()?CURSED:BLUE, PLAIN); movementStart = cat.position(); }
            return;
        }
        if (frame < 4) { cat.renewCombatHover(); motion(level, Vec3.ZERO); }
        else if (!contacted && frame < 16) {
            cat.releaseCombatHover();
            if(frame<=10)divePoint=dev.jujutsuvfx.core.CatKickMotion.lead(target.position(),target.getDeltaMovement(),1.6);
            Vec3 direction = divePoint.subtract(cat.position()).normalize();
            Vec3 velocity = new Vec3(direction.x * 1.35, Math.min(-.55, direction.y * 1.7), direction.z * 1.35);
            motion(level, velocity);
            if (kickContact(target) && cat.getSensing().hasLineOfSight(target)) {
                contacted = true;
                boolean guarded = target instanceof SorcererCat other && other.combat().blocks(cat.damageSources().mobAttack(cat));
                if (cat.performCombatHit(level, target, kind, DIVE_KICK)) {
                    if (!guarded && target.isAlive() && !DomainFreeze.hold(target)
                            && !CatDuel.pinned(target) && (!(target instanceof SorcererCat still) || still.behavior() != STILL)) {
                        if (target instanceof SorcererCat other) { other.combat().reset(); other.releaseCombatHover(); }
                        target.setDeltaMovement(new Vec3(velocity.x*.28, -1.85, velocity.z*.28)); target.hasImpulse=true; target.hurtMarked=true;
                        if(target instanceof SorcererCat other)other.armLandingSmash(cat,kind);
                        level.getChunkSource().broadcastAndSend(target,new ClientboundSetEntityMotionPacket(target));
                    }
                    cue(level, CatCombatCue.DIVE, target, cat.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(), kind, PLAIN);
                }
                motion(level, new Vec3(-velocity.x*.15, .18, -velocity.z*.15));
            }
        }
        kickPrevious=cat.position();
        if (frame >= duration(DIVE_KICK)) { finish(level, target, valid); cat.releaseCombatHover(); kickCooldown=65; }
    }
    private static double horizontalDistance(Vec3 a, Vec3 b) { return Math.hypot(a.x-b.x, a.z-b.z); }
    private int nearestContactDistance(){
        int distance=Math.abs(contact(move)-frame);
        if(blade(move))for(int stroke=1;stroke<bladeStrokes(move);stroke++)
            distance=Math.min(distance,Math.abs(bladeContact(move,stroke)-frame));
        return distance;
    }
    private boolean tryClash(ServerLevel level, LivingEntity target) {
        if (!(target instanceof SorcererCat rival) || !cat.isRival(rival) || clashCooldown > 0
                || rival.combat().clashCooldown > 0 || !rival.combat().threatens(cat)
                || rival.combat().nearestContactDistance() > 3
                || rival.isNoAi() || DomainFreeze.hold(rival) || cat.getRandom().nextFloat() > .58f) return false;
        SorcererCatCombat other = rival.combat();
        int theirKind = other.kind, ourKind = kind;
        Vec3 direction = horizontal(rival.position().subtract(cat.position()));
        clashCooldown = other.clashCooldown = 65;
        pending = null; other.pending = null;
        start(CLASH, hand, ourKind, 0, false); other.start(CLASH, other.hand, theirKind, 0, false);
        motion(level, direction.scale(-.32).add(0, .10, 0));
        other.motion(level, direction.scale(.32).add(0, .10, 0));
        cue(level, CatCombatCue.CLASH, rival, cat.getBoundingBox().getCenter(), rival.getBoundingBox().getCenter(), ourKind, theirKind);
        cat.playSound(SoundEvents.ANVIL_LAND, .28f, 1.85f);
        cat.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, .9f, .72f);
        return true;
    }
    private void dash(ServerLevel level, LivingEntity target) {
        if (frame <= 7 && (!cat.horizontalCollision||CatTerrain.mover(cat)) && cat.distanceToSqr(target) > Math.pow(target instanceof SorcererCat?CatSpacing.distance(cat,target)+.18:2.1,2)) {
            Vec3 direction = horizontal(target.position().subtract(cat.position()));
            double speed = .80 + .18 * Math.sin(frame * Math.PI / 8);
            Vec3 step=direction.scale(speed);
            CatTerrain.clearStep(cat,cat.position(),cat.position().add(step));
            if(CatArena.walkable(cat,level,cat.position().add(step)))motion(level,step.add(0,cat.getDeltaMovement().y,0));
            else motion(level,Vec3.ZERO);
        } else if (frame == 8 || cat.horizontalCollision) motion(level, cat.getDeltaMovement().multiply(.25, 1, .25));
        if (frame == 3 || frame == 7) {
            cue(level, CatCombatCue.DASH, target, movementStart.add(0, .55, 0), cat.position().add(0, .55, 0), CURSED, BLACK_FLASH);
            movementStart = cat.position();
        }
    }
    private void blink(ServerLevel level, LivingEntity target) {
        if(!cat.canCombatBlink())return;
        Vec3 before = cat.position();
        Vec3 away = horizontal(target.position().subtract(before));
        if (away.lengthSqr() < .01) away = new Vec3(0, 0, 1);
        double baseAngle = Math.atan2(away.z, away.x);
        double sign = cat.getRandom().nextBoolean() ? 1 : -1;
        for (int i = 0; i < 8; i++) {
            double angle = baseAngle + sign * (i < 4 ? 1.0 + i * .48 : -1.0 - (i - 4) * .48);
            double spacing=target instanceof SorcererCat?CatSpacing.distance(cat,target)+.22:2.15;
            Vec3 desired = target.position().add(Math.cos(angle)*spacing,0,Math.sin(angle)*spacing);
            Vec3 safe = supported(level, desired);
            if (safe == null || safe.distanceToSqr(before) > 16 * 16 || safe.distanceToSqr(before) < 1) continue;
            if(!cat.tryCombatBlink())return;
            cat.teleportTo(safe.x, safe.y, safe.z); motion(level, Vec3.ZERO); cat.fallDistance = 0; face(target);
            cue(level, CatCombatCue.BLINK, target, before.add(0, .6, 0), safe.add(0, .6, 0), BLUE, BLUE);
            cat.playSound(SoundEvents.ENDERMAN_TELEPORT, .38f, 1.7f);
            return;
        }
        // No supported, empty destination: finish the pose and resume ordinary navigation.
    }
    private Vec3 supported(ServerLevel level, Vec3 desired) {
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            Vec3 p = new Vec3(desired.x, Math.floor(desired.y) + dy, desired.z);
            AABB box = cat.getBoundingBox().move(p.subtract(cat.position()));
            if (!level.hasChunkAt(BlockPos.containing(box.minX, box.minY - 1, box.minZ))
                    || !level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                    || !level.getWorldBorder().isWithinBounds(box)
                    || box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()) continue;
            if (CatArena.landing(cat,level,p) && level.noCollision(cat, box) && !level.containsAnyLiquid(box)
                    && !level.noCollision(cat, box.deflate(.09, 0, .09).move(0, -.12, 0))) return p;
        }
        return null;
    }
    private void motion(ServerLevel level, Vec3 velocity) {
        // Downward kicks use ordinary collision; pursuit excavation must not dig out the landing floor.
        if(!cat.isSukuna()||move!=DIVE_KICK)CatTerrain.clearStep(cat,cat.position(),cat.position().add(velocity));
        cat.setDeltaMovement(velocity); cat.hasImpulse = true; cat.hurtMarked = true;
        level.getChunkSource().broadcastAndSend(cat, new ClientboundSetEntityMotionPacket(cat));
    }
    private void cue(ServerLevel level, int type, LivingEntity target, Vec3 from, Vec3 to, int first, int second) {
        new CatCombatCue(cat.getId(), target.getId(), type, from, to, first, second, cat.getRandom().nextInt()).broadcast(level);
    }
    private static Vec3 horizontal(Vec3 value) { return new Vec3(value.x, 0, value.z).normalize(); }
}
