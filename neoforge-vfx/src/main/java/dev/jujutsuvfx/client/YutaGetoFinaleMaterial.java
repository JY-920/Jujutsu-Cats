package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import java.util.function.Consumer;

/** Depth-tested late world pass: one solid batch followed by one soft-light batch. */
final class YutaGetoFinaleMaterial {
 private static ShaderInstance shader,glowShader;
 static void setShader(ShaderInstance value){shader=value;}
 static void setGlowShader(ShaderInstance value){glowShader=value;}
 static boolean ready(){return shader!=null&&glowShader!=null;}
 static void draw(Matrix4f viewProjection,boolean solid,Consumer<VertexConsumer> geometry){
  ShaderInstance material=solid?shader:glowShader;
  if(material==null)return;
  float[] color=RenderSystem.getShaderColor().clone();
  try(DrawState ignored=new DrawState();SceneSnapshot.Target target=new SceneSnapshot.Target()){
   try{
    RenderSystem.setShader(()->material);RenderSystem.setShaderColor(1,1,1,1);
    material.safeGetUniform("ViewProjection").set(viewProjection);
    RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(solid);
    RenderSystem.disableCull();GL14.glBlendEquation(GL14.GL_FUNC_ADD);
    if(solid){
     RenderSystem.enableBlend();
     RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE);
    }
    else{
     RenderSystem.enableBlend();
     RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
    }
    BufferBuilder buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
    geometry.accept(buffer);
    MeshData mesh=buffer.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
   }finally{
    // Also invalidate ShaderInstance's lastProgramId on empty meshes or exceptions,
    // before DrawState restores the native program that preceded this late pass.
    material.clear();
   }
  }finally{RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}
 }
 /** This late pass never touches the framebuffer's color mask, stencil or clear values. */
 private static final class DrawState implements AutoCloseable {
  private final ShaderInstance previous=RenderSystem.getShader();
  private final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
  private final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
  private final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
  private final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
  private final int sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
  private final int equation=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),alphaEquation=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
  private final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
  private final int array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),element=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
  DrawState(){
   // Reconcile RenderSystem caches with a shaderpack's native state without changing that state.
   if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
   if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
   if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
   RenderSystem.depthMask(write);RenderSystem.depthFunc(func);RenderSystem.blendFuncSeparate(sr,dr,sa,da);
  }
  public void close(){
   RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
   if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
   if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
   RenderSystem.blendFuncSeparate(sr,dr,sa,da);
   if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
   GL20.glBlendEquationSeparate(equation,alphaEquation);
   RenderSystem.setShader(()->previous);
   // BufferUploader caches its last immediate VAO; a native restore must invalidate it.
   BufferUploader.invalidate();
   GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,array);
   if(vao!=0)GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,element);
   GL20.glUseProgram(program);
  }
 }
}
