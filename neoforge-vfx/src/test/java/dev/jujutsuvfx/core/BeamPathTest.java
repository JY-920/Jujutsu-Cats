package dev.jujutsuvfx.core;

import java.util.List;
import static dev.jujutsuvfx.core.SustainedBeam.P;

public final class BeamPathTest {
 public static void main(String[] args) {
  P zero=new P(0,0,0),forward=new P(0,0,1),right=new P(1,0,0);
  BeamPath.Steering steering=new BeamPath.Steering();
  var straight=steering.update(zero,forward,false);
  check(Math.abs(BeamPath.length(straight)-48)<1e-6,"steady beam retains its full range");
  var curved=steering.update(zero,right,true);
  P near=curved.get(1).sub(curved.get(0)),far=curved.get(8).sub(curved.get(7));
  check(near.x()/near.length()>far.x()/far.length()+.1,"far end must turn more slowly than muzzle");
  check(far.z()/far.length()>.8,"turning cannot instantly put the far end on the crosshair");
  check(Math.abs(BeamPath.length(curved)-48)<1e-6,"turning must not stretch the range");
  P onCurve=curved.get(5);
  check(BeamPath.closest(curved,onCurve).distance()<1e-6,"hit follows visible curved beam");
  check(BeamPath.closest(curved,new P(30,0,0)).distance()>5,"old crosshair line must not still deal damage");
  var wall=BeamPath.clip(curved,(a,b)->a.z()<12&&b.z()>=12?a.add(b.sub(a).mul((12-a.z())/(b.z()-a.z()))):null);
  check(wall.get(wall.size()-1).z()<=12.00001&&BeamPath.length(wall)<48,"first wall truncates the whole remaining path");
  check(BeamPath.length(BeamPath.prefix(curved,7))<7.000001,"growing front cannot hit beyond its reach");
  for(int n=0;n<200;n++)curved=steering.update(zero,right,true);
  check(curved.get(8).sub(new P(48,0,0)).length()<.01,"beam catches up when camera stops");
  for(int n=0;n<40;n++)curved=steering.update(zero,new P(-1,0,0),true);
  check(curved.stream().allMatch(P::finite)&&BeamPath.length(curved)>47.99,"opposite and vertical turns stay finite");
  var teleported=steering.update(new P(100,0,0),forward,true);
  check(teleported.get(8).sub(new P(100,0,48)).length()<.001,"teleport resets trailing directions");
  check(BeamPath.interpolate(curved,teleported,0).equals(teleported),"client must snap on teleport, not sweep a detached beam across the world");
  for(int n=0;n<40;n++)curved=steering.update(new P(100,0,0),new P(0,1,0),true);
  check(curved.stream().allMatch(P::finite),"vertical camera turns stay finite");
  var a=List.of(zero,new P(10,0,0),new P(10,0,10));
  var b=List.of(new P(20,0,7),new P(0,0,7));
  var contact=BeamPath.contact(a,b,(pa,pb)->true);
  check(contact!=null&&contact.alongA()>10,"collision follows bent path, not source-to-tip chord");
  var stopped=BeamPath.prefix(a,contact.alongA());
  check(stopped.contains(new P(10,0,0))&&BeamPath.closest(stopped,new P(10,0,4)).distance()<.001,"clash must retain the bend before contact");
  check(BeamPath.contact(a,b,(pa,pb)->false)==null,"wall between finite-width beams blocks collision");
  check(!BeamPath.valid(List.of(zero,new P(Double.NaN,0,0))),"invalid snapshot rejected");
  check(!BeamPath.valid(List.of(zero,new P(49,0,0))),"oversized snapshot rejected");
  System.out.println("Beam steering, curved hit path, wall clipping, opposite turns and collision passed");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
