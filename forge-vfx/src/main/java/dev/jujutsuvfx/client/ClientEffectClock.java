package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.EffectClock;
import net.minecraft.client.multiplayer.ClientLevel;

/** One monotonically advancing clock for all local VFX, independent of ClientboundSetTimePacket. */
final class ClientEffectClock {
    private static final EffectClock CLOCK = new EffectClock();

    private ClientEffectClock() {}
    static void changeLevel(ClientLevel level) { CLOCK.changeWorld(level); }
    static void tick(ClientLevel level, boolean paused) { CLOCK.tick(level, paused); }
    static long ticks() { return CLOCK.ticks(); }
    static long after(int remainingTicks) { return CLOCK.after(remainingTicks); }
}
