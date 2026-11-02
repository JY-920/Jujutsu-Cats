package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.WorldCutPath;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Procedural razor and localized scene refraction; one scene capture for all active blades. */
final class WorldCutBladeRenderer {
 private static ShaderInstance shader,airShader;
 private static final SceneSnapshot SCENE=new SceneSnapshot();
 private WorldCutBladeRenderer(){}
 static void setShader(ShaderInstance value){shader=value;}
 static void setAirShader(ShaderInstance value){airShader=value;if(value==null)clear();}
 static void clear(){SCENE.release();}
 static void render(RenderLevelStageEvent event,List<WorldCutRenderer.Blade> blades,long now,float partial){
  if(blades.isEmpty()){clear();return;}
  if(shader==null)return;
  try(DomainRenderer.State state=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
   renderAir(event,blades,now,partial);
   RenderSystem.setShader(()->shader);
   shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()));
   shader.safeGetUniform("Time").set((now%24000+partial)*.05f);
   RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
   RenderSystem.disableCull();RenderSystem.enableBlend();
   RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
   BufferBuilder out=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
   Vec3 camera=event.getCamera().getPosition();
   for(var blade:blades){
    var hit=blade.hit();float ticks=now-blade.born()+partial,age=ticks*.05f;boolean traveling=hit.target()<0;
    if(age<0||age>(traveling?.48f:.22f))continue;
    double travel=traveling?Math.min(WorldCutPath.RANGE,ticks*WorldCutPath.SPEED):0;
    Vec3 c=hit.center().add(hit.forward().scale(travel)).subtract(camera);
    Vec3 stroke=hit.stroke(),up=hit.forward().cross(stroke).normalize();
    float fade=1-smooth((age-(traveling?.29f:.05f))/(traveling?.19f:.17f));
    double half=hit.bladeLength()*.5;
    float seed=(hit.seed()&65535)/65535f;
    Vec3 left=c.subtract(stroke.scale(half)),right=c.add(stroke.scale(half));
    // Front-facing strip: all edge shape, branching filaments and glow are evaluated per pixel.
    Vec3 height=up.scale(traveling?.95:.70);
    quad(out,left.subtract(height),right.subtract(height),right.add(height),left.add(height),seed,0,fade);
    if(traveling){
     Vec3 tail=hit.forward().scale(-Math.min(travel,3.6));
     quad(out,left,right,right.add(tail),left.add(tail),seed,1,fade*.85f);
    }
   }
   MeshData mesh=out.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
  }
 }
 private static void renderAir(RenderLevelStageEvent event,List<WorldCutRenderer.Blade> blades,long now,float partial){
  float strength=Minecraft.getInstance().options.screenEffectScale().get().floatValue();
  if(airShader==null||strength<.001f)return;
  // Avoid even allocating a snapshot after all visible blades have finished fading.
  boolean active=blades.stream().anyMatch(b->{float age=(now-b.born()+partial)*.05f;return age>=0&&age<(b.hit().target()<0?.48f:.22f);});
  if(!active){clear();return;}
  if(!SCENE.capture())return;
  int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
  RenderSystem.setShader(()->airShader);
  airShader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()));
  airShader.safeGetUniform("Time").set((now%24000+partial)*.05f);
  airShader.safeGetUniform("Viewport").set((float)viewport[0],(float)viewport[1],(float)viewport[2],(float)viewport[3]);
  airShader.safeGetUniform("Strength").set(strength);
  airShader.setSampler("SceneColor",SCENE.color());airShader.setSampler("SceneDepth",SCENE.depth());
  RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
  RenderSystem.disableCull();RenderSystem.enableBlend();
  RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE);
  BufferBuilder out=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
  Vec3 camera=event.getCamera().getPosition();
  for(var blade:blades){
   var hit=blade.hit();float ticks=now-blade.born()+partial,age=ticks*.05f;boolean traveling=hit.target()<0;
   if(age<0||age>=(traveling?.48f:.22f))continue;
   double travel=traveling?Math.min(WorldCutPath.RANGE,ticks*WorldCutPath.SPEED):0;
   Vec3 c=hit.center().add(hit.forward().scale(travel)).subtract(camera);
   Vec3 stroke=hit.stroke(),height=hit.forward().cross(stroke).normalize().scale(traveling?2.2:1.3);
   float fade=1-smooth((age-(traveling?.29f:.05f))/(traveling?.19f:.17f));
   Vec3 left=c.subtract(stroke.scale(hit.bladeLength()*.53)),right=c.add(stroke.scale(hit.bladeLength()*.53));
   quad(out,left.subtract(height),right.subtract(height),right.add(height),left.add(height),(hit.seed()&65535)/65535f,0,fade);
  }
  MeshData mesh=out.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
 }
 private static void quad(BufferBuilder out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float seed,float mode,float alpha){
  vertex(out,a,0,0,seed,mode,alpha);vertex(out,b,1,0,seed,mode,alpha);
  vertex(out,c,1,1,seed,mode,alpha);vertex(out,d,0,1,seed,mode,alpha);
 }
 private static void vertex(BufferBuilder out,Vec3 p,float u,float v,float seed,float mode,float alpha){
  out.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(seed,mode,0,alpha);
 }
 private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
