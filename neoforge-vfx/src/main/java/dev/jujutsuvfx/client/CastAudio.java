package dev.jujutsuvfx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Stop only this cast's voices, never every instance of a shared sound event. */
final class CastAudio {
    private final List<SoundInstance> voices=new ArrayList<>();
    private static final class Voice extends AbstractTickableSoundInstance {
        private final Vec3 point;
        private final net.minecraft.client.multiplayer.ClientLevel world;
        private int age;
        Voice(SoundEvent event,Vec3 point,float volume,float pitch){
            super(event,SoundSource.PLAYERS,SoundInstance.createUnseededRandom());
            this.point=point;this.volume=volume;this.pitch=pitch;world=Minecraft.getInstance().level;
            position();
        }
        private void position(){
            Minecraft mc=Minecraft.getInstance();
            Vec3 listener=CatCinematicCamera.audioPosition(mc.gameRenderer.getMainCamera().getPosition()),delta=point.subtract(listener);
            Vec3 source=listener.add(delta.scale(Math.min(1,8/Math.max(delta.length(),.001))));
            x=source.x;y=source.y;z=source.z;
        }
        @Override public void tick(){
            Minecraft mc=Minecraft.getInstance();
            if(mc.level!=world||++age>20&&!mc.getSoundManager().isActive(this)){stop();return;}
            position();
        }
    }
    void play(SoundEvent event,Vec3 point,float volume,float pitch){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        var manager=mc.getSoundManager();voices.removeIf(voice->!manager.isActive(voice));
        var voice=new Voice(event,point,volume,pitch);
        voices.add(voice);manager.play(voice);
    }
    void stop(SoundEvent event){
        var manager=Minecraft.getInstance().getSoundManager();
        voices.removeIf(voice->{if(!voice.getLocation().equals(event.getLocation()))return false;manager.stop(voice);return true;});
    }
    void stop(){var manager=Minecraft.getInstance().getSoundManager();voices.forEach(manager::stop);voices.clear();}
}
