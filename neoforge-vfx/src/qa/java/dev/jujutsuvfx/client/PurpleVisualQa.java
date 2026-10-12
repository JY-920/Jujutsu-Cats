package dev.jujutsuvfx.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Explicit QA source set only. Never include this class in a release jar. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class PurpleVisualQa {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ArrayDeque<String> SCREENSHOTS = new ArrayDeque<>();
    private static Run run;
    private static int serial;

    private PurpleVisualQa() {}

    private static final class Run {
        final ClientLevel level;
        final int heldTicks;
        final String id;
        final Set<Integer> sessions = new HashSet<>();
        final Set<String> captures = new HashSet<>();
        final Set<String> failures = new HashSet<>();
        volatile boolean equipped;
        int warmup = 8, waitTicks, elapsed, releaseTick = -1, launchTick = -1;
        int lastPhase = -1, launchTransitions, flightSamples;
        boolean started, sawFullHold, networked;
        float flightRadius = Float.NaN, firstRadius = Float.NaN, maxChargeRadius;
        Run(ClientLevel level, int heldTicks) {
            this.level = level;
            this.heldTicks = heldTicks;
            id = String.format(Locale.ROOT, "vfxqa-%d-run%02d-hold%03d", System.currentTimeMillis(), ++serial, heldTicks);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vfxqa")
                .then(Commands.literal("scene").executes(c -> scene()))
                .then(Commands.literal("purple")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 100))
                                .executes(c -> start(IntegerArgumentType.getInteger(c, "ticks")))))
                .then(Commands.literal("cancel").executes(c -> { cancel("operator canceled"); return 1; })));
    }

    /** Both name and ownership checks are repeated before every mutation. LAN is deliberately excluded. */
    private static IntegratedServer permittedServer(Minecraft mc) {
        if (!mc.hasSingleplayerServer() || mc.level == null || mc.player == null) return null;
        IntegratedServer server = mc.getSingleplayerServer();
        return server != null && !server.isPublished() && qaWorld(server) ? server : null;
    }

    private static boolean qaWorld(IntegratedServer server) {
        String name = server.getWorldData().getLevelName();
        return name.equals("VFX-QA") || name.startsWith("VFX-QA-");
    }

    private static int start(int heldTicks) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = permittedServer(mc);
        if (server == null) { message("Refused: use a private singleplayer world named VFX-QA or VFX-QA-... ."); return 0; }
        if (run != null || ClientEvents.purpleCast() != null) {
            message("Wait for the previous shot to finish, or /rift stop before starting QA."); return 0;
        }
        Run next = new Run(mc.level, heldTicks);
        UUID playerId = mc.player.getUUID();
        int slot = mc.player.getInventory().selected;
        run = next;
        server.execute(() -> {
            if (!qaWorld(server) || server.isPublished()) return;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            player.getInventory().setItem(slot, VfxItems.PURPLE.toStack());
            player.containerMenu.broadcastChanges();
            next.equipped = true;
        });
        LOG.info("[VFX-QA] START {} heldTicks={} world={} input=KeyMapping/use-item/network", next.id, heldTicks,
                server.getWorldData().getLevelName());
        message("Queued " + heldTicks + " ticks of real item use; screenshots and assertions go to the QA instance.");
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preTick(ClientTickEvent.Pre event) {
        Run current = run;
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (permittedServer(mc) == null || mc.level != current.level) { cancel("world/connection changed"); return; }
        if (!current.started) {
            if (++current.waitTicks > 240) { fail(current, "item/input did not become ready within 12 seconds"); cancel("setup timeout"); return; }
            if (!current.equipped || mc.screen != null || !VfxItems.isPurple(mc.player.getMainHandItem())) return;
            if (--current.warmup > 0) return;
            if (mc.player.isUsingItem()) { cancel("an item was already being used"); return; }
            current.started = true;
            mc.options.keyUse.setDown(true);
            KeyMapping.click(mc.options.keyUse.getKey());
            LOG.info("[VFX-QA] PRESS {}", current.id);
            return;
        }
        if (mc.screen != null || mc.isPaused()) { cancel("screen opened or game paused during run"); return; }
        if (current.elapsed >= current.heldTicks && current.releaseTick < 0) {
            current.releaseTick = current.elapsed;
            LOG.info("[VFX-QA] RELEASE_KEY {} elapsed={}", current.id, current.elapsed);
        }
        // Minecraft.handleKeybinds calls releaseUsingItem when the key goes up; no skill APIs are invoked here.
        mc.options.keyUse.setDown(current.releaseTick < 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void postTick(ClientTickEvent.Post event) {
        Run current = run;
        if (current == null || !current.started) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != current.level || mc.player == null) { cancel("player left"); return; }
        int t = current.elapsed;
        long now = ClientEffectClock.ticks();
        LimitlessCast cast = ClientEvents.purpleCast();
        if (cast != null) {
            current.sessions.add(cast.session);
            current.networked |= cast.networked;
            float radius = cast.coreRadius(now, 0), progress = cast.chargeProgress(now, 0);
            if (Float.isNaN(current.firstRadius)) current.firstRadius = radius;
            int phase = cast.phase();
            if (phase == 1 && current.lastPhase != 1) {
                current.launchTransitions++;
                current.launchTick = t;
                current.flightRadius = radius;
                capture(current, "launch-000t");
                if (!cast.networked) fail(current, "launch was visual-only rather than server confirmed");
                LOG.info("[VFX-QA] NETWORK_LAUNCH {} session={} held={} radius={} chargeProgress={}",
                        current.id, cast.session, current.heldTicks, radius, progress);
            }
            if (current.releaseTick < 0) {
                if (phase != 0) fail(current, "launched before key release");
                if (!cast.heldCharge()) fail(current, "cast did not originate from held item");
                if (!mc.player.isUsingItem()) fail(current, "real player use state was not active while holding");
                if (radius < .999f || radius > 5.001f) fail(current, "charge radius outside 1..5 blocks");
                current.maxChargeRadius = Math.max(current.maxChargeRadius, radius);
                if (t >= 62) {
                    current.sawFullHold = true;
                    if (Math.abs(radius - 5) > .002f || progress < .999f) fail(current, "full charge did not remain at radius 5");
                }
                if (t == 0 || t == 10 || t == 30 || t == 40 || t == 45 || t == 56 || t == 60
                        || t == 65 || t == 70 || t == 75 || t == current.heldTicks - 1)
                    capture(current, String.format(Locale.ROOT, "held-%03dt", t));
            }
            if (phase == 1) {
                current.flightSamples++;
                if (Math.abs(radius - current.flightRadius) > .002f) fail(current, "flight radius changed after server launch");
                if (radius < .999f || radius > 5.001f) fail(current, "server projectile radius outside 1..5 blocks");
                if (t - current.launchTick == 2 || t - current.launchTick == 10 || t - current.launchTick == 20)
                    capture(current, String.format(Locale.ROOT, "flight-%03dt", t - current.launchTick));
            }
            if (phase != current.lastPhase || t % 10 == 0) {
                LOG.info("[VFX-QA] SAMPLE {} elapsed={} phase={} radius={} progress={} using={} networked={} center={}",
                        current.id, t, phase, radius, progress, mc.player.isUsingItem(), cast.networked, cast.center(mc, 0));
            }
            current.lastPhase = phase;
        } else if (t < current.heldTicks) {
            fail(current, "no held cast after real use input");
        }
        current.elapsed++;
        if (current.releaseTick >= 0 && (t - current.releaseTick >= 180 || (cast == null && t - current.releaseTick >= 30))) finish(current);
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (SCREENSHOTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (permittedServer(mc) == null || mc.screen != null) return;
        String name = SCREENSHOTS.removeFirst();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                result -> LOG.info("[VFX-QA] SCREENSHOT {} {}", name, result.getString()));
    }

    private static void capture(Run current, String suffix) {
        if (current.captures.add(suffix)) SCREENSHOTS.add(current.id + "-" + suffix);
    }

    private static void fail(Run current, String detail) {
        if (current.failures.add(detail)) LOG.error("[VFX-QA] ASSERT_FAIL {} {}", current.id, detail);
    }

    private static void finish(Run current) {
        if (current.sessions.size() != 1 || current.launchTransitions != 1) fail(current, "expected exactly one cast session and one launch transition");
        if (!current.networked) fail(current, "no negotiated server technique channel");
        if (current.flightSamples < 2) fail(current, "not enough server flight samples");
        if (Float.isNaN(current.firstRadius) || current.firstRadius > 1.10f) fail(current, "initial charge did not start at radius 1");
        if (current.heldTicks >= 64 && !current.sawFullHold) fail(current, "did not observe full charge remaining held beyond 3 seconds");
        if (current.heldTicks >= 64 && Math.abs(current.flightRadius - 5) > .002f) fail(current, "full held release was not radius 5 on server");
        if (current.heldTicks <= 30 && current.flightRadius >= 4) fail(current, "short held release incorrectly used full projectile radius");
        LOG.info("[VFX-QA] RESULT {} {} heldTicks={} sessions={} launches={} initialRadius={} maxHeldRadius={} flightRadius={} flightSamples={} fullHold={} failures={}",
                current.id, current.failures.isEmpty() ? "PASS" : "FAIL", current.heldTicks, current.sessions.size(),
                current.launchTransitions, current.firstRadius, current.maxChargeRadius, current.flightRadius,
                current.flightSamples, current.sawFullHold, current.failures);
        message((current.failures.isEmpty() ? "PASS " : "FAIL ") + current.id + "; see logs/latest.log and screenshots.");
        Minecraft.getInstance().options.keyUse.setDown(false);
        if (run == current) run = null;
    }

    private static void cancel(String reason) {
        Run previous = run;
        run = null;
        Minecraft.getInstance().options.keyUse.setDown(false);
        SCREENSHOTS.clear();
        if (previous != null) LOG.warn("[VFX-QA] CANCELED {} reason={} failures={}", previous.id, reason, previous.failures);
    }

    private static int scene() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = permittedServer(mc);
        if (server == null) { message("Refused: scene editing is restricted to a private VFX-QA singleplayer world."); return 0; }
        if (run != null || ClientEvents.purpleCast() != null) { message("Finish or stop the current cast before resetting the QA scene."); return 0; }
        UUID playerId = mc.player.getUUID();
        server.execute(() -> {
            if (!qaWorld(server) || server.isPublished()) return;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            int floor = 80;
            // Fixed, small test exhibit. No source world is imported or opened by this driver.
            for (int x = -20; x <= 20; x++) for (int z = -10; z <= 60; z++) {
                put(level, x, floor, z, Math.floorMod(x / 3 + z / 3, 2) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
            }
            for (int z : new int[]{20, 36, 52}) for (int x = -16; x <= 16; x++) for (int y = 81; y <= 96; y++) {
                Block material = Math.floorMod(Math.floorDiv(x, 3) + Math.floorDiv(y, 2), 4) == 0 ? Blocks.QUARTZ_BLOCK
                        : Math.floorMod(x, 7) == 0 ? Blocks.OAK_LOG
                        : y % 4 == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.STONE_BRICKS;
                put(level, x, y, z, material);
            }
            for (int x : new int[]{-11, 11}) for (int z : new int[]{7, 28, 44}) {
                for (int y = 81; y <= 92; y++) put(level, x, y, z, y % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.OAK_LOG);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) put(level, x + dx, 93, z + dz, Blocks.OAK_LEAVES);
                for (int dx = -2; dx <= 2; dx++) put(level, x + dx, 83, z, Blocks.DARK_OAK_FENCE);
            }
            // Keep the observation lane clear even when QA is intentionally run in a non-flat QA world.
            for (int x = -2; x <= 2; x++) for (int y = 81; y <= 86; y++) for (int z = -8; z <= 6; z++) put(level, x, y, z, Blocks.AIR);
            level.setWeatherParameters(12000, 0, false, false);
            level.setDayTime(6000);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, .5, floor + 1, -6.5, 0, 0);
            LOG.info("[VFX-QA] SCENE_READY world={} dimension={} floorY={} camera=(0.5,81,-6.5) yaw=0 pitch=0 wallsZ=20,36,52",
                    server.getWorldData().getLevelName(), level.dimension().location(), floor);
            mc.execute(() -> {
                message("QA scene ready: /vfxqa purple 10, 30, or 80. Rebuild scene before comparing a new charge radius.");
                SCREENSHOTS.add("vfxqa-scene-" + System.currentTimeMillis());
            });
        });
        return 1;
    }

    private static void put(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[VFX-QA] " + text), false);
    }
}
