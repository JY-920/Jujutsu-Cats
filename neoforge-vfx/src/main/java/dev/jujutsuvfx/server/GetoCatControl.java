package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.GetoCat;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.GetoCatEdit;
import dev.jujutsuvfx.network.GetoCatView;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class GetoCatControl {
    private record Session(UUID token, UUID cat, ServerLevel level, long expires) {}
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();
    private GetoCatControl() {}
    private static boolean holdingStick(Player player) {
        return player.getMainHandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())
                || player.getOffhandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get());
    }
    public static void open(ServerPlayer player, GetoCat cat) {
        if (!player.isAlive() || player.isSpectator() || !holdingStick(player) || !cat.isAlive()
                || player.level() != cat.level() || player.distanceToSqr(cat) > 12 * 12
                || !player.connection.hasChannel(GetoCatView.TYPE)) return;
        UUID token = UUID.randomUUID();
        SESSIONS.computeIfAbsent(player.server, key -> new HashMap<>()).put(player.getUUID(),
                new Session(token, cat.getUUID(), player.serverLevel(), player.server.getTickCount() + 1200));
        PacketDistributor.sendToPlayer(player, GetoCatView.of(cat, token, true));
    }
    public static void edit(GetoCatEdit packet, Player sender) {
        if (!(sender instanceof ServerPlayer player)) return;
        Map<UUID, Session> sessions = SESSIONS.get(player.server);
        if (sessions == null) return;
        Session session = sessions.get(player.getUUID());
        if (session == null || !session.token.equals(packet.session())) return;
        if (packet.action() == GetoCatEdit.CLOSE) { sessions.remove(player.getUUID()); return; }
        if (session.expires < player.server.getTickCount() || session.level != player.level()
                || !player.isAlive() || player.isSpectator() || !holdingStick(player)) { sessions.remove(player.getUUID()); return; }
        Entity entity = session.level.getEntity(session.cat);
        if (!(entity instanceof GetoCat cat) || !cat.isAlive() || player.distanceToSqr(cat) > 16 * 16) {
            sessions.remove(player.getUUID()); return;
        }
        switch (packet.action()) {
            case GetoCatEdit.DANCE -> { if (packet.value() < 0 || packet.value() > 2) return; cat.setDanceStyle(packet.value()); }
            case GetoCatEdit.POSE -> { if (!dev.jujutsuvfx.core.GetoDebugPose.valid(packet.value())) return; cat.setDebugPose(packet.value()); }
            case GetoCatEdit.QUERY -> { }
            default -> { return; }
        }
        sessions.put(player.getUUID(), new Session(session.token, session.cat, session.level, player.server.getTickCount() + 1200));
        PacketDistributor.sendToPlayer(player, GetoCatView.of(cat, session.token, false));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 40 != 0) return;
        var sessions = SESSIONS.get(event.getServer());
        if (sessions != null) sessions.entrySet().removeIf(entry -> entry.getValue().expires < event.getServer().getTickCount()
                || event.getServer().getPlayerList().getPlayer(entry.getKey()) == null);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
}
