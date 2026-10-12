package dev.jujutsuvfx.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.GojoCatEdit;
import dev.jujutsuvfx.network.GojoCatView;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import dev.jujutsuvfx.platform.PacketDistributor;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class GojoCatControl {
    private record Session(UUID token, UUID cat, ServerLevel level, long expires) {}
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();
    private GojoCatControl() {}
    private static boolean holdingStick(Player player) {
        return player.getMainHandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())
                || player.getOffhandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get());
    }
    public static void open(ServerPlayer player, SorcererCat cat) {
        if (!player.isAlive() || player.isSpectator() || !holdingStick(player) || !cat.isAlive() || cat.isTransforming()
                || player.level() != cat.level() || player.distanceToSqr(cat) > 12 * 12
                || !dev.jujutsuvfx.platform.PacketDistributor.hasChannel(player.connection,GojoCatView.TYPE)) return;
        UUID token = UUID.randomUUID();
        SESSIONS.computeIfAbsent(player.server, key -> new HashMap<>()).put(player.getUUID(),
                new Session(token, cat.getUUID(), player.serverLevel(), player.server.getTickCount() + 1200));
        PacketDistributor.sendToPlayer(player, GojoCatView.of(cat, token, true));
    }
    public static void edit(GojoCatEdit packet, Player sender) {
        if (!(sender instanceof ServerPlayer player)) return;
        Map<UUID, Session> sessions = SESSIONS.get(player.server);
        if (sessions == null) return;
        Session session = sessions.get(player.getUUID());
        if (session == null || !session.token.equals(packet.session())) return;
        if (packet.action() == GojoCatEdit.CLOSE) { sessions.remove(player.getUUID()); return; }
        if (session.expires < player.server.getTickCount() || session.level != player.level()
                || !player.isAlive() || player.isSpectator() || !holdingStick(player)) { sessions.remove(player.getUUID()); return; }
        Entity entity = session.level.getEntity(session.cat);
        if (!(entity instanceof SorcererCat cat) || !cat.isAlive() || cat.isTransforming() || player.distanceToSqr(cat) > 16 * 16) {
            sessions.remove(player.getUUID()); return;
        }
        switch (packet.action()) {
            case GojoCatEdit.APPEARANCE -> {
                cat.setAppearance(packet.value());
                if (cat.isSukuna() && packet.value() == SorcererCat.NORMAL) cat.setBrokenArm(false);
            }
            case GojoCatEdit.ARM -> cat.setBrokenArm(!cat.brokenArm());
            case GojoCatEdit.BLUE -> cat.setHands(SorcererCat.BLUE, (cat.blueHands() + 1) & 3);
            case GojoCatEdit.RED -> cat.setHands(SorcererCat.RED, (cat.redHands() + 1) & 3);
            case GojoCatEdit.CURSED -> cat.setHands(SorcererCat.CURSED, (cat.cursedHands() + 1) & 3);
            case GojoCatEdit.BLACK_FLASH -> cat.setHands(SorcererCat.BLACK_FLASH, (cat.blackFlashHands() + 1) & 3);
            case GojoCatEdit.AUTOMATIC -> cat.setAutomaticEnergy(!cat.automaticEnergy());
            case GojoCatEdit.BEHAVIOR -> cat.setBehavior(packet.value());
            case GojoCatEdit.QUERY -> { /* Refresh live controls and keep this open view's session alive. */ }
            default -> { return; }
        }
        sessions.put(player.getUUID(), new Session(session.token, session.cat, session.level, player.server.getTickCount() + 1200));
        PacketDistributor.sendToPlayer(player, GojoCatView.of(cat, session.token, false));
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        registerBehavior(event, "gojocat", false);
        registerBehavior(event, "sukunacat", true);
    }
    private static void registerBehavior(RegisterCommandsEvent event, String root, boolean sukuna) {
        event.getDispatcher().register(Commands.literal(root).requires(source -> source.hasPermission(2))
                .then(Commands.literal("behavior").then(Commands.argument("mode", IntegerArgumentType.integer(0, 2))
                        .executes(c -> behavior(c.getSource(), IntegerArgumentType.getInteger(c, "mode"), null, sukuna))
                        .then(Commands.argument("targets", EntityArgument.entities()).executes(c -> behavior(c.getSource(),
                                IntegerArgumentType.getInteger(c, "mode"), EntityArgument.getEntities(c, "targets"), sukuna))))));
    }
    private static int behavior(CommandSourceStack source, int mode, Collection<? extends Entity> selected, boolean sukuna) {
        if (selected == null) {
            var nearest = source.getLevel().getEntitiesOfClass(SorcererCat.class,
                            new AABB(source.getPosition(), source.getPosition()).inflate(64), cat -> cat.isAlive() && (sukuna ? cat.isSukuna() : cat.isGojo()))
                    .stream().min(Comparator.comparingDouble(cat -> cat.distanceToSqr(source.getPosition()))).orElse(null);
            selected = nearest == null ? List.of() : List.of(nearest);
        }
        int count = 0;
        for (Entity entity : selected) if (entity instanceof SorcererCat cat && cat.isAlive() && (sukuna ? cat.isSukuna() : cat.isGojo())) { cat.setBehavior(mode); count++; }
        String translation = "command.jujutsuvfx." + (sukuna ? "sukuna_cat." : "gojo_cat.");
        if (count == 0) source.sendFailure(Component.translatable(translation + "none"));
        else {
            int changed = count;
            source.sendSuccess(() -> Component.translatable(translation + "behavior", changed, mode), false);
        }
        return count;
    }
    @SubscribeEvent public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount() % 40 != 0) return;
        var sessions = SESSIONS.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        if (sessions != null) sessions.entrySet().removeIf(entry -> entry.getValue().expires < net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount()
                || net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(entry.getKey()) == null);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()); }
}
