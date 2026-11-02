package dev.jujutsuvfx.core;

/** One server-owned contact per complete swing; recovery is the only attack delay. */
public final class RikaSupportClock {
 public static final int NONE=0,PUNCH=1,LAUNCH=2,SLAM=3,GUARD=4,WAVE=5,HOOK=6,CLAW=7,OVERHEAD=8,DURATION=36;
 private static final int[] MELEE={PUNCH,HOOK,CLAW,LAUNCH,OVERHEAD,SLAM};
 private int action,age;
 public boolean begin(int next){
  if(next<PUNCH||next>OVERHEAD||active())return false;
  action=next;age=0;return true;
 }
 public boolean tick(boolean valid){
  if(!valid){cancel();return false;}
  if(!active())return false;
  age++;boolean hit=age==contact(action);
  if(age>=duration(action))cancel();
  return hit;
 }
 public void cancel(){action=NONE;age=0;}
 public boolean active(){return action!=NONE;}
 public int action(){return action;}
 public int age(){return age;}
 public boolean ready(){return !active();}
 public boolean guarding(){return action==GUARD&&age>=3&&age<=15;}
 public static int duration(int action){return switch(action){case PUNCH,CLAW->22;case HOOK->26;case LAUNCH->28;case OVERHEAD->32;default->DURATION;};}
 public static int contact(int action){return switch(action){case PUNCH,CLAW->9;case HOOK->11;case GUARD,LAUNCH->12;default->14;};}
 public static int melee(int sequence){return MELEE[Math.floorMod(sequence,MELEE.length)];}
 public static String clip(int action){return switch(action){case PUNCH->"punch";case LAUNCH->"launch";case SLAM->"slam";case GUARD->"guard";case WAVE->"wave";case HOOK->"hook";case CLAW->"claw";case OVERHEAD->"overhead";default->"idle";};}
 /** The visible paws occupy a compact frontal volume, not a target-centred damage sphere. */
 public static boolean inReach(int action,double forward,double side,double height,double radius){
  double width=action==CLAW?1.65:action==HOOK?1.35:.95;
  double reach=action==LAUNCH?2.7:2.95;
  return action!=NONE&&action!=WAVE&&forward>=-.15-radius&&forward<=reach+radius
      &&Math.abs(side)<=width+radius&&height>=-.5&&height<=2.8;
 }
 public static float damage(int action){return action==WAVE?32:action==GUARD?8:action==PUNCH?22:action==LAUNCH?16:20;}
 public static float phase(float age,float start,float end){float t=Math.max(0,Math.min(1,(age-start)/(end-start)));return t*t*(3-2*t);}
 public static float opacity(float age){return phase(age,0,5)*(1-phase(age,26,DURATION));}
 public static float advance(float age){return -.45f*(1-phase(age,0,8))+.7f*phase(age,11,14)*(1-phase(age,18,32));}
}
