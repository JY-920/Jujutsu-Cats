package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Each player domain retains its own clock, sound, blades and settlement acknowledgement. */
final class LocalDomains {
    private static final Map<Integer,Slot> SLOTS=new LinkedHashMap<>();
    private static Slot selectedShrine,selectedVoid;
    static final class Slot {
        final int kind;
        final ClientEvents.Timing time;
        MalevolentShrine shrine;UnlimitedVoid gojo;ShrineFeedback feedback;DomainBarrage barrage;
        DomainClash clash;int peer,visualId,visualPeer;
        final Set<Rift> scars=Collections.newSetFromMap(new IdentityHashMap<>());
        Slot(int kind,int session,long end){this.kind=kind;time=new ClientEvents.Timing(session,end);}
        Vec3 center(){return shrine!=null?shrine.center:gojo!=null?gojo.center:Minecraft.getInstance().player.position();}
    }
    private LocalDomains(){}
    static int start(Minecraft mc,boolean gojo,float radius,float seconds){return start(mc,gojo,radius,seconds,false);}
    static int start(Minecraft mc,boolean gojo,float radius,float seconds,boolean timed){
        long now=ClientEffectClock.ticks();Slot slot;
        if(gojo){
            UnlimitedVoid visual=new UnlimitedVoid(mc,radius,seconds);
            int session=ClientDomainControl.start(new DomainBarrage.Focus(visual.center,-1),radius,seconds,DomainVolume.UNLIMITED_VOID,timed);
            slot=new Slot(DomainVolume.UNLIMITED_VOID,session,now+Math.round(seconds*20));slot.gojo=visual;
            TechniqueHands.begin(SkillGesture.VOID,session,null,false);
        }else{
            DomainBarrage.Focus focus=DomainBarrage.focus(mc);
            MalevolentShrine visual=new MalevolentShrine(mc,focus,radius,seconds);focus=new DomainBarrage.Focus(visual.center,focus.entityId());
            int session=ClientDomainControl.start(focus,radius,seconds,DomainVolume.SHRINE,timed);
            slot=new Slot(DomainVolume.SHRINE,session,now+Math.round(seconds*20));slot.shrine=visual;visual.session=session;
            slot.feedback=new ShrineFeedback(visual.center,radius,session);slot.barrage=new DomainBarrage(focus,radius,seconds,4,now,slot.feedback);
            installSink(slot);TechniqueHands.begin(SkillGesture.SHRINE,session,null,false);
        }
        SLOTS.put(slot.time.session,slot);
        if(!ClientDomainControl.networked(slot.time.session))for(Slot other:List.copyOf(SLOTS.values())){
            if(other==slot||other.kind==slot.kind||other.kind==DomainVolume.BARRAGE||other.time.closing()
                    ||ClientDomainControl.networked(other.time.session))continue;
            float otherRadius=other.shrine!=null?other.shrine.radius:other.gojo.radius;
            if(DomainVolume.overlaps(slot.center(),radius,other.center(),otherRadius)){
                clash(slot.time.session,other.time.session,DomainControl.CLASH_TICKS);break;
            }
        }
        select();return 1;
    }
    static int barrage(Minecraft mc){
        long now=ClientEffectClock.ticks();DomainBarrage.Focus focus=DomainBarrage.focus(mc);
        int session=ClientDomainControl.start(focus,12,DomainControl.DEFAULT_SECONDS,DomainVolume.BARRAGE);
        Slot slot=new Slot(DomainVolume.BARRAGE,session,now+DomainControl.DEFAULT_SECONDS*20);
        slot.feedback=new ShrineFeedback(focus.point(),12,session);
        slot.barrage=new DomainBarrage(focus,12,DomainControl.DEFAULT_SECONDS,3,now,slot.feedback);
        installSink(slot);SLOTS.put(session,slot);select();return 1;
    }
    private static void installSink(Slot slot){slot.barrage.sink(rift->{if(rift.persistent())slot.scars.add(rift);ClientEvents.addSlash(rift);});}
    static void tick(Minecraft mc){
        long now=ClientEffectClock.ticks();
        for(Slot slot:List.copyOf(SLOTS.values())){
            if(now>=slot.time.release){ClientDomainControl.finished(slot.time.session);end(slot.time.session);continue;}
            if(!slot.time.waiting&&now>=slot.time.deadline&&!ClientDomainControl.networked(slot.time.session))settle(slot,false);
            if(slot.shrine!=null)slot.shrine.tick(mc);if(slot.gojo!=null)slot.gojo.tick(mc);
            if(slot.barrage!=null&&!slot.time.waiting&&!slot.time.closing()){
                if(ClientDomainControl.networked(slot.time.session))slot.barrage.tickNetworked(mc);
                else if(!slot.barrage.expired(now)&&(slot.shrine==null||slot.shrine.attacking(now)))slot.barrage.tick(mc);
            }
            if(now%20==0)slot.scars.removeIf(rift->!ClientEvents.RIFTS.contains(rift));
        }
        select();
    }
    static void reschedule(int session,int remaining){
        Slot slot=SLOTS.get(session);if(slot==null||slot.time.closing()||remaining<1)return;
        slot.time.deadline=ClientEffectClock.after(Math.min(remaining,2400));slot.time.waiting=false;
        if(slot.shrine!=null)slot.shrine.reschedule(slot.time.deadline);
        if(slot.gojo!=null)slot.gojo.reschedule(slot.time.deadline);
        if(slot.barrage!=null)slot.barrage.reschedule(slot.time.deadline);
    }
    static void clash(int session,int peer,int remaining){
        Slot a=SLOTS.get(session),b=SLOTS.get(peer);
        if(a==null||b==null||a.kind==b.kind||a.time.closing()||b.time.closing()
                ||a.kind==DomainVolume.BARRAGE||b.kind==DomainVolume.BARRAGE)return;
        reschedule(session,remaining);reschedule(peer,remaining);a.peer=peer;b.peer=session;
        Slot shrine=a.shrine!=null?a:b,gojo=a.gojo!=null?a:b;
        if(a.clash==null||!a.clash.matches(shrine.time.session,gojo.time.session))
            a.clash=new DomainClash(shrine.time.session,gojo.time.session,ClientEffectClock.ticks());
        b.clash=a.clash;select();
    }
    private static void settle(Slot slot,boolean early){
        if(slot.time.closing())return;slot.time.waiting=true;long now=ClientEffectClock.ticks();
        if(slot.shrine!=null)slot.shrine.awaitSettlement(now);if(slot.gojo!=null)slot.gojo.awaitSettlement(now);
        if(ClientDomainControl.networked(slot.time.session)){if(early)ClientDomainControl.close(slot.time.session);}
        else settled(slot.time.session);
    }
    static void settled(int session){
        Slot slot=SLOTS.get(session);if(slot==null||slot.time.closing())return;
        long now=ClientEffectClock.ticks();slot.time.waiting=true;slot.time.release=now+DomainControl.CLOSE_TICKS;
        slot.barrage=null;
        if(slot.feedback!=null)slot.feedback.finish(Minecraft.getInstance(),now);
        if(slot.shrine!=null)slot.shrine.close(now);if(slot.gojo!=null)slot.gojo.close(now);
        Set<Rift> fading=Collections.newSetFromMap(new IdentityHashMap<>());
        ClientEvents.RIFTS.replaceAll(rift->{if(!slot.scars.contains(rift))return rift;Rift ended=rift.endingAt(slot.time.release);ClientShrineFocus.replace(rift,ended);fading.add(ended);return ended;});
        slot.scars.clear();slot.scars.addAll(fading);
    }
    static void blades(BladeBatch packet){Slot slot=SLOTS.get(packet.session());if(slot!=null&&!slot.time.waiting&&slot.barrage!=null)slot.barrage.accept(packet);}
    static void impact(BladeImpact packet){Slot slot=SLOTS.get(packet.session());if(slot!=null&&!slot.time.closing()&&slot.feedback!=null)
        slot.feedback.hit(Minecraft.getInstance(),packet.point(),packet.direction(),ClientEffectClock.ticks());}
    static void end(int session){
        Slot slot=SLOTS.remove(session);if(slot==null)return;
        ClientDomainControl.stop(session);ClientShrineFocus.end(session);
        TechniqueHands.cancel(slot.gojo!=null?SkillGesture.VOID:SkillGesture.SHRINE,session);
        if(slot.gojo!=null)slot.gojo.stop();if(slot.feedback!=null)slot.feedback.stop();
        ClientEvents.RIFTS.removeIf(slot.scars::contains);slot.scars.clear();select();
    }
    static void closeAll(){for(Slot slot:List.copyOf(SLOTS.values()))settle(slot,true);}
    static void clear(){for(int id:List.copyOf(SLOTS.keySet()))end(id);selectedShrine=selectedVoid=null;}
    static void stopKind(boolean gojo){for(Slot slot:List.copyOf(SLOTS.values()))if((slot.gojo!=null)==gojo)end(slot.time.session);}
    static boolean empty(){return SLOTS.isEmpty();}
    static void boundary(boolean enclosed){for(Slot slot:SLOTS.values()){if(slot.shrine!=null)slot.shrine.enclosed=false;if(slot.gojo!=null)slot.gojo.enclosed=enclosed;}}
    private static void select(){
        Minecraft mc=Minecraft.getInstance();selectedShrine=selectedVoid=null;if(mc.player==null)return;
        Slot nearest=SLOTS.values().stream().filter(s->s.shrine!=null||s.gojo!=null)
                .min(Comparator.comparingDouble(s->mc.player.distanceToSqr(s.center()))).orElse(null);
        if(nearest==null)nearest=SLOTS.values().stream().reduce((a,b)->b).orElse(null);
        if(nearest==null)return;
        if(nearest.gojo!=null)selectedVoid=nearest;else selectedShrine=nearest;
        Slot peer=SLOTS.get(nearest.peer);if(peer!=null){if(peer.gojo!=null)selectedVoid=peer;else selectedShrine=peer;}
    }
    static void observed(CatSpellState p){
        Slot slot=SLOTS.get(p.sourceSession());if(slot==null)return;
        int expected=slot.kind==DomainVolume.UNLIMITED_VOID?dev.jujutsuvfx.core.CatSpell.VOID:dev.jujutsuvfx.core.CatSpell.SHRINE;
        if(p.kind()!=expected)return;
        slot.visualId=p.session();slot.visualPeer=p.peer();
        if(p.phase()!=dev.jujutsuvfx.core.CatSpell.END&&!slot.time.closing())reschedule(p.sourceSession(),p.remaining());
    }
    static List<CatSpells.DomainView> views(){
        return SLOTS.values().stream().filter(s->s.shrine!=null||s.gojo!=null).map(s->new CatSpells.DomainView(
                s.visualId==0?s.time.session:s.visualId,s.visualId==0?s.peer:s.visualPeer,s.center(),s.shrine,s.gojo,s.feedback)).toList();
    }
    static Slot shrine(){return selectedShrine;}
    static Slot gojo(){return selectedVoid;}
    static List<MalevolentShrine> shrines(){return SLOTS.values().stream().filter(s->s.shrine!=null).map(s->s.shrine).toList();}
}
