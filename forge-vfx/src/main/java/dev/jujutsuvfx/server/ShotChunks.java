package dev.jujutsuvfx.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A moving, expiring corridor ticket; never waits synchronously for world generation. */
final class ShotChunks implements AutoCloseable {
    private static final TicketType<UUID> TYPE = TicketType.create("jujutsuvfx_shot", Comparator.<UUID>naturalOrder(), 80);
    private final ServerLevel level;
    private final UUID token = UUID.randomUUID();
    private final Set<Long> held = new HashSet<>();
    private boolean closed;
    private int admissionTick = Integer.MIN_VALUE, admitted;
    private int refreshTick = Integer.MIN_VALUE;

    ShotChunks(ServerLevel level) { this.level = level; }

    void prepare(Vec3 point, Vec3 direction, float radius, double remaining) {
        prepare(point, direction, radius, remaining, false);
    }

    void prepareBudgeted(Vec3 point, Vec3 direction, float radius, double remaining) {
        prepare(point, direction, radius, remaining, true);
    }

    private void prepare(Vec3 point, Vec3 direction, float radius, double remaining, boolean budgeted) {
        if (closed) return;
        Set<Long> wanted = new HashSet<>();
        double ahead = Math.min(budgeted ? 12 : 32, Math.max(0, remaining));
        int count = Math.max(1, (int) Math.ceil(ahead / 4));
        for (int i = 0; i <= count; i++) {
            Vec3 p = point.add(direction.scale(ahead * i / count));
            int minX = ((int) Math.floor(p.x - radius - 2)) >> 4, maxX = ((int) Math.floor(p.x + radius + 2)) >> 4;
            int minZ = ((int) Math.floor(p.z - radius - 2)) >> 4, maxZ = ((int) Math.floor(p.z + radius + 2)) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (level.getWorldBorder().isWithinBounds(chunk)) wanted.add(chunk.toLong());
            }
        }
        for (var old = held.iterator(); old.hasNext();) {
            long position = old.next();
            if (!wanted.contains(position)) { level.getChunkSource().removeRegionTicket(TYPE, new ChunkPos(position), 0, token); old.remove(); }
        }
        int tick = level.getServer().getTickCount();
        if (tick != admissionTick) { admissionTick = tick; admitted = 0; }
        boolean refresh = refreshTick == Integer.MIN_VALUE || tick - refreshTick >= 20;
        var ordered = new java.util.ArrayList<>(wanted);
        if (budgeted) ordered.sort(Comparator.comparingDouble(p -> {
            ChunkPos c = new ChunkPos(p);
            double x = c.x * 16 + 8 - point.x, z = c.z * 16 + 8 - point.z;
            return x * x + z * z;
        }));
        for (long position : ordered) {
            if (held.contains(position)) {
                if (!budgeted || refresh) level.getChunkSource().addRegionTicket(TYPE, new ChunkPos(position), 0, token);
            } else {
                ChunkPos chunk = new ChunkPos(position);
                boolean loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
                // Retain already-loaded chunks immediately; request at most two new chunks per tick.
                if (budgeted && !loaded && admitted >= 2) continue;
                level.getChunkSource().addRegionTicket(TYPE, chunk, 0, token);
                held.add(position);
                if (!loaded) admitted++;
            }
        }
        if (refresh) refreshTick = tick;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (long position : held) level.getChunkSource().removeRegionTicket(TYPE, new ChunkPos(position), 0, token);
        held.clear();
    }
}
