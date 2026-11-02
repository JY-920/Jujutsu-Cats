package dev.jujutsuvfx.core;

/** Reference footage: 88 frames at 30 fps; fixed right-foot support with continuous, synchronized acceleration pulses. */
public final class YutaDanceMotion {
 public static final int DURATION=200;
 public static final float LOOP=88f/30*20;
 private YutaDanceMotion(){}
 public static float weight(float age){return YutaBladeMotion.phase(age,0,10)*(1-YutaBladeMotion.phase(age,190,200));}
 public static float progress(float age){float t=Math.max(0,age-10)/LOOP;float cycle=t-(float)Math.floor(t);return cycle;}
 public static float step(float age){return (float)Math.sin(progress(age)*Math.PI*8);}
 /** Fast push followed by a longer settling arc; never quantize the animation clock. */
 public static float pulse(float cycle){float u=cycle-(float)Math.floor(cycle);return u<.20f?YutaKatanaMotion.smooth(u/.20f):1-YutaKatanaMotion.smooth((u-.20f)/.80f);}
 public static float bounce(float age){return pulse(progress(age)*8);}
 public static float open(float age){return pulse(progress(age)*4);}
 public static boolean safe(boolean target,boolean grounded,boolean wet,boolean riding,boolean hurt,boolean domain){
  return !target&&grounded&&!wet&&!riding&&!hurt&&!domain;
 }
}
