package dev.jujutsuvfx.core;

/** Committed crowd-clearing cuts. The same contact radii drive the visible arcs and server hits. */
public final class YutaAreaMotion {
 public static final int SWEEP=20,FAN=21,SLAM=22;
 private YutaAreaMotion(){}
 public static boolean active(int action){return action>=SWEEP&&action<=SLAM;}
 public static int duration(int action){return action==SLAM?32:action==FAN?26:28;}
 public static int contact(int action){return action==SLAM?17:action==FAN?12:13;}
 public static float radius(int action){return action==FAN?9:action==SLAM?7:6;}
 public static float damage(int action){return action==SLAM?30:action==FAN?26:24;}
 public static boolean reaches(int action,double x,double y,double z,double forwardX,double forwardZ,double padding){
  if(!active(action)||!Double.isFinite(x+y+z+forwardX+forwardZ+padding))return false;
  double horizontal=Math.sqrt(x*x+z*z),r=radius(action)+Math.max(0,Math.min(2,padding));
  if(horizontal>r||Math.abs(y)>(action==SLAM?4:3)+Math.max(0,Math.min(2,padding)))return false;
  return action!=FAN||horizontal<.1||(x*forwardX+z*forwardZ)/horizontal>=.35;
 }
 public static float visual(int action,float age){
  float t=age-contact(action);
  return YutaBladeMotion.phase(t,-2,0)*(1-YutaBladeMotion.phase(t,2,9));
 }
 public static float lift(int action,float age){
  return action==SLAM?.72f*YutaBladeMotion.phase(age,2,9)*(1-YutaBladeMotion.phase(age,12,17)):0;
 }
}
