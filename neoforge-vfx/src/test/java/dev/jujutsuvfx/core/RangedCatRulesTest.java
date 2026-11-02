package dev.jujutsuvfx.core;

public final class RangedCatRulesTest {
 public static void main(String[] args){
  check(!YutaBladeMotion.cinematicDraw(false,160,false),"no scripted draw outside pair");
  check(!YutaBladeMotion.cinematicDraw(true,39,false),"short opening exchange retained");
  check(YutaBladeMotion.cinematicDraw(true,40,false),"early draw even at full health");
  check(!YutaBladeMotion.cinematicDraw(true,160,true),"no repeat draw");
  check(YutaBladeMotion.shouldDraw(600,800,false),"normal health trigger retained");
  check(RangedCatRules.miniAge(18)==GetoVortexMotion.FIRE,"release clocks agree");
  check(RangedCatRules.miniAge(60)==GetoVortexMotion.END,"finite recovery");
  check(RangedCatRules.segment(0,1.5,0,0,1,0,1.6,.8),"upward pulse hits its segment");
  check(!RangedCatRules.segment(0,4,0,0,1,0,1.6,.8),"no remote hit ahead of pulse");
  check(!RangedCatRules.segment(2,1,0,0,1,0,1.6,.8),"dodging laterally works");
  check(RangedCatRules.segment(0,1.5,0,0,3,0,1.6,.8),"normalized directions");
  check(!RangedCatRules.segment(0,0,0,0,0,0,1,.8),"invalid axis rejected");
  check(!RangedCatRules.segment(Double.NaN,0,0,0,1,0,1,.8),"NaN rejected");
  var wave=new RikaSupportClock();check(wave.begin(RikaSupportClock.WAVE),"wave available");
  int contacts=0;for(int i=0;i<40;i++)if(wave.tick(true))contacts++;
  check(contacts==1&&!wave.active(),"one launch and bounded animation");
  check(wave.begin(RikaSupportClock.WAVE),"finished recovery permits the next wave without extra cooldown");
  check(!wave.begin(RikaSupportClock.WAVE),"an active wave cannot be restarted to duplicate its hit");
  System.out.println("Ranged cats: early draw boundary, short vortex clocks, 3D swept pulse and continuous non-overlapping waves passed");
 }
 private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
