package dev.jujutsuvfx.platform;

/** Java 17 equivalents of the bounded arithmetic used by the Java 21 spell implementation. */
public final class Numbers {
    private Numbers() {}
    public static int clamp(long value,int min,int max) { return (int)Math.max(min,Math.min((long)max,value)); }
    public static long clamp(long value,long min,long max) { return Math.max(min,Math.min(max,value)); }
    public static float clamp(float value,float min,float max) { return Math.max(min,Math.min(max,value)); }
    public static double clamp(double value,double min,double max) { return Math.max(min,Math.min(max,value)); }
}
