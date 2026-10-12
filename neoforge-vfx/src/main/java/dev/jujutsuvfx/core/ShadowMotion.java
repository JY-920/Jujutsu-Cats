package dev.jujutsuvfx.core;

/** Shared grounded shadow timing; the server entity stays above the terrain throughout. */
public final class ShadowMotion {
    private ShadowMotion(){}
    private static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    public static float reveal(float age,boolean retreat){
        return retreat?1-smooth((age-6)/28):smooth((age-5)/30);
    }
    public static float pool(float age,boolean retreat){
        return smooth(age/6)*(1-smooth((age-(retreat?32:38))/(retreat?8:12)));
    }
}
