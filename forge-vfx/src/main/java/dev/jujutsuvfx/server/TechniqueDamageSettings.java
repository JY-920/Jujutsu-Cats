package dev.jujutsuvfx.server;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.network.DamageSettingsRequest;
import dev.jujutsuvfx.network.DamageSettingsState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import dev.jujutsuvfx.platform.PacketDistributor;

/** Runs on the server thread. GUI permissions are only a convenience, never an authorization check. */
public final class TechniqueDamageSettings {
    private TechniqueDamageSettings() {}
    public static void handle(DamageSettingsRequest packet, Player sender) {
        if (!(sender instanceof ServerPlayer player)) return;
        if (!packet.update()) { send(player, packet.requestId(), DamageSettingsState.SNAPSHOT); return; }
        if (!player.hasPermissions(2) || !VfxConfig.SERVER.isLoaded()) {
            send(player, packet.requestId(), DamageSettingsState.DENIED); return;
        }
        int[] values = packet.percents();
        if (!DamageSettingsRequest.validValues(values)) {
            send(player, packet.requestId(), DamageSettingsState.INVALID); return;
        }
        try { VfxConfig.damagePercents(values); }
        catch (RuntimeException failure) {
            LogUtils.getLogger().error("Could not save technique damage settings", failure);
            send(player, packet.requestId(), DamageSettingsState.FAILED); return;
        }
        send(player, packet.requestId(), DamageSettingsState.SAVED);
        for (ServerPlayer observer : player.server.getPlayerList().getPlayers())
            if (observer != player) send(observer, 0, DamageSettingsState.SNAPSHOT);
    }
    private static void send(ServerPlayer player, int requestId, int result) {
        if (PacketDistributor.hasChannel(player.connection, DamageSettingsState.TYPE))
            PacketDistributor.sendToPlayer(player, new DamageSettingsState(requestId, result,
                    player.hasPermissions(2) && VfxConfig.SERVER.isLoaded(), VfxConfig.damagePercents()));
    }
}
