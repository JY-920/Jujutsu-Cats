package dev.jujutsuvfx.core;
public final class YutaDanceTest {
 public static void main(String[] args){
  if(YutaBladeMotion.duration(18)!=200)throw new AssertionError("Idle dance must last ten seconds");
  if(YutaBladeMotion.rate(18)!=1||YutaBladeMotion.isTravel(18)||YutaBladeMotion.brawlMove(18)!=0)throw new AssertionError("Dance cannot deal contact damage or travel");
  for(int flags=0;flags<64;flags++){
   boolean safe=YutaDanceMotion.safe((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0,(flags&16)!=0,(flags&32)!=0);
   if(safe!=(flags==2))throw new AssertionError("Unsafe ambient dance eligibility "+flags);
  }
  if(YutaDanceMotion.weight(0)!=0||YutaDanceMotion.weight(200)!=0||YutaDanceMotion.weight(20)!=1)throw new AssertionError("Dance entry/exit envelope");
  for(float t=10;t<130;t+=.125f){
   if(Math.abs(YutaDanceMotion.step(t)-YutaDanceMotion.step(t+YutaDanceMotion.LOOP))>.0001)throw new AssertionError("Drifting partner clock");
  }
  YutaActionClock clock=new YutaActionClock();clock.tick(4,18,2,121,false);
  if(clock.age(1)!=121)throw new AssertionError("Late tracker must join the current dance phase");
  clock.tick(5,3,1,0,false);
  if(clock.action()!=3||clock.oldAction()!=18||clock.oldAge()!=121)throw new AssertionError("Combat must interrupt the dance and preserve its exit pose");
  System.out.println("Yuta dance duration and noncombat action passed");
 }
}
