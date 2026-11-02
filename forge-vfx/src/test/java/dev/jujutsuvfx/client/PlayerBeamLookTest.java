package dev.jujutsuvfx.client;

/** Exercise the actual volume uniforms: beam intensity encodes its live endpoint. */
public final class PlayerBeamLookTest {
 public static void main(String[] args){
  for(int kind=0;kind<2;kind++)for(float age:new float[]{90,112,600,20000}){
   var look=YutaGetoClashEffects.held(kind,age,48);
   float beam=kind==1?look.rikaBeam():look.getoBeam();
   if(beam!=1||!look.visible())throw new AssertionError("Held discharge retracts or fades at "+age);
   if(Math.abs(look.time()-age/20)>.001)throw new AssertionError("Shader noise clock freezes");
  }
  for(float age=0;age<40;age+=.25f){
   var look=YutaGetoClashEffects.playerClash(age);
   if(look.rikaBeam()!=look.getoBeam())throw new AssertionError("Standalone clash has a scripted winner");
   if(look.rikaBeam()!=0&&look.rikaBeam()!=1)throw new AssertionError("Beam retreats instead of ending at collision");
   if(age<12&&look.rikaBeam()!=1)throw new AssertionError("Clash loses either full-length beam before impact");
   if(age>13&&look.burst()<=0)throw new AssertionError("No collision explosion");
  }
  System.out.println("Player volume uniforms: sustained full length, animated material, symmetric fixed contact and explosion passed");
 }
}
