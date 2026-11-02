package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.BeamPath;
import dev.jujutsuvfx.core.SustainedBeam.P;
import java.util.List;

/** Render actual geometry: collision must not replace an obstacle-following curve with a chord. */
public final class CurvedBeamGeometryTest {
 public static void main(String[] args){
  var rika=List.of(new P(0,0,0),new P(24,0,0),new P(24,0,24));
  var geto=List.of(new P(48,0,0),new P(48,0,24),new P(24,0,24));
  double[] max={0},atRikaBend={100},atGetoBend={100};int[] vertices={0};
  P[] quad={new P(0,0,0)};
  YutaGetoFinaleGeometry.Sink out=(x,y,z,u,v,rgb,alpha)->{
   if(alpha<.01)return;P p=new P(x,y,z);vertices[0]++;
   max[0]=Math.max(max[0],Math.min(BeamPath.closest(rika,p).distance(),BeamPath.closest(geto,p).distance()));
   quad[0]=quad[0].add(p);
   if(vertices[0]%4==0){P centre=quad[0].mul(.25);max[0]=Math.max(max[0],Math.min(BeamPath.closest(rika,centre).distance(),BeamPath.closest(geto,centre).distance()));quad[0]=new P(0,0,0);}
   atRikaBend[0]=Math.min(atRikaBend[0],p.sub(new P(24,0,0)).length());
   atGetoBend[0]=Math.min(atGetoBend[0],p.sub(new P(48,0,24)).length());
  };
  for(boolean solid:new boolean[]{true,false})YutaGetoFinaleGeometry.playerClash(4,
   p(0,0,0),p(48,0,0),p(24,0,24),p(24,16,-10),true,solid,false,out,rika,geto);
  if(vertices[0]<1000)throw new AssertionError("Missing clash beam geometry");
  if(max[0]>9)throw new AssertionError("Clash geometry crossed the empty inside of a bend: "+max[0]);
  if(atRikaBend[0]>3||atGetoBend[0]>3)throw new AssertionError("Clash discarded one of the original bends: "+atRikaBend[0]+", "+atGetoBend[0]);
  System.out.println("Curved clash: "+vertices[0]+" production vertices retained both bends without a source/contact chord");
 }
 private static YutaGetoFinaleGeometry.P p(double x,double y,double z){return new YutaGetoFinaleGeometry.P(x,y,z);}
}
