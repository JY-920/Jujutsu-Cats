package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.MartialArts;
import dev.jujutsuvfx.item.FistCombo;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** All reach, charge time, hit cadence, launch movement and damage are server-owned. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class MartialCombat {
    private static final Map<ServerPlayer,State> STATES=new WeakHashMap<>();
    private static final class State {
        final MartialArts.Sequence combo=new MartialArts.Sequence();
        final Map<UUID,Long> hits=new HashMap<>();
        ItemStack item=ItemStack.EMPTY;
        ServerLevel level;
        boolean black,consumed;
        int lastMove,lastAction,lastAge=-1;
        long lastPacket=-1,lastSync=-1;
    }
    private MartialCombat() {}
    private static boolean valid(ServerPlayer p) {
        return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&VfxItems.isHeld(p.getMainHandItem())
                &&!p.isPassenger()&&!p.isFallFlying()&&!p.isUsingItem()&&DomainFreeze.controlTicks(p)==0;
    }
    public static void input(MartialInput packet,Player actor) {
        if(!(actor instanceof ServerPlayer p)||packet.sequence()<=0||packet.edge()>MartialInput.CANCEL)return;
        State s=STATES.computeIfAbsent(p,k->new State());long now=p.serverLevel().getGameTime();
        if(s.level!=null&&(s.level!=p.serverLevel()||s.item!=p.getMainHandItem())){s=new State();STATES.put(p,s);}
        if(packet.edge()==MartialInput.CANCEL){s.combo.cancel();sync(p,s,0);return;}
        if(!valid(p)){s.combo.cancel();sync(p,s,MartialState.REJECT);return;}
        if(packet.edge()==MartialInput.PRESS) {
            s.lastPacket=now;
            if(!s.combo.press(packet.sequence(),now,!p.onGround()&&!p.isInWaterOrBubble())) {
                sync(p,s,0);return; // A full one-entry buffer must not cancel the current attack.
            }
            s.item=p.getMainHandItem();s.level=p.serverLevel();
        }else s.combo.release(packet.sequence(),now);
        sync(p,s,0);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var iterator=STATES.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();ServerPlayer p=entry.getKey();State s=entry.getValue();
            if(!valid(p)||p.getMainHandItem()!=s.item||s.level!=p.serverLevel()) {
                s.combo.cancel();if(!p.isRemoved())sync(p,s,0);iterator.remove();continue;
            }
            long now=p.serverLevel().getGameTime();s.combo.tick(now);
            int move=s.combo.move(),age=s.combo.age(now);
            if(move==MartialArts.DIVE) {
                p.fallDistance=0;
                if(age>=2&&(p.onGround()||p.isInWaterOrBubble())){s.combo.land(now);move=s.combo.move();age=0;}
            }
            boolean changed=move!=s.lastMove||s.combo.action()!=s.lastAction||age<s.lastAge;
            if(changed&&move!=MartialArts.IDLE)begin(p,s,move);
            if(age==MartialArts.contact(move)-1&&age!=s.lastAge)launch(p,move);
            if(MartialArts.contactAt(move,age)&&s.combo.busy(now)&&s.lastAge!=age)strike(p,s,move,age,now);
            if(changed||s.combo.busy(now)&&now-s.lastSync>=5||s.combo.charging()&&now-s.lastSync>=5)sync(p,s,0);
            s.lastMove=move;s.lastAction=s.combo.action();s.lastAge=age;
        }
    }
    private static void begin(ServerPlayer p,State s,int move) {
        s.hits.clear();s.consumed=false;s.black=FistCombo.ready(s.item);
        p.resetAttackStrengthTicker();
        if(move==MartialArts.DIVE)impulse(p,new Vec3(p.getDeltaMovement().x*.3,-1.45,p.getDeltaMovement().z*.3));
        else if(move==MartialArts.LAND){
            p.serverLevel().sendParticles(ParticleTypes.POOF,p.getX(),p.getY()+.12,p.getZ(),18,.95,.06,.95,.08);
            p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_BIG_FALL,SoundSource.PLAYERS,.9f,.70f);
        }
    }
    private static void launch(ServerPlayer p,int move) {
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,
                move<=MartialArts.KICK?.5f:.72f,move<=MartialArts.KICK?1.48f:.96f);
        Vec3 forward=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();
        if(move==MartialArts.UPPERCUT&&!p.isInWaterOrBubble())impulse(p,forward.scale(.22).add(0,.48,0));
        else if(move==MartialArts.SPIN&&p.onGround())impulse(p,forward.scale(.18).add(0,.32,0));
        else if(move==MartialArts.PALM)impulse(p,p.getDeltaMovement().add(forward.scale(.28)));
    }
    private static void strike(ServerPlayer p,State s,int move,int age,long now) {
        boolean area=move==MartialArts.SPIN||move==MartialArts.LAND;
        double reach=area?3.3:move==MartialArts.PALM?3.25:2.95;
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle();
        var targets=p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(reach,1.3,reach),
                e->e!=p&&e.isAlive()&&e.isPickable()&&!e.isSpectator()&&!p.isAlliedTo(e));
        targets.sort(Comparator.comparingDouble(e->e.distanceToSqr(p)));
        int count=0;
        for(LivingEntity target:targets) {
            if(target instanceof Player victim&&!p.canHarmPlayer(victim))continue;
            var b=target.getBoundingBox();
            Vec3 nearest=new Vec3(Mth.clamp(eye.x,b.minX,b.maxX),Mth.clamp(eye.y,b.minY,b.maxY),Mth.clamp(eye.z,b.minZ,b.maxZ));
            Vec3 ray=nearest.subtract(eye);double distance=ray.length();
            if(distance>reach||!p.hasLineOfSight(target))continue;
            if(!area&&distance>.35&&ray.normalize().dot(look)<.48)continue;
            Long last=s.hits.get(target.getUUID());
            if(last!=null&&(move!=MartialArts.BARRAGE||now-last<3))continue;
            int immunity=target.invulnerableTime;
            // Only this actor's accepted, paced combo may refresh its own previous hurt window.
            // Shield, armor, team/PvP restrictions and invulnerability checks remain vanilla.
            var previous=target.getLastDamageSource();
            if(previous!=null&&previous.getEntity()==p&&immunity>0)target.invulnerableTime=0;
            float amount=(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*MartialArts.damage(move)*(s.black?2:1);
            boolean hit=target.hurt(p.damageSources().playerAttack(p),amount);
            if(!hit){target.invulnerableTime=Math.max(immunity,target.invulnerableTime);continue;}
            s.hits.put(target.getUUID(),now);p.setLastHurtMob(target);
            if(!s.consumed){FistCombo.confirmed(s.item);p.inventoryMenu.broadcastChanges();s.consumed=true;}
            if(!DomainFreeze.hold(target)) {
                double resistance=1-Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,1);
                Vec3 away=target.position().subtract(p.position()).multiply(1,0,1).normalize();
                if(away.lengthSqr()<.001)away=new Vec3(look.x,0,look.z).normalize();
                if(move==MartialArts.UPPERCUT)impulse(target,away.scale(.20*resistance).add(0,.78*resistance,0));
                else if(move==MartialArts.SPIN||move==MartialArts.KICK||move==MartialArts.PALM||move==MartialArts.LAND||s.black) {
                    double strength=move==MartialArts.KICK?.46:move==MartialArts.SPIN?1.1:.70;
                    if(move==MartialArts.BARRAGE&&age<18)strength=.03;
                    target.knockback(strength,p.getX()-target.getX(),p.getZ()-target.getZ());impulse(target,target.getDeltaMovement());
                }else if(move==MartialArts.BARRAGE) {
                    if(age<18)impulse(target,target.getDeltaMovement().multiply(.12,1,.12));
                    else {target.knockback(.55,p.getX()-target.getX(),p.getZ()-target.getZ());impulse(target,target.getDeltaMovement());}
                }
            }
            Vec3 point=b.clip(eye,b.getCenter()).orElse(b.getCenter()),dir=b.getCenter().subtract(eye).normalize();
            if(dir.lengthSqr()<.001)dir=look;
            FistStrike packet=new FistStrike(new FistImpact(p.getId(),target.getId(),point,dir,
                    move==MartialArts.BARRAGE?.52f:move>=MartialArts.PALM?1:.78f),s.black);
            for(ServerPlayer observer:p.serverLevel().players())if(observer.distanceToSqr(p)<128*128
                    &&observer.connection.hasChannel(FistStrike.TYPE))PacketDistributor.sendToPlayer(observer,packet);
            p.serverLevel().playSound(null,target.blockPosition(),s.black?SoundEvents.PLAYER_ATTACK_CRIT:SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS,s.black?.95f:.65f,s.black?.70f:move==MartialArts.BARRAGE?1.4f:1.05f);
            p.causeFoodExhaustion(move==MartialArts.BARRAGE?.03f:.10f);
            if(++count>=(area?8:1))break;
        }
        if(count>0){
            MartialHit beat=new MartialHit(p.getId(),p.getUUID(),s.combo.action(),move,age,s.black);
            for(ServerPlayer observer:p.serverLevel().players())if(observer.distanceToSqr(p)<128*128
                    &&observer.connection.hasChannel(MartialHit.TYPE))PacketDistributor.sendToPlayer(observer,beat);
        }
    }
    private static void impulse(LivingEntity actor,Vec3 velocity) {
        actor.setDeltaMovement(velocity);actor.hasImpulse=true;actor.hurtMarked=true;
        if(actor.level() instanceof ServerLevel level)level.getChunkSource().broadcastAndSend(actor,new ClientboundSetEntityMotionPacket(actor));
    }
    private static MartialState snapshot(ServerPlayer p,State s,int flags) {
        long now=p.serverLevel().getGameTime();
        return new MartialState(p.getId(),p.getUUID(),s.combo.sequence(),s.combo.action(),s.combo.move(),s.combo.age(now),s.combo.next(),
                s.combo.held(now),flags|(s.combo.charging()?MartialState.HOLD:0)
                        |((s.combo.busy(now)?s.black:FistCombo.ready(s.item))?MartialState.BLACK:0));
    }
    private static void sync(ServerPlayer p,State s,int flags) {
        s.lastSync=p.serverLevel().getGameTime();var packet=snapshot(p,s,flags);
        if((flags&MartialState.REJECT)!=0){if(p.connection.hasChannel(MartialState.TYPE))PacketDistributor.sendToPlayer(p,packet);return;}
        for(ServerPlayer observer:p.serverLevel().players())if(observer.distanceToSqr(p)<128*128
                &&observer.connection.hasChannel(MartialState.TYPE))PacketDistributor.sendToPlayer(observer,packet);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if(!(event.getEntity() instanceof ServerPlayer observer)||!(event.getTarget() instanceof ServerPlayer actor))return;
        State s=STATES.get(actor);
        if(s!=null&&observer.connection.hasChannel(MartialState.TYPE))PacketDistributor.sendToPlayer(observer,snapshot(actor,s,0));
    }
}
