package dev.jujutsuvfx.compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
/** Forge 1.20.1 uses world-space terrain. The 1.21 Sable ABI cannot be loaded here. */
public final class CleaveCompatibility {
    private CleaveCompatibility() {}
    public static boolean aeronauticsAvailable() { return false; }
    public static Vec3 worldEye(LivingEntity owner) { return owner.getEyePosition(); }
    public static Vec3 worldPosition(ServerLevel level, Vec3 point) { return point; }
    public static Vec3 worldDirection(LivingEntity owner, Vec3 direction) { return direction.normalize(); }
    public static boolean worldBlock(ServerLevel level, BlockPos pos) { return true; }
    public static void beforeWorldBreak(ServerLevel level, BlockPos pos) {}
}
