package dev.jujutsuvfx.core;

/** Client sound timing derived solely from the shared server phase clock. */
public final class YutaFinaleAudio {
    public static final int RING=1,CLASH=2,BREAKTHROUGH=3,AFTERMATH=4;
    public record Mix(double geto,double rika,double pressure){}
    private YutaFinaleAudio(){}

    /** World observers hear nearby pairs; a cinematic boom may deliberately follow a distant pair. */
    public static boolean observe(int phase,boolean livingPair,double distanceSquared,boolean viewed){
        return livingPair&&phase>YutaGetoBeat.NONE&&phase<YutaGetoBeat.DONE
                &&Double.isFinite(distanceSquared)&&distanceSquared>=0&&(viewed||distanceSquared<=128*128);
    }

    /** Gain uses the real source distance; the short positional boom only preserves direction. */
    public static double listenerGain(double distance,boolean viewed){
        if(!Double.isFinite(distance)||distance<0)return 0;
        return viewed?1:1-ease((distance-24)/104);
    }

    public static final class Clock {
        private int phase=-1;
        private long start=Long.MIN_VALUE;
        private boolean consumed;
        public void reset(){phase=-1;start=Long.MIN_VALUE;consumed=false;}
        public int cue(int next,long began,double age){
            if(next!=phase||began!=start){phase=next;start=began;consumed=false;}
            if(consumed||!Double.isFinite(age)||age<0)return 0;
            consumed=true;
            // Tracking a scene late starts its current bed, never a stale attack transient.
            if(age>8)return 0;
            return switch(next){
                case YutaGetoBeat.RING,YutaGetoBeat.VOW -> RING;
                case YutaGetoBeat.CLASH -> CLASH;
                case YutaGetoBeat.BREAKTHROUGH -> BREAKTHROUGH;
                case YutaGetoBeat.AFTERMATH -> AFTERMATH;
                default -> 0;
            };
        }
    }
    public static Mix mix(int phase,double age){
        if(!Double.isFinite(age)||age<0)return new Mix(0,0,0);
        return switch(phase){
            case YutaGetoBeat.CHARGE -> new Mix(.35+.50*ease(age/110),
                    .80*ease((age-25)/38),0);
            // Make room for the fast discharge attack before the sustained pressure arrives.
            case YutaGetoBeat.CLASH -> new Mix(.18,.22,.12+.70*ease(age/8));
            case YutaGetoBeat.BREAKTHROUGH -> new Mix(0,.26*(1-ease(age/20)),
                    .52*(1-ease(age/40)));
            default -> new Mix(0,0,0);
        };
    }
    private static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
