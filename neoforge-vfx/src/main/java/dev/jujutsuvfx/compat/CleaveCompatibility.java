package dev.jujutsuvfx.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Optional coordinates only. The outer class and its signatures do not load physics engine types. */
public final class CleaveCompatibility {
    private static final boolean SABLE = ModList.get().isLoaded("sable");
    private static final boolean AERONAUTICS = SABLE && (ModList.get().isLoaded("aeronautics")
            || ModList.get().isLoaded("aeronautics_bundled"));
    private CleaveCompatibility() {}
    public static boolean aeronauticsAvailable() { return AERONAUTICS; }
    public static Vec3 worldEye(LivingEntity owner) { return worldPosition((ServerLevel) owner.level(), owner.getEyePosition()); }
    public static Vec3 worldPosition(ServerLevel level, Vec3 position) {
        return SABLE ? SableAccess.position(level, position) : position;
    }
    public static Vec3 worldDirection(LivingEntity owner, Vec3 direction) {
        return SABLE ? SableAccess.direction(owner, direction) : direction.normalize();
    }
    public static boolean worldBlock(ServerLevel level, BlockPos pos) {
        return !SABLE || SableAccess.worldBlock(level, pos);
    }
    public static void beforeWorldBreak(ServerLevel level, BlockPos pos) {
        // Native connector removal may otherwise destroy a distant linked block on a moving structure.
        // Detach the connection, without adding the remote partner to the destruction batch.
        if (ModList.get().isLoaded("simulated")) SableDestructiveBlocks.beforeDestroy(level, pos);
    }
    private static final class SableAccess {
        static Vec3 position(ServerLevel level, Vec3 point) {
            return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, point);
        }
        static Vec3 direction(LivingEntity owner, Vec3 direction) {
            var containing = dev.ryanhcode.sable.Sable.HELPER.getContaining(owner);
            return (containing == null ? direction : containing.logicalPose().transformNormal(direction)).normalize();
        }
        static boolean worldBlock(ServerLevel level, BlockPos pos) {
            return dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos) == null;
        }
    }
}
