package dev.jujutsuvfx.core;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** A server-selected slash style, shared with the physical cut and every observing client. */
public final class SukunaSlashStyle {
    private SukunaSlashStyle() {}

    /** Matches the domain's forty-percent chance of adding a perpendicular arm. */
    public static boolean isCross(int seed) { return random(seed, 0x18b4a521L).nextFloat() < .40f; }

    /** Uses the exact domain palettes, including its white/red emissive borders and bloom. */
    public static float materialSeed(int seed) {
        return SlashBlade.materialSeed(random(seed, 0x39d83f17L));
    }

    /** Cosmetic variation never makes the main visible blade shorter than the cut's diameter. */
    public static float mainScale(int seed) { return 1 + random(seed, 0x4eebd83dL).nextFloat() * .22f; }

    /** The server also applies this ratio to the second physical cut. */
    public static float crossScale(int seed) { return .62f + random(seed, 0x63a29105L).nextFloat() * .48f; }

    public static float lifetime(int seed) { return .46f + random(seed, 0x54e38ba9L).nextFloat() * .22f; }

    public static Vec3 secondStroke(Vec3 first, Vec3 forward) { return forward.cross(first).normalize(); }

    /** Call on the server before cutting; the transmitted stroke is already in its final orientation. */
    public static Vec3 rotateStroke(int seed, Vec3 first, Vec3 forward) {
        Vec3 up = first.subtract(forward.scale(first.dot(forward))).normalize();
        Vec3 side = secondStroke(up, forward);
        double angle = random(seed, 0x72b54a43L).nextDouble() * Math.PI * 2;
        return up.scale(Math.cos(angle)).add(side.scale(Math.sin(angle))).normalize();
    }

    private static Random random(int seed, long channel) {
        return new Random((Integer.toUnsignedLong(seed) * 0x9e3779b97f4a7c15L) ^ channel);
    }
}
