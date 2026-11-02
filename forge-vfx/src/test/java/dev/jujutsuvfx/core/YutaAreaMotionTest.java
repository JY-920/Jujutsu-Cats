package dev.jujutsuvfx.core;

public final class YutaAreaMotionTest {
 public static void main(String[] args){
  int checks=0;
  for(int a=20;a<=22;a++){
   if(YutaBladeMotion.duration(a)<=YutaAreaMotion.contact(a))throw new AssertionError("contact outside action");
   int hits=0;for(int age=0;age<=YutaBladeMotion.duration(a);age++)if(YutaBladeMotion.contact(a,age))hits++;
   if(hits!=1)throw new AssertionError("exactly one wide hit");
   if(YutaAreaMotion.visual(a,YutaAreaMotion.contact(a))<.99)throw new AssertionError("impact must be visible");
   if(YutaAreaMotion.reaches(a,100,0,0,0,1,0)||YutaAreaMotion.reaches(a,0,20,0,0,1,0))throw new AssertionError("bounded volume");
   if(YutaAreaMotion.reaches(a,Double.NaN,0,0,0,1,0))throw new AssertionError("nonfinite rejected");
   for(float t=0;t<=YutaBladeMotion.duration(a);t+=.05f){
    var p=YutaBladeMotion.pose(a,t);
    if(!Float.isFinite(p.yaw()+p.pitch()+p.roll()+YutaAreaMotion.visual(a,t)))throw new AssertionError("finite animation");
    checks++;
   }
  }
  if(!YutaAreaMotion.reaches(21,0,0,8.9,0,1,0)||YutaAreaMotion.reaches(21,0,0,-2,0,1,0))throw new AssertionError("fan front only");
  if(!YutaAreaMotion.reaches(20,0,0,-5,0,1,0))throw new AssertionError("sweep covers rear");
  if(SpiritRules.MAX_HEALTH!=30||SpiritRules.loadedHealth(130)!=30||SpiritRules.loadedHealth(7)!=7||SpiritRules.loadedHealth(0)!=0)throw new AssertionError("spirit migration no healing/revival");
  System.out.println("Yuta area: "+checks+" poses, one contact, visible radius, fan direction, finite inputs and spirit health migration passed");
 }
}
