package dev.jujutsuvfx.core;

/** Seconds stored in server config; authored scenes have a separate, fixed tick timeline. */
public final class DomainDuration {
    public static final int DEFAULT_SECONDS = 5, MIN_SECONDS = 5, MAX_SECONDS = 120;
    private DomainDuration() { }
    public static boolean valid(int seconds) { return seconds >= MIN_SECONDS && seconds <= MAX_SECONDS; }
    public static int ticks(int seconds) {
        if (!valid(seconds)) throw new IllegalArgumentException("Domain duration must be 5-120 seconds");
        return seconds * 20;
    }
    public static int castTicks(int configuredSeconds, int requestedTicks, boolean scripted) {
        return scripted ? requestedTicks : ticks(configuredSeconds);
    }
}
