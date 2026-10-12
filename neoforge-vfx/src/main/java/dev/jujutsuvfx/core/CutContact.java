package dev.jujutsuvfx.core;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** Exact finite-slash contact against an axis-aligned box, in either world or structure-local space. */
public final class CutContact {
    private static final double EPSILON = 1e-7;

    private CutContact() {}

    /**
     * Returns the squared distance from planeCenter to the box/plane intersection, or positive
     * infinity when they do not intersect. Compare the result with the slash disk radius squared.
     * To handle an oriented physical body, transform the plane into its local space first; rigid
     * transforms preserve this distance. The normal need not be normalized.
     *
     * The closest point is the solution of a box-constrained quadratic minimization on the plane.
     * Each coordinate is free or fixed at either box face, giving only 27 active sets. This also
     * handles a plane coincident with a face, an edge or a single corner without polygon allocation.
     */
    public static double distanceSquared(AABB box, Vec3 planeCenter, Vec3 planeNormal) {
        Objects.requireNonNull(box, "box");
        requireFinite(planeCenter, "planeCenter");
        requireFinite(planeNormal, "planeNormal");
        if (!finite(box.minX) || !finite(box.minY) || !finite(box.minZ)
                || !finite(box.maxX) || !finite(box.maxY) || !finite(box.maxZ))
            throw new IllegalArgumentException("Box must contain finite coordinates");
        double length = Math.hypot(Math.hypot(planeNormal.x, planeNormal.y), planeNormal.z);
        if (!finite(length) || length < 1e-12)
            throw new IllegalArgumentException("Plane normal must be finite and nonzero");
        double nx = planeNormal.x / length, ny = planeNormal.y / length, nz = planeNormal.z / length;
        // Translate before solving so the plane is n dot q = 0 and the objective is simply |q|^2.
        double lx = box.minX - planeCenter.x, hx = box.maxX - planeCenter.x;
        double ly = box.minY - planeCenter.y, hy = box.maxY - planeCenter.y;
        double lz = box.minZ - planeCenter.z, hz = box.maxZ - planeCenter.z;
        if (!finite(lx) || !finite(hx) || !finite(ly) || !finite(hy) || !finite(lz) || !finite(hz))
            throw new IllegalArgumentException("Box is too far from the plane center");

        double minimum = nx * (nx >= 0 ? lx : hx) + ny * (ny >= 0 ? ly : hy) + nz * (nz >= 0 ? lz : hz);
        double maximum = nx * (nx >= 0 ? hx : lx) + ny * (ny >= 0 ? hy : ly) + nz * (nz >= 0 ? hz : lz);
        if (minimum > EPSILON || maximum < -EPSILON) return Double.POSITIVE_INFINITY;
        if (lx <= 0 && hx >= 0 && ly <= 0 && hy >= 0 && lz <= 0 && hz >= 0) return 0;

        double best = Double.POSITIVE_INFINITY;
        for (int sx = 0; sx < 3; sx++) for (int sy = 0; sy < 3; sy++) for (int sz = 0; sz < 3; sz++) {
            // State 0 is free, state 1 fixes the minimum face, state 2 fixes the maximum face.
            double qx = sx == 0 ? 0 : sx == 1 ? lx : hx;
            double qy = sy == 0 ? 0 : sy == 1 ? ly : hy;
            double qz = sz == 0 ? 0 : sz == 1 ? lz : hz;
            double fixedDot = nx * qx + ny * qy + nz * qz;
            double freeNormSquared = (sx == 0 ? nx * nx : 0)
                    + (sy == 0 ? ny * ny : 0) + (sz == 0 ? nz * nz : 0);
            if (freeNormSquared > 0) {
                double lambda = fixedDot / freeNormSquared;
                if (sx == 0) qx = -lambda * nx;
                if (sy == 0) qy = -lambda * ny;
                if (sz == 0) qz = -lambda * nz;
            } else if (Math.abs(fixedDot) > EPSILON) {
                continue;
            }
            if (!finite(qx) || !finite(qy) || !finite(qz)
                    || qx < lx - EPSILON || qx > hx + EPSILON
                    || qy < ly - EPSILON || qy > hy + EPSILON
                    || qz < lz - EPSILON || qz > hz + EPSILON) continue;
            // Free solutions already satisfy the plane analytically. This catches overflow or
            // numerical cancellation rather than accepting a nearby but unrelated box face.
            if (Math.abs(nx * qx + ny * qy + nz * qz) > EPSILON) continue;
            double distance = qx * qx + qy * qy + qz * qz;
            best = Math.min(best, Math.min(Double.MAX_VALUE, distance));
        }
        return best;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
    private static void requireFinite(Vec3 value, String name) {
        Objects.requireNonNull(value, name);
        if (!finite(value.x) || !finite(value.y) || !finite(value.z))
            throw new IllegalArgumentException(name + " must contain finite coordinates");
    }
}
