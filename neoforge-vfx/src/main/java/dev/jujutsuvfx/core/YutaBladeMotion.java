package dev.jujutsuvfx.core;

/** Shared authoritative timings and continuous poses; ages are game ticks, not render frames. */
public final class YutaBladeMotion {
 public static final int IDLE=0, CLAW=1, DRAW=2, CUT_LEFT=3, CUT_RIGHT=4, CHOP=5;
 public static final int SPEECH=14, DOMAIN=15, PICK_SWORD=16, GUARD=17, DANCE=18, RIKA_SUMMON=19;
 public static final int VOW=23, LOVE_FIRE=24;
 public static final int CHASE=10, SIDESTEP=11, RETREAT=12, LEAP=13;
 public static final int CROSS=6, SPIN_CUT=7, LUNGE=8, RISING=9, BRAWL_BASE=32;
 private static final int[] BRAWL={CatCombatMoves.JAB,CatCombatMoves.HOOK,CatCombatMoves.UPPERCUT,
  CatCombatMoves.ELBOW,CatCombatMoves.RAPID,CatCombatMoves.KICK,CatCombatMoves.DOUBLE_PALM,CatCombatMoves.SWEEP};
 private static final int[] SWORD={CUT_LEFT,CUT_RIGHT,CROSS,RISING,LUNGE,CHOP,SPIN_CUT,CROSS,CUT_RIGHT,LUNGE};
 public static int chooseBrawl(int index){return BRAWL_BASE+BRAWL[Math.floorMod(index,BRAWL.length)];}
 public static int chooseSword(int index){return SWORD[Math.floorMod(index,SWORD.length)];}
 public static int brawlMove(int action){return action==CLAW?CatCombatMoves.JAB:action>=BRAWL_BASE?action-BRAWL_BASE:0;}
 public static boolean isTravel(int action){return action>=CHASE&&action<=LEAP;}
 public static int rate(int action){return brawlMove(action)!=0||action>=CUT_LEFT&&action<=RISING?2:1;}
 public static int chooseTravel(double distance,double rise,boolean grounded,boolean obstructed,boolean pressured,boolean combo){
  if(!grounded||distance>20)return IDLE;
  if(pressured&&distance<5)return SIDESTEP;
  if(combo&&distance<3.8)return RETREAT;
  if(distance>3.8&&distance<14){if((obstructed||rise>.8)&&rise<2.5)return LEAP;return CHASE;}
  return IDLE;
 }
 public static float travelProgress(int action,float age){return phase(age,0,duration(action));}
 public static float travelLift(int action,float age){return action==LEAP?(float)Math.sin(Math.PI*travelProgress(action,age))*1.8f:0;}
 public static double travelStep(int action,int age){return travelProgress(action,age+1)-travelProgress(action,age);}
 public static final int THROW=12, CATCH=34, DASH_START=38, DASH_END=46;
 private YutaBladeMotion(){}
 public static boolean cinematicDraw(boolean pair,int engagedTicks,boolean drawn){
  return pair&&!drawn&&engagedTicks>=40;
 }
 public static boolean shouldDraw(float health,float maximum,boolean committed){
  return !committed&&Float.isFinite(health)&&Float.isFinite(maximum)&&maximum>0&&health>0&&health<=maximum*.75f;
 }
 public static int duration(int action){if(YutaAreaMotion.active(action))return YutaAreaMotion.duration(action);int move=brawlMove(action);if(move!=0)return CatCombatMoves.duration(move)+4;
  return switch(action){case VOW->70;case LOVE_FIRE->305;case RIKA_SUMMON->36;case DANCE->YutaDanceMotion.DURATION;case GUARD->52;case DRAW->58;case SPEECH->CursedSpeechShape.DURATION;case DOMAIN->YutaDomainRules.POSE_TICKS;case PICK_SWORD->YutaDomainRules.PICK_TICKS;case CHASE->10;case SIDESTEP,RETREAT->8;case LEAP->14;case CHOP->26;case CROSS->27;case SPIN_CUT->30;case LUNGE->23;case RISING->22;case CUT_LEFT,CUT_RIGHT->20;default->0;};}
 public static int impact(int action){if(YutaAreaMotion.active(action))return YutaAreaMotion.contact(action);int move=brawlMove(action);if(move!=0)return CatCombatMoves.contact(move);
  return switch(action){case CHOP->12;case CROSS->7;case SPIN_CUT->12;case LUNGE->8;case RISING->9;default->7;};}
 public static boolean contact(int action,int age){
  if(brawlMove(action)==CatCombatMoves.RAPID)return age>=4&&age<=28&&(age-4)%3==0;
  return age==impact(action)||action==CROSS&&age==13;
 }
 public static boolean multiple(int action){return action==CROSS||brawlMove(action)==CatCombatMoves.RAPID;}
 public static double attackStep(int action,int age){
  if(action==LUNGE)return 2.4*(phase(age+1,5,11)-phase(age,5,11));
  if(brawlMove(action)==CatCombatMoves.KICK)return 1.4*(phase(age+1,4,9)-phase(age,4,9));
  return 0;
 }
 public static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
 public static float phase(float age,float start,float end){return smooth((age-start)/(end-start));}
 public static double dashStep(int tick){return 5.0*(phase(tick+1,DASH_START,DASH_END)-phase(tick,DASH_START,DASH_END));}
 public static float toss(float age){return Math.max(0,Math.min(1,(age-THROW)/(CATCH-THROW)));}
 public static float airborne(float age){float t=toss(age);return 4*t*(1-t);}
 public static float spin(float age){return (float)(Math.PI*4*toss(age));}
 public record Pose(float yaw,float roll,float pitch,float crouch,float headYaw,float headPitch){}
 public static Pose pose(int action,float age){
  if(YutaAreaMotion.active(action)){
   float hit=YutaAreaMotion.contact(action),w=phase(age,0,5)*(1-phase(age,duration(action)-6,duration(action)));
   float cut=phase(age,hit-3,hit+2),wind=phase(age,0,hit-4);
   if(action==YutaAreaMotion.SWEEP)return new Pose((float)(Math.PI*2)*phase(age,5,18),.12f*w,0,.14f*w,-.25f*w,0);
   if(action==YutaAreaMotion.FAN){float arc=(-.8f*wind+1.6f*cut)*w;return new Pose(.45f*arc,-.12f*arc,.08f*w,.12f*w,arc,0);}
   return new Pose(.12f*w,0,(-.25f*wind+.48f*cut)*w,-YutaAreaMotion.lift(action,age),
      0,(-.7f*wind+1.2f*cut)*w);
  }
  if(action==DRAW){
   float wind=phase(age,0,10), release=phase(age,10,18), settle=phase(age,24,34), recover=phase(age,46,58);
   float yaw=(-.68f*wind+1.18f*release)*(1-settle);
   float dash=phase(age,34,38)*(1-recover);
   return new Pose(yaw,.10f*wind*(1-settle),-.12f*dash,.09f*wind*(1-release)+.13f*dash,
     -.40f*wind*(1-release),-.42f*phase(age,12,20)*(1-phase(age,27,34))+.08f*dash);
  }
  if(action==IDLE)return new Pose(0,0,0,0,0,0);
  if(action==SPIN_CUT){float turn=phase(age,3,18),out=1-phase(age,20,30),weight=phase(age,0,4)*out;
   return new Pose((float)(Math.PI*2)*turn,.20f*(float)Math.sin(turn*Math.PI*2),.16f*weight,.12f*weight,-.24f*weight,0);}
  if(action==LUNGE){float thrust=phase(age,4,8)*(1-phase(age,12,23));
   return new Pose(.10f*thrust,0,.18f*thrust,.10f*thrust,-.35f*thrust,-.08f*thrust);}
  if(action==CROSS){float slash= - .60f*phase(age,0,5)+1.35f*phase(age,5,8)-1.5f*phase(age,11,14)+.75f*phase(age,16,27);
   return new Pose(slash*.48f,-slash*.20f,0,.09f*phase(age,0,4)*(1-phase(age,17,27)),slash,.12f*slash);}
  if(action==RISING){float stroke=phase(age,6,10),out=1-phase(age,13,22),w=phase(age,0,4)*out;
   return new Pose((-.4f+.85f*stroke)*w,.18f*w,(-.05f-.18f*stroke)*w,.16f*(1-stroke)*w,(.5f-stroke)*w,(.40f-.95f*stroke)*w);}
  float hit=impact(action), wind=phase(age,0,hit-2), cut=phase(age,hit-2,hit+2), recover=phase(age,hit+3,duration(action));
  float direction=action==CUT_RIGHT?-1:1;
  float swing=(-.62f*wind+1.32f*cut)*(1-recover)*direction;
  if(action==CHOP)return new Pose(.20f*wind*(1-recover),0,(-.24f*wind+.48f*cut)*(1-recover),
    -.12f*(float)Math.sin(Math.PI*phase(age,4,24)),.15f*wind*(1-recover),(-.60f*wind+1.05f*cut)*(1-recover));
  return new Pose(swing*.40f,-swing*.10f,0,.06f*wind*(1-recover),swing,0);
 }
}
