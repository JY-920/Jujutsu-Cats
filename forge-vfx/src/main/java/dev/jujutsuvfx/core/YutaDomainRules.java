package dev.jujutsuvfx.core;

/** Authored ticks, health milestones and deterministic battlefield distribution. */
public final class YutaDomainRules {
 public static final int WINDUP=50,POSE_TICKS=70,REVEAL_TICKS=45,PICK_TICKS=18,PICK_CONTACT=9,CLASH_TICKS=100;
 public static final float RADIUS=40;
 private YutaDomainRules(){}
 public static boolean playerChargeComplete(int ticks){return ticks>=WINDUP;}
 public static float damageMultiplier(boolean opened,boolean casterInside){return opened&&casterInside?1.5f:1f;}
 public static int milestone(float health,float max,int used){
  return CatDomainRules.milestone(health,max,used);
 }
 public static int consume(int used,int milestone){return CatDomainRules.consume(used,milestone);}
}
