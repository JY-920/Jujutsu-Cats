package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.core.ShadowMotion;
import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/** One small opaque ink pool and its feathered edge; no particles or frame-buffer copies. */
final class MahoragaShadowRenderer {
    private static VertexBuffer poolMesh;
    static void release(){if(poolMesh!=null){poolMesh.close();poolMesh=null;}}
    private MahoragaShadowRenderer(){}
    static void render(RenderLevelStageEvent event){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();
        var cats=mc.level.getEntitiesOfClass(SorcererCat.class,new AABB(camera,camera).inflate(96),
                c->c.isMahoraga()&&c.combatMove()==CatCombatMoves.MAHORAGA_ARRIVAL);
        if(cats.isEmpty())return;
        float partial=event.getPartialTick();
        try(DomainRenderer.State state=new DomainRenderer.State()){
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
            RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            BufferBuilder b=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
            for(SorcererCat cat:cats){
                boolean retreat=cat.punchKind()==CatCombatMoves.SHADOW_RETREAT;
                float age=cat.punchAge(partial),pool=ShadowMotion.pool(age,retreat);if(pool<.001f)continue;
                Vec3 center=cat.getPosition(partial).subtract(camera).add(0,.09,0);
                double radius=2.55*(.36+.64*pool),time=Math.floor(age*1.2)/1.2;
                for(int i=0;i<80;i++){
                    double a=i*Math.PI/40,beta=(i+1)*Math.PI/40;
                    double r0=radius*(1+.09*Math.sin(a*7+time*.13)+.05*Math.sin(a*13-time*.19));
                    double r1=radius*(1+.09*Math.sin(beta*7+time*.13)+.05*Math.sin(beta*13-time*.19));
                    Vec3 p=center.add(Math.cos(a)*r0,0,Math.sin(a)*r0),q=center.add(Math.cos(beta)*r1,0,Math.sin(beta)*r1);
                    vertex(b,center,pool);vertex(b,p,pool);vertex(b,q,pool);
                    Vec3 outerP=center.add(p.subtract(center).scale(1.24)),outerQ=center.add(q.subtract(center).scale(1.24));
                    vertex(b,p,pool);vertex(b,outerP,0);vertex(b,outerQ,0);
                    vertex(b,p,pool);vertex(b,outerQ,0);vertex(b,q,pool);
                }
            }
            BufferBuilder.RenderedBuffer mesh=b.end();
            if(poolMesh==null)poolMesh=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            poolMesh.bind();
            try{poolMesh.upload(mesh);poolMesh.drawWithShader(event.getPoseStack().last().pose(),event.getProjectionMatrix(),GameRenderer.getPositionColorShader());}
            finally{VertexBuffer.unbind();}
        }
    }
    private static void vertex(BufferBuilder b,Vec3 p,float opacity){
        b.vertex((float)p.x,(float)p.y,(float)p.z).color(2,1,6,Math.round(opacity*255)).endVertex();
    }
}
