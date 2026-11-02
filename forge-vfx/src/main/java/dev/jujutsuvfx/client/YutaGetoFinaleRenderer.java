package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.core.YutaGetoBeat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Reads only the server-owned screenplay tag; no client timers or proxy entities. */
final class YutaGetoFinaleRenderer {
 static boolean active(CompoundTag state){
  int phase=state.getInt("Phase");
  return phase>=YutaGetoBeat.VOW&&phase<=YutaGetoBeat.AFTERMATH
    &&state.contains("RX")&&state.contains("GX")&&state.contains("MX");
 }
 static Vec3 anchor(CompoundTag state,String prefix){return new Vec3(state.getDouble(prefix+"X"),state.getDouble(prefix+"Y"),state.getDouble(prefix+"Z"));}
 private static YutaGetoFinaleGeometry.P p(Vec3 v){return new YutaGetoFinaleGeometry.P(v.x,v.y,v.z);}
 static void draw(CompoundTag state,long now,float partial,Vec3 camera,boolean solid,VertexConsumer out){
  if(!active(state))return;
  // E/L are the actual elevated emitters. R/G remain the actors' ground anchors.
  Vec3 rika=state.contains("LX")?anchor(state,"L"):anchor(state,"R").add(0,2.25,0);
  Vec3 geto=state.contains("EX")?anchor(state,"E"):anchor(state,"G").add(0,2.2,0);
  Vec3 middle=anchor(state,"M");
  if(!finite(rika)||!finite(geto)||!finite(middle)||rika.distanceToSqr(geto)>128*128)return;
  float age=now-state.getLong("Start")+partial;
  YutaGetoFinaleGeometry.scene(state.getInt("Phase"),age,p(rika),p(geto),p(middle),p(camera),anchor(state,"G").y+.10,VfxConfig.flashes(),solid,
    YutaGetoClashEffects.ready(),sink(out,camera));
 }
 static YutaGetoFinaleGeometry.Sink sink(VertexConsumer out,Vec3 camera){
  return (x,y,z,u,v,rgb,alpha)->out.vertex(x-camera.x,y-camera.y,z-camera.z)
    .uv(u,v).color(rgb>>16&255,rgb>>8&255,rgb&255,(int)(255*alpha)).endVertex();
 }
 static void maximum(float age,Vec3 source,Vec3 direction,double length,double scale,Vec3 camera,boolean solid,VertexConsumer out){
  YutaGetoFinaleGeometry.vortex(age,length,p(source),p(direction),p(camera),scale,solid,sink(out,camera));
 }
 private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
