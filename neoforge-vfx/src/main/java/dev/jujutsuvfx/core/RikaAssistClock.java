package dev.jujutsuvfx.core;

/** One requested swing, followed by dismissal. Only the ring-owned resident may loop attacks. */
public final class RikaAssistClock {
 public static final int APPROACH=60,FADE=8,GAP=32;
 private static final int NONE=0,WALKING=1,SWING=2,LEAVING=3;
 private int phase,remaining,cooldown;
 public boolean begin(){
  if(active()||cooldown>0)return false;
  phase=WALKING;remaining=APPROACH;return true;
 }
 public void attackStarted(){if(waiting()){phase=SWING;remaining=FADE;}}
 public void tick(boolean attacking,boolean targetValid){
  if(cooldown>0)cooldown--;
  if(phase==WALKING&&(!targetValid||--remaining<=0)||phase==SWING&&!attacking){
   phase=LEAVING;remaining=FADE;
  }else if(phase==LEAVING&&--remaining<=0){phase=NONE;cooldown=GAP;}
 }
 public boolean active(){return phase!=NONE;}
 public boolean waiting(){return phase==WALKING;}
 public boolean retiring(){return phase==LEAVING;}
 /** Keep a started swing fully visible; the fade begins only after recovery. */
 public int remaining(){return phase==WALKING?remaining+FADE:phase==SWING?FADE+1:Math.max(0,remaining);}
 public void cancel(){phase=NONE;remaining=0;cooldown=Math.max(cooldown,GAP);}
}
