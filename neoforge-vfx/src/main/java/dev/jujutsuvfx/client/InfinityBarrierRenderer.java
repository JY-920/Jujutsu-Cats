package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Infinity and recovery share a depth-tested posed-model mask; recovery adds green crosses. */
final class InfinityBarrierRenderer {
    private static final int LIMIT=12;
    private static final Map<UUID,Long> CONTACTS=new HashMap<>();
    private static final LinkedHashMap<UUID,Capture> CAPTURES=new LinkedHashMap<>();
    private static final ByteBufferBuilder[] STORAGE=new ByteBufferBuilder[LIMIT*2];
    private static final SceneSnapshot SCENE=new SceneSnapshot();
    private static ShaderInstance shader,maskShader,crossShader;
    private static TextureTarget mask;
    private static boolean accepting;
    private static long lastDraw;
    private record Part(MeshData mesh,ResourceLocation texture) {}
    private record Capture(List<Part> parts,Matrix4f view,Matrix4f projection,float strength,float width,
                           float seed,boolean healing,Vec3 center,float bodyWidth,float bodyHeight) {}
    static void setShader(ShaderInstance value){clear();shader=value;}
    static void setMask(ShaderInstance value){clear();maskShader=value;}
    static void setCross(ShaderInstance value){crossShader=value;}
    static void clear(){
        accepting=false;clearMeshes();CONTACTS.clear();releaseTargets();
        for(int i=0;i<STORAGE.length;i++)if(STORAGE[i]!=null){STORAGE[i].close();STORAGE[i]=null;}
    }
    private static void clearMeshes(){
        for(Capture capture:CAPTURES.values())for(Part part:capture.parts)part.mesh.close();
        CAPTURES.clear();
    }
    private static void releaseTargets(){SCENE.release();if(mask!=null){mask.destroyBuffers();mask=null;}}
    static void beginFrame(){
        clearMeshes();accepting=true;
        long now=ClientEffectClock.ticks();CONTACTS.values().removeIf(t->now-t>14);
    }
    static void endCapture(){accepting=false;}
    static void hit(int id){
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.getEntity(id) instanceof SorcererCat cat&&cat.isGojo())
            CONTACTS.put(cat.getUUID(),ClientEffectClock.ticks());
    }
    private static float strength(SorcererCat cat,float partial){
        if(cat.finaleWard())return .95f;
        float stance=cat.combatMove()==CatCombatMoves.INFINITY?smooth(cat.punchAge(partial)/3)
                :CatCombatMoves.move(cat.previousCombatAction())==CatCombatMoves.INFINITY?1-cat.combatBlend(partial):0;
        float age=(ClientEffectClock.ticks()+partial-CONTACTS.getOrDefault(cat.getUUID(),-1000L))*.05f;
        float impact=age>=0&&age<.7f?(float)Math.exp(-age*3)*(1-smooth((age-.35f)/.35f)):0;
        return Math.clamp(stance*.72f+impact*.65f,0,1);
    }
    private static float recovery(SorcererCat cat,float partial){
        // Stop immediately on interruption; a domain can freeze the pose blend itself.
        return cat.combatMove()==CatCombatMoves.REVERSE_CURSED?smooth(cat.punchAge(partial)/5):0;
    }
    static void capture(SorcererCat cat,SorcererCatModel<?> model,PoseStack poses,float partial,
                        ResourceLocation body,ResourceLocation details){
        Minecraft mc=Minecraft.getInstance();
        if(!accepting||shader==null||maskShader==null||mc.level!=cat.level()
                ||cat.isMahoraga()||!cat.isAlive()||cat.isInvisible()||cat.isTransforming())return;
        float recovery=recovery(cat,partial);boolean healing=recovery>.001f;
        float strength=cat.finaleWard()?.95f:healing?recovery*.92f:cat.isGojo()?strength(cat,partial):0;
        double distance=cat.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition());
        if(strength<.01f||distance>96*96)return;
        int slot=0;
        for(UUID id:CAPTURES.keySet()){if(id.equals(cat.getUUID()))break;slot++;}
        if(slot>=LIMIT)return;
        Capture previous=CAPTURES.get(cat.getUUID());
        if(previous!=null){for(Part part:previous.parts)part.mesh.close();previous.parts.clear();}
        List<Part> parts=new ArrayList<>(2);
        for(int material=0;material<2;material++){
            int index=slot*2+material;
            if(STORAGE[index]==null)STORAGE[index]=new ByteBufferBuilder(32768);
            BufferBuilder vertices=new BufferBuilder(STORAGE[index],VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            model.renderMaterial(poses,vertices,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,-1,material);
            MeshData data=vertices.build();
            if(data!=null)parts.add(new Part(data,material==0?body:details));
        }
        if(parts.isEmpty()){CAPTURES.remove(cat.getUUID());return;}
        CAPTURES.put(cat.getUUID(),new Capture(parts,new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()),strength,
                (float)Math.clamp(12/Math.sqrt(Math.max(1,distance)),.5,1.5),Math.floorMod(cat.getId(),251)/251f,
                healing,new Vec3(Mth.lerp(partial,cat.xo,cat.getX()),Mth.lerp(partial,cat.yo,cat.getY())+cat.getBbHeight()*.5,
                Mth.lerp(partial,cat.zo,cat.getZ())),cat.getBbWidth(),cat.getBbHeight()));
    }
    static void render(RenderLevelStageEvent event){
        accepting=false;
        Minecraft mc=Minecraft.getInstance();long now=ClientEffectClock.ticks();
        if(shader==null||maskShader==null||mc.level==null||CAPTURES.isEmpty()){
            clearMeshes();if(now-lastDraw>40)releaseTargets();return;
        }
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        float[] clear=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        try(DomainRenderer.State ignored=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
            if(!SCENE.capture())return;
            lastDraw=now;
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            float downscale=Math.min(.5f,960f/viewport[2]);
            int width=Math.max(1,Math.round(viewport[2]*downscale)),height=Math.max(1,Math.round(viewport[3]*downscale));
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            try(SceneSnapshot.Target original=new SceneSnapshot.Target()){
                if(mask==null||mask.width!=width||mask.height!=height){
                    if(mask!=null)mask.destroyBuffers();
                    mask=new TextureTarget(width,height,true,Minecraft.ON_OSX);mask.setFilterMode(GL11.GL_LINEAR);
                }
                mask.bindWrite(true);RenderSystem.clearColor(0,0,0,0);RenderSystem.clearDepth(1);
                RenderSystem.depthMask(true);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
                // Rasterize a solid silhouette against its own depth. Comparing half-resolution
                // face fragments to full-resolution world depth punched moire holes into the mask.
                RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.setShader(()->maskShader);
                for(Capture capture:CAPTURES.values()){
                    maskShader.safeGetUniform("CaptureView").set(capture.view);
                    maskShader.safeGetUniform("CaptureProjection").set(capture.projection);
                    // Pack the green/white palette with its seed in the existing mask channel.
                    maskShader.safeGetUniform("OutlineData").set(capture.strength,capture.width/2,
                            (capture.healing?.55f:0)+capture.seed*.4f);
                    for(var iterator=capture.parts.iterator();iterator.hasNext();){
                        Part part=iterator.next();iterator.remove();
                        maskShader.setSampler("SkinTexture",mc.getTextureManager().getTexture(part.texture).getId());
                        // drawWithShader consumes and closes the mesh. Remove it from cleanup first.
                        MeshData mesh=part.mesh;
                        BufferUploader.drawWithShader(mesh);
                    }
                }
            }
            shader.setSampler("Silhouette",mask.getColorTextureId());shader.setSampler("SilhouetteDepth",mask.getDepthTextureId());
            shader.setSampler("SceneDepth",SCENE.depth());
            shader.safeGetUniform("MaskSize").set((float)width,(float)height);
            shader.safeGetUniform("Time").set((now%24000+event.getPartialTick().getGameTimeDeltaPartialTick(false))*.05f);
            RenderSystem.setShader(()->shader);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            RenderSystem.disableCull();RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            b.addVertex(-1,-1,0).setUv(0,0).setColor(-1);b.addVertex(1,-1,0).setUv(1,0).setColor(-1);
            b.addVertex(1,1,0).setUv(1,1).setColor(-1);b.addVertex(-1,1,0).setUv(0,1).setColor(-1);
            BufferUploader.drawWithShader(b.buildOrThrow());
            crosses(event);
        }finally{
            clearMeshes();RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);RenderSystem.clearDepth(clearDepth);
            if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
    private static void crosses(RenderLevelStageEvent event){
        if(crossShader==null||CAPTURES.values().stream().noneMatch(Capture::healing))return;
        float time=(ClientEffectClock.ticks()%24000+event.getPartialTick().getGameTimeDeltaPartialTick(false))*.05f;
        Vec3 camera=event.getCamera().getPosition();Matrix4f view=event.getModelViewMatrix(),projection=event.getProjectionMatrix();
        RenderSystem.setShader(()->crossShader);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        // Five small camera-facing glyphs per cat, all in one batch and occluded by terrain.
        for(Capture c:CAPTURES.values())if(c.healing){
            Vec3 relative=c.center.subtract(camera);
            Vector3f center=view.transformPosition(new Vector3f((float)relative.x,(float)relative.y,(float)relative.z));
            for(int i=0;i<5;i++){
                float clock=time*(.43f+i*.027f)+i*.2f+c.seed;
                float phase=clock-(float)Math.floor(clock);
                float fade=smooth(phase/.15f)*(1-smooth((phase-.78f)/.22f));
                float seed=c.seed*57+i*4.7f+(float)Math.floor(clock)*1.37f;
                float lateral=(i%2==0?-1:1)*c.bodyWidth*(.54f+.19f*random(seed));
                Vector3f point=new Vector3f(center).add(lateral,c.bodyHeight*(phase*1.25f-.65f),c.bodyWidth*.25f);
                float size=(.13f+.065f*random(seed+9))*Math.clamp(c.bodyWidth, .8f,1.5f);
                cross(b,projection,point,size,c.strength*fade);
            }
        }
        MeshData mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
    }
    private static void cross(BufferBuilder b,Matrix4f projection,Vector3f center,float size,float alpha){
        if(alpha<.01f)return;
        Vector4f clip=projection.transform(new Vector4f(center,1));
        if(clip.w<.06f)return;
        float x=clip.x/clip.w,y=clip.y/clip.w,z=clip.z/clip.w;
        float rx=Math.abs(projection.m00()*size/clip.w),ry=Math.abs(projection.m11()*size/clip.w);
        if(Math.abs(z)>1||Math.abs(x)>1+rx||Math.abs(y)>1+ry)return;
        b.addVertex(x-rx,y-ry,z).setUv(-1,-1).setColor(1f,1f,1f,alpha);
        b.addVertex(x+rx,y-ry,z).setUv(1,-1).setColor(1f,1f,1f,alpha);
        b.addVertex(x+rx,y+ry,z).setUv(1,1).setColor(1f,1f,1f,alpha);
        b.addVertex(x-rx,y+ry,z).setUv(-1,1).setColor(1f,1f,1f,alpha);
    }
    private static float random(float seed){double n=Math.sin(seed*127.1)*43758.5453;return (float)(n-Math.floor(n));}
    private static float smooth(float value){float t=Math.clamp(value,0,1);return t*t*(3-2*t);}
}
