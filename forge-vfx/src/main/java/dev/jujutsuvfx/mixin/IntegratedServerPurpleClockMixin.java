package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.PurpleCastClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** The integrated server keeps calling this outer method even when its ordinary world ticks are paused. */
@Mixin(IntegratedServer.class)
abstract class IntegratedServerPurpleClockMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void jujutsu$purplePauseClock(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        PurpleCastClock.observePause((MinecraftServer) (Object) this, Minecraft.getInstance().isPaused());
    }
}
