package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** A lethal hit starts one final cast, without borrowing the duel director or the camera. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class GojoLastStand {
    private static final Map<MinecraftServer,Map<UUID,FinalCast>> ACTIVE=new WeakHashMap<>();
    private static final class FinalCast {
        final SorcererCat cat;final ServerLevel level;final Vec3 sky,anchor;final long began;
        final LivingEntity target;
        int blue,red,phase;long ready,heldTick=Long.MIN_VALUE;UUID blast;
        FinalCast(SorcererCat cat,DamageSource source){
            this.cat=cat;level=(ServerLevel)cat.level();began=cat.getServer().getTickCount();
            sky=cat.position().add(0,8,0);anchor=hoverPoint(cat,level);
            target=source.getEntity() instanceof LivingEntity e&&e.level()==level?e:cat;
        }
    }
    private GojoLastStand(){}
    public static boolean active(SorcererCat cat){
        Map<UUID,FinalCast> casts=ACTIVE.get(cat.getServer());return casts!=null&&casts.containsKey(cat.getUUID());
    }
    /** Ordinary last stands hold their caster; the target remains free to leave the warning circle. */
    public static boolean control(SorcererCat cat){
        Map<UUID,FinalCast> casts=ACTIVE.get(cat.getServer());FinalCast f=casts==null?null:casts.get(cat.getUUID());
        if(f==null||f.cat!=cat||cat.level()!=f.level||cat.isRemoved()||!cat.isAlive())return false;
        cat.getNavigation().stop();cat.renewCombatHover();cat.setDeltaMovement(Vec3.ZERO);
        long now=cat.getServer().getTickCount();
        if(f.heldTick!=now){
            f.heldTick=now;Vec3 delta=f.anchor.subtract(cat.position());
            if(delta.lengthSqr()>1e-6){
                cat.move(net.minecraft.world.entity.MoverType.SELF,delta.scale(Math.min(1,.20/delta.length())));
                cat.hasImpulse=true;
            }
        }
        cat.fallDistance=0;return true;
    }
    /** Rise a little when grounded, without moving Blue's warning centre or teleporting through a ceiling. */
    private static Vec3 hoverPoint(SorcererCat cat,ServerLevel level){
        Vec3 start=cat.position();if(!cat.onGround())return start;
        for(double height=2.5;height>=.5;height-=.5){
            Vec3 point=start.add(0,height,0);
            if(CatTactics.empty(cat,level,point)&&CatTactics.airRoute(cat,level,start,point))return point;
        }
        return start;
    }
    public static boolean begin(SorcererCat cat,DamageSource source){
        if(VfxConfig.cinematic()||!cat.isGojo()||cat.isRemoved()||cat.isNoAi()||cat.isTransforming()
                ||!(cat.level() instanceof ServerLevel))return false;
        if(active(cat))return true;
        cat.setHealth(1);cat.finaleWard(true);cat.combat().reset();CatSorcery.cancelAll(cat);
        DomainFreeze.releaseFinaleActor(cat);
        ACTIVE.computeIfAbsent(cat.getServer(),s->new LinkedHashMap<>()).put(cat.getUUID(),new FinalCast(cat,source));
        control(cat);return true;
    }
    private static void warning(FinalCast f,boolean end){
        new CatCombatCue(f.cat.getId(),-1,CatCombatCue.BLAST_WARNING,f.sky,f.sky.add(0,-8,0),
                end?0:(int)UnlimitedPurpleBlast.RADIUS,0,0).broadcast(f.level);
    }
    public static void cancel(SorcererCat cat){
        Map<UUID,FinalCast> casts=ACTIVE.get(cat.getServer());FinalCast f=casts==null?null:casts.remove(cat.getUUID());
        if(f==null)return;
        warning(f,true);CatSorcery.cancelSession(cat,f.blue);CatSorcery.cancelSession(cat,f.red);
        if(f.blast!=null)UnlimitedPurpleBlast.cancelCat(cat,f.blast);
        cat.finaleWard(false);cat.releaseCombatHover();
    }
    private static void finish(FinalCast f){cancel(f.cat);f.cat.kill();}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        Map<UUID,FinalCast> casts=ACTIVE.get(event.getServer());if(casts==null)return;
        long now=event.getServer().getTickCount();
        for(FinalCast f:List.copyOf(casts.values())){
            SorcererCat cat=f.cat;
            if(cat.isRemoved()||cat.level()!=f.level||!cat.isAlive()){cancel(cat);continue;}
            // Aborted casts must not leave a permanent invulnerable entity behind.
            if(now-f.began>700||cat.isNoAi()||cat.isInSittingPose()){finish(f);continue;}
            if(f.phase==0){f.blue=CatSorcery.startFinaleBlue(cat,f.target,f.sky);f.phase=1;}
            if(f.phase<=2&&!CatSorcery.active(cat,f.blue)){finish(f);continue;}
            if(f.phase==1&&CatSorcery.finaleBlueReady(cat,f.blue)){
                CatSorcery.freeFinaleBlue(cat,f.blue);f.ready=now;f.phase=2;
            }
            if(f.phase==2&&now-f.ready>=100){
                f.red=CatSorcery.startFinaleRed(cat,f.target,f.blue,blast->{f.blast=blast;f.phase=4;});
                if(f.red==0){finish(f);continue;}f.phase=3;
            }
            if(f.phase==3&&!CatSorcery.active(cat,f.red)){finish(f);continue;}
            if(f.phase==4&&!UnlimitedPurpleBlast.active(cat,f.blast)){finish(f);continue;}
            control(cat);
            if(now%10==0&&(f.phase>=2||CatSorcery.finaleBlueLaunched(cat,f.blue)))warning(f,false);
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        Map<UUID,FinalCast> casts=ACTIVE.get(event.getServer());
        if(casts!=null)for(FinalCast f:List.copyOf(casts.values()))finish(f);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.remove(event.getServer());}
}
