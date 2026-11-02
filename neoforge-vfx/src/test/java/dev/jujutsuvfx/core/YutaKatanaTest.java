package dev.jujutsuvfx.core;
public final class YutaKatanaTest {
 public static void main(String[] args){
  if(YutaKatanaMotion.BASE_DAMAGE!=12||YutaKatanaMotion.bonus(1)!=6)throw new AssertionError("12 + 6 full-strength contact");
  if(YutaKatanaMotion.bonus(.2f)>=YutaKatanaMotion.bonus(.8f))throw new AssertionError("Do not bypass vanilla cooldown by spamming bonus damage");
  float previous=-1;
  for(float t=0;t<=YutaKatanaMotion.CHARGE;t+=.05f){float wipe=YutaKatanaMotion.wipe(t);if(wipe<previous)throw new AssertionError("Wipe reversed mid-blade");previous=wipe;}
  if(YutaKatanaMotion.wipe(8)!=0||YutaKatanaMotion.wipe(26)!=1)throw new AssertionError("Tip-to-base wipe must finish before imbuing");
  for(int move=0;move<3;move++){
   var last=YutaKatanaMotion.swing(move,0);
   for(float age=.01f;age<=12;age+=.01f){
    var p=YutaKatanaMotion.swing(move,age);
    for(float v:new float[]{p.x(),p.y(),p.z(),p.pitch(),p.yaw(),p.roll(),p.torso()})if(!Float.isFinite(v))throw new AssertionError("Invalid katana transform");
    if(Math.abs(p.roll()-last.roll())>2||Math.abs(p.yaw()-last.yaw())>2||Math.abs(p.x()-last.x())>.025f)throw new AssertionError("Discontinuous cut "+move+" at "+age);
    last=p;
   }
   if(!YutaKatanaMotion.swing(move,12).equals(YutaKatanaMotion.IDLE))throw new AssertionError("Cut must recover");
  }
  if(YutaDanceMotion.progress(10.05f)==YutaDanceMotion.progress(10.30f))throw new AssertionError("Dance clock must interpolate continuously, never hold cels");
  System.out.println("Katana: 12+6 damage, cooldown scaling, monotonic blade wipe, three continuous cuts and continuous dance acceleration passed");
 }
}
