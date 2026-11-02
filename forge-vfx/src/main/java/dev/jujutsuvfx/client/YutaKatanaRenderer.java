package dev.jujutsuvfx.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.joml.Vector3f;
import java.util.*;

/** Use the supplied textured sword mesh for inventory, dropped items, and both hand views. */
public final class YutaKatanaRenderer extends BlockEntityWithoutLevelRenderer {
 public static final ResourceLocation TEXTURE=new ResourceLocation("jujutsuvfx","textures/entity/yuta_katana.png");
 public static final RenderType SOLID=RenderType.entityCutoutNoCull(TEXTURE);
 private static Object resources;
 private static List<float[][]> faces=List.of();
 public YutaKatanaRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
 private static void load(){
  var manager=Minecraft.getInstance().getResourceManager();if(resources==manager&&!faces.isEmpty())return;
  var result=new ArrayList<float[][]>();
  try(var reader=manager.openAsReader(new ResourceLocation("jujutsuvfx","meshes/yuta_katana.json"))){
   for(var f:JsonParser.parseReader(reader).getAsJsonArray()){
    float[][] q=new float[4][5];for(int i=0;i<4;i++)for(int j=0;j<5;j++)q[i][j]=f.getAsJsonArray().get(i).getAsJsonArray().get(j).getAsFloat();result.add(q);
   }
  }catch(java.io.IOException e){throw new IllegalStateException("Cannot load Yuta sword",e);}
  faces=List.copyOf(result);resources=manager;
 }
 public static void draw(PoseStack p,VertexConsumer out,int light,int overlay){
  load();for(var q:faces){
   Vector3f normal=new Vector3f(q[1][0]-q[0][0],q[1][1]-q[0][1],q[1][2]-q[0][2]).cross(q[2][0]-q[0][0],q[2][1]-q[0][1],q[2][2]-q[0][2]).normalize();
   for(var v:q)out.vertex(p.last().pose(),v[0],v[1],v[2]).color(255,255,255,255).uv(v[3],v[4]).overlayCoords(overlay).uv2(light).normal(p.last().normal(),normal.x,normal.y,normal.z).endVertex();
  }
 }
 @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack p,MultiBufferSource buffers,int light,int overlay){
  p.pushPose();try{
   p.translate(.5,.5,.5);
   if(context==ItemDisplayContext.GUI){p.translate(-.16,-.26,0);p.mulPose(Axis.ZP.rotationDegrees(-36));p.mulPose(Axis.YP.rotationDegrees(20));p.scale(.90f,.90f,.90f);}
   else if(context==ItemDisplayContext.GROUND){p.mulPose(Axis.XP.rotationDegrees(85));p.scale(.75f,.75f,.75f);}
   else if(context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND||context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND){
    p.translate(0,-.12,0);p.mulPose(Axis.ZP.rotationDegrees(context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND?-25:25));p.scale(1.65f,1.65f,1.65f);
   }else {p.translate(0,-.25,0);p.scale(.85f,.85f,.85f);}
   draw(p,buffers.getBuffer(SOLID),light,overlay);
   if((context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND||context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
      &&dev.jujutsuvfx.item.YutaKatanaItem.imbued(stack)&&Minecraft.getInstance().player!=null){
    FistAuraRenderer.drawPlayerBlade(p,Minecraft.getInstance().player,Minecraft.getInstance().getFrameTime(),1,true);
   }
  }finally{p.popPose();}
 }
}
