package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.core.*;
import dev.jujutsuvfx.server.LivingDance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
public final class LivingDanceLayer<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
 private SorcererCatGeometry rika;
 private static final ResourceLocation RIKA=new ResourceLocation("jujutsuvfx","textures/entity/rika_dance.png");
 public LivingDanceLayer(RenderLayerParent<T,M> parent){super(parent);}
 @Override public void render(PoseStack p,MultiBufferSource buffers,int light,T e,float swing,float amount,float partial,float age,float yaw,float pitch){
  if(!LivingDanceClient.active(e)||e.isInvisible())return;
  float time=LivingDance.age(e,partial);
  if(LivingDance.style(e)==2){
   CatDanceLayer.copies(p,time,color->getParentModel().renderToBuffer(p,buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(e))),light,OverlayTexture.NO_OVERLAY,1,1,1,((color>>>24)&255)/255f));
  }else{
   if(rika==null)rika=new SorcererCatGeometry(Minecraft.getInstance().getResourceManager(),"rika_dance","animation.rika.dance");
   p.pushPose();try{RikaDanceLayer.placement(p,1);rika.render(p,buffers.getBuffer(RenderType.entityTranslucent(RIKA)),light,OverlayTexture.NO_OVERLAY,((int)(255*ShuffleDanceMotion.weight(time))<<24)|0xffffff,0,null,YutaDanceMotion.progress(time));}finally{p.popPose();}
  }
 }
}
