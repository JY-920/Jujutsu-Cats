package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.network.CatSpellState;
import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.network.FurnaceControl;
import dev.jujutsuvfx.network.FurnaceState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import dev.jujutsuvfx.platform.PacketDistributor;

/** The charge follows the hands; the released arrow and explosion stay in world space. */
final class FurnaceCast {
    private static final SoundEvent CHARGE = sound("furnace_charge"), RELEASE = sound("furnace_release"), BURST = sound("furnace_burst");
    final int session;
    final float radius;
    final boolean networked;
    private final long born;
    private long launched = Long.MAX_VALUE, impact = Long.MAX_VALUE;
    private Vec3 previous, position, direction, explosion;
    private float traveled;
    private boolean waitingImpact;
    private final CastAudio audio=new CastAudio();
    private CatSpellState remote; private long remoteReceived;

    FurnaceCast(Minecraft mc, int session, float radius) {
        this.session = session; this.radius = radius; born = ClientEffectClock.ticks();
        var connection = mc.getConnection();
        networked = connection != null && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(connection,FurnaceControl.TYPE) && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(connection,FurnaceState.TYPE);
        position = previous = FurnaceFlight.bow(mc.player, 1); direction = mc.player.getLookAngle();
        play(CHARGE, position, .65f, 1);
        if (networked) PacketDistributor.sendToServer(new FurnaceControl(FurnaceControl.CAST, session, radius));
    }
    FurnaceCast(Minecraft mc,CatSpellState packet){
        session=packet.session();radius=packet.radius();born=ClientEffectClock.ticks()-packet.age();networked=false;
        previous=position=packet.point();direction=packet.direction();remoteState(packet,true);
    }
    void remoteState(CatSpellState packet,boolean initial){
        if(remote!=null&&(packet.phase()<remote.phase()||packet.age()<remote.age()
                ||packet.phase()==remote.phase()&&packet.age()==remote.age()))return;
        int old=remote==null?-1:remote.phase();remote=packet;remoteReceived=ClientEffectClock.ticks();
        previous=position;position=packet.point();direction=packet.direction();
        if(packet.phase()==CatSpell.FLIGHT||packet.phase()==CatSpell.IMPACT&&launched==Long.MAX_VALUE)launched=Math.min(launched,remoteReceived-packet.stageAge());
        if(packet.phase()>=CatSpell.FLIGHT)audio.stop(CHARGE);
        if(packet.phase()==CatSpell.IMPACT){impact=Math.min(impact,remoteReceived-packet.stageAge());explosion=position;}
        if(old!=packet.phase()&&(!initial||packet.stageAge()<4))play(packet.phase()==CatSpell.IMPACT?BURST:packet.phase()==CatSpell.FLIGHT?RELEASE:CHARGE,
                position,.85f*range(position,96),1);
    }
    private float remoteExtra(long now,float partial){return dev.jujutsuvfx.platform.Numbers.clamp((now-remoteReceived+partial)*CatSpells.castRate(remote),0,2);}
    void receive(FurnaceState packet) {
        long now = ClientEffectClock.ticks();
        // ACCEPTED acknowledges the request; it must not rewind an already playing charge animation.
        if (packet.phase() == FurnaceState.LAUNCHED && launched == Long.MAX_VALUE)
            launch(packet.point(), packet.direction(), now);
        else if (packet.phase() == FurnaceState.IMPACT && impact == Long.MAX_VALUE)
            explode(packet.point(), now);
    }
    void tick(Minecraft mc) {
        if(remote!=null)return;
        long now = ClientEffectClock.ticks();
        if (launched == Long.MAX_VALUE) {
            if (!networked && now - born >= FurnaceFlight.CHARGE_TICKS) {
                Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
                var clearance = FurnaceFlight.trace(mc.level, mc.player, eye, FurnaceFlight.bow(mc.player, 1).add(look.scale(2.8)));
                Vec3 aim = FurnaceFlight.trace(mc.level, mc.player, eye, eye.add(look.scale(FurnaceFlight.RANGE))).point();
                Vec3 delta = aim.subtract(clearance.point());
                launch(clearance.point(), delta.lengthSqr() < .01 ? look : delta.normalize(), now);
                if (clearance.hit()) explode(clearance.point(), now);
            }
            return;
        }
        if (impact != Long.MAX_VALUE || waitingImpact) return;
        previous = position;
        var contact = FurnaceFlight.trace(mc.level, mc.player, position, position.add(direction.scale(
                Math.min(FurnaceFlight.SPEED / 20, Math.max(0, FurnaceFlight.RANGE - traveled)))));
        traveled += position.distanceTo(contact.point()); position = contact.point();
        if (contact.hit() || traveled >= FurnaceFlight.RANGE) {
            if (networked) waitingImpact = true;
            else explode(position, now);
        }
    }
    private void launch(Vec3 point, Vec3 axis, long now) {
        launched = now; previous = position = point; direction = axis.normalize();
        audio.stop(CHARGE);
        play(RELEASE, point, .95f, 1);
    }
    private void explode(Vec3 point, long now) {
        impact = now; explosion = point; position = previous = point;
        if (launched == Long.MAX_VALUE) launched = now;
        play(BURST, point, 1.25f * range(point, radius + 64), .94f);
    }
    int phase() { return impact != Long.MAX_VALUE ? 2 : launched != Long.MAX_VALUE ? 1 : 0; }
    float age(long now, float partial) { if(remote!=null)return (remote.age()+remoteExtra(now,partial))/20f;return (now - born + partial) / 20f; }
    float phaseAge(long now, float partial) {
        if(remote!=null)return (remote.stageAge()+remoteExtra(now,partial))/20f;
        return (now - (phase() == 2 ? impact : phase() == 1 ? launched : born) + partial) / 20f;
    }
    Vec3 center(Minecraft mc, float partial) {
        if(remote!=null)return phase()==2?position:previous.lerp(position,dev.jujutsuvfx.platform.Numbers.clamp((ClientEffectClock.ticks()+partial-remoteReceived)*CatSpells.castRate(remote)/2,0,1));
        return phase() == 0 ? FurnaceFlight.bow(mc.player, partial) : phase() == 2 ? explosion : previous.lerp(position, partial);
    }
    Vec3 direction(Minecraft mc, float partial) { if(remote!=null)return direction;return phase() == 0 ? mc.player.getViewVector(partial) : direction; }
    boolean expired(long now) {
        if(remote!=null)return now-remoteReceived>100;
        return impact != Long.MAX_VALUE ? phaseAge(now, 0) >= FurnaceFlight.AFTERGLOW : age(now, 0) > 12;
    }
    String status() { return (phase() == 0 ? "凝火张弓" : phase() == 1 ? "火矢离弦" : "灶火爆发") + (networked ? "（服务端控制）" : "（仅画面）"); }
    void camera(ViewportEvent.ComputeCameraAngles event, long now, float partial, float scale) {
        if(remote!=null)scale*=range(center(Minecraft.getInstance(),partial),64);
        float elapsed = phaseAge(now, partial), age = age(now, partial);
        float power;
        if (phase() == 0) power = .10f * dev.jujutsuvfx.platform.Numbers.clamp(elapsed / 1.6f, 0, 1);
        else if (phase() == 1) power = .65f * (float) Math.exp(-elapsed * 12);
        else power = (1.45f * (float) Math.exp(-elapsed * 3.3f) + .13f * (float) Math.exp(-elapsed * .85f))
                * range(explosion, radius + 32);
        event.setPitch(event.getPitch() + (float) Math.sin(age * 61) * power * scale);
        event.setYaw(event.getYaw() + (float) Math.sin(age * 47 + .8) * power * .52f * scale);
        event.setRoll(event.getRoll() + (float) Math.sin(age * 53 + 2.1) * power * .65f * scale);
    }
    void stop() {
        audio.stop();
        if(remote!=null)return;
        var mc = Minecraft.getInstance(); var connection = mc.getConnection();
        if (networked && connection != null && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(connection,FurnaceControl.TYPE))
            PacketDistributor.sendToServer(new FurnaceControl(FurnaceControl.CANCEL, session, 0));
    }
    private static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(JujutsuVfx.ID, id));
    }
    private static float range(Vec3 point, float radius) {
        return (float) dev.jujutsuvfx.platform.Numbers.clamp(1 - Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(point) / radius, 0, 1);
    }
    private void play(SoundEvent sound,Vec3 point,float volume,float pitch){audio.play(sound,point,volume,pitch);}
}
