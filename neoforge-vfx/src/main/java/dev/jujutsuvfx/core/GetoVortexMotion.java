package dev.jujutsuvfx.core;

/** Shared server/client timing: collect, compress, discharge, travel, dissipate. */
public final class GetoVortexMotion {
 public static final int COLLECT=28, COMPRESS=58, FIRE=70, END=112;
 public static final double RANGE=48, SPEED=5, RADIUS=1.6;
 private GetoVortexMotion(){}
 public static float phase(float t,float a,float b){float x=Math.max(0,Math.min(1,(t-a)/(b-a)));return x*x*(3-2*x);}
 public static double reach(float age,double limit){return Math.max(0,Math.min(Math.max(0,Math.min(RANGE,limit)),(age-FIRE)*SPEED));}
 public static float fade(float age){return phase(age,0,12)*(1-phase(age,FIRE+24,END));}
 public static float radius(float age){return (.35f+2.4f*phase(age,0,COLLECT))*(1-.72f*phase(age,COLLECT,COMPRESS));}
 public static float brace(float age){return phase(age,0,14)*(1-phase(age,END-12,END));}
 public static float recoil(float age){return phase(age,FIRE-2,FIRE+2)*(1-phase(age,FIRE+2,FIRE+15));}
 public static boolean beamActive(float age){return age>FIRE&&age<FIRE+24;}
 public static boolean reaches(double along,double perpendicular,double entityRadius,float age,double limit){
  return beamActive(age)&&along>=0&&along<=reach(age,limit)&&perpendicular<=RADIUS+Math.max(0,entityRadius);
 }
}
