package dev.jujutsuvfx.core;
import java.util.*;
public final class PlaneCutTest {
 public static void main(String[] args) {
  var plane=new PlaneCut.Plane(1,0,0,0);
  var quad=List.of(v(-1,-1,0,0),v(1,-1,1,0),v(1,1,1,1),v(-1,1,0,1));
  var a=PlaneCut.clip(quad,plane,true);var b=PlaneCut.clip(quad,plane,false);
  close(2,area(a));close(2,area(b));
  for(var v:a) {if(v.x() < -1e-7)throw new AssertionError("Wrong positive half");if(Math.abs(v.x())<1e-7)close(.5,v.u());}
  for(var v:b)if(v.x()>1e-7)throw new AssertionError("Wrong negative half");
  Random random=new Random(7);
  for(int i=0;i<500;i++){
   var p=new PlaneCut.Plane(random.nextDouble()-.5,random.nextDouble()-.5,0,random.nextDouble()-.5);
   close(area(quad),area(PlaneCut.clip(quad,p,true))+area(PlaneCut.clip(quad,p,false)));
  }
  if(!PlaneCut.clip(quad,new PlaneCut.Plane(0,0,1,0),false).isEmpty())throw new AssertionError("Coplanar face duplicated");
  try {new PlaneCut.Plane(0,0,0,0);throw new AssertionError("Zero plane accepted");}catch(IllegalArgumentException expected){}
  var cube=cube(0);var loops=PlaneCut.sections(cube,new PlaneCut.Plane(1,0,0,0));
  if(loops.size()!=1||loops.get(0).size()!=4)throw new AssertionError("Cube cut not closed");
  close(4,areaYZ(loops.get(0)));
  var limbs=new ArrayList<>(cube);limbs.addAll(cube(5));
  if(PlaneCut.sections(limbs,new PlaneCut.Plane(1,0,0,0)).size()!=2)throw new AssertionError("Separated limbs bridged");
  if(!PlaneCut.sections(cube,new PlaneCut.Plane(1,0,0,2)).isEmpty())throw new AssertionError("Outside cap created");
  var diagonal=new PlaneCut.Plane(1,1,1,.2);var section=PlaneCut.sections(cube,diagonal);
  if(section.size()!=1||section.get(0).size()!=6)throw new AssertionError("Diagonal cube section invalid");
  for(var v:section.get(0))close(0,diagonal.distance(v));
  var moved=cube.stream().map(f->f.stream().map(v->v.shifted(17,-9,12)).toList()).toList();
  var center=PlaneCut.boundsCenter(moved);close(17,center.x());close(-9,center.y());close(12,center.z());
  var centered=moved.stream().map(f->f.stream().map(v->v.shifted(-center.x(),-center.y(),-center.z())).toList()).toList();
  if(PlaneCut.sections(centered,new PlaneCut.Plane(1,0,0,0)).size()!=1)throw new AssertionError("Moving target lost cut section");
  System.out.println("PlaneCut: complementary halves, UV interpolation, 500 cuts, coplanar ownership and closed/disjoint 3D caps and moving mesh recentering passed");
 }
 static List<List<PlaneCut.Vertex>> cube(double y){
  var vertices=new ArrayList<PlaneCut.Vertex>();
  for(int i=0;i<8;i++)vertices.add(new PlaneCut.Vertex((i&1)==0?-1:1,y+((i&2)==0?-1:1),(i&4)==0?-1:1,0,0,1,0,0,255,255,255,255,0,0));
  int[][] faces={{0,2,3,1},{4,5,7,6},{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3}};
  return Arrays.stream(faces).map(f->Arrays.stream(f).mapToObj(vertices::get).toList()).toList();
 }
 static double areaYZ(List<PlaneCut.Vertex> v){double a=0;for(int i=0;i<v.size();i++){var p=v.get(i);var q=v.get((i+1)%v.size());a+=p.y()*q.z()-p.z()*q.y();}return Math.abs(a)*.5;}
 static PlaneCut.Vertex v(double x,double y,double u,double v){return new PlaneCut.Vertex(x,y,0,(float)u,(float)v,0,0,1,255,255,255,255,15728880,0);}
 static double area(List<PlaneCut.Vertex> v){double a=0;for(int i=0;i<v.size();i++){var p=v.get(i);var q=v.get((i+1)%v.size());a+=p.x()*q.y()-p.y()*q.x();}return Math.abs(a)*.5;}
 static void close(double a,double b){if(Math.abs(a-b)>1e-6)throw new AssertionError(a+" != "+b);}
}
