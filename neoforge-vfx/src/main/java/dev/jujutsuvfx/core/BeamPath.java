package dev.jujutsuvfx.core;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import static dev.jujutsuvfx.core.SustainedBeam.P;

/** Shared world-space centreline for rendering, damage and beam/beam contacts. */
public final class BeamPath {
 public static final int SEGMENTS=8,MAX_POINTS=SEGMENTS+1;
 private static final P X=new P(1,0,0),Y=new P(0,1,0),Z=new P(0,0,1);
 private BeamPath(){}
 public static final class Steering {
  private final P[] directions=new P[SEGMENTS];
  private P previousOrigin;
  public List<P> update(P origin,P aim,boolean firing){
   aim=unit(aim);
   boolean reset=!firing||previousOrigin==null||origin.sub(previousOrigin).length()>12;
   List<P> result=new ArrayList<>();result.add(origin);
   for(int i=0;i<SEGMENTS;i++){
    double fraction=i/(double)(SEGMENTS-1),response=1-Math.exp(-1/(4+12*fraction));
    directions[i]=reset?aim:turn(directions[i],aim,response);
    result.add(result.get(i).add(directions[i].mul(SustainedBeam.RANGE/SEGMENTS)));
   }
   previousOrigin=origin;return List.copyOf(result);
  }
 }
 private static P cross(P a,P b){return new P(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
 private static P unit(P v){return v!=null&&v.finite()&&v.length()>1e-8?v.mul(1/v.length()):Z;}
 private static P turn(P from,P to,double amount){
  double cosine=Math.max(-1,Math.min(1,from.dot(to))),angle=Math.acos(cosine);
  if(angle<1e-5)return to;
  P tangent=to.sub(from.mul(cosine));
  if(tangent.length()<1e-6)tangent=cross(from,Math.abs(from.y())<.9?Y:X);
  tangent=unit(tangent);return unit(from.mul(Math.cos(angle*amount)).add(tangent.mul(Math.sin(angle*amount))));
 }
 public static double length(List<P> points){double n=0;for(int i=1;i<points.size();i++)n+=points.get(i).sub(points.get(i-1)).length();return n;}
 public static boolean valid(List<P> points){
  if(points==null||points.isEmpty()||points.size()>MAX_POINTS)return false;
  for(P p:points)if(p==null||!p.finite())return false;
  return length(points)<=SustainedBeam.RANGE+.02;
 }
 public static List<P> prefix(List<P> points,double distance){
  if(points.isEmpty())return List.of();
  List<P> out=new ArrayList<>();out.add(points.get(0));
  for(int i=1;i<points.size()&&distance>1e-7;i++){
   P a=points.get(i-1),delta=points.get(i).sub(a);double span=delta.length();
   if(span<1e-7)continue;
   out.add(a.add(delta.mul(Math.min(1,distance/span))));distance-=span;
  }
  return List.copyOf(out);
 }
 /** Return null from the clipper for a clear segment, otherwise its first obstacle. */
 public static List<P> clip(List<P> points,BiFunction<P,P,P> clipper){
  if(points.isEmpty())return List.of();
  List<P> out=new ArrayList<>();out.add(points.get(0));
  for(int i=1;i<points.size();i++){
   P a=points.get(i-1),b=points.get(i),hit=clipper.apply(a,b);
   if(hit!=null){if(hit.sub(a).length()>1e-7)out.add(hit);break;}out.add(b);
  }
  return List.copyOf(out);
 }
 public static P sample(List<P> points,double distance){
  if(points.isEmpty())return new P(0,0,0);
  for(int i=1;i<points.size();i++){
   P a=points.get(i-1),d=points.get(i).sub(a);double span=d.length();
   if(distance<=span&&span>1e-7)return a.add(d.mul(Math.max(0,distance/span)));distance-=span;
  }
  return points.get(points.size()-1);
 }
 public static List<P> interpolate(List<P> previous,List<P> current,double t){
  if(previous.isEmpty()||current.isEmpty()||previous.get(0).sub(current.get(0)).length()>12)return current;
  t=clamp(t,0,1);double before=length(previous),length=length(current),at=0;List<P> out=new ArrayList<>();
  for(int i=0;i<current.size();i++){
   if(i>0)at+=current.get(i).sub(current.get(i-1)).length();
   out.add(sample(previous,length>1e-7?at/length*before:0).mul(1-t).add(current.get(i).mul(t)));
  }return List.copyOf(out);
 }
 public record Nearest(P point,P tangent,double along,double distance){}
 public static Nearest closest(List<P> points,P point){
  Nearest best=new Nearest(points.get(0),Z,0,point.sub(points.get(0)).length());double along=0;
  for(int i=1;i<points.size();i++){
   P a=points.get(i-1),d=points.get(i).sub(a);double span=d.length();if(span<1e-7)continue;
   P dir=d.mul(1/span);double at=clamp(point.sub(a).dot(dir),0,span);P near=a.add(dir.mul(at));double distance=point.sub(near).length();
   if(distance<=best.distance)best=new Nearest(near,dir,along+at,distance);along+=span;
  }
  return best;
 }
 public static SustainedBeam.Contact contact(List<P> a,List<P> b,BiPredicate<P,P> clear){
  SustainedBeam.Contact best=null;double alongA=0;
  for(int i=1;i<a.size();i++){
   P pa=a.get(i-1),da=a.get(i).sub(pa);double la=da.length();if(la<1e-7)continue;P u=da.mul(1/la);double alongB=0;
   for(int j=1;j<b.size();j++){
    P pb=b.get(j-1),db=b.get(j).sub(pb);double lb=db.length();if(lb<1e-7)continue;P v=db.mul(1/lb);
    double uv=u.dot(v),offsetA=alongA,offsetB=alongB;alongB+=lb;if(uv>.35)continue;
    P r=pa.sub(pb);double ur=u.dot(r),vr=v.dot(r),denom=1-uv*uv,s,t;
    if(denom<1e-7){double axial=pb.sub(pa).dot(u),lo=Math.max(0,axial-lb),hi=Math.min(la,axial);if(hi<lo)continue;s=(lo+hi)*.5;t=clamp(v.dot(pa.add(u.mul(s)).sub(pb)),0,lb);}
    else{s=clamp((uv*vr-ur)/denom,0,la);t=clamp(uv*s+vr,0,lb);s=clamp(uv*t-ur,0,la);t=clamp(uv*s+vr,0,lb);}
    P ca=pa.add(u.mul(s)),cb=pb.add(v.mul(t));
    if(offsetA+s<1||offsetB+t<1||ca.sub(cb).length()>SustainedBeam.RADIUS*2||!clear.test(ca,cb))continue;
    var hit=new SustainedBeam.Contact(offsetA+s,offsetB+t,ca.add(cb).mul(.5));
    if(best==null||hit.alongA()+hit.alongB()<best.alongA()+best.alongB())best=hit;
   }alongA+=la;
  }return best;
 }
 private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
