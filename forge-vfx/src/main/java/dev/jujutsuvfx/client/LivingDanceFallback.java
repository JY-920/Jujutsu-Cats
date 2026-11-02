package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.*;
import dev.jujutsuvfx.server.LivingDance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import java.util.*;
/** Special renderers (including the dragon) have no LivingEntityRenderer model/layer hook. */
public final class LivingDanceFallback {
 private static SorcererCatGeometry rika;
 private static boolean capturing;
 private static final Set<Class<?>> failed=new HashSet<>();
 private static final ResourceLocation RIKA=new ResourceLocation("jujutsuvfx","textures/entity/rika_dance.png");
 @SuppressWarnings({"rawtypes","unchecked"})
 public static void before(Entity actor,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){
  if(capturing||!(actor instanceof LivingEntity e)||!LivingDanceClient.active(e))return;
  EntityRenderer renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
  if(renderer instanceof LivingEntityRenderer)return;
  LivingDanceClient.root(e,null,p,partial);
  if(e.isInvisible())return;
  float time=LivingDance.age(e,partial),spread=ShuffleDanceMotion.sample(time)[4]*ShuffleDanceMotion.weight(time);
  if(LivingDance.style(e)==1){
   if(rika==null)rika=new SorcererCatGeometry(Minecraft.getInstance().getResourceManager(),"rika_dance","animation.rika.dance");
   p.pushPose();try{
    p.mulPose(Axis.YP.rotationDegrees(180-e.yBodyRot));p.scale(-1,-1,1);p.translate(0,-1.501,0);RikaDanceLayer.placement(p,1);
    rika.render(p,buffers.getBuffer(RenderType.entityTranslucent(RIKA)),light,OverlayTexture.NO_OVERLAY,((int)(255*ShuffleDanceMotion.weight(time))<<24)|0xffffff,0,null,YutaDanceMotion.progress(time));
   }finally{p.popPose();}
  }else if(spread>.002f&&!failed.contains(renderer.getClass())){
   // Capture once, then reuse exactly the same posed triangles for both transparent copies.
   // Non-model buffers (names, beams, lines) are deliberately excluded by the recorder.
   var capture=new CutMeshCapture();capturing=true;
   try{renderer.render(e,yaw,partial,new PoseStack(),capture,light);capture.finish();
    var texture=renderer.getTextureLocation(e);if(texture==null)return;
    double angle=Math.toRadians(e.yBodyRot),offset=Math.max(.94,Math.min(6,e.getBbWidth()*.8))*spread;
    for(int side:new int[]{-1,1}){
     p.pushPose();try{p.translate(Math.cos(angle)*side*offset,0,Math.sin(angle)*side*offset);
      var out=buffers.getBuffer(RenderType.entityTranslucent(texture));
      for(var face:capture.faces)for(var v:face.points()){int alpha=(int)(v.a()*.40f*spread);out.vertex(p.last().pose(),(float)v.x(),(float)v.y(),(float)v.z()).color(v.r(),v.g(),v.b(),alpha).uv(v.u(),v.v()).overlayCoords(v.overlay()).uv2(v.light()).normal(p.last().normal(),v.nx(),v.ny(),v.nz()).endVertex();}
     }finally{p.popPose();}
    }
   }catch(RuntimeException failure){failed.add(renderer.getClass());com.mojang.logging.LogUtils.getLogger().warn("Dance copies cannot capture renderer {}; keeping the root dance",renderer.getClass().getName(),failure);}
   finally{capturing=false;}
  }
 }
}
