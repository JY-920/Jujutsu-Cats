package dev.jujutsuvfx.core;

/** One effect's monotonic clock. Changing speed never changes the world's tick rate or replays a frame. */
public final class ScopedEffectTimeline {
    private double age,lastClock;
    public ScopedEffectTimeline(double age,double clock){this.age=Math.max(0,age);lastClock=clock;}
    public double advance(double clock,double rate){
        if(!Double.isFinite(clock)||!Double.isFinite(rate))return age;
        double elapsed=Math.max(0,clock-lastClock);
        age+=elapsed*Math.max(0,Math.min(1,rate));lastClock=Math.max(lastClock,clock);
        return age;
    }
    public void synchronize(double serverAge,double clock,double rate){
        advance(clock,rate);
        if(Double.isFinite(serverAge))age=Math.max(age,serverAge);
    }
}
