package dev.jujutsuvfx.core;

public final class RikaSupportTest {
 public static void main(String[] args)throws Exception {
  RikaAssistTest.main(args);
  Class<?> type=Class.forName("dev.jujutsuvfx.core.RikaSupportClock");
  var begin=type.getMethod("begin",int.class);var tick=type.getMethod("tick",boolean.class);
  var active=type.getMethod("active");var guard=type.getMethod("guarding");
  int[] durations={0,22,28,36,36,36,26,22,32};
  int[] contactTicks={0,9,12,14,12,14,11,9,14};
  for(int action=1;action<=8;action++){
   Object clock=type.getConstructor().newInstance();
   check(!(boolean)begin.invoke(clock,0),"invalid action cannot consume cooldown");
   check((boolean)begin.invoke(clock,action),"fresh assist starts");
   int hits=0,guardTicks=0;
   for(int i=0;i<durations[action];i++){
    check(!(boolean)begin.invoke(clock,action),"a second request cannot restart an unfinished swing");
    if((boolean)tick.invoke(clock,true)){hits++;check(i+1==contactTicks[action],"contact follows the authored move's timing");}
    if((boolean)guard.invoke(clock))guardTicks++;
   }
   check(hits==1,"exactly one server contact per assist");
   check(!(boolean)active.invoke(clock),"animation retires");
   check(action==4?guardTicks>0&&guardTicks<=14:guardTicks==0,"guard is short and belongs only to guard action");
   check((boolean)begin.invoke(clock,action),"recovery ends into the next attack without a hidden cooldown");
   check(!(boolean)tick.invoke(clock,false)&&!(boolean)active.invoke(clock),"invalid target cancels before damage");
   check((boolean)begin.invoke(clock,action),"cancelled support can pursue again without an unrelated lockout");
  }
  RikaResidentTest.main(args);RikaGroundPathTest.main(args);
  var variety=new java.util.HashSet<String>();
  for(int i=0;i<6;i++)variety.add(RikaSupportClock.clip(RikaSupportClock.melee(i)));
  check(variety.size()==6,"six melee variants are actually selected by the resident sequence");
  check(RikaSupportClock.inReach(1,2.4,0,1,.35),"a visible frontal paw contact can land");
  check(!RikaSupportClock.inReach(1,5,0,1,.35),"an escaped target is not remotely hit");
  check(!RikaSupportClock.inReach(1,-2,0,1,.35),"a target behind the committed swing is not hit");
  check(!RikaSupportClock.inReach(1,2,3,1,.35),"a sidestep escapes a straight punch");
  check(!RikaSupportClock.inReach(1,2,0,4,.35),"airborne targets require the wave");
  System.out.println("Rika: one contact per swing, continuous recovery, eight actions, cancellation and guard passed");
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
