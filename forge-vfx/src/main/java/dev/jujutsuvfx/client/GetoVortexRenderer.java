package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.GetoVortexMotion;
import net.minecraft.world.phys.Vec3;
/** Wound cursed matter, contracting core and travelling discharge in the local world frame. */
final class GetoVortexRenderer {
 static void draw(float age,Vec3 origin,Vec3 direction,double beamLength,PoseStack p,VertexConsumer out){
  float fade=GetoVortexMotion.fade(age);if(fade<=0)return;
  Vec3 axis=direction.normalize();
  Vec3 right=axis.cross(new Vec3(0,1,0));if(right.lengthSqr()<.01)right=new Vec3(1,0,0);right=right.normalize();Vec3 up=right.cross(axis).normalize();
  p.pushPose();try{p.translate(origin.x,origin.y,origin.z);
   float radius=GetoVortexMotion.radius(age),release=GetoVortexMotion.phase(age,GetoVortexMotion.FIRE,GetoVortexMotion.FIRE+8);
   for(int strand=0;strand<9;strand++)for(int i=0;i<52;i++){
    double t=i/52.0,nt=(i+1)/52.0;
    Vec3 a=spiral(axis,right,up,t,age,strand,radius),b=spiral(axis,right,up,nt,age,strand,radius);
    double angle=t*Math.PI*5-age*(.10+.14*GetoVortexMotion.phase(age,28,58))+strand*Math.PI*2/9;
    Vec3 w=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale((.08+.24*(1-t))*(1-.7*release));
    int rgb=strand%3==0?0xDBB9EB:strand%3==1?0x271030:0x9053A7;
    quad(out,p,a.subtract(w),a.add(w),b.add(w),b.subtract(w),rgb,fade*(1-.8f*release),(float)t*3-age*.035f,(float)nt*3-age*.035f,0);
   }
   sphere(out,p,axis.scale(-.3),right,up,axis,(.22+.40*GetoVortexMotion.phase(age,18,58))*(1-.75*release),age,0xDEBBEF,fade);
   if(age>=GetoVortexMotion.FIRE){
    double length=GetoVortexMotion.reach(age,beamLength);
    tube(out,p,axis,right,up,length,1.6,age,0x26102E,fade*.70f);
    tube(out,p,axis,right,up,length,.88,age,0xA963D2,fade*.86f);
    tube(out,p,axis,right,up,length,.38,age,0xF5DFFB,fade);
    for(int ring=0;ring<4;ring++){
     double z=(age-GetoVortexMotion.FIRE)*GetoVortexMotion.SPEED-ring*5;
     if(z>0&&z<length)ring(out,p,axis.scale(z),right,up,1.65+ring*.09,.12,0xD4A7EB,fade*.60f,age);
    }
    double impact=age-GetoVortexMotion.FIRE-beamLength/GetoVortexMotion.SPEED;
    if(impact>=0&&impact<20){
     float f=(float)(1-impact/20);Vec3 end=axis.scale(beamLength);
     ring(out,p,end,right,up,.5+impact*.27,.12+.25*f,0xDEC4F1,f,age);
     ring(out,p,end,right,up,.3+impact*.20,.10,0x4D2163,f*.8f,age);
     sphere(out,p,end,right,up,axis,.35+Math.sin(Math.min(1,impact/12)*Math.PI)*1.2,age,0xC995E5,f*.65f);
    }
   }
  }finally{p.popPose();}
 }
 private static Vec3 spiral(Vec3 axis,Vec3 right,Vec3 up,double t,float age,int strand,float radius){
  double a=t*Math.PI*5-age*(.10+.14*GetoVortexMotion.phase(age,28,58))+strand*Math.PI*2/9;
  double r=(1-t)*radius*(1+.10*Math.sin(t*21+strand+age*.13));
  return axis.scale(-1.1*(1-t)).add(right.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r));
 }
 static void ring(VertexConsumer out,PoseStack p,Vec3 center,Vec3 right,Vec3 up,double radius,double width,int rgb,float alpha,float age){
  for(int i=0;i<48;i++){
   double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
   Vec3 va=right.scale(Math.cos(a)).add(up.scale(Math.sin(a))),vb=right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
   quad(out,p,center.add(va.scale(radius-width)),center.add(va.scale(radius+width)),center.add(vb.scale(radius+width)),center.add(vb.scale(radius-width)),rgb,alpha,i/12f-age*.04f,(i+1)/12f-age*.04f,0);
  }
 }
 private static void tube(VertexConsumer out,PoseStack p,Vec3 axis,Vec3 right,Vec3 up,double length,double radius,float age,int rgb,float alpha){
  if(length<=0)return;
  for(int z=0;z<16;z++)for(int i=0;i<16;i++){
   double a=i*Math.PI/8,b=(i+1)*Math.PI/8,t=z/16.0,nt=(z+1)/16.0;
   Vec3 ra=right.scale(Math.cos(a)).add(up.scale(Math.sin(a))),rb=right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
   double r=radius*(.78+.22*Math.sin(t*Math.PI))*(1+.05*Math.sin(t*42+age*.6));
   double nr=radius*(.78+.22*Math.sin(nt*Math.PI))*(1+.05*Math.sin(nt*42+age*.6));
   quad(out,p,axis.scale(t*length).add(ra.scale(r)),axis.scale(t*length).add(rb.scale(r)),axis.scale(nt*length).add(rb.scale(nr)),axis.scale(nt*length).add(ra.scale(nr)),rgb,alpha,(float)(t*length*.25)-age*.18f,(float)(nt*length*.25)-age*.18f,2);
  }
 }
 private static Vec3 spherePoint(Vec3 c,Vec3 r,Vec3 u,Vec3 axis,double radius,double latitude,double angle){return c.add(u.scale(Math.sin(latitude)*radius)).add(r.scale(Math.cos(latitude)*Math.cos(angle)*radius)).add(axis.scale(Math.cos(latitude)*Math.sin(angle)*radius));}
 static void sphere(VertexConsumer out,PoseStack p,Vec3 c,Vec3 r,Vec3 u,Vec3 axis,double radius,float age,int rgb,float alpha){
  for(int lat=0;lat<10;lat++)for(int i=0;i<20;i++){
   double a=lat*Math.PI/10-Math.PI/2,b=(lat+1)*Math.PI/10-Math.PI/2,x=i*Math.PI/10,y=(i+1)*Math.PI/10;
   quad(out,p,spherePoint(c,r,u,axis,radius,a,x),spherePoint(c,r,u,axis,radius,a,y),spherePoint(c,r,u,axis,radius,b,y),spherePoint(c,r,u,axis,radius,b,x),rgb,alpha,lat*.2f-age*.03f,(lat+1)*.2f-age*.03f,2);
  }
 }
 private static void quad(VertexConsumer b,PoseStack p,Vec3 a,Vec3 c,Vec3 d,Vec3 e,int rgb,float alpha,float t,float nt,float mode){
  vertex(b,p,a,t,mode,rgb,alpha);vertex(b,p,c,t,mode+1,rgb,alpha);vertex(b,p,d,nt,mode+1,rgb,alpha);vertex(b,p,e,nt,mode,rgb,alpha);
 }
 private static void vertex(VertexConsumer b,PoseStack p,Vec3 v,float u,float w,int rgb,float alpha){b.vertex(p.last().pose(),(float)v.x,(float)v.y,(float)v.z).uv(u,w).color(rgb>>16&255,rgb>>8&255,rgb&255,(int)(255*alpha)).endVertex();}
}
