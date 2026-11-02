package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.core.ShuffleDanceMotion;
import dev.jujutsuvfx.core.YutaDanceMotion;
import dev.jujutsuvfx.entity.DancingCat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Mob;
/** Two depth-tested copies of the current posed mesh, never a duplicated renderer/AI. */
final class CatDanceLayer<T extends Mob & DancingCat,M extends EntityModel<T>> extends RenderLayer<T,M> {
 private final ResourceLocation skin,details;
 private final SorcererCatGeometry rika;
 private static final ResourceLocation RIKA=ResourceLocation.fromNamespaceAndPath("jujutsuvfx","textures/entity/rika_dance.png");
 CatDanceLayer(RenderLayerParent<T,M> parent,ResourceManager resources,ResourceLocation skin,ResourceLocation details){
  super(parent);this.skin=skin;this.details=details;rika=new SorcererCatGeometry(resources,"rika_dance","animation.rika.dance");
 }
 // White RGB preserves the original skin; only alpha fades the two model copies.
 @FunctionalInterface interface Copy {void draw(int color);}
 static void copies(PoseStack p,float time,Copy copy){
  float spread=ShuffleDanceMotion.sample(time)[4]*ShuffleDanceMotion.weight(time);
  if(spread<.002f)return;
  for(int side:new int[]{-1,1}){
   p.pushPose();try{p.translate(side*.94*spread,-.035*spread,.12*spread);copy.draw(((int)(102*spread)<<24)|0xffffff);}finally{p.popPose();}
  }
 }
 @Override public void render(PoseStack p,MultiBufferSource buffers,int light,T cat,float swing,float amount,float partial,float age,float yaw,float pitch){
  if(cat.isInvisible()||!cat.isAlive())return;
  float time=cat.danceAge(partial);
  if(cat.danceStyle()==2){
   copies(p,time,color->{for(int material=0;material<2;material++){
    var b=buffers.getBuffer(RenderType.entityTranslucent(material==0?skin:details));
    if(getParentModel() instanceof YutaCatModel m)m.renderMaterial(p,b,light,OverlayTexture.NO_OVERLAY,color,material);
    else if(getParentModel() instanceof GetoCatModel m)m.renderMaterial(p,b,light,OverlayTexture.NO_OVERLAY,color,material);
    else if(getParentModel() instanceof SorcererCatModel<?> m)m.renderMaterial(p,b,light,OverlayTexture.NO_OVERLAY,color,material);
   }});
  }else if(cat.danceStyle()==1&&getParentModel() instanceof SorcererCatModel<?> model){
   p.pushPose();try{RikaDanceLayer.placement(p);rika.render(p,buffers.getBuffer(RenderType.entityTranslucent(RIKA)),light,OverlayTexture.NO_OVERLAY,
    ((int)(255*ShuffleDanceMotion.weight(time))<<24)|0xffffff,0,null,YutaDanceMotion.progress(dev.jujutsuvfx.server.CatDance.loopAge(time)));}finally{p.popPose();}
   p.pushPose();try{model.handPose(p,dev.jujutsuvfx.entity.SorcererCat.RIGHT);FistAuraRenderer.drawDancePalm(p,cat,partial,ShuffleDanceMotion.weight(time));}finally{p.popPose();}
  }else if(cat.danceStyle()==1&&getParentModel() instanceof GetoCatModel model){
   p.pushPose();try{RikaDanceLayer.placement(p);rika.render(p,buffers.getBuffer(RenderType.entityTranslucent(RIKA)),light,OverlayTexture.NO_OVERLAY,
    ((int)(255*ShuffleDanceMotion.weight(time))<<24)|0xffffff,0,null,YutaDanceMotion.progress(dev.jujutsuvfx.server.CatDance.loopAge(time)));}finally{p.popPose();}
   p.pushPose();try{model.rig.combatRoot(p);model.rig.dancePalm(p);FistAuraRenderer.drawDancePalm(p,cat,partial,ShuffleDanceMotion.weight(time));}finally{p.popPose();}
  }
 }
}
