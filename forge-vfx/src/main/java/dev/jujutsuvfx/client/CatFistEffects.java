package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.TechniqueFistImpact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Blue convergence and Red pressure fronts, independent of the ink-flame fist material. */
final class CatFistEffects {
    private static final float LIFETIME=.64f;
    private static final List<Impact> HITS=new ArrayList<>();
    private static final List<Hand> HANDS=new ArrayList<>();
    private static ShaderInstance shader;
    private static long nextBlueSound,nextRedSound;
    private record Hand(Vector3f center,Matrix4f projection,float radius,float time,float power,float seed,boolean red) {}
    private record Impact(Vec3 point,Vec3 axis,long born,float power,float seed,boolean red) {
        float age(float partial){return Math.max(0,(ClientEffectClock.ticks()-born+partial)*.05f);}
    }
    private CatFistEffects() {}
    static void setShader(ShaderInstance value){clear();shader=value;}
    static void clear(){HANDS.clear();HITS.clear();nextBlueSound=nextRedSound=0;}
    static void beginFrame(){HANDS.clear();}
    static void tick(){HITS.removeIf(hit->hit.age(0)>LIFETIME);}

    static void receive(TechniqueFistImpact packet){
        Minecraft mc=Minecraft.getInstance();ClientFistEffects.changeLevel(mc.level);
        var hit=packet.impact();
        if(mc.level==null||mc.player==null||!finite(hit.point())||!finite(hit.direction())
                ||!Float.isFinite(hit.strength())||EffectView.distanceToSqr(hit.point())>128*128)return;
        Vec3 axis=hit.direction().lengthSqr()>1e-8?hit.direction().normalize():new Vec3(0,0,1);
        long now=ClientEffectClock.ticks();
        float seed=Math.floorMod(hit.attackerId()*31+hit.targetId()*17+(int)now,1021);
        if(HITS.size()>=24)HITS.remove(0);
        HITS.add(new Impact(hit.point(),axis,now,dev.jujutsuvfx.platform.Numbers.clamp(hit.strength(),.2f,1),seed,packet.red()));
        if(now<(packet.red()?nextRedSound:nextBlueSound))return;
        if(packet.red())nextRedSound=now+5;else nextBlueSound=now+5;
        float near=(float)dev.jujutsuvfx.platform.Numbers.clamp(1-Math.sqrt(EffectView.distanceToSqr(hit.point()))/72,0,1);
        if(near>.01f)new CastAudio().play(SoundEvent.createVariableRangeEvent(new ResourceLocation(
                "jujutsuvfx",packet.red()?"red_impact":"blue_close")),hit.point(),near*(packet.red()?.38f:.28f),1.3f);
    }
    static void hand(PoseStack poses,float time,float power,SorcererCat cat,int hand,boolean red){
        if(shader==null||HANDS.size()>=64)return;
        Matrix4f transform=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        float determinant=transform.determinant();
        if(!Float.isFinite(determinant)||Math.abs(determinant)<1e-8f)return;
        boolean mixed=cat.hasEnergy(SorcererCat.BLUE,hand)&&cat.hasEnergy(SorcererCat.RED,hand);
        Vector3f center=transform.transformPosition(new Vector3f(mixed?(red?.055f:-.055f):0,-.025f,.025f));
        if(center.lengthSquared()>72*72)return;
        float radius=transform.transformDirection(new Vector3f(1,0,0)).length()*(mixed?.35f:.42f)*(.90f+.10f*power);
        center.z+=radius*.12f;
        HANDS.add(new Hand(center,new Matrix4f(RenderSystem.getProjectionMatrix()),radius,time,power,
                Math.floorMod(cat.getId()*19+hand*83,1021),red));
    }
    static void render(RenderLevelStageEvent event){
        if(shader==null||HANDS.isEmpty()&&HITS.isEmpty()){HANDS.clear();return;}
        float partial=event.getPartialTick();
        float time=(ClientEffectClock.ticks()%24000+partial)*.05f;
        Vec3 camera=event.getCamera().getPosition();
        var hits=new ArrayList<>(HITS);
        hits.removeIf(hit->hit.age(partial)>LIFETIME||hit.point.distanceToSqr(camera)>80*80);
        hits.sort(Comparator.comparingDouble(hit->hit.point.distanceToSqr(camera)));
        if(hits.size()>8)hits.subList(8,hits.size()).clear();
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            HANDS.sort(Comparator.comparingDouble(hand->hand.center.lengthSquared()));
            for(int i=Math.min(12,HANDS.size())-1;i>=0;i--){
                Hand hand=HANDS.get(i);
                billboard(hand.projection,hand.center,hand.radius,hand.time,0,hand.seed,hand.power,hand.red,0);
            }
            for(int i=hits.size()-1;i>=0;i--){
                Impact hit=hits.get(i);float age=hit.age(partial),size=.8f+.25f*hit.power;
                Vec3 point=hit.point.add(hit.axis.scale(hit.red?.12f:0));
                Vector3f center=event.getPoseStack().last().pose().transformPosition(relative(point,camera));
                billboard(event.getProjectionMatrix(),center,(hit.red?1.25f:1.48f)*size,
                        time,age,hit.seed,hit.power,hit.red,1);
                Vec3 right=right(hit.axis),up=right.cross(hit.axis).normalize();
                float progress=1-(float)Math.exp(-age*(hit.red?17:13));
                float radius=(hit.red?.26f+1.65f*progress:1.4f-1.1f*smooth(age/.26f))*size;
                Vec3 front=hit.point.add(hit.axis.scale(hit.red?.10+progress*1.15:-.10));
                plane(event,front,right,up,radius,time,age,hit.seed,hit.power,hit.red);
            }
            if(!hits.isEmpty()){
                // All curved streams share one draw. Their world-space paths carry
                // depth and perspective; no particle entities or scene copies are needed.
                BufferBuilder paths=begin(time,0,0,1,false,2);
                for(Impact hit:hits)streams(paths,event,hit,hit.age(partial));
                finish(paths);
            }
        }finally{HANDS.clear();}
    }
    private static void streams(BufferBuilder buffer,RenderLevelStageEvent event,Impact hit,float age){
        float fade=(1-smooth((age-.13f)/.43f))*smooth(age/.025f);
        if(fade<.005f)return;
        Vec3 right=right(hit.axis),up=right.cross(hit.axis).normalize();
        float expansion=1-(float)Math.exp(-age*19),contraction=1-smooth(age/.32f);
        for(int i=0;i<7;i++){
            float seed=hit.seed+i*27.31f,angle=Mth.TWO_PI*(i+random(seed)*.45f)/7;
            Vec3[] curve=new Vec3[7];
            for(int j=0;j<curve.length;j++){
                float t=j/(float)(curve.length-1),a,r,z;
                if(hit.red){
                    a=angle+Mth.sin(t*Mth.PI)*.18f;
                    r=(.10f+t*t*(1.05f+random(seed+1)*.6f))*expansion;
                    z=(.20f+t*(1.0f+random(seed+2)*1.1f))*expansion;
                }else{
                    a=angle+(1-t)*(1-t)*1.85f+age*5;
                    r=(.10f+(1-t)*1.55f)*(.18f+.82f*contraction);
                    z=Mth.sin(t*Mth.PI)*.25f-(1-t)*.38f;
                }
                curve[j]=hit.point.add(right.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r)).add(hit.axis.scale(z));
            }
            ribbon(buffer,event,curve,(hit.red?.045f:.030f)*(.7f+random(seed+3)*.6f),fade,hit.red);
        }
    }
    private static void ribbon(BufferBuilder b,RenderLevelStageEvent event,Vec3[] curve,float width,float alpha,boolean red){
        Matrix4f projection=event.getProjectionMatrix(),view=event.getPoseStack().last().pose();
        float aspect=aspect(projection);
        Vector3f[] points=new Vector3f[curve.length],offsets=new Vector3f[curve.length];
        for(int i=0;i<curve.length;i++){
            Vector3f local=view.transformPosition(relative(curve[i],event.getCamera().getPosition()));
            if(local.z>-.08f)return;
            points[i]=project(projection,local,aspect);
            if(points[i]==null||Math.abs(points[i].z)>1)return;
            float t=i/(float)(curve.length-1);
            float taper=.12f+.88f*Mth.sin(t*Mth.PI);
            offsets[i]=new Vector3f(Math.abs(projection.m11()/local.z)*width*taper,0,0);
        }
        for(int i=0;i<curve.length;i++){
            Vector3f start=points[Math.max(0,i-1)],end=points[Math.min(curve.length-1,i+1)];
            float dx=end.x-start.x,dy=end.y-start.y,length=Mth.sqrt(dx*dx+dy*dy);
            float w=offsets[i].x/Math.max(.00001f,length);
            offsets[i].set(-dy*w,dx*w,0);
        }
        for(int i=0;i<curve.length-1;i++){
            float a=i/(float)(curve.length-1),end=(i+1)/(float)(curve.length-1);
            ribbonVertex(b,points[i],offsets[i],aspect,-1,a,alpha,red);
            ribbonVertex(b,points[i+1],offsets[i+1],aspect,-1,end,alpha,red);
            ribbonVertex(b,points[i+1],offsets[i+1],aspect,1,end,alpha,red);
            ribbonVertex(b,points[i],offsets[i],aspect,1,a,alpha,red);
        }
    }
    private static void ribbonVertex(BufferBuilder b,Vector3f point,Vector3f offset,float aspect,float side,float t,float alpha,boolean red){
        b.vertex((point.x+offset.x*side)/aspect,point.y+offset.y*side,point.z).uv(side,t)
                .color(red?1f:.04f,red?.045f:.60f,red?.16f:1f,alpha).endVertex();
    }
    private static void billboard(Matrix4f projection,Vector3f center,float radius,float time,float age,float seed,float power,boolean red,int pass){
        float aspect=aspect(projection);Vector3f point=project(projection,center,aspect);
        if(point==null||Math.abs(point.z)>1)return;
        float extent=Math.abs(projection.m11()/center.z)*radius;
        if(extent<.0002f||Math.abs(point.x)>aspect+extent||Math.abs(point.y)>1+extent)return;
        BufferBuilder b=begin(time,age,seed,power,red,pass);
        vertex(b,(point.x-extent)/aspect,point.y-extent,point.z,-1,-1);
        vertex(b,(point.x+extent)/aspect,point.y-extent,point.z,1,-1);
        vertex(b,(point.x+extent)/aspect,point.y+extent,point.z,1,1);
        vertex(b,(point.x-extent)/aspect,point.y+extent,point.z,-1,1);finish(b);
    }
    private static void plane(RenderLevelStageEvent event,Vec3 center,Vec3 right,Vec3 up,float radius,
                              float time,float age,float seed,float power,boolean red){
        float aspect=aspect(event.getProjectionMatrix());
        int[] x={-1,1,1,-1},y={-1,-1,1,1};Vector3f[] corners=new Vector3f[4];
        for(int i=0;i<4;i++){
            Vec3 p=center.add(right.scale(x[i]*radius)).add(up.scale(y[i]*radius));
            corners[i]=project(event.getProjectionMatrix(),event.getPoseStack().last().pose().transformPosition(relative(p,event.getCamera().getPosition())),aspect);
            if(corners[i]==null||Math.abs(corners[i].z)>1)return;
        }
        BufferBuilder b=begin(time,age,seed,power,red,3);
        for(int i=0;i<4;i++)vertex(b,corners[i].x/aspect,corners[i].y,corners[i].z,x[i],y[i]);finish(b);
    }
    private static BufferBuilder begin(float time,float age,float seed,float power,boolean red,int pass){
        shader.safeGetUniform("Time").set(time);shader.safeGetUniform("Age").set(age);
        shader.safeGetUniform("Seed").set(seed);shader.safeGetUniform("Power").set(power);
        shader.safeGetUniform("Kind").set(red?1f:0f);shader.safeGetUniform("Pass").set((float)pass);
        shader.safeGetUniform("FlashEnabled").set(VfxConfig.flashScale());
        RenderSystem.setShader(()->shader);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        return dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
    }
    private static void vertex(BufferBuilder b,float x,float y,float z,float u,float v){b.vertex(x,y,z).uv(u,v).color(-1).endVertex();}
    private static void finish(BufferBuilder b){BufferBuilder.RenderedBuffer mesh=b.endOrDiscardIfEmpty();if(mesh!=null)BufferUploader.drawWithShader(mesh);}
    private static Vector3f project(Matrix4f projection,Vector3f p,float aspect){
        org.joml.Vector4f clip=projection.transform(new org.joml.Vector4f(p,1));
        return clip.w<.035f?null:new Vector3f(clip.x/clip.w*aspect,clip.y/clip.w,clip.z/clip.w);
    }
    private static float aspect(Matrix4f projection){return Math.abs(projection.m11()/projection.m00());}
    private static Vec3 right(Vec3 axis){return axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();}
    private static Vector3f relative(Vec3 point,Vec3 camera){return new Vector3f((float)(point.x-camera.x),(float)(point.y-camera.y),(float)(point.z-camera.z));}
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static float smooth(float t){t=dev.jujutsuvfx.platform.Numbers.clamp(t,0,1);return t*t*(3-2*t);}
    private static float random(float s){double n=Math.sin(s*127.1)*43758.5453;return(float)(n-Math.floor(n));}
}
