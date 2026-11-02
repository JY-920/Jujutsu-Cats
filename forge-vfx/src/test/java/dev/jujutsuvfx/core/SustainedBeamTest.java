package dev.jujutsuvfx.core;

public final class SustainedBeamTest {
 public static void main(String[] args){
  check(!SustainedBeam.firing(69,true),"charge cannot deal beam damage");
  check(SustainedBeam.firing(70,true)&&SustainedBeam.firing(20000,true),"holding must sustain beyond the old finale");
  check(!SustainedBeam.firing(100,false),"release stops the beam");
  check(SustainedBeam.reach(200,48)==48&&SustainedBeam.reach(200,7)==7,"hold must not retract or cross a wall");
  var a=ray(0,0,0,1,0,0,30);var b=ray(20,0,0,-1,0,0,30);
  var hit=SustainedBeam.contact(a,b);check(hit!=null,"head-on contact");
  check(hit.point().x()>1&&hit.point().x()<19,"contact must be between casters");
  check(SustainedBeam.contact(a,ray(10,0,-10,0,0,1,20))!=null,"crossing beams");
  check(SustainedBeam.contact(a,ray(0,1,0,1,0,0,30))==null,"parallel co-directional beams do not clash");
  check(SustainedBeam.contact(ray(0,0,0,1,0,0,5),ray(20,0,0,-1,0,0,5))==null,"unreached or wall-clipped beams");
  check(SustainedBeam.contact(a,ray(20,8,0,-1,0,0,30))==null,"separated heights");
  check(SustainedBeam.contact(ray(0,0,0,0,0,0,30),b)==null,"zero direction rejected");
  var mirrored=SustainedBeam.contact(b,a);check(hit.point().sub(mirrored.point()).length()<1e-6,"caster order does not shift contact");
  // A clips at the near face of a one-block wall; B runs along its far side.
  var clipped=ray(0,0,0,1,0,0,10);var farSide=ray(12,0,-10,0,0,1,20);
  check(SustainedBeam.contact(clipped,farSide)!=null,"finite beam radii reproduce thin-wall proximity");
  java.util.function.Predicate<SustainedBeam.P> wallVisible=p->clearOfWall(clipped.origin(),p)&&clearOfWall(farSide.origin(),p);
  check(SustainedBeam.contact(clipped,farSide,wallVisible)==null,"crossing beams must not clash through a thin wall");
  check(SustainedBeam.contact(farSide,clipped,wallVisible)==null,"wall rejection must not depend on caster order");
  check(SustainedBeam.contact(clipped,farSide,p->true)!=null,"same crossing clashes after the wall is removed");
  System.out.println("Sustained beam hold, reach, finite collision and symmetry checks passed");
 }
 private static SustainedBeam.Ray ray(double x,double y,double z,double dx,double dy,double dz,double length){return new SustainedBeam.Ray(new SustainedBeam.P(x,y,z),new SustainedBeam.P(dx,dy,dz),length);}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static boolean clearOfWall(SustainedBeam.P from,SustainedBeam.P to){return Math.max(from.x(),to.x())<10||Math.min(from.x(),to.x())>11;}
}
