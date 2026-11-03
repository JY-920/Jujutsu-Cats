package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.core.YutaBladeMotion;
import net.minecraft.util.Mth;
import java.util.Arrays;
import static dev.jujutsuvfx.core.YutaBladeMotion.*;

/** Shares Gojo's authored melee bones, with a separate jaw-led sword choreography. */
final class YutaCombatPose {
 private final float[] values=new float[63];
 private final CatCombatPose brawl=new CatCombatPose();
 float crouch,hipFollow,danceWeight,ringSlide;
 float value(int bone,int field){return values[bone*7+field];}
 float elbow(int hand){return values[hand==1?56:57];}
 float forearmTwist(int hand){return values[hand==1?61:62];}
 float pitch(){return values[58];} float roll(){return values[59];} float yaw(){return values[60];}
 void sampleShuffle(float age){
  Arrays.fill(values,0);crouch=hipFollow=danceWeight=ringSlide=0;
  float w=dev.jujutsuvfx.core.ShuffleDanceMotion.weight(age);float[] k=dev.jujutsuvfx.core.ShuffleDanceMotion.sample(age);
  float tilt=-(float)Math.toRadians(60)*w,bob=-k[0]*16*w,side=dev.jujutsuvfx.core.ShuffleDanceMotion.LATERAL_TRAVEL*16*k[2]*w;
  values[7]=tilt;standing(1,12,-10,tilt);standing(0,15,-9,tilt);
  float compress=.5f*(1-k[0]/dev.jujutsuvfx.core.ShuffleDanceMotion.HOP_HEIGHT)*w;
  values[11]+=bob+compress;values[4]+=bob+compress;values[10]=values[3]=side;
  values[8]=.025f*k[1]*w;values[0]=.38f*w;
  float shoulderY=standY(14.1f,-5,tilt)+bob+compress,shoulderZ=standZ(14.1f,-5,tilt);
  for(int bone=2;bone<=3;bone++){
   float sign=bone==2?1:-1;
   values[bone*7]=(.08f+sign*.34f*k[1])*w;
   values[bone*7+2]=-sign*.10f*w;values[bone*7+3]=side+sign*1.1f*w;
   values[bone*7+4]=shoulderY+1.8f*w;values[bone*7+5]=shoulderZ+1.0f*w;values[bone*7+6]=w;
  }
  values[56]=values[57]=-.42f*w;
  for(int bone=4;bone<=5;bone++){
   float sign=bone==4?1:-1,lift=dev.jujutsuvfx.core.ShuffleDanceMotion.lift(k[1],sign);
   values[bone*7]=(-.18f-.65f*lift)*w;values[bone*7+1]=sign*.10f*k[1]*w;
   values[bone*7+2]=sign*.04f*w;values[bone*7+3]=side+sign*.5f*w;
   values[bone*7+4]=bob-4.0f*lift*w;values[bone*7+5]=(-1.7f-3.5f*lift)*w;values[bone*7+6]=w;
  }
  // Lean the upper body around the hips; planted feet do not slide with the chest.
  float lean=-dev.jujutsuvfx.core.ShuffleDanceMotion.LEAN*k[2]*w;
  leanUpper(0,0,15,18+bob+compress,lean);leanUpper(1,0,12,18+bob+compress,lean);
  leanUpper(2,1.2f,14.1f,18+bob+compress,lean);leanUpper(3,-1.2f,14.1f,18+bob+compress,lean);
  values[42]=1.65f*w;values[49]=1.4f*w;values[43]=-.12f*k[2]*w;values[50]=-.18f*k[2]*w;
 }
 private void leanUpper(int bone,float baseX,float baseY,float hipY,float lean){
  int i=bone*7;float x=baseX+values[i+3],y=baseY+values[i+4]-hipY;
  values[i+3]=x*Mth.cos(lean)-y*Mth.sin(lean)-baseX;
  values[i+4]=hipY+x*Mth.sin(lean)+y*Mth.cos(lean)-baseY;
  values[i+2]+=lean;
 }
 void sample(int action,int hand,float age){
  Arrays.fill(values,0);crouch=hipFollow=danceWeight=ringSlide=0;
  if(action==VOW||action==LOVE_FIRE){sampleLove(action,age);return;}
  if(action>=104&&action<=106){sampleGetoFinale(action,age);return;}
  if(action==101){
   float w=dev.jujutsuvfx.core.GetoVortexMotion.brace(age),r=dev.jujutsuvfx.core.GetoVortexMotion.recoil(age);
   values[0]=(-.12f-.18f*r)*w;values[58]=(-.08f+.12f*r)*w;crouch=.10f*w;
   for(int bone=2;bone<=5;bone++){float side=bone%2==0?1:-1;values[bone*7+3]=side*.7f*w;values[bone*7+4]=-1.6f*w;values[bone*7+6]=w;}
   values[14]=(-.55f+.35f*r)*w;values[21]=(-.34f+.20f*r)*w;
   values[16]=-.18f*w;values[23]=.18f*w;values[56]=-.65f*w;values[57]=-.5f*w;
   values[28]=.18f*w;values[35]=.28f*w;values[42]=.25f*w;values[49]=.15f*w;return;
  }
  if(dev.jujutsuvfx.core.GetoCloudMotion.active(action)){
   var p=dev.jujutsuvfx.core.GetoCloudMotion.pose(action,age);
   values[0]=p.headPitch();values[1]=p.headYaw();values[60]=p.bodyYaw();values[59]=p.roll();values[58]=p.bodyPitch();crouch=p.crouch();
   float w=phase(age,0,5)*(1-phase(age,23,30));
   for(int bone=2;bone<=5;bone++){
    float side=bone%2==0?1:-1,lead=bone==2||bone==5?1:-1;
    values[bone*7]=.08f*w+lead*p.stride();values[bone*7+2]=-side*.10f*w;
    values[bone*7+3]=side*.3f*w;values[bone*7+4]=-crouch*16;values[bone*7+6]=w;
   }
   // Opposed planted paws support the jaw's impulse; both tail links follow the turn.
   values[56]=-.22f*w;values[57]=-.18f*w;
   values[42+1]=-Mth.sin(p.bodyYaw())*.35f;values[49+1]=-Mth.sin(p.bodyYaw())*.20f;
   return;
  }
  int move=brawlMove(action);
  if(move!=0||isTravel(action)){
   int sampled=isTravel(action)?action==LEAP?CatCombatMoves.AIR_CHASE:action==CHASE?CatCombatMoves.DASH:CatCombatMoves.REPOSITION:move;
   int variant=action==SIDESTEP?CatCombatMoves.SIDE_STEP:action==RETREAT?CatCombatMoves.BACK_HOP:0;
   float sampleAge=action==SIDESTEP||action==RETREAT?age*2:age;
   brawl.sample(sampled,hand,sampleAge,variant);
   float exit=isTravel(action)?1-phase(age,duration(action)-3,duration(action)):1-phase(age,CatCombatMoves.duration(move)-2,duration(action));
   for(int bone=0;bone<8;bone++)for(int field=0;field<7;field++)values[bone*7+field]=brawl.value(bone,field)*exit;
   values[56]=brawl.elbow(1)*exit;values[57]=brawl.elbow(2)*exit;
   values[58]=brawl.rootPitch()*exit;values[59]=brawl.rootRoll()*exit;values[60]=brawl.rootYaw()*exit;
   // Do not blend a wrapped +/-180 degree spin through zero in the middle of a kick.
   float sign=hand==1?1:-1;
   if(move==CatCombatMoves.KICK)values[60]=sign*Mth.TWO_PI*phase(age,2,13);
   if(move==CatCombatMoves.SWEEP)values[60]=sign*Mth.TWO_PI*phase(age,1,16);
   hipFollow=brawl.hipFollow()*exit;return;
  }
  if(action==DANCE){
   float w=dev.jujutsuvfx.core.YutaDanceMotion.weight(age);
   float sway=dev.jujutsuvfx.core.YutaDanceMotion.step(age),bounce=dev.jujutsuvfx.core.YutaDanceMotion.bounce(age);
   danceWeight=w;
   float tilt=-(float)Math.toRadians(50)*w;
   float bob=(-1.0f-.7f*bounce)*w,side=.25f*sway*w;
   values[7]=tilt;standing(1,12,-10,tilt);standing(0,15,-9,tilt);
   values[7+4]+=bob;values[4]+=bob;values[7+3]=values[3]=side;
   values[7+1]=.035f*sway*w;values[0]=(.12f+.025f*bounce)*w;values[1]=-.03f*sway*w;
   float shoulderY=standY(14.1f,-5,tilt)+bob,shoulderZ=standZ(14.1f,-5,tilt);
   for(int bone=2;bone<=3;bone++){
    float sign=bone==2?1:-1;
    float open=dev.jujutsuvfx.core.YutaDanceMotion.open(age);
    values[bone*7]=-.62f+.045f*bounce;
    values[bone*7+2]=-sign*(.24f+.36f*open);
    values[bone*7+3]=side+sign*(1.25f+.25f*open)*w;
    values[bone*7+4]=shoulderY+.45f*w;
    values[bone*7+5]=shoulderZ-1.1f*w;
    values[bone*7+6]=w;
   }
   values[56]=values[57]=(-2.0f+.08f*bounce)*w;
   // Right paw stays forward and planted; left paw remains tucked behind throughout the bounce.
   for(int bone=4;bone<=5;bone++){
    float sign=bone==4?1:-1;boolean raised=bone==4;
    float angle=(raised?-.42f+.08f*bounce:-1.03f+.08f*bounce)*w,lift=raised?(1.6f+.25f*bounce)*w:0;
    values[bone*7]=angle;values[bone*7+4]=-lift;
    values[bone*7+5]=(raised?3.0f:-1.0f)*w;values[bone*7+3]=sign*.65f*w+side;
      // Move the hip with the torso, but tilt the supporting shin back toward its planted paw.
      values[bone*7+1]=.035f*sway*w;
      values[bone*7+2]=raised?.035f*sway*w:(float)Math.atan2(side,6);
    values[bone*7+6]=w;
   }
   values[6*7]=1.7f*w;values[7*7]=1.5f*w;
   values[6*7+1]=-.09f*sway*w;values[7*7+1]=-.13f*sway*w;
   return;
  }
  if(action==GUARD){
   float w=phase(age,0,4);
   // A horizontal mouth-held blade intercepts ahead of the jaw; all paws brace below the body.
   values[0]=.12f*w;values[1]=-.15f*w;values[2]=-.20f*w;
   values[1*7+4]=.65f*w;values[58]=.05f*w;
   values[2*7]=-.22f*w;values[3*7]=.18f*w;
   values[2*7+2]=-.16f*w;values[3*7+2]=.16f*w;
   values[4*7]=.22f*w;values[5*7]=-.16f*w;
   for(int bone=2;bone<=5;bone++)values[bone*7+6]=w;
   values[56]=values[57]=-.16f*w;values[6*7]=-.18f*w;values[7*7]=.12f*w;
   return;
  }
  if(action==RIKA_SUMMON){
   // Stand on the hind paws; meet both front paws below the face, then slide the ring home.
   float rise=phase(age,0,9)*(1-phase(age,29,36));
   float reach=phase(age,7,15)*(1-phase(age,25,34)),seat=phase(age,15,22);
   ringSlide=.16f*reach*(1-seat);
   float tilt=-(float)Math.toRadians(73)*rise;
   values[7]=tilt;standing(1,12,-10,tilt);standing(0,15,-9,tilt);
   values[0]=.16f*reach;values[1]=-.06f*reach;
   float sy=standY(14.1f,-5,tilt),sz=standZ(14.1f,-5,tilt);
   values[14]=-.60f*rise;values[16]=-.06f*reach;
   values[17]=-.55f*reach;values[18]=sy+.6f*reach;values[19]=sz-1.4f*reach;values[20]=rise;
   values[21]=(-.70f-.18f*seat)*rise;values[23]=.12f*reach;
   values[24]=(.55f-.4f*seat)*reach;values[25]=sy+(.9f-.65f*seat)*reach;
   values[26]=sz-(1.8f-.65f*seat)*reach;values[27]=rise;
   values[56]=-1.45f*reach;values[57]=(-1.45f+.18f*seat)*reach;
   values[28]=values[35]=.08f*rise;values[30]=.12f*rise;values[37]=-.12f*rise;
   values[31]=.3f*rise;values[38]=-.3f*rise;values[34]=values[41]=rise;
   values[42]=1.7f*rise;values[49]=1.35f*rise;
   return;
  }
  if(action==DOMAIN){
   age*=40f/dev.jujutsuvfx.core.YutaDomainRules.POSE_TICKS;
   // Yuta's own seal (ch. 178/249): left fist held to the chest, right palm extended upward.
   // Preserve this asymmetric silhouette on paws; do not substitute Gojo/Sukuna's seal.
   float settle=1-phase(age,32,40),rise=phase(age,0,10)*settle;
   float seal=phase(age,5,14)*settle;
   float tilt=-(float)Math.toRadians(70)*rise;
   values[1*7]=tilt;
   standing(1,12,-10,tilt);standing(0,15,-9,tilt);
   values[0]=-.04f*rise;values[1]=.055f*seal;
   float shoulderY=standY(14.1f,-5,tilt),shoulderZ=standZ(14.1f,-5,tilt);
   // Left elbow opens sideways, then curls the paw back to the lower chest.
   values[2*7]=-.55f;values[2*7+2]=-.22f;
   values[2*7+3]=.9f*seal;values[2*7+4]=shoulderY+1.45f*seal;values[2*7+5]=shoulderZ-.25f*seal;
   values[2*7+6]=seal;values[56]=-2.15f*seal;
   // Right arm is lower and farther forward; rotate the forearm about its long axis.
   values[3*7]=-.62f;values[3*7+2]=.12f;
   values[3*7+3]=-.55f*seal;values[3*7+4]=shoulderY+.35f*seal;values[3*7+5]=shoulderZ-2.5f*seal;
   values[3*7+6]=seal;values[57]=-.95f*seal;values[62]=Mth.PI*seal;
   values[4*7]=values[5*7]=.06f;values[4*7+2]=.09f;values[5*7+2]=-.09f;
   values[4*7+6]=values[5*7+6]=rise;
   values[6*7]=1.8f*rise;values[7*7]=1.5f*rise;
   return;
  }
  if(action==PICK_SWORD){
   float reach=phase(age,0,7)*(1-phase(age,10,18));
   values[0]=.60f*reach;values[1]=-.20f*reach;values[58]=.12f*reach;
   values[1*7+4]=.7f*reach;values[2*7]=-.65f*reach;values[3*7]=.18f*reach;
   values[2*7+6]=values[3*7+6]=reach;values[56]=-.32f*reach;
   values[4*7]=values[5*7]=.25f*reach;values[4*7+6]=values[5*7+6]=reach;
   values[6*7]=-.14f*reach;return;
  }
  if(action==SPEECH){
   float ready=phase(age,0,5),pulse=phase(age,7,8)*(1-phase(age,9,16)),w=ready*(1-phase(age,11,16));
   values[0]=(-.22f*ready+.35f*pulse)*w;values[58]=-.09f*w+.14f*pulse;
   values[1*7+4]=.4f*w;values[2*7]=values[3*7]=-.28f*w;
   values[2*7+2]=-.18f*w;values[3*7+2]=.18f*w;
   values[2*7+6]=values[3*7+6]=w;values[56]=values[57]=-.4f*w;
   values[4*7]=values[5*7]=.22f*w;values[4*7+6]=values[5*7+6]=w;
   values[6*7]=-.16f*w;return;
  }
  if(action==IDLE)return;
  YutaBladeMotion.Pose p=YutaBladeMotion.pose(action,age);
  values[58]=p.pitch();values[59]=p.roll();values[60]=p.yaw();crouch=p.crouch();
  values[0]=p.headPitch();values[1]=p.headYaw();
  float weight=phase(age,0,3)*(1-phase(age,duration(action)-5,duration(action)));
  float dash=action==DRAW?phase(age,34,38)*(1-phase(age,44,48)):0;
  float stride=Mth.lerp(dash,Mth.sin(age*.35f)*.12f*weight,.65f);
  float coil=phase(age,0,impact(action)-2)*(1-phase(age,impact(action),duration(action)));
  for(int bone=2;bone<=5;bone++){
   values[bone*7]=(bone==2||bone==5?1:-1)*stride;
   // SLAM lifts the complete sibling-bone rig through combatRoot. Grounded
   // stance compensation would cancel that lift on every limb and tear the joints away.
   values[bone*7+4]=action==dev.jujutsuvfx.core.YutaAreaMotion.SLAM?0:-crouch*16;
   values[bone*7+6]=weight;
  }
  values[2*7+2]=-.14f*coil;values[3*7+2]=.14f*coil;
  values[4*7+2]=-.12f*weight;values[5*7+2]=.12f*weight;
  if(action==CHOP||action==RISING||action==dev.jujutsuvfx.core.YutaAreaMotion.SLAM){
   float lift=phase(age,3,8)*(1-phase(age,12,20));
   values[2*7]=values[3*7]=-.65f*lift;
   values[56]=values[57]=-.50f*lift;
  }
  if(action==SPIN_CUT||action==dev.jujutsuvfx.core.YutaAreaMotion.SWEEP){values[2*7]=-.5f*weight;values[3*7]=.55f*weight;}
  values[6*7+1]=-p.yaw()*.12f*weight;values[6*7+2]=-p.roll();
 }
 private void sampleLove(int action,float age){
  boolean vow=action==VOW;
  float rise=vow?phase(age,0,15):1-phase(age,277,305);
  float pledge=vow?phase(age,10,25):1;
  float command=vow?0:phase(age,12,38);
  float recoil=vow?0:phase(age,110,115)*(1-phase(age,118,136));
  float breathe=(float)Math.sin(age*.13f)*.08f*rise*(vow?phase(age,22,34)*(1-phase(age,52,70)):phase(age,5,15)*(1-phase(age,250,275)));
  float tilt=-(float)Math.toRadians(73-4*command-3*recoil)*rise;
  finaleStand(rise,tilt,breathe);
  values[0]=(.13f*pledge-.19f*command+.08f*recoil)*rise;
  values[1]=(-.07f*pledge+.07f*command)*rise;
  // Ring-bearing left paw curls toward the face. The right paw first witnesses
  // the vow, then uncurls into a clear forward command while the shoulders stay attached.
  values[14]=(-.60f-.13f*pledge+.12f*command)*rise;
  values[16]=(-.06f-.10f*command)*rise;
  values[56]=(-1.58f*pledge+.25f*command)*rise;
  values[21]=(-.56f-.14f*pledge-.62f*command+.17f*recoil)*rise;
  values[23]=(.08f+.04f*command)*rise;
  values[57]=(-1.55f*pledge+1.30f*command-.22f*recoil)*rise;
  values[61]=-.20f*pledge*rise;values[62]=.48f*command*rise;
  values[42]=(1.70f-.10f*command)*rise;values[49]=1.35f*rise;
 }
 private void sampleGetoFinale(int action,float age){
  float defeat=action==104?0:action==105?phase(age,0,18):1;
  float stagger=action==105?phase(age,0,7)*(1-phase(age,9,25)):0;
  float withdraw=action==106?phase(age,0,28):0;
  float down=action==106?phase(age,34,64):0;
  float charge=action==104?phase(age,0,32):1;
  float recoil=action==104?phase(age,110,116)*(1-phase(age,120,138)):0;
  float breathe=action==104?(float)Math.sin(age*.16f)*.06f*phase(age,3,15)*(1-phase(age,182,200)):0;
  float rise=1-down,tilt=-(float)Math.toRadians(66+3*charge-12*defeat-10*stagger)*rise;
  finaleStand(rise,tilt,breathe);
  values[0]=(-.08f-.10f*charge+.33f*defeat+.15f*stagger)*rise;
  values[1]=(.10f*defeat+.25f*withdraw)*rise;
  // The left arm supports the casting arm. Defeat folds the casting paw back
  // against the chest, then the retreat starts with a protective over-shoulder glance.
  values[14]=(-.63f-.24f*charge+.16f*defeat+.18f*withdraw)*rise;
  values[16]=(-.22f+.12f*withdraw)*rise;
  values[56]=(-1.15f-.16f*charge+.25f*defeat)*rise;
  values[21]=(-.68f-1.37f*charge+.50f*defeat+.42f*withdraw+.12f*recoil)*rise;
  values[23]=(.11f+.20f*defeat)*rise;
  values[57]=(-1.18f+.99f*charge-1.35f*defeat)*rise;
  values[62]=(.48f*charge+.3f*defeat)*rise;
  values[42]=(1.55f-.23f*defeat)*rise;values[49]=1.32f*rise;
  if(action==106){
   float walk=phase(age,52,68)*(1-phase(age,88,100)),step=Mth.sin((age-52)*.55f)*.36f*walk;
   values[14]+=step;values[21]-=step;values[28]-=step;values[35]+=step;
   values[20]=values[27]=values[34]=values[41]=Math.max(rise,walk);
   values[0]+=.22f*walk;values[1]+=.28f*walk;
  }
 }
 private void finaleStand(float rise,float tilt,float bob){
  values[7]=tilt;standing(1,12,-10,tilt);standing(0,15,-9,tilt);
  values[11]+=bob;values[4]+=bob;
  float sy=standY(14.1f,-5,tilt)+bob,sz=standZ(14.1f,-5,tilt);
  for(int bone=2;bone<=3;bone++){
   values[bone*7+4]=sy;values[bone*7+5]=sz;values[bone*7+6]=rise;
  }
  // Hind paws remain on their original floor; widening comes from stance rather
  // than moving the actor root, so the torso and the tail can recoil above them.
  values[31]=.45f*rise;values[38]=-.45f*rise;
  values[34]=values[41]=rise;
 }
 void sampleCopied(int kind,boolean large,float age){
  if(kind==dev.jujutsuvfx.core.CatSpell.BARRAGE){sample(SPEECH,2,age);return;}
  sample(IDLE,2,0);
  brawl.sample(dev.jujutsuvfx.core.CatSpell.pose(kind),2,age,large?1:0);
  for(int bone=0;bone<8;bone++)for(int field=0;field<7;field++)values[bone*7+field]=brawl.value(bone,field);
  values[56]=brawl.elbow(1);values[57]=brawl.elbow(2);values[58]=brawl.rootPitch();values[59]=brawl.rootRoll();values[60]=brawl.rootYaw();hipFollow=brawl.hipFollow();
 }
 private void standing(int bone,float y,float z,float tilt){values[bone*7+4]=standY(y,z,tilt);values[bone*7+5]=standZ(y,z,tilt);}
 private static float standY(float y,float z,float tilt){return 18+(y-18)*Mth.cos(tilt)-(z-5)*Mth.sin(tilt)-y;}
 private static float standZ(float y,float z,float tilt){return 5+(y-18)*Mth.sin(tilt)+(z-5)*Mth.cos(tilt)-z;}
 void blendFrom(YutaCombatPose previous,float weight){
  danceWeight=Mth.lerp(weight,previous.danceWeight,danceWeight);
  crouch=Mth.lerp(weight,previous.crouch,crouch);hipFollow=Mth.lerp(weight,previous.hipFollow,hipFollow);
  for(int i=0;i<60;i++)values[i]=Mth.lerp(weight,previous.values[i],values[i]);
  float delta=Mth.wrapDegrees((values[60]-previous.values[60])*180/Mth.PI)*Mth.PI/180;
  values[60]=previous.values[60]+delta*weight;
  for(int i=61;i<values.length;i++)values[i]=Mth.lerp(weight,previous.values[i],values[i]);
 }
}
