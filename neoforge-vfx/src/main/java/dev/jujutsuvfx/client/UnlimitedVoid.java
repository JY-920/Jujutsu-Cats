package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.network.DomainControl;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** A fixed domain around the caster; the server owns freezing and the end acknowledgement. */
final class UnlimitedVoid {
    final Vec3 center, axis;
    final float radius;
    final long born;
    private long settlesAt, ends = Long.MAX_VALUE;
    private long nextWindCheck;
    private VoidWindSound wind;
    private boolean opened, ending;
    boolean enclosed = true;

    UnlimitedVoid(Minecraft mc, float radius, float seconds) {
        this(mc,mc.player.position().add(0,.025,0),mc.player.getLookAngle(),radius,ClientEffectClock.ticks(),Math.round(seconds*20));
    }
    UnlimitedVoid(Minecraft mc,Vec3 center,Vec3 look,float radius,long born,int remaining) {
        this.radius=radius;this.center=center;
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < .001) horizontal = new Vec3(0, 0, 1);
        axis = horizontal.normalize().add(0, .24, 0).normalize();
        this.born=born;settlesAt=ClientEffectClock.ticks()+remaining;
        opened=age(ClientEffectClock.ticks(),0)>=.94f;
        if(ClientEffectClock.ticks()-born<4)sound(mc, SoundEvents.BEACON_ACTIVATE, .5f, 1.55f);
    }

    float age(long tick, float partial) { return (tick - born + partial) / 20f; }
    float remaining(long tick) { return Math.max(0, (settlesAt - tick) / 20f); }
    float expansion(long tick, float partial) {
        return DomainVolume.expansion(DomainVolume.UNLIMITED_VOID,
                Math.min((tick-born) + partial, settlesAt-born) / 20f);
    }
    float visibility(long tick, float partial) {
        return smooth(age(tick, partial) / .20f) * (ends == Long.MAX_VALUE ? 1
                : smooth((ends - tick - partial) / DomainControl.CLOSE_TICKS));
    }
    boolean expired(long tick) { return tick >= ends; }
    void awaitSettlement(long tick) { settlesAt = Math.min(settlesAt, tick); }
    void reschedule(long deadline) { if(ends==Long.MAX_VALUE)settlesAt=deadline; }
    void close(long tick) {
        awaitSettlement(tick);
        ends = Math.min(ends, tick + DomainControl.CLOSE_TICKS);
    }
    String phase(long tick) {
        if (ends != Long.MAX_VALUE) return "消退 · 即将解除冻结";
        if (tick >= settlesAt) return "等待服务端结束确认";
        return age(tick, 0) < .94f ? "白色侵染 · 时间静止" : age(tick,0)<3.10f ? "白幕破开" : "无量空处 · 时间静止";
    }
    void tick(Minecraft mc) {
        long now = ClientEffectClock.ticks();
        if (!opened && age(now, 0) >= .94f) {
            opened = true;
            sound(mc, SoundEvents.AMETHYST_BLOCK_CHIME, .85f, .65f);
        }
        if (opened && !expired(now) && mc.player != null && mc.player.isAlive() && now >= nextWindCheck) {
            nextWindCheck = now + 20;
            // Recover after a sound-device/resource reload; active loops never restart or stack.
            if (wind == null || !mc.getSoundManager().isActive(wind)) {
                if (wind != null) wind.cancel();
                wind = new VoidWindSound(this, mc.level);
                mc.getSoundManager().play(wind);
            }
        }
        if (!ending && ends != Long.MAX_VALUE) {
            ending = true;
            sound(mc, SoundEvents.BEACON_DEACTIVATE, .45f, 1.1f);
        }
    }
    void stop() {
        if (wind != null) wind.cancel();
        wind = null;
    }
    private void sound(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.level.playLocalSound(center.x, center.y + 1, center.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }
    private static float smooth(float t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
}
