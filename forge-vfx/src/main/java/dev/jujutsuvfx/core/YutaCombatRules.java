package dev.jujutsuvfx.core;

/** Pure rules shared by both server loaders. */
public final class YutaCombatRules {
 private YutaCombatRules(){}
 public static float amplification(boolean player,boolean buff,boolean technique){return player&&buff&&technique?1.25f:1f;}
 public static float guardMultiplier(boolean guarding,boolean frontal,boolean bypass){return guarding&&frontal&&!bypass?.20f:1f;}
 public static float bladeFlame(int action,float age){
  if(action==YutaBladeMotion.DRAW)return YutaBladeMotion.phase(age,32,37)*(1-YutaBladeMotion.phase(age,46,52));
  if(YutaAreaMotion.active(action))return YutaBladeMotion.phase(age,0,4)*(1-YutaBladeMotion.phase(age,YutaBladeMotion.duration(action)-5,YutaBladeMotion.duration(action)));
  if(action<YutaBladeMotion.CUT_LEFT||action>YutaBladeMotion.RISING)return 0;
  return YutaBladeMotion.phase(age,0,3)*(1-YutaBladeMotion.phase(age,YutaBladeMotion.duration(action)-6,YutaBladeMotion.duration(action)));
 }
}
