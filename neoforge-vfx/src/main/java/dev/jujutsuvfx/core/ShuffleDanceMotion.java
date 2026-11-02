package dev.jujutsuvfx.core;
/** Reference estimate: one alternating left/right phrase in about 0.75 seconds. */
public final class ShuffleDanceMotion {
 public static final float STEP_CYCLE=15, LOOP=75, LATERAL_TRAVEL=0, LEAN=.075f, HOP_HEIGHT=.18f;
 private ShuffleDanceMotion(){}
 public static float[] sample(float age){
  float t=((age-10)%LOOP+LOOP)%LOOP;
  double beat=t*Math.PI*2/STEP_CYCLE;
  float step=(float)Math.sin(beat);
  // Feet exchange during the airborne crest; the raised knee reaches its peak on landing.
  float bob=(float)(.5+.5*Math.cos(beat*2))*HOP_HEIGHT;
  float spread=smooth((t-20)/10)*(1-smooth((t-61)/14));
  return new float[]{bob,step,step,(float)Math.sin(beat*2+.5),spread};
 }
 public static float lift(float step,float side){float v=Math.max(0,step*side);return v*v;}
 public static float smooth(float v){v=Math.max(0,Math.min(1,v));return v*v*(3-2*v);}
 public static float weight(float age){return smooth(age/10);}
}
