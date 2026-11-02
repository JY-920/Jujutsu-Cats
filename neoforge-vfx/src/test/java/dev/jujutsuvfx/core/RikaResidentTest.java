package dev.jujutsuvfx.core;

/** Lifecycle regressions: a resident must outlive each attack and retire without refunding cooldown. */
public final class RikaResidentTest {
 public static void main(String[] args)throws Exception {
  Class<?> type;
  try{type=Class.forName("dev.jujutsuvfx.core.RikaResidentClock");}
  catch(ClassNotFoundException missing){throw new AssertionError("Resident support lifecycle is missing",missing);}
  var begin=type.getMethod("begin");var tick=type.getMethod("tick",boolean.class);
  var manifest=type.getMethod("manifest");var active=type.getMethod("active");
  var preparing=type.getMethod("preparing");var cancel=type.getMethod("cancel");
  Object resident=type.getConstructor().newInstance();
  check((boolean)begin.invoke(resident),"eligible first ring begins");
  for(int i=0;i<35;i++){
   check(!(boolean)manifest.invoke(resident),"resident cannot skip the standing ring prelude");
   tick.invoke(resident,true);
  }
  check(!(boolean)manifest.invoke(resident),"35 ticks is still prelude");
  tick.invoke(resident,true);
  check((boolean)manifest.invoke(resident),"ring completes at 36 ticks");
  check(!(boolean)preparing.invoke(resident)&&(boolean)active.invoke(resident),"ring transitions to residence");
  RikaSupportClock assist=new RikaSupportClock();int contacts=0;
  for(int i=0;i<400;i++){
   check((boolean)active.invoke(resident),"resident persists through all 400 service ticks");
   check(!(boolean)begin.invoke(resident),"residence cannot retrigger its ring");
   if(assist.ready())assist.begin(RikaSupportClock.PUNCH);
   if(assist.tick(true))contacts++;
   tick.invoke(resident,true);
  }
  check(contacts==18,"resident jabs follow complete 22-tick swings without extra cooldown or duplicate contacts");
  check(!(boolean)active.invoke(resident),"resident expires after exactly 20 seconds");
  for(int i=0;i<364;i++){
   check(!(boolean)begin.invoke(resident),"activation cooldown survives residence expiry");
   tick.invoke(resident,true);
  }
  check((boolean)begin.invoke(resident),"40 seconds after activation another ring is allowed");
  tick.invoke(resident,false);
  check(!(boolean)preparing.invoke(resident)&&!(boolean)active.invoke(resident),"invalid owner cancels lifecycle");
  cancel.invoke(resident);check(!(boolean)begin.invoke(resident),"cancellation never refunds cooldown");
  Object interrupted=type.getConstructor().newInstance();begin.invoke(interrupted);
  for(int i=0;i<42;i++)tick.invoke(interrupted,true);
  check(!(boolean)preparing.invoke(interrupted),"unconsumed prelude expires rather than locking Yuta forever");
  System.out.println("Rika resident: 36-tick prelude, 400-tick residence, bounded contacts, 800-tick cooldown, cancellation passed");
 }
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
