package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float visibility=shrine.visibility(tick,partial);
        Vec3 camera=event.getCamera().getPosition();
        if(visibility<.005f||camera.distanceToSqr(shrine.center)>160*160)return;
        try(State ignored=new State()) {
            WaterDomainRenderer.render(event,shrine);
            geometry.safeGetUniform("RiftView").set(event.getModelViewMatrix());
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
                try { shrineMesh.drawWithShader(event.getModelViewMatrix(), event.getProjectionMatrix(), geometry); }
                finally { VertexBuffer.unbind(); }
            }
        }
    }

    /** Added detail is uploaded once, instead of rebuilt on every frame. */
    private static void ensureShrineMesh() {
        if (shrineMesh != null) return;
        BufferBuilder buffer=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(ShrineMesh.Vertex vertex:ShrineMesh.VERTICES)
            buffer.addVertex(vertex.p().x(),vertex.p().y(),vertex.p().z()).setUv(vertex.material(),0).setColor(vertex.color());
        VertexBuffer candidate = new VertexBuffer(VertexBuffer.Usage.STATIC);
        candidate.bind();
        try { candidate.upload(buffer.buildOrThrow()); shrineMesh = candidate; }
        catch (RuntimeException error) { candidate.close(); throw error; }
        finally { VertexBuffer.unbind(); }
    }

    static final class State implements AutoCloseable {
        private final EffectSamplers samplers = new EffectSamplers();
        final ShaderInstance shader=RenderSystem.getShader();
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        public void close() {
            RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(sr,dr,sa,da);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShader(()->shader);
            samplers.close();
        }
    }
}
