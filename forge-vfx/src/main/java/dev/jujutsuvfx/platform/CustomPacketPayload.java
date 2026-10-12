package dev.jujutsuvfx.platform;

import net.minecraft.resources.ResourceLocation;

/** Version-independent spell messages, transported over Forge's SimpleChannel. */
public interface CustomPacketPayload {
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
    Type<? extends CustomPacketPayload> type();
}
