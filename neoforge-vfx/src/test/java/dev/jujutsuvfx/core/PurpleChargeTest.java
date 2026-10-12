package dev.jujutsuvfx.core;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Run as a small Java main against the mod's runtime classpath; no game world is required. */
public final class PurpleChargeTest {
    public static void main(String[] args) {
        require(LimitlessTechnique.PURPLE_PREP_TICKS == 20, "preparation lasts one second");
        require(LimitlessTechnique.PURPLE_SMALL_CHARGE_TICKS == 20, "small branch synthesizes for one second");
        require(LimitlessTechnique.PURPLE_LARGE_CHARGE_TICKS == 60, "large branch synthesizes for three seconds");
        require(LimitlessTechnique.PURPLE_LARGE_HOLD_TICKS == 20, "large branch holds for one second after synthesis");
        require(LimitlessTechnique.purpleLaunchTicks(true) == 80, "large branch launches after synthesis and hold");
        require(LimitlessTechnique.purpleLaunchTicks(false) == 20, "small branch launches without the large hold");
        require(LimitlessTechnique.PURPLE_CHARGE_TICKS == LimitlessTechnique.PURPLE_PREP_TICKS, "compatibility alias denotes preparation");
        near(LimitlessTechnique.PURPLE_MIN_RADIUS, 1, "small branch final radius");
        near(LimitlessTechnique.PURPLE_RADIUS, 3, "large branch final radius");
        near(LimitlessTechnique.shotRadius(LimitlessTechnique.PURPLE, 1), 1, "small projectile keeps committed radius");
        near(LimitlessTechnique.shotRadius(LimitlessTechnique.PURPLE, 3), 3, "large projectile keeps committed radius");
        near(LimitlessTechnique.shotRadius(LimitlessTechnique.PURPLE, -1), 1, "invalid radius clamps to small bound");
        near(LimitlessTechnique.shotRadius(LimitlessTechnique.PURPLE, 5), 5, "radius five is valid for amplified projectiles");
        near(LimitlessTechnique.shotRadius(LimitlessTechnique.PURPLE, 6), 6, "amplified projectile retains radius six");
        near(LimitlessTechnique.shotRadius(LimitlessTechnique.PURPLE, 100), 6, "untrusted radius cannot exceed amplified bound");
        Vec3 start = new Vec3(0, 0, 0), end = new Vec3(0, 0, 3.2);
        AABB smallInside = new AABB(.99, -.25, 1, 1.25, .25, 1.5);
        AABB smallOutside = new AABB(1.01, -.25, 1, 1.25, .25, 1.5);
        require(SweptSphere.distanceSquared(start, end, smallInside) < 1, "small branch hits just inside radius one");
        require(SweptSphere.distanceSquared(start, end, smallOutside) > 1, "small branch excludes targets outside radius one");
        AABB largeInside = new AABB(2.99, -.25, 1, 3.25, .25, 1.5);
        AABB largeOutside = new AABB(3.01, -.25, 1, 3.25, .25, 1.5);
        double d2 = SweptSphere.distanceSquared(start, end, largeInside);
        require(d2 < 9 && d2 > 1, "large branch covers targets beyond the small branch");
        require(SweptSphere.distanceSquared(start, end, largeOutside) > 9, "large branch excludes targets outside radius three");
        near(LimitlessTechnique.shotRange(LimitlessTechnique.PURPLE, false), 50, "small branch travels fifty blocks");
        near(LimitlessTechnique.shotRange(LimitlessTechnique.PURPLE, true), 150, "large branch travels one hundred fifty blocks");
        near(LimitlessTechnique.shotSpeed(LimitlessTechnique.PURPLE), 64, "flight speed preserved");
        System.out.println("Purple branches: preparation/synthesis timings, fixed small/large radii, swept hit bounds, speed and range passed.");
    }
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < .0001f, message + ": " + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
