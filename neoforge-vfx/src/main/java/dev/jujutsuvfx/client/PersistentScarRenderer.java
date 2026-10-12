package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.RiftShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Settled scars share one static GPU buffer, instead of thousands of independent draw calls. */
final class PersistentScarRenderer {
    private static ShaderInstance shader;
    private static VertexBuffer mesh;
    private static List<Rift> cached=List.of();
    private static Vec3 origin=Vec3.ZERO;
    static void setShader(ShaderInstance value){shader=value;}
    static void release(){if(mesh!=null){mesh.close();mesh=null;}cached=List.of();}

    static void render(RenderLevelStageEvent event,List<Rift> all,MalevolentShrine shrine){
        Minecraft mc=Minecraft.getInstance();if(shader==null||mc.level==null)return;
        long tick=ClientEffectClock.ticks();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<Rift> settled=all.stream().filter(r->r.persistent()&&r.age(tick,partial)>=RiftShape.STRIKE_SECONDS
                &&r.age(tick,partial)<r.duration()).toList();
        if(settled.isEmpty()){release();return;}
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            if(!settled.equals(cached))rebuild(settled);
            Vec3 camera=event.getCamera().getPosition();Vec3 offset=origin.subtract(camera);
            shader.safeGetUniform("Offset").set((float)offset.x,(float)offset.y,(float)offset.z);
            shader.safeGetUniform("RiftView").set(event.getModelViewMatrix());
            shader.safeGetUniform("RiftProjection").set(event.getProjectionMatrix());
            DomainRenderer.setRiftBoundary(shader,shrine,camera,tick,partial);
            Rift first=settled.getFirst();
            shader.safeGetUniform("Fade").set(first.opening(first.age(tick,partial)));
            RenderSystem.setShader(()->shader);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            mesh.bind();
            try{mesh.drawWithShader(event.getModelViewMatrix(),event.getProjectionMatrix(),shader);}
            finally{VertexBuffer.unbind();}
        }
    }
    private static void rebuild(List<Rift> scars){
        release();origin=scars.getFirst().center();
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(Rift r:scars){
            float w=r.halfWidth(1),h=r.halfHeight(1),d=r.halfDepth(1);
            for(int side:new int[]{-1,1}){
                vertex(b,r,-w*1.15f,-h,side*d,-1.15f,-1);
                vertex(b,r,w*1.15f,-h,side*d,1.15f,-1);
                vertex(b,r,w*1.15f,h,side*d,1.15f,1);
                vertex(b,r,-w*1.15f,h,side*d,-1.15f,1);
            }
        }
        mesh=new VertexBuffer(VertexBuffer.Usage.STATIC);mesh.bind();
        try{mesh.upload(b.buildOrThrow());}finally{VertexBuffer.unbind();}
        cached=scars;
    }
    private static void vertex(BufferBuilder b,Rift r,float x,float y,float z,float u,float v){
        Vec3 p=r.point(x,y,z).subtract(origin);
        // Use the same stable material seed as the moving blade, so settling never changes its surface.
        int color=(Math.round((r.seed()%1)*255)<<24)|0x00FFFFFF;
        b.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(color);
    }
}
