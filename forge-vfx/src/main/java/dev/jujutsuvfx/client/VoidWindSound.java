package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** One listener-relative air loop per cast; the domain clock also controls its fade-out. */
final class VoidWindSound extends AbstractTickableSoundInstance {
    private static final SoundEvent AIR = SoundEvent.createVariableRangeEvent(
            new ResourceLocation(JujutsuVfx.ID, "void_air_friction"));
    private final UnlimitedVoid domain;
    private final ClientLevel owner;

    VoidWindSound(UnlimitedVoid domain, ClientLevel owner) {
        super(AIR, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.domain = domain;
        this.owner = owner;
        looping = true;
        delay = 0;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        volume = 0;
        pitch = 1;
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public boolean canPlaySound() { return !isStopped(); }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != owner || mc.player == null || !mc.player.isAlive()
                || domain.expired(ClientEffectClock.ticks())) {
            stop();
            return;
        }
        long now = ClientEffectClock.ticks();
        float age = domain.age(now, 0);
        float reveal = dev.jujutsuvfx.platform.Numbers.clamp((age - .94f) / .80f, 0, 1);
        reveal = reveal * reveal * (3 - 2 * reveal);
        float gust = VoidFeedback.gust(domain, age);
        float target = (.50f + .16f * gust) * reveal * domain.visibility(now, 0)
                * VoidFeedback.range(domain, mc.gameRenderer.getMainCamera().getPosition());
        volume += (target - volume) * .32f;
        pitch = 1.0f + .07f * gust;
    }

    void cancel() {
        stop();
        Minecraft.getInstance().getSoundManager().stop(this);
    }
}
