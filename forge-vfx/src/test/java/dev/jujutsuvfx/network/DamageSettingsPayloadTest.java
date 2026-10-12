package dev.jujutsuvfx.network;

import java.util.Arrays;

public final class DamageSettingsPayloadTest {
    public static void main(String[] args) {
        assertEquals(DamageSettingsRequest.COUNT, dev.jujutsuvfx.core.TechniqueDamage.values().length);
        requestHasFixedSizeAndOwnsItsValues();
        replyCannotBeChangedByClientDraftEdits();
        validatesEveryValueBeforeSaving();
        System.out.println("Damage settings payload checks passed");
    }
    private static void validatesEveryValueBeforeSaving() {
        int[] values = new int[10];
        values[9] = 1000;
        if (!DamageSettingsRequest.validValues(values)) throw new AssertionError("0 and 1000 must be accepted");
        for (int i = 0; i < values.length; i++) {
            int before = values[i];
            values[i] = -1;
            if (DamageSettingsRequest.validValues(values)) throw new AssertionError("Negative percentage accepted at " + i);
            values[i] = 1001;
            if (DamageSettingsRequest.validValues(values)) throw new AssertionError("Excessive percentage accepted at " + i);
            values[i] = before;
        }
        if (DamageSettingsRequest.validValues(null) || DamageSettingsRequest.validValues(new int[9]))
            throw new AssertionError("Wrong-sized settings accepted");
    }
    private static void requestHasFixedSizeAndOwnsItsValues() {
        int[] values = new int[10];
        Arrays.fill(values, 100);
        var request = new DamageSettingsRequest(7, true, values);
        values[0] = 900;
        request.percents()[1] = 800;
        assertEquals(100, request.percents()[0]);
        assertEquals(100, request.percents()[1]);
        rejects(() -> new DamageSettingsRequest(7, true, new int[11]));
        rejects(() -> new DamageSettingsRequest(7, true, new int[0]));
    }

    private static void replyCannotBeChangedByClientDraftEdits() {
        int[] values = new int[10];
        Arrays.fill(values, 100);
        var state = new DamageSettingsState(7, DamageSettingsState.SAVED, true, values);
        values[0] = 0;
        state.percents()[1] = 0;
        assertEquals(100, state.percents()[0]);
        assertEquals(100, state.percents()[1]);
        rejects(() -> new DamageSettingsState(7, 0, true, new int[9]));
    }
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Wrong-sized damage payload was accepted");
    }
}
