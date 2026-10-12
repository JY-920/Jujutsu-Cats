package dev.jujutsuvfx.platform;
import net.minecraft.network.Connection;
/** Lets common networking query the client connection without linking a client-only class. */
public interface ConnectionAccess {
    Connection jujutsuvfx$connection();
}
