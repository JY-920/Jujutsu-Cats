package dev.jujutsuvfx.core;
import java.util.*;
/** Clips posed, textured geometry in object space; no GPU clipping extensions required. */
public final class PlaneCut {
 public static final double EPS=1e-7;
 private PlaneCut(){}
 public record Plane(double x,double y,double z,double d){
  public Plane {double length=Math.sqrt(x*x+y*y+z*z);if(!Double.isFinite(length)||length<1e-8||!Double.isFinite(d))throw new IllegalArgumentException("Invalid cut plane");x/=length;y/=length;z/=length;d/=length;}
  public double distance(Vertex v){return x*v.x+y*v.y+z*v.z-d;}
 }
 public record Point(double x,double y,double z){}
 public static Point boundsCenter(List<List<Vertex>> faces){
  double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
  for(var face:faces)for(var v:face){x0=Math.min(x0,v.x);y0=Math.min(y0,v.y);z0=Math.min(z0,v.z);x1=Math.max(x1,v.x);y1=Math.max(y1,v.y);z1=Math.max(z1,v.z);}
  if(!Double.isFinite(x0+x1+y0+y1+z0+z1))throw new IllegalArgumentException("Empty or non-finite mesh");
  return new Point((x0+x1)*.5,(y0+y1)*.5,(z0+z1)*.5);
 }
 public record Vertex(double x,double y,double z,float u,float v,float nx,float ny,float nz,int r,int g,int b,int a,int light,int overlay){
  public Vertex shifted(double dx,double dy,double dz){return new Vertex(x+dx,y+dy,z+dz,u,v,nx,ny,nz,r,g,b,a,light,overlay);}
  Vertex mix(Vertex other,double t){return new Vertex(lerp(x,other.x,t),lerp(y,other.y,t),lerp(z,other.z,t),(float)lerp(u,other.u,t),(float)lerp(v,other.v,t),(float)lerp(nx,other.nx,t),(float)lerp(ny,other.ny,t),(float)lerp(nz,other.nz,t),(int)Math.round(lerp(r,other.r,t)),(int)Math.round(lerp(g,other.g,t)),(int)Math.round(lerp(b,other.b,t)),(int)Math.round(lerp(a,other.a,t)),light,overlay);}
 }
 private static double lerp(double a,double b,double t){return a+(b-a)*t;}
 private static boolean same(Vertex a,Vertex b){return Math.abs(a.x-b.x)+Math.abs(a.y-b.y)+Math.abs(a.z-b.z)<EPS*4;}
 public static List<Vertex> clip(List<Vertex> polygon,Plane plane,boolean positive){
  if(polygon.size()<3)return List.of();
  if(!positive&&polygon.stream().allMatch(v->Math.abs(plane.distance(v))<=EPS))return List.of();
  List<Vertex> out=new ArrayList<>();Vertex previous=polygon.get(polygon.size()-1);double prev=plane.distance(previous)*(positive?1:-1);
  for(Vertex current:polygon){double next=plane.distance(current)*(positive?1:-1);
   if((prev>=-EPS)!=(next>=-EPS)){double t=prev/(prev-next);append(out,previous.mix(current,Math.max(0,Math.min(1,t))));}
   if(next>=-EPS)append(out,current);previous=current;prev=next;
  }
  if(out.size()>1&&same(out.get(0),out.get(out.size()-1)))out.remove(out.size()-1);
  return out.size()<3?List.of():List.copyOf(out);
 }
 private static void append(List<Vertex> out,Vertex v){if(out.isEmpty()||!same(out.get(out.size()-1),v))out.add(v);}
 /** Closed section loops used to cover the exposed cut faces, preserving gaps between limbs. */
 public static List<List<Vertex>> sections(List<List<Vertex>> faces,Plane plane){
  List<Vertex[]> edges=new ArrayList<>();
  for(var face:faces){List<Vertex> points=new ArrayList<>();boolean above=false,below=false;
   for(int i=0;i<face.size();i++){Vertex a=face.get(i),b=face.get((i+1)%face.size());double da=plane.distance(a),db=plane.distance(b);above|=da>EPS;below|=da < -EPS;
    if(Math.abs(da)<=EPS&&points.stream().noneMatch(v->same(v,a)))points.add(a);
    if(da*db < -EPS*EPS){Vertex hit=a.mix(b,da/(da-db));if(points.stream().noneMatch(v->same(v,hit)))points.add(hit);}
   }
   if((above||below)&&points.size()==2)edges.add(new Vertex[]{points.get(0),points.get(1)});
  }
  List<List<Vertex>> loops=new ArrayList<>();
  while(!edges.isEmpty()){Vertex[] first=edges.remove(edges.size()-1);List<Vertex> loop=new ArrayList<>();loop.add(first[0]);loop.add(first[1]);boolean closed=false;
   for(int guard=0;guard<4096;guard++){Vertex end=loop.get(loop.size()-1);if(same(end,loop.get(0))){loop.remove(loop.size()-1);closed=true;break;}int found=-1;Vertex next=null;
    for(int i=0;i<edges.size();i++){Vertex[] e=edges.get(i);if(same(e[0],end)){found=i;next=e[1];break;}if(same(e[1],end)){found=i;next=e[0];break;}}
    if(found<0)break;edges.remove(found);loop.add(next);
   }
   if(closed&&loop.size()>=3)loops.add(List.copyOf(loop));
  }
  return List.copyOf(loops);
 }
}
