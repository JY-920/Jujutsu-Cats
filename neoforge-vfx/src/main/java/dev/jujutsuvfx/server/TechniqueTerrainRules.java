package dev.jujutsuvfx.server;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.network.VfxSettingsRequest;
import dev.jujutsuvfx.network.VfxSettingsState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TechniqueTerrainRules {
    private TechniqueTerrainRules() {}
    public static boolean enabled() { return VfxConfig.destruction(); }
    public static boolean cleavePhysics() {
        return enabled() && VfxConfig.cleavePhysics() && dev.jujutsuvfx.compat.CleaveCompatibility.aeronauticsAvailable();
    }
    public static boolean canGrief(LivingEntity owner) {
        if (!enabled() || owner.level().isClientSide() || !owner.isAlive() || owner.isSpectator()
                || owner instanceof ServerPlayer player && !player.mayBuild()) return false;
        // Use our own default, then let protection mods veto it. Never change any world game rule.
        var event = new EntityMobGriefingEvent(owner.level(), owner);
        event.setCanGrief(true);
        return NeoForge.EVENT_BUS.post(event).canGrief() && enabled();
    }
    public static void handle(VfxSettingsRequest packet, Player sender) {
        if (!(sender instanceof ServerPlayer player)) return;
        int action = packet.action();
        boolean changed = false;
        // Recheck the real permission on EVERY edit; a modified client cannot bypass the GUI lock.
        if ((action == 0 || action == 1) && player.hasPermissions(2) && VfxConfig.SERVER.isLoaded()
                && enabled() != (action == 1)) {
            try { VfxConfig.destruction(action == 1); changed = true; }
            catch (RuntimeException failure) { LogUtils.getLogger().error("Could not save technique terrain setting", failure); }
        }
        if ((action == 2 || action == 3) && player.hasPermissions(2) && VfxConfig.SERVER.isLoaded()
                && enabled() && dev.jujutsuvfx.compat.CleaveCompatibility.aeronauticsAvailable()
                && VfxConfig.cleavePhysics() != (action == 3)) {
            try { VfxConfig.cleavePhysics(action == 3); changed = true; }
            catch (RuntimeException failure) { LogUtils.getLogger().error("Could not save Cleave physics setting", failure); }
        }
        if ((action == 4 || action == 5) && player.hasPermissions(2) && VfxConfig.SERVER.isLoaded()
                && VfxConfig.cinematic() != (action == 5)) {
            try { VfxConfig.cinematic(action == 5); changed = true; CatDuel.configurationChanged(player.getServer()); }
            catch (RuntimeException failure) { LogUtils.getLogger().error("Could not save cinematic setting", failure); }
        }
        if ((action == 6 || action == 7) && player.hasPermissions(2) && VfxConfig.SERVER.isLoaded()
                && enabled() && VfxConfig.unlimitedDestruction() != (action == 7)) {
            try { VfxConfig.unlimitedDestruction(action == 7); changed = true; }
            catch (RuntimeException failure) { LogUtils.getLogger().error("Could not save Unlimited Purple terrain setting", failure); }
        }
        if (changed) for (ServerPlayer observer : player.server.getPlayerList().getPlayers()) send(observer);
        else send(player);
    }
    private static void send(ServerPlayer player) {
        if (player.connection.hasChannel(VfxSettingsState.TYPE))
            PacketDistributor.sendToPlayer(player, new VfxSettingsState(enabled(), player.hasPermissions(2) && VfxConfig.SERVER.isLoaded(),
                    dev.jujutsuvfx.compat.CleaveCompatibility.aeronauticsAvailable(), VfxConfig.cleavePhysics(), VfxConfig.cinematic(), VfxConfig.unlimitedDestruction()));
    }
}
