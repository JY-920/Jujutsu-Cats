package dev.jujutsuvfx.core;

/** Preview state never executes the gameplay action associated with its pose. */
public final class GetoDebugPose {
 public static final int AUTO=0,IDLE=1,GUARD=2,SUMMON=3,CHARGE=4,FIRE=5,COUNT=6;
 private GetoDebugPose(){}
 public static boolean valid(int pose){return pose>=AUTO&&pose<COUNT;}
 public static int action(int pose){return switch(pose){case GUARD->YutaBladeMotion.GUARD;case SUMMON->100;case CHARGE,FIRE->101;default->0;};}
 public static float age(int pose,float elapsed){
  float t=Math.max(0,elapsed);
  return switch(pose){case GUARD->Math.min(t,8);case SUMMON->Math.min(t,14);case CHARGE->Math.min(t,GetoVortexMotion.FIRE-8);case FIRE->GetoVortexMotion.FIRE+Math.min(t,12);default->0;};
 }
}
