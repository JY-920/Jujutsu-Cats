package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.List;

/** Swept silver-gold blade ribbons. World geometry, one draw, no scene copy or shrine material. */
final class MahoragaBladeRenderer {
    private record Stroke(CatCombatCue cue,long born){
        float age(float partial){return Math.max(0,(ClientEffectClock.ticks()-born+partial)*.05f);}
    }
    private static final List<Stroke> STROKES=new ArrayList<>();
    private static ShaderInstance shader;
    static void setShader(ShaderInstance value){shader=value;}
    static void clear(){STROKES.clear();}
    static void tick(){STROKES.removeIf(s->s.age(0)>.48f);}
    static void receive(CatCombatCue cue){
        if(!CatCombatMoves.blade(cue.first())||(cue.second()&~11)!=0)return;
        var level=Minecraft.getInstance().level;
        if(level==null||!(level.getEntity(cue.actor()) instanceof SorcererCat cat)||!cat.isMahoraga())return;
        if(STROKES.size()>=24)STROKES.remove(0);
        STROKES.add(new Stroke(cue,ClientEffectClock.ticks()));
    }
    static void render(RenderLevelStageEvent event){
        if(shader==null||STROKES.isEmpty())return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=event.getCamera().getPosition();
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            RenderSystem.setShader(()->shader);
            shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()));
            shader.safeGetUniform("Time").set((ClientEffectClock.ticks()%24000+partial)*.05f);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
            RenderSystem.disableCull();RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(Stroke stroke:STROKES){
                if(stroke.cue.from().distanceToSqr(camera)>80*80)continue;
                float age=stroke.age(partial);if(age>=.48f)continue;
                if((stroke.cue.second()&8)!=0)impact(b,stroke.cue,age,camera);
                else sweep(b,stroke.cue,age,camera);
            }
            MeshData mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
        }
    }
    private static void sweep(BufferBuilder b,CatCombatCue p,float age,Vec3 camera){
        Vec3 axis=p.to().subtract(p.from()).normalize();if(axis.lengthSqr()<.01)axis=new Vec3(0,0,1);
        Vec3 right=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=right.cross(axis).normalize();
        int variant=p.first()==CatCombatMoves.BLADE_CHAIN?p.second()&3:0;
        double tilt=p.first()==CatCombatMoves.BLADE_RISE?1.40:p.first()==CatCombatMoves.BLADE_CHOP?-1.20:
                variant==1?2.65:variant==2?-.60:.20;
        Vec3 sweep=right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        float radius=Mth.clamp((float)p.from().distanceTo(p.to()),1.5f,4.8f);
        float head=Mth.clamp(age/.16f,0,1),tail=Mth.clamp((age-.13f)/.31f,0,1);
        float opacity=(1-smooth((age-.16f)/.32f))*smooth(age/.025f);
        Vec3 center=p.from().subtract(camera);
        for(int layer=0;layer<3;layer++){
            float r=radius*(1-layer*.055f),width=(layer==0?.32f:.08f)*(p.first()==CatCombatMoves.BLADE_CHOP?1.20f:1);
            float alpha=opacity*(layer==0?.88f:.50f);
            Vec3 origin=center.add(up.scale(layer*.055));
            for(int i=0;i<28;i++){
                float u0=Math.max(tail,i/28f),u1=Math.min(head,(i+1)/28f);if(u1<=u0)continue;
                float a0=-1.25f+u0*2.50f,a1=-1.25f+u1*2.50f;
                Vec3 n0=axis.scale(Math.cos(a0)).add(sweep.scale(Math.sin(a0)));
                Vec3 n1=axis.scale(Math.cos(a1)).add(sweep.scale(Math.sin(a1)));
                float w0=width*(.15f+.85f*Mth.sin(u0*Mth.PI)),w1=width*(.15f+.85f*Mth.sin(u1*Mth.PI));
                float end0=smooth((u0-tail)/.09f)*(.65f+.35f*smooth((head-u0)/.06f));
                float end1=smooth((u1-tail)/.09f)*(.65f+.35f*smooth((head-u1)/.06f));
                vertex(b,origin.add(n0.scale(r-w0)),u0,0,alpha*end0,layer);
                vertex(b,origin.add(n0.scale(r+w0*.30)),u0,1,alpha*end0,layer);
                vertex(b,origin.add(n1.scale(r+w1*.30)),u1,1,alpha*end1,layer);
                vertex(b,origin.add(n1.scale(r-w1)),u1,0,alpha*end1,layer);
            }
        }
    }
    private static void impact(BufferBuilder b,CatCombatCue p,float age,Vec3 camera){
        if(age>.28f)return;
        float fade=1-smooth(age/.28f),travel=1-(float)Math.exp(-age*16);
        Vec3 axis=p.to().subtract(p.from()).normalize();if(axis.lengthSqr()<.01)axis=new Vec3(0,0,1);
        Vec3 right=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),up=right.cross(axis).normalize();
        Vec3 center=p.to().subtract(camera).subtract(axis.scale(.16));
        for(int i=0;i<7;i++){
            float seed=Math.floorMod(p.seed()+i*197,997)/997f;
            double angle=i*2.399+seed*.65;
            Vec3 radial=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 across=axis.cross(radial).normalize();
            float length=(.25f+seed*.65f)*fade;
            Vec3 start=center.add(radial.scale(.10+travel*(.35+seed)));
            Vec3 end=start.add(radial.scale(length));float width=(.025f+seed*.030f)*fade;
            vertex(b,start.subtract(across.scale(width)),0,0,fade,1);
            vertex(b,start.add(across.scale(width)),0,1,fade,1);
            vertex(b,end.add(across.scale(width*.08)),1,1,0,1);
            vertex(b,end.subtract(across.scale(width*.08)),1,0,0,1);
        }
    }
    private static void vertex(BufferBuilder b,Vec3 p,float u,float v,float alpha,int layer){
        b.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v)
                .setColor(255,layer==0?245:227,layer==0?220:158,Math.round(Mth.clamp(alpha,0,1)*255));
    }
    private static float smooth(float v){v=Mth.clamp(v,0,1);return v*v*(3-2*v);}
}
