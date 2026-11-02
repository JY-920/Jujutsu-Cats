package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.CursedSpeechShape;
import dev.jujutsuvfx.core.YutaBladeMotion;
import dev.jujutsuvfx.entity.YutaCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Three bounded material passes: mouth seal, directional pressure fronts, target restraint. */
final class CursedSpeechRenderer {
 private record Cue(CatCombatCue packet,long born){}
 private record Card(Vec3 center,Vec3 right,Vec3 up,float seed,float mode,float phase,float alpha){}
 private static final List<Cue> CUES=new ArrayList<>();
 private static final SceneSnapshot SCENE=new SceneSnapshot();
 private static final CastAudio AUDIO=new CastAudio();
 private static ShaderInstance glow,air;
 static void setShader(ShaderInstance shader){glow=shader;}
 static void setAirShader(ShaderInstance shader){air=shader;}
 static void clear(){CUES.clear();SCENE.release();AUDIO.stop();}
 static void tick(){long now=ClientEffectClock.ticks();CUES.removeIf(c->now-c.born>(c.packet.first()==0?10:c.packet.first()==1?17:c.packet.second()+6));}
 static void receive(CatCombatCue cue){
  if(cue.first()<0||cue.first()>2||cue.second()<0||cue.second()>CursedSpeechShape.STUN_TICKS)return;
  if(CUES.size()>=24)CUES.remove(0);CUES.add(new Cue(cue,ClientEffectClock.ticks()));
  if(cue.first()<2){
   float volume=(float)Math.max(0,1-Math.sqrt(EffectView.distanceToSqr(cue.from()))/70);
   AUDIO.play(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("jujutsuvfx",cue.first()==0?"cursed_speech_charge":"cursed_speech_release")),cue.from(),volume*(cue.first()==0?.8f:1.6f),1);
  }
 }
 static void camera(ViewportEvent.ComputeCameraAngles event,float partial,float scale){
  float kick=0;long now=ClientEffectClock.ticks();
  for(Cue cue:CUES)if(cue.packet.first()==1){float age=now-cue.born+partial;
   float near=(float)Math.max(0,1-Math.sqrt(EffectView.distanceToSqr(cue.packet.from()))/18);
   kick+=near*(float)Math.exp(-age*.65)*.55f;
  }
  event.setPitch(event.getPitch()-Math.min(.65f,kick)*scale);
 }
 static void render(RenderLevelStageEvent event){
  if(CUES.isEmpty()){SCENE.release();return;}if(glow==null)return;
  Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
  float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
  long now=ClientEffectClock.ticks();Vec3 camera=event.getCamera().getPosition();
  List<Card> cards=new ArrayList<>(64);boolean pressure=false;
  for(Cue cue:CUES){
   var p=cue.packet;float age=now-cue.born+partial,seed=(p.seed()&65535)/65535f;
   if(age<0||EffectView.distanceToSqr(p.to())>112*112)continue;
   Vec3 origin=p.from(),forward=p.to().subtract(p.from()).normalize();
   if(p.first()==0){
    if(age>9)continue;
    if(mc.level.getEntity(p.actor()) instanceof YutaCat cat){
     if(age>3&&cat.bladeAction()!=YutaBladeMotion.SPEECH&&cat.copiedSpellKind()!=dev.jujutsuvfx.core.CatSpell.BARRAGE)continue;
     forward=cat.getViewVector(partial);origin=cat.getEyePosition(partial).add(forward.scale(.65)).add(0,-.15,0);
    }
    if(mc.level.getEntity(p.actor()) instanceof net.minecraft.world.entity.player.Player player){
     if(age>3&&!player.isUsingItem())continue;
     forward=player.getViewVector(partial);origin=player.getEyePosition(partial).add(forward.scale(.25)).add(0,-.15,0);
    }
    float t=Math.min(1,age/8),alpha=YutaBladeMotion.phase(age,0,2)*(1-YutaBladeMotion.phase(age,8,10));
    plane(cards,origin,forward,.42+.17*t,seed,0,t,alpha);
   }else if(p.first()==1){
    if(age>16)continue;pressure=true;
    for(int layer=0;layer<3;layer++){
     if(age<layer*1.3f)continue;double distance=CursedSpeechShape.waveDistance(age,layer);
     float fade=(1-YutaBladeMotion.phase(age,8+layer,15+layer))*(1-layer*.15f);
     plane(cards,origin.add(forward.scale(distance)),forward,.45+distance*.48,seed+layer*.05f,1,age/17,fade);
    }
    Vec3 end=origin.add(forward.scale(CursedSpeechShape.waveDistance(age,0)));
    Vec3 right=forward.cross(camera.subtract(origin)).normalize().scale(.35);
    cards.add(new Card(origin.lerp(end,.5),end.subtract(origin).scale(.5),right,seed,3,age/17,1-YutaBladeMotion.phase(age,3,10)));
   }else{
    float fade=1-YutaBladeMotion.phase(age,p.second(),p.second()+6);
    if(fade<=0)continue;Vec3 at=p.to();double radius=.9;
    var entity=mc.level.getEntity(p.other());
    if(entity!=null){at=entity.getPosition(partial).add(0,entity.getBbHeight()*.55,0);radius=Math.max(.65,entity.getBbHeight()*.8);}
    plane(cards,at,camera.subtract(at).normalize(),radius,seed,2,age/40,fade);
   }
  }
  if(cards.isEmpty())return;
  try(DomainRenderer.State state=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
   Matrix4f vp=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
   float time=(now%24000+partial)*.05f;
   float strength=mc.options.screenEffectScale().get().floatValue();
   if(pressure&&air!=null&&strength>.001&&SCENE.capture()){
    int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
    air.safeGetUniform("Viewport").set((float)viewport[0],(float)viewport[1],(float)viewport[2],(float)viewport[3]);
    air.safeGetUniform("Strength").set(strength);air.setSampler("SceneColor",SCENE.color());air.setSampler("SceneDepth",SCENE.depth());
    draw(cards,camera,vp,time,air,false);
   }
   draw(cards,camera,vp,time,glow,true);
  }
 }
 private static void plane(List<Card> cards,Vec3 center,Vec3 normal,double radius,float seed,float mode,float phase,float alpha){
  Vec3 right=normal.cross(Math.abs(normal.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
  Vec3 up=right.cross(normal).normalize();cards.add(new Card(center,right.scale(radius),up.scale(radius),seed,mode,phase,alpha));
 }
 private static void draw(List<Card> cards,Vec3 camera,Matrix4f vp,float time,ShaderInstance shader,boolean luminous){
  RenderSystem.setShader(()->shader);shader.safeGetUniform("ViewProjection").set(vp);shader.safeGetUniform("Time").set(time);
  RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
  RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,luminous?GL11.GL_ONE:GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE);
  BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
  for(Card card:cards){if(!luminous&&card.mode!=1)continue;if(card.alpha<=.001)continue;
   Vec3 c=card.center.subtract(camera),r=card.right,u=card.up;
   vertex(b,c.subtract(r).subtract(u),0,0,card);vertex(b,c.add(r).subtract(u),1,0,card);
   vertex(b,c.add(r).add(u),1,1,card);vertex(b,c.subtract(r).add(u),0,1,card);
  }
  MeshData mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
 }
 private static void vertex(BufferBuilder b,Vec3 p,float u,float v,Card c){
  b.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(c.seed%1,c.mode/3,Math.max(0,Math.min(1,c.phase)),c.alpha);
 }
}
