package dev.jujutsuvfx.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared aiming and swept collision for the renderer's fire arrow; never edits blocks. */
public final class FurnaceFlight {
    public static final int CHARGE_TICKS = 32;
    public static final float SPEED = 72, RANGE = 72, AFTERGLOW = 4.2f;
    public static final float DEFAULT_RADIUS = 12, MAX_RADIUS = 50;
    private FurnaceFlight() {}

    public record Contact(Vec3 point, boolean hit) {}
    public static Vec3 right(Vec3 direction) {
        Vec3 axis = direction.cross(new Vec3(0, 1, 0));
        return axis.lengthSqr() < .0001 ? new Vec3(1, 0, 0) : axis.normalize();
    }
    public static Vec3 bow(Entity caster, float partial) {
        Vec3 direction = caster.getViewVector(partial), right = right(direction);
        Vec3 up = right.cross(direction).normalize();
        return caster.getEyePosition(partial).add(direction.scale(1.35)).add(right.scale(.45)).add(up.scale(-.30));
    }
    public static Contact trace(Level level, Entity caster, Vec3 start, Vec3 end) {
        Vec3 path = end.subtract(start);
        int checks = Math.max(1, (int) Math.ceil(path.length() / 2));
        for (int i = 1; i <= checks; i++) {
            if (!level.isLoaded(BlockPos.containing(start.add(path.scale((double) i / checks))))) {
                end = start.add(path.scale((double) (i - 1) / checks)); break;
            }
        }
        HitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var entity = ProjectileUtil.getEntityHitResult(level, caster, start, limit,
                new AABB(start, limit).inflate(.24),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != caster
                        && !caster.isAlliedTo(e) && (!(e instanceof Player player)
                        || !player.isCreative() && !player.getAbilities().invulnerable
                        && (level.isClientSide || !(caster instanceof LivingEntity owner)
                        || dev.jujutsuvfx.server.TechniquePlayerRules.canAffect(owner,player))), .16f);
        if (entity != null) return new Contact(entity.getLocation(), true);
        if (block.getType() != HitResult.Type.MISS)
            return new Contact(limit.subtract(end.subtract(start).normalize().scale(.035)), true);
        return new Contact(end, false);
    }
}
