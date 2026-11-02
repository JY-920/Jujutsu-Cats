package dev.jujutsuvfx.core;

/** Authoritative one-way screenplay. Ordinary fights retain their own AI inside the four combat beats. */
public final class YutaGetoBeat {
 public static final int NONE=0,WAIT=1,MELEE=2,DRAW=3,SWARM=4,RING=5,COOP=6,RANGED=7,
   SEPARATE=8,VOW=9,CHARGE=10,CLASH=11,BREAKTHROUGH=12,AFTERMATH=13,RETREAT=14,DONE=15;
 private YutaGetoBeat(){}
 public static boolean combat(int phase){return phase==MELEE||phase==SWARM||phase==COOP||phase==RANGED;}
 public static boolean finale(int phase){return phase>=SEPARATE&&phase<DONE;}
 public static int duration(int phase){return switch(phase){
  case WAIT->40;case MELEE->40;case DRAW->58;case SWARM->200;case RING->36;case COOP->240;
  case RANGED->200;case SEPARATE->32;case VOW->70;case CHARGE->110;case CLASH->90;
  case BREAKTHROUGH->40;case AFTERMATH->65;case RETREAT->100;default->0;
 };}
 public static float threshold(int phase){return switch(phase){case MELEE->0f;case SWARM->.40f;case COOP->.65f;case RANGED->.85f;default->0;};}
 /** One rapid backstep, with a short plant before charge; only Geto consumes this displacement. */
 public static double separationStep(int age,double distance){
  if(age<0||age>=28||distance>=26)return 0;
  double enter=Math.min(1,(age+1)/4.0),exit=Math.min(1,(28-age)/6.0);
  return Math.min(Math.max(0,26-distance),1.05*enter*exit);
 }
 public static int next(int phase,int age,float loss,boolean terminal){
  if(phase<=NONE||phase>=DONE||age<duration(phase))return phase;
  // Bounded stagnation recovery also handles unusual mods that constantly heal or fully negate damage.
  if(combat(phase)&&!terminal&&DuelProgress.bounded(loss)<threshold(phase)&&age<duration(phase)+600)return phase;
  return phase+1;
 }
 public static int resume(int saved){
  if(saved<=WAIT)return MELEE;
  if(saved==DRAW)return SWARM;
  if(saved==RING)return COOP;
  if(saved>=SEPARATE&&saved<AFTERMATH)return saved;
  if(saved>=AFTERMATH)return DONE;
  return Math.min(RANGED,saved);
 }
 public static int resumeAge(int saved,int age){
  if(resume(saved)!=saved)return 0;
  return Math.max(0,Math.min(age,duration(saved)+(combat(saved)?600:0)));
 }
 public static float progress(float old,float yh,float ym,float gh,float gm){return DuelProgress.observe(old,yh,ym,gh,gm);}
 public static double clashFraction(float age){
  double t=Math.max(0,Math.min(1,age/duration(CLASH)));
  return .5+.05*Math.sin(t*Math.PI*4)*(1-t)+.17*t*t*t;
 }
 public static double breakthroughFraction(float age){
  double t=Math.max(0,Math.min(1,age/duration(BREAKTHROUGH)));
  return .67+.33*t*t*(3-2*t);
 }
}
