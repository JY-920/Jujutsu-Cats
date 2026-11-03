package dev.jujutsuvfx.core;

/** A frozen pose stops movement, never damage cooldown or hit feedback. Independent of render rate. */
public final class StasisDamageClock {
 private long tick;
 public StasisDamageClock(long tick){this.tick=tick;}
 public record Timers(int invulnerability,int hurt) {}
 public Timers advance(long now,int invulnerability,int hurt){
  int elapsed=(int)Math.min(100000,Math.max(0,now-tick));tick=now;
  return new Timers(Math.max(0,invulnerability-elapsed),Math.max(0,hurt-elapsed));
 }
}
