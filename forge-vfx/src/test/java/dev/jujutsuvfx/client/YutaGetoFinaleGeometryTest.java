package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.YutaGetoBeat;
import static dev.jujutsuvfx.client.YutaGetoFinaleGeometry.*;

/** Runs real mesh construction: missing phases, runaway batches, invalid vertices and late beams fail. */
public final class YutaGetoFinaleGeometryTest {
 private static final P RIKA=new P(-10,2.25,0),GETO=new P(10,2.2,0),MIDDLE=new P(0,2.225,0),CAMERA=new P(0,7,24);
 static final class Count implements Sink {
  int vertices,dark,pink;double minX=1e9,maxX=-1e9;float maxAlpha;long fingerprint=1;
  public void vertex(double x,double y,double z,float u,float v,int rgb,float alpha){
   check(Double.isFinite(x+y+z+u+v+alpha),"Non-finite production vertex");
   check(Math.abs(x)<150&&Math.abs(y)<150&&Math.abs(z)<150,"Unbounded geometry");
   check(alpha>=0&&alpha<=1,"Invalid opacity");vertices++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);
   maxAlpha=Math.max(maxAlpha,alpha);
   for(double value:new double[]{x,y,z,u,v,alpha})fingerprint=31*fingerprint+Math.round(value*100000);
   fingerprint=31*fingerprint+rgb;
   int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
   if(Math.max(r,Math.max(g,b))<150)dark++;
   if(r>200&&b>150&&r>g*1.1)pink++;
  }
 }
 private static Count scene(int phase,float age,boolean flashes,boolean solid){
  Count result=new Count();YutaGetoFinaleGeometry.scene(phase,age,RIKA,GETO,MIDDLE,CAMERA,flashes,solid,result);return result;
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int kind:new int[]{0,1})for(float age:new float[]{80,160,6000}){
   Count held=new Count();sustained(kind,age,20,RIKA,new P(1,0,0),CAMERA,true,held);
   sustained(kind,age,20,RIKA,new P(1,0,0),CAMERA,false,held);
   check(held.vertices>100&&held.maxX>=9.8,"Standalone held beam shrinks after the old discharge window");
  }
  Count ended=new Count();playerClash(40,RIKA,GETO,MIDDLE,CAMERA,true,false,false,ended);
  check(ended.vertices==0,"Player collision never expires");
  Count shell=scene(YutaGetoBeat.CHARGE,80,true,true);
  check(shell.dark>500,"Charge must show a dark solid silhouette");
  Count plasma=scene(YutaGetoBeat.CHARGE,80,true,false);
  check(plasma.pink>200,"Charge must show Rika's distinct pink plasma");
  for(int phase:new int[]{YutaGetoBeat.NONE,YutaGetoBeat.MELEE,YutaGetoBeat.RETREAT,YutaGetoBeat.DONE})
   for(boolean solid:new boolean[]{false,true})check(scene(phase,20,true,solid).vertices==0,"Effect escaped finale phase");
  for(int phase:new int[]{YutaGetoBeat.VOW,YutaGetoBeat.CHARGE,YutaGetoBeat.CLASH,YutaGetoBeat.BREAKTHROUGH,YutaGetoBeat.AFTERMATH})
   for(float age:new float[]{0,.5f,10,30,65,89,110}){
    int total=0;
    for(boolean solid:new boolean[]{false,true}){
     Count result=scene(phase,age,true,solid);check(result.vertices%4==0,"Incomplete quad");
     total+=result.vertices;
    }
    check(total<65000,"Scene exceeded bounded vertex budget: "+total);
   }
  Count clash=scene(YutaGetoBeat.CLASH,50,true,false);
  check(clash.minX<-9&&clash.maxX>8,"Opposing exchange does not span both sources");
  Count calm=scene(YutaGetoBeat.BREAKTHROUGH,20,false,false);
  check(calm.maxAlpha<.9f,"Flashes-off must reduce local breakthrough brightness");
  for(boolean solid:new boolean[]{false,true}){
   Count previous=scene(YutaGetoBeat.CLASH,90,true,solid),next=scene(YutaGetoBeat.BREAKTHROUGH,0,true,solid);
   check(previous.vertices==next.vertices&&previous.fingerprint==next.fingerprint,"Clash-to-breakthrough rewinds rotation/flow");
  }
  for(boolean solid:new boolean[]{false,true}){
   check(scene(YutaGetoBeat.AFTERMATH,65,true,solid).vertices==0,"Aftermath never expires");
   Count expired=new Count();vortex(112,48,GETO,new P(-1,0,0),CAMERA,1,solid,expired);
   check(expired.vertices==0,"Original maximum vortex never expires");
   Count released=new Count();vortex(80,32,GETO,new P(-1,0,0),CAMERA,1,solid,released);
   check(released.vertices>100,"Maximum vortex missing discharge");
   check(released.minX>=-22.1,"Maximum vortex renders beyond bounded impact distance");
  }
  Count observer=new Count(),farObserver=new Count(),caster=new Count();
  vortex(28,32,GETO,new P(-1,0,0),GETO.add(new P(0,0,4.1)),1,true,observer);
  vortex(28,32,GETO,new P(-1,0,0),GETO.add(new P(0,0,20)),1,true,farObserver);
  vortex(28,32,GETO,new P(-1,0,0),GETO.add(new P(1.6,.15,0)),1,true,caster);
  check(observer.vertices==farObserver.vertices&&observer.fingerprint==farObserver.fingerprint,
    "Maximum's local camera clearance alters the opaque spectator shell");
  check(observer.vertices==caster.vertices&&observer.fingerprint!=caster.fingerprint,
    "Caster receives no local clearance through the maximum shell");
  // Volumetric mode keeps only bounded lightning/fragments; the shader owns the energy.
  for(int phase:new int[]{YutaGetoBeat.VOW,YutaGetoBeat.CHARGE,YutaGetoBeat.CLASH,YutaGetoBeat.BREAKTHROUGH,YutaGetoBeat.AFTERMATH})
   for(float age:new float[]{0,6,30,60,89})for(boolean solid:new boolean[]{false,true}){
    Count accents=new Count();YutaGetoFinaleGeometry.scene(phase,age,RIKA,GETO,MIDDLE,CAMERA,GETO.y()-2.1,true,solid,true,accents);
    check(accents.vertices%4==0&&accents.vertices<12000,"Unbounded accent geometry");
   }
  Count arcs=new Count();YutaGetoFinaleGeometry.scene(YutaGetoBeat.CLASH,50,RIKA,GETO,MIDDLE,CAMERA,GETO.y()-2.1,true,false,true,arcs);
  check(arcs.vertices>0,"Clash lost its lightning");
  System.out.println("PASS finale geometry: dark/plasma separation, complete phases, finite bounded batches, flash preference and expiry");
 }
}
