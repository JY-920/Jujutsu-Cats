package dev.jujutsuvfx.core;
/** Shared spell timing and visible wave travel, in original game ticks. */
public final class CursedSpeechShape {
 public static final int RELEASE=8,RECOVERY=8,DURATION=RELEASE+RECOVERY,COOLDOWN=240,IMMUNITY=80,STUN_TICKS=60;
 public static final double RANGE=12,COS_HALF_ANGLE=.86;
 private CursedSpeechShape(){}
 public static int stunTicks(boolean player){return STUN_TICKS;}
 public static boolean inCone(double x,double y,double z,double fx,double fy,double fz){
  double d=x*x+y*y+z*z,n=fx*fx+fy*fy+fz*fz;
  if(!Double.isFinite(d+n)||d>RANGE*RANGE||n<.001)return false;
  return d<.04||(x*fx+y*fy+z*fz)/Math.sqrt(d*n)>=COS_HALF_ANGLE;
 }
 public static double waveDistance(float age,int layer){return Math.min(RANGE,Math.max(0,age-layer*1.3)*1.65);}
}
