package dev.jujutsuvfx.core;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Includes an enclosing target: vanilla AABB.clip alone only reports an entry from outside. */
public final class WorldCutPath {
 public static final double RANGE=48, SPEED=8, WIDTH=30;
 private WorldCutPath(){}
 /** Deterministic +/- 12 degree roll, shared by the hit plane and every observer. */
 public static Vec3 stroke(Vec3 forward,int seed){
  Vec3 right=forward.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.5)right=new Vec3(1,0,0);
  Vec3 up=right.cross(forward).normalize();
  int hash=seed*0x45d9f3b;hash^=hash>>>16;
  double angle=((hash&65535)/65535.0*2-1)*Math.toRadians(12);
  return right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
 }
 /** Exact separating-axis test of this tick's swept blade prism against an entity AABB. */
 public static boolean intersectsBlade(AABB box,Vec3 from,Vec3 to,Vec3 stroke,double width){
  Vec3 delta=to.subtract(from);double length=delta.length();if(length<1e-6)return false;
  Vec3 forward=delta.scale(1/length),normal=forward.cross(stroke).normalize();
  Vec3[] axes={forward,stroke,normal};double[] half={length*.5,width*.5,.32};
  Vec3[] world={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
  Vec3 offset=box.getCenter().subtract(from.add(to).scale(.5));
  double[] extent={box.getXsize()*.5,box.getYsize()*.5,box.getZsize()*.5};
  for(Vec3 a:axes)if(separated(a,offset,axes,half,extent))return false;
  for(Vec3 a:world){
   if(separated(a,offset,axes,half,extent))return false;
   for(Vec3 b:axes)if(separated(a.cross(b),offset,axes,half,extent))return false;
  }
  return true;
 }
 /** Enumerate a thin oriented cut without scanning its entire enclosing 3D volume. */
 public static java.util.List<net.minecraft.core.BlockPos> sweptBlocks(Vec3 from,Vec3 to,Vec3 stroke,double width){
  Vec3 forward=to.subtract(from).normalize();if(forward.lengthSqr()<.9||width<=0)return java.util.List.of();
  Vec3 normal=forward.cross(stroke).normalize(),middle=from.add(to).scale(.5);
  AABB bounds=new AABB(from,to).inflate(Math.abs(stroke.x)*width*.5+Math.abs(normal.x)*.32,
   Math.abs(stroke.y)*width*.5+Math.abs(normal.y)*.32,Math.abs(stroke.z)*width*.5+Math.abs(normal.z)*.32);
  double[] n={normal.x,normal.y,normal.z},c={middle.x,middle.y,middle.z};
  int[] lo={(int)Math.floor(bounds.minX),(int)Math.floor(bounds.minY),(int)Math.floor(bounds.minZ)},
   hi={(int)Math.floor(bounds.maxX),(int)Math.floor(bounds.maxY),(int)Math.floor(bounds.maxZ)};
  int k=Math.abs(n[0])>Math.abs(n[1])?0:1;if(Math.abs(n[2])>Math.abs(n[k]))k=2;
  int a=(k+1)%3,b=(k+2)%3;
  double margin=(.32+.5*(Math.abs(n[a])+Math.abs(n[b])))/Math.abs(n[k])+.5;
  var cells=new java.util.ArrayList<net.minecraft.core.BlockPos>();
  for(int u=lo[a];u<=hi[a];u++)for(int v=lo[b];v<=hi[b];v++){
   double center=c[k]-(n[a]*(u+.5-c[a])+n[b]*(v+.5-c[b]))/n[k];
   int first=Math.max(lo[k],(int)Math.ceil(center-margin-.5-1e-7)),last=Math.min(hi[k],(int)Math.floor(center+margin-.5+1e-7));
   for(int w=first;w<=last;w++){
    int[] xyz=new int[3];xyz[a]=u;xyz[b]=v;xyz[k]=w;
    var pos=new net.minecraft.core.BlockPos(xyz[0],xyz[1],xyz[2]);
    if(intersectsBlade(new AABB(pos),from,to,stroke,width))cells.add(pos);
   }
  }
  return cells;
 }
 private static boolean separated(Vec3 a,Vec3 offset,Vec3[] axes,double[] half,double[] extent){
  double radius=Math.abs(a.x)*extent[0]+Math.abs(a.y)*extent[1]+Math.abs(a.z)*extent[2];
  for(int i=0;i<3;i++)radius+=Math.abs(a.dot(axes[i]))*half[i];
  return Math.abs(a.dot(offset))>radius+1e-7;
 }
 public static boolean intersects(AABB bounds,Vec3 from,Vec3 to){
  AABB expanded=bounds.inflate(.55);
  return expanded.contains(from)||expanded.clip(from,to).isPresent();
 }
}
