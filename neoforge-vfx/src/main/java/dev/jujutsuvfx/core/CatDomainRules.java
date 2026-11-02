package dev.jujutsuvfx.core;

/** Shared own-health milestones outside the exact Gojo/Sukuna screenplay. */
public final class CatDomainRules {
 public static final int NONE=0,GOJO=1,SUKUNA=2,MAHORAGA=3,OTHER=4;
 private CatDomainRules(){}
 public static int milestone(float health,float max,int used){
  if(!Float.isFinite(health+max)||health<=0||max<=0)return 0;
  if(health<=max*.10f&&(used&2)==0)return 2;
  return health<=max*.50f&&(used&1)==0?1:0;
 }
 public static int consume(int used,int milestone){return used|(milestone==2?3:milestone);}
 public static boolean fullyRecovered(float health,float max){return Float.isFinite(health+max)&&max>0&&health>=max*.98f;}
 public static boolean scriptOpponent(int caster,int target,boolean scriptedGuest){
  if(target==NONE)return true;
  return caster==GOJO&&(target==SUKUNA||target==MAHORAGA&&scriptedGuest)||caster==SUKUNA&&target==GOJO;
 }
}
