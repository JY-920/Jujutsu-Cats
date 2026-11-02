package dev.jujutsuvfx.core;

/** Shared world-space projectile volume and the short vortex's remapped animation clock. */
public final class RangedCatRules {
 private RangedCatRules(){}
 public static float miniAge(float age){
  return age<=18?Math.max(0,age)*GetoVortexMotion.FIRE/18f:GetoVortexMotion.FIRE+age-18;
 }
 public static boolean segment(double x,double y,double z,double dx,double dy,double dz,double length,double radius){
  if(!Double.isFinite(x+y+z+dx+dy+dz+length+radius)||length<0||radius<0)return false;
  double square=dx*dx+dy*dy+dz*dz;if(square<.0001)return false;
  double t=Math.max(0,Math.min(length,(x*dx+y*dy+z*dz)/Math.sqrt(square)));
  double inverse=1/Math.sqrt(square);
  x-=dx*inverse*t;y-=dy*inverse*t;z-=dz*inverse*t;
  return x*x+y*y+z*z<=radius*radius;
 }
}
