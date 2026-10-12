package dev.jujutsuvfx.core;

public final class DomainDurationTest {
    public static void main(String[] args) {
        equal(100, DomainDuration.ticks(5));
        equal(2400, DomainDuration.ticks(120));
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 4, 121, Integer.MAX_VALUE}) {
            if (DomainDuration.valid(invalid)) throw new AssertionError("Invalid seconds accepted: " + invalid);
            try { DomainDuration.ticks(invalid); throw new AssertionError("Invalid duration converted"); }
            catch (IllegalArgumentException expected) { }
        }
        // Normal casts ignore a client's requested lifetime. Authored scenes preserve their timeline.
        equal(1600, DomainDuration.castTicks(80, 100, false));
        equal(100, DomainDuration.castTicks(5, 2400, false));
        equal(300, DomainDuration.castTicks(120, 300, true));
        equal(100, DomainDuration.castTicks(80, 100, true));
        System.out.println("Domain duration authority and scripted timing checks passed");
    }
    private static void equal(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
