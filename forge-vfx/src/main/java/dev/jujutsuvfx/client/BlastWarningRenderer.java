package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Loaded terrain only. One cached 128-segment perimeter per final cast, with a bright readable edge. */
final class BlastWarningRenderer {
    private static final int SEGMENTS=128;
    private static final Map<Integer,Ring> RINGS=new LinkedHashMap<>();
    private static ClientLevel world;
    private static ShaderInstance shader;
    private static final class Ring {
        final Vec3 center;final float radius;final UUID actor;
        final Vec3[] points=new Vec3[SEGMENTS];long until,sampled=-100;
        Ring(Vec3 center,float radius,UUID actor){this.center=center;this.radius=radius;this.actor=actor;}
    }
    static void setShader(ShaderInstance value){shader=value;}
    static void clear(){RINGS.clear();world=null;}
    private static void ensureWorld(){
        ClientLevel current=Minecraft.getInstance().level;if(world!=current){clear();world=current;}
        long now=ClientEffectClock.ticks();RINGS.values().removeIf(r->now>r.until);
    }
    static void receive(CatCombatCue cue){
        ensureWorld();if(world==null)return;
        if(cue.first()==0){RINGS.remove(cue.actor());return;}
        if(cue.first()<1||cue.first()>64||!(world.getEntity(cue.actor()) instanceof SorcererCat cat)||!cat.isGojo())return;
        Ring ring=RINGS.get(cue.actor());
        if(ring==null||!ring.actor.equals(cat.getUUID())||ring.center.distanceToSqr(cue.from())>.01){
            if(RINGS.size()>=8)RINGS.remove(RINGS.keySet().iterator().next());
            ring=new Ring(cue.from(),cue.first(),cat.getUUID());RINGS.put(cue.actor(),ring);
        }
        ring.until=ClientEffectClock.ticks()+40;
    }
    private static void sample(Ring ring,long now){
        if(now-ring.sampled<20)return;ring.sampled=now;
        for(int i=0;i<SEGMENTS;i++){
            double a=i*Math.PI*2/SEGMENTS,x=ring.center.x+Math.cos(a)*ring.radius,z=ring.center.z+Math.sin(a)*ring.radius;
            BlockPos cell=BlockPos.containing(x,ring.center.y,z);
            if(!world.hasChunkAt(cell)){ring.points[i]=null;continue;}
            int y=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,cell.getX(),cell.getZ());
            ring.points[i]=new Vec3(x,y+.085,z);
        }
    }
    static void render(RenderLevelStageEvent event){
        ensureWorld();if(shader==null||RINGS.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();long now=ClientEffectClock.ticks();
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            RenderSystem.setShader(()->shader);
            shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()));
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
            RenderSystem.disableCull();RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            BufferBuilder buffer=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            float pulse=.65f+.25f*(float)Math.sin(now*.12);
            for(Ring ring:RINGS.values()){
                if(ring.center.distanceToSqr(camera)>192*192)continue;
                sample(ring,now);
                for(int i=0;i<SEGMENTS;i++){
                    Vec3 a=ring.points[i],b=ring.points[(i+1)%SEGMENTS];if(a==null||b==null)continue;
                    Vec3 ra=new Vec3(a.x-ring.center.x,0,a.z-ring.center.z).normalize().scale(.65);
                    Vec3 rb=new Vec3(b.x-ring.center.x,0,b.z-ring.center.z).normalize().scale(.65);
                    vertex(buffer,a.subtract(ra).subtract(camera),0,pulse);
                    vertex(buffer,b.subtract(rb).subtract(camera),0,pulse);
                    vertex(buffer,b.add(rb).subtract(camera),1,pulse);
                    vertex(buffer,a.add(ra).subtract(camera),1,pulse);
                }
            }
            BufferBuilder.RenderedBuffer mesh=buffer.end();if(!mesh.isEmpty())BufferUploader.drawWithShader(mesh);else mesh.release();
        }
    }
    private static void vertex(BufferBuilder b,Vec3 p,float u,float alpha){
        b.vertex((float)p.x,(float)p.y,(float)p.z).uv(u,0).color(1f,.5f,1f,alpha).endVertex();
    }
}
