package dev.jujutsuvfx.client;

import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.core.CameraAutoStartPolicy;
import dev.jujutsuvfx.network.DuelScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Scene snapshots belong to one loaded world. Repeated sync packets do not replay a shot or overlay. */
public final class DuelScenes {
    private static final Map<Long,Scene> SCENES=new LinkedHashMap<>();
    private static ClientLevel level;
    public static final class Scene {
        private DuelScene packet;
        private long received;
        private double born;
        private Scene(DuelScene packet,long now){this.packet=packet;received=now;born=now-packet.age();}
        public DuelScene packet(){return packet;}
        public float age(float partial){return (float)Math.max(0,Math.min(packet.duration(),ClientEffectClock.ticks()+partial-born));}
        public float progress(float partial){return Mth.clamp(age(partial)/Math.max(1,packet.duration()),0,1);}
        public SorcererCat actor(int id){
            Entity e=level==null?null:level.getEntity(id);
            return e instanceof SorcererCat cat&&cat.isAlive()&&!cat.isRemoved()?cat:null;
        }
        public SorcererCat gojo(){return actor(packet.gojo());}
        public SorcererCat sukuna(){return actor(packet.sukuna());}
        public SorcererCat mahoraga(){return actor(packet.mahoraga());}
        public SorcererCat cameraActor(){
            SorcererCat selected=switch(packet.phase()){
                case DuelScene.OPENING_GUARD,DuelScene.DOMAIN_SUKUNA,DuelScene.WHEEL_SUMMON,DuelScene.DOMAIN_GOJO_WIN->sukuna();
                case DuelScene.MAHORAGA_DESCENT,DuelScene.RESCUE_EMERGE,DuelScene.RESCUE_CUT,DuelScene.RESCUE_EXIT->mahoraga();
                case DuelScene.RESCUE_WHEEL,DuelScene.SOLO_HOLD->sukuna();
                default->gojo();
            };
            return selected!=null?selected:gojo();
        }
    }
    private DuelScenes(){}
    private static void ensureWorld(){
        ClientLevel current=Minecraft.getInstance().level;
        if(level!=current){SCENES.clear();DomainBarrierEffects.clear();level=current;CatCinematicCamera.resetAutomaticMemory();}
        long now=ClientEffectClock.ticks();
        SCENES.values().removeIf(s->now-s.received>100||now-s.born>s.packet.duration()+30);
    }
    public static void clear(){SCENES.clear();DomainBarrierEffects.clear();level=null;CatCinematicCamera.resetAutomaticMemory();}
    public static void receive(DuelScene packet){
        ensureWorld();if(level==null||!valid(packet))return;
        if(packet.phase()==DuelScene.NONE){CatCinematicCamera.authoritativeEnd(packet.duel());SCENES.remove(packet.duel());return;}
        if(!VfxConfigScreen.cinematic())return;
        long now=ClientEffectClock.ticks();Scene previous=SCENES.get(packet.duel());
        // The lead-in movement and the actual cast may intentionally share one shot family.
        // A fresh authoritative start with a different duration is a new segment, not a late refresh.
        boolean segment=previous!=null&&packet.age()<=1&&previous.packet.age()>packet.age()
                &&previous.packet.duration()!=packet.duration();
        boolean transition=previous==null||previous.packet.phase()!=packet.phase()||segment;
        if(transition){
            if(SCENES.size()>=16)SCENES.remove(SCENES.keySet().iterator().next());
            SCENES.put(packet.duel(),new Scene(packet,now));DomainBarrierEffects.begin(packet);
            if(packet.phase()==DuelScene.DOMAIN_GOJO_WIN||packet.phase()==DuelScene.DOMAIN_SUKUNA_WIN)
                ClientDomainResolution.receive(packet.gojo(),packet.sukuna(),packet.phase()==DuelScene.DOMAIN_GOJO_WIN);
        }else{
            if(packet.age()<previous.packet.age())return;
            previous.packet=packet;previous.received=now;
            previous.born=Math.min(previous.born,now-packet.age());
        }
    }
    private static boolean valid(DuelScene p){
        return p.phase()>=DuelScene.NONE&&p.phase()<=DuelScene.RESCUE_EXIT&&p.age()>=0&&p.duration()>=0
                &&p.duration()<=12000&&p.age()<=p.duration()+20&&p.focus()!=null
                &&Double.isFinite(p.focus().x)&&Double.isFinite(p.focus().y)&&Double.isFinite(p.focus().z)
                &&Double.isFinite(p.radius())&&p.radius()>=0&&p.radius()<=160;
    }
    public static Scene forActor(SorcererCat actor){
        ensureWorld();if(actor==null)return null;
        Scene found=null;
        for(Scene scene:SCENES.values()){
            DuelScene p=scene.packet;int id=actor.getId();
            if((p.gojo()==id||p.sukuna()==id||p.mahoraga()==id)&&(found==null||scene.received>found.received))found=scene;
        }
        return found;
    }
    public static Scene filming(){return forActor(CatCinematicCamera.protagonist());}
    static Scene automaticCandidate(CameraAutoStartPolicy policy){
        ensureWorld();var player=Minecraft.getInstance().player;if(player==null||!VfxConfigScreen.cinematic())return null;
        Scene closest=null;double distance=128*128;
        for(Scene scene:SCENES.values()){
            SorcererCat gojo=scene.gojo(),sukuna=scene.sukuna();
            if(gojo==null||sukuna==null||!gojo.isGojo()||!sukuna.isSukuna()||policy.seen(scene.packet.duel()))continue;
            double near=Math.min(player.distanceToSqr(gojo),player.distanceToSqr(sukuna));
            if(near<distance){distance=near;closest=scene;}
        }
        return closest;
    }
    static void suppressKnown(CameraAutoStartPolicy policy){
        ensureWorld();for(Scene scene:SCENES.values())policy.suppress(scene.packet.duel());
    }
    /** Matches only this duel's finishing blast; unrelated players and effects keep their normal clocks. */
    public static double blastRate(UUID owner,Vec3 center){
        ensureWorld();
        for(Scene scene:SCENES.values()){
            DuelScene p=scene.packet;SorcererCat gojo=scene.gojo();
            if(p.phase()==DuelScene.FINALE_FUSION&&(p.flags()&DuelScene.SLOW_MOTION)!=0
                    &&gojo!=null&&gojo.getUUID().equals(owner)&&p.focus().distanceToSqr(center)<256)return .25;
        }
        return 1;
    }
}
