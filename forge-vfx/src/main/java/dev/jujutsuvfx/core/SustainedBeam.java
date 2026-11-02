package dev.jujutsuvfx.core;

/** Standalone held beams have no screenplay winner, retreat, or automatic dissolve. */
public final class SustainedBeam {
 public static final int GETO=0,LOVE=1,CHARGE=70,USE_TICKS=72000,HIT_INTERVAL=10,CLASH_TICKS=12,CLASH_END=40;
 public static final double RANGE=48,SPEED=5,RADIUS=1.6;
 private SustainedBeam(){}
 public static boolean firing(int age,boolean held){return held&&age>=CHARGE;}
 public static double reach(double age,double limit){return Math.max(0,Math.min(Math.min(RANGE,Math.max(0,limit)),(age-CHARGE)*SPEED));}
 public record P(double x,double y,double z){
  public P add(P b){return new P(x+b.x,y+b.y,z+b.z);} public P sub(P b){return new P(x-b.x,y-b.y,z-b.z);}
  public P mul(double n){return new P(x*n,y*n,z*n);} public double dot(P b){return x*b.x+y*b.y+z*b.z;}
  public double length(){return Math.sqrt(dot(this));} public boolean finite(){return Double.isFinite(x+y+z);}
 }
 public record Ray(P origin,P direction,double length){}
 public record Contact(double alongA,double alongB,P point){}
 /** Finite radii can touch across a wall even when both centerlines were clipped. */
 public static Contact contact(Ray a,Ray b,java.util.function.Predicate<P> clearContact){
  Contact hit=contact(a,b);return hit!=null&&clearContact.test(hit.point)?hit:null;
 }
 public static Contact contact(Ray a,Ray b){
  if(!valid(a)||!valid(b))return null;
  P u=a.direction.mul(1/a.direction.length()),v=b.direction.mul(1/b.direction.length());
  double uv=u.dot(v);if(uv>.35)return null;
  P offset=a.origin.sub(b.origin);double ur=u.dot(offset),vr=v.dot(offset),denom=1-uv*uv;
  double s,t;
  if(denom<1e-7){
   // Opposing collinear rays have infinitely many closest points. Choose the
   // middle of the overlapping *live* intervals, not either caster's muzzle.
   double along=b.origin.sub(a.origin).dot(u);
   double lo=Math.max(0,along-b.length),hi=Math.min(a.length,along);
   if(hi<lo)return null;s=(lo+hi)*.5;t=clamp(v.dot(a.origin.add(u.mul(s)).sub(b.origin)),0,b.length);
  }else{
   s=clamp((uv*vr-ur)/denom,0,a.length);t=clamp(uv*s+vr,0,b.length);
   s=clamp(uv*t-ur,0,a.length);t=clamp(uv*s+vr,0,b.length);
  }
  P pa=a.origin.add(u.mul(s)),pb=b.origin.add(v.mul(t));
  if(s<1||t<1||pa.sub(pb).length()>RADIUS*2)return null;
  return new Contact(s,t,pa.add(pb).mul(.5));
 }
 private static boolean valid(Ray ray){return ray.origin.finite()&&ray.direction.finite()&&Double.isFinite(ray.length)&&ray.length>0&&ray.length<=RANGE&&ray.direction.length()>.99&&ray.direction.length()<1.01;}
 private static double clamp(double value,double lo,double hi){return Math.max(lo,Math.min(hi,value));}
}
