package dev.jujutsuvfx.core;

/** Regression: an explicitly sustained dance must keep moving after the ambient duration. */
public final class YutaDebugControlTest {
 public static void main(String[] args){
  YutaActionClock clock=new YutaActionClock();
  clock.tick(8,YutaBladeMotion.DANCE,2,300,false);
  clock.tick(8,YutaBladeMotion.DANCE,2,301,false);
  if(clock.age(1)<=300)throw new AssertionError("Sustained dance froze or rewound after the ambient duration");
  float prior=clock.age(1);
  clock.tick(8,YutaBladeMotion.DANCE,2,0,false);
  if(clock.age(1)<prior)throw new AssertionError("A delayed metadata packet rewound the sustained dance");
  clock.tick(9,YutaBladeMotion.IDLE,2,0,false);
  if(clock.action()!=YutaBladeMotion.IDLE||clock.oldAction()!=YutaBladeMotion.DANCE)
   throw new AssertionError("Leaving sustained dance must restore normal action transitions");
  System.out.println("Sustained Yuta dance clock passed");
 }
}
