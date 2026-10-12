package dev.jujutsuvfx.core;

/** NPC presentation and simulation use one finite, tick-based spell timeline. */
public final class CatSpell {
    public static final int BLUE = 0, RED = 1, PURPLE = 2, FURNACE = 3, DISMANTLE = 4, SHRINE = 5, VOID = 6, CLEAVE = 7, BARRAGE = 8;
    public static final int PREPARE = 0, CHARGE = 1, FLIGHT = 2, IMPACT = 3, END = 4;
    private CatSpell() {}
    public static boolean domain(int kind) { return kind == SHRINE || kind == VOID; }
    public static int pose(int kind) { return switch (kind) {
        case BLUE -> CatCombatMoves.BLUE_CAST; case RED -> CatCombatMoves.RED_CAST;
        case PURPLE -> CatCombatMoves.PURPLE_CAST; case FURNACE -> CatCombatMoves.FURNACE_CAST;
        case DISMANTLE -> CatCombatMoves.SLASH_CAST; case CLEAVE -> CatCombatMoves.CLEAVE_CAST; default -> CatCombatMoves.DOMAIN_CAST;
    }; }
    public static int prepareTicks(int kind, boolean large) {
        return kind == PURPLE ? large ? 20 : 10 : 0;
    }
    public static int launchTick(int kind, boolean large) { return switch (kind) {
        case BLUE -> 12; case RED -> large ? 60 : 14;
        case PURPLE -> prepareTicks(kind, large) + LimitlessTechnique.purpleLaunchTicks(large);
        case FURNACE -> FurnaceFlight.CHARGE_TICKS; case DISMANTLE -> 10;
        case CLEAVE -> large ? 28 : 10; default -> 16;
    }; }
    public static int poseTicks(int kind, boolean large) {
        if (domain(kind)) return 40;
        if (kind == CLEAVE) return launchTick(kind, large) + (large ? 28 : 12);
        if (kind == RED && large) return 60 + 12 + LimitlessTechnique.RED_BEAM_HOLD_TICKS + 12;
        return launchTick(kind, large) + (kind == BLUE && large ? 42 : 12);
    }
}
