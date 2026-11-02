package dev.jujutsuvfx.core;

import dev.jujutsuvfx.core.ChimeraAnatomy.Box;
import dev.jujutsuvfx.core.ChimeraAnatomy.Organ;
import java.util.*;
import static dev.jujutsuvfx.core.ChimeraAnatomy.*;

/** Bone names, donor mixing and stitched layout of Geto's curse chimeras. */
public final class ChimeraAnatomyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Organ organ(int kind, double x0, double y0, double z0, double x1, double y1, double z1) {
        return new Organ(kind, new Box(x0, y0, z0, x1, y1, z1), Math.abs((x0 + x1) / 2) < .02 ? 0 : (x0 + x1) > 0 ? 1 : -1);
    }
    public static void main(String[] args) {
        check(kind("head") == HEAD && kind("real_head") == HEAD && kind("beak") == HEAD && kind("right_ear") == HEAD, "head bones");
        check(kind("body") == BODY && kind("mane") == BODY && kind("upper_body") == BODY, "body bones");
        check(kind("right_hind_leg") == LEG && kind("leftFrontLeg") == LEG && kind("tentacle3") == LEG, "leg bones");
        check(kind("left_arm") == ARM && kind("rightArm") == ARM, "arm bones");
        check(kind("left_wing") == WING && kind("left_wing_tip") == WING, "wing bones");
        check(kind("tail") == TAIL && kind("tail_fin") == TAIL && kind("back_fin") == TAIL, "tail bones");
        check(kind("saddle") < 0 && kind("") < 0 && kind("root") < 0, "accessories are not organs");

        List<String> five = List.of("minecraft:cow", "minecraft:spider", "minecraft:chicken", "minecraft:zombie", "minecraft:bee");
        String[][] r = recipes(five, 2, 42);
        check(r.length == 2 && r[0].length == SLOTS, "two chimeras with five slots");
        for (String[] recipe : r) check(new HashSet<>(Arrays.asList(recipe)).size() == 5, "five donors spread over every slot");
        check(!Arrays.equals(r[0], r[1]), "the two chimeras differ");
        check(!r[0][SLOT_BODY].equals(r[1][SLOT_BODY]), "different bodies");
        String[][] pair = recipes(List.of("minecraft:cow", "minecraft:spider"), 2, 7);
        for (String[] recipe : pair) check(new HashSet<>(Arrays.asList(recipe)).size() == 2, "both donors used by each chimera");
        check(recipes(List.of(), 2, 1).length == 0, "no donors, no recipe");

        // Cow (quadruped) + spider (8 sprawled legs) + chicken (wings).
        List<Organ> cow = List.of(organ(BODY, -.38, .3, -.5, .38, .9, .62), organ(HEAD, -.25, .1, -.9, .25, .6, -.5),
                organ(LEG, -.3, .9, .3, -.05, 1.5, .55), organ(LEG, .05, .9, .3, .3, 1.5, .55), organ(LEG, -.3, .9, -.45, -.05, 1.5, -.2), organ(LEG, .05, .9, -.45, .3, 1.5, -.2));
        List<Organ> spider = new ArrayList<>(List.of(organ(BODY, -.3, .8, -.2, .3, 1.3, .9), organ(HEAD, -.25, .8, -.7, .25, 1.3, -.2)));
        for (int i = 0; i < 4; i++) { spider.add(organ(LEG, -1.1, 1.0, -.3 + i * .2, -.2, 1.15, -.2 + i * .2)); spider.add(organ(LEG, .2, 1.0, -.3 + i * .2, 1.1, 1.15, -.2 + i * .2)); }
        List<Organ> chicken = List.of(organ(BODY, -.2, .6, -.25, .2, 1.1, .25), organ(HEAD, -.12, .2, -.4, .12, .6, -.2),
                organ(WING, -.3, .7, -.2, -.2, 1.0, .2), organ(WING, .2, .7, -.2, .3, 1.0, .2), organ(LEG, -.15, 1.1, 0, -.05, 1.5, .1), organ(LEG, .05, 1.1, 0, .15, 1.5, .1));
        List<List<Organ>> donors = List.of(cow, spider, chicken);
        int[] slots = {0, 2, 1, 2, 0};
        int[][] chosen = choose(donors, slots, true);
        check(chosen[BODY].length == 1 && chosen[LEG].length == 8 && chosen[WING].length == 2, "body + spider legs + wings chosen");
        List<Organ> flat = new ArrayList<>(); donors.forEach(flat::addAll);
        var placements = assemble(flat, chosen);
        check(placements.stream().filter(p -> p.kind() == LEG).count() == 8, "every donor leg placed");
        check(placements.stream().filter(p -> p.kind() == LEG).allMatch(p -> p.sprawled()), "spider legs keep their sprawl");
        check(placements.stream().filter(p -> p.kind() == LEG && p.side() > 0).count() == 4, "legs split across both sides");
        check(placements.stream().noneMatch(p -> p.kind() == TAIL), "no tail when no donor has one");
        for (var p : placements) {
            check(Double.isFinite(p.scale()) && p.scale() >= .18 && p.scale() <= 5, "bounded organ scale");
            check(p.py() <= GROUND + 1e-6 && p.py() > -2, "organs stay above ground and near the body");
        }
        // A single blob donor still yields a body.
        int[][] blob = choose(List.of(List.of(organ(-1 + 1 + HEAD, -.5, 0, -.5, .5, 1, .5))), new int[]{0, 0, 0, 0, 0}, false);
        check(blob[BODY].length == 1 && blob[HEAD].length == 0, "largest organ becomes the body of a blob");
        check(geometric(new Box(-.1, 1.0, 0, .1, 1.5, .1), new Box(-.4, .3, -.5, .4, .9, .6)) == LEG, "below the body is a leg");
        check(geometric(new Box(-.2, .1, -.9, .2, .5, -.5), new Box(-.4, .3, -.5, .4, .9, .6)) == HEAD, "in front is a head");
        System.out.println("PASS chimera anatomy: bone names, donor mixing, organ borrowing and stitched layout");
    }
}
