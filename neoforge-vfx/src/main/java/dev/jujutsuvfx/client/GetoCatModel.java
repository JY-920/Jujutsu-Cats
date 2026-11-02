package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.entity.GetoCat;
import dev.jujutsuvfx.core.YutaBladeMotion;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.ResourceManager;

final class GetoCatModel extends EntityModel<GetoCat> {
 final YutaCatModel rig;
 GetoCatModel(ResourceManager resources){rig=new YutaCatModel(YutaCatModel.createRoot(),new SorcererCatGeometry(resources,"geto_cat",null));}
 public void setupAnim(GetoCat cat,float swing,float amount,float age,float yaw,float pitch){
  rig.young=false;float time=cat.actionAge(Math.max(0,Math.min(1,age-cat.tickCount)));
  if(cat.danceStyle()!=0)rig.applyDance(cat.danceStyle(),cat.danceAge(age-cat.tickCount));
  else if(cat.action()==0)rig.applyLocomotion(swing,amount,age,yaw,pitch);
  else if(cat.action()==GetoCat.SUMMON)rig.applyAction(YutaBladeMotion.BRAWL_BASE+dev.jujutsuvfx.core.CatCombatMoves.DOUBLE_PALM,time*.65f);
  else if(cat.action()==GetoCat.RETREAT)rig.applyAction(YutaBladeMotion.RETREAT,
    cat.duelState().getInt("Phase")==dev.jujutsuvfx.core.YutaGetoBeat.SEPARATE?time*8f/28f:time);
  else if(cat.action()==GetoCat.UZUMAKI||cat.action()==GetoCat.BLAST)rig.applyAction(GetoCat.UZUMAKI,cat.vortexAge(Math.max(0,Math.min(1,age-cat.tickCount))));
  else rig.applyAction(cat.action(),time);
 }
 void renderMaterial(PoseStack p,VertexConsumer b,int light,int overlay,int color,int material){rig.renderMaterial(p,b,light,overlay,color,material);}
 public void renderToBuffer(PoseStack p,VertexConsumer b,int light,int overlay,int color){renderMaterial(p,b,light,overlay,color,0);}
}
