package dev.jujutsuvfx.core;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/** Exact distance from a segment to an axis-aligned box, including both rounded end caps. */
public final class SweptSphere {
    private SweptSphere() {}

    public static double distanceSquared(Vec3 start, Vec3 end, AABB box) {
        Vec3 delta = end.subtract(start);
        double[] a = {start.x, start.y, start.z}, d = {delta.x, delta.y, delta.z};
        double[] low = {box.minX, box.minY, box.minZ}, high = {box.maxX, box.maxY, box.maxZ};
        double[] cuts = new double[8]; cuts[0] = 0; cuts[1] = 1;
        int count = 2;
        for (int axis = 0; axis < 3; axis++) if (Math.abs(d[axis]) > 1e-12) {
            double t0 = (low[axis] - a[axis]) / d[axis], t1 = (high[axis] - a[axis]) / d[axis];
            if (t0 > 0 && t0 < 1) cuts[count++] = t0;
            if (t1 > 0 && t1 < 1) cuts[count++] = t1;
        }
        Arrays.sort(cuts, 0, count);
        double best = Math.min(pointDistance(start, box), pointDistance(end, box));
        for (int i = 0; i < count - 1; i++) {
            double left = cuts[i], right = cuts[i + 1], middle = (left + right) * .5;
            double quadratic = 0, linear = 0;
            for (int axis = 0; axis < 3; axis++) {
                double p = a[axis] + d[axis] * middle;
                if (p >= low[axis] && p <= high[axis]) continue;
                double offset = a[axis] - (p < low[axis] ? low[axis] : high[axis]);
                quadratic += d[axis] * d[axis]; linear += offset * d[axis];
            }
            double t = quadratic < 1e-18 ? middle : dev.jujutsuvfx.platform.Numbers.clamp(-linear / quadratic, left, right);
            best = Math.min(best, pointDistance(start.add(delta.scale(t)), box));
            if (best < 1e-12) return 0;
        }
        return best;
    }

    private static double pointDistance(Vec3 point, AABB box) {
        double x = point.x - dev.jujutsuvfx.platform.Numbers.clamp(point.x, box.minX, box.maxX);
        double y = point.y - dev.jujutsuvfx.platform.Numbers.clamp(point.y, box.minY, box.maxY);
        double z = point.z - dev.jujutsuvfx.platform.Numbers.clamp(point.z, box.minZ, box.maxZ);
        return x * x + y * y + z * z;
    }
}
