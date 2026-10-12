package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.SukunaSlashStyle;
import dev.jujutsuvfx.network.SukunaCut;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/** Domain-style blades with server-confirmed planes, palettes and cross arms. */
public final class ClientSukunaSlashes {
    private static final SoundEvent CUT = sound("shrine_slash"), IMPACT = sound("shrine_impact");
    private static long born = Long.MIN_VALUE, lastSound = Long.MIN_VALUE;
    private static float strength;
    private ClientSukunaSlashes() {}

    public static void receive(SukunaCut packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !finite(packet.center()) || !finite(packet.forward()) || !finite(packet.stroke())
                || !Float.isFinite(packet.length()) || packet.length() <= 0 || packet.length() > 256
                || packet.kind() < 0 || packet.kind() > 1) return;
        ClientEvents.prepareSukunaEffects(mc.level);
        Vec3 forward = packet.forward().normalize();
        Vec3 up = packet.stroke().subtract(forward.scale(packet.stroke().dot(forward))).normalize();
        if (forward.lengthSqr() < .99 || up.lengthSqr() < .99) return;
        long now = ClientEffectClock.ticks();
        boolean heavy = packet.kind() == 1;
        float seed = SukunaSlashStyle.materialSeed(packet.styleSeed());
        float length = Math.min(256, packet.length() * SukunaSlashStyle.mainScale(packet.styleSeed()));
        float lifetime = SukunaSlashStyle.lifetime(packet.styleSeed());
        float crossLength = Math.min(256, length * SukunaSlashStyle.crossScale(packet.styleSeed()));
        emit(packet.center(), forward, up, length, lifetime, now, seed, heavy);
        Vec3 second = SukunaSlashStyle.secondStroke(up, forward);
        // The server confirms the second physical plane; failed secondary cuts never invent a cross.
        if (packet.cross()) emit(packet.center(), forward, second, crossLength, lifetime, now, seed, heavy);
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 audiblePoint = packet.center();
        if (heavy) {
            // On a large structure the nearby part of the cut, rather than its distant
            // center, determines the impact sound and shake felt by this observer.
            audiblePoint = closestPoint(listener, packet.center(), forward, up, length * .5);
            if (packet.cross()) {
                Vec3 other = closestPoint(listener, packet.center(), forward, second, crossLength * .5);
                if (listener.distanceToSqr(other) < listener.distanceToSqr(audiblePoint)) audiblePoint = other;
            }
        }
        double distance = listener.distanceTo(audiblePoint);
        float scale = heavy ? dev.jujutsuvfx.platform.Numbers.clamp(packet.length() / 24, 1, 1.35f) : 1;
        float power = (heavy ? 1.05f : .55f) * scale * (float) (1 / (1 + distance * distance / 260));
        if (now != born) strength = power;
        else strength = Math.max(strength, power);
        born = now;
        // One sweep per tick even when 解 hits a row of creatures.
        if (now - lastSound >= 2 || lastSound == Long.MIN_VALUE) {
            lastSound = now;
            Vec3 delta = audiblePoint.subtract(listener);
            Vec3 source = listener.add(delta.scale(Math.min(1, 12 / Math.max(.001, delta.length()))));
            play(mc, CUT, source, .7f * power, heavy ? .88f : 1.14f);
            if (packet.impact()) play(mc, IMPACT, source, .58f * power, heavy ? .84f : 1.1f);
        }
    }

    private static void emit(Vec3 center, Vec3 forward, Vec3 up, float length, float lifetime,
                             long now, float seed, boolean heavy) {
        Vec3 normal = forward.scale(-1), right = up.cross(normal).normalize();
        ClientEvents.addSlash(new Rift(center, right, up, normal, length, lifetime,
                now, seed, Vec3.ZERO, true, false));
        // Both views describe this same plane. This remains readable beside a large physical body.
        if (heavy) ClientEvents.addSlash(new Rift(center, forward, up, up.cross(forward).normalize(),
                length, lifetime, now, seed, Vec3.ZERO, true, false));
    }

    private static Vec3 closestPoint(Vec3 listener, Vec3 center, Vec3 forward, Vec3 up, double radius) {
        Vec3 offset = listener.subtract(center);
        Vec3 onPlane = forward.scale(offset.dot(forward)).add(up.scale(offset.dot(up)));
        if (onPlane.lengthSqr() > radius * radius) onPlane = onPlane.normalize().scale(radius);
        return center.add(onPlane);
    }

    static void camera(ViewportEvent.ComputeCameraAngles event, long now, float partial, float scale) {
        if (born == Long.MIN_VALUE || now < born) return;
        float age = (now - born + partial) / 20f;
        if (age > .35f) return;
        float weight = strength * (1 - age / .35f) * (1 - age / .35f) * scale;
        event.setPitch(event.getPitch() + (float) Math.cos(age * 65) * weight * .75f);
        event.setYaw(event.getYaw() + (float) Math.sin(age * 53) * weight * .30f);
        event.setRoll(event.getRoll() + (float) Math.sin(age * 72) * weight * .62f);
    }
    static void clear() { born = lastSound = Long.MIN_VALUE; strength = 0; }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static SoundEvent sound(String path) { return SoundEvent.createVariableRangeEvent(new ResourceLocation(JujutsuVfx.ID, path)); }
    private static void play(Minecraft mc, SoundEvent sound, Vec3 pos, float volume, float pitch) {
        mc.level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
