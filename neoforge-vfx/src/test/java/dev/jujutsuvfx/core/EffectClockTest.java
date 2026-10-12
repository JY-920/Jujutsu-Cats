package dev.jujutsuvfx.core;

/** Regression for server time synchronizations rewinding charge/white-intro animation. */
public final class EffectClockTest {
    public static void main(String[] args) {
        EffectClock clock = new EffectClock();
        Object world = new Object();
        clock.changeWorld(world); // The first skill may be pressed before the first world tick.
        long born = clock.ticks();
        require(born == 0, "first skill has a valid origin before ticking");
        long serverWorldTime = 10_000, previousWorldTime = serverWorldTime;
        float previousAge = 0;
        int whiteCrossings = 0, galaxyCrossings = 0, chargeCrossings = 0, backwardsSyncs = 0;
        long deadline = 0;
        for (int localTick = 1; localTick <= 160; localTick++) {
            // ClientboundSetTimePacket can replace game time with an earlier value under server lag.
            serverWorldTime = localTick == 40 ? 10_004 : localTick == 80 ? 1_000_000
                    : localTick == 100 ? 500 : serverWorldTime + 1;
            if (serverWorldTime < previousWorldTime) backwardsSyncs++;
            previousWorldTime = serverWorldTime;
            clock.tick(world, false);
            float age = (clock.ticks() - born) / 20f;
            require(age > previousAge, "animation advances through backwards/forwards server syncs");
            if (previousAge < .94f && age >= .94f) whiteCrossings++;
            if (previousAge < 3.10f && age >= 3.10f) galaxyCrossings++;
            if (previousAge < 3f && age >= 3f) chargeCrossings++;
            if (localTick == 50) deadline = clock.after(100);
            if (localTick < 150 && localTick >= 50) require(clock.ticks() < deadline, "remaining ticks are delivery-relative");
            if (localTick == 150) require(clock.ticks() == deadline, "deadline ignores absolute world jumps");
            previousAge = age;
        }
        require(backwardsSyncs == 2, "regression trace includes real backwards world time samples");
        require(whiteCrossings == 1 && galaxyCrossings == 1 && chargeCrossings == 1, "intro thresholds never replay");
        long pausedAt = clock.ticks();
        for (int i = 0; i < 200; i++) clock.tick(world, true);
        require(clock.ticks() == pausedAt, "pause does not age effects or expire them");
        clock.tick(world, false);
        require(clock.ticks() == pausedAt + 1, "resume has no catch-up jump");
        clock.changeWorld(world);
        require(clock.ticks() == pausedAt + 1, "same-level ensure calls cannot reset playback");
        clock.changeWorld(null);
        for (int i = 0; i < 40; i++) clock.tick(null, false);
        require(clock.ticks() == 0, "disconnected time cannot advance");
        Object rejoined = new Object();
        clock.changeWorld(rejoined);
        require(clock.ticks() == 0, "rejoining the same save gets a fresh effect timeline");
        clock.tick(rejoined, false);
        require(clock.ticks() == 1, "first new-world tick advances once");
        require(clock.after(-100) == clock.ticks(), "negative remaining duration cannot schedule in the past");
        System.out.println("Effect clock: backwards/forwards world sync, one-shot intros, packet deadlines, pause, resume and rejoin passed.");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
