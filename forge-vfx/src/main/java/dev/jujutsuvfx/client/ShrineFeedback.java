package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;

/** Bounded, cast-owned audiovisual impulses. Dense blade fields do not spawn one sound per blade. */
final class ShrineFeedback {
    private static final SoundEvent SWEEP = sound("shrine_slash");
    private static final SoundEvent HIT = sound("shrine_impact");
    private static final SoundEvent FOCUS_CUT = sound("shrine_focus_cut");
    private static final SoundEvent FINISH = sound("shrine_finish");
    record Impulse(Vec3 point, Vec3 direction, long tick, float power, float duration, float seed) {
        float age(long now, float partial) { return (now - tick + partial) / 20f; }
        float envelope(float age) {
            if (age < 0 || age >= duration) return 0;
            float tail = 1 - age / duration;
            return Math.min(age / .009f, 1) * tail * tail;
        }
    }
    private final Vec3 center;
    private final float radius;
    private final int session;
    private final List<Impulse> impulses = new ArrayList<>();
    private long nextSweep, nextHit;
    private boolean finished;

    ShrineFeedback(Vec3 center, float radius) { this(center,radius,0); }
    ShrineFeedback(Vec3 center, float radius,int session) { this.center = center; this.radius = radius;this.session=session; }

    void wave(Minecraft mc, Rift blade, long now) {
        if (finished || blade == null || now < nextSweep) return;
        boolean focused=ClientShrineFocus.active(session);nextSweep = now + (focused?2:3);
        Vec3 point = closestPoint(mc, blade);
        float power = strength(mc, point);
        if (power < .01f) return;
        add(point, blade.up(), now, power * .68f, .16f, mc);
        play(mc, focused?FOCUS_CUT:SWEEP, point, (focused?.60f:.46f) * power, .94f + mc.level.random.nextFloat() * .18f);
    }

    void hit(Minecraft mc, Rift blade, long now) {
        hit(mc,blade.center(),blade.up(),now);
    }
    void hit(Minecraft mc,Vec3 point,Vec3 direction,long now){
        if (finished || now < nextHit) return;
        nextHit = now + 4;
        float power = strength(mc, point);
        if (power < .01f) return;
        add(point,direction,now,power,.20f,mc);
        if(ClientShrineFocus.active(session)){
            if(now>=nextSweep){nextSweep=now+2;play(mc,FOCUS_CUT,point,.50f*power,1.04f+mc.level.random.nextFloat()*.12f);}
        }else play(mc,HIT,point,.66f*power,.94f+mc.level.random.nextFloat()*.12f);
    }

    void finish(Minecraft mc, long now) {
        if (finished) return;
        finished = true;
        Vec3 point = center.add(0, 1.5, 0);
        float power = range(mc);
        if (power < .01f) return;
        add(point, new Vec3(.7,.7,.2).normalize(), now, power * .45f, .30f, mc);
        play(mc, FINISH, point, power * .60f, 1f);
    }

    List<Impulse> active(long now, float partial) {
        if(ClientShrineFocus.quiet(session)){impulses.clear();return impulses;}
        impulses.removeIf(p -> p.age(now, partial) >= p.duration());
        return impulses;
    }

    void camera(ViewportEvent.ComputeCameraAngles event, long now, float partial, float scale) {
        if (scale < .001f || ClientShrineFocus.quiet(session)) return;
        float pitch = 0, yaw = 0, roll = 0;
        for (Impulse p : active(now, partial)) {
            float age = p.age(now, partial), kick = p.envelope(age) * p.power();
            float recoil = (float)Math.cos(age * 63);
            pitch += kick * recoil * (.62f + .18f * (float)Math.sin(p.seed()));
            yaw += kick * (float)Math.sin(age * 51 + p.seed()) * .40f;
            roll += kick * (float)Math.cos(age * 47 + p.seed()) * .48f;
        }
        event.setPitch(event.getPitch() + dev.jujutsuvfx.platform.Numbers.clamp(pitch, -1.1f, 1.1f) * scale);
        event.setYaw(event.getYaw() + dev.jujutsuvfx.platform.Numbers.clamp(yaw, -.7f, .7f) * scale);
        event.setRoll(event.getRoll() + dev.jujutsuvfx.platform.Numbers.clamp(roll, -.75f, .75f) * scale);
    }

    void stop() {
        impulses.clear();
        // These short one-shots decay naturally; another shrine may share each event.
    }

    private float range(Minecraft mc) {
        return (float)dev.jujutsuvfx.platform.Numbers.clamp((radius + 12 - mc.gameRenderer.getMainCamera().getPosition().distanceTo(center)) / 12, 0, 1);
    }
    private float strength(Minecraft mc, Vec3 point) {
        double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(point);
        return range(mc) * (float)(.40 + .60 / (1 + distance * distance / 100));
    }
    private Vec3 closestPoint(Minecraft mc, Rift blade) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double along = dev.jujutsuvfx.platform.Numbers.clamp(eye.subtract(blade.center()).dot(blade.up()), -blade.height()*.5, blade.height()*.5);
        Vec3 point = blade.center().add(blade.up().scale(along));
        return point.distanceToSqr(center) <= radius*radius ? point : blade.center();
    }
    private void add(Vec3 point, Vec3 direction, long now, float power, float duration, Minecraft mc) {
        if(ClientShrineFocus.quiet(session))return;
        active(now, 0);
        if (impulses.size() >= 6) impulses.remove(0);
        impulses.add(new Impulse(point, direction, now, power, duration, mc.level.random.nextFloat()*6.283185f));
    }
    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(JujutsuVfx.ID, name));
    }
    private static void play(Minecraft mc, SoundEvent sound, Vec3 point, float volume, float pitch) {
        // Keep distant cuts audible inside large domains, while retaining their left/right direction.
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 delta = point.subtract(listener);
        Vec3 source = listener.add(delta.scale(Math.min(1, 10 / Math.max(delta.length(), .001))));
        mc.level.playLocalSound(source.x, source.y, source.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
