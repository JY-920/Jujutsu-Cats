package dev.jujutsuvfx.core;

/** Motion may stop, but repeated damage and its visible feedback must recover in real ticks. */
public final class StasisDamageClockTest {
 public static void main(String[] args){
  var clock=new StasisDamageClock(100);
  var timers=clock.advance(101,20,10);
  check(timers.invulnerability()==19&&timers.hurt()==9,"first frozen tick advances both timers");
  for(int frame=0;frame<144;frame++)timers=clock.advance(101,timers.invulnerability(),timers.hurt());
  check(timers.invulnerability()==19&&timers.hurt()==9,"render calls cannot consume combat ticks");
  timers=clock.advance(112,timers.invulnerability(),timers.hurt());
  check(timers.invulnerability()==8&&timers.hurt()==0,"stasis expires hurt tint and permits another normal hit");
  timers=clock.advance(113,20,10);
  check(timers.invulnerability()==19&&timers.hurt()==9,"new hit gets its own full feedback window");
  timers=clock.advance(160,timers.invulnerability(),timers.hurt());
  check(timers.invulnerability()==0&&timers.hurt()==0,"long freeze never makes timers negative");
  timers=clock.advance(150,20,10);
  check(timers.invulnerability()==20&&timers.hurt()==10,"clock rollback cannot create damage immunity or remove a hit");
  System.out.println("Stasis combat timers: repeat hits, render independence, feedback recovery passed");
 }
 private static void check(boolean result,String why){if(!result)throw new AssertionError(why);}
}
