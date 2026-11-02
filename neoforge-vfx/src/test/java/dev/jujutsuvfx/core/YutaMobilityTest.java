package dev.jujutsuvfx.core;
import java.util.*;
import static dev.jujutsuvfx.core.YutaBladeMotion.*;
public final class YutaMobilityTest {
 public static void main(String[] args){
  List<Integer> attacks=new ArrayList<>();for(int i=0;i<8;i++)attacks.add(chooseBrawl(i));for(int i=0;i<10;i++)attacks.add(chooseSword(i));
  for(int action:attacks){
   if(rate(action)!=2)throw new AssertionError("every melee animation must run at 2x");
   List<Integer> slow=new ArrayList<>(),fast=new ArrayList<>();double distance=0,fastDistance=0;
   for(int age=0;age<=duration(action);age++){if(contact(action,age))slow.add(age);distance+=attackStep(action,age);}
   for(int age=0;age<=duration(action);age+=rate(action))for(int sub=age;sub<Math.min(age+rate(action),duration(action)+1);sub++){
    if(contact(action,sub))fast.add(sub);fastDistance+=attackStep(action,sub);
   }
   if(!slow.equals(fast)||Math.abs(distance-fastDistance)>1e-6)throw new AssertionError("double-speed must retain every contact and displacement");
  }
  if(rate(DRAW)!=1)throw new AssertionError("draw ceremony keeps original choreography");
  if(chooseTravel(9,0,true,false,false,false)!=CHASE)throw new AssertionError("gap close");
  if(chooseTravel(6,1,true,true,false,false)!=LEAP)throw new AssertionError("vault obstacle");
  if(chooseTravel(2,0,true,false,true,false)!=SIDESTEP)throw new AssertionError("pressure dodge");
  if(chooseTravel(2,0,true,false,false,true)!=RETREAT)throw new AssertionError("combo disengage");
  if(chooseTravel(9,0,false,false,false,false)!=IDLE)throw new AssertionError("no repeated airborne dash");
  if(Math.abs(travelLift(LEAP,duration(LEAP)/2f)-1.8)>1e-5||Math.abs(travelLift(LEAP,duration(LEAP)))>1e-5)
   throw new AssertionError("checked leap arc has fixed apex and exact landing height");
  for(int action:new int[]{CHASE,SIDESTEP,RETREAT,LEAP}){
   double sum=0;for(int age=0;age<duration(action);age++){double step=travelStep(action,age);if(step<0||!Double.isFinite(step))throw new AssertionError("invalid movement");sum+=step;}
   if(Math.abs(sum-1)>1e-6)throw new AssertionError("exact travel distance");
  }
  System.out.println("Yuta: 2x melee retains every contact/travel; pressure dodge, chase, leap, disengage and distance budgets passed");
 }
}
