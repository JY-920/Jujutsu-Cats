package dev.jujutsuvfx.core;
public final class CursedSpeechTest {
 public static void main(String[] args){
  check(CursedSpeechShape.inCone(8,0,0,1,0,0),"forward enemy");
  check(!CursedSpeechShape.inCone(-2,0,0,1,0,0),"behind excluded");
  check(!CursedSpeechShape.inCone(2,0,5,1,0,0),"side excluded");
  check(!CursedSpeechShape.inCone(13,0,0,1,0,0),"range bounded");
  check(!CursedSpeechShape.inCone(Double.NaN,0,0,1,0,0),"invalid coordinates rejected");
  check(CursedSpeechShape.stunTicks(true)==60&&CursedSpeechShape.stunTicks(false)==60,"players and mobs held for three seconds");
  check(CursedSpeechShape.RECOVERY+10+5<CursedSpeechShape.stunTicks(false),"mob stun allows recovery/chase/sword followup");
  check(YutaBladeMotion.rate(YutaBladeMotion.SPEECH)==1,"speech is not accelerated melee");
  for(int i=0;i<3;i++){
   double last=-1;for(float t=0;t<16;t+=.05f){double d=CursedSpeechShape.waveDistance(t,i);check(d>=last&&Double.isFinite(d),"wave moves outward");last=d;}
  }
  System.out.println("Cursed speech: cone/range, player control duration, combo window, wave progression passed");
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
