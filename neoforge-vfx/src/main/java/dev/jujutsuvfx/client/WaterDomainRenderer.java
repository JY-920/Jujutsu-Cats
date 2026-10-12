package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.RiftShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** A flat water mirror with an owned reflection target, independent of Iris's scene attachments. */
final class WaterDomainRenderer {
    static final float SURFACE_HEIGHT=.065f;
    private static ShaderInstance water,reflection;
    private static TextureTarget mirror;
    static void setWater(ShaderInstance shader){water=shader;}
    static void setReflection(ShaderInstance shader){reflection=shader;}
    static boolean ready(){return water!=null&&reflection!=null;}
    static void release(){releaseMirror();WaterSceneCapture.release();}
    private static void releaseMirror(){if(mirror!=null){mirror.destroyBuffers();mirror=null;}}

    static void render(RenderLevelStageEvent event,MalevolentShrine shrine) {
        Minecraft mc=Minecraft.getInstance();
        if(!ready()||mc.level==null)return;
        Vec3 surface=shrine.center.add(0,SURFACE_HEIGHT,0);
        Vec3 offset=surface.subtract(event.getCamera().getPosition());
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick=ClientEffectClock.ticks();
        try(DomainRenderer.State ignored=new DomainRenderer.State()) {
            boolean sceneAvailable=WaterSceneCapture.capture();
            reflect(event,shrine,surface,offset,tick,partial);
            water.setSampler("Reflection",mirror.getColorTextureId());
            water.setSampler("ReflectionDepth",mirror.getDepthTextureId());
            water.setSampler("SceneColor",WaterSceneCapture.color());
            water.setSampler("SceneDepth",WaterSceneCapture.depth());
            water.safeGetUniform("UseScene").set(sceneAvailable?1f:0f);
            water.safeGetUniform("InverseViewProjection").set(new Matrix4f(event.getProjectionMatrix())
                    .mul(event.getModelViewMatrix()).invert());
            water.safeGetUniform("RiftView").set(event.getModelViewMatrix());
            water.safeGetUniform("RiftProjection").set(event.getProjectionMatrix());
            water.safeGetUniform("Offset").set((float)offset.x,(float)offset.y,(float)offset.z);
            water.safeGetUniform("Time").set(shrine.age(tick,partial));
            water.safeGetUniform("Radius").set(shrine.radius);
            water.safeGetUniform("Expansion").set(shrine.expansion(tick,partial));
            water.safeGetUniform("Visibility").set(shrine.visibility(tick,partial));
            RenderSystem.setShader(()->water);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);RenderSystem.disableCull();
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            float r=shrine.radius+.15f;
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            vertex(b,new Vec3(-r,0,-r),0,0,-1);vertex(b,new Vec3(r,0,-r),1,0,-1);
            vertex(b,new Vec3(r,0,r),1,1,-1);vertex(b,new Vec3(-r,0,r),0,1,-1);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }

    private static void reflect(RenderLevelStageEvent event,MalevolentShrine shrine,Vec3 surface,Vec3 offset,
                                long tick,float partial) {
        Minecraft mc=Minecraft.getInstance();
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        float[] clear=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            // Preserve aspect ratio; half resolution is enough after the water ripple filter.
            float ratio=Math.min(.5f,1024f/mc.getMainRenderTarget().width);
            int w=Math.max(1,Math.round(mc.getMainRenderTarget().width*ratio));
            int h=Math.max(1,Math.round(mc.getMainRenderTarget().height*ratio));
            if(mirror==null||mirror.width!=w||mirror.height!=h) {
                releaseMirror();mirror=new TextureTarget(w,h,true,Minecraft.ON_OSX);
                mirror.setClearColor(0,0,0,0);
                mirror.setFilterMode(GL11.GL_LINEAR);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.depthMask(true);
            mirror.clear(Minecraft.ON_OSX);mirror.bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableBlend();RenderSystem.disableCull();
            reflection.safeGetUniform("RiftView").set(event.getModelViewMatrix());
            reflection.safeGetUniform("RiftProjection").set(event.getProjectionMatrix());
            reflection.safeGetUniform("Offset").set((float)offset.x,(float)offset.y,(float)offset.z);
            reflection.safeGetUniform("Reveal").set(shrine.reveal(tick,partial));
            reflection.safeGetUniform("Visibility").set(shrine.visibility(tick,partial));
            reflection.safeGetUniform("Radius").set(shrine.radius);
            reflection.safeGetUniform("Mode").set(0f);
            Vec3 base=shrine.shrineBase.subtract(surface);
            Matrix4f model=new Matrix4f().translation((float)base.x,
                    (float)base.y-(1-shrine.reveal(tick,partial))*.65f*shrine.scale,(float)base.z)
                    .rotateY((float)Math.atan2(shrine.front.x,shrine.front.z)).scale(shrine.scale);
            reflection.safeGetUniform("LocalToWater").set(model);
            RenderSystem.setShader(()->reflection);
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(ShrineMesh.Vertex v:ShrineMesh.VERTICES)
                b.addVertex(v.p().x(),v.p().y(),v.p().z()).setUv(v.material(),0).setColor(v.color());
            BufferUploader.drawWithShader(b.buildOrThrow());
            // Mirror the same directional blade front and fade as the world-space cuts.
            var cuts=ClientEvents.RIFTS.stream().filter(r->r.age(tick,partial)>0&&r.age(tick,partial)<r.duration()).toList();
            if(!cuts.isEmpty()) {
                reflection.safeGetUniform("LocalToWater").set(new Matrix4f());
                reflection.safeGetUniform("Mode").set(1f);
                b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
                for(Rift cut:cuts) {
                    float age=cut.age(tick,partial),opening=cut.opening(age);
                    Rift pose=cut.pose(age);float w0=cut.halfWidth(opening)*1.15f,h0=cut.halfHeight(opening);
                    // Opacity, progress, slash flag and the same material seed used by the blade and halo.
                    int tint=(Math.round((cut.seed()%1)*255)<<24)|(Math.round(opening*255)<<16)
                            |(Math.round((cut.slash()?RiftShape.cutProgress(age):1)*255)<<8)|(cut.slash()?255:0);
                    vertex(b,pose.point(-w0,-h0,0).subtract(surface),-1.15f,-1,tint);
                    vertex(b,pose.point(w0,-h0,0).subtract(surface),1.15f,-1,tint);
                    vertex(b,pose.point(w0,h0,0).subtract(surface),1.15f,1,tint);
                    vertex(b,pose.point(-w0,h0,0).subtract(surface),-1.15f,1,tint);
                }
                BufferUploader.drawWithShader(b.buildOrThrow());
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);
            if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void vertex(BufferBuilder b,Vec3 p,float u,float v,int color) {
        b.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(color);
    }
}
