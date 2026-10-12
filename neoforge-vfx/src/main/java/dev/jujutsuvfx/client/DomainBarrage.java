package dev.jujutsuvfx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.network.DomainControl;
import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.core.CutCadence;
import dev.jujutsuvfx.core.SlashBlade;
import dev.jujutsuvfx.network.BladeBatch;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Client-only scheduling. The domain has a fixed boundary; each volley reacquires its targets. */
final class DomainBarrage {
    static final int WAVE_INTERVAL_TICKS = SlashBlade.WAVE_TICKS;
    record Focus(Vec3 point, int entityId) {}
    private final Vec3 center;
    private final double radius;
    private long endTick;
    private final int bladesPerWave;
    private final int primaryId;
    private final ShrineFeedback feedback;
    private final Random random = new Random();
    private final Map<UUID,Integer> marked = new HashMap<>();
    private final Map<UUID,CutCadence> targets = new HashMap<>();
    private long nextWave;
    private int serial;
    private int lastBladeId;
    private final List<Rift> queuedSounds=new ArrayList<>();
    private java.util.function.Consumer<Rift> networkSink=ClientEvents::addSlash;
    void sink(java.util.function.Consumer<Rift> sink){networkSink=sink;}

    DomainBarrage(Focus focus, float radius, float seconds, int density, long now, ShrineFeedback feedback) {
        this.center = focus.point();
        this.primaryId = focus.entityId();
        this.radius = radius;
        this.endTick = now + Math.round(seconds * 20);
        this.nextWave = now;
        this.bladesPerWave = density;
        this.feedback = feedback;
    }

    static Focus focus(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null) return new Focus(Vec3.ZERO, -1);
        Vec3 eye = camera.getEyePosition(1);
        Vec3 look = camera.getViewVector(1);
        HitResult block = camera.pick(48, 1, false);
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(48)) : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, end,
                camera.getBoundingBox().expandTowards(look.scale(48)).inflate(1),
                e -> e instanceof LivingEntity && e != mc.player && e.isAlive() && !e.isSpectator(),
                eye.distanceToSqr(end));
        if (hit != null) return new Focus(bodyCenter(hit.getEntity()), hit.getEntity().getId());
        Vec3 point = block.getType() == HitResult.Type.MISS
                ? eye.add(look.scale(12)) : block.getLocation().add(0, 1, 0);
        // A near miss still lets the player select a creature without an exact pixel hit.
        List<LivingEntity> nearby = mc.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(point, point).inflate(3), e -> eligible(e, mc) &&
                        e.position().distanceToSqr(point) <= 9 && mc.player.hasLineOfSight(e));
        LivingEntity nearest = nearby.stream().min(Comparator.comparingDouble(e -> e.position().distanceToSqr(point)))
                .orElse(null);
        return nearest == null ? new Focus(point, -1) : new Focus(bodyCenter(nearest), nearest.getId());
    }

    private static boolean eligible(LivingEntity entity, Minecraft mc) {
        return !(entity instanceof Player) && entity.isAlive() && !entity.isSpectator();
    }

    private static Vec3 bodyCenter(Entity e) { return e.position().add(0, e.getBbHeight() * 0.55, 0); }

    boolean expired(long tick) { return tick >= endTick; }
    void reschedule(long deadline) { endTick=deadline; }
    long remaining(long tick) { return Math.max(0, (endTick - tick + 19) / 20); }

    void accept(BladeBatch packet){
        // A batch's absolute server tick is not the presentation clock. Anchor the batch on delivery
        // and preserve its relative delays, even when a lagged server sends an older world time.
        long received=ClientEffectClock.ticks();
        for(SlashBlade blade:packet.blades()){
            if(blade.id()<=lastBladeId)continue;lastBladeId=blade.id();
            Rift rift=new Rift(blade.center(),blade.right(),blade.up(),blade.normal(),blade.length(),
                    blade.persistent()?Float.POSITIVE_INFINITY:blade.lifetime(),received+Math.clamp(blade.delay(),-2400,20),
                    blade.seed(),Vec3.ZERO,true,blade.persistent());
            networkSink.accept(rift);if(blade.delay()>=-4)queuedSounds.add(rift);
            ClientShrineFocus.track(packet.session(),rift);
        }
    }
    void tickNetworked(Minecraft mc){
        if(mc.level==null)return;
        long now=ClientEffectClock.ticks();Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();
        Rift nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(var iterator=queuedSounds.iterator();iterator.hasNext();){
            Rift blade=iterator.next();if(now<blade.startTick())continue;
            iterator.remove();if(now-blade.startTick()>4||!ClientEvents.RIFTS.contains(blade))continue;
            double current=camera.distanceToSqr(blade.center());
            if(current<distance){distance=current;nearest=blade;}
        }
        if(nearest!=null)feedback.wave(mc,nearest,now);
    }

    void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || expired(ClientEffectClock.ticks())) return;
        long now = ClientEffectClock.ticks();
        if(now>=nextWave){
            nextWave=now+WAVE_INTERVAL_TICKS;
            int count=SlashBlade.count((float)radius,bladesPerWave);
            Rift nearest=null;double nearestDistance=Double.POSITIVE_INFINITY;
            Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();
            for(int i=0;i<count;){
                double angle=random.nextDouble()*Math.PI*2,reach=radius*Math.sqrt(random.nextDouble())*.93;
                double ceiling=Math.sqrt(radius*radius-reach*reach);
                double y=.4+random.nextDouble()*Math.min(ceiling-.4,Math.max(2,radius*.52));
                Vec3 aim=center.add(Math.cos(angle)*reach,y,Math.sin(angle)*reach);
                SlashBlade first=SlashBlade.create(++serial,aim,random,SlashBlade.ambientLength((float)radius,random),
                        (.46f+random.nextFloat()*.22f)*.60f,i%WAVE_INTERVAL_TICKS,false);
                Rift blade=emit(first,now);i++;
                double distance=camera.distanceToSqr(aim);
                if(distance<nearestDistance){nearestDistance=distance;nearest=blade;}
                if(i<count&&random.nextFloat()<.40f){
                    emit(SlashBlade.crossArm(++serial,first,random,first.delay()+1,false),now);i++;
                }
            }
            feedback.wave(mc,nearest,now);
        }
        // Visual-only connections use the same cadence, but never pretend to apply server damage.
        List<LivingEntity> present=level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(center,center).inflate(radius),e->eligible(e,mc)
                                &&DomainVolume.intersects(center,radius,e.getBoundingBox())).stream()
                .sorted(Comparator.<LivingEntity>comparingInt(e -> e.getId() == primaryId ? 0 : 1)
                        .thenComparingDouble(e->e.position().distanceToSqr(center))).toList();
        Set<UUID> inside=new HashSet<>();
        for(LivingEntity target:present){
            UUID id=target.getUUID();inside.add(id);
            CutCadence cadence=targets.computeIfAbsent(id,key->new CutCadence(now));
            int demand=cadence.demand(now,endTick);if(demand==0)continue;
            boolean urgent=cadence.urgent(now,endTick);
            int count=urgent?demand:demand>=2&&random.nextFloat()<.42f?2:1;
            cadence.scheduled(count,now,endTick);
            Vec3 aim=SlashBlade.targetPoint(target.getBoundingBox(),center,(float)radius);
            int latest=(int)(endTick-now-2),lead=Math.min(0,latest);
            for(int i=0;i<count;){
                int delay=Math.min(lead+(urgent?0:i),latest);
                float length=Math.clamp(target.getBbHeight()*(2.8f+random.nextFloat()*2.3f)+2+random.nextFloat()*5,7f,22f);
                SlashBlade first=SlashBlade.create(++serial,aim,random,length,.48f+random.nextFloat()*.22f,delay,retain(id));
                Rift blade=emit(first,now);cadence.contacted();cadence.resolved();i++;
                if(i<count){
                    emit(SlashBlade.crossArm(++serial,first,random,Math.min(delay+(urgent?0:1),latest),retain(id)),now);
                    cadence.contacted();cadence.resolved();i++;
                }
                feedback.hit(mc,blade,now);
            }
        }
        targets.keySet().retainAll(inside);
    }

    private boolean retain(UUID id){
        Integer count=marked.get(id);
        if(count==null){if(marked.size()>=DomainControl.MAX_MARKED_TARGETS)return false;count=0;}
        if(count>=2)return false;marked.put(id,count+1);return true;
    }

    static void burst(Minecraft mc, Focus focus) {
        if (mc.level == null) return;
        Random rng = new Random();
        for (int i = 0; i < 12;) {
            Vec3 aim = focus.point().add((rng.nextDouble() - 0.5) * 0.6,
                    (rng.nextDouble() - 0.5) * 0.9, (rng.nextDouble() - 0.5) * 0.6);
            SlashBlade first=SlashBlade.create(i,aim,rng,8+rng.nextFloat()*20,.5f+rng.nextFloat()*.25f,i%2,false);
            emit(first,ClientEffectClock.ticks());i++;
            if(i<12&&rng.nextFloat()<.40f){
                emit(SlashBlade.crossArm(i,first,rng,first.delay()+1,false),ClientEffectClock.ticks());i++;
            }
        }
    }

    private static Rift emit(SlashBlade frame,long tick) {
        Rift blade=new Rift(frame.center(),frame.right(),frame.up(),frame.normal(),frame.length(),
                frame.persistent()?Float.POSITIVE_INFINITY:frame.lifetime(),tick+frame.delay(),
                frame.seed(),Vec3.ZERO,true,frame.persistent());
        ClientEvents.addSlash(blade);
        return blade;
    }
}
