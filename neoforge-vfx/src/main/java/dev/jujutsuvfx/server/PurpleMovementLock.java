package dev.jujutsuvfx.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** A short cast-owned movement/aim anchor; it never changes abilities, gravity or player ticking. */
final class PurpleMovementLock {
    private final ServerPlayer owner;
    private final ResourceKey<Level> dimension;
    private final Vec3 position;
    private final float yaw, pitch;
    private final long until;
    private boolean released;

    PurpleMovementLock(ServerPlayer player, long until, float yaw, float pitch) {
        owner = player; dimension = player.level().dimension(); position = player.position();
        this.yaw = yaw; this.pitch = pitch; this.until = until;
        // Anchor the caster independently of a moving mount, without freezing other passengers.
        if (player.isPassenger()) player.stopRiding();
        hold(player, PurpleCastClock.now(player.getServer()));
        // Correct position only. Zero relative rotation preserves the client's already locked view,
        // even if the server had not processed its most recent ordinary movement/look packet yet.
        player.connection.teleport(position.x, position.y, position.z, player.getYRot(), player.getXRot(),
                Set.of(RelativeMovement.Y_ROT, RelativeMovement.X_ROT));
    }

    boolean hold(ServerPlayer player, long now) {
        if (released || player != owner || now > until || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || !player.level().dimension().equals(dimension)) {
            released = true; return false;
        }
        if (DomainFreeze.hold(player)) return true;
        player.setPos(position.x, position.y, position.z);
        player.setYRot(yaw); player.setXRot(pitch); player.setYHeadRot(yaw);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        return true;
    }

    void release() { released = true; }
}
