package dev.jujutsuvfx.core;

import java.util.ArrayList;
import java.util.List;

/** Exercises the real grounded path search against small, hand-built terrain fixtures. */
public final class RikaGroundPathTest {
 public static void main(String[] args){
  Terrain flat=new Terrain();
  var owner=new RikaGroundPath.Point(0,0,0);
  var spawned=RikaGroundPath.beside(owner,0,flat);
  check(spawned!=null&&spawned.distance(owner)>=1.8&&spawned.distance(owner)<=3.3,"manifest beside the owner");
  follow(flat,new RikaGroundPath.Point(0,0,0),new RikaGroundPath.Point(10,0,0),false);
  Terrain wall=new Terrain();wall.boxes.add(new Box(3,-2,4,2,4));
  follow(wall,owner,new RikaGroundPath.Point(9,0,0),true);
  Terrain stairs=new Terrain();stairs.boxes.add(new Box(2,-20,4,20,1));stairs.boxes.add(new Box(4,-20,8,20,2));
  var top=follow(stairs,owner,new RikaGroundPath.Point(6,2,0),false);
  check(top.y()==2,"one-block stairs are climbed onto their actual surface");
  Terrain pit=new Terrain();pit.voidFrom=2;
  var stopped=followBounded(pit,owner,new RikaGroundPath.Point(8,0,0),80);
  check(stopped.x()<2&&stopped.y()==0,"a missing floor is never crossed or levitated over");
  Terrain changed=new Terrain();var path=new RikaGroundPath();var p=owner;
  for(int i=0;i<8;i++)p=path.move(p,new RikaGroundPath.Point(9,0,0),.24,.7,changed);
  changed.boxes.add(new Box(2,-20,3,20,4));
  for(int i=0;i<30;i++){var next=path.move(p,new RikaGroundPath.Point(9,0,0),.24,.7,changed);check(next.x()<2,"cached paths recheck newly placed blocks");p=next;}
  Terrain sealed=new Terrain();sealed.boxes.add(new Box(-1,-1,1,1,5));
  check(RikaGroundPath.beside(owner,0,sealed)==null,"cannot manifest from inside obstructed or unsupported owner space");
  System.out.println("Rika ground path: owner spawn, bounded pursuit, wall detour, stairs, void, changed blocks passed");
 }
 private static RikaGroundPath.Point follow(Terrain terrain,RikaGroundPath.Point start,RikaGroundPath.Point goal,boolean detour){
  var path=new RikaGroundPath();var p=start;double largestZ=0;
  for(int i=0;i<180&&p.distance(goal)>.8;i++){
   var next=path.move(p,goal,.24,.7,terrain);
   check(Math.hypot(next.x()-p.x(),next.z()-p.z())<=.240001,"movement cannot jump to the enemy");
   check(terrain.clear(p,next),"every movement segment respects collision");
   check(terrain.ground(next.x(),next.z(),next.y())!=null,"every position has a ground surface");
   largestZ=Math.max(largestZ,Math.abs(next.z()));p=next;
  }
  check(p.distance(goal)<=.81,"reachable enemy must be pursued to contact range: "+p);
  if(detour)check(largestZ>=2,"a wall forces a real detour");return p;
 }
 private static RikaGroundPath.Point followBounded(Terrain terrain,RikaGroundPath.Point start,RikaGroundPath.Point goal,int ticks){
  var path=new RikaGroundPath();var p=start;for(int i=0;i<ticks;i++)p=path.move(p,goal,.24,.7,terrain);return p;
 }
 private record Box(double x0,double z0,double x1,double z1,double top){boolean at(double x,double z){return x>x0&&x<x1&&z>z0&&z<z1;}}
 private static final class Terrain implements RikaGroundPath.Terrain {
  final List<Box> boxes=new ArrayList<>();double voidFrom=Double.POSITIVE_INFINITY;
  public RikaGroundPath.Point ground(double x,double z,double nearY){
   if(x>=voidFrom)return null;double y=0;for(var b:boxes)if(b.at(x,z))y=Math.max(y,b.top);
   return y-nearY<=1.001&&nearY-y<=1.251?new RikaGroundPath.Point(x,y,z):null;
  }
  public boolean clear(RikaGroundPath.Point from,RikaGroundPath.Point to){
   if(Math.abs(to.y()-from.y())>1.251)return false;
   for(int i=0;i<=12;i++){double t=i/12.,x=from.x()+(to.x()-from.x())*t,z=from.z()+(to.z()-from.z())*t;
    if(x>=voidFrom)return false;for(var b:boxes)if(b.at(x,z)&&b.top>Math.max(from.y(),to.y())+.001)return false;
   }return true;
  }
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
