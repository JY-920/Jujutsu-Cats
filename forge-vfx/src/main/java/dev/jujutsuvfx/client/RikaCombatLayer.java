package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.RikaSupportClock;
import dev.jujutsuvfx.core.RikaResidentClock;
import dev.jujutsuvfx.core.YutaGetoBeat;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

/** Unwinds only the renderer's documented base transform; Yuta's animated model bones never move Rika. */
final class RikaCombatLayer extends RenderLayer<YutaCat,YutaCatModel> {
 private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("jujutsuvfx","textures/entity/rika_dance.png");
 private final SorcererCatGeometry[] clips=new SorcererCatGeometry[RikaSupportClock.OVERHEAD+1];
 private final SorcererCatGeometry crawl;
 private final SorcererCatGeometry[] finale=new SorcererCatGeometry[5];
 RikaCombatLayer(YutaCatRenderer parent,ResourceManager resources){
  super(parent);
  for(int i=0;i<clips.length;i++)clips[i]=new SorcererCatGeometry(resources,"rika_combat","animation.rika."+RikaSupportClock.clip(i));
  crawl=new SorcererCatGeometry(resources,"rika_combat","animation.rika.crawl");
  String[] scene={"vow","charge","clash","breakthrough","recovery"};
  for(int i=0;i<scene.length;i++)finale[i]=new SorcererCatGeometry(resources,"rika_finale","animation.rika."+scene[i]);
 }
 @Override public void render(PoseStack p,MultiBufferSource buffers,int light,YutaCat cat,float swing,float amount,float partial,float age,float yaw,float pitch){
  if(cat.isInvisible()||!cat.isAlive())return;
  int beat=cat.duelState().getInt("Phase");
  if(beat>=YutaGetoBeat.VOW&&beat<=YutaGetoBeat.RETREAT){renderFinale(p,buffers,light,cat,partial,beat);return;}
  var state=cat.rikaState();int action=state.getInt("Action");
  boolean idle=action==RikaSupportClock.NONE;
  if(cat.isInvisible()||!cat.isAlive()||state.isEmpty()||state.getInt("Phase")==RikaResidentClock.PREPARING
      ||action<0||action>RikaSupportClock.OVERHEAD)return;
  float time=idle?0:cat.level().getGameTime()-state.getLong("Start")+partial;
  float elapsed=cat.level().getGameTime()-state.getLong("ResidentStart")+partial;
  float remaining=Math.max(0,state.getInt("Remaining")-Math.max(0,cat.level().getGameTime()-state.getLong("PositionTick")+partial));
  float alpha=RikaSupportClock.phase(elapsed,0,5)*RikaSupportClock.phase(remaining,0,8);
  if(alpha<.01f)return;
  float blend=Mth.clamp(cat.level().getGameTime()-state.getLong("PositionTick")+partial,0,1);
  float facing=Mth.rotLerp(blend,state.getFloat("PrevYaw"),state.getFloat("Yaw")),bodyYaw=Mth.rotLerp(partial,cat.yBodyRotO,cat.yBodyRot);
  double x=Mth.lerp(blend,state.getDouble("PrevX"),state.getDouble("X"))-Mth.lerp(partial,cat.xo,cat.getX());
  double y=Mth.lerp(blend,state.getDouble("PrevY"),state.getDouble("Y"))-Mth.lerp(partial,cat.yo,cat.getY());
  double z=Mth.lerp(blend,state.getDouble("PrevZ"),state.getDouble("Z"))-Mth.lerp(partial,cat.zo,cat.getZ());
  float walk=Mth.lerp(blend,state.getFloat("PrevWalk"),state.getFloat("Walk"));
  float movement=Mth.lerp(blend,state.getFloat("PrevMoving"),state.getFloat("Moving"));
  p.pushPose();
  try{
   p.translate(0,1.501,0);p.scale(1/1.5f,1/1.5f,1/1.5f);p.scale(-1,-1,1);
   p.mulPose(Axis.YP.rotationDegrees(bodyYaw-180));
   p.translate(x,y,z);p.mulPose(Axis.YP.rotationDegrees(180-facing));p.scale(-2.7f,-2.7f,2.7f);p.translate(0,-1.5,0);
   // Only server-authorized positions move the root. Paw/tail gait follows travelled distance.
   boolean walking=idle&&movement>.04f;
   (walking?crawl:clips[action]).render(p,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),light,OverlayTexture.NO_OVERLAY,
       ((int)(255*alpha)<<24)|0xffffff,0,null,walking?walk-(float)Math.floor(walk):idle?(elapsed%36)/36:Math.min(1,time/RikaSupportClock.duration(action)));
  }finally{p.popPose();}
 }
 private void renderFinale(PoseStack p,MultiBufferSource buffers,int light,YutaCat cat,float partial,int phase){
  var state=cat.duelState();float time=Math.max(0,cat.level().getGameTime()-state.getLong("Start")+partial);
  float alpha=phase==YutaGetoBeat.VOW?RikaSupportClock.phase(time,0,8):
      phase==YutaGetoBeat.RETREAT?1-RikaSupportClock.phase(time,80,100):1;
  if(alpha<.01f)return;
  double x=state.getDouble("RX")-Mth.lerp(partial,cat.xo,cat.getX());
  double y=state.getDouble("RY")-Mth.lerp(partial,cat.yo,cat.getY());
  double z=state.getDouble("RZ")-Mth.lerp(partial,cat.zo,cat.getZ());
  float facing=(float)Math.toDegrees(Math.atan2(-(state.getDouble("GX")-state.getDouble("RX")),state.getDouble("GZ")-state.getDouble("RZ")));
  float bodyYaw=Mth.rotLerp(partial,cat.yBodyRotO,cat.yBodyRot);
  p.pushPose();
  try{
   p.translate(0,1.501,0);p.scale(1/1.5f,1/1.5f,1/1.5f);p.scale(-1,-1,1);
   p.mulPose(Axis.YP.rotationDegrees(bodyYaw-180));
   p.translate(x,y,z);p.mulPose(Axis.YP.rotationDegrees(180-facing));p.scale(-3.1f,-3.1f,3.1f);p.translate(0,-1.5,0);
   finale[Math.min(4,phase-YutaGetoBeat.VOW)].render(p,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),light,OverlayTexture.NO_OVERLAY,
       ((int)(255*alpha)<<24)|0xffffff,0,null,phase==YutaGetoBeat.RETREAT?1:time/YutaGetoBeat.duration(phase));
  }finally{p.popPose();}
 }
}
