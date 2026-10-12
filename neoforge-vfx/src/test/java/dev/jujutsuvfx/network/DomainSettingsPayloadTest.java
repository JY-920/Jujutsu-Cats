package dev.jujutsuvfx.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class DomainSettingsPayloadTest {
    public static void main(String[] args) {
        requestRoundTrip(new DomainSettingsRequest(0, false, 5, 5));
        requestRoundTrip(new DomainSettingsRequest(17, true, 5, 120));
        for (int result = 0; result <= DomainSettingsState.FAILED; result++) {
            var original = new DomainSettingsState(29, result, result != DomainSettingsState.DENIED, 120, 5);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                DomainSettingsState.CODEC.encode(buffer, original);
                if (!original.equals(DomainSettingsState.CODEC.decode(buffer))) throw new AssertionError("State fields lost");
                if (buffer.isReadable()) throw new AssertionError("Unread reply fields");
            } finally { buffer.release(); }
        }
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 4, 121, Integer.MAX_VALUE}) {
            var voidInvalid = new DomainSettingsRequest(1, true, invalid, 5);
            var shrineInvalid = new DomainSettingsRequest(1, true, 5, invalid);
            requestRoundTrip(voidInvalid); // Reject bad values at the server boundary, not in the decoder.
            if (voidInvalid.validValues() || shrineInvalid.validValues()) throw new AssertionError("Invalid duration accepted");
            if (new DomainSettingsState(1, 0, true, invalid, 5).validValues()) throw new AssertionError("Invalid snapshot accepted");
        }
        System.out.println("Domain settings wire round trips, permission replies and bounds passed");
    }
    private static void requestRoundTrip(DomainSettingsRequest original) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            DomainSettingsRequest.CODEC.encode(buffer, original);
            if (!original.equals(DomainSettingsRequest.CODEC.decode(buffer))) throw new AssertionError("Request fields lost");
            if (buffer.isReadable()) throw new AssertionError("Unread request fields");
        } finally { buffer.release(); }
    }
}
