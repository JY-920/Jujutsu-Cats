package dev.jujutsuvfx.server;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.network.DomainSettingsRequest;
import dev.jujutsuvfx.network.DomainSettingsState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import dev.jujutsuvfx.platform.PacketDistributor;

/** Executed on the server thread; every write checks current OP permissions. */
public final class DomainDurationSettings {
    private DomainDurationSettings() { }
    public static void handle(DomainSettingsRequest packet, Player sender) {
        if (!(sender instanceof ServerPlayer player)) return;
        if (!packet.update()) { send(player, packet.requestId(), DomainSettingsState.SNAPSHOT); return; }
        if (!player.hasPermissions(2) || !VfxConfig.SERVER.isLoaded()) {
            send(player, packet.requestId(), DomainSettingsState.DENIED); return;
        }
        if (!packet.validValues()) { send(player, packet.requestId(), DomainSettingsState.INVALID); return; }
        try { VfxConfig.domainSeconds(packet.voidSeconds(), packet.shrineSeconds()); }
        catch (RuntimeException failure) {
            LogUtils.getLogger().error("Could not save domain durations", failure);
            send(player, packet.requestId(), DomainSettingsState.FAILED); return;
        }
        send(player, packet.requestId(), DomainSettingsState.SAVED);
        for (ServerPlayer observer : player.server.getPlayerList().getPlayers())
            if (observer != player) send(observer, 0, DomainSettingsState.SNAPSHOT);
    }
    private static void send(ServerPlayer player, int requestId, int result) {
        if (PacketDistributor.hasChannel(player.connection, DomainSettingsState.TYPE))
            PacketDistributor.sendToPlayer(player, new DomainSettingsState(requestId, result,
                    player.hasPermissions(2) && VfxConfig.SERVER.isLoaded(), VfxConfig.voidSeconds(), VfxConfig.shrineSeconds()));
    }
}
