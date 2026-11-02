package dev.jujutsuvfx.core;
public final class SpiritRulesTest {
 public static void main(String[] args){
  for(int role=0;role<3;role++)for(int choice=-15;choice<20;choice++)for(float size:new float[]{-1,0,.5f,1,9,Float.NaN}){
   var s=SpiritRules.choose(role,choice,choice,size);
   if(s.skin()<0||s.skin()>=SpiritRules.SKINS.length||s.role()!=role||s.palette()<0||s.palette()>2||!Float.isFinite(s.height())||s.height()<.6||s.height()>3)throw new AssertionError("Invalid appearance");
   if(role==1&&(s.skin()<3||s.skin()>5))throw new AssertionError("Ground model in flight pool");
   float scale=SpiritRules.scale(s.height(),.5f);if(scale<.35||scale>3.5)throw new AssertionError("Unbounded scale");
  }
  if(!SpiritRules.milestone(400,800,1,0)||SpiritRules.milestone(400,800,1,1)||!SpiritRules.milestone(80,800,2,1)||SpiritRules.milestone(0,800,2,0))throw new AssertionError("Milestone repeats or triggers at death");
  System.out.println("Spirit rules: bounded palettes/scales, role pools and one-shot health milestones passed");
 }
}
