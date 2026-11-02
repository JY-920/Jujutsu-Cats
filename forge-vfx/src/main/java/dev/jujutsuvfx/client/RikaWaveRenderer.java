package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** A detached pulse with a bright compressed core and expanding trailing pressure rings. */
final class RikaWaveRenderer {
 static void draw(CompoundTag s,long now,float partial,Vec3 camera,PoseStack p,VertexConsumer out){
  Vec3 origin=new Vec3(s.getDouble("WaveX"),s.getDouble("WaveY"),s.getDouble("WaveZ"));
  if(camera.distanceToSqr(origin)>192*192)return;
  Vec3 axis=new Vec3(s.getDouble("WaveDX"),s.getDouble("WaveDY"),s.getDouble("WaveDZ")).normalize();
  if(axis.lengthSqr()<.5)return;
  double travel=Math.min(32,s.getDouble("WaveDistance")+Math.min(1,Math.max(0,now-s.getLong("PositionTick")+partial))*1.6);
  float age=now-s.getLong("Start")+partial;
  Vec3 right=axis.cross(new Vec3(0,1,0));
  if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
  right=right.normalize();Vec3 up=right.cross(axis).normalize(),relative=origin.subtract(camera);
  p.pushPose();
  try{
   p.translate(relative.x,relative.y,relative.z);
   for(int i=0;i<5;i++){
    double behind=i*.75,z=travel-behind;if(z<0)continue;
    GetoVortexRenderer.ring(out,p,axis.scale(z),right,up,.55+i*.18,.06+i*.014,
      i==0?0xFFF5FF:0xC49AF0,(1-i/6f)*.85f,age);
   }
   Vec3 center=axis.scale(travel);
   GetoVortexRenderer.sphere(out,p,center,right,up,axis,.48,age,0xFCF5FF,.95f);
   GetoVortexRenderer.sphere(out,p,center,right,up,axis,.73,age,0xBA74ED,.24f);
  }finally{p.popPose();}
 }
}
