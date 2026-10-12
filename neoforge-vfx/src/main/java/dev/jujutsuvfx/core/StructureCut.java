package dev.jujutsuvfx.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Plans a whole-block cut without reading or changing a level or a physics body. */
public final class StructureCut {
    /** Signed world-space distances within this tolerance belong to the positive side. */
    public static final double PLANE_EPSILON = 1e-7;
    private static final Direction[] NEIGHBORS = Direction.values();
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.<BlockPos>comparingInt(pos -> pos.getX())
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);

    private StructureCut() {}

    /**
     * Keys are the original body's local/storage block positions. Values must be their transformed
     * world-space centers, including the body's translation and rotation. Only centers inside the
     * closed sphere (center, radius) are selected; a boundary block is kept whole on one side of
     * the plane. Local coordinates are used exclusively for six-face connectivity.
     *
     * The result contains every input position exactly once across positive, negative and remainder.
     * A cut is possible only when a selected positive block shares a face with a selected negative
     * block. Callers must leave the source untouched when cuttable() is false. Neither side alone,
     * a lone block, nor two already disconnected pieces constitute a new cut.
     *
     * maxBlocks bounds the entire source, not just the selected area. maxComponents bounds the sum
     * of positive, negative and remainder components. Exceeding either budget throws before a plan
     * is returned; no positions are silently omitted to meet a budget.
     */
    public static Plan plan(Map<BlockPos, Vec3> worldCenters, Vec3 center, Vec3 normal,
                            double radius, int maxBlocks, int maxComponents) {
        Objects.requireNonNull(worldCenters, "worldCenters");
        requireBudgets(maxBlocks, maxComponents);
        requireFinite(center, "center");
        requireFinite(normal, "normal");
        if (!Double.isFinite(radius) || radius <= 0)
            throw new IllegalArgumentException("Cut radius must be finite and positive");
        double length = Math.hypot(Math.hypot(normal.x, normal.y), normal.z);
        if (!Double.isFinite(length) || length < 1e-12)
            throw new IllegalArgumentException("Cut normal must have a finite, nonzero length");
        Vec3 unitNormal = normal.scale(1 / length);
        requireBlockBudget(worldCenters.size(), maxBlocks);

        // Snapshot mutable BlockPos keys before grouping. Never derive world coordinates from a
        // storage position: physics engines may store blocks millions of blocks away from the body.
        Map<BlockPos, Vec3> snapshot = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Vec3> entry : worldCenters.entrySet()) {
            BlockPos pos = Objects.requireNonNull(entry.getKey(), "block position").immutable();
            Vec3 world = entry.getValue();
            requireFinite(world, "world block center");
            if (snapshot.put(pos, world) != null)
                throw new IllegalArgumentException("Duplicate block position after snapshot: " + pos);
            requireBlockBudget(snapshot.size(), maxBlocks);
        }

        Set<BlockPos> positive = new LinkedHashSet<>(), negative = new LinkedHashSet<>();
        Set<BlockPos> remainder = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, Vec3> entry : snapshot.entrySet()) {
            Vec3 delta = entry.getValue().subtract(center);
            requireFinite(delta, "world block offset");
            double distance = Math.hypot(Math.hypot(delta.x, delta.y), delta.z);
            if (distance > radius) {
                remainder.add(entry.getKey());
            } else if (delta.dot(unitNormal) >= -PLANE_EPSILON) {
                positive.add(entry.getKey());
            } else {
                negative.add(entry.getKey());
            }
        }

        ComponentBudget budget = new ComponentBudget(maxComponents);
        List<Set<BlockPos>> positiveGroups = group(positive, budget);
        List<Set<BlockPos>> negativeGroups = group(negative, budget);
        List<Set<BlockPos>> remainderGroups = group(remainder, budget);
        boolean cuttable = sharesFace(positive, negative);
        Plan plan = new Plan(positiveGroups, negativeGroups, remainder, remainderGroups, cuttable);
        // The partition is exclusive and exhaustive. A failed integrity check must never reach a
        // mutating adapter, since moving a partial or duplicate position list could lose blocks.
        if (plan.blockCount() != snapshot.size())
            throw new IllegalStateException("Structure cut did not preserve the source block count");
        return plan;
    }

    /** Groups any retained set in its original block grid; disconnected pieces remain separate. */
    public static List<Set<BlockPos>> components(Collection<BlockPos> positions,
                                                  int maxBlocks, int maxComponents) {
        Objects.requireNonNull(positions, "positions");
        requireBudgets(maxBlocks, maxComponents);
        requireBlockBudget(positions.size(), maxBlocks);
        Set<BlockPos> snapshot = new LinkedHashSet<>();
        for (BlockPos position : positions) {
            BlockPos pos = Objects.requireNonNull(position, "block position").immutable();
            if (!snapshot.add(pos))
                throw new IllegalArgumentException("Duplicate block position: " + pos);
            requireBlockBudget(snapshot.size(), maxBlocks);
        }
        return group(snapshot, new ComponentBudget(maxComponents));
    }

    /** Immutable local/storage positions, ready for a physics adapter to transfer into new bodies. */
    public record Plan(List<Set<BlockPos>> selectedPositive, List<Set<BlockPos>> selectedNegative,
                       Set<BlockPos> remainder, List<Set<BlockPos>> remainderComponents,
                       boolean cuttable) {
        public Plan {
            selectedPositive = freezeGroups(selectedPositive);
            selectedNegative = freezeGroups(selectedNegative);
            remainder = freezePositions(remainder);
            remainderComponents = freezeGroups(remainderComponents);

            Set<BlockPos> seen = new HashSet<>();
            addExclusive(seen, selectedPositive);
            addExclusive(seen, selectedNegative);
            for (BlockPos pos : remainder) if (!seen.add(pos))
                throw new IllegalArgumentException("A remainder block also belongs to a cut side: " + pos);
            Set<BlockPos> retained = new HashSet<>();
            addExclusive(retained, remainderComponents);
            if (!retained.equals(remainder))
                throw new IllegalArgumentException("Remainder components do not match the remainder");
            if (cuttable && (selectedPositive.isEmpty() || selectedNegative.isEmpty()))
                throw new IllegalArgumentException("A cut requires blocks on both sides");
        }

        public int selectedBlockCount() {
            return count(selectedPositive) + count(selectedNegative);
        }

        public int blockCount() { return selectedBlockCount() + remainder.size(); }

        public int fragmentCount() {
            return selectedPositive.size() + selectedNegative.size() + remainderComponents.size();
        }
    }

    /** An explicit all-or-nothing refusal, suitable for reporting before touching world blocks. */
    public static final class BudgetExceededException extends IllegalArgumentException {
        private final String budget;
        private final int required;
        private final int limit;

        private BudgetExceededException(String budget, int required, int limit) {
            super("Structure cut " + budget + " budget exceeded: requires at least " + required
                    + ", limit " + limit);
            this.budget = budget;
            this.required = required;
            this.limit = limit;
        }

        public String budget() { return budget; }
        public int required() { return required; }
        public int limit() { return limit; }
    }

    private static List<Set<BlockPos>> group(Set<BlockPos> positions, ComponentBudget budget) {
        List<BlockPos> ordered = new ArrayList<>(positions);
        ordered.sort(POSITION_ORDER);
        Set<BlockPos> unseen = new HashSet<>(positions);
        List<Set<BlockPos>> groups = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos seed : ordered) {
            if (!unseen.remove(seed)) continue;
            budget.claim();
            Set<BlockPos> component = new LinkedHashSet<>();
            queue.addLast(seed);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                component.add(pos);
                for (Direction direction : NEIGHBORS) {
                    BlockPos adjacent = neighbor(pos, direction);
                    if (adjacent != null && unseen.remove(adjacent)) queue.addLast(adjacent);
                }
            }
            groups.add(Collections.unmodifiableSet(component));
        }
        return List.copyOf(groups);
    }

    private static boolean sharesFace(Set<BlockPos> positive, Set<BlockPos> negative) {
        if (positive.isEmpty() || negative.isEmpty()) return false;
        for (BlockPos pos : positive) for (Direction direction : NEIGHBORS) {
            BlockPos adjacent = neighbor(pos, direction);
            if (adjacent != null && negative.contains(adjacent)) return true;
        }
        return false;
    }

    private static BlockPos neighbor(BlockPos pos, Direction direction) {
        long x = (long) pos.getX() + direction.getStepX();
        long y = (long) pos.getY() + direction.getStepY();
        long z = (long) pos.getZ() + direction.getStepZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE
                || y > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return null;
        return new BlockPos((int) x, (int) y, (int) z);
    }

    private static Set<BlockPos> freezePositions(Collection<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions");
        Set<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos pos : positions)
            if (!result.add(Objects.requireNonNull(pos, "block position").immutable()))
                throw new IllegalArgumentException("Duplicate block position: " + pos);
        return Collections.unmodifiableSet(result);
    }

    private static List<Set<BlockPos>> freezeGroups(List<Set<BlockPos>> groups) {
        Objects.requireNonNull(groups, "groups");
        List<Set<BlockPos>> result = new ArrayList<>(groups.size());
        for (Set<BlockPos> group : groups) {
            if (group.isEmpty()) throw new IllegalArgumentException("Empty structure component");
            result.add(freezePositions(group));
        }
        return List.copyOf(result);
    }

    private static void addExclusive(Set<BlockPos> seen, List<Set<BlockPos>> groups) {
        for (Set<BlockPos> group : groups) for (BlockPos pos : group) if (!seen.add(pos))
            throw new IllegalArgumentException("Block appears in multiple cut components: " + pos);
    }

    private static int count(List<Set<BlockPos>> groups) {
        int count = 0;
        for (Set<BlockPos> group : groups) count = Math.addExact(count, group.size());
        return count;
    }

    private static void requireFinite(Vec3 vector, String name) {
        Objects.requireNonNull(vector, name);
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z))
            throw new IllegalArgumentException(name + " must contain finite coordinates");
    }

    private static void requireBudgets(int maxBlocks, int maxComponents) {
        if (maxBlocks <= 0 || maxComponents <= 0)
            throw new IllegalArgumentException("Structure cut budgets must be positive");
    }

    private static void requireBlockBudget(int required, int limit) {
        if (required > limit) throw new BudgetExceededException("blocks", required, limit);
    }

    private static final class ComponentBudget {
        private final int limit;
        private int used;
        private ComponentBudget(int limit) { this.limit = limit; }
        private void claim() {
            if (used == limit) throw new BudgetExceededException("components", used + 1, limit);
            used++;
        }
    }
}
