package dev.jujutsuvfx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** A short render-only survivor transition; the server has already removed the losing field's effects. */
public final class ClientDomainResolution {
    private static ClientLevel world;
    private static int gojo=-1,sukuna=-1,shrineSession,voidSession;
    private static boolean gojoWins;
    private static long started=Long.MIN_VALUE;
    static final int TICKS=20;
    private ClientDomainResolution(){}
    public static void receive(int gojoId,int sukunaId,boolean winner){
        ClientLevel current=Minecraft.getInstance().level;if(current==null)return;
        if(current!=world){clear();world=current;}
        if(active()&&gojo==gojoId&&sukuna==sukunaId&&gojoWins==winner)return;
        gojo=gojoId;sukuna=sukunaId;gojoWins=winner;started=ClientEffectClock.ticks();
    }
    static void bind(int gojoId,int sukunaId,boolean winner,int shrine,int unlimited){
        receive(gojoId,sukunaId,winner);shrineSession=shrine;voidSession=unlimited;
    }
    static boolean active(){return world!=null&&world==Minecraft.getInstance().level&&started!=Long.MIN_VALUE
            &&ClientEffectClock.ticks()-started>=0&&ClientEffectClock.ticks()-started<TICKS;}
    static boolean actor(int id){return active()&&(id==gojo||id==sukuna);}
    static float progress(float partial){return active()?(float)Math.max(0,Math.min(1,(ClientEffectClock.ticks()+partial-started)/TICKS)):0;}
    static float direction(DomainClash clash){return active()&&clash!=null&&clash.matches(shrineSession,voidSession)?gojoWins?1:-1:0;}
    static void clear(){world=null;started=Long.MIN_VALUE;gojo=sukuna=-1;shrineSession=voidSession=0;}
}
