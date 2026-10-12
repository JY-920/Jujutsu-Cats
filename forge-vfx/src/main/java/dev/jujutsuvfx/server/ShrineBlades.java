package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.CutCadence;
import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.core.SlashBlade;
import dev.jujutsuvfx.network.BladeBatch;
import dev.jujutsuvfx.network.ShrineFocus;
import dev.jujutsuvfx.network.BladeImpact;
import dev.jujutsuvfx.network.DomainControl;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import java.util.function.Predicate;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.jujutsuvfx.platform.PacketDistributor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Visible server-owned blades, with an independent five-contact quota for every arriving creature. */
final class ShrineBlades {
    private static final ResourceKey<DamageType> DAMAGE=ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(JujutsuVfx.ID,"shrine_cut"));
    private final int session,kind;
    private final Vec3 center;
    private final float radius;
    private final long attackingAt;
    private final Random random=new Random();
    private final Map<UUID,CutCadence> targets=new HashMap<>();
    private final Map<UUID,Integer> marked=new HashMap<>();
    private final List<Pending> pending=new ArrayList<>();
    private long nextWave;
    private int serial;
    private LivingEntity owner,focused;
    private UUID candidate;
    private long candidateSince,focusSince,nextFocusSync;
    private int generation; private boolean forcedFocus; private long nextFocusAccent;
    private static final float FOCUSED_DAMAGE=20f;
    private static final class Pending {
        final SlashBlade blade;final long start;final Set<UUID> hit=new HashSet<>();
        final UUID intended;final CutCadence cadence;
        boolean feedback,reserved,cosmetic;
        Pending(SlashBlade blade,long now,UUID intended,CutCadence cadence){
            this.blade=blade;start=now+blade.delay();this.intended=intended;this.cadence=cadence;reserved=cadence!=null;
        }
        void releaseReservation(){if(reserved){cadence.resolved();reserved=false;}}
    }
    ShrineBlades(int session,int kind,Vec3 center,float radius,long born){
        this.session=session;this.kind=kind;this.center=center;this.radius=radius;
        attackingAt=born+(kind==DomainVolume.SHRINE?44:0);nextWave=attackingAt;
    }
    private Predicate<LivingEntity> extraFilter = entity -> true;
    void filter(Predicate<LivingEntity> filter) { extraFilter = filter; }
    void close(){
        if(focused!=null&&owner!=null)sendFocus(false,owner.getServer().getTickCount(),0);
        focused=null;owner=null;pending.clear();targets.clear();marked.clear();
    }
    void forceFocus(LivingEntity caster,LivingEntity target,long now,long deadline){
        owner=caster;focused=target;forcedFocus=true;candidate=target.getUUID();candidateSince=focusSince=now;
        generation++;pending.clear();targets.clear();marked.clear();nextWave=nextFocusAccent=now;nextFocusSync=now+10;
        sendFocus(true,now,deadline);
    }
    void finish(LivingEntity owner,long now){pending.clear();targets.clear();if(focused!=null)sendFocus(true,now,now);}
    private void selectFocus(LivingEntity owner,List<LivingEntity> all,long now,long deadline){
        this.owner=owner;if(kind!=DomainVolume.SHRINE)return;
        if(forcedFocus){if(focused!=null&&now>=nextFocusSync){sendFocus(true,now,deadline);nextFocusSync=now+10;}return;}
        LivingEntity single=all.size()==1?all.get(0):null;
        // Keep the concentrated scene through a lone victim's death; do not suddenly
        // fill the empty domain with ambient blades when its last target is defeated.
        boolean defeated=focused!=null&&(!focused.isAlive()||focused.isRemoved());
        if(focused!=null&&(all.size()>1||single!=null&&single!=focused||single==null&&!defeated)){
            sendFocus(false,now,0);focused=null;pending.clear();targets.clear();marked.clear();nextWave=now;
        }
        UUID wanted=single==null?null:single.getUUID();
        if(!java.util.Objects.equals(candidate,wanted)){candidate=wanted;candidateSince=now;}
        if(focused==null&&single!=null&&now-candidateSince>=8&&deadline-now>=22){
            focused=single;focusSince=Math.max(now,attackingAt-16);generation++;
            pending.clear();targets.clear();marked.clear();sendFocus(true,now,deadline);nextFocusSync=now+10;
        }
        if(focused!=null&&now>=nextFocusSync){sendFocus(true,now,deadline);nextFocusSync=now+10;}
    }
    private void sendFocus(boolean active,long now,long deadline){
        if(focused==null||owner==null)return;
        send(owner,new ShrineFocus(session,owner.getUUID(),generation,active,focused.getId(),focused.getUUID(),center,radius,
                (int)(now-focusSince),active?(int)Math.max(0,deadline-now+DomainControl.CLOSE_TICKS):0));
    }
    void tick(LivingEntity owner,long now,long deadline,float currentRadius,
              Consumer<LivingEntity> freeze,BooleanSupplier active){
        if(!active.getAsBoolean()||now>=deadline)return;
        List<LivingEntity> all=((ServerLevel)owner.level()).getEntitiesOfClass(LivingEntity.class,
                new AABB(center,center).inflate(radius),entity->eligible(entity)&&entity!=owner
                        &&DomainVolume.intersects(center,radius,entity.getBoundingBox()));
        selectFocus(owner,all,now,deadline);
        if(currentRadius<=0)return;
        List<LivingEntity> present=all.stream().filter(entity->DomainVolume.intersects(center,currentRadius,entity.getBoundingBox())).toList();
        Set<UUID> inside=new HashSet<>();
        for(LivingEntity target:present){
            if(!active.getAsBoolean())return;
            // Capture new arrivals before aiming, including arrivals on the final active tick.
            freeze.accept(target);inside.add(target.getUUID());
            targets.computeIfAbsent(target.getUUID(),id->focused==target?new CutCadence(now,15,forcedFocus?5:6):new CutCadence(now));
        }
        targets.keySet().retainAll(inside);
        DamageSource source=new DamageSource(owner.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE),owner,owner);
        for(Pending cut:List.copyOf(pending)){
            if(!active.getAsBoolean())return;
            sweep(owner,cut,now,source,active);
        }
        if(!active.getAsBoolean()||now<attackingAt)return;
        List<Pending> created=new ArrayList<>();
        for(LivingEntity target:present){
            if(!eligible(target)||!active.getAsBoolean())continue;
            CutCadence cadence=targets.get(target.getUUID());
            int demand=cadence.demand(now,deadline);if(demand==0)continue;
            boolean urgent=cadence.urgent(now,deadline);
            int count=urgent?demand:focused!=null?1:demand>=2&&random.nextFloat()<.42f?2:1;
            cadence.scheduled(count,now,deadline);
            Vec3 aim=SlashBlade.targetPoint(target.getBoundingBox(),center,radius);
            int latest=(int)(deadline-now-2),lead=Math.min(SlashBlade.LEAD_TICKS,latest);
            for(int i=0;i<count;){
                int delay=Math.min(lead+(urgent?0:i),latest);
                float length=focused!=null?dev.jujutsuvfx.platform.Numbers.clamp(target.getBbHeight()*(2.1f+random.nextFloat()*4.2f)+2+random.nextFloat()*4,5.5f,22)
                        :dev.jujutsuvfx.platform.Numbers.clamp(target.getBbHeight()*(2.8f+random.nextFloat()*2.3f)+2+random.nextFloat()*5,7f,22f);
                Vec3 point=aim;
                if(focused!=null){Vec3 varied=aim.add((random.nextDouble()-.5)*target.getBbWidth()*.7,
                        (random.nextDouble()-.5)*target.getBbHeight()*.65,(random.nextDouble()-.5)*target.getBbWidth()*.7);
                    if(varied.distanceToSqr(center)<radius*radius)point=varied;}
                SlashBlade first=SlashBlade.create(++serial,point,random,length,.48f+random.nextFloat()*.22f,
                        delay,retain(target.getUUID()));
                if(focused!=null&&!urgent){
                    // Shift along the cut, so the silhouette is not always its midpoint while
                    // the authoritative blade still crosses the chosen point inside the victim.
                    Vec3 shifted=first.center().add(first.up().scale((random.nextDouble()-.5)*length*.55));
                    if(shifted.distanceToSqr(center)<radius*radius)first=new SlashBlade(first.id(),shifted,first.right(),first.up(),
                            first.length(),first.lifetime(),first.seed(),first.delay(),first.persistent());
                }
                created.add(new Pending(first,now,target.getUUID(),cadence));i++;
                if(focused!=null&&random.nextFloat()<.42f){
                    SlashBlade crossing=SlashBlade.crossArm(++serial,first,random,delay,retain(target.getUUID()));
                    Pending accent=new Pending(crossing,now,null,null);accent.cosmetic=true;created.add(accent);
                }
                if(i<count){
                    SlashBlade other=SlashBlade.crossArm(++serial,first,random,Math.min(delay+(urgent?0:1),latest),retain(target.getUUID()));
                    created.add(new Pending(other,now,target.getUUID(),cadence));i++;
                }
            }
        }
        // Cosmetic slices occupy a volume around the body. They do not increase damage or retained scars.
        if(focused!=null&&focused.isAlive()&&now>=nextFocusAccent){
            nextFocusAccent=now+2;Vec3 body=focused.getBoundingBox().getCenter();
            int count=3+random.nextInt(4);
            for(int i=0;i<count;i++){
                double reach=1.4+random.nextDouble()*3.8,angle=random.nextDouble()*Math.PI*2;
                Vec3 point=body.add(Math.cos(angle)*reach,(random.nextDouble()-.5)*5,Math.sin(angle)*reach);
                if(point.distanceToSqr(center)>radius*radius)continue;
                SlashBlade accent=SlashBlade.create(++serial,point,random,4+random.nextFloat()*15,
                        .18f+random.nextFloat()*.20f,0,false);
                Pending cut=new Pending(accent,now,null,null);cut.cosmetic=true;created.add(cut);
                if(random.nextFloat()<.46f){Pending cross=new Pending(SlashBlade.crossArm(++serial,accent,random,0,false),now,null,null);
                    cross.cosmetic=true;created.add(cross);}
            }
        }
        if(focused==null&&now>=nextWave){
            nextWave=now+SlashBlade.WAVE_TICKS;
            int latest=(int)(deadline-now-SlashBlade.STRIKE_TICKS-1);
            if(latest>=SlashBlade.LEAD_TICKS){
                int count=SlashBlade.count(radius,kind==DomainVolume.BARRAGE?3:4);
                for(int i=0;i<count;){
                    double angle=random.nextDouble()*Math.PI*2,reach=radius*Math.sqrt(random.nextDouble())*.93;
                    double ceiling=Math.sqrt(radius*radius-reach*reach);
                    double y=.4+random.nextDouble()*Math.min(ceiling-.4,Math.max(2,radius*.52));
                    Vec3 aim=center.add(Math.cos(angle)*reach,y,Math.sin(angle)*reach);
                    int delay=Math.min(SlashBlade.LEAD_TICKS+i%SlashBlade.WAVE_TICKS,latest);
                    SlashBlade first=SlashBlade.create(++serial,aim,random,SlashBlade.ambientLength(radius,random),
                            (.46f+random.nextFloat()*.22f)*.60f,delay,false);
                    created.add(new Pending(first,now,null,null));i++;
                    if(i<count&&random.nextFloat()<.40f){
                        created.add(new Pending(SlashBlade.crossArm(++serial,first,random,Math.min(delay+1,latest),false),now,null,null));i++;
                    }
                }
            }
        }
        if(created.isEmpty()||!active.getAsBoolean())return;
        pending.addAll(created);
        // Split presentation packets instead of limiting the number of creatures entitled to targeted cuts.
        long gameTick=((ServerLevel) owner.level()).getGameTime();
        for(int i=0;i<created.size();i+=64){
            List<SlashBlade> batch=created.subList(i,Math.min(i+64,created.size())).stream().map(p->presentation(p.blade,now,deadline)).toList();
            send(owner,new BladeBatch(session,gameTick,batch));
        }
        // With less than two ticks remaining, the blades share the final tick's fast sweep.
        // Their center has already been crossed at age 0.05 s; there is no end-of-domain damage pulse.
        for(Pending cut:created){
            if(!active.getAsBoolean())return;
            if(cut.start<now)sweep(owner,cut,now,source,active);
        }
    }
    /** The four-second winner shot also owns the final blade's fade; closing the sky adds no scars. */
    private SlashBlade presentation(SlashBlade blade,long now,long deadline){
        if(!forcedFocus)return blade;
        float life=Math.max(.001f,Math.min(blade.lifetime(),(deadline-now-blade.delay())/20f));
        return new SlashBlade(blade.id(),blade.center(),blade.right(),blade.up(),blade.length(),life,
                blade.seed(),blade.delay(),false);
    }
    private void sweep(LivingEntity owner,Pending cut,long now,DamageSource source,BooleanSupplier active){
        if(now<=cut.start)return;
        if(cut.cosmetic){if(now>=cut.start+SlashBlade.STRIKE_TICKS)pending.remove(cut);return;}
        float before=(now-cut.start-1)/20f,after=(now-cut.start)/20f;
        Vec3 impact=null;
        for(LivingEntity target:((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class,cut.blade.bounds(),
                entity->eligible(entity)&&!cut.hit.contains(entity.getUUID())
                        &&DomainVolume.intersects(center,radius,entity.getBoundingBox()))){
            if(!active.getAsBoolean())return;
            if(!eligible(target)||!cut.blade.hits(target.getBoundingBox(),before,after,center,radius))continue;
            cut.hit.add(target.getUUID());
            CutCadence cadence=targets.get(target.getUUID());if(cadence!=null)cadence.contacted();
            if(target.getUUID().equals(cut.intended))cut.releaseReservation();
            float damage=target instanceof dev.jujutsuvfx.entity.SorcererCat cat&&cat.isGojo()?6f
                    :focused!=null?FOCUSED_DAMAGE:DomainControl.SLASH_DAMAGE;
            if(TechniqueDamageRules.hurt(target,source,damage,TechniqueDamage.SHRINE)&&active.getAsBoolean()){
                target.setDeltaMovement(Vec3.ZERO);
                if(impact==null)impact=target.position().add(0,target.getBbHeight()*.55,0);
            }
        }
        if(impact!=null&&!cut.feedback&&active.getAsBoolean()){
            cut.feedback=true;send(owner,new BladeImpact(session,impact,cut.blade.up()));
        }
        if(now>=cut.start+SlashBlade.STRIKE_TICKS){cut.releaseReservation();pending.remove(cut);}
    }
    private void send(LivingEntity owner, CustomPacketPayload packet) {
        if (owner instanceof ServerPlayer player) {
            if(dev.jujutsuvfx.platform.PacketDistributor.hasChannel(player.connection,packet.type()))PacketDistributor.sendToPlayer(player, packet);
            PlayerSpellSync.domainEffect(player,session,kind==DomainVolume.BARRAGE?dev.jujutsuvfx.core.CatSpell.BARRAGE:dev.jujutsuvfx.core.CatSpell.SHRINE,packet);
            return;
        }
        for (ServerPlayer observer : ((ServerLevel)owner.level()).players())
            if (observer.distanceToSqr(center) < 160*160 && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(observer.connection,packet.type()))
                PacketDistributor.sendToPlayer(observer, packet);
    }
    private boolean retain(UUID target){
        if(focused!=null)return false;
        Integer count=marked.get(target);
        if(count==null){if(marked.size()>=DomainControl.MAX_MARKED_TARGETS)return false;count=0;}
        if(count>=(focused!=null?4:2)||focused!=null&&random.nextFloat()>.36f)return false;
        marked.put(target,count+1);return true;
    }
    private boolean eligible(LivingEntity entity){return extraFilter.test(entity)&&entity.isAlive()&&!entity.isRemoved()&&!entity.isSpectator();}
}
