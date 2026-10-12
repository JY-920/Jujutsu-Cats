package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.RiftShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Draw all temporary cuts and newly opening creature scars together, regardless of their count. */
final class SlashBatchRenderer {
    private static ShaderInstance shader;
    static void setShader(ShaderInstance value){shader=value;}

    static void render(RenderLevelStageEvent event,List<Rift> rifts,MalevolentShrine shrine) {
        Minecraft mc=Minecraft.getInstance();if(shader==null||mc.level==null)return;
        long tick=ClientEffectClock.ticks();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=event.getCamera().getPosition();
        var visible=rifts.stream().filter(r->r.slash()&&r.age(tick,partial)>0&&r.age(tick,partial)<r.duration()
                &&(!r.persistent()||r.age(tick,partial)<RiftShape.STRIKE_SECONDS)
                &&r.opening(r.age(tick,partial))>.005f
                &&camera.distanceToSqr(r.center())<Math.pow(160+Math.max(0,r.height()*.5-16),2)
                &&event.getFrustum().isVisible(new AABB(r.center(),r.center()).inflate(r.height()))).toList();
        if(visible.isEmpty())return;
        try(DomainRenderer.State ignored=new DomainRenderer.State()) {
            shader.safeGetUniform("RiftView").set(event.getModelViewMatrix());
            shader.safeGetUniform("RiftProjection").set(event.getProjectionMatrix());
            DomainRenderer.setRiftBoundary(shader,shrine,camera,tick,partial);
            shader.safeGetUniform("FocusMono").set(shrine!=null&&ClientShrineFocus.active(shrine.session)?1f:0f);
            RenderSystem.setShader(()->shader);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(Rift r:visible) {
                float age=r.age(tick,partial),opening=r.opening(age);
                Rift pose=r.pose(age);float w=r.halfWidth(opening),h=r.halfHeight(opening),d=r.halfDepth(opening);
                int packed=(channel((r.seed()%1))<<24)|(channel(opening)<<16)
                        |(channel(RiftShape.cutProgress(age))<<8)|channel(RiftShape.impact(age));
                // Only the near face is needed for this thin, opaque slit.
                float side=camera.subtract(pose.center()).dot(pose.normal())>=0?1:-1;
                vertex(b,pose,camera,-w*1.15f,-h,d*side,-1.15f,-1,packed);
                vertex(b,pose,camera,w*1.15f,-h,d*side,1.15f,-1,packed);
                vertex(b,pose,camera,w*1.15f,h,d*side,1.15f,1,packed);
                vertex(b,pose,camera,-w*1.15f,h,d*side,-1.15f,1,packed);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }
    private static int channel(float value){return Math.round(Math.clamp(value,0,1)*255);}
    private static void vertex(BufferBuilder b,Rift r,Vec3 camera,float x,float y,float z,float u,float v,int packed) {
        Vec3 p=r.point(x,y,z).subtract(camera);
        b.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(packed);
    }
}
