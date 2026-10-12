package dev.jujutsuvfx.core;

/** A presentation timeline. Server/world time is deliberately not an input: sync may move it backwards. */
public final class EffectClock {
    private Object world;
    private long ticks;

    public void changeWorld(Object next) {
        if (world != next) { world = next; ticks = 0; }
    }

    public void tick(Object currentWorld, boolean paused) {
        changeWorld(currentWorld);
        if (world != null && !paused) ticks++;
    }

    public long ticks() { return ticks; }

    /** Packet durations become local deadlines once, when the packet is received. */
    public long after(int remainingTicks) { return ticks + Math.max(0, remainingTicks); }
}
