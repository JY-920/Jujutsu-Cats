package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.platform.ConnectionAccess;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ClientPacketListener.class)
public abstract class ClientConnectionAccessor implements ConnectionAccess {
    @Accessor("connection") public abstract Connection jujutsuvfx$connection();
}
