package dev.jujutsuvfx.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/** Forge1.20 has no supported Sable2 ABI; the caller uses transient visual blocks instead. */
public final class StructureThrowBridge {
    public record Body(UUID id,Vec3 center,double radius) {}
    private StructureThrowBridge() {}
    public static Body acquire(LivingEntity owner,Vec3 around){return null;}
    public static Body assemble(LivingEntity owner,List<BlockPos> blocks){return null;}
    public static Body inspect(LivingEntity owner,UUID id){return null;}
    public static boolean velocity(LivingEntity owner,UUID id,Vec3 velocity,Vec3 spin){return false;}
    public static List<UUID> cut(LivingEntity owner,UUID id,Vec3 normal,Vec3 forward){return List.of();}
}
