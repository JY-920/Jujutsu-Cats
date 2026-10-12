package dev.jujutsuvfx.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared bounds and targeting; the server always repeats aiming against its own world. */
public final class LimitlessTechnique {
    public static final int BLUE = 0, RED = 1, PURPLE = 2;
    public static final float DEFAULT_BLUE_RADIUS = 12f, DEFAULT_RED_RADIUS = 10f, MAX_RADIUS = 50f;
    public static final int DEFAULT_BLUE_SECONDS = 10, BLUE_OPEN_TICKS = 12, BLUE_CLOSE_TICKS = 16;
    public static final float BLUE_SPEED = 12f, BLUE_MAX_SPEED = 42f;
    private static final double BLUE_TURN_PER_TICK = Math.toRadians(4.5);
    public static final int RED_CHARGE_TICKS = 20;
    public static final int RED_PREP_TICKS = 26, RED_BEAM_CHARGE_TICKS = 60, RED_BEAM_HOLD_TICKS = 16;
    public static final float RED_BEAM_RADIUS = 3f, RED_BEAM_RANGE = 96f, RED_BEAM_SPEED = 160f;
    public static final float RED_SPEED = 84f, RED_RANGE = 32f, RED_AFTERGLOW = 2.2f;
    public static final int PURPLE_PREP_TICKS = 20, PURPLE_SMALL_CHARGE_TICKS = 20, PURPLE_LARGE_CHARGE_TICKS = 60;
    public static final int PURPLE_LARGE_HOLD_TICKS = 20;
    public static final int PURPLE_CHARGE_TICKS = PURPLE_PREP_TICKS;
    public static final float PURPLE_MIN_RADIUS = 1f, PURPLE_RADIUS = 3f;
    public static final float PURPLE_SPEED = 64f, PURPLE_SMALL_RANGE = 50f, PURPLE_LARGE_RANGE = 150f, PURPLE_AFTERGLOW = 1.4f;
    public static final float PURPLE_DAMAGE = 200f;
    public static final float PURPLE_AMPLIFIED_RADIUS = 6f, PURPLE_AMPLIFIED_RANGE = 300f;
    public static final float PURPLE_AMPLIFIED_DAMAGE = 400f;

    private LimitlessTechnique() {}

    public static float blueCoreRadius(float radius) { return dev.jujutsuvfx.platform.Numbers.clamp(radius * .115f, .65f, 2.8f); }

    public static double blueSpeed(double distance) {
        // Smoothly gain speed with distance from the caster; do not tie physics to FOV or frame rate.
        double t = dev.jujutsuvfx.platform.Numbers.clamp((distance - 4) / 60, 0, 1);
        return BLUE_SPEED + (BLUE_MAX_SPEED - BLUE_SPEED) * t * t * (3 - 2 * t);
    }

    public static int chargeTicks(int kind) { return kind == PURPLE ? PURPLE_CHARGE_TICKS : RED_CHARGE_TICKS; }
    public static int purpleLaunchTicks(boolean large) {
        return large ? PURPLE_LARGE_CHARGE_TICKS + PURPLE_LARGE_HOLD_TICKS : PURPLE_SMALL_CHARGE_TICKS;
    }
    public static float shotSpeed(int kind) { return kind == PURPLE ? PURPLE_SPEED : RED_SPEED; }
    public static float shotRange(int kind, boolean largePurple) {
        return kind == PURPLE ? largePurple ? PURPLE_LARGE_RANGE : PURPLE_SMALL_RANGE : RED_RANGE;
    }
    public static float shotRange(int kind, boolean largePurple, boolean amplified) {
        return kind == PURPLE && largePurple && amplified ? PURPLE_AMPLIFIED_RANGE : shotRange(kind, largePurple);
    }
    public static float afterglow(int kind) { return kind == PURPLE ? PURPLE_AFTERGLOW : RED_AFTERGLOW; }
    public static float shotRadius(int kind, float radius) {
        return kind == PURPLE ? dev.jujutsuvfx.platform.Numbers.clamp(radius, PURPLE_MIN_RADIUS, PURPLE_AMPLIFIED_RADIUS)
                : dev.jujutsuvfx.platform.Numbers.clamp(.38f + radius * .018f, .44f, 1.25f);
    }
    public static Vec3 shotMuzzle(Entity caster, int kind, float seconds, float partial) {
        // Generic Purple calls only locate the uncommitted PREP. A committed branch uses its fixed
        // final radius through purpleMuzzle; elapsed preparation never interpolates a shot's radius.
        if (kind == PURPLE) return purpleMuzzle(caster, PURPLE_MIN_RADIUS, partial);
        return redMuzzle(caster, partial);
    }
    public static Vec3 purpleMuzzle(Entity caster, float finalRadius, float partial) {
        return purpleMuzzle(caster, finalRadius, partial, caster.getViewVector(partial));
    }
    public static Vec3 purpleMuzzle(Entity caster, float finalRadius, float partial, Vec3 aim) {
        float radius = dev.jujutsuvfx.platform.Numbers.clamp(finalRadius, PURPLE_MIN_RADIUS, PURPLE_AMPLIFIED_RADIUS);
        Vec3 desired = caster.getEyePosition(partial).add(aim.scale(1.8 + radius * 1.65));
        return clippedMuzzle(caster, desired, partial);
    }
    private static Vec3 clippedMuzzle(Entity caster, Vec3 desired, float partial) {
        Vec3 eye = caster.getEyePosition(partial);
        Vec3 loaded = eye.lerp(desired, loadedFraction(caster.level(), eye, desired));
        // Keep the charging sphere on the near face of a wall, so launch cannot leave an opaque wall
        // between the camera and the tunnel. With space available, the whole growing sphere stays in view.
        return caster.level().clip(new ClipContext(eye, loaded, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getLocation();
    }

    public static Vec3 blueMuzzle(Entity caster) {
        Vec3 eye = caster.getEyePosition();
        return trace(caster.level(), caster, eye, eye.add(caster.getLookAngle().scale(2.4))).point();
    }

    /** A bounded angular step preserves momentum, including when the player turns a full 180 degrees. */
    public static Vec3 steerBlue(Vec3 direction, Vec3 look) {
        Vec3 from = direction.normalize(), to = look.normalize();
        if (to.lengthSqr() < .5) return from;
        if (from.lengthSqr() < .5) return to;
        double angle = Math.acos(dev.jujutsuvfx.platform.Numbers.clamp(from.dot(to), -1, 1));
        if (angle <= BLUE_TURN_PER_TICK) return to;
        Vec3 axis = from.cross(to);
        if (axis.lengthSqr() < .000001) axis = FurnaceFlight.right(from);
        axis = axis.normalize();
        return from.scale(Math.cos(BLUE_TURN_PER_TICK))
                .add(axis.cross(from).scale(Math.sin(BLUE_TURN_PER_TICK))).normalize();
    }

    public static Vec3 blueFocus(Level level, Entity caster) {
        Vec3 eye = caster.getEyePosition(), direction = caster.getLookAngle();
        // Command placement observes the same player control distance as the held technique.
        return trace(level, caster, eye, eye.add(direction.scale(BlueGuidance.REACH))).point();
    }

    public static Vec3 redMuzzle(Entity caster, float partial) {
        return redMuzzle(caster, partial, caster.getViewVector(partial), 1);
    }
    public static Vec3 redMuzzle(Entity caster, float partial, Vec3 direction, float handSide) {
        Vec3 right = FurnaceFlight.right(direction);
        Vec3 up = right.cross(direction).normalize();
        return clippedMuzzle(caster, caster.getEyePosition(partial).add(direction.scale(.95))
                .add(right.scale(.23 * handSide)).add(up.scale(.08)), partial);
    }

    public static FurnaceFlight.Contact trace(Level level, Entity caster, Vec3 start, Vec3 end) {
        double fraction = loadedFraction(level, start, end);
        Vec3 limit = fraction >= 1 ? end : start.add(end.subtract(start).scale(fraction));
        return FurnaceFlight.trace(level, caster, start, limit);
    }

    public static boolean loadedSegment(Level level, Vec3 start, Vec3 end) {
        return loadedFraction(level, start, end) >= 1;
    }

    /** Visit crossed chunk boundaries, including exact diagonal corners, without reading block states. */
    private static double loadedFraction(Level level, Vec3 start, Vec3 end) {
        int x = (int) Math.floor(start.x) >> 4, z = (int) Math.floor(start.z) >> 4;
        if (!chunkLoaded(level, x, z)) return 0;
        Vec3 path = end.subtract(start);
        int sx = path.x > 0 ? 1 : path.x < 0 ? -1 : 0;
        int sz = path.z > 0 ? 1 : path.z < 0 ? -1 : 0;
        double tx = sx == 0 ? Double.POSITIVE_INFINITY
                : ((x + (sx > 0 ? 1 : 0)) * 16.0 - start.x) / path.x;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY
                : ((z + (sz > 0 ? 1 : 0)) * 16.0 - start.z) / path.z;
        double dx = sx == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(path.x);
        double dz = sz == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(path.z);
        while (Math.min(tx, tz) <= 1) {
            double entry = Math.min(tx, tz);
            boolean crossX = tx <= tz, crossZ = tz <= tx;
            // Vanilla block traversal can touch either adjacent chunk when the ray hits a corner exactly.
            if ((crossX && !chunkLoaded(level, x + sx, z))
                    || (crossZ && !chunkLoaded(level, x, z + sz)))
                return Math.max(0, entry - .0001 / Math.max(1, path.length()));
            if (crossX) { x += sx; tx += dx; }
            if (crossZ) { z += sz; tz += dz; }
            if (!chunkLoaded(level, x, z)) return Math.max(0, entry - .0001 / Math.max(1, path.length()));
        }
        return 1;
    }

    private static boolean chunkLoaded(Level level, int x, int z) {
        return level.isLoaded(new BlockPos(x << 4, 0, z << 4));
    }
}
