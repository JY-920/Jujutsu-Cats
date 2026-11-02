package dev.jujutsuvfx.core;

public final class YutaGetoBeatTest {
 public static void main(String[] args){
  int transitions=0;
  for(int phase=1;phase<YutaGetoBeat.DONE;phase++){
   check(YutaGetoBeat.next(phase,YutaGetoBeat.duration(phase)-1,1,true)==phase,"minimum action window");
   check(YutaGetoBeat.next(phase,YutaGetoBeat.duration(phase),1,true)==phase+1,"no skipped beats even terminal");
   transitions++;
  }
  check(YutaGetoBeat.next(YutaGetoBeat.MELEE,39,0,false)==YutaGetoBeat.MELEE,"one opening exchange before draw");
  check(YutaGetoBeat.next(YutaGetoBeat.MELEE,40,0,false)==YutaGetoBeat.DRAW,"early draw even when opening attacks are blocked");
  double distance=3;
  for(int t=0;t<32;t++)distance+=YutaGetoBeat.separationStep(t,distance);
  check(distance>24&&distance<=26,"single rapid retreat creates charge space within 1.6 seconds");
  check(YutaGetoBeat.separationStep(29,3)==0,"plant feet before charge");
  check(YutaGetoBeat.separationStep(10,28)==0,"already separated actor stays put");
  check(YutaGetoBeat.next(YutaGetoBeat.SWARM,800,0,false)==YutaGetoBeat.RING,"stagnation bound");
  check(YutaGetoBeat.progress(.65f,800,800,800,800)==.65f,"healing cannot rewind");
  check(YutaGetoBeat.progress(0,400,800,800,800)==.25f,"average loss");
  check(YutaGetoBeat.resume(YutaGetoBeat.DRAW)==YutaGetoBeat.SWARM,"do not replay completed draw");
  check(YutaGetoBeat.resume(YutaGetoBeat.CLASH)==YutaGetoBeat.CLASH,"reload cannot replay finished vow or charge");
  check(YutaGetoBeat.resumeAge(YutaGetoBeat.CLASH,47)==47,"restore in-progress clash clock");
  check(YutaGetoBeat.resumeAge(YutaGetoBeat.DRAW,42)==0,"skipped one-shot pose resets next clock");
  check(YutaGetoBeat.resumeAge(YutaGetoBeat.CLASH,Integer.MAX_VALUE)==90,"corrupt age cannot overflow");
  check(YutaGetoBeat.resumeAge(YutaGetoBeat.CHARGE,-20)==0,"negative checkpoint clamped");
  check(YutaGetoBeat.resume(YutaGetoBeat.AFTERMATH)==YutaGetoBeat.DONE,"never duplicate resolved blast");
  for(int age=0;age<=90;age++){
   double c=YutaGetoBeat.clashFraction(age);
   check(c>.4&&c<.71,"collision between opponents");
  }
  check(Math.abs(YutaGetoBeat.clashFraction(90)-YutaGetoBeat.breakthroughFraction(0))<.00001,"continuous breakthrough");
  check(YutaGetoBeat.breakthroughFraction(40)==1,"Geto loses at beam endpoint");
  check(YutaGetoBeat.next(YutaGetoBeat.DONE,99999,1,true)==YutaGetoBeat.DONE,"finished cannot restart");
  RikaResidentClock rika=new RikaResidentClock();
  rika.scriptedBegin();
  for(int t=0;t<36;t++){check(!rika.manifest(),"scripted ring cannot skip pose");rika.tick(true);}
  check(rika.manifest(),"scripted ring manifests once");
  for(int t=0;t<2200;t++){rika.sustain();rika.tick(true);check(rika.active()&&rika.remaining()>=39,"long combat retains resident without replay");}
  for(int t=0;t<40;t++)rika.tick(true);
  check(!rika.active(),"stopping sustain still releases resident");
  rika.scriptedResident();rika.tick(false);check(!rika.active(),"invalid owner cannot keep scripted residence");
  int phase=YutaGetoBeat.WAIT,age=0,total=0;
  while(phase<YutaGetoBeat.DONE&&total<6000){
   int next=YutaGetoBeat.next(phase,age,0,false);
   if(next!=phase){phase=next;age=0;}else age++;
   total++;
  }
  check(phase==YutaGetoBeat.DONE,"even perfect guards cannot make the entire story permanent");
  System.out.println("Yuta/Geto: "+transitions+" gated transitions, monotonic health, reload, terminal, contact-plane continuity passed");
 }
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
