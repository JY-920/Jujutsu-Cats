package dev.jujutsuvfx.core;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The renderer and the server use the same expanding sphere, including partial boundary overlap. */
public final class DomainVolume {
    public static final int SHRINE = 0, BARRAGE = 1, UNLIMITED_VOID = 2;
    public static final float DEFAULT_SHRINE_RADIUS = 64, DEFAULT_VOID_RADIUS = 40;
    private DomainVolume() {}
    public static int maxRadius(int kind){return kind==SHRINE?80:50;}
    public static boolean encloses(Vec3 outer,double outerRadius,Vec3 inner,double innerRadius) {
        return innerRadius>0 && outerRadius>innerRadius && outer.distanceTo(inner)+innerRadius<outerRadius-.05;
    }

    public static boolean contains(Vec3 center,double radius,Vec3 point){
        return radius>0&&center.distanceToSqr(point)<radius*radius;
    }
    public static boolean validKind(int kind) { return kind >= SHRINE && kind <= UNLIMITED_VOID; }
    public static boolean overlaps(Vec3 a, double radiusA, Vec3 b, double radiusB) {
        double reach=radiusA+radiusB;
        return radiusA>0 && radiusB>0 && a.distanceToSqr(b)<reach*reach;
    }

    public static float expansion(int kind, float ageSeconds) {
        if (kind == BARRAGE) return 1;
        float t = kind == UNLIMITED_VOID ? ageSeconds / .20f : (ageSeconds - .4f) / 1.8f;
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Void arrests the whole declared volume as the first white frame appears. */
    public static float freezeRadius(int kind,float radius,float ageSeconds) {
        return kind==UNLIMITED_VOID ? radius : radius*expansion(kind,ageSeconds);
    }

    public static boolean intersects(Vec3 center, double radius, AABB bounds) {
        if (radius <= 0) return false;
        double x = Math.clamp(center.x, bounds.minX, bounds.maxX) - center.x;
        double y = Math.clamp(center.y, bounds.minY, bounds.maxY) - center.y;
        double z = Math.clamp(center.z, bounds.minZ, bounds.maxZ) - center.z;
        return x * x + y * y + z * z <= radius * radius;
    }
}
