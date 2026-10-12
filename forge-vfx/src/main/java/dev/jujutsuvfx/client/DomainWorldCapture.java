package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** One frame's entity image before domain grading, with optional local terrain-depth removal. */
final class DomainWorldCapture {
    private static final SceneSnapshot TERRAIN=new SceneSnapshot(),ENTITIES=new SceneSnapshot();
    private static ShaderInstance clearShader;
    private static boolean wanted,ready,cleared;
    private static Object level;
    private DomainWorldCapture(){}
    static void setShader(ShaderInstance shader){release();clearShader=shader;}
    static void release(){TERRAIN.release();ENTITIES.release();wanted=ready=cleared=false;level=null;}
    static void beginFrame(){
        Minecraft mc=Minecraft.getInstance();if(level!=mc.level){release();level=mc.level;}
        ready=cleared=false;wanted=mc.level!=null&&(ClientEvents.visualVoid()!=null||ClientShrineFocus.needsCapture());
        if(!wanted){TERRAIN.release();ENTITIES.release();}
    }
    static void terrain(RenderLevelStageEvent event){
        if(!wanted||clearShader==null)return;
        UnlimitedVoid domain=ClientEvents.visualVoid();if(domain==null)return;
        long tick=ClientEffectClock.ticks();float partial=event.getPartialTick();
        Vec3 camera=event.getCamera().getPosition();
        if(domain.age(tick,partial)<1||domain.visibility(tick,partial)<.99f
                ||!CatSpells.interior(domain,camera,tick,partial)||DomainBarrierEffects.progress(domain,partial)>0)return;
        try(DomainRenderer.State state=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
            if(!TERRAIN.capture())return;
            Matrix4f matrix=new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
            clearShader.setSampler("SceneDepth",TERRAIN.depth());
            clearShader.safeGetUniform("InverseViewProjection").set(matrix.invert());
            Vec3 center=domain.center.subtract(camera);
            clearShader.safeGetUniform("CenterRelative").set((float)center.x,(float)center.y,(float)center.z);
            clearShader.safeGetUniform("Radius").set(domain.radius*domain.expansion(tick,partial));
            RenderSystem.setShader(()->clearShader);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.disableCull();
            quad();cleared=true;
        }
    }
    static void entities(){
        if(!wanted)return;
        // Finish queued entity geometry before block entities and translucent terrain are drawn.
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        try(DomainRenderer.State state=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
            ready=ENTITIES.capture();
        }
    }
    static void bind(ShaderInstance shader,boolean requireCleared){
        boolean use=ready&&(!requireCleared||cleared);
        shader.safeGetUniform("RawSceneReady").set(use?1f:0f);
        shader.setSampler("RawSceneColor",use?ENTITIES.color():0);
        shader.setSampler("RawSceneDepth",use?ENTITIES.depth():0);
    }
    private static void quad(){
        BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(-1,-1,1).uv(0,0).color(-1).endVertex();b.vertex(1,-1,1).uv(1,0).color(-1).endVertex();
        b.vertex(1,1,1).uv(1,1).color(-1).endVertex();b.vertex(-1,1,1).uv(0,1).color(-1).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
}
