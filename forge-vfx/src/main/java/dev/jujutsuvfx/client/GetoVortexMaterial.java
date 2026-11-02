package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.function.Consumer;

/** Original noise material, submitted after the shaderpack has finished its world pass. */
final class GetoVortexMaterial {
 private static ShaderInstance shader;
 static void setShader(ShaderInstance value){shader=value;}
 static boolean ready(){return shader!=null;}
 static void draw(Matrix4f viewProjection,Consumer<VertexConsumer> geometry){
  if(shader==null)return;
  float[] color=RenderSystem.getShaderColor().clone();
  try(DomainRenderer.State ignored=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
   RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(1,1,1,1);
   shader.safeGetUniform("ViewProjection").set(viewProjection);
   RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
   RenderSystem.disableCull();RenderSystem.enableBlend();
   RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE);
   BufferBuilder buffer=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
   geometry.accept(buffer);
   BufferBuilder.RenderedBuffer mesh=buffer.end();if(!mesh.isEmpty())BufferUploader.drawWithShader(mesh);else mesh.release();
  }finally{RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}
 }
}
