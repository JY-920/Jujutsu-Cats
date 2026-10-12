package dev.jujutsuvfx.client;

import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.jujutsuvfx.mixin.EffectBlendModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** The retained shrine: original shrine mesh above deep teal mirror water. */
final class DomainRenderer {
    private static ShaderInstance geometry;
    private static VertexBuffer shrineMesh;
    static void setGeometry(ShaderInstance value) { geometry = value; }
    static boolean ready() { return geometry != null && WaterDomainRenderer.ready(); }
    static void release() {
        WaterDomainRenderer.release();
        if (shrineMesh != null) { shrineMesh.close(); shrineMesh = null; }
    }

    static void setRiftBoundary(ShaderInstance shader,MalevolentShrine shrine,Vec3 camera,long tick,float partial) {
        Vec3 center=shrine==null?Vec3.ZERO:shrine.center.subtract(camera);
        shader.safeGetUniform("DomainCenter").set((float)center.x,(float)center.y,(float)center.z);
        shader.safeGetUniform("DomainRadius").set(shrine==null?0f:shrine.radius*shrine.expansion(tick,partial));
    }

    static void render(RenderLevelStageEvent event, MalevolentShrine shrine) {
        Minecraft mc=Minecraft.getInstance();
        if(geometry==null||mc.level==null)return;
        long tick=ClientEffectClock.ticks();
        float partial=event.getPartialTick();
        float visibility=shrine.visibility(tick,partial);
        Vec3 camera=event.getCamera().getPosition();
        if(visibility<.005f||camera.distanceToSqr(shrine.center)>160*160)return;
        try(State ignored=new State()) {
            WaterDomainRenderer.render(event,shrine);
            geometry.safeGetUniform("RiftView").set(event.getPoseStack().last().pose());
            geometry.safeGetUniform("RiftProjection").set(event.getProjectionMatrix());
            geometry.safeGetUniform("Time").set(shrine.age(tick,partial));
            geometry.safeGetUniform("Visibility").set(visibility);
            geometry.safeGetUniform("Reveal").set(shrine.reveal(tick,partial));
            geometry.safeGetUniform("ModelHeight").set(ShrineMesh.MODEL_HEIGHT);
            geometry.safeGetUniform("Mode").set(0f);
            RenderSystem.setShader(()->geometry);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.depthMask(true);
            if(event.getFrustum().isVisible(new AABB(shrine.shrineBase,shrine.shrineBase).inflate(ShrineMesh.BOUND_RADIUS*shrine.scale))) {
                Vec3 offset=shrine.shrineBase.subtract(camera);
                Matrix4f transform=new Matrix4f().translation((float)offset.x,
                        (float)offset.y-(1-shrine.reveal(tick,partial))*.65f*shrine.scale,(float)offset.z)
                        .rotateY((float)Math.atan2(shrine.front.x,shrine.front.z)).scale(shrine.scale);
                geometry.safeGetUniform("ModelToCamera").set(transform);
                ensureShrineMesh();
                shrineMesh.bind();
                try { shrineMesh.drawWithShader(event.getPoseStack().last().pose(), event.getProjectionMatrix(), geometry); }
                finally { VertexBuffer.unbind(); }
            }
        }
    }

    /** Added detail is uploaded once, instead of rebuilt on every frame. */
    private static void ensureShrineMesh() {
        if (shrineMesh != null) return;
        BufferBuilder buffer=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(ShrineMesh.Vertex vertex:ShrineMesh.VERTICES)
            buffer.vertex(vertex.p().x(),vertex.p().y(),vertex.p().z()).uv(vertex.material(),0).color(vertex.color()).endVertex();
        VertexBuffer candidate = new VertexBuffer(VertexBuffer.Usage.STATIC);
        candidate.bind();
        try { candidate.upload(buffer.end()); shrineMesh = candidate; }
        catch (RuntimeException error) { candidate.close(); throw error; }
        finally { VertexBuffer.unbind(); }
    }

    static final class State implements AutoCloseable {
        private final BlendMode cachedBlend = EffectBlendModeAccessor.jujutsuvfx$getLastApplied();
        private final EffectSamplers samplers = new EffectSamplers();
        final ShaderInstance shader=RenderSystem.getShader();
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final boolean stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        final int equation=GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB),
                alphaEquation=GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_ALPHA);
        final int[] colorMask=new int[4];
        State(){
            // Reconcile cached GL state before a pass changes it. Other renderers can use raw GL.
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
            RenderSystem.blendFuncSeparate(sr,dr,sa,da);
            GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK,colorMask);
            RenderSystem.colorMask(true,true,true,true);GL11.glColorMask(true,true,true,true);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        }
        public void close() {
            RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(sr,dr,sa,da);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShader(()->shader);
            RenderSystem.colorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);
            GL11.glColorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);
            if(stencil)GL11.glEnable(GL11.GL_STENCIL_TEST);else GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.blendEquation(equation);org.lwjgl.opengl.GL20.glBlendEquationSeparate(equation,alphaEquation);
            samplers.close();
            // 1.20.1 tracks this separately from RenderSystem's GL state. Leaving an
            // additive effect cached makes the next GUI shader override the vignette's
            // ZERO / ONE_MINUS_SRC_COLOR blend and paint its opaque black center.
            EffectBlendModeAccessor.jujutsuvfx$setLastApplied(cachedBlend);
        }
    }
}
