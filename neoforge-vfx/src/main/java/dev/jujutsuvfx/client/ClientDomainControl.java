package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.network.DomainControl;
import dev.jujutsuvfx.network.DomainState;
import dev.jujutsuvfx.network.BladeBatch;
import dev.jujutsuvfx.network.BladeImpact;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Each cast has its own acknowledgement and release; starting the other kind never cancels it. */
public final class ClientDomainControl {
    private static final Map<Integer,Slot> SESSIONS=new HashMap<>();
    private static int serial;
    private static final class Slot {
        final int kind; final boolean networked; boolean settled;
        Slot(int kind,boolean networked){this.kind=kind;this.networked=networked;}
    }
    static int start(DomainBarrage.Focus focus,float radius,float seconds,int kind){return start(focus,radius,seconds,kind,false);}
    static int start(DomainBarrage.Focus focus,float radius,float seconds,int kind,boolean timed){
        var connection=Minecraft.getInstance().getConnection();
        boolean available=connection!=null&&connection.hasChannel(DomainControl.TYPE)&&connection.hasChannel(DomainState.TYPE)
                &&connection.hasChannel(BladeBatch.TYPE)&&connection.hasChannel(BladeImpact.TYPE);
        int session=++serial;
        SESSIONS.put(session,new Slot(kind,available));
        if(available){
            DomainControl packet=DomainControl.start(session,focus.point(),radius,seconds,kind,Minecraft.getInstance().player.getLookAngle());
            // Older optional NeoForge peers understand START, but not the added timed command action.
            boolean timedSupported=connection.hasChannel(dev.jujutsuvfx.network.DomainSettingsRequest.TYPE)
                    &&connection.hasChannel(dev.jujutsuvfx.network.DomainSettingsState.TYPE);
            PacketDistributor.sendToServer(timed&&timedSupported?packet.timed():packet);
        }
        return session;
    }
    static boolean networked(int session){Slot slot=SESSIONS.get(session);return slot!=null&&slot.networked;}
    static void close(int session){
        Slot slot=SESSIONS.get(session);
        if(slot!=null&&slot.networked&&!slot.settled)PacketDistributor.sendToServer(DomainControl.close(session));
    }
    static void finished(int session){
        Slot slot=SESSIONS.remove(session);
        if(slot!=null&&slot.networked&&slot.settled)PacketDistributor.sendToServer(DomainControl.finish(session));
    }
    public static void receive(DomainState packet){
        Slot slot=SESSIONS.get(packet.session());
        if(slot==null||!slot.networked)return;
        if(packet.phase()==DomainState.SETTLED&&!slot.settled){
            slot.settled=true;ClientEvents.damageSettled(packet.session());
        }else if(packet.phase()==DomainState.CANCELED){
            SESSIONS.remove(packet.session());ClientEvents.domainCanceled(packet.session());
        }else if(packet.phase()==DomainState.ACCEPTED&&!slot.settled){
            ClientEvents.reschedule(packet.session(),packet.remainingTicks());
        }else if(packet.phase()==DomainState.CLASH&&!slot.settled){
            ClientEvents.clashConfirmed(packet.session(),packet.peerSession(),packet.remainingTicks());
        }
    }
    static void stop(int session){
        Slot slot=SESSIONS.remove(session);var connection=Minecraft.getInstance().getConnection();
        if(slot!=null&&slot.networked&&connection!=null&&connection.hasChannel(DomainControl.TYPE))
            PacketDistributor.sendToServer(DomainControl.stop(session));
    }
    public static void receive(BladeBatch packet){
        if(packet.session()<0){CatSpells.blades(packet);return;}
        Slot slot=SESSIONS.get(packet.session());
        if(slot!=null&&slot.networked&&!slot.settled)ClientEvents.blades(packet);
    }
    public static void receive(BladeImpact packet){
        if(packet.session()<0){CatSpells.bladeImpact(packet);return;}
        Slot slot=SESSIONS.get(packet.session());
        if(slot!=null&&slot.networked&&!slot.settled)ClientEvents.bladeImpact(packet);
    }
    static void stopAll(){for(int session:List.copyOf(SESSIONS.keySet()))stop(session);}
}
