package dev.jujutsuvfx.client;

import dev.jujutsuvfx.network.FistImpact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import java.util.ArrayList;
import java.util.List;

/** Only server-confirmed melee contacts create bursts. Held auras need no per-frame packets. */
public final class ClientFistEffects {
    static final float LIFETIME = .90f;
    private static ClientLevel owner;
    private static final List<Burst> HITS = new ArrayList<>();
    record Burst(Vec3 point, Vec3 direction, long tick, float power, float seed, boolean attacker, boolean empowered) {
        float age(long now, float partial) { return Math.max(0, (now - tick + partial) / 20f); }
    }
    private ClientFistEffects() {}
    static void clear() { HITS.clear(); FistAuraRenderer.reset(); CatTechniqueEffects.clear(); CatFistEffects.clear(); CatCombatEffects.clear(); }
    public static void receive(FistImpact packet) {
        receive(packet, false);
    }
    public static void receive(FistImpact packet, boolean empowered) {
        Minecraft mc = Minecraft.getInstance();
        changeLevel(mc.level);
        if (owner == null || mc.player == null || !finite(packet.point()) || !finite(packet.direction())
                || !Float.isFinite(packet.strength()) || EffectView.distanceToSqr(packet.point()) > 128 * 128) return;
        if (HITS.size() >= 32) HITS.remove(0);
        float seed = Math.floorMod(packet.attackerId() * 31 + packet.targetId() * 13 + (int) ClientEffectClock.ticks(), 1021);
        HITS.add(new Burst(packet.point(), packet.direction(), ClientEffectClock.ticks(),
                dev.jujutsuvfx.platform.Numbers.clamp(packet.strength(), .2f, 1), seed, packet.attackerId() == mc.player.getId(), empowered));
    }
    static void changeLevel(ClientLevel level) {
        if (owner != level) { owner = level; clear(); }
    }
    static void tick(Minecraft mc) {
        changeLevel(mc.level);
        if (owner != null) HITS.removeIf(hit -> hit.age(ClientEffectClock.ticks(), 0) > LIFETIME);
        CatTechniqueEffects.tick();
        CatFistEffects.tick();
        CatCombatEffects.tick();
    }
    static void beginFrame() { FistAuraRenderer.clear(); CatTechniqueEffects.beginFrame(); CatFistEffects.beginFrame(); }
    static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        changeLevel(mc.level);
        if (owner != null && (!HITS.isEmpty() || FistAuraRenderer.hasWorldHands())) {
            try (DomainRenderer.State ignored = new DomainRenderer.State()) {
                FistWaveRenderer.render(event, HITS);
                if (FistSpriteRenderer.ready()) FistAuraRenderer.renderWorld(event);
            } finally { FistAuraRenderer.clear(); }
        }
        if (owner != null) CatTechniqueEffects.render(event);
        if (owner != null) CatFistEffects.render(event);
        if (owner != null) CatCombatEffects.render(event);
    }
    static void camera(ViewportEvent.ComputeCameraAngles event, long now, float partial, float scale) {
        CatCombatEffects.camera(event, partial, scale);
        float pitch = 0, yaw = 0, roll = 0;
        for (Burst hit : HITS) {
            if (!hit.attacker()) continue;
            float t = hit.age(now, partial), kick = hit.power() * (float) Math.exp(-t * 22) * .40f;
            pitch -= kick * (float) Math.cos(t * 52);
            yaw += kick * (float) Math.sin(t * 67 + hit.seed()) * .35f;
            roll += kick * (float) Math.sin(t * 48 + hit.seed()) * .40f;
        }
        event.setPitch(event.getPitch() + pitch * scale);
        event.setYaw(event.getYaw() + yaw * scale);
        event.setRoll(event.getRoll() + roll * scale);
    }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
}
