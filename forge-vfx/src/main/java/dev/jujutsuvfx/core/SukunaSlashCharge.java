package dev.jujutsuvfx.core;

/** Shared input/gesture timing. Holding prepares one release; it never ticks attacks. */
public final class SukunaSlashCharge {
    public static final int CHARGE_TICKS = 12;
    public static final int FIRST_CUT_DELAY = 3;
    public static final int BURST_INTERVAL = 2;
    public static final int BURST_CUTS = 8;
    public static final int SHORT_COOLDOWN = 16;
    public static final int BURST_COOLDOWN = 28;
    private SukunaSlashCharge() {}
    public static int cuts(int heldTicks) { return heldTicks >= CHARGE_TICKS ? BURST_CUTS : 1; }
    public static int cooldown(int heldTicks) { return heldTicks >= CHARGE_TICKS ? BURST_COOLDOWN : SHORT_COOLDOWN; }
}
