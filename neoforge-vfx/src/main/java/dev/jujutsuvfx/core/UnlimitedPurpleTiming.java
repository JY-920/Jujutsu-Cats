package dev.jujutsuvfx.core;

/** Shared absolute timeline: existing spells condense before the original fusion and wave. */
public final class UnlimitedPurpleTiming {
    public static final int CONDENSE_TICKS = 12, GATHER_TICKS = 20;
    public static final int CHARGE_TICKS = CONDENSE_TICKS + GATHER_TICKS, WAVE_TICKS = 20;
    public static final int VISIBLE_TICKS = 110 + CONDENSE_TICKS, MAX_PACKET_AGE = 160 + CONDENSE_TICKS;
    private UnlimitedPurpleTiming() {}
}
