package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.RiftShape;
import dev.jujutsuvfx.core.SlashBlade;
import net.minecraft.world.phys.Vec3;

/** A fixed world frame: it never turns to follow the camera. */
record Rift(Vec3 center, Vec3 right, Vec3 up, Vec3 normal,
            float height, float duration, long startTick, float seed, Vec3 launchOffset, boolean slash, boolean persistent) {
    float age(long tick, float partialTick) { return (tick - startTick + partialTick) / 20f; }
    float opening(float age) {
        return slash ? RiftShape.slashEnvelope(age, duration) : RiftShape.envelope(age, duration);
    }
    float halfWidth(float opening) {
        if(!slash)return Math.min(.17f,height*.014f)*opening;
        return SlashBlade.halfWidth(height);
    }
    float halfHeight(float opening) { return height * 0.5f * (slash ? 1 : 0.2f + 0.8f * opening); }
    float halfDepth(float opening) { return Math.min(slash ? 0.009f : 0.02f, height * 0.0015f) * (slash ? 1 : opening); }
    Rift pose(float age) {
        if (!slash || launchOffset.lengthSqr()==0) return this;
        return new Rift(center.add(launchOffset.scale(RiftShape.approach(age))), right, up, normal,
                height, duration, startTick, seed, Vec3.ZERO, true, persistent);
    }
    Rift endingAt(long tick){return new Rift(center,right,up,normal,height,
            Math.max(0,(tick-startTick)/20f),startTick,seed,launchOffset,slash,persistent);}
    Vec3 point(double x, double y, double z) {
        return center.add(right.scale(x)).add(up.scale(y)).add(normal.scale(z));
    }
}
