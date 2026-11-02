package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.core.YutaBladeMotion;
import dev.jujutsuvfx.core.YutaDanceMotion;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** The companion shares Yuta's tracked action clock, including late tracking and interruptions. */
final class RikaDanceLayer extends RenderLayer<YutaCat,YutaCatModel> {
 private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("jujutsuvfx","textures/entity/rika_dance.png");
 private final SorcererCatGeometry geometry;
 RikaDanceLayer(YutaCatRenderer parent,ResourceManager resources){
  super(parent);geometry=new SorcererCatGeometry(resources,"rika_dance","animation.rika.dance");
 }
 static void placement(PoseStack p){
  placement(p,1.5f);
 }
 static void placement(PoseStack p,float parentScale){
  // A close stagger, as in the reference: Rika leans over the dancer's rear
  // shoulder. Keep world spacing identical across cat, player and mob scales.
  float size=2.4f/parentScale;
  p.translate(1.75/parentScale,1.501,1.10/parentScale);
  p.scale(size,size,size);p.translate(0,-1.5,0);
 }
 @Override public void render(PoseStack p,MultiBufferSource buffers,int light,YutaCat cat,float swing,float amount,float partial,float age,float yaw,float pitch){
  if(cat.isInvisible()||!cat.isAlive())return;
  if(cat.danceStyle()==2)return;
  float time=cat.danceStyle()==1?dev.jujutsuvfx.server.CatDance.loopAge(cat.danceAge(partial)):cat.bladeAge(partial),weight=(cat.danceStyle()==1||cat.bladeAction()==YutaBladeMotion.DANCE)?YutaDanceMotion.weight(time):0;
  if(weight==0&&cat.previousBladeAction()==YutaBladeMotion.DANCE){
   time=cat.previousBladeAge();weight=YutaDanceMotion.weight(time)*(1-cat.bladeBlend(partial));
  }
  if(weight<.01f)return;
  p.pushPose();
  try{
   placement(p);
   geometry.render(p,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),light,OverlayTexture.NO_OVERLAY,
    ((int)(255*weight)<<24)|0xffffff,0,null,YutaDanceMotion.progress(time));
  }finally{p.popPose();}
 }
}
