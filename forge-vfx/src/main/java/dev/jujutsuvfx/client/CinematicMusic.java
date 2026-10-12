package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.core.MusicEnvelope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** A single streaming, non-positional soundtrack for the local viewer, not one sound per cat/shot. */
final class CinematicMusic {
    private static final SoundEvent TRACK=SoundEvent.createVariableRangeEvent(
            new ResourceLocation(JujutsuVfx.ID,"cinematic_rain_love"));
    private static ClientLevel world;
    private static Voice voice;
    private static int clock,retryAt;
    private CinematicMusic(){}
    static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(world!=mc.level){clear();world=mc.level;}
        if(world==null||mc.player==null||mc.isPaused())return;
        clock++;
        boolean wanted=VfxConfig.music()&&VfxConfig.musicVolume()>0&&VfxConfigScreen.cinematic()
                &&mc.player.isAlive()&&DuelScenes.filming()!=null;
        boolean audible=mc.options.getSoundSourceVolume(SoundSource.MASTER)>0
                &&mc.options.getSoundSourceVolume(SoundSource.MUSIC)>0;
        var manager=mc.getSoundManager();
        if(voice!=null&&clock-voice.created>60&&!manager.isActive(voice)){
            voice.cancel();voice=null;retryAt=clock+40; // Resource reload/device changes cannot create a retry storm.
        }
        if(voice==null&&wanted&&audible&&clock>=retryAt){
            voice=new Voice(clock);voice.advance(true,VfxConfig.musicVolume()/100f);manager.play(voice);
        }
        if(voice==null)return;
        voice.advance(wanted&&audible,VfxConfig.musicVolume()/100f);
        if((!wanted||!audible)&&voice.envelope.silent()){voice.cancel();voice=null;return;}
        // MusicManager owns only vanilla background music; technique/impact sounds remain audible.
        if(audible&&clock%20==0)mc.getMusicManager().stopPlaying();
    }
    static void clear(){
        if(voice!=null)voice.cancel();voice=null;world=null;clock=retryAt=0;
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        final MusicEnvelope envelope=new MusicEnvelope();
        final int created;
        Voice(int created){
            super(TRACK,SoundSource.MUSIC,SoundInstance.createUnseededRandom());this.created=created;
            relative=true;attenuation=SoundInstance.Attenuation.NONE;looping=true;delay=0;pitch=1;volume=0;
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){} // Advanced by the client tick, including fade-out after the camera returns.
        void advance(boolean wanted,float gain){volume=envelope.tick(wanted,gain);}
        void cancel(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
    }
}
