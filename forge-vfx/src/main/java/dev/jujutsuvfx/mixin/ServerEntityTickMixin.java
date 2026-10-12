package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.server.DomainFreeze;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerEntityTickMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void jujutsu$holdEntity(Entity entity,CallbackInfo ci){if(DomainFreeze.hold(entity)||!dev.jujutsuvfx.server.CatDuel.tickActor(entity))ci.cancel();}
    @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
    private void jujutsu$holdPassenger(Entity vehicle,Entity entity,CallbackInfo ci){if(DomainFreeze.hold(entity)||!dev.jujutsuvfx.server.CatDuel.tickActor(entity))ci.cancel();}
}
