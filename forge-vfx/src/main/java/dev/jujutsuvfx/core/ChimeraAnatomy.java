package dev.jujutsuvfx.core;

import java.util.*;

/**
 * Loader-independent anatomy for Geto's curse chimeras: bone-name classification, the donor mixing
 * plan and the assembly layout. Coordinates are Minecraft model space in blocks (y points down,
 * the front is -z, the ground lies at y = 1.5).
 */
public final class ChimeraAnatomy {
    public static final int HEAD = 0, BODY = 1, LEG = 2, ARM = 3, WING = 4, TAIL = 5, KINDS = 6;
    /** Recipe slots: which donor supplies which organ. */
    public static final int SLOT_BODY = 0, SLOT_HEAD = 1, SLOT_LEGS = 2, SLOT_LIMBS = 3, SLOT_TAIL = 4, SLOTS = 5;
    public static final String[] SLOT_KEYS = {"Body", "Head", "Legs", "Limbs", "Tail"};
    public static final double GROUND = 1.5;
    private ChimeraAnatomy() {}

    /** Axis-aligned box in model space. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public static final Box EMPTY = new Box(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        public boolean empty() { return !(maxX >= minX && maxY >= minY && maxZ >= minZ); }
        public Box union(Box b) {
            if (empty()) return b; if (b.empty()) return this;
            return new Box(Math.min(minX, b.minX), Math.min(minY, b.minY), Math.min(minZ, b.minZ), Math.max(maxX, b.maxX), Math.max(maxY, b.maxY), Math.max(maxZ, b.maxZ));
        }
        public Box include(double x, double y, double z) { return union(new Box(x, y, z, x, y, z)); }
        public double sx() { return maxX - minX; } public double sy() { return maxY - minY; } public double sz() { return maxZ - minZ; }
        public double cx() { return (minX + maxX) * .5; } public double cy() { return (minY + maxY) * .5; } public double cz() { return (minZ + maxZ) * .5; }
        public double extent() { return Math.max(sx(), Math.max(sy(), sz())); }
        public double volume() { return Math.max(1e-6, sx()) * Math.max(1e-6, sy()) * Math.max(1e-6, sz()); }
    }

    /** Organ found in a donor; {@code side} is -1 (model right), 0 or 1 (model left). */
    public record Organ(int kind, Box rest, int side) {}
    /** A transform request: attach point (donor space) maps onto anchor (chimera space) at a scale. */
    public record Placement(int organ, int kind, int side, int index, int count, double scale,
                            double ax, double ay, double az, double px, double py, double pz, boolean sprawled) {}

    private static final String[][] WORDS = {
            {"head", "hat", "jaw", "beak", "nose", "mouth", "ear", "horn", "snout", "eye", "face", "skull", "bill", "crest", "wattle", "red_thing", "mustache", "teeth", "tooth", "antenna", "stinger_head"},
            {"body", "torso", "chest", "neck", "mane", "waist", "trunk", "shell", "hump", "belly", "spine", "cube", "upper_body", "lower_body", "jacket", "core", "bag"},
            {"leg", "foot", "feet", "tentacle", "shin", "thigh", "calf", "paw"},
            {"arm", "hand", "claw", "fist"},
            {"wing"},
            {"tail", "fin", "rattle", "fluke", "stinger"},
    };
    /** Bone names survive obfuscation because they are runtime strings in every layer definition. */
    public static int kind(String name) {
        if (name == null || name.isEmpty()) return -1;
        String n = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        if (n.contains("armor") || n.contains("saddle") || n.contains("chest_")) return -1;
        // Wings and tails win over generic body words ("tail_body", "wing_tip").
        for (int kind : new int[]{WING, TAIL, LEG, ARM, HEAD, BODY})
            for (String word : WORDS[kind]) if (n.contains(word)) return kind;
        return -1;
    }

    /** Unnamed bones are judged against the main body: below it legs, in front or above it the head. */
    public static int geometric(Box part, Box body) {
        if (part.empty() || body.empty()) return BODY;
        double margin = Math.max(.02, body.sy() * .15);
        if (part.cy() > body.maxY - margin && part.minY > body.cy()) return LEG;
        if (part.cz() < body.minZ + margin || part.maxY < body.minY + margin) return HEAD;
        if (part.cz() > body.maxZ - margin) return TAIL;
        if (Math.abs(part.cx()) > body.sx() * .5 + .01) return ARM;
        return BODY;
    }

    /**
     * Shares the absorbed donors between the chimeras so every new curse mixes several species.
     * With n donors, chimera c takes slot s from donor (s + c) mod n of a shuffled order.
     */
    public static String[][] recipes(List<String> donors, int count, long seed) {
        if (donors.isEmpty() || count <= 0) return new String[0][];
        List<String> order = new ArrayList<>(donors);
        Collections.shuffle(order, new Random(seed));
        int n = order.size();
        String[][] result = new String[count][SLOTS];
        for (int c = 0; c < count; c++)
            for (int s = 0; s < SLOTS; s++) result[c][s] = order.get(Math.floorMod(s + c * (n > 2 ? 2 : 1), n));
        return result;
    }

    public static double fit(double extent, double target) {
        if (!(extent > 1e-4) || !Double.isFinite(extent)) return 1;
        return Math.max(.18, Math.min(5, target / extent));
    }
    private static final double BODY_SIZE = .85, HEAD_SIZE = .52, LEG_SIZE = .62, ARM_SIZE = .62, WING_SIZE = .80, TAIL_SIZE = .58;

    /**
     * Places organs taken from different donors onto one body. {@code chosen[kind]} lists the organ
     * indices (into {@code organs}) supplying that kind; BODY must contain at least one entry.
     */
    public static List<Placement> assemble(List<Organ> organs, int[][] chosen) {
        List<Placement> out = new ArrayList<>();
        if (chosen[BODY].length == 0) return out;
        Box rawBody = Box.EMPTY;
        for (int i : chosen[BODY]) rawBody = rawBody.union(organs.get(i).rest());
        double bodyScale = fit(rawBody.extent(), BODY_SIZE);
        // Leg clearance comes from the scaled height of the chosen legs.
        double legScale = 1, clearance = .06;
        if (chosen[LEG].length > 0) {
            Box legs = Box.EMPTY;
            for (int i : chosen[LEG]) legs = legs.union(organs.get(i).rest());
            double longest = 0;
            for (int i : chosen[LEG]) longest = Math.max(longest, organs.get(i).rest().extent());
            legScale = fit(longest, LEG_SIZE);
            clearance = Math.max(.12, legs.sy() * legScale * .88);
        }
        double w = rawBody.sx() * bodyScale, h = rawBody.sy() * bodyScale, d = rawBody.sz() * bodyScale;
        boolean upright = h > d * 1.1;
        // Chimera-space body box: centred on x/z, standing on the legs.
        double bottom = GROUND - clearance, top = bottom - h, front = -d * .5, back = d * .5;
        for (int i : chosen[BODY]) {
            out.add(new Placement(i, BODY, 0, 0, 1, bodyScale, rawBody.cx(), rawBody.maxY, rawBody.cz(), 0, bottom, 0, false));
        }
        double half = w * .5;
        // Head: front-top of a horizontal body, on top of an upright one.
        if (chosen[HEAD].length > 0) {
            Box head = Box.EMPTY;
            for (int i : chosen[HEAD]) head = head.union(organs.get(i).rest());
            double s = fit(head.extent(), HEAD_SIZE);
            for (int i : chosen[HEAD]) {
                if (upright) out.add(new Placement(i, HEAD, 0, 0, 1, s, head.cx(), head.maxY, head.cz(), 0, top + .04, 0, false));
                else out.add(new Placement(i, HEAD, 0, 0, 1, s, head.cx(), head.cy(), head.maxZ, 0, top + h * .30, front + .06, false));
            }
        }
        if (chosen[LEG].length > 0) {
            List<Integer> left = new ArrayList<>(), right = new ArrayList<>();
            int alternate = 0;
            for (int i : chosen[LEG]) {
                int side = organs.get(i).side();
                if (side == 0) side = (alternate++ & 1) == 0 ? 1 : -1;
                (side > 0 ? left : right).add(i);
            }
            Comparator<Integer> byZ = Comparator.comparingDouble(i -> organs.get(i).rest().cz());
            left.sort(byZ); right.sort(byZ);
            for (int pass = 0; pass < 2; pass++) {
                List<Integer> list = pass == 0 ? left : right; int side = pass == 0 ? 1 : -1;
                for (int k = 0; k < list.size(); k++) {
                    int i = list.get(k); Box r = organs.get(i).rest();
                    boolean sprawled = r.sx() > r.sy() * 1.25;
                    double ax = sprawled ? (r.cx() < 0 ? r.maxX : r.minX) : r.cx();
                    double z = upright || list.size() == 1 ? 0 : front * .72 + (back - front) * .72 * (k + .5) / list.size();
                    double x = side * half * (upright ? .48 : .72);
                    out.add(new Placement(i, LEG, side, k, list.size(), legScale, ax, r.minY, r.cz(), x, bottom - .03, z, sprawled));
                }
            }
        }
        for (int kind : new int[]{ARM, WING}) {
            if (chosen[kind].length == 0) continue;
            double longest = 0;
            for (int i : chosen[kind]) longest = Math.max(longest, organs.get(i).rest().extent());
            double s = fit(longest, kind == ARM ? ARM_SIZE : WING_SIZE);
            int alternate = 0, index = 0;
            for (int i : chosen[kind]) {
                Box r = organs.get(i).rest(); int side = organs.get(i).side();
                if (side == 0) side = (alternate++ & 1) == 0 ? 1 : -1;
                if (kind == ARM) {
                    double z = upright ? 0 : front * .55;
                    out.add(new Placement(i, ARM, side, index++, chosen[kind].length, s, r.cx(), r.minY, r.cz(), side * (half + .02), top + .10, z, false));
                } else {
                    double ax = r.cx() < 0 ? r.maxX : r.cx() > 0 ? r.minX : r.cx();
                    double z = upright ? back * .5 : front * .15;
                    out.add(new Placement(i, WING, side, index++, chosen[kind].length, s, ax, r.cy(), r.cz(), side * half * .55, top + .02, z, false));
                }
            }
        }
        if (chosen[TAIL].length > 0) {
            Box tail = Box.EMPTY;
            for (int i : chosen[TAIL]) tail = tail.union(organs.get(i).rest());
            double s = fit(tail.extent(), TAIL_SIZE);
            for (int i : chosen[TAIL])
                out.add(new Placement(i, TAIL, 0, 0, 1, s, tail.cx(), tail.minY, tail.minZ, 0, upright ? bottom - h * .2 : top + h * .25, back - .02, false));
        }
        return out;
    }

    /** Organs by kind in recipe order; a missing organ is borrowed from the next donor that has one. */
    public static int[][] choose(List<List<Organ>> donorOrgans, int[] slotDonor, boolean preferWings) {
        // Flatten in donor order so indices are stable for the caller.
        int[][] result = new int[KINDS][];
        int[] offsets = new int[donorOrgans.size() + 1];
        for (int i = 0; i < donorOrgans.size(); i++) offsets[i + 1] = offsets[i] + donorOrgans.get(i).size();
        int[] slotKind = {BODY, HEAD, LEG, -1, TAIL};
        for (int kind = 0; kind < KINDS; kind++) result[kind] = new int[0];
        for (int slot = 0; slot < SLOTS; slot++) {
            int[] wanted = slot == SLOT_LIMBS ? (preferWings ? new int[]{WING, ARM} : new int[]{ARM, WING}) : new int[]{slotKind[slot]};
            done:
            for (int attempt = 0; attempt < donorOrgans.size(); attempt++) {
                int donor = Math.floorMod(slotDonor[slot] + attempt, donorOrgans.size());
                for (int kind : wanted) {
                    List<Integer> found = new ArrayList<>();
                    List<Organ> organs = donorOrgans.get(donor);
                    for (int i = 0; i < organs.size(); i++) if (organs.get(i).kind() == kind) found.add(offsets[donor] + i);
                    if (found.isEmpty()) continue;
                    result[kind] = found.stream().mapToInt(Integer::intValue).toArray();
                    break done;
                }
            }
        }
        if (result[BODY].length == 0) {
            // Some donors are a single blob (slimes); use the largest organ of the body donor.
            int donor = Math.floorMod(slotDonor[SLOT_BODY], donorOrgans.size()), best = -1; double volume = -1;
            List<Organ> organs = donorOrgans.get(donor);
            for (int i = 0; i < organs.size(); i++) if (organs.get(i).rest().volume() > volume) { volume = organs.get(i).rest().volume(); best = offsets[donor] + i; }
            if (best >= 0) {
                result[BODY] = new int[]{best};
                for (int kind = 0; kind < KINDS; kind++) if (kind != BODY)
                    result[kind] = Arrays.stream(result[kind]).filter(i -> i != result[BODY][0]).toArray();
            }
        }
        return result;
    }
}
