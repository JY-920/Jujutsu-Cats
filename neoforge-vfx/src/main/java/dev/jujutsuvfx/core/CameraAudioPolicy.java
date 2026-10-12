package dev.jujutsuvfx.core;

public final class CameraAudioPolicy {
    private CameraAudioPolicy() {}
    /** Vanilla already delivers sounds inside the body radius; never duplicate those packets. */
    public static boolean needsRelay(double bodyDistanceSquared,double microphoneDistanceSquared,double range){
        if(!Double.isFinite(bodyDistanceSquared)||!Double.isFinite(microphoneDistanceSquared)||!Double.isFinite(range)||range<=0)return false;
        return bodyDistanceSquared>=range*range&&microphoneDistanceSquared<range*range;
    }
}
