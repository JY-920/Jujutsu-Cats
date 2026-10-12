package dev.jujutsuvfx.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Disposable QA source only: changes client time, never server/world-save time and never player input. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class EffectClockVisualQa {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Field VOID_FIELD = voidField();
    private static Run run;

    private EffectClockVisualQa() {}

    private static final class Run {
        final ClientLevel level;
        final IntegratedServer server;
        final long initialRaw;
        final int firstAt;
        final String id;
        final Map<String, Float> lastTickAges = new HashMap<>(), lastFrameAges = new HashMap<>();
        final Set<String> failures = new LinkedHashSet<>(), coveredTypes = new LinkedHashSet<>();
        long lastRaw = Long.MIN_VALUE, lastTick = Long.MIN_VALUE;
        double lastFrameTime = Double.NaN;
        int elapsed, ticksObserved, framesObserved, injections, populatedInjections;
        boolean waitingForWorld = true;

        Run(Minecraft mc, IntegratedServer server, int firstAt) {
            level = mc.level;
            this.server = server;
            initialRaw = level.getGameTime();
            this.firstAt = firstAt;
            id = "clock-" + System.currentTimeMillis();
        }
    }

    private static Field voidField() {
        try {
            Field field = ClientEvents.class.getDeclaredField("unlimitedVoid");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.error("[VFX-QA-CLOCK] Cannot inspect the private domain state", failure);
            return null;
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vfxqa_clock")
                .executes(c -> start(30))
                .then(Commands.argument("delayTicks", IntegerArgumentType.integer(10, 200))
                        .executes(c -> start(IntegerArgumentType.getInteger(c, "delayTicks"))))
                .then(Commands.literal("cancel").executes(c -> { end("operator canceled", false); return 1; })));
    }

    private static IntegratedServer permitted(Minecraft mc) {
        if (mc.level == null || mc.player == null || !mc.hasSingleplayerServer()) return null;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || server.isPublished()) return null;
        String name = server.getWorldData().getLevelName();
        return name.equals("VFX-QA") || name.startsWith("VFX-QA-") ? server : null;
    }

    private static int start(int firstAt) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = permitted(mc);
        if (server == null) { message("Refused: private VFX-QA singleplayer world required."); return 0; }
        if (run != null) { message("A clock QA run is already active; wait or /vfxqa_clock cancel."); return 0; }
        if (VOID_FIELD == null) { message("Cannot inspect the domain; see the QA log."); return 0; }
        run = new Run(mc, server, firstAt);
        LOG.info("[VFX-QA-CLOCK] START {} raw={} effect={} backwardAt={} forwardAt={} finishAt={} world={}",
                run.id, run.initialRaw, ClientEffectClock.ticks(), firstAt, firstAt + 40, firstAt + 100,
                server.getWorldData().getLevelName());
        message("Clock QA queued: client time -40 then +80 ticks. Start a held Purple run now if desired.");
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        Run current = run;
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (permitted(mc) != current.server || mc.level != current.level) { end("world/connection changed", false); return; }
        if (mc.isPaused()) { current.lastFrameAges.clear(); current.lastFrameTime = Double.NaN; return; }
        if (current.waitingForWorld) {
            if (mc.screen != null) return;
            current.waitingForWorld = false;
        }

        long effect = ClientEffectClock.ticks(), raw = current.level.getGameTime();
        Map<String, Float> ages = ages(effect, 0);
        if (current.lastTick != Long.MIN_VALUE && effect - current.lastTick != 1)
            fail(current, "effect tick did not advance exactly once", "before=" + current.lastTick + " after=" + effect);
        compare(current, current.lastTickAges, ages, "TICK", .00001f);
        current.lastTick = effect;
        current.ticksObserved++;
        if (current.lastRaw != Long.MIN_VALUE && raw - current.lastRaw != 1)
            LOG.info("[VFX-QA-CLOCK] RAW_CORRECTION {} elapsed={} previous={} raw={} delta={} effect={} ages={}",
                    current.id, current.elapsed, current.lastRaw, raw, raw - current.lastRaw, effect, ages);
        current.lastRaw = raw;
        if (current.elapsed % 10 == 0)
            LOG.info("[VFX-QA-CLOCK] SAMPLE {} elapsed={} raw={} effect={} ages={}",
                    current.id, current.elapsed, raw, effect, ages);

        if (current.elapsed == current.firstAt) inject(current, -40);
        if (current.elapsed == current.firstAt + 40) inject(current, 80);
        if (current.elapsed++ >= current.firstAt + 100) end("completed", true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frame(RenderFrameEvent.Post event) {
        Run current = run;
        if (current == null || current.waitingForWorld) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != current.level || mc.isPaused()) return;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        long ticks = ClientEffectClock.ticks();
        double stamp = ticks + (double) partial;
        if (!Double.isNaN(current.lastFrameTime) && stamp + .0001 < current.lastFrameTime)
            fail(current, "render timestamp moved backward", "before=" + current.lastFrameTime + " after=" + stamp);
        compare(current, current.lastFrameAges, ages(ticks, partial), "FRAME", .00001f);
        current.lastFrameTime = stamp;
        current.framesObserved++;
    }

    private static void inject(Run current, int delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != current.level || permitted(mc) != current.server) { end("injection safety check failed", false); return; }
        long effectBefore = ClientEffectClock.ticks(), rawBefore = current.level.getGameTime();
        Map<String, Float> before = ages(effectBefore, 0);
        current.level.setGameTime(rawBefore + delta);
        long effectAfter = ClientEffectClock.ticks(), rawAfter = current.level.getGameTime();
        Map<String, Float> after = ages(effectAfter, 0);
        current.injections++;
        if (!before.isEmpty()) current.populatedInjections++;
        for (String key : before.keySet()) current.coveredTypes.add(key.startsWith("purple#") ? "purple" : "void");
        if (rawAfter != rawBefore + delta) fail(current, "client time injection did not apply", "delta=" + delta);
        if (effectBefore != effectAfter) fail(current, "raw world time changed the VFX clock", "delta=" + delta);
        if (!before.keySet().equals(after.keySet())) fail(current, "active effect set changed during injection", "delta=" + delta);
        for (String key : before.keySet()) {
            Float next = after.get(key);
            if (next == null || Math.abs(next - before.get(key)) > .00001f)
                fail(current, "injection changed an effect age", key + " before=" + before.get(key) + " after=" + next);
        }
        current.lastRaw = rawAfter;
        LOG.info("[VFX-QA-CLOCK] INJECT {} elapsed={} delta={} rawBefore={} rawAfter={} effectBefore={} effectAfter={} agesBefore={} agesAfter={}",
                current.id, current.elapsed, delta, rawBefore, rawAfter, effectBefore, effectAfter, before, after);
    }

    private static Map<String, Float> ages(long ticks, float partial) {
        Map<String, Float> result = new LinkedHashMap<>();
        LimitlessCast purple = ClientEvents.purpleCast();
        if (purple != null) result.put("purple#" + purple.session, purple.age(ticks, partial));
        if (VOID_FIELD != null) try {
            UnlimitedVoid domain = (UnlimitedVoid) VOID_FIELD.get(null);
            if (domain != null) result.put("void#" + System.identityHashCode(domain), domain.age(ticks, partial));
        } catch (IllegalAccessException failure) {
            Run current = run;
            if (current != null) fail(current, "cannot read domain state", failure.toString());
        }
        return result;
    }

    private static void compare(Run current, Map<String, Float> previous, Map<String, Float> next, String stage, float tolerance) {
        for (var entry : next.entrySet()) {
            Float old = previous.get(entry.getKey());
            if (!Float.isFinite(entry.getValue())) fail(current, stage + " invalid effect age", entry.getKey());
            if (old != null && entry.getValue() + tolerance < old)
                fail(current, stage + " effect age moved backward", entry.getKey() + " before=" + old + " after=" + entry.getValue());
        }
        previous.clear();
        previous.putAll(next);
    }

    private static void fail(Run current, String category, String detail) {
        if (current.failures.add(category + ": " + detail))
            LOG.error("[VFX-QA-CLOCK] ASSERT_FAIL {} {} {}", current.id, category, detail);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { end("logged out", false); }

    private static void end(String reason, boolean completed) {
        Run current = run;
        if (current == null) return;
        run = null;
        if (completed) {
            if (current.injections != 2) fail(current, "expected two time jumps", "count=" + current.injections);
            if (current.populatedInjections != 2) fail(current, "time jump lacked a live effect", "covered=" + current.populatedInjections);
            if (current.framesObserved < 2) fail(current, "not enough rendered frames", "count=" + current.framesObserved);
            LOG.info("[VFX-QA-CLOCK] RESULT {} {} ticks={} frames={} injections={} liveInjections={} covered={} failures={}",
                    current.id, current.failures.isEmpty() ? "PASS" : "FAIL", current.ticksObserved, current.framesObserved,
                    current.injections, current.populatedInjections, current.coveredTypes, current.failures);
            message((current.failures.isEmpty() ? "Clock QA PASS " : "Clock QA FAIL ") + current.id + " covered=" + current.coveredTypes);
        } else LOG.warn("[VFX-QA-CLOCK] CANCELED {} reason={}", current.id, reason);
        restore(current);
    }

    private static void restore(Run current) {
        // Restore a local baseline immediately, even if logout is already stopping the integrated server.
        // This only touches the original disposable ClientLevel object, never another loaded world.
        long baseline = current.initialRaw + current.ticksObserved;
        current.level.setGameTime(baseline);
        LOG.info("[VFX-QA-CLOCK] RESTORE_CLIENT_BASELINE {} raw={}", current.id, baseline);
        // Prefer a fresh authoritative read after completion. No server setGameTime/setDayTime call exists here.
        if (!current.server.isRunning()) return;
        current.server.execute(() -> {
            ServerLevel serverLevel = current.server.getLevel(current.level.dimension());
            if (serverLevel == null) return;
            long authoritative = serverLevel.getGameTime();
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level != current.level || mc.getSingleplayerServer() != current.server) return;
                current.level.setGameTime(authoritative);
                LOG.info("[VFX-QA-CLOCK] RESTORE_SERVER_TIME {} raw={} effect={}", current.id, authoritative, ClientEffectClock.ticks());
            });
        });
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[VFX-QA-CLOCK] " + text), false);
    }
}
