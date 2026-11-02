package dev.jujutsuvfx.core;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public final class WorldCutPathTest {
 public static void main(String[] args){
  if(WorldCutPath.WIDTH!=30)throw new AssertionError("World cut must span exactly thirty blocks");
  if(!WorldCutPath.intersectsBlade(new AABB(14.3,.8,4,14.5,1.2,5),new Vec3(0,1,0),new Vec3(0,1,8),new Vec3(1,0,0),WorldCutPath.WIDTH))throw new AssertionError("Expanded visual wing must also cut targets");
  Vec3 from=new Vec3(0,1,0),to=from.add(0,0,48);
  Vec3 forward=new Vec3(0,0,1),stroke=new Vec3(1,0,0);
  if(!WorldCutPath.intersectsBlade(new AABB(2.5,0,4,3,2,5),from,from.add(0,0,8),stroke,8))throw new AssertionError("Visible blade wing missed target");
  if(WorldCutPath.intersectsBlade(new AABB(5,0,4,6,2,5),from,from.add(0,0,8),stroke,8))throw new AssertionError("Outside blade width hit");
  if(WorldCutPath.intersectsBlade(new AABB(-1,0,10,1,2,11),from,from.add(0,0,8),stroke,8))throw new AssertionError("Target hit before traveling blade arrives");
  // Turning the view must rotate the broad hit corridor, not leave a world-axis box.
  Vec3 diagonal=new Vec3(1,0,1).normalize(),side=new Vec3(-1,0,1).normalize();
  Vec3 contact=from.add(diagonal.scale(5)).add(side.scale(3));
  if(!WorldCutPath.intersectsBlade(new AABB(contact.subtract(.2,.2,.2),contact.add(.2,.2,.2)),from,from.add(diagonal.scale(8)),side,8))throw new AssertionError("Rotated blade wing missed");
  Vec3 outside=from.add(diagonal.scale(5)).add(side.scale(5));
  if(WorldCutPath.intersectsBlade(new AABB(outside.subtract(.2,.2,.2),outside.add(.2,.2,.2)),from,from.add(diagonal.scale(8)),side,8))throw new AssertionError("Rotated broadphase corner incorrectly hit");
  var shoulder=new org.joml.Vector3f(-.375f,.125f,0);
  var wrist=new org.joml.Vector3f(-.375f,10.125f/16f,0);
  var anchor=WorldCutHandPose.matrix(.16f,1).transformPosition(new org.joml.Vector3f(shoulder));
  for(float t=.16f;t<.60f;t+=.01f){
   var m=WorldCutHandPose.matrix(t,1);
   if(m.transformPosition(new org.joml.Vector3f(shoulder)).distance(anchor)>.001f)throw new AssertionError("Shoulder swings around stationary fist");
   if(!m.isFinite())throw new AssertionError("Non-finite hand transform");
  }
  var wind=WorldCutHandPose.matrix(.24f,1).transformPosition(new org.joml.Vector3f(wrist));
  var release=WorldCutHandPose.matrix(.40f,1).transformPosition(new org.joml.Vector3f(wrist));
  if(release.x-wind.x<.55f)throw new AssertionError("Fist must sweep outward, not stay pinned");
  var mirrored=WorldCutHandPose.matrix(.40f,-1).transformPosition(new org.joml.Vector3f(.375f,10.125f/16f,0));
  if(Math.abs(mirrored.x+release.x)>.001f||Math.abs(mirrored.y-release.y)>.001f||Math.abs(mirrored.z-release.z)>.001f)throw new AssertionError("Left-handed release is not mirrored");
  // Optimized thin-plane scan must match brute-force block/prism intersections at any view angle.
  Vec3 start=new Vec3(.23,1.47,.38);
  for(Vec3 dir:new Vec3[]{new Vec3(0,0,1),new Vec3(1,.7,.8).normalize(),new Vec3(0,1,0)}){
   Vec3 end=start.add(dir.scale(8)),cut=WorldCutPath.stroke(dir,731);
   var actual=new java.util.HashSet<>(WorldCutPath.sweptBlocks(start,end,cut,WorldCutPath.WIDTH));
   var expected=new java.util.HashSet<net.minecraft.core.BlockPos>();
   AABB search=new AABB(start,end).inflate(16);
   for(var cursor:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(search.minX,search.minY,search.minZ),net.minecraft.core.BlockPos.containing(search.maxX,search.maxY,search.maxZ))){
    if(WorldCutPath.intersectsBlade(new AABB(cursor),start,end,cut,WorldCutPath.WIDTH))expected.add(cursor.immutable());
   }
   if(!actual.equals(expected))throw new AssertionError("Thin-plane scan leaves holes or breaks extra cells: "+actual.size()+" / "+expected.size());
  }
  boolean positive=false,negative=false;
  for(int seed=0;seed<1000;seed++){
   Vec3 s=WorldCutPath.stroke(forward,seed);
   if(Math.abs(s.y)>.21||Math.abs(s.dot(forward))>.00001||Math.abs(s.length()-1)>.00001)throw new AssertionError("Slash must remain almost horizontal");
   positive|=s.y>.05;negative|=s.y<-.05;
  }
  if(!positive||!negative)throw new AssertionError("Tilt must vary in both directions");
  for(int z:new int[]{2,9,24,47})if(!WorldCutPath.intersects(new AABB(-.5,0,z-.5,.5,2,z+.5),from,to))throw new AssertionError("Piercing path lost target at "+z);
  if(!WorldCutPath.intersects(new AABB(-3,-3,-3,3,3,3),from,to))throw new AssertionError("Enclosing target lost");
  if(WorldCutPath.intersects(new AABB(3,0,20,4,2,21),from,to))throw new AssertionError("Side target hit");
  if(WorldCutPath.intersects(new AABB(-.5,0,50,.5,2,51),from,to))throw new AssertionError("Out of range hit");
  if(WorldCutPath.intersects(new AABB(-.5,0,-4,.5,2,-3),from,to))throw new AssertionError("Target behind hit");
  System.out.println("Space slash: 30-block width, exact thin-plane block scan, shoulder-anchored outward fist, mirrored left hand, swept hits and range checks passed");
 }
}
