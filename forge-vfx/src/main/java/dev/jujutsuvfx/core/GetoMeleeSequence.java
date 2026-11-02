package dev.jujutsuvfx.core;

/** A committed staff exchange; spells and defensive interruptions own a separate decision. */
public final class GetoMeleeSequence {
 private static final int[][] COMBOS={
  {GetoCloudMotion.LEFT,GetoCloudMotion.RIGHT,GetoCloudMotion.RISING},
  {GetoCloudMotion.THRUST,GetoCloudMotion.LEFT,GetoCloudMotion.DOWN},
  {GetoCloudMotion.LOW,GetoCloudMotion.RISING,GetoCloudMotion.SPIN},
  {GetoCloudMotion.RIGHT,GetoCloudMotion.THRUST,GetoCloudMotion.LOW,GetoCloudMotion.DOWN}
 };
 private int variant,index=-1;
 public void begin(int choice){variant=Math.floorMod(choice,COMBOS.length);index=0;}
 public boolean active(){return index>=0;}
 public int action(){return active()?COMBOS[variant][index]:0;}
 public boolean advance(){if(!active())return false;if(++index>=COMBOS[variant].length)cancel();return active();}
 public boolean finisher(){return active()&&index==COMBOS[variant].length-1;}
 public void cancel(){index=-1;}
 public static boolean canDisengage(int completedCombos,double distance,double height){
  return completedCombos>=2||distance>=10||height>3;
 }
}
