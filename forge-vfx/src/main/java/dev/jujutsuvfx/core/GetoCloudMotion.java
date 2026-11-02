package dev.jujutsuvfx.core;
/** Jaw-driven staff choreography. Each complete stroke has one contact and an inertial recovery. */
public final class GetoCloudMotion {
 public static final int LEFT=200,RIGHT=201,DOWN=202,RISING=203,THRUST=204,SPIN=205,LOW=206,DURATION=30,HIT=11;
 public record Pose(float headYaw,float headPitch,float bodyYaw,float roll,float crouch,float bodyPitch,float stride){}
 public static boolean active(int action){return action>=LEFT&&action<=LOW;}
 public static int impact(int action){return action==THRUST?9:action==SPIN?14:HIT;}
 public static boolean contact(int action,int age){return active(action)&&age==impact(action);}
 /** Reach in the committed body's local frame; only the full spin includes the rear arc. */
 public static boolean inReach(int action,double forward,double side,double height,double radius){
  if(!active(action)||!Double.isFinite(forward+side+height+radius)||radius<0)return false;
  double top=action==LOW?1.65:action==RISING?3.1:2.5;
  if(height<-.5-radius||height>top+radius||Math.hypot(forward,side)>3.8+radius)return false;
  if(action==SPIN)return true;
  if(forward<-.2-radius)return false;
  return Math.abs(side)<=.8+radius+(action==THRUST?0:Math.max(0,forward)*1.4);
 }
 private static float ease(float age,float a,float b){float x=Math.max(0,Math.min(1,(age-a)/(b-a)));return x*x*(3-2*x);}
 public static Pose pose(int action,float age){
  if(!active(action))return new Pose(0,0,0,0,0,0,0);
  float hit=impact(action),wind=ease(age,0,hit-3),cast=ease(age,hit-3,hit+1),recover=ease(age,hit+5,DURATION),w=1-recover;
  float sign=action==RIGHT?-1:1,sweep=(-.67f*wind+1.6f*cast)*w*sign;
  float step=(.16f*wind-.34f*cast)*w;
  if(action==DOWN)return new Pose(.16f*wind*w,(-.5f*wind+1.0f*cast)*w,.16f*wind*w,0,.035f*wind*w,(-.07f*wind+.13f*cast)*w,step);
  if(action==RISING)return new Pose(-.12f*wind*w,(.46f*wind-1.08f*cast)*w,(-.28f*wind+.5f*cast)*w,.04f*wind*w,.055f*wind*w,(.10f*wind-.16f*cast)*w,-step);
  if(action==THRUST)return new Pose((-.16f*wind+.2f*cast)*w,.07f*wind*w,.10f*wind*w,0,.06f*wind*w,(-.14f*wind+.24f*cast)*w,step*1.5f);
  if(action==SPIN){
   float turn=ease(age,4,23),arc=(float)Math.sin(turn*Math.PI*2);
   return new Pose(-.28f*(float)Math.sin(turn*Math.PI)*w,-.08f*wind*w,(float)(Math.PI*2)*turn,.09f*arc,.035f*wind*w,.03f*arc,.26f*arc);
  }
  if(action==LOW)return new Pose(sweep*.65f,.25f*wind*w,(-.72f*wind+1.5f*cast)*w,-.10f*wind*w,.105f*wind*w,.15f*wind*w,-step);
  return new Pose(sweep,.06f*wind*w,sweep*.38f,-sweep*.07f,.035f*wind*w,0,step*sign);
 }
 public static float drive(int action,float age){float hit=impact(action);return active(action)?ease(age,hit-5,hit-2)*(1-ease(age,hit+2,hit+7)):0;}
 /** Cumulative root travel: the server takes a difference so 1.5x clocks cannot skip or double a step. */
 public static double travel(int action,float age){
  if(!active(action))return 0;
  double length=action==THRUST?1.05:action==RISING?.55:action==SPIN?.28:.38;
  return length*ease(age,impact(action)-5,impact(action)+2);
 }
}
