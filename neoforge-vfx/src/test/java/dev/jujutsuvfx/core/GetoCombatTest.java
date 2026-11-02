package dev.jujutsuvfx.core;
public final class GetoCombatTest {
 public static void main(String[] args)throws Exception{
  GetoMeleeSequenceTest.main(args);
  java.lang.reflect.Method reach;
  try{reach=GetoCloudMotion.class.getMethod("inReach",int.class,double.class,double.class,double.class,double.class);}
  catch(NoSuchMethodException e){throw new AssertionError("Committed staff strikes lack directional reach",e);}
  if(!(boolean)reach.invoke(null,204,2.5,0.0,.6,.3))throw new AssertionError("Visible forward thrust misses");
  if((boolean)reach.invoke(null,204,-2.5,0.0,.6,.3))throw new AssertionError("Forward thrust hits an escaped rear target");
  if((boolean)reach.invoke(null,204,2.0,2.0,.6,.3))throw new AssertionError("Forward thrust hits a lateral dodge");
  if((boolean)reach.invoke(null,200,2.0,0.0,4.0,.3))throw new AssertionError("Grounded sweep hits far above its model");
  if((boolean)reach.invoke(null,200,-2.0,0.0,.6,.3))throw new AssertionError("Frontal sweep hits behind its committed arc");
  if(!(boolean)reach.invoke(null,205,-2.0,0.0,.6,.3))throw new AssertionError("Full spin loses its rear arc");
  var silhouettes=new java.util.HashSet<String>();
  for(int a=200;a<=206;a++){
   if(!GetoCloudMotion.active(a))throw new AssertionError("Missing selectable staff choreography "+a);
   int hits=0;for(int t=0;t<=GetoCloudMotion.DURATION;t++)if(GetoCloudMotion.contact(a,t))hits++;
   if(hits!=1)throw new AssertionError("Each staff stroke must hit once");
   var first=GetoCloudMotion.pose(a,0);var end=GetoCloudMotion.pose(a,GetoCloudMotion.DURATION);
   if(Math.abs(first.headYaw()-end.headYaw())>.001)throw new AssertionError("Pose does not recover");
   var contact=GetoCloudMotion.pose(a,GetoCloudMotion.impact(a));
   silhouettes.add(String.format(java.util.Locale.ROOT,"%.2f/%.2f/%.2f/%.2f",contact.headYaw(),contact.headPitch(),contact.bodyYaw(),contact.bodyPitch()));
   for(float t=0;t<30;t+=.05f){var p=GetoCloudMotion.pose(a,t);if(!Float.isFinite(p.headYaw()+p.headPitch()+p.bodyYaw()+p.bodyPitch()+p.stride()))throw new AssertionError("Invalid pose");}
   double travel=0;for(float t=1.5f;t<=30;t+=1.5f)travel+=GetoCloudMotion.travel(a,t)-GetoCloudMotion.travel(a,t-1.5f);
   if(travel<.25||travel>1.1)throw new AssertionError("Staff step must be bounded, positive and independent of skipped frames");
  }
  if(silhouettes.size()!=7)throw new AssertionError("Seven staff actions reuse the same contact silhouette");
  int pixel=0xff00ff00; // Native green eyes must become purple, not remain green under multiply tint.
  int recolored=SpiritRules.colorizeAbgr(pixel,0),rr=recolored&255,gg=recolored>>8&255,bb=recolored>>16&255;
  if(!(bb>gg&&rr>gg)||(recolored>>>24)!=255)throw new AssertionError("Texture hue filter missing");
  if((SpiritRules.colorizeAbgr(0x0000ff00,0)>>>24)!=0)throw new AssertionError("Lost cutout alpha");
  if(GetoVortexMotion.reaches(10,0,.3f,GetoVortexMotion.FIRE,48))throw new AssertionError("Damage before visible beam");
  if(GetoVortexMotion.reaches(12,0,.3f,GetoVortexMotion.FIRE+2,48))throw new AssertionError("Damage ahead of beam");
  if(!GetoVortexMotion.reaches(10,0,.3f,GetoVortexMotion.FIRE+2,48))throw new AssertionError("Beam misses its visible front");
  if(GetoVortexMotion.reaches(8,0,.3f,GetoVortexMotion.FIRE+5,7))throw new AssertionError("Beam reaches through wall");
  if(GetoVortexMotion.reaches(10,0,.3f,GetoVortexMotion.END,48))throw new AssertionError("Lingering beam still damages");
  if(GetoVortexMotion.radius(28)<=GetoVortexMotion.radius(58)*2)throw new AssertionError("Missing compression");
  for(float age=0;age<115;age+=.1f){
   if(!Float.isFinite(GetoVortexMotion.radius(age))||GetoVortexMotion.fade(age)<0||GetoVortexMotion.fade(age)>1)throw new AssertionError("Vortex invalid");
  }
  java.util.Set<Integer> roles=new java.util.HashSet<>();for(int i=0;i<100;i++)roles.add(SpiritRules.attackRole(i));
  if(!roles.contains(SpiritRules.AIR)||!roles.contains(SpiritRules.ASSAULT))throw new AssertionError("Predictable attack summons");

  for(int move=200;move<=206;move++)for(int fps:new int[]{30,60,144}){
   PlayfulCloudPhysics rig=new PlayfulCloudPhysics();rig.ground(.04);
   for(int step=0;step<fps*4;step++){
    double now=step/(double)fps;float age=(float)(now*20%54);
    var pose=GetoCloudMotion.pose(move,age);double yaw=pose.headYaw()+pose.bodyYaw();
    var frame=new PlayfulCloudPhysics.Frame(new PlayfulCloudPhysics.Vec(0,1,0),new PlayfulCloudPhysics.Vec(0,.93,0),new PlayfulCloudPhysics.Vec(Math.cos(yaw)*.93,0,Math.sin(yaw)*.93),new PlayfulCloudPhysics.Vec(Math.sin(yaw)*.93,0,-Math.cos(yaw)*.93));
    rig.update(now,frame,GetoCloudMotion.drive(move,age));
    for(int i=1;i<5;i++){
     double expected=(i%2==1?PlayfulCloudMotion.CHAIN_LENGTH:PlayfulCloudMotion.ROD_LENGTH)*.93;
     if(Math.abs(rig.point(i).sub(rig.point(i-1)).length()-expected)>.0001||rig.point(i).y()<.039||!Double.isFinite(rig.point(i).length()))throw new AssertionError("Mouth rig length/floor failed at "+fps+" fps");
    }
   }
  }
  System.out.println("Geto: one hit per swing, continuous mouth motion, texture hue/alpha and travelling vortex timing and 30/60/144-fps grounded physical chain passed");
 }
}
