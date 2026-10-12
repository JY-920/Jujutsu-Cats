package dev.jujutsuvfx.client;

import dev.jujutsuvfx.network.DomainControl;
import dev.jujutsuvfx.core.DomainVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One open domain, with a fixed ground anchor and tick-driven presentation. */
final class MalevolentShrine {
    int session;
    static final float WINDUP = 2.2f;
    static final float OUTRO = DomainControl.CLOSE_TICKS / 20f;
    final Vec3 center;
    final Vec3 shrineBase;
    final Vec3 right;
    final Vec3 front;
    final float radius;
    final float scale;
    final long born;
    private long settlesAt;
    private long ends = Long.MAX_VALUE;
    private boolean impactPlayed;
    private boolean endingPlayed;
    boolean enclosed = false;

    MalevolentShrine(Minecraft mc, DomainBarrage.Focus focus, float radius, float seconds) {
        this(mc,ground(mc,focus.point()),mc.player.getLookAngle(),radius,ClientEffectClock.ticks(),Math.round(seconds*20));
    }
    MalevolentShrine(Minecraft mc,Vec3 center,Vec3 look,float radius,long born,int remaining) {
        this(mc,center,look,radius,born,remaining,null);
    }
    MalevolentShrine(Minecraft mc,Vec3 center,Vec3 look,float radius,long born,int remaining,Vec3 anchor) {
        this.radius=radius;this.born=born;this.settlesAt=ClientEffectClock.ticks()+remaining;
        Vec3 forward = new Vec3(look.x, 0, look.z);
        if (forward.lengthSqr() < 0.001) forward = new Vec3(0, 0, 1);
        forward = forward.normalize();
        this.front = forward.scale(-1);
        this.right = new Vec3(front.z, 0, -front.x);
        this.center = center;
        Vec3 base = center.add(forward.scale(Math.clamp(radius * 0.43, 2.5, 9)));
        this.shrineBase = anchor==null?base.add(0, .065, 0):anchor;
        this.scale = Math.clamp(radius / 15f, 0.48f, 1.25f);
        impactPlayed=age(ClientEffectClock.ticks(),0)>=WINDUP;
        if(ClientEffectClock.ticks()-born<4)sound(mc, SoundEvents.BEACON_ACTIVATE, 0.7f, 0.55f);
    }

    private static Vec3 ground(Minecraft mc, Vec3 point) {
        HitResult hit = mc.level.clip(new ClipContext(point.add(0, 10, 0), point.add(0, -40, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? point.add(0, -1, 0) : hit.getLocation().add(0, 0.025, 0);
    }

    float age(long tick, float partial) { return (tick - born + partial) / 20f; }
    float remaining(long tick, float partial) { return ((ends == Long.MAX_VALUE ? settlesAt : ends) - tick - partial) / 20f; }
    boolean expired(long tick) { return tick >= ends; }
    boolean attacking(long tick) { return age(tick, 0) >= WINDUP && tick < settlesAt && ends == Long.MAX_VALUE; }
    float visibility(long tick, float partial) {
        return smooth(age(tick, partial) / 0.5f)
                * (ends == Long.MAX_VALUE ? 1 : smooth(remaining(tick, partial) / OUTRO));
    }
    float reveal(long tick, float partial) { return smooth((age(tick, partial) - 0.35f) / 1.6f); }
    float expansion(long tick, float partial) {
        return DomainVolume.expansion(DomainVolume.SHRINE, Math.min((tick-born) + partial, settlesAt-born) / 20f);
    }
    void awaitSettlement(long tick) { settlesAt = Math.min(settlesAt, tick); }
    void reschedule(long deadline) { if(ends==Long.MAX_VALUE)settlesAt=deadline; }
    void close(long tick) {
        awaitSettlement(tick);
        ends = Math.min(ends, tick + DomainControl.CLOSE_TICKS);
    }

    void tick(Minecraft mc) {
        long tick = ClientEffectClock.ticks();
        if (!impactPlayed && attacking(tick)) {
            sound(mc, SoundEvents.END_PORTAL_SPAWN, 0.36f, 0.65f);
            impactPlayed = true;
        }
        if (!endingPlayed && ends != Long.MAX_VALUE) {
            sound(mc, SoundEvents.BEACON_DEACTIVATE, 0.5f, 0.6f);
            endingPlayed = true;
        }
    }

    private void sound(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.level.playLocalSound(shrineBase.x, shrineBase.y + 2, shrineBase.z, sound,
                SoundSource.BLOCKS, volume, pitch, false);
    }

    String phase(long tick) {
        if (ends != Long.MAX_VALUE) return "消退";
        if (tick >= settlesAt) return "等待收束";
        if (age(tick, 0) < 0.5) return "显现";
        if (age(tick, 0) < WINDUP) return "扩张";
        return "持续斩击";
    }

    private static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }
}
