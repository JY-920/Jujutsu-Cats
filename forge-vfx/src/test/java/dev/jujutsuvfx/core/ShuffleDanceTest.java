package dev.jujutsuvfx.core;
public final class ShuffleDanceTest {
 public static void main(String[] args){
  if(ShuffleDanceMotion.LATERAL_TRAVEL!=0)throw new AssertionError("Reference stays in place");
  if(ShuffleDanceMotion.LEAN<.04||ShuffleDanceMotion.LEAN>.10)throw new AssertionError("Body lean must be visible but restrained");
  if(ShuffleDanceMotion.sample(10)[0]<.16f||ShuffleDanceMotion.sample(13.75f)[0]>.01f)throw new AssertionError("Hop must peak during foot exchange, not single-leg landing");
  for(float t=10;t<310;t+=.125f){
   float[] a=ShuffleDanceMotion.sample(t),b=ShuffleDanceMotion.sample(t+ShuffleDanceMotion.LOOP),step=ShuffleDanceMotion.sample(t+15);
   for(int i=0;i<a.length;i++)if(!Float.isFinite(a[i])||Math.abs(a[i]-b[i])>.0002f)throw new AssertionError("Loop seam at "+t);
   for(int i=0;i<4;i++)if(Math.abs(a[i]-step[i])>.0002f)throw new AssertionError("Reference phrase cadence at "+t);
   float[] next=ShuffleDanceMotion.sample(t+.01f);
   for(int i=0;i<a.length;i++)if(Math.abs(a[i]-next[i])>.03f)throw new AssertionError("Frame snapping at "+t);
   float left=ShuffleDanceMotion.lift(a[1],1),right=ShuffleDanceMotion.lift(a[1],-1);
   if(left*right!=0||left<0||right<0||a[0]<0||a[0]>.201f||a[4]<0||a[4]>1)throw new AssertionError("Invalid support/raised-foot phase");
  }
  if(ShuffleDanceMotion.lift(ShuffleDanceMotion.sample(13.75f)[1],1)<.99f||ShuffleDanceMotion.lift(ShuffleDanceMotion.sample(21.25f)[1],-1)<.99f)throw new AssertionError("Alternating raised knees missing");
  System.out.println("Shuffle: reference cadence, stationary hips, alternating smooth knee lifts and continuous loop passed");
 }
}
