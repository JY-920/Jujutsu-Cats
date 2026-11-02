package dev.jujutsuvfx.core;

/** Shared hand/weapon clock: a deliberate wipe, then three compact whole-body cuts. */
public final class YutaKatanaMotion {
 public static final int CHARGE=32,RECOVERY=11;
 public static final float BASE_DAMAGE=12,BONUS_DAMAGE=6;
 private YutaKatanaMotion(){}
 public static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
 public static float wipe(float ticks){return smooth((ticks-8)/18);}
 public static float bonus(float strength){float c=Math.max(0,Math.min(1,strength));return BONUS_DAMAGE*(.2f+.8f*c*c);}
 public record Pose(float x,float y,float z,float pitch,float yaw,float roll,float torso) {
  public Pose blend(Pose b,float t){return new Pose(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,pitch+(b.pitch-pitch)*t,yaw+(b.yaw-yaw)*t,roll+(b.roll-roll)*t,torso+(b.torso-torso)*t);}
 }
 public static final Pose IDLE=new Pose(.48f,-.56f,-.98f,-18,-10,-26,0);
 public static final Pose HORIZONTAL=new Pose(.58f,-.22f,-1.02f,0,0,90,-12);
 public static Pose charge(float ticks){return IDLE.blend(new Pose(HORIZONTAL.x()-.30f*wipe(ticks),HORIZONTAL.y(),HORIZONTAL.z(),0,0,90,-12),smooth(ticks/8));}
 public static Pose swing(int step,float age){
  if(age<0||age>=RECOVERY)return IDLE;
  Pose wind,cut;
  switch(Math.floorMod(step,3)){
   case 1->{wind=new Pose(-.20f,-.55f,-.72f,18,-48,65,-24);cut=new Pose(.62f,-.32f,-1.10f,0,35,-90,30);}
   case 2->{wind=new Pose(.48f,-.32f,-.52f,-48,-10,-8,-18);cut=new Pose(.10f,-.18f,-1.40f,-85,8,-9,25);}
   default->{wind=new Pose(.60f,-.04f,-.56f,12,45,-60,26);cut=new Pose(-.36f,-.32f,-1.10f,0,-35,90,-32);}
  }
  if(age<2)return IDLE.blend(wind,smooth(age/2));
  if(age<4)return wind.blend(cut,smooth((age-2)/2));
  if(age<5)return cut; // one brief contact accent, not a duplicate swing
  return cut.blend(IDLE,smooth((age-5)/6));
 }
}
