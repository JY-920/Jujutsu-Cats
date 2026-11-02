package dev.jujutsuvfx.core;
import static dev.jujutsuvfx.core.PlayfulCloudPhysics.*;
public final class PlayfulCloudPhysicsTest {
 public static void main(String[] args){
  Frame shear=new Frame(new Vec(3,4,5),new Vec(2,0,0),new Vec(.4,1,0),new Vec(0,0,.5));
  Vec gripLocal=new Vec(.2,.65,-.1);
  if(shear.local(shear.world(gripLocal)).sub(gripLocal).length()>1e-8)
   throw new AssertionError("camera/root affine transform does not round trip the grip");
  Frame turned=new Frame(new Vec(0,2,0),new Vec(-1,0,0),new Vec(0,-1,0),new Vec(0,0,1));
  Frame middle=frame(0).mix(turned,.5);
  if(Math.abs(middle.x().cross(middle.y()).dot(middle.z()))<.99)
   throw new AssertionError("180-degree turn collapses the simulated grip basis");
  cameraIndependentGrip();
  attackMomentum();
  outwardFlick();
  var rig=new PlayfulCloudPhysics();Frame rest=frame(0);rig.update(0,rest);
  for(int i=1;i<=600;i++){rig.update(i/120.0,rest);checkLengths(rig);}
  if(rig.point(4).y()>rig.point(0).y()-1.5)throw new AssertionError("gravity must hang the free rods: "+rig.point(0)+" to "+rig.point(4));
  Vec tip=rig.point(4);rig.update(5+1/120.0,frame(.6));
  if(Math.abs(rig.point(4).x()-tip.x())>.5)throw new AssertionError("free tip must lag behind a moved grip");
  var a=motion(30);var b=motion(144);
  if(a.point(4).sub(b.point(4)).length()>.18)throw new AssertionError("render FPS alters swing "+a.point(4).sub(b.point(4)).length());
  Vec before=a.point(4);a.update(2,frame(0));a.update(2,frame(0));
  if(before.sub(a.point(4)).length()>1e-7)throw new AssertionError("duplicate render pass advances physics");
  // Shadow and preview passes may use a slightly older partial tick. They must not rewind/reset the world rig.
  var delayed=motion(120);Vec delayedTip=delayed.point(4);
  delayed.update(2-.5/20,frame(-.5));
  if(delayed.point(4).sub(delayedTip).length()>1e-9)throw new AssertionError("out-of-order render pass rewinds the world rig");
  a.update(20,frame(10000));checkLengths(a);
  if(a.point(4).sub(a.point(0)).length()>2.4)throw new AssertionError("teleport must reset the rig");
  if(a.history().size()>18)throw new AssertionError("bounded trail history");
  System.out.println("Playful Cloud physics: gravity, inertial lag, rigid lengths, frame rate, repeated passes, teleport and bounded trail, camera-independent roots and stale pass protection passed");
 }
 static void outwardFlick(){
  var rig=new PlayfulCloudPhysics();
  Frame f=new Frame(new Vec(0,2,0),new Vec(1,0,0),new Vec(0,0,-1),new Vec(0,1,0));
  for(int i=0;i<=240;i++)rig.update(i/120.0,f);
  double reach=0;
  for(int i=1;i<=36;i++){rig.update(2+i/120.0,f,1);checkLengths(rig);reach=Math.max(reach,-rig.point(4).sub(rig.point(0)).z());}
  if(reach<1.5)throw new AssertionError("Free rods stay hanging during outward flick: reach="+reach);
  System.out.println("Outward flick free-tip reach="+reach);
 }
 static void attackMomentum(){
  PlayfulCloudPhysics passive=strike(120,0),driven=strike(120,1);
  double separation=passive.point(4).sub(driven.point(4)).length();
  if(separation<.4)throw new AssertionError("strike must visibly carry the free tip through the swing: "+separation);
  PlayfulCloudPhysics low=strike(30,1),high=strike(144,1);
  double fpsDifference=low.point(4).sub(high.point(4)).length();
  if(fpsDifference>.30)throw new AssertionError("driven strike changes with render FPS: "+fpsDifference);
  System.out.println("Strike momentum separation="+separation+", 30/144 fps tip delta="+fpsDifference);
 }
 static PlayfulCloudPhysics strike(int fps,double power){
  var rig=new PlayfulCloudPhysics();
  for(int i=0;i<=fps*2;i++)rig.update(i/(double)fps,frame(0));
  for(int i=1;i<=fps/2;i++){
   double t=i/(double)fps,angle=Math.min(1,t/.16)*Math.PI*.8;
   Frame f=new Frame(new Vec(.4*Math.sin(angle),2,0),new Vec(Math.cos(angle),Math.sin(angle),0),
    new Vec(-Math.sin(angle),Math.cos(angle),0),new Vec(0,0,1));
   rig.update(2+t,f,t<.25?power:0);checkLengths(rig);
  }
  return rig;
 }
 static void cameraIndependentGrip(){
  Vec worldRoot=new Vec(1000000,64,-1000000);
  Frame hand=new Frame(new Vec(.45,1.1,-.2),new Vec(.8,0,-.6),new Vec(0,-1,0),new Vec(-.6,0,-.8));
  Frame expected=new Frame(worldRoot.add(hand.origin()),hand.x(),hand.y(),hand.z());
  // Vanilla view, orbit camera, scaled GUI preview and an unrelated shadow camera.
  for(Frame root:new Frame[]{frame(0),new Frame(new Vec(-5,-2,8),new Vec(0,0,-1),new Vec(0,1,0),new Vec(1,0,0)),
      new Frame(new Vec(150,90,0),new Vec(40,0,0),new Vec(8,-40,0),new Vec(0,0,40)),
      new Frame(new Vec(20,-5,7),new Vec(0,1,0),new Vec(-1,0,0),new Vec(0,0,1))}){
   Frame rendered=new Frame(root.world(hand.origin()),direction(root,hand.x()),direction(root,hand.y()),direction(root,hand.z()));
   Frame actual=Frame.relative(root,rendered,worldRoot);
   for(Vec local:new Vec[]{new Vec(0,0,0),new Vec(0,.69,0),new Vec(-1,2,1)})
    if(actual.world(local).sub(expected.world(local)).length()>1e-7)
     throw new AssertionError("observer/shadow camera moves the physical grip");
  }
 }
 static Vec direction(Frame f,Vec v){return f.x().mul(v.x()).add(f.y().mul(v.y())).add(f.z().mul(v.z()));}
 static PlayfulCloudPhysics motion(int fps){var p=new PlayfulCloudPhysics();for(int i=0;i<=2*fps;i++){
  double t=(double)i/fps;double x=t<1?Math.sin(t*Math.PI*2)*.7:0;p.update(t,frame(x));checkLengths(p);
 }return p;}
 static Frame frame(double x){return new Frame(new Vec(x,2,0),new Vec(1,0,0),new Vec(0,1,0),new Vec(0,0,1));}
 static void checkLengths(PlayfulCloudPhysics p){for(int i=1;i<=4;i++){
  double actual=p.point(i).sub(p.point(i-1)).length(),expected=i%2==1?PlayfulCloudMotion.CHAIN_LENGTH:PlayfulCloudMotion.ROD_LENGTH;
  if(!Double.isFinite(actual)||Math.abs(actual-expected)>.006)throw new AssertionError("rig stretch "+i+": "+actual);
 }}
}
