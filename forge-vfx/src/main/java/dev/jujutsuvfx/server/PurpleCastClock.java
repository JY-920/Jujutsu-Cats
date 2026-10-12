package dev.jujutsuvfx.server;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.WeakHashMap;

/** Real elapsed casting time, independent of simulation TPS and frozen during an integrated pause. */
public final class PurpleCastClock {
    public static final long TICK_NANOS = 50_000_000L;
    private static final Map<MinecraftServer, Clock> CLOCKS = new WeakHashMap<>();

    private static final class Clock {
        long sampled = System.nanoTime(), elapsed;
        boolean paused;
        Clock(boolean paused) { this.paused = paused; }
        long sample() {
            long next = System.nanoTime();
            if (!paused) elapsed += Math.max(0, next - sampled);
            sampled = next;
            return elapsed;
        }
    }

    private PurpleCastClock() {}

    /** Safe at payload ingress as well as on the server thread; the returned value is never client supplied. */
    public static synchronized long now(MinecraftServer server) {
        return CLOCKS.computeIfAbsent(server, key -> new Clock(false)).sample();
    }

    /** IntegratedServer skips ordinary server tick events while paused, so its outer tick observes transitions. */
    public static synchronized void observePause(MinecraftServer server, boolean paused) {
        Clock clock = CLOCKS.computeIfAbsent(server, key -> new Clock(paused));
        clock.sample();
        clock.paused = paused;
    }

    static synchronized void discard(MinecraftServer server) { CLOCKS.remove(server); }

    static long ticks(int ticks) { return ticks * TICK_NANOS; }

    static int remaining(long deadline, long now) {
        return (int) dev.jujutsuvfx.platform.Numbers.clamp((Math.max(0, deadline - now) + TICK_NANOS - 1) / TICK_NANOS, 0, Integer.MAX_VALUE);
    }
}
