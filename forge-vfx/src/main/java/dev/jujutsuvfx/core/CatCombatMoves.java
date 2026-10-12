package dev.jujutsuvfx.core;

/** Shared animation/contact timeline; gameplay and rendering read exactly the same action frame. */
public final class CatCombatMoves {
    public static final int IDLE = 0, PREPARE = 1, JAB = 2, HOOK = 3, RAPID = 4,
            KICK = 5, DASH = 6, BLINK = 7, CLASH = 8;
    public static final int BLUE_CAST = 9, RED_CAST = 10, PURPLE_CAST = 11, FURNACE_CAST = 12,
            SLASH_CAST = 13, DOMAIN_CAST = 14, REPOSITION = 15,
            GUARD = 16, AIR_CHASE = 17, DIVE_KICK = 18, CLEAVE_CAST = 19, INFINITY = 20,
            REVERSE_CURSED = 21, UPPERCUT = 22, ELBOW = 23, DOUBLE_PALM = 24, SWEEP = 25,
            BLADE_SWEEP = 26, BLADE_RISE = 27, BLADE_CHOP = 28, BLADE_CHAIN = 29, DOMAIN_REVEAL = 30,
            MAHORAGA_ARRIVAL = 31;
    // MAHORAGA_ARRIVAL uses its variant bits for two independent shadow animations.
    public static final int SHADOW_EMERGE=0,SHADOW_RETREAT=1;
    public static final int SHADOW_EXIT_TICKS=40;
    public static final int ARRIVAL_LAND_TICK = 22, ARRIVAL_CROUCH_TICK = 28, ARRIVAL_RISE_TICK = 42;
    public static final int REACTION_WATCH=6,REACTION_BRACE=7;
    public static boolean defense(int move) { return move == GUARD || move == INFINITY; }
    public static final int STEP_BACK = 0, SIDE_STEP = 1, BACK_HOP = 2, SPACE_BLINK = 3, SPACE_DASH = 4, RUSH_IN = 5, AIR_RETREAT = 6, AIR_ESCAPE = 7;
    public static boolean spell(int move) { return move >= BLUE_CAST && move <= DOMAIN_CAST || move == CLEAVE_CAST; }
    private CatCombatMoves() {}
    public static int duration(int move) {
        return switch (move) {
            case PREPARE -> 3;
            case DOMAIN_REVEAL -> 38;
            case MAHORAGA_ARRIVAL -> 50;
            case JAB -> 10;
            case BLINK -> 12;
            case HOOK, ELBOW -> 12;
            case UPPERCUT -> 14;
            case DOUBLE_PALM, BLADE_SWEEP, BLADE_RISE -> 16;
            case BLADE_CHOP -> 20;
            case BLADE_CHAIN -> 26;
            case SWEEP -> 18;
            case RAPID -> 36;
            case GUARD, INFINITY -> 250;
            case REVERSE_CURSED -> 120;
            case AIR_CHASE -> 36;
            case DIVE_KICK -> 24;
            case KICK -> 18;
            case DASH -> 10;
            case CLASH -> 10;
            case REPOSITION -> 48;
            case BLUE_CAST, RED_CAST, PURPLE_CAST, FURNACE_CAST, SLASH_CAST, DOMAIN_CAST, CLEAVE_CAST -> 240;
            default -> 0;
        };
    }
    public static int contact(int move) {
        return switch (move) { case BLADE_SWEEP, BLADE_RISE -> 6; case BLADE_CHOP -> 8; case BLADE_CHAIN -> 5; case JAB -> 3; case HOOK -> 5; case RAPID -> 4; case KICK -> 8; case DIVE_KICK -> 8; case UPPERCUT -> 5; case ELBOW -> 4; case DOUBLE_PALM -> 6; case SWEEP -> 7; default -> -100; };
    }
    public static boolean melee(int move) { return move >= JAB && move <= KICK || move == DIVE_KICK || move >= UPPERCUT && move <= SWEEP || blade(move); }
    public static boolean blade(int move) { return move >= BLADE_SWEEP && move <= BLADE_CHAIN; }
    public static int bladeContact(int move,int stroke) { return contact(move)+(move==BLADE_CHAIN?stroke*6:0); }
    public static int bladeStrokes(int move) { return move==BLADE_CHAIN?3:1; }
    public static long pack(int sequence, int move, int frame, int hand, int kind) {
        return (long) (sequence & 0x7fffffff) << 17 | (long) (kind & 7) << 14
                | (hand == 1 ? 1L << 13 : 0) | (long) (move & 31) << 8 | (frame & 255);
    }
    public static int move(long value) { return value < 0 ? IDLE : (int) (value >>> 8) & 31; }
    public static int frame(long value) { return value < 0 ? 0 : (int) value & 255; }
    public static int hand(long value) { return value >= 0 && (value & 8192) != 0 ? 1 : 2; }
    public static int kind(long value) { return value < 0 ? 0 : (int) (value >>> 14) & 7; }
    public static int sequence(long value) { return value < 0 ? -1 : (int) (value >>> 17); }
}
