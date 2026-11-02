package dev.jujutsuvfx.core;
public final class YutaBladeMotionTest {
 public static void main(String[] args){
  check(YutaBladeMotion.duration(23)>=70&&YutaBladeMotion.rate(23)==1&&YutaBladeMotion.brawlMove(23)==0,"vow survives its complete scene window without becoming a melee move");
  check(YutaBladeMotion.duration(24)>=305&&YutaBladeMotion.rate(24)==1&&YutaBladeMotion.brawlMove(24)==0,"love fire survives charge, clash, breakthrough and recovery");
  check(!YutaBladeMotion.shouldDraw(601,800,false),"above threshold");
  check(YutaBladeMotion.shouldDraw(600,800,false),"75 percent remaining");
  check(!YutaBladeMotion.shouldDraw(200,800,true),"draw once even after healing/reload");
  check(!YutaBladeMotion.shouldDraw(0,800,false),"no postmortem draw");
  double distance=0; for(int tick=0;tick<YutaBladeMotion.duration(YutaBladeMotion.DRAW);tick++){
   double step=YutaBladeMotion.dashStep(tick);check(step>=0&&step<=1,"bounded dash step");
   if(tick<YutaBladeMotion.CATCH)check(step==0,"cannot dash before catching");distance+=step;
  }
  check(Math.abs(distance-5)<1e-6,"dash integrates to exactly five blocks");
  check(YutaBladeMotion.airborne(11)==0&&YutaBladeMotion.airborne(23)>0&&YutaBladeMotion.airborne(34)==0,"toss arc endpoints");
  check(Math.abs(YutaBladeMotion.spin(34)-Math.PI*4)<1e-5,"two airborne turns");
  for(int action=0;action<=YutaBladeMotion.RISING;action++)for(float t=0;t<=YutaBladeMotion.duration(action);t+=.1f){
   var p=YutaBladeMotion.pose(action,t);check(Float.isFinite(p.yaw()+p.roll()+p.pitch()+p.crouch()+p.headYaw()+p.headPitch()),"finite pose");
  }
  check(YutaBladeMotion.pose(YutaBladeMotion.DRAW,0).yaw()==0,"continuous entry");
  check(Math.abs(YutaBladeMotion.pose(YutaBladeMotion.DRAW,58).yaw())<1e-5,"settled recovery");
  System.out.println("Yuta: one-time 75% draw, catch-before-dash, exact 5-block travel, toss endpoints and finite poses passed");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
