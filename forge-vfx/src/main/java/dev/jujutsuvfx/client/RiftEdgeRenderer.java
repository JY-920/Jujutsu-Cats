package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.RiftShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** One batched halo/refraction pass shared by sweeping cuts and retained creature scars. */
final class RiftEdgeRenderer {
    private static ShaderInstance shader;
    static void setShader(ShaderInstance value){shader=value;}
    static boolean ready(){return shader!=null;}
    record Frame(List<Rift> visible,boolean sceneReady) {}

    /** Capture after the domain background, before any cut can contaminate the refraction source. */
    static Frame prepare(RenderLevelStageEvent event,List<Rift> rifts) {
        Minecraft mc=Minecraft.getInstance();if(shader==null||mc.level==null)return new Frame(List.of(),false);
        long tick=ClientEffectClock.ticks();float partial=event.getPartialTick();
        Vec3 camera=event.getCamera().getPosition();
        var visible=rifts.stream().filter(r->r.slash()&&r.age(tick,partial)>0&&r.age(tick,partial)<r.duration()
                &&camera.distanceToSqr(r.center())<160*160
                &&event.getFrustum().isVisible(new AABB(r.center(),r.center()).inflate(r.height()))).toList();
        if(visible.isEmpty())return new Frame(visible,false);
        try(DomainRenderer.State ignored=new DomainRenderer.State()) {
            return new Frame(visible,WaterSceneCapture.capture());
        }
    }

    static void render(RenderLevelStageEvent event,Frame frame,MalevolentShrine shrine) {
        Minecraft mc=Minecraft.getInstance();
        if(shader==null||mc.level==null||frame.visible().isEmpty())return;
        long tick=ClientEffectClock.ticks();float partial=event.getPartialTick();
        Vec3 camera=event.getCamera().getPosition();
        try(DomainRenderer.State ignored=new DomainRenderer.State()) {
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            shader.setSampler("SceneColor",WaterSceneCapture.color());
            shader.setSampler("SceneDepth",WaterSceneCapture.depth());
            shader.safeGetUniform("UseScene").set(frame.sceneReady()?1f:0f);
            shader.safeGetUniform("Viewport").set((float)viewport[0],(float)viewport[1],(float)viewport[2],(float)viewport[3]);
            shader.safeGetUniform("RiftView").set(event.getPoseStack().last().pose());
            shader.safeGetUniform("RiftProjection").set(event.getProjectionMatrix());
            shader.safeGetUniform("Distortion").set(mc.options.screenEffectScale().get().floatValue());
            DomainRenderer.setRiftBoundary(shader,shrine,camera,tick,partial);
            shader.safeGetUniform("FocusMono").set(shrine!=null&&ClientShrineFocus.active(shrine.session)?1f:0f);
            RenderSystem.setShader(()->shader);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            BufferBuilder b=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(Rift r:frame.visible()) {
                float age=r.age(tick,partial),opening=r.opening(age);
                Rift pose=r.pose(age);
                float w=r.halfWidth(r.persistent()?1:opening),h=r.halfHeight(opening);
                float sign=camera.subtract(pose.center()).dot(pose.normal())>=0?1:-1;
                float z=sign*(r.halfDepth(opening)+.012f);
                int color=(channel(r.seed()%1)<<24)|(channel(opening)<<16)
                        |(channel(RiftShape.cutProgress(age))<<8)|channel(RiftShape.impact(age));
                vertex(b,pose,camera,-w*6,-h*1.03f,z,-6,-1.03f,color);
                vertex(b,pose,camera,w*6,-h*1.03f,z,6,-1.03f,color);
                vertex(b,pose,camera,w*6,h*1.03f,z,6,1.03f,color);
                vertex(b,pose,camera,-w*6,h*1.03f,z,-6,1.03f,color);
            }
            BufferUploader.drawWithShader(b.end());
        }
    }
    private static int channel(float value){return Math.round(dev.jujutsuvfx.platform.Numbers.clamp(value,0,1)*255);}
    private static void vertex(BufferBuilder b,Rift r,Vec3 camera,float x,float y,float z,float u,float v,int color) {
        Vec3 p=r.point(x,y,z).subtract(camera);
        b.vertex((float)p.x,(float)p.y,(float)p.z).uv(u,v).color(color).endVertex();
    }
}
