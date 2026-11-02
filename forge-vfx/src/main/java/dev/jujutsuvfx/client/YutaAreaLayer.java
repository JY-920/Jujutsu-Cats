package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.YutaAreaMotion;
import dev.jujutsuvfx.core.YutaBladeMotion;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Thin world-sized blade trails, using a standard entity material also supported by shader packs. */
final class YutaAreaLayer extends RenderLayer<YutaCat,YutaCatModel> {
 private static final ResourceLocation WHITE=new ResourceLocation("jujutsuvfx","textures/effect/yuta_slash.png");
 YutaAreaLayer(YutaCatRenderer parent){super(parent);}
 @Override public void render(PoseStack p,MultiBufferSource buffers,int light,YutaCat cat,float swing,float amount,float partial,float age,float yaw,float pitch){
  int action=cat.bladeAction();if(!cat.isAlive()||cat.isInvisible()||!YutaAreaMotion.active(action))return;
  float time=cat.bladeAge(partial),alpha=YutaAreaMotion.visual(action,time);if(alpha<.001)return;
  float radius=YutaAreaMotion.radius(action),t=time-YutaAreaMotion.contact(action);
  p.pushPose();
  try{
   p.translate(0,1.501,0);p.scale(1/1.5f,1/1.5f,1/1.5f);p.scale(-1,-1,1);
   p.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial,cat.yBodyRotO,cat.yBodyRot)-180));
   p.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,cat.yRotO,cat.getYRot())));
   VertexConsumer b=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
   float reach=radius*(.78f+.22f*YutaBladeMotion.phase(t,-2,1));
   double half=action==YutaAreaMotion.FAN?1.21:Math.PI;
   for(int layer=0;layer<3;layer++){
    float width=layer==0?.28f:layer==1?.11f:.035f;
    int red=layer==0?126:layer==1?211:255,green=layer==0?38:layer==1?161:245;
    float opacity=alpha*(layer==0?.30f:layer==1?.65f:1);
    double start=-half,end=half;
    int segments=action==YutaAreaMotion.FAN?46:96;
    for(int i=0;i<segments;i++){
     double a=start+(end-start)*i/segments,c=start+(end-start)*(i+1)/segments;
     float height=action==YutaAreaMotion.SLAM?.13f:.85f;
     float tilt=action==YutaAreaMotion.SLAM?0:.22f;
     float r=reach+(layer==0?.03f:0);
     vertex(b,p,r-width,a,height,tilt,red,green,opacity);
     vertex(b,p,r+width,a,height,tilt,red,green,opacity);
     vertex(b,p,r+width,c,height,tilt,red,green,opacity);
     vertex(b,p,r-width,c,height,tilt,red,green,opacity);
    }
   }
  }finally{p.popPose();}
 }
 private static void vertex(VertexConsumer b,PoseStack p,float radius,double angle,float y,float tilt,int red,int green,float alpha){
  float x=(float)Math.sin(angle)*radius,z=(float)Math.cos(angle)*radius;
  b.vertex(p.last().pose(),x,y+x*tilt,z).color(red,green,255,(int)(255*alpha)).uv(.5f,.5f)
    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.last().normal(),0,1,0).endVertex();
 }
}
