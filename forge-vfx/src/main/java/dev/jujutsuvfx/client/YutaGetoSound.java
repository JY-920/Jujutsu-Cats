package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.YutaFinaleAudio;
import dev.jujutsuvfx.entity.GetoCat;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Original positional layers for nearby world observers and the camera's distant boom microphone. */
final class YutaGetoSound {
    private record Pair(UUID yuta,UUID geto){}
    private static final Map<Pair,Session> SESSIONS=new HashMap<>();
    private static ClientLevel owner;
    private YutaGetoSound(){}

    /** Called by the existing global camera tick, including while no cinematic camera is active. */
    static void tick(YutaCat viewedYuta,GetoCat viewedGeto){
        Minecraft mc=Minecraft.getInstance();
        if(owner!=mc.level){stopAll();owner=mc.level;}
        if(owner==null||mc.player==null||!mc.player.isAlive()){stopAll();return;}
        if(mc.isPaused())return;
        Set<Pair> heard=new HashSet<>();
        // A pair may straddle the audible radius: test the nearer caster after the broad query.
        for(YutaCat yuta:owner.getEntitiesOfClass(YutaCat.class,mc.player.getBoundingBox().inflate(256),
                y->live(y)&&y.distanceToSqr(mc.player)<=256*256)){
            watch(yuta,viewedYuta,viewedGeto,heard);
        }
        if(live(viewedYuta))watch(viewedYuta,viewedYuta,viewedGeto,heard);
        SESSIONS.entrySet().removeIf(entry->{
            if(heard.contains(entry.getKey()))return false;
            entry.getValue().cancel();return true;
        });
    }

    private static void watch(YutaCat yuta,YutaCat viewedYuta,GetoCat viewedGeto,Set<Pair> heard){
        CompoundTag state=yuta.duelState();
        if(!(owner.getEntity(state.getInt("Peer")) instanceof GetoCat geto))return;
        Minecraft mc=Minecraft.getInstance();
        boolean viewed=yuta==viewedYuta&&geto==viewedGeto;
        double distance=Math.min(yuta.distanceToSqr(mc.player),geto.distanceToSqr(mc.player));
        if(!YutaFinaleAudio.observe(state.getInt("Phase"),live(yuta)&&live(geto),distance,viewed))return;
        Pair key=new Pair(yuta.getUUID(),geto.getUUID());
        if(!heard.add(key))return;
        Session session=SESSIONS.computeIfAbsent(key,unused->new Session(owner,yuta,geto));
        session.viewed=viewed;
        session.tick(state);
    }

    /** Stop camera-owned layers immediately. Nearby world audio may resume without replaying cues. */
    static void releaseView(YutaCat yuta,GetoCat geto){
        if(yuta==null||geto==null)return;
        Pair key=new Pair(yuta.getUUID(),geto.getUUID());
        Session session=SESSIONS.get(key);
        if(session==null)return;
        session.cancelLayers();
        Minecraft mc=Minecraft.getInstance();
        double distance=mc.player==null?Double.POSITIVE_INFINITY:
                Math.min(yuta.distanceToSqr(mc.player),geto.distanceToSqr(mc.player));
        if(distance>128*128){session.cancel();SESSIONS.remove(key);}
    }

    private static void stopAll(){
        for(Session session:SESSIONS.values())session.cancel();
        SESSIONS.clear();owner=null;
    }
    private static boolean live(Entity entity){
        return entity!=null&&entity.isAlive()&&!entity.isRemoved()&&entity.level()==owner;
    }
    private static Vec3 anchor(CompoundTag state,String key,Vec3 fallback){
        if(!state.contains(key+"X")||!state.contains(key+"Y")||!state.contains(key+"Z"))return fallback;
        Vec3 p=new Vec3(state.getDouble(key+"X"),state.getDouble(key+"Y"),state.getDouble(key+"Z"));
        return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)?p:fallback;
    }

    private static final class Session {
        final ClientLevel world;
        final YutaCat yuta;
        final GetoCat geto;
        final YutaFinaleAudio.Clock clock=new YutaFinaleAudio.Clock();
        final Map<String,Layer> loops=new HashMap<>();
        final List<Layer> transients=new ArrayList<>();
        boolean active=true,viewed;
        Session(ClientLevel world,YutaCat yuta,GetoCat geto){this.world=world;this.yuta=yuta;this.geto=geto;}
        void tick(CompoundTag state){
            int phase=state.getInt("Phase");
            double age=world.getGameTime()-state.getLong("Start");
            Vec3 yp=anchor(state,"Y",yuta.position());
            Vec3 gp=anchor(state,"E",anchor(state,"G",geto.position()).add(0,2.2,0));
            Vec3 rp=anchor(state,"L",anchor(state,"R",yp.add(0,0,-2.4)).add(0,2.25,0));
            Vec3 hit=anchor(state,"M",rp.lerp(gp,.5));
            var mix=YutaFinaleAudio.mix(phase,age);
            loop("duel_geto_charge",gp,mix.geto());
            loop("duel_rika_charge",rp,mix.rika());
            loop("duel_clash_pressure",hit,mix.pressure());
            switch(clock.cue(phase,state.getLong("Start"),age)){
                case YutaFinaleAudio.RING -> play("duel_ring",yp.add(0,1.3,0),.60f,48);
                case YutaFinaleAudio.CLASH -> play("duel_clash_hit",hit,1.0f,55);
                case YutaFinaleAudio.BREAKTHROUGH -> play("duel_breakthrough",gp,1.0f,75);
                case YutaFinaleAudio.AFTERMATH -> play("duel_aftermath",gp,.65f,72);
                default -> {}
            }
            transients.removeIf(Layer::isStopped);
        }
        void loop(String key,Vec3 position,double gain){
            Layer sound=loops.get(key);
            if(sound!=null&&sound.isStopped()){loops.remove(key);sound=null;}
            if(sound==null&&gain>.001){
                sound=new Layer(this,key,true,0,position,0);
                loops.put(key,sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
            if(sound!=null)sound.update(position,(float)gain);
        }
        void play(String key,Vec3 position,float gain,int duration){
            Layer sound=new Layer(this,key,false,duration,position,gain);
            transients.add(sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
        void cancelLayers(){
            for(Layer sound:loops.values())sound.cancel();
            for(Layer sound:transients)sound.cancel();
            loops.clear();transients.clear();
        }
        void cancel(){active=false;cancelLayers();}
    }

    private static final class Layer extends AbstractTickableSoundInstance {
        private final Session session;
        private final int duration;
        private float target;
        private float sourceGain;
        private Vec3 source;
        private int elapsed;
        Layer(Session session,String key,boolean loop,int duration,Vec3 position,float gain){
            super(SoundEvent.createVariableRangeEvent(new ResourceLocation(JujutsuVfx.ID,key)),
                    SoundSource.PLAYERS,SoundInstance.createUnseededRandom());
            this.session=session;this.duration=duration;
            looping=loop;delay=0;relative=false;attenuation=SoundInstance.Attenuation.LINEAR;
            pitch=1;update(position,gain);volume=target;
        }
        void update(Vec3 position,float gain){
            source=position;sourceGain=Math.max(0,Math.min(1,gain));relocate();
        }
        private void relocate(){
            Minecraft mc=Minecraft.getInstance();
            Vec3 listener=CatCinematicCamera.audioPosition(mc.gameRenderer.getMainCamera().getPosition());
            Vec3 delta=source.subtract(listener);double distance=delta.length();
            // Keep the original direction at the microphone, while world observers retain range falloff.
            Vec3 audible=listener.add(delta.scale(Math.min(1,10/Math.max(.001,distance))));
            x=audible.x;y=audible.y;z=audible.z;
            target=sourceGain*(float)YutaFinaleAudio.listenerGain(distance,session.viewed);
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public boolean canPlaySound(){return !isStopped()&&session.active;}
        @Override public void tick(){
            Minecraft mc=Minecraft.getInstance();
            if(mc.level!=session.world||mc.player==null||!mc.player.isAlive()
                    ||!session.active||!live(session.yuta)||!live(session.geto)){
                stop();return;
            }
            relocate();
            elapsed++;
            if(looping){
                volume+=(target-volume)*.20f;
                if(target<=.001f&&volume<.005f)stop();
            }else{volume=target;if(elapsed>=duration)stop();}
        }
        void cancel(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
    }
}
