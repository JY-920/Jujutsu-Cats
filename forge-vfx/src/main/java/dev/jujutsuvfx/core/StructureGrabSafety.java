package dev.jujutsuvfx.core;

/** A floor exclusion shared by scanning, assembly and the detachment seam. Units are world blocks. */
public final class StructureGrabSafety {
    private StructureGrabSafety() {}
    public static boolean allowed(double feetX,double feetY,double feetZ,double x,double y,double z) {
        double dx=x-feetX,dz=z-feetZ;
        // The horizontal footprint includes a full block plus the one-block detachment seam.
        // Roofs sufficiently above the caster remain valid; everything below his feet is protected.
        return y>feetY+2.5 || dx*dx+dz*dz>4.5*4.5;
    }
    public static boolean bodyAllowed(double feetX,double feetY,double feetZ,double x,double y,double z,double radius) {
        if(!Double.isFinite(radius)||radius<=0)return false;
        double dx=x-feetX,dz=z-feetZ;
        return y-radius>feetY+2.5 || Math.hypot(dx,dz)>radius+4.5;
    }
}
