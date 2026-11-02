package dev.jujutsuvfx.core;
public final class YutaActionClockTest {
 public static void main(String[] args){
  YutaActionClock c=new YutaActionClock();c.tick(1,2,2,0,false);float last=0;
  for(int tick=0;tick<14;tick++){
   float before=c.age(1);c.tick(1,2,2,(tick/4)*4,false);
   check(c.age(1)-before>=.9f&&c.age(1)-before<=1.2f,"four-tick sparse metadata still advances smoothly");
   for(int frame=0;frame<10;frame++){float age=c.age(frame/10f);check(age>=last,"sparse metadata cannot rewind between client ticks");last=age;}
  }
  c.tick(1,2,2,0,false);check(c.age(1)>=last,"old authoritative frame cannot rewind");
  c.tick(1,2,2,17,true);float held=c.age(1);c.tick(1,2,2,17,true);check(c.age(1)==held,"hit stop freezes clock");
  c.tick(2,2,1,0,false);check(c.age(1)==0&&c.oldAction()==2,"same move new sequence restarts once");
  c.tick(2,2,1,1,false);check(c.age(1)>0,"same sequence advances");
  YutaActionClock fast=new YutaActionClock();fast.tick(1,3,2,0,false);
  for(int tick=0;tick<8;tick++){float before=fast.age(1);fast.tick(1,3,2,(tick/4)*8,false);check(Math.abs(fast.age(1)-before-2)<.01,"melee client clock advances two frames per tick with sparse packets");}
  for(int i=0;i<8;i++)check(YutaBladeMotion.brawlMove(YutaBladeMotion.chooseBrawl(i))!=0,"Gojo brawl move mapping");
  java.util.Set<Integer> blade=new java.util.HashSet<>();for(int i=0;i<12;i++)blade.add(YutaBladeMotion.chooseSword(i));check(blade.size()>=7,"seven sword attacks");
  check(YutaBladeMotion.contact(YutaBladeMotion.CROSS,7)&&YutaBladeMotion.contact(YutaBladeMotion.CROSS,13),"two independent cross contacts");
  System.out.println("Yuta combat: sparse/late updates never rewind, hit stop, sequence restart, brawl reuse and sword variety passed");
 }
 static void check(boolean value,String msg){if(!value)throw new AssertionError(msg);}
}
