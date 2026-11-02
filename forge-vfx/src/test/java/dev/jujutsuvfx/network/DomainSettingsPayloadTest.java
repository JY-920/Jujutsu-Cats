package dev.jujutsuvfx.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class DomainSettingsPayloadTest {
    public static void main(String[] args) {
        requestRoundTrip(new DomainSettingsRequest(0, false, 5, 5, 20));
        requestRoundTrip(new DomainSettingsRequest(17, true, 5, 120, 73));
        for (int result = 0; result <= DomainSettingsState.FAILED; result++) {
            var original = new DomainSettingsState(29, result, result != DomainSettingsState.DENIED, 120, 5, 47);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DomainSettingsState.CODEC.encode(buffer, original);
                if (!original.equals(DomainSettingsState.CODEC.decode(buffer))) throw new AssertionError("State fields lost");
                if (buffer.isReadable()) throw new AssertionError("Unread reply fields");
            } finally { buffer.release(); }
        }
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 4, 121, Integer.MAX_VALUE}) {
            var voidInvalid = new DomainSettingsRequest(1, true, invalid, 5, 20);
            var shrineInvalid = new DomainSettingsRequest(1, true, 5, invalid, 20);
            var yutaInvalid = new DomainSettingsRequest(1, true, 5, 5, invalid);
            requestRoundTrip(yutaInvalid);
            requestRoundTrip(voidInvalid); // Reject bad values at the server boundary, not in the decoder.
            if (voidInvalid.validValues() || shrineInvalid.validValues() || yutaInvalid.validValues()) throw new AssertionError("Invalid duration accepted");
            if (new DomainSettingsState(1, 0, true, invalid, 5, 20).validValues()) throw new AssertionError("Invalid snapshot accepted");
            if (new DomainSettingsState(1, 0, true, 5, 5, invalid).validValues()) throw new AssertionError("Invalid Yuta snapshot accepted");
        }
        for (int seconds : new int[]{5, 20, 120}) {
            var boundary = new DomainSettingsRequest(2, true, 5, 120, seconds);
            if (!boundary.validValues()) throw new AssertionError("Valid Yuta duration rejected");
            requestRoundTrip(boundary);
        }
        rejectsInvalidConfigTransaction();
        System.out.println("Domain settings wire round trips, permission replies and bounds passed");
    }
    private static void rejectsInvalidConfigTransaction() {
        // Validation must reject the whole edit before requiring or mutating a loaded world config.
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 4, 121, Integer.MAX_VALUE}) {
            for (int index = 0; index < 3; index++) {
                int[] values = {5, 120, 20};
                values[index] = invalid;
                try {
                    dev.jujutsuvfx.config.VfxConfig.domainSeconds(values[0], values[1], values[2]);
                    throw new AssertionError("Invalid config transaction accepted at " + index);
                } catch (IllegalArgumentException expected) { }
            }
        }
        if (dev.jujutsuvfx.config.VfxConfig.yutaSeconds() != 20)
            throw new AssertionError("Unloaded Yuta config must default to twenty seconds");
        if (dev.jujutsuvfx.config.VfxConfig.voidSeconds() != 5 || dev.jujutsuvfx.config.VfxConfig.shrineSeconds() != 5)
            throw new AssertionError("Invalid transaction changed other domain defaults");
    }
    private static void requestRoundTrip(DomainSettingsRequest original) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DomainSettingsRequest.CODEC.encode(buffer, original);
            if (!original.equals(DomainSettingsRequest.CODEC.decode(buffer))) throw new AssertionError("Request fields lost");
            if (buffer.isReadable()) throw new AssertionError("Unread request fields");
        } finally { buffer.release(); }
    }
}
