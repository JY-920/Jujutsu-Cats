package dev.jujutsuvfx.core;

/** A temporary summon must never turn into an unannounced resident. */
public final class RikaAssistTest {
 public static void main(String[] args)throws Exception{
  Class<?> type;
  try{type=Class.forName("dev.jujutsuvfx.core.RikaAssistClock");}
  catch(ClassNotFoundException e){throw new AssertionError("Temporary Rika has no one-attack lifecycle",e);}
  var begin=type.getMethod("begin");var started=type.getMethod("attackStarted");
  var tick=type.getMethod("tick",boolean.class,boolean.class);
  var active=type.getMethod("active");var waiting=type.getMethod("waiting");
  var retiring=type.getMethod("retiring");var cancel=type.getMethod("cancel");
  for(int action=1;action<=8;action++){
   Object assist=type.getConstructor().newInstance();RikaSupportClock swing=new RikaSupportClock();
   check((boolean)begin.invoke(assist),"fresh temporary appearance");
   for(int i=0;i<10;i++){
    tick.invoke(assist,false,true);
    check((boolean)waiting.invoke(assist),"approaches on foot before contact");
    check(!(boolean)begin.invoke(assist),"repeated requests must not reset approach");
   }
   check(swing.begin(action),"requested action starts");started.invoke(assist);
   int hits=0;
   for(int i=0;i<50;i++){
    if(swing.tick(true))hits++;
    tick.invoke(assist,swing.active(),true);
    check(!(boolean)waiting.invoke(assist),"completed strike cannot select another action");
    check(!(boolean)begin.invoke(assist),"recovery/fade cannot be renewed by melee or ranged requests");
    if(!swing.active())break;
   }
   check(hits==1,"single assist creates exactly one contact");
   check((boolean)retiring.invoke(assist),"immediate fade after its one swing");
   for(int i=0;i<8;i++)tick.invoke(assist,false,true);
   check(!(boolean)active.invoke(assist),"no idle resident after an assist");
   check(!(boolean)begin.invoke(assist),"there is a visible gap before a new assist");
   for(int i=0;i<40;i++)tick.invoke(assist,false,true);
   check((boolean)begin.invoke(assist),"later independent assists remain available");
   cancel.invoke(assist);check(!(boolean)active.invoke(assist),"cancel retires owner-invalid support");
  }
  Object blocked=type.getConstructor().newInstance();begin.invoke(blocked);
  for(int i=0;i<80;i++)tick.invoke(blocked,false,true);
  check(!(boolean)active.invoke(blocked),"unreachable targets do not leave a companion idling for nine seconds");
  Object invalid=type.getConstructor().newInstance();begin.invoke(invalid);tick.invoke(invalid,false,false);
  check((boolean)retiring.invoke(invalid),"a lost target starts dismissal instead of retargeting another victim");
  System.out.println("Temporary Rika: one strike, finite approach, short fade, re-request rejection and cancellation passed");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
