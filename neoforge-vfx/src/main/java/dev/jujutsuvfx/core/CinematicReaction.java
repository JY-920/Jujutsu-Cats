package dev.jujutsuvfx.core;

/** Bounded reaction motion, expressed on the original fight line. No global tick-rate change. */
public final class CinematicReaction {
    public static final float RATE=.25f;
    public static final double MAX_STEP=.04;
    public record Offset(double back,double side) {}
    private CinematicReaction(){}
    public static Offset offset(double age,double side) {
        double t=Math.max(0,Math.min(240,age));
        double enter=smooth(t/36),back=1.35*smooth(t/100);
        return new Offset(back,.82*Math.sin(t*.035)*enter*(side<0?-1:1));
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
