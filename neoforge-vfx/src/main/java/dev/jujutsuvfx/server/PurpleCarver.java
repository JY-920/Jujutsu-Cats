package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.SweptSphere;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/** Resumable, exact swept carving. Its budget delays work; it never trims the shot's radius or range. */
final class PurpleCarver {
    private static final double SLICE_LENGTH = 1.0;
    private static final int BATCH_SIZE = 16;

    /** Shared by all amplified shots in one server, reset once at the start of its skill tick. */
    static final class Budget {
        private int blocks, scans, callers;
        private long nanos;
        void reset(int active) { blocks = 256; scans = 12288; nanos = 4_000_000L; callers = Math.max(1, active); }
    }

    private record Pending(BlockPos pos, BlockState state) {}
    private final LongOpenHashSet visited = new LongOpenHashSet();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private Slice slice;
    private boolean worked, waiting, blocked;

    boolean worked() { return worked; }
    boolean waitingForChunks() { return waiting; }
    boolean blocked() { return blocked; }
    void close() { pending.clear(); visited.clear(); slice = null; }

    Vec3 advance(ServerPlayer owner, Vec3 start, Vec3 desired, float radius, Budget budget, BooleanSupplier current) {
        return advance((LivingEntity) owner, start, desired, radius, budget, current);
    }

    Vec3 advance(LivingEntity owner, Vec3 start, Vec3 desired, float radius, Budget budget, BooleanSupplier current) {
        worked = waiting = false;
        if (blocked || !current.getAsBoolean()) return start;
        ServerLevel level = (ServerLevel) owner.level();
        if (!TechniqueTerrainRules.enabled()) {
            // Discard queued cells immediately. A non-carving shot need not scan a destruction corridor.
            close();
            if (!envelopeLoaded(level, start, desired, radius)) { waiting = true; return start; }
            if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(desired))) { blocked = true; return start; }
            return desired;
        }
        int share = Math.max(1, budget.callers--);
        if (budget.blocks <= 0 || budget.scans <= 0 || budget.nanos <= 0) return start;
        int blockQuota = Math.max(1, budget.blocks / share), scanQuota = Math.max(1, budget.scans / share);
        long began = System.nanoTime(), deadline = began + Math.max(1, budget.nanos / share);
        Vec3 reached = start;
        int blockStart = budget.blocks, scanStart = budget.scans;
        try {
            boolean grief = allowed(owner) && TechniqueTerrainRules.canGrief(owner);
            while (current.getAsBoolean() && System.nanoTime() < deadline
                    && blockStart - budget.blocks < blockQuota && scanStart - budget.scans < scanQuota) {
                if (!pending.isEmpty()) {
                    // Capture once per small batch, while preserving native container/connector
                    // callbacks and immediate no-drop cleanup of frames and paintings.
                    try (SkillBlockDrops.Scope ignored = SkillBlockDrops.quietTerrain(level);
                         SkillHangingBreaks attachments = SkillHangingBreaks.capture(level,
                                 pending.stream().map(Pending::pos).toList())) {
                        while (!pending.isEmpty() && current.getAsBoolean() && System.nanoTime() < deadline
                                && blockStart - budget.blocks < blockQuota) {
                            Pending next = pending.peekFirst();
                            if (!loaded(level, next.pos)) { waiting = true; return reached; }
                            pending.removeFirst();
                            if (!grief || !allowed(owner)
                                    || level.getBlockState(next.pos) != next.state || !mayBreak(owner, next.pos)
                                    || next.state.getDestroySpeed(level, next.pos) < 0
                                    || !next.state.canEntityDestroy(level, next.pos, owner)) continue;
                            if (!current.getAsBoolean()) return reached;
                            boolean permitted;
                            if (owner instanceof ServerPlayer player) {
                                BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, next.pos, next.state, player);
                                NeoForge.EVENT_BUS.post(event); permitted = !event.isCanceled();
                            } else permitted = EventHooks.onEntityDestroyBlock(owner, next.pos, next.state);
                            if (!current.getAsBoolean()) return reached;
                            if (!permitted || !allowed(owner) || !loaded(level, next.pos) || !mayBreak(owner, next.pos)
                                    || level.getBlockState(next.pos) != next.state) continue;
                            // Count every native destruction attempt. Time is checked between
                            // callbacks; a third-party callback itself cannot be preempted safely.
                            budget.blocks--; worked = true;
                            level.destroyBlock(next.pos, false, owner);
                        }
                    }
                    if (!pending.isEmpty()) break;
                }
                if (!current.getAsBoolean()) return reached;
                if (slice != null && slice.done()) { reached = slice.end; slice = null; }
                // A queued slice can span ticks. Recheck after callbacks as well, so reads never
                // force synchronous chunk loading when another mod unloads part of the corridor.
                if (slice != null && !envelopeLoaded(level, slice.start, slice.end, radius)) { waiting = true; break; }
                if (slice == null) {
                    Vec3 delta = desired.subtract(reached);
                    if (delta.lengthSqr() < 1e-8) break;
                    Vec3 end = reached.add(delta.scale(Math.min(1, SLICE_LENGTH / delta.length())));
                    if (!envelopeLoaded(level, reached, end, radius)) { waiting = true; break; }
                    slice = new Slice(reached, end, radius, level);
                    if (slice.done()) continue;
                }
                while (!slice.done() && pending.size() < BATCH_SIZE && System.nanoTime() < deadline
                        && scanStart - budget.scans < scanQuota) {
                    int x = slice.x, y = slice.y, z = slice.z;
                    slice.next(); budget.scans--; worked = true;
                    long key = BlockPos.asLong(x, y, z);
                    if (visited.contains(key) || !slice.touches(x, y, z)) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getWorldBorder().isWithinBounds(pos)) { blocked = true; return reached; }
                    visited.add(key);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock) && grief)
                        pending.addLast(new Pending(pos, state));
                }
            }
            // Do not stall at a completed slice merely because its last cell consumed the budget.
            if (slice != null && slice.done() && pending.isEmpty()) { reached = slice.end; slice = null; }
            return reached;
        } finally {
            budget.nanos -= System.nanoTime() - began;
        }
    }

    private static boolean allowed(LivingEntity owner) {
        return owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && (!(owner instanceof ServerPlayer player) || player.mayBuild())
                && TechniqueTerrainRules.enabled();
    }
    private static boolean mayBreak(LivingEntity owner, BlockPos pos) {
        ServerLevel level = (ServerLevel) owner.level();
        return !(owner instanceof ServerPlayer player) || level.mayInteract(player, pos) && player.mayInteract(level, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++)
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean envelopeLoaded(ServerLevel level, Vec3 start, Vec3 end, float radius) {
        int minX = ((int) Math.floor(Math.min(start.x, end.x) - radius - 1)) >> 4;
        int maxX = ((int) Math.floor(Math.max(start.x, end.x) + radius + 1)) >> 4;
        int minZ = ((int) Math.floor(Math.min(start.z, end.z) - radius - 1)) >> 4;
        int maxZ = ((int) Math.floor(Math.max(start.z, end.z) + radius + 1)) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    /** Each slice scans its capsule once; the primitive set removes overlap between slices. */
    private static final class Slice {
        final Vec3 start, end, delta;
        final double lengthSquared, radiusSquared, outerSquared;
        final int minY, maxY, minZ, maxZ, maxX;
        int x, y, z;
        Slice(Vec3 start, Vec3 end, float radius, ServerLevel level) {
            this.start = start; this.end = end; delta = end.subtract(start); lengthSquared = delta.lengthSqr();
            radiusSquared = radius * radius; outerSquared = Math.pow(radius + Math.sqrt(3) * .5, 2);
            x = (int) Math.floor(Math.min(start.x, end.x) - radius);
            maxX = (int) Math.floor(Math.max(start.x, end.x) + radius);
            minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(Math.min(start.y, end.y) - radius));
            maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(Math.max(start.y, end.y) + radius));
            minZ = (int) Math.floor(Math.min(start.z, end.z) - radius);
            maxZ = (int) Math.floor(Math.max(start.z, end.z) + radius);
            y = minY; z = minZ;
        }
        boolean done() { return x > maxX || minY > maxY; }
        void next() {
            if (++z <= maxZ) return;
            z = minZ;
            if (++y <= maxY) return;
            y = minY; x++;
        }
        boolean touches(int x, int y, int z) {
            // Most cells need no temporary AABB, vectors or polynomial scratch arrays.
            double t = Math.clamp(((x + .5 - start.x) * delta.x + (y + .5 - start.y) * delta.y
                    + (z + .5 - start.z) * delta.z) / Math.max(lengthSquared, 1e-12), 0, 1);
            double px = start.x + delta.x * t, py = start.y + delta.y * t, pz = start.z + delta.z * t;
            double dx = x + .5 - px, dy = y + .5 - py, dz = z + .5 - pz;
            if (dx * dx + dy * dy + dz * dz > outerSquared) return false;
            dx = px - Math.clamp(px, x, x + 1); dy = py - Math.clamp(py, y, y + 1); dz = pz - Math.clamp(pz, z, z + 1);
            if (dx * dx + dy * dy + dz * dz < radiusSquared) return true;
            return SweptSphere.distanceSquared(start, end, new AABB(x, y, z, x + 1, y + 1, z + 1)) < radiusSquared;
        }
    }
}
