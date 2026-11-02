package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.GetoVortexMotion;
import dev.jujutsuvfx.core.YutaGetoBeat;

/**
 * Loader-independent world geometry. Solid curse matter and soft light are emitted
 * separately; the caller batches each pass once and keeps the scene depth buffer.
 * No particles/entities are allocated and the loops have fixed upper bounds.
 */
final class YutaGetoFinaleGeometry {
 private static final double TAU=Math.PI*2;
 private static final P Y=new P(0,1,0),X=new P(1,0,0);
 private static final int CURSE=0,VOID=2,RIBBON=4,PLASMA=6,HALO=8,SPARK=10,HEART=12,EYE=14,BLOOM=16;
 // The original Maximum uses modes 0–8 unchanged. Duel modes sample local turbulent volumes.
 private static final int CURSE_CLOUD=18,LOVE_CLOUD=20,LOVE_STREAM=22,CURSE_STREAM=24,PRESSURE=26,FILAMENT=28,IMPACT=30,PINK_WING=32;
 record P(double x,double y,double z) {
  P add(P b){return new P(x+b.x,y+b.y,z+b.z);}
  P add(double a,double b,double c){return new P(x+a,y+b,z+c);}
  P sub(P b){return new P(x-b.x,y-b.y,z-b.z);}
  P mul(double s){return new P(x*s,y*s,z*s);}
  double length(){return Math.sqrt(x*x+y*y+z*z);}
  P unit(){double n=length();return n<1e-7?X:mul(1/n);}
  P cross(P b){return new P(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
  P mix(P b,double t){return mul(1-t).add(b.mul(t));}
 }
 interface Sink { void vertex(double x,double y,double z,float u,float v,int rgb,float alpha); }
 private record Frame(P axis,P right,P up){
  static Frame along(P direction){
   P axis=direction.unit(),right=axis.cross(Y);
   if(right.length()<.01)right=X;
   right=right.unit();return new Frame(axis,right,right.cross(axis).unit());
  }
  P radial(double angle){return right.mul(Math.cos(angle)).add(up.mul(Math.sin(angle)));}
  P at(P origin,double axial,double radius,double angle){return origin.add(axis.mul(axial)).add(radial(angle).mul(radius));}
 }
 private YutaGetoFinaleGeometry(){}
 /** Held player casts reuse the same materials without the screenplay collapse. */
 static void sustained(int kind,float age,double length,P source,P direction,P camera,boolean solid,Sink out){
  sustained(kind,age,length,source,direction,camera,solid,out,java.util.List.of());
 }
 static void sustained(int kind,float age,double length,P source,P direction,P camera,boolean solid,Sink out,java.util.List<dev.jujutsuvfx.core.SustainedBeam.P> path){
  if(!Float.isFinite(age)||age<0)return;
  Frame f=Frame.along(direction);float charge=smooth(age,0,70),fade=smooth(age,0,10);
  double reach=Math.max(0,Math.min(length,(age-70)*5));P end=source.add(f.axis.mul(reach));
  float near=1-smooth(camera.sub(source).length(),2.2,5),sourceFade=1-near*(age>=70?1f:.85f);
  Sink beam=followPath(out,source,f,path);
  if(kind==1){
   if(solid)return;
   loveCharge(out,source,f,camera,age,.15+.65*charge,fade*sourceFade);
   if(reach>0)loveBeam(beam,source,end,f,camera,age,.98,fade,1);
  }else if(solid){
   curseCloud(out,source,f,age,.4+1.4*charge,fade*sourceFade,true,camera);
   if(reach>0)curseStream(beam,source,end,camera,age,1,fade,1);
  }else curseCorona(out,source,f,camera,age,.4+1.4*charge,fade*.6f*sourceFade);
 }
 private static Sink followPath(Sink out,P source,Frame f,java.util.List<dev.jujutsuvfx.core.SustainedBeam.P> path){
  if(path.size()<2)return out;
  return (x,y,z,u,v,rgb,alpha)->{
   P offset=new P(x,y,z).sub(source);double along=offset.x*f.axis.x+offset.y*f.axis.y+offset.z*f.axis.z;
   var centre=dev.jujutsuvfx.core.BeamPath.sample(path,along);
   var next=dev.jujutsuvfx.core.BeamPath.sample(path,along+.08);
   var before=dev.jujutsuvfx.core.BeamPath.sample(path,along-.08);
   Frame at=Frame.along(new P(next.x()-before.x(),next.y()-before.y(),next.z()-before.z()));
   double right=offset.x*f.right.x+offset.y*f.right.y+offset.z*f.right.z,up=offset.x*f.up.x+offset.y*f.up.y+offset.z*f.up.z;
   P warped=new P(centre.x(),centre.y(),centre.z()).add(at.right.mul(right)).add(at.up.mul(up));
   out.vertex(warped.x,warped.y,warped.z,u,v,rgb,alpha);
  };
 }
 /** Brief even collision, then explode at the contact; neither source is a loser. */
 static void playerClash(float age,P rika,P geto,P center,P camera,boolean flashes,boolean solid,boolean accents,Sink out){
  playerClash(age,rika,geto,center,camera,flashes,solid,accents,out,java.util.List.of(),java.util.List.of());
 }
 static void playerClash(float age,P rika,P geto,P center,P camera,boolean flashes,boolean solid,boolean accents,Sink out,
    java.util.List<dev.jujutsuvfx.core.SustainedBeam.P> rikaPath,java.util.List<dev.jujutsuvfx.core.SustainedBeam.P> getoPath){
  if(age<0||age>=40)return;
  Frame rf=Frame.along(center.sub(rika)),gf=Frame.along(center.sub(geto));
  P rikaEnd=rikaPath.size()>1?rika.add(rf.axis.mul(dev.jujutsuvfx.core.BeamPath.length(rikaPath))):center;
  P getoEnd=getoPath.size()>1?geto.add(gf.axis.mul(dev.jujutsuvfx.core.BeamPath.length(getoPath))):center;
  float fade=1-smooth(age,12,20),brightness=flashes?1:.66f;
  if(solid){
   if(!accents&&fade>.005){curseCloud(out,geto,gf,age+180,1.8,fade,true,camera);curseStream(followPath(out,geto,gf,getoPath),geto,getoEnd,camera,age+180,1,fade,1);}
   if(age<20)clashDebris(out,center,rf,age+180,fade);
   if(age>=12)fragments(out,center,rf,age-6,camera);
   return;
  }
  if(!accents&&fade>.005){
   loveCharge(out,rika,rf,camera,age+180,.7,fade*brightness);
   loveBeam(followPath(out,rika,rf,rikaPath),rika,rikaEnd,rf,camera,age+180,1,fade*brightness,1);
   collision(out,center,rf,camera,age+180,1.25+smooth(age,0,12)*.5,fade*brightness,false);
  }
  if(age<20)for(int i=0;i<6;i++)lightning(out,center,rf.at(center,.1,2.5+i*.2,i*2.4+age*.02),camera,age,i,.025,0xFFD6F4,fade*brightness);
  if(age>=12&&!accents)shockwave(out,center,rf,camera,center.y-2,age-1,brightness);
 }
 private static float smooth(double t,double a,double b){double x=Math.max(0,Math.min(1,(t-a)/(b-a)));return (float)(x*x*(3-2*x));}

 static void scene(int phase,float age,P rika,P geto,P middle,P camera,boolean flashes,boolean solid,Sink out) {
  scene(phase,age,rika,geto,middle,camera,geto.y-2.1,flashes,solid,out);
 }
 static void scene(int phase,float age,P rika,P geto,P middle,P camera,double groundY,boolean flashes,boolean solid,Sink out) {
  scene(phase,age,rika,geto,middle,camera,groundY,flashes,solid,false,out);
 }
 /** With the full-screen volume active only crisp lightning and solid curse fragments remain geometry. */
 static void scene(int phase,float age,P rika,P geto,P middle,P camera,double groundY,boolean flashes,boolean solid,boolean accents,Sink out) {
  if(phase<YutaGetoBeat.VOW||phase>YutaGetoBeat.AFTERMATH||!Float.isFinite(age)||age<0)return;
  if(phase==YutaGetoBeat.AFTERMATH&&age>=65)return;
  Frame rf=Frame.along(geto.sub(rika)),gf=Frame.along(rika.sub(geto));
  float brightness=flashes?1:.66f;
  if(accents){accents(phase,age,rika,geto,middle,camera,rf,gf,brightness,solid,out);return;}
  if(phase==YutaGetoBeat.VOW){
   float gather=smooth(age,20,70);
   if(solid){if(gather>.01f)curseCloud(out,geto,gf,age,.35+gather*.65,gather*.72f,false,camera);}
   else if(gather>.01f)loveCharge(out,rika,rf,camera,age,.18+gather*.25,gather*brightness);
   return;
  }
  if(phase==YutaGetoBeat.CHARGE){
   float gather=smooth(age,0,100);
   if(solid)curseCloud(out,geto,gf,age+70,1+gather*1.15,1,false,camera);
   else{
    loveCharge(out,rika,rf,camera,age+70,.43+gather*.57,brightness);
    curseCorona(out,geto,gf,camera,age+70,1+gather*1.15,.6f*brightness);
   }
   return;
  }
  if(phase==YutaGetoBeat.CLASH||phase==YutaGetoBeat.BREAKTHROUGH){
   boolean breaking=phase==YutaGetoBeat.BREAKTHROUGH;
   float time=age+(breaking?270:180);
   float pressure=breaking?1:smooth(age,0,90);
   // The same published contact point drives both beams, camera focus and server impact.
   P knot=middle;
   float release=breaking?1:smooth(age,0,8);
   float collapse=breaking?1-smooth(age,11,32):1;
   float end=breaking?1-smooth(age,29,40):1;
   double rr=.92+pressure*.18;
   if(solid){
    if(collapse>.01){
     curseCloud(out,geto,gf,time,2.15*collapse,collapse,true,camera);
     curseStream(out,geto,knot,camera,time,.98*collapse,collapse,release);
    }
    if(release>.01)clashDebris(out,knot,rf,time,release*end);
    if(breaking)fragments(out,geto,gf,age,camera);
   }else{
    loveCharge(out,rika,rf,camera,time,.72,end*brightness);
    loveBeam(out,rika,knot,rf,camera,time,rr,end*brightness,release);
    if(collapse>.01)curseCorona(out,geto,gf,camera,time,1.8*collapse,collapse*brightness*.6f);
    if(release>.01)collision(out,knot,rf,camera,time,1.18+pressure*.46,release*end*brightness,breaking);
    if(breaking)shockwave(out,geto,rf,camera,groundY,age,brightness);
   }
   return;
  }
  if(!solid){
   float fade=1-smooth(age,0,65);
   aftermath(out,geto,rf,camera,groundY,age,fade*brightness);
   if(age<20)loveCharge(out,rika,rf,camera,age+310,.28,fade*.35f*brightness);
  }
 }

 private static void accents(int phase,float age,P rika,P geto,P middle,P camera,Frame rf,Frame gf,float brightness,boolean solid,Sink out){
  if(phase==YutaGetoBeat.BREAKTHROUGH){
   if(solid){fragments(out,geto,gf,age,camera);return;}
  }
  if(solid)return;
  float charge=switch(phase){
   case YutaGetoBeat.VOW->smooth(age,20,70)*.45f;
   case YutaGetoBeat.CHARGE->.45f+.55f*smooth(age,0,100);
   case YutaGetoBeat.CLASH->1;
   case YutaGetoBeat.BREAKTHROUGH->1-smooth(age,29,40);
   default->0;
  };
  float time=age+(phase==YutaGetoBeat.CHARGE?70:phase==YutaGetoBeat.CLASH?180:phase==YutaGetoBeat.BREAKTHROUGH?270:0);
  // Bolts flicker on short epochs: each epoch picks new paths so arcs snap instead of sliding.
  int epoch=(int)Math.floor(time/2.2);
  if(charge>.02f){
   double radius=.45+1.45*charge;
   for(int i=0;i<6;i++){
    int seed=epoch*31+i*7;if(fract(seed*.6180339887)>.35+.5*charge)continue;
    double a=fract(seed*.381966)*TAU,b=fract(seed*.7236)*2-1;
    P direction=rf.radial(a).mul(Math.sqrt(1-b*b)).add(rf.axis.mul(b));
    P start=rika.add(direction.mul(radius*.32)),end=rika.add(direction.mul(radius*(1.1+.9*fract(seed*.53))));
    lightning(out,start,end,camera,epoch*2.2f,seed,.012+.010*charge,0xFF8FE0,charge*brightness);
   }
  }
  float contact=switch(phase){
   case YutaGetoBeat.CLASH->smooth(age,0,8)*(.55f+.45f*smooth(age,0,90));
   case YutaGetoBeat.BREAKTHROUGH->1-smooth(age,11,32);
   default->0;
  };
  if(contact>.02f){
   double radius=(.95+1.05*(phase==YutaGetoBeat.CLASH?smooth(age,0,90):1))*contact;
   for(int i=0;i<10;i++){
    int seed=epoch*17+i*13;if(fract(seed*.6180339887)>.7)continue;
    double a=fract(seed*.381966)*TAU,b=(fract(seed*.7236)*2-1)*.45;
    P direction=rf.radial(a).mul(Math.sqrt(1-b*b)).add(rf.axis.mul(b));
    P start=middle.add(direction.mul(radius*.3)),end=middle.add(direction.mul(radius*(2.2+2.6*fract(seed*.53))));
    lightning(out,start,end,camera,epoch*2.2f,seed,.016+.010*contact,0xFF9CE6,contact*brightness);
   }
   // Arcs crawling along Rika's beam toward the contact.
   double length=middle.sub(rika).length();
   for(int i=0;i<4;i++){
    int seed=epoch*23+i*5;double t0=fract(seed*.618);
    P start=rika.mix(middle,t0*.85).add(rf.radial(fract(seed*.31)*TAU).mul(.5));
    P end=rika.mix(middle,Math.min(1,t0*.85+2.5/Math.max(1,length))).add(rf.radial(fract(seed*.77)*TAU).mul(.7));
    lightning(out,start,end,camera,epoch*2.2f,seed+3,.010,0xFFB8EF,contact*.8f*brightness);
   }
  }
 }

 /** Same geometry for the original player/NPC Maximum Uzumaki; combat timing stays unchanged. */
 static void vortex(float age,double length,P origin,P direction,P camera,double scale,boolean solid,Sink out) {
  float fade=GetoVortexMotion.fade(age);if(fade<=.001f||!Float.isFinite(age)||scale<=0)return;
  P zero=new P(0,0,0);Frame frame=Frame.along(direction);
  P localCamera=camera.sub(origin).mul(1/scale);
  float nearCaster=solid?1-smooth(localCamera.length()*scale,1.9,4):0;
  Sink transformed=(x,y,z,u,v,rgb,a)->{
   double wx=origin.x+x*scale,wy=origin.y+y*scale,wz=origin.z+z*scale;
   if(nearCaster>.001f){
    // First-person casting starts only 1.6m from the eye. Open a local near-camera
    // clearance through the solid mass; observers outside 4m retain the opaque shell.
    double dx=wx-camera.x,dy=wy-camera.y,dz=wz-camera.z;
    a*=1-nearCaster+nearCaster*smooth(Math.sqrt(dx*dx+dy*dy+dz*dz),2.1,4.2);
   }
   out.vertex(wx,wy,wz,u,v,rgb,a);
  };
  double release=smooth(age,GetoVortexMotion.FIRE,GetoVortexMotion.FIRE+8);
  double radius=GetoVortexMotion.radius(age)*.87;
  if(solid){
   curse(transformed,zero,frame,age,radius*(1-.6*release),fade,false,localCamera);
   if(age>=GetoVortexMotion.FIRE){
    double reach=GetoVortexMotion.reach(age,Math.max(0,length))/scale;
    if(reach>.01)curseBeam(transformed,zero,frame.axis.mul(reach),frame,age,1.25,fade,1);
   }
  }else{
   curseLight(transformed,zero,frame,age,radius,fade*.7f);
   double reach=GetoVortexMotion.reach(age,Math.max(0,length))/scale;
   if(reach>.01){
    P end=frame.axis.mul(reach);
    for(int i=0;i<4;i++){
     double at=(age-GetoVortexMotion.FIRE)*GetoVortexMotion.SPEED/scale-i*4;
     if(at>0&&at<reach)ring(transformed,frame.axis.mul(at),frame.right,frame.up,1.32+i*.1,.065,0xC9B7AD,fade*.35f,age,HALO);
    }
    double impact=age-GetoVortexMotion.FIRE-length/GetoVortexMotion.SPEED;
    if(impact>=0&&impact<18){
     float f=(float)(1-impact/18);
     ring(transformed,end,frame.right,frame.up,.7+impact*.24,.08+.12*f,0xE9CFC0,fade*f,age,HALO);
    }
   }
  }
 }

 /** A recessed face surrounded by seven overlapping, genuinely round wound ropes. */
 private static void curse(Sink out,P source,Frame f,float time,double radius,float alpha,boolean firing,P camera){
  if(radius<.03||alpha<.005)return;
  P center=source.sub(f.axis.mul(radius*.22));
  ellipsoid(out,center.sub(f.axis.mul(radius*.28)),f,radius*.89,radius*.89,radius*.64,0x2D2B2A,alpha,time,CURSE,12,24);
  // A black recessed socket stays visible between the tangled shell's inner loops.
  ellipsoid(out,source.add(f.axis.mul(radius*.39)),f,radius*.39,radius*.45,radius*.08,0x050407,alpha,time,VOID,8,24);
  for(int strand=0;strand<7;strand++){
   P[] points=new P[43];double offset=strand*TAU/7;
   for(int i=0;i<points.length;i++){
    double t=i/(double)(points.length-1);
    double a=offset+t*TAU*1.18-time*.037;
    double r=radius*(1.03-.65*t)*(1+.055*Math.sin(t*31+strand*2.3+time*.055));
    double axial=radius*(-.55+1.04*t+.08*Math.sin(t*16+strand));
    points[i]=f.at(center,axial,r,a);
   }
   tubePath(out,points,radius*(.105+.012*(strand%3)),7,0x77716B,alpha,time*.02+strand*.41,CURSE);
  }
  // A serrated lip makes the front read as a mouth rather than a glowing energy ball.
  for(int tooth=0;tooth<13;tooth++){
   double a=tooth*TAU/13-time*.014,rad=radius*.43;
   P base=f.at(source,radius*.46,rad,a),side=f.radial(a+Math.PI/2).mul(radius*.042);
   P tip=f.at(source,radius*(.50+.045*Math.sin(tooth)),rad-radius*(.12+.055*Math.sin(tooth*2.4)),a);
   quad(out,base.sub(side),base.add(side),tip,tip,0x8D8278,alpha,0,1,CURSE);
  }
  for(int strand=0;strand<8;strand++){
   double a=strand*TAU/8-time*.023;P[] path=new P[13];
   for(int i=0;i<path.length;i++){
    double t=i/12.0,r=radius*(.75+.85*t),angle=a+t*.8+.13*Math.sin(t*8+time*.06+strand);
    path[i]=f.at(center,-radius*(.2+.7*t),r,angle);
   }
   taperedPath(out,path,radius*.09,4,0x353232,alpha*.94f,time*.04+strand,CURSE);
  }
 }

 private static void curseLight(Sink out,P source,Frame f,float time,double radius,float alpha){
  if(radius<.03||alpha<.005)return;
  // Sparse blood-red slits are carried by the shell, never a purple luminous center.
  for(int i=0;i<4;i++){
   double a=i*TAU/4-time*.037+1.2;P c=f.at(source,radius*.51,radius*.55,a);
   P right=f.radial(a+Math.PI/2).mul(radius*.15),up=f.radial(a).mul(radius*.024);
   quad(out,c.sub(right).sub(up),c.add(right).sub(up),c.add(right).add(up),c.sub(right).add(up),0xEC354A,alpha,0,1,EYE);
  }
 }

 private static void curseBeam(Sink out,P source,P target,Frame f,float time,double radius,float alpha,float release){
  double length=target.sub(source).length()*release;if(length<.02||radius<.02)return;
  P end=source.add(f.axis.mul(length));
  // Round rope sections tilt with the helix tangent; clip their end to the same
  // physical impact plane instead of allowing their cross-section past the hit.
  Sink bounded=(x,y,z,u,v,rgb,a)->{
   double along=(x-source.x)*f.axis.x+(y-source.y)*f.axis.y+(z-source.z)*f.axis.z;
   double excess=Math.max(0,along-length);
   out.vertex(x-f.axis.x*excess,y-f.axis.y*excess,z-f.axis.z*excess,u,v,rgb,a);
  };
  beamTube(bounded,source,end,f,radius*.74,0x292627,alpha,time,CURSE,18,12);
  for(int strand=0;strand<6;strand++){
   P[] path=new P[25];
   for(int i=0;i<path.length;i++){
    double t=i/24.0,angle=strand*TAU/6+t*length*1.55-time*.32;
    double r=radius*(.87+.10*Math.sin(t*23+strand+time*.13));
    path[i]=f.at(source,t*length,r,angle);
   }
   tubePath(bounded,path,radius*.19,6,strand%2==0?0x817870:0x635B58,alpha,time*.11+strand,CURSE);
  }
 }

 /** A turbulent absorbing cloud with fine wound tendrils, separate from Maximum's solid shell. */
 private static void curseCloud(Sink out,P source,Frame f,float time,double radius,float alpha,boolean firing,P camera){
  if(radius<.03||alpha<.005)return;
  volume(out,source.sub(f.axis.mul(radius*.12)),camera,radius*1.42,radius*1.42,time,0x5C404C,alpha,CURSE_CLOUD);
  // Irregular dark streamlines dissolve across their width. No polygon rope sections.
  for(int k=0;k<8;k++){
   P[] path=new P[27];double offset=k*2.399963;
   for(int i=0;i<path.length;i++){
    double t=i/26.0,a=offset+t*3.8-time*.027;
    double r=radius*(.25+1.12*t)*(1+.11*Math.sin(t*19+k*2.7+time*.04));
    path[i]=f.at(source,radius*(.15-.62*t),r,a);
   }
   energyRibbon(out,path,camera,radius*.075,0x4E3342,alpha*.43f,time+k*6,CURSE_STREAM,true);
  }
 }
 private static void curseCorona(Sink out,P source,Frame f,P camera,float time,double radius,float alpha){
  if(radius<.03||alpha<.005)return;
  for(int k=0;k<5;k++){
   P[] path=new P[31];double start=k*2.399963-time*.022;
   for(int i=0;i<path.length;i++){
    double t=i/30.0,a=start+t*2.6,r=radius*(.55+.48*t+.06*Math.sin(t*18+time*.08+k));
    path[i]=f.at(source,radius*(.15-.3*t),r,a);
   }
   energyRibbon(out,path,camera,.014+radius*.004,0xD85A8A,alpha*.4f,time+k*9,FILAMENT,true);
  }
 }
 private static void curseStream(Sink out,P source,P target,P camera,float time,double radius,float alpha,float release){
  if(radius<.02||alpha<.005||release<=.001)return;
  P end=source.mix(target,release);if(end.sub(source).length()<.01)return;
  volumeBeam(out,source,end,camera,radius*1.55,time,0x684152,alpha,CURSE_STREAM);
 }
 private static void loveCharge(Sink out,P source,Frame f,P camera,float time,double strength,float alpha){
  if(alpha<.005||strength<.01)return;
  double radius=.38+strength*1.15;
  bloom(out,source,camera,radius*2.65,0xEB1AAE,alpha*.12f);
  volume(out,source,camera,radius*1.65,radius*1.65,time,0xFF4AD1,alpha,LOVE_CLOUD);
  // Unequal thin paths converge into the gas. Brightness resides in filaments, not a white shell.
  for(int k=0;k<9;k++){
   P[] path=new P[33];double offset=k*2.399963;
   for(int i=0;i<path.length;i++){
    double t=i/32.0,a=offset+(1-t)*1.8+time*.018;
    double r=radius*(.12+(1-t)*1.55),axial=radius*(.18*Math.sin(t*6+k)-.28*(1-t));
    path[i]=f.at(source,axial,r,a);
   }
   energyRibbon(out,path,camera,.010+strength*.008,k%3==0?0xFFF3FE:0xFF70DB,alpha*.64f,time+k*7,FILAMENT,true);
  }
  for(int i=0;i<5;i++){
   double a=i*2.399963+time*.017;
   P start=f.at(source,.05,radius*.32,a),end=f.at(source,.22*Math.sin(time*.04+i),radius*(1.6+.28*Math.sin(i*3)),a+.48);
   lightning(out,start,end,camera,time,i,.009+.005*strength,0xFFB8EF,alpha*.74f);
  }
  for(int i=0;i<52;i++){
   double cycle=fract(i*.6180339887+time*.012),a=i*2.399963+cycle*1.8+time*.014;
   double r=radius*(.25+1.8*(1-cycle));
   P point=f.at(source,Math.sin(i*7.3+time*.02)*radius*(1-cycle)*.4,r,a);
   spark(out,point,camera,(.010+.025*fract(i*.71))*Math.max(.35,strength),i%4==0?0xFF69D3:0xFFE5F6,(float)(Math.sin(cycle*Math.PI)*alpha*.85));
  }
 }
 private static void loveBeam(Sink out,P source,P target,Frame ignored,P camera,float time,double radius,float alpha,float release){
  if(alpha<.005||release<=.001)return;
  P end=source.mix(target,release);double length=end.sub(source).length();if(length<.01)return;
  Frame f=Frame.along(end.sub(source));
  volumeBeam(out,source,end,camera,radius*2.15,time,0xFF42C5,alpha,LOVE_STREAM);
  for(int k=0;k<7;k++){
   P[] path=new P[35];
   for(int i=0;i<path.length;i++){
    double t=i/34.0,a=k*2.399963+t*length*(.20+.04*(k%3))-time*.19;
    double r=radius*(.48+.44*fract(k*.61)+.14*Math.sin(t*19-time*.24+k));
    path[i]=f.at(source,t*length,r,a);
   }
   energyRibbon(out,path,camera,k<3?.021:.011,k<3?0xFFEFFA:0xFF91E2,alpha*.62f,time+k*5,FILAMENT,false);
  }
  for(int k=0;k<14;k++){
   double t=fract(time*.065+k*.618),a=k*2.399963+time*.021,r=radius*(.4+fract(k*.71)*.9);
   P at=f.at(source,t*length,r,a);P endPoint=at.add(f.axis.mul(Math.min(.8+1.1*fract(k*.31),length*(1-t))));
   energyRibbon(out,new P[]{at,endPoint},camera,.010,0xFFB3E8,alpha*.74f,time+k,FILAMENT,false);
  }
 }
 private static void collision(Sink out,P point,Frame f,P camera,float time,double radius,float alpha,boolean breakthrough){
  bloom(out,point,camera,radius*3.8,0xF623B2,alpha*.18f);
  volume(out,point,camera,radius*2.35,radius*2.35,time,0xFF61D7,alpha,IMPACT);
  P upper=point.add(f.up.mul(radius*.76)).sub(f.axis.mul(radius*.16));
  volume(out,upper,camera,radius*1.85,radius*1.92,time+11,0xFF5BCB,alpha*.90f,PINK_WING);
  P lower=point.sub(f.up.mul(radius*.68)).sub(f.axis.mul(radius*.34));
  volume(out,lower,camera,radius*1.70,radius*1.30,time+39,0xFF6BCE,alpha*.73f,PINK_WING);
  for(int i=0;i<3;i++){
   double cycle=fract(time*.037+i*.333),r=radius*(.8+cycle*2.35);
   P right=f.right.add(f.axis.mul((i-1)*.25)).unit();
   pressure(out,point.add(f.axis.mul((i-1)*.12)),right,f.up,r,radius*(.25+.25*cycle),0xFF94DD,
     (float)(Math.sin(cycle*Math.PI)*alpha*.55),time+i*8);
  }
  for(int i=0;i<13;i++){
   double a=i*2.399963+time*.011;
   P end=f.at(point,.85*Math.sin(i*5+time*.03),radius*(1.8+1.7*fract(i*.61)),a);
   lightning(out,point.add(f.radial(a).mul(radius*.3)),end,camera,time+40,i+13,.012,0xFFB5E9,alpha*.87f);
  }
  for(int i=0;i<70;i++){
   double t=fract(time*.038+i*.618),a=i*2.399963+time*.016;
   P p=f.at(point,Math.sin(i*1.8)*t*1.8,radius*(.45+3.2*t),a);
   spark(out,p,camera,.009+.027*(1-t),i%3==0?0xF66BCA:0xFFE6F5,(float)((1-t)*alpha*.8));
  }
 }
 private static void shockwave(Sink out,P point,Frame f,P camera,double groundY,float age,float alpha){
  double growth=smooth(age,10,40);if(growth<=.001)return;
  float fade=1-smooth(age,31,46);
  P ground=new P(point.x,groundY,point.z);
  for(int i=0;i<2;i++){
   double r=Math.max(0,growth*12-i*1.8);if(r<=.02)continue;
   pressure(out,ground.add(0,.18+i*.22,0),X,new P(0,0,1),r,.55+(1-growth)*.8,0xFFC8E8,alpha*fade*(.5f-i*.14f),age+i*11);
  }
  if(age>19){
   double cloud=smooth(age,19,34)*(1-smooth(age,33,43));
   volume(out,point,camera,cloud*5.2,cloud*5.2,age+300,0xFF66CB,alpha*.48f*fade,IMPACT);
   pressure(out,point,f.right,f.up,growth*6,.38,0xFFD5EE,alpha*.4f*fade,age+71);
  }
 }
 private static void aftermath(Sink out,P source,Frame f,P camera,double groundY,float age,float alpha){
  P ground=new P(source.x,groundY,source.z);
  for(int i=0;i<2;i++)pressure(out,ground.add(0,.18+i*.22,0),X,new P(0,0,1),12+age*.075-i*1.8,.6,0xED9DCD,alpha*(.23f-i*.07f),age+43+i*11);
  for(int i=0;i<43;i++){
   double a=i*2.399963,rad=1.2+fract(i*.618)*6,fall=age*.016;
   P point=source.add(Math.cos(a)*rad,1.8+Math.sin(i*3)*1.6-fall,Math.sin(a)*rad);
   spark(out,point,camera,.009+fract(i*.31)*.028,0xFFB7DF,alpha*.65f);
  }
 }
 /** View-aligned volume bounds. The fragment shader integrates density along the local depth. */
 private static void volume(Sink out,P center,P camera,double width,double height,float time,int rgb,float alpha,int mode){
  if(width<.01||alpha<.005)return;
  Frame f=Frame.along(camera.sub(center));P r=f.right.mul(width),u=f.up.mul(height);
  float base=(float)(Math.floor(time*8)*2);
  quadUV(out,center.sub(r).sub(u),center.sub(r).add(u),center.add(r).add(u),center.add(r).sub(u),rgb,alpha,base,base+1,mode,mode+1);
 }
 private static void volumeBeam(Sink out,P start,P end,P camera,double radius,float time,int rgb,float alpha,int mode){
  P axis=end.sub(start),side=axis.cross(camera.sub(start.mix(end,.5))).unit().mul(radius);
  float base=(float)(Math.floor(time*8)*2);
  // Subdivide before a player's centreline warp; one long quad would cut across its bends.
  int spans=Math.max(1,(int)Math.ceil(axis.length()/2));
  for(int i=0;i<spans;i++){
   float t=i/(float)spans,next=(i+1)/(float)spans;P a=start.mix(end,t),b=start.mix(end,next);
   quadUV(out,a.sub(side),a.add(side),b.add(side),b.sub(side),rgb,alpha,base+t,base+next,mode,mode+1);
  }
 }
 private static void energyRibbon(Sink out,P[] path,P camera,double width,int rgb,float alpha,float time,int mode,boolean taper){
  float base=(float)(Math.floor(time*8)*2);
  for(int i=0;i<path.length-1;i++){
   double t=i/(double)(path.length-1),nt=(i+1)/(double)(path.length-1);
   P side=path[i+1].sub(path[i]).cross(camera.sub(path[i])).unit();
   double w=width*(taper?Math.sin(Math.PI*(.035+.93*t)):1),nw=width*(taper?Math.sin(Math.PI*(.035+.93*nt)):1);
   quadUV(out,path[i].sub(side.mul(w)),path[i].add(side.mul(w)),path[i+1].add(side.mul(nw)),path[i+1].sub(side.mul(nw)),rgb,alpha,
     base+(float)t,base+(float)nt,mode,mode+1);
  }
 }
 private static void pressure(Sink out,P center,P right,P up,double radius,double width,int rgb,float alpha,float time){
  if(radius<.02||alpha<.005)return;float base=(float)(Math.floor(time*8)*2);
  for(int i=0;i<96;i++){
   double a=i*TAU/96,b=(i+1)*TAU/96;
   double ra=radius*(1+.024*Math.sin(a*5+time*.12)+.016*Math.sin(a*11-time*.08));
   double rb=radius*(1+.024*Math.sin(b*5+time*.12)+.016*Math.sin(b*11-time*.08));
   double wa=width*(.72+.28*Math.sin(a*7+time*.09)),wb=width*(.72+.28*Math.sin(b*7+time*.09));
   P pa=right.mul(Math.cos(a)).add(up.mul(Math.sin(a))),pb=right.mul(Math.cos(b)).add(up.mul(Math.sin(b)));
   quadUV(out,center.add(pa.mul(ra-wa)),center.add(pa.mul(ra+wa)),center.add(pb.mul(rb+wb)),center.add(pb.mul(rb-wb)),rgb,alpha,
     base+i/96f,base+(i+1)/96f,PRESSURE,PRESSURE+1);
  }
 }

 private static void fragments(Sink out,P source,Frame f,float age,P camera){
  float amount=smooth(age,5,15)*(1-smooth(age,29,40));if(amount<=.005)return;
  for(int i=0;i<12;i++){
   double t=Math.max(0,age-5)*.07,a=i*2.399;
   P c=f.at(source,-t*.3,t*(1+fract(i*.51)),a).add(0,-t*t*.12,0);
   ellipsoid(out,c,f,.07+.12*fract(i*.71),.09+.12*fract(i*.41),.16,0x49413E,amount,age+i,CURSE,4,6);
  }
 }
 private static void clashDebris(Sink out,P point,Frame f,float time,float alpha){
  for(int i=0;i<8;i++){
   double cycle=fract(time*.012+i*.618),angle=i*2.399+time*.012;
   P c=f.at(point,Math.sin(i*3.1)*cycle*.8,.9+cycle*3.1,angle).add(0,-cycle*cycle*.5,0);
   float fade=(float)(Math.sin(cycle*Math.PI)*alpha);
   ellipsoid(out,c,f,.035+fract(i*.7)*.06,.04+fract(i*.21)*.07,.08,0x75616B,fade,time+i,CURSE,4,6);
  }
 }

 private static void beamTube(Sink out,P source,P target,Frame f,double radius,int rgb,float alpha,float time,int mode,int steps,int sides){
  double length=target.sub(source).length();
  for(int i=0;i<steps;i++)for(int j=0;j<sides;j++){
   double t=i/(double)steps,nt=(i+1)/(double)steps,a=j*TAU/sides,b=(j+1)*TAU/sides;
   double r=radius*(.82+.18*Math.sin(t*Math.PI))*(1+.04*Math.sin(t*44-time*.3));
   double nr=radius*(.82+.18*Math.sin(nt*Math.PI))*(1+.04*Math.sin(nt*44-time*.3));
   quadUV(out,f.at(source,t*length,r,a),f.at(source,t*length,r,b),f.at(source,nt*length,nr,b),f.at(source,nt*length,nr,a),
     rgb,alpha,(float)(t*length*.7-time*.18),(float)(nt*length*.7-time*.18),mode+j/(float)sides,mode+(j+1)/(float)sides);
  }
 }
 private static void ellipsoid(Sink out,P c,Frame f,double rx,double ry,double rz,int rgb,float alpha,float time,int mode,int lat,int slices){
  if(alpha<.005||rx<.001)return;
  for(int y=0;y<lat;y++)for(int x=0;x<slices;x++){
   double a=y*Math.PI/lat-Math.PI/2,b=(y+1)*Math.PI/lat-Math.PI/2,u=x*TAU/slices,v=(x+1)*TAU/slices;
   quadUV(out,spherePoint(c,f,rx,ry,rz,a,u),spherePoint(c,f,rx,ry,rz,a,v),spherePoint(c,f,rx,ry,rz,b,v),spherePoint(c,f,rx,ry,rz,b,u),
     rgb,alpha,x/(float)slices*4-time*.015f,(x+1)/(float)slices*4-time*.015f,mode+y/(float)lat,mode+(y+1)/(float)lat);
  }
 }
 private static P spherePoint(P c,Frame f,double rx,double ry,double rz,double lat,double angle){
  return c.add(f.up.mul(Math.sin(lat)*ry)).add(f.right.mul(Math.cos(lat)*Math.cos(angle)*rx)).add(f.axis.mul(Math.cos(lat)*Math.sin(angle)*rz));
 }
 private static void tubePath(Sink out,P[] path,double radius,int sides,int rgb,float alpha,double time,int mode){pathTube(out,path,radius,sides,rgb,alpha,time,mode,false);}
 private static void taperedPath(Sink out,P[] path,double radius,int sides,int rgb,float alpha,double time,int mode){pathTube(out,path,radius,sides,rgb,alpha,time,mode,true);}
 private static void pathTube(Sink out,P[] path,double radius,int sides,int rgb,float alpha,double time,int mode,boolean taper){
  Frame previous=Frame.along(path[1].sub(path[0]));
  for(int i=0;i<path.length-1;i++){
   Frame next=Frame.along(path[Math.min(path.length-1,i+2)].sub(path[i]));
   double r=radius*(taper?Math.max(.08,1-i/(double)(path.length-1)):1);
   double nr=radius*(taper?Math.max(.035,1-(i+1)/(double)(path.length-1)):1);
   for(int j=0;j<sides;j++){
    double a=j*TAU/sides,b=(j+1)*TAU/sides;
    quadUV(out,path[i].add(previous.radial(a).mul(r)),path[i].add(previous.radial(b).mul(r)),
      path[i+1].add(next.radial(b).mul(nr)),path[i+1].add(next.radial(a).mul(nr)),rgb,alpha,
      (float)(i*.12-time),(float)((i+1)*.12-time),mode+j/(float)sides,mode+(j+1)/(float)sides);
   }
   previous=next;
  }
 }
 private static void ribbon(Sink out,P[] path,P camera,double width,int rgb,float alpha,double time,int mode,boolean taper){
  for(int i=0;i<path.length-1;i++){
   double t=i/(double)(path.length-1),nt=(i+1)/(double)(path.length-1);
   P side=path[i+1].sub(path[i]).cross(camera.sub(path[i])).unit();
   double w=width*(taper?Math.sin(Math.PI*(.13+.87*t)):1);
   double nw=width*(taper?Math.sin(Math.PI*(.13+.87*nt)):1);
   quad(out,path[i].sub(side.mul(w)),path[i].add(side.mul(w)),path[i+1].add(side.mul(nw)),path[i+1].sub(side.mul(nw)),
     rgb,alpha,(float)(t*2-time),(float)(nt*2-time),mode);
  }
 }
 private static void lightning(Sink out,P start,P end,P camera,float time,int seed,double width,int rgb,float alpha){
  P[] path=new P[12];Frame f=Frame.along(end.sub(start));
  for(int i=0;i<path.length;i++){
   double t=i/11.0,envelope=Math.sin(t*Math.PI);
   double jitter=Math.sin(i*17.13+seed*2.13+time*.21)*.20+Math.sin(i*7.4-seed+time*.13)*.08;
   path[i]=start.mix(end,t).add(f.right.mul(jitter*envelope)).add(f.up.mul(Math.cos(i*13+seed-time*.17)*.13*envelope));
  }
  ribbon(out,path,camera,width*2.8,rgb,alpha*.22f,time*.02,RIBBON,false);
  ribbon(out,path,camera,width,0xFFF0FC,alpha,time*.02,RIBBON,false);
  // One short fork per bolt gives the arcs a branching silhouette.
  P fork=path[7].add(end.sub(start).mul(.18)).add(f.up.mul(.30+fract(seed*.39)*.35));
  ribbon(out,new P[]{path[6],path[7],fork},camera,width*.65,rgb,alpha*.65f,time*.02,RIBBON,true);
 }
 private static void spark(Sink out,P center,P camera,double radius,int rgb,float alpha){
  if(alpha<.005)return;Frame f=Frame.along(camera.sub(center));
  P r=f.right.mul(radius),u=f.up.mul(radius);
  quad(out,center.sub(r).sub(u),center.add(r).sub(u),center.add(r).add(u),center.sub(r).add(u),rgb,alpha,0,1,SPARK);
 }
 private static void bloom(Sink out,P center,P camera,double radius,int rgb,float alpha){
  if(radius<.01||alpha<.005)return;Frame f=Frame.along(camera.sub(center));
  P r=f.right.mul(radius),u=f.up.mul(radius);
  quad(out,center.sub(r).sub(u),center.add(r).sub(u),center.add(r).add(u),center.sub(r).add(u),rgb,alpha,0,1,BLOOM);
 }
 private static void ring(Sink out,P c,P right,P up,double radius,double width,int rgb,float alpha,float time,int mode){
  if(alpha<.005)return;
  for(int i=0;i<64;i++){
   double a=i*TAU/64,b=(i+1)*TAU/64;
   P ra=right.mul(Math.cos(a)).add(up.mul(Math.sin(a))),rb=right.mul(Math.cos(b)).add(up.mul(Math.sin(b)));
   quad(out,c.add(ra.mul(radius-width)),c.add(ra.mul(radius+width)),c.add(rb.mul(radius+width)),c.add(rb.mul(radius-width)),
    rgb,alpha,i/16f-time*.03f,(i+1)/16f-time*.03f,mode);
  }
 }
 private static double fract(double v){return v-Math.floor(v);}
 private static void quad(Sink out,P a,P b,P c,P d,int rgb,float alpha,float u,float nu,int mode){quadUV(out,a,b,c,d,rgb,alpha,u,nu,mode,mode+1);}
 private static void quadUV(Sink out,P a,P b,P c,P d,int rgb,float alpha,float u,float nu,float v,float nv){
  alpha=Math.max(0,Math.min(1,alpha));
  out.vertex(a.x,a.y,a.z,u,v,rgb,alpha);out.vertex(b.x,b.y,b.z,u,nv,rgb,alpha);
  out.vertex(c.x,c.y,c.z,nu,nv,rgb,alpha);out.vertex(d.x,d.y,d.z,nu,v,rgb,alpha);
 }
}
