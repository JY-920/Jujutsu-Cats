package dev.jujutsuvfx.core;

import java.util.*;

/** Bounded ground navigation shared by temporary and resident Rika. */
public final class RikaGroundPath {
 public record Point(double x,double y,double z){
  public double distance(Point other){return Math.sqrt((x-other.x)*(x-other.x)+(y-other.y)*(y-other.y)+(z-other.z)*(z-other.z));}
 }
 public interface Terrain {
  Point ground(double x,double z,double nearY);
  boolean clear(Point from,Point to);
 }
 private List<Point> route=List.of();
 private Point goal;
 private int repath,index;
 private record Cell(int x,int z,int y){}
 private record Node(Point point,int x,int z,double cost,double estimate,Node parent){}
 public void clear(){route=List.of();goal=null;repath=0;index=0;}
 /** Spawn candidates are all owner-relative and must be connected to the owner's floor. */
 public static Point beside(Point owner,float yaw,Terrain terrain){
  Point floor=terrain.ground(owner.x,owner.z,owner.y);if(floor==null)return null;
  for(double radius:new double[]{2.4,3.1})for(int turn:new int[]{0,180,45,-45,135,-135,90,-90}){
   double angle=Math.toRadians(yaw+turn);
   Point point=terrain.ground(owner.x+Math.cos(angle)*radius,owner.z+Math.sin(angle)*radius,owner.y);
   if(point!=null&&terrain.clear(floor,point))return point;
  }return null;
 }
 public Point move(Point from,Point target,double speed,double stop,Terrain terrain){
  if(speed<=0||from.distance(target)<=stop){clear();return from;}
  if(--repath<=0||goal==null||goal.distance(target)>1.25){
   route=find(from,target,stop,terrain);goal=target;index=0;repath=10;
  }
  while(index<route.size()&&from.distance(route.get(index))<.12)index++;
  if(index>=route.size())return from;
  Point waypoint=route.get(index);double dx=waypoint.x-from.x,dz=waypoint.z-from.z;
  double length=Math.hypot(dx,dz);if(length<1e-6){repath=0;return from;}
  double distance=Math.min(speed,length),scale=distance/length;
  Point next=terrain.ground(from.x+dx*scale,from.z+dz*scale,from.y);
  // Cached routes never bypass a new block, a changed floor or an unloaded chunk.
  if(next==null||!terrain.clear(from,next)){repath=0;return from;}
  return next;
 }
 private static List<Point> find(Point start,Point target,double stop,Terrain terrain){
  Point direct=terrain.ground(target.x,target.z,start.y);
  if(direct!=null&&start.distance(direct)<=24&&terrain.clear(start,direct))return List.of(direct);
  var open=new PriorityQueue<Node>(Comparator.comparingDouble(n->n.cost+n.estimate));
  Map<Cell,Double> costs=new HashMap<>();
  Node first=new Node(start,0,0,0,estimate(start,target,stop),null),best=first;
  open.add(first);costs.put(cell(first),0.);
  int visited=0;
  while(!open.isEmpty()&&visited++<384){
   Node node=open.poll();if(node.cost>costs.getOrDefault(cell(node),Double.POSITIVE_INFINITY)+1e-6)continue;
   if(node.estimate<best.estimate)best=node;
   if(node.point.distance(target)<=Math.max(.8,stop)){best=node;break;}
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
    if(dx==0&&dz==0)continue;int x=node.x+dx,z=node.z+dz;if(x*x+z*z>24*24)continue;
    Point next=terrain.ground(start.x+x,start.z+z,node.point.y);
    if(next==null||!terrain.clear(node.point,next))continue;
    double cost=node.cost+Math.hypot(dx,dz)+Math.abs(next.y-node.point.y)*.35;
    Node candidate=new Node(next,x,z,cost,estimate(next,target,stop),node);Cell key=cell(candidate);
    if(cost>=costs.getOrDefault(key,Double.POSITIVE_INFINITY)-1e-6)continue;
    costs.put(key,cost);open.add(candidate);
   }
  }
  var result=new ArrayList<Point>();for(Node node=best;node.parent!=null;node=node.parent)result.add(node.point);
  Collections.reverse(result);return result;
 }
 private static Cell cell(Node node){return new Cell(node.x,node.z,(int)Math.round(node.point.y*16));}
 private static double estimate(Point from,Point target,double stop){return Math.max(0,Math.hypot(from.x-target.x,from.z-target.z)-stop)+Math.abs(from.y-target.y)*.65;}
}
