package dev.jujutsuvfx.server;

import dev.jujutsuvfx.network.FistImpact;
import dev.jujutsuvfx.network.FistStrike;
import dev.jujutsuvfx.item.FistCombo;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashSet;

/** No attack request channel: vanilla melee alone can reach this successful-item-hit hook. */
public final class FistCombat {
    private FistCombat() {}

    public static void confirmed(ItemStack stack, LivingEntity attacker, LivingEntity target) {
        if (!(attacker instanceof ServerPlayer player) || player.isSpectator()
                || target.level() != player.level() || !VfxItems.isHeld(stack)
                || player.getMainHandItem() != stack) return;
        // Damage and native knockback have already read this charge; only successful hits change it.
        boolean empowered = FistCombo.confirmed(stack);
        player.inventoryMenu.broadcastChanges();
        var level = player.serverLevel();
        if (DomainFreeze.hold(target)) {
            // Vanilla already sent player victims their impulse before the item post hook.
            // Correct that packet too, so a frozen victim never temporarily inherits the punch.
            target.hasImpulse = true;
            level.getChunkSource().broadcastAndSend(target, new ClientboundSetEntityMotionPacket(target));
        }

        Vec3 eye = player.getEyePosition();
        var box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        Vec3 aimEnd = eye.add(player.getLookAngle().scale(Math.max(3, eye.distanceTo(center) + 2)));
        Vec3 point = box.clip(eye, aimEnd).orElseGet(() -> box.clip(eye, center).orElse(center));
        Vec3 direction = point.subtract(eye).normalize();
        if (direction.lengthSqr() < 1.0e-8) direction = player.getLookAngle();
        point = point.subtract(direction.scale(0.025));
        float cooldown = Mth.clamp(player.getAttackStrengthScale(0.5f), 0, 1);
        FistImpact packet = new FistImpact(player.getId(), target.getId(), point, direction,
                0.2f + 0.8f * cooldown * cooldown);
        var observers = new HashSet<>(level.getChunkSource().chunkMap.getPlayersWatching(target));
        observers.addAll(level.getChunkSource().chunkMap.getPlayersWatching(player));
        for(ServerPlayer observer:level.players())if(CinematicAudio.visualRange(observer,point,160))observers.add(observer);
        observers.add(player);
        if (target instanceof ServerPlayer victim) observers.add(victim);
        FistStrike strike = new FistStrike(packet, empowered);
        for (ServerPlayer observer : observers) if (observer.level() == level) {
            if (observer.connection.hasChannel(FistStrike.TYPE)) PacketDistributor.sendToPlayer(observer, strike);
            else if (observer.connection.hasChannel(FistImpact.TYPE)) PacketDistributor.sendToPlayer(observer, packet);
        }
    }
}
