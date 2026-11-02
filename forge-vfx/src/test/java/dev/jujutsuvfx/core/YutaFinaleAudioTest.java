package dev.jujutsuvfx.core;

/** Sound timing is driven once by synchronized phase time, never by render frequency. */
public final class YutaFinaleAudioTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var clock=new YutaFinaleAudio.Clock();
        check(clock.cue(YutaGetoBeat.RING,100,0)==YutaFinaleAudio.RING,"Ring cue missing");
        for(int packet=0;packet<100;packet++)check(clock.cue(YutaGetoBeat.RING,100,packet*.3)==0,"Repeated ring sound");
        check(clock.cue(YutaGetoBeat.CLASH,400,0)==YutaFinaleAudio.CLASH,"Discharge impact missing");
        for(int frame=0;frame<900;frame++)check(clock.cue(YutaGetoBeat.CLASH,400,frame*.1)==0,"Render-frequency sound spam");
        check(clock.cue(YutaGetoBeat.BREAKTHROUGH,490,2)==YutaFinaleAudio.BREAKTHROUGH,"Breakthrough cue missing");
        check(clock.cue(YutaGetoBeat.AFTERMATH,530,31)==0,"Late tracking replays old explosion");
        check(clock.cue(YutaGetoBeat.AFTERMATH,530,32)==0,"Late tracking plays stale tail");
        clock.reset();
        check(clock.cue(YutaGetoBeat.VOW,600,3)==YutaFinaleAudio.RING,"Fresh viewing session cannot hear vow");
        check(clock.cue(YutaGetoBeat.CLASH,700,-1)==0,"Future phase played before its start");
        check(clock.cue(YutaGetoBeat.CLASH,700,0)==YutaFinaleAudio.CLASH,"Early tracking packet permanently swallowed impact");
        check(YutaFinaleAudio.observe(YutaGetoBeat.CLASH,true,100*100,false),"Nearby non-camera player loses duel audio");
        check(!YutaFinaleAudio.observe(YutaGetoBeat.CLASH,true,129*129,false),"Remote world sound stays allocated");
        check(YutaFinaleAudio.observe(YutaGetoBeat.CLASH,true,300*300,true),"Camera's distant duel is silent");
        check(!YutaFinaleAudio.observe(YutaGetoBeat.CLASH,false,1,true),"Dead or unloaded pair keeps audio");
        check(!YutaFinaleAudio.observe(YutaGetoBeat.NONE,true,1,true),"Cancelled pair keeps audio");
        check(!YutaFinaleAudio.observe(YutaGetoBeat.DONE,true,1,false),"Completed pair keeps audio");
        check(YutaFinaleAudio.listenerGain(0,false)==1,"Close world observer loses impact gain");
        check(YutaFinaleAudio.listenerGain(64,false)>.60,"Mid-distance impact is inaudible");
        check(YutaFinaleAudio.listenerGain(96,false)>.20,"Audible-radius observer loses the pressure bed");
        check(YutaFinaleAudio.listenerGain(128,false)==0,"World boom makes remote duel global");
        check(YutaFinaleAudio.listenerGain(500,true)==1,"Cinematic boom inherits wide-shot distance loss");
        check(YutaFinaleAudio.listenerGain(Double.NaN,true)==0,"Invalid source reaches sound engine");
        for(int phase=YutaGetoBeat.NONE;phase<=YutaGetoBeat.DONE;phase++)
            for(double age:new double[]{0,12,36,55,89,109,1000}){
                var mix=YutaFinaleAudio.mix(phase,age);
                check(mix.geto()>=0&&mix.geto()<=1&&mix.rika()>=0&&mix.rika()<=1&&mix.pressure()>=0&&mix.pressure()<=1,"Unbounded layer gain");
                if(phase<YutaGetoBeat.CHARGE||phase>YutaGetoBeat.BREAKTHROUGH)
                    check(mix.geto()==0&&mix.rika()==0&&mix.pressure()==0,"Phase loop leaks into ordinary combat or aftermath");
            }
        var late=YutaFinaleAudio.mix(YutaGetoBeat.CHARGE,80);
        check(late.geto()>.1&&late.rika()>.1,"Late tracking loses current charge loops");
        check(YutaFinaleAudio.mix(YutaGetoBeat.CLASH,40).pressure()>.4,"Clash pressure missing");
        check(YutaFinaleAudio.mix(YutaGetoBeat.CLASH,0).pressure()<.25,"Pressure bed masks the initial attack transient");
        check(YutaFinaleAudio.mix(YutaGetoBeat.BREAKTHROUGH,39).pressure()<.1,"Pressure never releases");
        System.out.println("Yuta finale audio: "+checks+" checks passed");
    }
}
