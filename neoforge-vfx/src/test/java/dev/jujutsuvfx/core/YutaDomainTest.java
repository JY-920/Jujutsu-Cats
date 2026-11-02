package dev.jujutsuvfx.core;
public final class YutaDomainTest {
 public static void main(String[] args){
  try{
   var ready=YutaDomainRules.class.getMethod("playerChargeComplete",int.class);
   for(int tick=0;tick<YutaDomainRules.WINDUP;tick++)check(!(boolean)ready.invoke(null,tick),"tap/early release must not cast");
   check((boolean)ready.invoke(null,YutaDomainRules.WINDUP),"full hold commits domain");
  }catch(ReflectiveOperationException e){throw new AssertionError("Missing hold-to-cast guard",e);}

  check(YutaDomainRules.milestone(401,800,0)==0,"no early domain");
  check(YutaDomainRules.milestone(400,800,0)==1,"half-health domain");
  int used=YutaDomainRules.consume(0,1);
  check(YutaDomainRules.milestone(350,800,used)==0,"one cast per milestone");
  check(YutaDomainRules.milestone(80,800,used)==2,"critical domain");
  check(YutaDomainRules.milestone(400,800,YutaDomainRules.consume(0,2))==0,"critical first consumes skipped half threshold");
  check(YutaDomainRules.milestone(0,800,0)==0,"death never casts");
  check(YutaDomainRules.milestone(Float.NaN,800,0)==0,"invalid health rejected");
  try {
   var method=YutaDomainRules.class.getMethod("damageMultiplier",boolean.class,boolean.class);
   check((float)method.invoke(null,true,true)==1.5f,"inside open own domain gives exactly 1.5x");
   check((float)method.invoke(null,false,true)==1f,"windup/expired domain no buff");
   check((float)method.invoke(null,true,false)==1f,"outside own domain no buff");
  }catch(ReflectiveOperationException e){throw new AssertionError("Domain empowerment missing",e);}
  check(YutaDomainRules.WINDUP<YutaDomainRules.POSE_TICKS,"gesture precedes unfolding");
  System.out.println("Yuta domain: one-shot health milestones, death guard, bounded empowerment passed");
 }
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
