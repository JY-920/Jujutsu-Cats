package dev.jujutsuvfx.core;

/** One real-time client envelope; scene transitions never restart the song. */
public final class MusicEnvelope {
    private float fade,mix=.5f;
    public float tick(boolean playing,float volume) {
        volume=Float.isFinite(volume)?Math.max(0,Math.min(1,volume)):0;
        if(playing){fade=1;mix=volume;}
        else {fade=approach(fade,0,1f/60);mix=approach(mix,volume,1f/20);}
        return fade*fade*(3-2*fade)*mix;
    }
    public boolean silent(){return fade<=0;}
    private static float approach(float value,float target,float step) {
        return value<target?Math.min(target,value+step):Math.max(target,value-step);
    }
}
