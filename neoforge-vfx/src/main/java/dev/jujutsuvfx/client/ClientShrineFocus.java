package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.network.ShrineFocus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

/** One cheap composite and vertex tinting of the real victim, with no duplicate entity render. */
public final class ClientShrineFocus {
    private static final Map<Integer,Focus> FOCI=new HashMap<>();
    private static final Map<Integer,Set<Rift>> BLADES=new HashMap<>();
    private static final Map<Rift,Integer> OWNERS=new IdentityHashMap<>();
    private static final Map<Integer,Integer> FINISHED=new HashMap<>();
    private static ClientLevel level;
    private static ShaderInstance shader;
    private static final class Focus {
        ShrineFocus packet;long born,ends,received;boolean sounded;
        final CastAudio audio=new CastAudio();
        Focus(ShrineFocus p,long now){packet=p;born=now-p.age();ends=now+p.remaining();received=now;sounded=p.age()>8;}
        float age(float partial){return ClientEffectClock.ticks()+partial-born;}
    }
    private ClientShrineFocus(){}
    static void setShader(ShaderInstance value){shader=value;}
    static void clear(){FOCI.values().forEach(f->f.audio.stop());FOCI.clear();BLADES.clear();OWNERS.clear();FINISHED.clear();level=Minecraft.getInstance().level;}
    static boolean active(int session){Focus focus=FOCI.get(session);return focus!=null&&ClientEffectClock.ticks()>=focus.born;}
    static boolean quiet(int session){
        Focus focus=FOCI.get(session);if(focus==null||level==null)return false;
        Entity target=level.getEntity(focus.packet.target());
        return target instanceof dev.jujutsuvfx.entity.SorcererCat cat&&cat.isGojo()
                &&target.getUUID().equals(focus.packet.targetUuid());
    }
    private static void world(){if(level!=Minecraft.getInstance().level)clear();}
    public static void receive(ShrineFocus packet){
        world();if(level==null||packet.generation()<1||packet.age()<-64||packet.age()>24000
                ||packet.remaining()<0||packet.remaining()>2426||!Float.isFinite(packet.radius())
                ||packet.radius()<1||packet.radius()>dev.jujutsuvfx.core.DomainVolume.maxRadius(dev.jujutsuvfx.core.DomainVolume.SHRINE)||!finite(packet.center()))return;
        Focus old=FOCI.get(packet.session());long now=ClientEffectClock.ticks();
        if(old!=null&&(!old.packet.owner().equals(packet.owner())||packet.generation()<old.packet.generation()))return;
        if(!packet.active()){
            FINISHED.merge(packet.session(),packet.generation(),Math::max);
            Focus removed=FOCI.remove(packet.session());if(removed!=null)removed.audio.stop();clearBlades(packet.session());return;
        }
        if(packet.generation()<=FINISHED.getOrDefault(packet.session(),0))return;
        if(old==null||old.packet.generation()!=packet.generation()){
            if(old!=null)old.audio.stop();
            if(FOCI.size()>=20)FOCI.remove(FOCI.keySet().iterator().next()).audio.stop();
            clearBlades(packet.session());FOCI.put(packet.session(),new Focus(packet,now));
        }else{
            if(packet.age()<old.packet.age())return;
            old.packet=packet;old.born=Math.min(old.born,now-packet.age());old.ends=now+packet.remaining();old.received=now;
        }
    }
    static void track(int session,Rift blade){
        BLADES.computeIfAbsent(session,key->Collections.newSetFromMap(new IdentityHashMap<>())).add(blade);
        OWNERS.put(blade,session);
    }
    static void replace(Rift old,Rift next){
        Integer session=OWNERS.remove(old);if(session==null)return;
        Set<Rift> blades=BLADES.get(session);if(blades!=null)blades.remove(old);track(session,next);
    }
    static Map<Integer,List<Rift>> groups(List<Rift> blades){
        Map<Integer,List<Rift>> groups=new LinkedHashMap<>();
        for(Rift blade:blades)groups.computeIfAbsent(OWNERS.getOrDefault(blade,0),key->new ArrayList<>()).add(blade);
        return groups;
    }
    static void forget(int session){
        Focus focus=FOCI.remove(session);if(focus!=null)focus.audio.stop();
        FINISHED.remove(session);clearBlades(session);
    }
    static void end(int session){
        Focus focus=FOCI.remove(session);
        if(focus!=null){FINISHED.merge(session,focus.packet.generation(),Math::max);focus.audio.stop();}
        clearBlades(session);
    }
    private static void clearBlades(int session){
        Set<Rift> owned=BLADES.remove(session);if(owned!=null){ClientEvents.RIFTS.removeIf(owned::contains);owned.forEach(OWNERS::remove);}
    }
    static void tick(){
        world();long now=ClientEffectClock.ticks();
        for(var i=FOCI.entrySet().iterator();i.hasNext();){var entry=i.next();Focus f=entry.getValue();
            if(now>=f.ends||now-f.received>45){f.audio.stop();clearBlades(entry.getKey());i.remove();continue;}
            if(!f.sounded&&now>=f.born){
                f.sounded=true;
                if(now-f.born<=8){Entity target=level.getEntity(f.packet.target());
                    f.audio.play(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jujutsuvfx","shrine_focus_open")),
                            target==null?f.packet.center():target.getBoundingBox().getCenter(),.82f,1);}
            }
        }
        if(now%10==0){
            Set<Rift> live=Collections.newSetFromMap(new IdentityHashMap<>());live.addAll(ClientEvents.RIFTS);
            BLADES.values().removeIf(blades->{blades.retainAll(live);return blades.isEmpty();});
            OWNERS.keySet().retainAll(live);
            if(FINISHED.size()>128)FINISHED.clear();
        }
    }
    static boolean needsCapture(){return !FOCI.isEmpty();}
    public static boolean silhouette(Entity entity){
        if(level==null||entity.level()!=level)return false;
        long now=ClientEffectClock.ticks();
        for(Focus f:FOCI.values())if(now>=f.born&&now<f.ends&&now-f.received<=45
                &&entity.getId()==f.packet.target()&&entity.getUUID().equals(f.packet.targetUuid()))return true;
        return false;
    }
    public static MultiBufferSource tint(Entity entity,MultiBufferSource source){
        return silhouette(entity)?type->new InkVertex(source.getBuffer(type)):source;
    }
    private record InkVertex(VertexConsumer delegate) implements VertexConsumer {
        public VertexConsumer addVertex(float x,float y,float z){delegate.addVertex(x,y,z);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){delegate.setColor(0,0,0,a);return this;}
        public VertexConsumer setUv(float u,float v){delegate.setUv(u,v);return this;}
        public VertexConsumer setUv1(int u,int v){delegate.setOverlay(OverlayTexture.NO_OVERLAY);return this;}
        public VertexConsumer setUv2(int u,int v){delegate.setUv2(u,v);return this;}
        public VertexConsumer setNormal(float x,float y,float z){delegate.setNormal(x,y,z);return this;}
    }
    static void render(RenderLevelStageEvent event,MalevolentShrine shrine){
        if(shader==null||shrine==null)return;
        Focus focus=FOCI.get(shrine.session);if(focus==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false),age=focus.age(partial)*.05f;
        if(age<0)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||!mc.level.equals(level))return;
        Entity target=level.getEntity(focus.packet.target());
        if(target==null||!target.getUUID().equals(focus.packet.targetUuid()))return;
        Vec3 camera=event.getCamera().getPosition();
        Matrix4f matrix=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        var box=target.getBoundingBox().inflate(.12);
        float minX=1,maxX=0,minY=1,maxY=0;int visible=0;
        for(int i=0;i<8;i++){
            Vector4f p=matrix.transform(new Vector4f((float)(((i&1)==0?box.minX:box.maxX)-camera.x),
                    (float)(((i&2)==0?box.minY:box.maxY)-camera.y),(float)(((i&4)==0?box.minZ:box.maxZ)-camera.z),1));
            if(p.w<=.02f)continue;float x=p.x/p.w*.5f+.5f,y=p.y/p.w*.5f+.5f;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);visible++;
        }
        if(visible==0){minX=.35f;maxX=.65f;minY=.2f;maxY=.8f;}
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            if(!WaterSceneCapture.capture())return;
            DomainWorldCapture.bind(shader,false);
            shader.setSampler("SceneColor",WaterSceneCapture.color());shader.setSampler("SceneDepth",WaterSceneCapture.depth());
            shader.safeGetUniform("FocusBounds").set((minX+maxX)*.5f,(minY+maxY)*.5f,Math.max(.008f,(maxX-minX)*.5f),Math.max(.012f,(maxY-minY)*.5f));
            shader.safeGetUniform("Age").set(age);shader.safeGetUniform("Seed").set((float)Math.floorMod(target.getUUID().hashCode(),1024));
            shader.safeGetUniform("Effects").set(quiet(shrine.session)||!dev.jujutsuvfx.config.VfxConfig.flashes()?0f:mc.options.screenEffectScale().get().floatValue());
            shader.safeGetUniform("Quiet").set(quiet(shrine.session)?1f:0f);
            shader.safeGetUniform("Visibility").set((float)Math.max(0,Math.min(1,(focus.ends-ClientEffectClock.ticks()-partial)/10.0)));
            RenderSystem.setShader(()->shader);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            RenderSystem.disableCull();RenderSystem.disableBlend();
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            b.addVertex(-1,-1,1).setUv(0,0).setColor(-1);b.addVertex(1,-1,1).setUv(1,0).setColor(-1);
            b.addVertex(1,1,1).setUv(1,1).setColor(-1);b.addVertex(-1,1,1).setUv(0,1).setColor(-1);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
