package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.PlayfulCloudMotion;
import dev.jujutsuvfx.item.PlayfulCloudItem;
import dev.jujutsuvfx.network.PlayfulCloudState;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Map;
import java.util.WeakHashMap;

/** Each player owns one combo and at most one pending heavy strike. No terrain edits. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class PlayfulCloudCombat {
    private static final Map<ServerPlayer,State> STATES=new WeakHashMap<>();
    private static final class State {
        final PlayfulCloudMotion.Combo combo=new PlayfulCloudMotion.Combo();
        float strength=1,power;
        int hitAt=-1;
        ItemStack weapon=ItemStack.EMPTY;
        net.minecraft.server.level.ServerLevel castLevel;
    }
    private PlayfulCloudCombat() {}
    private static boolean valid(ServerPlayer p) {
        return p.isAlive()&&!p.isSpectator()&&p.getMainHandItem().getItem() instanceof PlayfulCloudItem;
    }
    public static void prepare(Player actor) {
        if(!(actor instanceof ServerPlayer p)||!valid(p)||p.isUsingItem()||p.getCooldowns().isOnCooldown(p.getMainHandItem().getItem()))return;
        State s=STATES.computeIfAbsent(p,k->new State());
        s.strength=p.getAttackStrengthScale(.5f);
        begin(p,s);
    }
    public static void swing(LivingEntity actor) {
        if(!(actor instanceof ServerPlayer p)||!valid(p)||p.getCooldowns().isOnCooldown(p.getMainHandItem().getItem()))return;
        begin(p,STATES.computeIfAbsent(p,k->new State()));
    }
    private static void begin(ServerPlayer p,State s) {
        if(!s.combo.begin(p.tickCount))return;
        broadcast(p,new PlayfulCloudState(p.getId(),PlayfulCloudState.SWING,s.combo.step(),p.getEyePosition(),p.getLookAngle(),1));
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.55f, .92f+s.combo.step()*.14f);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.CHAIN_HIT,SoundSource.PLAYERS,.25f,1.5f+s.combo.step()*.1f);
    }
    public static void confirmed(ItemStack stack,LivingEntity actor,LivingEntity target) {
        if(!(actor instanceof ServerPlayer p)||!valid(p)||p.getMainHandItem()!=stack||target.level()!=p.level())return;
        State s=STATES.computeIfAbsent(p,k->new State());
        if(s.combo.started()==Long.MIN_VALUE)begin(p,s);
        if(!s.combo.hitOnce())return;
        impact(p,target,s.combo.step(),s.strength);
    }
    public static void release(ItemStack stack,LivingEntity actor,int charge) {
        if(!(actor instanceof ServerPlayer p)||!valid(p)||p.getMainHandItem()!=stack||p.getCooldowns().isOnCooldown(stack.getItem()))return;
        State s=STATES.computeIfAbsent(p,k->new State());
        if(s.hitAt>=0)return;
        s.combo.heavy(p.tickCount);s.weapon=stack;
        s.castLevel=p.serverLevel();
        s.power=PlayfulCloudMotion.heavyPower(charge);s.hitAt=p.tickCount+PlayfulCloudMotion.HEAVY_HIT_TICK;
        p.getCooldowns().addCooldown(stack.getItem(),PlayfulCloudMotion.HEAVY_RECOVERY);
        broadcast(p,new PlayfulCloudState(p.getId(),PlayfulCloudState.HEAVY,3,p.getEyePosition(),p.getLookAngle(),s.power));
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.8f,.67f);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.CHAIN_PLACE,SoundSource.PLAYERS,.4f,.85f);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var it=STATES.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next();ServerPlayer p=entry.getKey();State s=entry.getValue();
            if(p.isRemoved()||!valid(p)){it.remove();continue;}
            if(s.hitAt<0)continue;
            if(p.getMainHandItem()!=s.weapon||p.serverLevel()!=s.castLevel){s.hitAt=-1;s.weapon=ItemStack.EMPTY;continue;}
            if(p.tickCount<s.hitAt)continue;
            s.hitAt=-1;s.weapon=ItemStack.EMPTY;
            Vec3 start=p.getEyePosition(),look=p.getLookAngle(),end=start.add(look.scale(4.2));
            var block=p.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            double reachSqr=Math.min(4.2*4.2,start.distanceToSqr(block.getLocation()));
            var hit=ProjectileUtil.getEntityHitResult(p,start,end,p.getBoundingBox().expandTowards(look.scale(4.2)).inflate(.65),
                    e->e instanceof LivingEntity l&&l.isAlive()&&l.isPickable()&&!l.isSpectator()&&!p.isAlliedTo(l),reachSqr);
            if(hit==null||!(hit.getEntity() instanceof LivingEntity victim))continue;
            if(victim instanceof Player other&&!p.canHarmPlayer(other))continue;
            float damage=(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*(1.15f+.55f*s.power);
            p.resetAttackStrengthTicker();
            if(victim.hurt(p.damageSources().playerAttack(p),damage)) {
                p.setLastHurtMob(victim);
                impact(p,victim,3,s.power);
            }
        }
    }
    private static void impact(ServerPlayer p,LivingEntity victim,int step,float strength) {
        Vec3 from=p.getEyePosition(),center=victim.getBoundingBox().getCenter();
        Vec3 point=victim.getBoundingBox().clip(from,center).orElse(center);
        Vec3 direction=center.subtract(from).normalize();
        if(direction.lengthSqr()<.001)direction=p.getLookAngle();
        if((step==2||step==3)&&strength>.72f&&!DomainFreeze.hold(victim)) {
            victim.knockback(step==3?1.05:.55,p.getX()-victim.getX(),p.getZ()-victim.getZ());
            victim.hasImpulse=true;
            p.serverLevel().getChunkSource().broadcastAndSend(victim,new ClientboundSetEntityMotionPacket(victim));
        }
        broadcast(p,new PlayfulCloudState(p.getId(),PlayfulCloudState.HIT,step,point,direction,Math.max(.25f,strength)));
        p.serverLevel().playSound(null,victim.blockPosition(),SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,.85f,step==3?.66f:.91f);
        p.serverLevel().playSound(null,victim.blockPosition(),SoundEvents.SHIELD_BLOCK,SoundSource.PLAYERS,.40f,step==3?.70f:1.05f);
    }
    private static void broadcast(ServerPlayer p,PlayfulCloudState packet) {
        for(ServerPlayer observer:p.serverLevel().players())if(observer.distanceToSqr(p)<128*128
                &&observer.connection.hasChannel(PlayfulCloudState.TYPE))PacketDistributor.sendToPlayer(observer,packet);
    }
}
