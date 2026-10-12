package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.DomainVolume;

import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.network.BladeBatch;
import dev.jujutsuvfx.network.BladeImpact;
import dev.jujutsuvfx.network.CatSpellState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import java.util.*;

/** Bounded, observer-only cast slots. Player command/input slots stay separate. */
public final class CatSpells {
    private static final Map<Integer,Slot> SLOTS=new LinkedHashMap<>();
    private static final class Slot {
        CatSpellState packet;long received;boolean closing;long destroyedAt=Long.MAX_VALUE;
        LimitlessCast limitless;FurnaceCast furnace;MalevolentShrine shrine;UnlimitedVoid gojo;
        DomainBarrage barrage;ShrineFeedback feedback;
        final Set<Rift> scars=Collections.newSetFromMap(new IdentityHashMap<>());
        Slot(CatSpellState p){packet=p;received=ClientEffectClock.ticks();}
        void stop(){
            if(barrage!=null)ClientShrineFocus.forget(packet.session());
            if(limitless!=null)limitless.stop();if(furnace!=null)furnace.stop();if(gojo!=null)gojo.stop();if(feedback!=null)feedback.stop();
            ClientEvents.RIFTS.removeIf(scars::contains);scars.clear();
        }
    }
    record DomainView(int id,int peer,Vec3 point,MalevolentShrine shrine,UnlimitedVoid gojo,ShrineFeedback feedback) {}
    private static DomainView selectedShrine,selectedVoid;
    private static DomainClash clash;
    private CatSpells() {}
    /** Read-only snapshot for the director, preferring the actor's present charge over old projectiles. */
    static float castRate(CatSpellState packet){
        if(packet==null||packet.playerCast()||packet.phase()>CatSpell.CHARGE)return 1;
        var level=Minecraft.getInstance().level;
        var entity=level==null?null:level.getEntity(packet.actor());
        return entity instanceof dev.jujutsuvfx.entity.SorcererCat cat&&cat.getUUID().equals(packet.owner())?cat.cinematicRate():1;
    }
    static CatSpellState cameraCast(UUID owner){
        Slot best=null;int rank=Integer.MIN_VALUE;long now=ClientEffectClock.ticks();
        for(Slot slot:SLOTS.values()){
            CatSpellState p=slot.packet;
            if(p.playerCast()||!p.owner().equals(owner)||p.phase()>=CatSpell.IMPACT||now-slot.received>40)continue;
            int score=(p.phase()<CatSpell.FLIGHT?100000:0)-p.age();
            if(score>rank){rank=score;best=slot;}
        }
        return best==null?null:best.packet.advance((int)Math.min(2,Math.max(0,(now-best.received)*castRate(best.packet))));
    }
    public static void receive(CatSpellState packet){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        ClientEvents.prepareSukunaEffects(mc.level);
        if(packet.session()>=0||packet.kind()<CatSpell.BLUE||packet.kind()>CatSpell.BARRAGE||packet.kind()==CatSpell.DISMANTLE||packet.kind()==CatSpell.CLEAVE||packet.phase()<0||packet.phase()>CatSpell.END
                ||packet.age()<0||packet.age()>12000||packet.stageAge()<0||packet.stageAge()>12000||packet.remaining()<0||packet.remaining()>2600
                ||!Float.isFinite(packet.radius())||packet.radius()<.1||packet.radius()>(packet.kind()==CatSpell.SHRINE?DomainVolume.maxRadius(DomainVolume.SHRINE):50)||!finite(packet.point())||!finite(packet.origin())||!finite(packet.direction()))return;
        if(packet.dimension()!=null&&!packet.dimension().equals(mc.level.dimension().location()))return;
        if(packet.prepareTicks()<0||packet.prepareTicks()>12000||packet.hand()>1
                ||!Float.isFinite(packet.influenceRadius())||packet.influenceRadius()<.1||packet.influenceRadius()>(packet.kind()==CatSpell.SHRINE?DomainVolume.maxRadius(DomainVolume.SHRINE):50))return;
        if(packet.playerCast()&&packet.owner().equals(mc.player.getUUID())){LocalDomains.observed(packet);select();return;}
        Slot slot=SLOTS.get(packet.session());
        if(slot!=null&&(!slot.packet.owner().equals(packet.owner())||slot.packet.kind()!=packet.kind()))return;
        if(packet.phase()==CatSpell.END){
            if(slot!=null&&slot.gojo!=null&&slot.packet.peer()==0&&DomainBarrierEffects.destroying(slot.gojo)){
                slot.destroyedAt=ClientEffectClock.ticks()+20;slot.received=ClientEffectClock.ticks();
                slot.gojo.reschedule(slot.destroyedAt+2);select();return;
            }
            if(slot!=null&&CatSpell.domain(packet.kind())&&slot.packet.peer()!=0){
                Slot peer=SLOTS.get(slot.packet.peer());
                if(peer!=null&&CatSpell.domain(peer.packet.kind())&&!peer.packet.owner().equals(slot.packet.owner())){
                    boolean gojoWon=packet.kind()==CatSpell.SHRINE;
                    Slot shrine=gojoWon?slot:peer,unlimited=gojoWon?peer:slot;
                    ClientDomainResolution.bind(unlimited.packet.actor(),shrine.packet.actor(),gojoWon,shrine.packet.session(),unlimited.packet.session());
                    slot.destroyedAt=ClientEffectClock.ticks()+ClientDomainResolution.TICKS;slot.received=ClientEffectClock.ticks();
                    if(slot.shrine!=null)slot.shrine.reschedule(slot.destroyedAt+2);
                    if(slot.gojo!=null)slot.gojo.reschedule(slot.destroyedAt+2);
                    if(slot.feedback!=null)slot.feedback.stop();select();return;
                }
            }
            if(slot!=null){slot.stop();SLOTS.remove(packet.session());select();}return;
        }
        if(Math.min(mc.player.distanceToSqr(packet.point()),mc.player.distanceToSqr(packet.origin()))>(packet.playerCast()?400*400:176*176))return;
        if(slot!=null&&slot.destroyedAt!=Long.MAX_VALUE)return;
        if(slot!=null&&(packet.phase()<slot.packet.phase()||packet.age()<slot.packet.age()))return;
        boolean initial=slot==null;
        if(initial){
            slot=new Slot(packet);SLOTS.put(packet.session(),slot);
            if(packet.kind()<=CatSpell.PURPLE)slot.limitless=new LimitlessCast(mc,packet);
            else if(packet.kind()==CatSpell.FURNACE)slot.furnace=new FurnaceCast(mc,packet);
            else if(packet.kind()==CatSpell.BARRAGE){
                slot.feedback=new ShrineFeedback(packet.point(),packet.radius(),packet.session());
                slot.barrage=new DomainBarrage(new DomainBarrage.Focus(packet.point(),-1),packet.radius(),packet.remaining()/20f,3,ClientEffectClock.ticks(),slot.feedback);
                Slot domain=slot;slot.barrage.sink(rift->{if(rift.persistent())domain.scars.add(rift);ClientEvents.addSlash(rift);});
            }
            else if(CatSpell.domain(packet.kind())){
                long born=ClientEffectClock.ticks()-packet.age();
                if(packet.kind()==CatSpell.SHRINE){
                    slot.shrine=new MalevolentShrine(mc,packet.point(),packet.direction(),packet.radius(),born,packet.remaining(),packet.origin());
                    slot.shrine.session=packet.session();
                    slot.feedback=new ShrineFeedback(packet.point(),packet.radius(),packet.session());
                    slot.barrage=new DomainBarrage(new DomainBarrage.Focus(packet.point(),-1),packet.radius(),packet.remaining()/20f,4,ClientEffectClock.ticks(),slot.feedback);
                    Slot domain=slot;slot.barrage.sink(rift->{if(rift.persistent())domain.scars.add(rift);ClientEvents.addSlash(rift);});
                }else slot.gojo=new UnlimitedVoid(mc,packet.point(),packet.direction(),packet.radius(),born,packet.remaining());
            }
        }else{
            if(slot.limitless!=null)slot.limitless.remoteState(packet,false);
            if(slot.furnace!=null)slot.furnace.remoteState(packet,false);
        }
        slot.packet=packet;slot.received=ClientEffectClock.ticks();
        if(CatSpell.domain(packet.kind())||packet.kind()==CatSpell.BARRAGE){
            long end=slot.received+packet.remaining();
            if(slot.shrine!=null){slot.shrine.reschedule(end);slot.barrage.reschedule(end);}
            if(slot.gojo!=null)slot.gojo.reschedule(end);
            if(slot.shrine==null&&slot.barrage!=null)slot.barrage.reschedule(end);
            if(packet.phase()==CatSpell.IMPACT&&!slot.closing){
                slot.closing=true;long closingAt=slot.received-packet.stageAge();
                if(slot.shrine!=null){slot.shrine.close(closingAt);slot.feedback.finish(mc,closingAt);
                    Set<Rift> owned=slot.scars, fading=Collections.newSetFromMap(new IdentityHashMap<>());
                    ClientEvents.RIFTS.replaceAll(rift->{if(!owned.contains(rift))return rift;Rift ending=rift.endingAt(end);ClientShrineFocus.replace(rift,ending);fading.add(ending);return ending;});
                    owned.clear();owned.addAll(fading);}
                if(slot.gojo!=null)slot.gojo.close(closingAt);
            }
        }
        select();
    }
    static void tick(Minecraft mc){
        long now=ClientEffectClock.ticks();
        for(var iterator=SLOTS.values().iterator();iterator.hasNext();){
            Slot slot=iterator.next();
            if(now>=slot.destroyedAt||now-slot.received>100||slot.packet.playerCast()&&slot.packet.phase()==CatSpell.IMPACT&&now-slot.received>=slot.packet.remaining()||slot.shrine!=null&&slot.shrine.expired(now)||slot.gojo!=null&&slot.gojo.expired(now)){
                slot.stop();iterator.remove();continue;
            }
            if(slot.limitless!=null)slot.limitless.tick(mc);if(slot.furnace!=null)slot.furnace.tick(mc);
            if(slot.shrine!=null)slot.shrine.tick(mc);
            if(slot.barrage!=null&&!slot.closing&&slot.destroyedAt==Long.MAX_VALUE)slot.barrage.tickNetworked(mc);
            if(slot.gojo!=null)slot.gojo.tick(mc);
        }
        select();
    }
    static void select(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null){selectedShrine=selectedVoid=null;clash=null;return;}
        List<DomainView> domains=new ArrayList<>(LocalDomains.views());
        for(Slot s:SLOTS.values())if(s.shrine!=null||s.gojo!=null)
            domains.add(new DomainView(s.packet.session(),s.packet.peer(),s.packet.point(),s.shrine,s.gojo,s.feedback));
        Vec3 eye=CatCinematicCamera.viewpoint();
        DomainView nearest=domains.stream().min(Comparator.comparingDouble(s->{
            float radius=s.shrine!=null?s.shrine.radius:s.gojo.radius;
            return (dev.jujutsuvfx.core.DomainVolume.contains(s.point,radius,eye)?0:1e8)+eye.distanceToSqr(s.point);
        })).orElse(null);
        selectedShrine=selectedVoid=null;
        if(nearest!=null){
            if(nearest.shrine!=null)selectedShrine=nearest;else selectedVoid=nearest;
            DomainView peer=domains.stream().filter(s->s.id==nearest.peer).findFirst().orElse(null);
            if(peer!=null){if(peer.shrine!=null)selectedShrine=peer;else if(peer.gojo!=null)selectedVoid=peer;}
        }
        if(ClientDomainResolution.active())for(Slot slot:SLOTS.values())
            if(ClientDomainResolution.actor(slot.packet.actor())&&(slot.shrine!=null||slot.gojo!=null)){
                DomainView view=new DomainView(slot.packet.session(),slot.packet.peer(),slot.packet.point(),slot.shrine,slot.gojo,slot.feedback);
                if(slot.shrine!=null)selectedShrine=view;else selectedVoid=view;
            }
        if(selectedShrine!=null&&selectedVoid!=null){
            int s=selectedShrine.id,v=selectedVoid.id;
            if(clash==null||!clash.matches(s,v))clash=new DomainClash(s,v,ClientEffectClock.ticks());
        }else clash=null;
    }
    private static boolean containsView(DomainView view,Vec3 eye,long tick,float partial){
        if(view==null)return false;
        float radius=view.shrine!=null?view.shrine.radius*view.shrine.expansion(tick,partial):view.gojo.radius*view.gojo.expansion(tick,partial);
        return dev.jujutsuvfx.core.DomainVolume.contains(view.point,radius,eye);
    }
    static boolean interior(UnlimitedVoid field,Vec3 eye,long tick,float partial){
        return dev.jujutsuvfx.core.DomainVolume.contains(field.center,field.radius*field.expansion(tick,partial),eye)
                ||clash!=null&&selectedVoid!=null&&selectedVoid.gojo==field&&containsView(selectedShrine,eye,tick,partial)
                &&!DomainClash.surrounds(selectedShrine.shrine,field,tick,partial);
    }
    static boolean interior(MalevolentShrine field,Vec3 eye,long tick,float partial){
        return dev.jujutsuvfx.core.DomainVolume.contains(field.center,field.radius*field.expansion(tick,partial),eye)
                ||clash!=null&&selectedShrine!=null&&selectedShrine.shrine==field&&containsView(selectedVoid,eye,tick,partial);
    }
    static MalevolentShrine shrine(){return selectedShrine==null?null:selectedShrine.shrine;}
    static MalevolentShrine cameraShrine(UUID owner){
        for(Slot slot:SLOTS.values())if(owner.equals(slot.packet.owner())&&slot.shrine!=null)return slot.shrine;
        return null;
    }
    static UnlimitedVoid cameraVoid(UUID owner){
        for(Slot slot:SLOTS.values())if(owner.equals(slot.packet.owner())&&slot.gojo!=null)return slot.gojo;
        return null;
    }
    static List<MalevolentShrine> shrines(){return SLOTS.values().stream().filter(s->s.shrine!=null).map(s->s.shrine).toList();}
    static UnlimitedVoid gojo(){return selectedVoid==null?null:selectedVoid.gojo;}
    static ShrineFeedback feedback(){return selectedShrine==null?null:selectedShrine.feedback;}
    static DomainClash clash(){return clash;}
    static boolean owns(Rift rift){return SLOTS.values().stream().anyMatch(s->s.scars.contains(rift));}
    static void purpleHand(com.mojang.blaze3d.vertex.PoseStack paw,dev.jujutsuvfx.entity.SorcererCat cat,float partial,boolean support) {
        boolean rightHand = (cat.punchHand() == dev.jujutsuvfx.entity.SorcererCat.RIGHT) != support;
        for(Slot slot:SLOTS.values()) {
            CatSpellState p=slot.packet;
            boolean amplified=p.large()&&p.radius()>dev.jujutsuvfx.core.LimitlessTechnique.PURPLE_RADIUS+.5f;
            if (amplified ? !rightHand : support) continue;
            if(amplified!=((cat.punchKind()&2)!=0)||p.large()!=((cat.punchKind()&1)!=0))continue;
            if(!p.owner().equals(cat.getUUID())||p.kind()!=CatSpell.PURPLE||p.large()&&!amplified
                    ||p.phase()<CatSpell.CHARGE||p.phase()>CatSpell.FLIGHT||p.phase()==CatSpell.FLIGHT&&p.stageAge()>4)continue;
            float extra=Math.clamp((ClientEffectClock.ticks()+partial-slot.received)*castRate(p),0,2);
            float age=(p.age()+extra)/20f,synthesis=(p.age()-CatSpell.prepareTicks(CatSpell.PURPLE,p.large())+extra)/20f;
            PurpleChargeHands.catGlow(paw,age,synthesis,p.phase()==CatSpell.FLIGHT?(p.stageAge()+extra)/20f:-1,cat.getId()+(support?37:0),amplified);return;
        }
    }
    static void absorb(dev.jujutsuvfx.network.UnlimitedPurple.Source source,int kind){
        Slot slot=SLOTS.values().stream().filter(s->s.packet.sourceSession()==source.session()&&s.packet.kind()==kind&&s.packet.owner().equals(source.caster())).findFirst().orElse(null);
        if(slot==null||slot.packet.kind()!=kind||!slot.packet.owner().equals(source.caster()))return;
        slot.stop();SLOTS.remove(slot.packet.session());select();
    }
    static LimitlessCast playerTechnique(UUID owner,int kind){
        return SLOTS.values().stream().filter(s->s.limitless!=null&&s.packet.playerCast()&&s.packet.owner().equals(owner)&&s.packet.kind()==kind)
                .max(Comparator.comparingInt(s->s.packet.sourceSession())).map(s->s.limitless).orElse(null);
    }
    static List<LimitlessCast> techniques(){
        Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return SLOTS.values().stream().filter(s->s.limitless!=null)
                .sorted(Comparator.comparingDouble(s->s.packet.point().distanceToSqr(camera))).map(s->s.limitless).toList();
    }
    static List<FurnaceCast> furnaces(){
        Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return SLOTS.values().stream().filter(s->s.furnace!=null)
                .sorted(Comparator.comparingDouble(s->s.packet.point().distanceToSqr(camera))).map(s->s.furnace).toList();
    }
    static LimitlessCast technique(int kind){return techniques().stream().filter(c->c.kind==kind).findFirst().orElse(null);}
    static FurnaceCast furnace(){return furnaces().stream().findFirst().orElse(null);}
    static boolean blades(BladeBatch packet){
        Slot slot=SLOTS.get(packet.session());if(slot==null||slot.barrage==null||slot.closing)return false;
        slot.barrage.accept(packet);return true;
    }
    static boolean bladeImpact(BladeImpact packet){
        Slot slot=SLOTS.get(packet.session());if(slot==null||slot.feedback==null||slot.closing)return false;
        slot.feedback.hit(Minecraft.getInstance(),packet.point(),packet.direction(),ClientEffectClock.ticks());return true;
    }
    static void camera(ViewportEvent.ComputeCameraAngles event,long now,float partial,float scale){
        for(LimitlessCast cast:techniques())cast.camera(event,now,partial,scale);
        for(FurnaceCast cast:furnaces())cast.camera(event,now,partial,scale);
    }
    static void clear(){ClientDomainResolution.clear();for(Slot slot:SLOTS.values())slot.stop();SLOTS.clear();selectedShrine=selectedVoid=null;clash=null;}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
}
