package dev.jujutsuvfx.core;

/** Ring preparation and residence are independent of individual assist attack cooldowns. */
public final class RikaResidentClock {
 public static final int PRELUDE=36,DURATION=400,COOLDOWN=800;
 public static final int NONE=0,PREPARING=1,RESIDENT=2,TEMPORARY=3;
 private int phase,prelude,remaining,cooldown;
 public boolean begin(){
  if(phase!=NONE||cooldown>0)return false;
  phase=PREPARING;prelude=0;cooldown=COOLDOWN;return true;
 }
 public boolean manifest(){
  if(!preparing()||prelude<PRELUDE)return false;
  phase=RESIDENT;remaining=DURATION;return true;
 }
 public void tick(boolean valid){
  if(cooldown>0)cooldown--;
  if(!valid){cancel();return;}
  if(preparing()&&++prelude>PRELUDE+4)cancel();
  else if(active()&&--remaining<=0)cancel();
 }
 public void cancel(){phase=NONE;prelude=0;remaining=0;}
 /** An authored ring beat has its own once-per-pair gate, independent of incidental assist cooldowns. */
 public void scriptedBegin(){phase=PREPARING;prelude=0;remaining=0;cooldown=COOLDOWN;}
 public void scriptedResident(){phase=RESIDENT;prelude=PRELUDE;remaining=Math.max(remaining,DURATION);}
 public void sustain(){if(active())remaining=Math.max(remaining,40);}
 public boolean active(){return phase==RESIDENT;}
 public boolean preparing(){return phase==PREPARING;}
 public int remaining(){return remaining;}
}
