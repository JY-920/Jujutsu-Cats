package dev.jujutsuvfx.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

public final class YutaCatRenderer extends MobRenderer<YutaCat,YutaCatModel> {
 private static final ResourceLocation CAT=ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"textures/entity/yuta_cat.png");
 private static final ResourceLocation KATANA=ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"textures/entity/yuta_katana.png");
 private static final ResourceLocation DETAILS=ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"textures/entity/yuta_details.png");
 public YutaCatRenderer(EntityRendererProvider.Context context){
  super(context,new YutaCatModel(YutaCatModel.createRoot(),new SorcererCatGeometry(context.getResourceManager(),"yuta_cat",null)),.65f);
  List<float[][]> faces=new ArrayList<>();
  try(var reader=context.getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"meshes/yuta_katana.json"))){
   for(var face:JsonParser.parseReader(reader).getAsJsonArray()){
    float[][] vertices=new float[4][5];for(int i=0;i<4;i++)for(int j=0;j<5;j++)vertices[i][j]=face.getAsJsonArray().get(i).getAsJsonArray().get(j).getAsFloat();faces.add(vertices);
   }
  }catch(Exception error){throw new IllegalStateException("Cannot load supplied Yuta katana",error);}
  addLayer(new SkinLayer(this));
  addLayer(new YutaAreaLayer(this));
  addLayer(new CatDanceLayer<>(this,context.getResourceManager(),CAT,DETAILS));
  addLayer(new WeaponLayer(this,List.copyOf(faces)));
  addLayer(new RikaDanceLayer(this,context.getResourceManager()));
  addLayer(new RikaCombatLayer(this,context.getResourceManager()));
 }
 @Override public ResourceLocation getTextureLocation(YutaCat cat){return CAT;}
 @Override protected void scale(YutaCat cat,PoseStack p,float partial){p.scale(1.5f,1.5f,1.5f);}
 @Override public boolean shouldRender(YutaCat cat,Frustum frustum,double x,double y,double z){
  // The grounded companion can pursue beyond the owner's frustum; cull its actual tracked bounds.
  var rika=cat.rikaState();
  if(!rika.isEmpty()&&rika.contains("X")&&cat.shouldRender(x,y,z)){
   double rx=rika.getDouble("X"),ry=rika.getDouble("Y"),rz=rika.getDouble("Z");
   if(Double.isFinite(rx)&&Double.isFinite(ry)&&Double.isFinite(rz)
      &&frustum.isVisible(new net.minecraft.world.phys.AABB(rx-3.5,ry-.2,rz-3.5,rx+3.5,ry+4.0,rz+3.5)))return true;
  }
  return super.shouldRender(cat,frustum,x,y,z)||cat.shouldRender(x,y,z)&&frustum.isVisible(cat.getBoundingBox().inflate(dev.jujutsuvfx.core.YutaAreaMotion.active(cat.bladeAction())?12:(!cat.rikaState().isEmpty()||dev.jujutsuvfx.core.YutaGetoBeat.finale(cat.duelState().getInt("Phase")))?16:cat.danceStyle()!=0?7:cat.bladeAction()==dev.jujutsuvfx.core.YutaBladeMotion.DANCE?7:2));
 }
 private static final class SkinLayer extends RenderLayer<YutaCat,YutaCatModel>{
  SkinLayer(YutaCatRenderer parent){super(parent);}
  @Override public void render(PoseStack p,MultiBufferSource buffers,int light,YutaCat cat,float swing,float amount,float partial,float age,float yaw,float pitch){
   if(cat.isInvisible())return;
   getParentModel().renderMaterial(p,buffers.getBuffer(RenderType.entityCutoutNoCull(DETAILS)),light,LivingEntityRenderer.getOverlayCoords(cat,0),-1,1);
   if(cat.danceStyle()==1||cat.danceStyle()==0&&cat.bladeAction()==dev.jujutsuvfx.core.YutaBladeMotion.DANCE){
    p.pushPose();try{getParentModel().combatRoot(p);getParentModel().dancePalm(p);FistAuraRenderer.drawDancePalm(p,cat,partial,cat.danceStyle()==1?dev.jujutsuvfx.core.ShuffleDanceMotion.weight(cat.danceAge(partial)):dev.jujutsuvfx.core.YutaDanceMotion.weight(cat.bladeAge(partial)));}finally{p.popPose();}
   }
   InfinityBarrierRenderer.captureYuta(cat,getParentModel(),p,partial,CAT,DETAILS);
  }
 }
 private static final class WeaponLayer extends RenderLayer<YutaCat,YutaCatModel>{
  private final List<float[][]> faces;
  WeaponLayer(YutaCatRenderer renderer,List<float[][]> faces){super(renderer);this.faces=faces;}
  @Override public void render(PoseStack p,MultiBufferSource buffers,int light,YutaCat cat,float swing,float amount,float partial,float age,float yaw,float pitch){
   if(cat.isInvisible()||(cat.danceStyle()!=0||cat.bladeAction()==dev.jujutsuvfx.core.YutaBladeMotion.DANCE))return;
   p.pushPose();
   try{
    getParentModel().combatRoot(p);
    Matrix4f weapon=getParentModel().weaponPose(cat,partial);
    p.last().pose().mul(weapon);p.last().normal().mul(new Matrix3f(weapon));
    FistAuraRenderer.drawBlade(p,cat,partial,cat.bladePower(partial));
    VertexConsumer b=buffers.getBuffer(RenderType.entityCutoutNoCull(KATANA));
    int overlay=LivingEntityRenderer.getOverlayCoords(cat,0);
    for(float[][] face:faces){
     Vector3f normal=new Vector3f(face[1][0]-face[0][0],face[1][1]-face[0][1],face[1][2]-face[0][2])
      .cross(face[2][0]-face[0][0],face[2][1]-face[0][1],face[2][2]-face[0][2]).normalize();
     for(float[] v:face)vertex(b,p.last(),v,normal,light,overlay);
    }
   }finally{p.popPose();}
  }
 }
 private static void vertex(VertexConsumer b,PoseStack.Pose p,float[] v,Vector3f n,int light,int overlay){
  b.addVertex(p,v[0],v[1],v[2]).setColor(255,255,255,255).setUv(v[3],v[4]).setOverlay(overlay).setLight(light).setNormal(p,n.x,n.y,n.z);
 }
}
