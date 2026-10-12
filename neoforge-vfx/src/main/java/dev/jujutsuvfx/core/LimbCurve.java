package dev.jujutsuvfx.core;

/** A continuous circular joint, with rigid upper/lower ends and no detached elbow or knee caps. */
public final class LimbCurve {
    private LimbCurve() {}
    public record Point(float y,float z,float angle) {}
    public static Point bend(float y,float z,float radians,float center) {
        float angle=Math.max(-2.27f,Math.min(2.27f,radians));
        if(Math.abs(angle)<.0001f)return new Point(y,z,0);
        float width=6f/16,start=center-width*.5f,d=y-start;
        if(d<=0)return new Point(y,z,0);
        float curved=Math.min(width,d),theta=angle*curved/width,k=angle/width;
        float s=(float)Math.sin(theta),c=(float)Math.cos(theta),tail=Math.max(0,d-width);
        return new Point(start+s/k+tail*c-z*s,(1-c)/k+tail*s+z*c,theta);
    }
}
