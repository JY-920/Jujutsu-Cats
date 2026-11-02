package dev.jujutsuvfx.core;

/** Tick-owned visual state. Rendering only interpolates; stale metadata cannot rewind a pose. */
public final class YutaActionClock {
 private int sequence=Integer.MIN_VALUE,action,hand=2,oldAction,oldHand=2;
 private float age,previous,oldAge,blend,previousBlend;
 public void tick(int nextSequence,int nextAction,int nextHand,int authoritative,boolean paused){
  if(sequence!=nextSequence||action!=nextAction){
   oldAction=action;oldHand=hand;oldAge=age;
   sequence=nextSequence;action=nextAction;hand=nextHand;age=previous=Math.max(0,authoritative);
   blend=previousBlend=0;return;
  }
  previous=age;previousBlend=blend;
  if(paused)return;
  int speed=YutaBladeMotion.rate(action);
  age=Math.min(action==YutaBladeMotion.DANCE&&(authoritative>YutaBladeMotion.duration(action)||age>YutaBladeMotion.duration(action))?Integer.MAX_VALUE:YutaBladeMotion.duration(action),Math.max(age,Math.min(authoritative+5*speed,age+speed*(authoritative-age>2*speed?1.15f:1))));
  blend=Math.min(3,blend+Math.max(speed,YutaBladeMotion.rate(oldAction)));
 }
 public boolean ready(){return sequence!=Integer.MIN_VALUE;}
 public int action(){return action;}
 public int hand(){return hand;}
 public int oldAction(){return oldAction;}
 public int oldHand(){return oldHand;}
 public float oldAge(){return oldAge;}
 public float age(float partial){return previous+(age-previous)*Math.max(0,Math.min(1,partial));}
 public float blend(float partial){return YutaBladeMotion.smooth((previousBlend+(blend-previousBlend)*Math.max(0,Math.min(1,partial)))/3);}
}
