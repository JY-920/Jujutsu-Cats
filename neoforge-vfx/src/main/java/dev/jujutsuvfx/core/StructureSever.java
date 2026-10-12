package dev.jujutsuvfx.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Whole-block severing: remove only adjacency edges crossed by a finite world-space cut disk. */
public final class StructureSever {
    public static final double PLANE_EPSILON = 1e-7;
    private static final Direction[] FORWARD = {Direction.EAST, Direction.UP, Direction.SOUTH};
    private static final Direction[] NEIGHBORS = Direction.values();
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);

    private StructureSever() {}

    /**
     * Keys remain in the source's block grid; values are the corresponding transformed world centers.
     * An edge is removed only if its centers lie on different sides and the intervening segment
     * intersects the cut plane inside its radius. Blocks outside the disk remain connected normally:
     * a finite slash does not carve an additional spherical boundary through the body.
     *
     * cutEdges can be positive while the body remains connected by a path around the slash. Likewise,
     * existing disconnected components do not constitute a successful cut when cutEdges is zero.
     */
    public static Plan plan(Map<BlockPos, Vec3> worldCenters, Vec3 center, Vec3 normal,
                            double radius, int maxBlocks, int maxComponents) {
        Objects.requireNonNull(worldCenters, "worldCenters");
        requireFinite(center, "center");
        requireFinite(normal, "normal");
        if (maxBlocks <= 0 || maxComponents <= 0)
            throw new IllegalArgumentException("Structure sever budgets must be positive");
        if (!Double.isFinite(radius) || radius <= 0)
            throw new IllegalArgumentException("Cut radius must be finite and positive");
        double length = Math.hypot(Math.hypot(normal.x, normal.y), normal.z);
        if (!Double.isFinite(length) || length < 1e-12)
            throw new IllegalArgumentException("Cut normal must have a finite, nonzero length");
        requireBudget("blocks", worldCenters.size(), maxBlocks);
        Vec3 unit = normal.scale(1 / length);

        Map<BlockPos, Vec3> positions = new LinkedHashMap<>();
        Map<BlockPos, Double> distances = new LinkedHashMap<>();
        for (var entry : worldCenters.entrySet()) {
            BlockPos pos = Objects.requireNonNull(entry.getKey(), "block position").immutable();
            Vec3 world = entry.getValue();
            requireFinite(world, "world block center");
            if (positions.put(pos, world) != null)
                throw new IllegalArgumentException("Duplicate block position after snapshot: " + pos);
            requireBudget("blocks", positions.size(), maxBlocks);
            Vec3 offset = world.subtract(center);
            requireFinite(offset, "world block offset");
            double signed = offset.dot(unit);
            if (!Double.isFinite(signed)) throw new IllegalArgumentException("Non-finite plane distance");
            // Snap the tolerance band onto the plane consistently for classification and intersection.
            distances.put(pos, Math.abs(signed) <= PLANE_EPSILON ? 0 : signed);
        }

        Set<Edge> severed = new HashSet<>();
        double contactDistanceSquared = Double.POSITIVE_INFINITY;
        for (var entry : positions.entrySet()) {
            BlockPos a = entry.getKey();
            double da = distances.get(a);
            for (Direction direction : FORWARD) {
                BlockPos b = neighbor(a, direction);
                if (b == null || !positions.containsKey(b)) continue;
                double db = distances.get(b);
                if ((da >= 0) == (db >= 0)) continue;
                // Scaling first avoids overflowing da - db for unusually large finite coordinates.
                double scale = Math.max(Math.abs(da), Math.abs(db));
                double fraction = (da / scale) / ((da / scale) - (db / scale));
                Vec3 first = entry.getValue(), second = positions.get(b);
                Vec3 intersection = first.scale(1 - fraction).add(second.scale(fraction));
                Vec3 delta = intersection.subtract(center);
                requireFinite(delta, "cut intersection offset");
                double distance = Math.hypot(Math.hypot(delta.x, delta.y), delta.z);
                if (distance > radius + PLANE_EPSILON) continue;
                severed.add(Edge.of(a, b));
                contactDistanceSquared = Math.min(contactDistanceSquared, Math.min(Double.MAX_VALUE, distance * distance));
            }
        }

        List<BlockPos> ordered = new ArrayList<>(positions.keySet());
        ordered.sort(POSITION_ORDER);
        Set<BlockPos> unseen = new HashSet<>(positions.keySet());
        List<Set<BlockPos>> components = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos seed : ordered) {
            if (!unseen.remove(seed)) continue;
            requireBudget("components", components.size() + 1, maxComponents);
            Set<BlockPos> component = new LinkedHashSet<>();
            queue.addLast(seed);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                component.add(pos);
                for (Direction direction : NEIGHBORS) {
                    BlockPos adjacent = neighbor(pos, direction);
                    if (adjacent != null && unseen.contains(adjacent)
                            && !severed.contains(Edge.of(pos, adjacent))) {
                        unseen.remove(adjacent);
                        queue.addLast(adjacent);
                    }
                }
            }
            components.add(component);
        }
        Plan result = new Plan(components, severed.size(), contactDistanceSquared);
        if (result.blockCount() != positions.size())
            throw new IllegalStateException("Structure sever did not preserve the source block count");
        return result;
    }

    public record Plan(List<Set<BlockPos>> components, int cutEdges, double contactDistanceSquared) {
        public Plan {
            Objects.requireNonNull(components, "components");
            if (cutEdges < 0 || Double.isNaN(contactDistanceSquared) || contactDistanceSquared < 0)
                throw new IllegalArgumentException("Invalid sever contact");
            if (cutEdges == 0 && contactDistanceSquared != Double.POSITIVE_INFINITY)
                throw new IllegalArgumentException("An uncut graph cannot have a cut contact");
            if (cutEdges > 0 && !Double.isFinite(contactDistanceSquared))
                throw new IllegalArgumentException("A cut graph must have a finite contact");
            List<Set<BlockPos>> frozen = new ArrayList<>(components.size());
            Set<BlockPos> seen = new HashSet<>();
            for (Set<BlockPos> component : components) {
                if (component.isEmpty()) throw new IllegalArgumentException("Empty sever component");
                Set<BlockPos> copy = new LinkedHashSet<>();
                for (BlockPos mutable : component) {
                    BlockPos pos = Objects.requireNonNull(mutable, "block position").immutable();
                    if (!seen.add(pos)) throw new IllegalArgumentException("Repeated sever block: " + pos);
                    copy.add(pos);
                }
                frozen.add(Collections.unmodifiableSet(copy));
            }
            components = List.copyOf(frozen);
        }

        public int blockCount() {
            int count = 0;
            for (Set<BlockPos> component : components) count = Math.addExact(count, component.size());
            return count;
        }
    }

    /** No blocks have been moved when this is thrown; the adapter can skip just this source. */
    public static final class BudgetExceededException extends IllegalArgumentException {
        private final String budget;
        private final int required;
        private final int limit;

        private BudgetExceededException(String budget, int required, int limit) {
            super("Structure sever " + budget + " budget exceeded: requires " + required + ", limit " + limit);
            this.budget = budget;
            this.required = required;
            this.limit = limit;
        }

        public String budget() { return budget; }
        public int required() { return required; }
        public int limit() { return limit; }
    }

    private record Edge(BlockPos first, BlockPos second) {
        static Edge of(BlockPos a, BlockPos b) {
            return POSITION_ORDER.compare(a, b) <= 0 ? new Edge(a, b) : new Edge(b, a);
        }
    }

    private static BlockPos neighbor(BlockPos pos, Direction direction) {
        long x = (long) pos.getX() + direction.getStepX();
        long y = (long) pos.getY() + direction.getStepY();
        long z = (long) pos.getZ() + direction.getStepZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE
                || y > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return null;
        return new BlockPos((int) x, (int) y, (int) z);
    }

    private static void requireFinite(Vec3 vector, String name) {
        Objects.requireNonNull(vector, name);
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z))
            throw new IllegalArgumentException(name + " must contain finite coordinates");
    }

    private static void requireBudget(String name, int required, int limit) {
        if (required > limit) throw new BudgetExceededException(name, required, limit);
    }
}
