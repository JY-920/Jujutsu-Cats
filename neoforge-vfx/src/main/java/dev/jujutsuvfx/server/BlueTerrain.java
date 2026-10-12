package dev.jujutsuvfx.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** A short swept core carves its path without spawning a vanilla explosion every tick. */
final class BlueTerrain {
    private static final int BLOCK_BUDGET = 96;
    private static long lastBlueFailureLog;
    private record Candidate(BlockPos pos, BlockState state) {}

    private BlueTerrain() {}

    static Vec3 sweep(LivingEntity owner, Vec3 start, Vec3 end, float radius, BooleanSupplier current) {
        if (!current.getAsBoolean()) return start;
        ServerLevel level = (ServerLevel) owner.level();
        // Flight is independent of carving. Only the loaded world boundary limits travel;
        // a protected block, disabled destruction or a spent budget cannot stall Blue.
        Vec3 destination = start;
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) / .20));
        for (int step = 1; step <= steps; step++) {
            if (!current.getAsBoolean()) return start;
            Vec3 point = start.lerp(end, (double) step / steps);
            BlockPos pos = BlockPos.containing(point);
            if (!level.getWorldBorder().isWithinBounds(pos)
                    || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) break;
            destination = point;
        }
        try {
            carveBlue(owner, level, start, destination, radius, current);
        } catch (RuntimeException failure) {
            // Third-party block callbacks can fail after denying or replacing a block.
            // Isolate terrain work from the active cast; do not repeatedly flood the log.
            long now = System.nanoTime();
            if (lastBlueFailureLog == 0 || now - lastBlueFailureLog >= 30_000_000_000L) {
                lastBlueFailureLog = now;
                com.mojang.logging.LogUtils.getLogger().warn("Blue terrain update failed; flight continues. Further reports suppressed for 30 seconds", failure);
            }
        }
        return current.getAsBoolean() ? destination : start;
    }

    private static void carveBlue(LivingEntity owner, ServerLevel level, Vec3 start, Vec3 end,
                                  float radius, BooleanSupplier current) {
        if (!current.getAsBoolean() || !allowed(owner, level) || !TechniqueTerrainRules.canGrief(owner)) return;
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) / .20));
        List<Candidate> blocks = new ArrayList<>();
        Set<BlockPos> examined = new HashSet<>();
        scan: for (int step = 1; step <= steps; step++) {
            if (!current.getAsBoolean() || !allowed(owner, level)) return;
            Vec3 point = start.lerp(end, (double) step / steps);
            // Keep block updates out of unloaded neighbor chunks without halting the sphere.
            if (!loadedEnvelope(level, point, radius + 1)) continue;
            int minX = (int) Math.floor(point.x - radius), maxX = (int) Math.floor(point.x + radius);
            int minY = (int) Math.floor(point.y - radius), maxY = (int) Math.floor(point.y + radius);
            int minZ = (int) Math.floor(point.z - radius), maxZ = (int) Math.floor(point.z + radius);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                double dx = point.x - Math.clamp(point.x, x, x + 1);
                double dy = point.y - Math.clamp(point.y, y, y + 1);
                double dz = point.z - Math.clamp(point.z, z, z + 1);
                if (dx * dx + dy * dy + dz * dz >= radius * radius) continue;
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !examined.add(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getBlock() instanceof LiquidBlock || !mayBreak(owner, level, pos)
                        || state.getDestroySpeed(level, pos) < 0 || !state.canEntityDestroy(level, pos, owner)) continue;
                if (!current.getAsBoolean() || !allowed(owner, level)) return;
                boolean denied;
                if (owner instanceof ServerPlayer player) {
                    BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
                    NeoForge.EVENT_BUS.post(event); denied = event.isCanceled();
                } else denied = !EventHooks.onEntityDestroyBlock(owner, pos, state);
                if (!current.getAsBoolean() || !allowed(owner, level)) return;
                if (denied) continue;
                blocks.add(new Candidate(pos, state));
                if (blocks.size() >= BLOCK_BUDGET) break scan;
            }
        }
        if (blocks.isEmpty()) return;
        try (SkillBlockDrops.Scope ignored = SkillBlockDrops.suppress(level);
             SkillHangingBreaks attachments = SkillHangingBreaks.capture(level, blocks.stream().map(Candidate::pos).toList())) {
            for (Candidate block : blocks) {
                if (!current.getAsBoolean() || !allowed(owner, level)) return;
                // Event replacements and failed destruction are skipped, never movement blockers.
                if (level.getBlockState(block.pos) != block.state) continue;
                level.destroyBlock(block.pos, false, owner);
            }
        }
    }

    static Vec3 pierce(LivingEntity owner, Vec3 start, Vec3 end, float radius, BooleanSupplier current) {
        return sweep(owner, start, end, radius, current, true);
    }

    static Vec3 beam(LivingEntity owner, Vec3 start, Vec3 end, float radius, float traveled, BooleanSupplier current) {
        return sweep(owner, start, end, radius, current, true, traveled);
    }

    private static Vec3 sweep(LivingEntity owner, Vec3 start, Vec3 end, float radius,
                              BooleanSupplier current, boolean piercing) {
        return sweep(owner, start, end, radius, current, piercing, -1);
    }

    private static Vec3 sweep(LivingEntity owner, Vec3 start, Vec3 end, float radius,
                              BooleanSupplier current, boolean piercing, float beamTraveled) {
        ServerLevel level = (ServerLevel) owner.level();
        if (!current.getAsBoolean()) return start;
        boolean grief = allowed(owner, level) && TechniqueTerrainRules.canGrief(owner);
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) / (piercing ? .5 : .20)));
        int budget = piercing ? 512 : BLOCK_BUDGET;
        Vec3 reached = start;
        List<Candidate> blocks = new ArrayList<>();
        for (int step = 1; step <= steps; step++) {
            Vec3 point = start.lerp(end, (double) step / steps);
            float localRadius = beamTraveled < 0 ? radius
                    : radius * (float) Math.clamp(.12 + (beamTraveled + start.distanceTo(point)) / 8, .12, 1);
            // Include a one-block neighbor margin for normal block updates. No chunk tickets are added.
            if (!loadedEnvelope(level, point, localRadius + 1) || !current.getAsBoolean()) return reached;
            blocks.clear();
            int minX = (int) Math.floor(point.x - localRadius), maxX = (int) Math.floor(point.x + localRadius);
            int minY = (int) Math.floor(point.y - localRadius), maxY = (int) Math.floor(point.y + localRadius);
            int minZ = (int) Math.floor(point.z - localRadius), maxZ = (int) Math.floor(point.z + localRadius);
            boolean exhausted = false;
            scan: for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                double dx = point.x - Math.clamp(point.x, x, x + 1);
                double dy = point.y - Math.clamp(point.y, y, y + 1);
                double dz = point.z - Math.clamp(point.z, z, z + 1);
                if (dx * dx + dy * dy + dz * dz >= localRadius * localRadius) continue;
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getWorldBorder().isWithinBounds(pos)) return reached;
                // A piercing shot may continue through empty sky/void beyond the build-height interval.
                if (!level.isInWorldBounds(pos)) {
                    if (piercing && level.isOutsideBuildHeight(pos)) continue;
                    return reached;
                }
                BlockState block = level.getBlockState(pos);
                if (block.isAir() || block.getBlock() instanceof LiquidBlock) continue;
                boolean solid = !block.getCollisionShape(level, pos).isEmpty();
                if (!grief || !allowed(owner, level) || !mayBreak(owner, level, pos)
                        || block.getDestroySpeed(level, pos) < 0 || !block.canEntityDestroy(level, pos, owner)) {
                    if (solid && !piercing) return reached;
                    continue;
                }
                if (!current.getAsBoolean()) return reached;
                boolean denied;
                if (owner instanceof ServerPlayer player) {
                    BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, block, player);
                    NeoForge.EVENT_BUS.post(event); denied = event.isCanceled();
                } else denied = !EventHooks.onEntityDestroyBlock(owner, pos, block);
                if (!current.getAsBoolean()) return reached;
                if (denied) {
                    if (solid && !piercing) return reached;
                    continue;
                }
                blocks.add(new Candidate(pos, block));
                if (blocks.size() > budget) {
                    if (!piercing) return reached;
                    blocks.removeLast(); exhausted = true; break scan;
                }
            }
            if (!blocks.isEmpty()) {
                try (SkillBlockDrops.Scope ignored = SkillBlockDrops.suppress(level);
                     SkillHangingBreaks attachments = SkillHangingBreaks.capture(level, blocks.stream().map(Candidate::pos).toList())) {
                    for (Candidate block : blocks) {
                        if (!current.getAsBoolean() || !allowed(owner, level)) return reached;
                        // A protection/event callback may have replaced a block. Never break that replacement.
                        if (level.getBlockState(block.pos) != block.state) return reached;
                        if (!level.destroyBlock(block.pos, false, owner)) return reached;
                        budget--;
                        if (!current.getAsBoolean()) return reached;
                        BlockState after = level.getBlockState(block.pos);
                        if (!after.getCollisionShape(level, block.pos).isEmpty()) return reached;
                    }
                }
            }
            // A five-block sphere can contain more than one tick's budget. Clear a bounded slice
            // without advancing the shot; the next tick resumes against the remaining real blocks.
            if (exhausted) return reached;
            reached = point;
        }
        return reached;
    }

    private static boolean allowed(LivingEntity owner, ServerLevel level) {
        return owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && (!(owner instanceof ServerPlayer player) || player.mayBuild())
                && TechniqueTerrainRules.enabled();
    }

    private static boolean mayBreak(LivingEntity owner, ServerLevel level, BlockPos pos) {
        return !(owner instanceof ServerPlayer player) || level.mayInteract(player, pos) && player.mayInteract(level, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    private static boolean loadedEnvelope(ServerLevel level, Vec3 point, float reach) {
        int minX = ((int) Math.floor(point.x - reach)) >> 4, maxX = ((int) Math.floor(point.x + reach)) >> 4;
        int minZ = ((int) Math.floor(point.z - reach)) >> 4, maxZ = ((int) Math.floor(point.z + reach)) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
}
