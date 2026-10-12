package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Rebuild the old scene, unfold the new scene over it, then composite along the spatial seam. */
final class DomainClashRenderer {
    private static ShaderInstance composite,particles;
    private static final SceneSnapshot WORLD=new SceneSnapshot(),SHRINE=new SceneSnapshot(),VOID=new SceneSnapshot();
    static void setComposite(ShaderInstance shader){composite=shader;}
    static void setParticles(ShaderInstance shader){particles=shader;}
    static boolean ready(){return composite!=null&&particles!=null;}
    static void release(){WORLD.release();SHRINE.release();VOID.release();}

    static boolean render(RenderLevelStageEvent event,MalevolentShrine shrine,UnlimitedVoid gojo,DomainClash clash,
                          Runnable shrineDraw,Runnable voidDraw){
        Minecraft mc=Minecraft.getInstance();if(composite==null||mc.level==null)return false;
        long now=ClientEffectClock.ticks();float partial=event.getPartialTick();
        Vec3 camera=event.getCamera().getPosition();
        boolean exterior=clash!=null&&DomainClash.surrounds(shrine,gojo,now,partial)
                &&!dev.jujutsuvfx.core.DomainVolume.contains(gojo.center,gojo.radius*gojo.expansion(now,partial),camera);
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            if(!WORLD.capture())return false;
            // Render into single-sample owned layers. Never blit resolved depth back into an Iris MSAA target.
            try(SceneSnapshot.Target target=new SceneSnapshot.Target()){
                // A fresh world seed each frame avoids feedback. The incoming domain inherits the older
                // domain's completed sky, so its transparent opening never exposes the real sky.
                if(exterior){
                    // Outside the smaller barrier, see Shrine's surrounding territory behind Void's shell.
                    if(!SHRINE.copyFrom(WORLD))return false;
                    SHRINE.bind();shrineDraw.run();
                    if(!VOID.copyFrom(SHRINE))return false;
                    VOID.bind();voidDraw.run();
                }else if(gojo.born<=shrine.born){
                    if(!VOID.copyFrom(WORLD))return false;
                    VOID.bind();voidDraw.run();
                    if(!SHRINE.copyFrom(VOID))return false;
                    SHRINE.bind();shrineDraw.run();
                }else{
                    if(!SHRINE.copyFrom(WORLD))return false;
                    SHRINE.bind();shrineDraw.run();
                    if(!VOID.copyFrom(SHRINE))return false;
                    VOID.bind();voidDraw.run();
                }
            }
            DomainClash.Contact contact=DomainClash.contact(shrine,gojo,now,partial);
            float visibility=Math.min(shrine.visibility(now,partial),gojo.visibility(now,partial));
            float age=clash==null?0:clash.age(now,partial);
            float strength=clash==null||contact==null?0:visibility*dev.jujutsuvfx.platform.Numbers.clamp(age/.6f,0,1);
            composite.setSampler("ShrineColor",SHRINE.color());composite.setSampler("ShrineDepth",SHRINE.depth());
            composite.setSampler("VoidColor",VOID.color());composite.setSampler("VoidDepth",VOID.depth());
            composite.safeGetUniform("InverseViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).invert());
            vector(composite,"ShrineCenter",shrine.center.subtract(camera));
            vector(composite,"VoidCenter",gojo.center.subtract(camera));
            composite.safeGetUniform("ShrineRadius").set(Math.max(.001f,shrine.radius*shrine.expansion(now,partial)));
            composite.safeGetUniform("VoidRadius").set(Math.max(.001f,gojo.radius*gojo.expansion(now,partial)));
            Vec3 between=gojo.center.subtract(shrine.center);
            vector(composite,"PlanePoint",(contact==null?shrine.center.add(gojo.center).scale(.5):contact.point()).subtract(camera));
            vector(composite,"PlaneNormal",contact==null?(between.lengthSqr()<.01?new Vec3(gojo.axis.x,0,gojo.axis.z).normalize():between.normalize()):contact.normal());
            composite.safeGetUniform("BlendWidth").set(contact==null?1f:contact.width());
            composite.safeGetUniform("Time").set(age);
            composite.safeGetUniform("NestedExterior").set(exterior?1f:0f);
            composite.safeGetUniform("ClashStrength").set(strength);
            float resolved=ClientDomainResolution.direction(clash);
            composite.safeGetUniform("Resolution").set(resolved,ClientDomainResolution.progress(partial));
            composite.safeGetUniform("ScreenEffects").set(mc.options.screenEffectScale().get().floatValue());
            RenderSystem.setShader(()->composite);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_ALWAYS);RenderSystem.depthMask(true);
            RenderSystem.disableCull();RenderSystem.disableBlend();
            BufferBuilder b=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            b.vertex(-1,-1,1).uv(0,0).color(-1).endVertex();b.vertex(1,-1,1).uv(1,0).color(-1).endVertex();
            b.vertex(1,1,1).uv(1,1).color(-1).endVertex();b.vertex(-1,1,1).uv(0,1).color(-1).endVertex();
            BufferUploader.drawWithShader(b.end());
            if(!exterior&&contact!=null&&strength>.001f&&resolved==0)drawParticles(event,shrine,gojo,contact,age,strength,now,partial);
        }
        return true;
    }
    private static void drawParticles(RenderLevelStageEvent event,MalevolentShrine shrine,UnlimitedVoid gojo,
                                      DomainClash.Contact contact,float age,float strength,long now,float partial){
        if(particles==null)return;
        float ra=shrine.radius*shrine.expansion(now,partial),rb=gojo.radius*gojo.expansion(now,partial);
        Vec3 camera=event.getCamera().getPosition();
        particles.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()));
        RenderSystem.setShader(()->particles);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        BufferBuilder buffer=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        int count=dev.jujutsuvfx.platform.Numbers.clamp((int)(contact.radius()*contact.radius()*1.7f),96,640);
        double ground=Math.min(shrine.center.y,gojo.center.y)+.08;
        for(int i=0;i<count;i++){
            double seed=hash(i*17.31+5),phase=age*(.75+seed*.8)+seed*13;
            double cycle=Math.floor(phase),t=phase-cycle;
            double angle=hash(i*3.19+cycle*9.7)*Math.PI*2;
            double reach=contact.radius()*Math.sqrt(hash(i*9.61+cycle*4.1))*.95;
            Vec3 anchor=contact.point().add(contact.right().scale(Math.cos(angle)*reach)).add(contact.up().scale(Math.sin(angle)*reach));
            double side=(i%2==0?-1:1),rush=side*Math.pow(1-t,2)*Math.min(contact.radius()*.24,4);
            Vec3 point=anchor.add(contact.normal().scale(rush)).add(contact.up().scale(t*t*.45));
            if(point.y<ground||point.distanceToSqr(shrine.center)>ra*ra||point.distanceToSqr(gojo.center)>rb*rb)continue;
            float life=(float)(Math.sin(Math.PI*t)*strength);
            if(life<.01f)continue;
            float width=.035f+(float)seed*.05f;
            Vec3 direction=contact.normal().scale(-side).add(contact.up().scale(.15)).normalize();
            float length=.18f+(float)(1-t)*.85f;
            int color=i%2==0?0xFF553D:0x55D8FF;
            if(t>.82){color=0xE6F3FF;length*=.45f;width*=1.6f;}
            streak(buffer,point,direction,camera,width,length,color,life*.86f);
        }
        // Long plasma filaments travel through the contact disk, with paired red/cyan accents.
        for(int arc=0;arc<14;arc++){
            float cycle=(float)Math.floor(age*5+arc*.31),life=(float)Math.sin(Math.PI*fract(age*5+arc*.31));
            if(life<.04f)continue;
            double x=(hash(arc*19.3+cycle*1.9)-.5)*contact.radius()*1.55;
            double reach=Math.sqrt(Math.max(0,contact.radius()*contact.radius()-x*x));
            Vec3 last=null;
            for(int segment=0;segment<=22;segment++){
                double y=-reach+2*reach*segment/22;
                double zig=(hash(arc*11.7+segment*7.3+cycle*13.4)-.5)*.55;
                Vec3 point=contact.point().add(contact.right().scale(x+zig)).add(contact.up().scale(y))
                        .add(contact.normal().scale(Math.sin(segment*1.8+cycle)*.11));
                boolean visible=point.y>=ground&&point.distanceToSqr(shrine.center)<=ra*ra&&point.distanceToSqr(gojo.center)<=rb*rb;
                if(last!=null&&visible){
                    Vec3 delta=point.subtract(last);
                    streak(buffer,last.add(point).scale(.5),delta.normalize(),camera,.035f,
                            (float)delta.length(),arc%2==0?0xFFAE9C:0xAEECFF,life*strength*.36f);
                }
                last=visible?point:null;
            }
        }
        BufferBuilder.RenderedBuffer mesh=buffer.endOrDiscardIfEmpty();
        if(mesh!=null)BufferUploader.drawWithShader(mesh);
    }
    private static int streak(BufferBuilder b,Vec3 center,Vec3 direction,Vec3 camera,float width,float length,int rgb,float opacity){
        Vec3 facing=camera.subtract(center).normalize();Vec3 across=direction.cross(facing);
        if(across.lengthSqr()<.0001)across=new Vec3(0,1,0).cross(facing);
        if(across.lengthSqr()<.0001)return 0;
        across=across.normalize().scale(width);Vec3 along=direction.scale(length*.5);
        int color=(dev.jujutsuvfx.platform.Numbers.clamp(Math.round(opacity*255),0,255)<<24)|rgb;
        vertex(b,center.subtract(across).subtract(along).subtract(camera),-1,-1,color);
        vertex(b,center.add(across).subtract(along).subtract(camera),1,-1,color);
        vertex(b,center.add(across).add(along).subtract(camera),1,1,color);
        vertex(b,center.subtract(across).add(along).subtract(camera),-1,1,color);return 1;
    }
    private static void vertex(BufferBuilder b,Vec3 p,float u,float v,int color){b.vertex((float)p.x,(float)p.y,(float)p.z).uv(u,v).color(color).endVertex();}
    private static void vector(ShaderInstance shader,String name,Vec3 p){shader.safeGetUniform(name).set((float)p.x,(float)p.y,(float)p.z);}
    private static double hash(double value){return fract(Math.sin(value*12.9898)*43758.5453);}
    private static double fract(double value){return value-Math.floor(value);}
}
