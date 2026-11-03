package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.core.CinematicFraming;
import dev.jujutsuvfx.core.CinematicFraming.*;
import dev.jujutsuvfx.core.CursedSpeechShape;
import dev.jujutsuvfx.core.GetoVortexMotion;
import dev.jujutsuvfx.core.RikaResidentClock;
import dev.jujutsuvfx.core.YutaBladeMotion;
import dev.jujutsuvfx.core.YutaCinematicFraming;
import dev.jujutsuvfx.core.YutaGetoBeat;
import dev.jujutsuvfx.entity.GetoCat;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Client-only observation, with authored inserts driven by the actors' synchronized duel state. */
final class YutaGetoCamera {
    private record Pair(UUID yuta,UUID geto) {}
    private static final LinkedHashSet<Pair> SEEN=new LinkedHashSet<>();
    private static ClientLevel world,memoryWorld;
    private static YutaCat yuta;
    private static GetoCat geto;
    private static LivingEntity subject,microphone,compositionActor;
    private static Entity previousCamera;
    private static CameraType previousType;
    private static CatCinematicCamera.Frame frame;
    private static V eye,aim,forward;
    private static double lastSample=Double.NaN,seconds,fov=65;
    private static long quietSince=Long.MIN_VALUE;
    private static boolean automatic,scripted;
    private static List<YutaCat> shakeActors=List.of();
    private static final ShotClock SHOTS=new ShotClock();

    private YutaGetoCamera() {}
    static boolean active(){return yuta!=null;}
    static LivingEntity subject(){return subject;}
    static Vec3 viewpoint(){return frame!=null?frame.eye():subject.getEyePosition();}
    private static boolean live(Entity e){
        return e!=null&&e.isAlive()&&!e.isRemoved()&&e.level()==Minecraft.getInstance().level;
    }
    private static boolean engaged(YutaCat y,GetoCat g){
        return live(y)&&live(g)&&!y.isNoAi()&&!g.isNoAi()&&y.distanceToSqr(g)<=128*128
                &&(managed(y,g)||y.cameraTarget()==g||g.cameraTarget()==y);
    }
    private static boolean managed(YutaCat y,GetoCat g){
        CompoundTag state=y.duelState();
        int phase=state.getInt("Phase");
        return state.getInt("Peer")==g.getId()&&phase>YutaGetoBeat.NONE&&phase<YutaGetoBeat.DONE;
    }
    private static CompoundTag scene(){
        CompoundTag state=yuta.duelState();
        return state.getInt("Peer")==geto.getId()?state:new CompoundTag();
    }
    private static List<YutaCat> nearby(){
        Minecraft mc=Minecraft.getInstance();
        return mc.level==null||!live(mc.player)?List.of():mc.level.getEntitiesOfClass(YutaCat.class,
                mc.player.getBoundingBox().inflate(128),y->live(y)&&y.distanceToSqr(mc.player)<=128*128);
    }
    private static GetoCat partner(YutaCat y){
        if(y.level().getEntity(y.duelState().getInt("Peer")) instanceof GetoCat g&&engaged(y,g))return g;
        if(y.cameraTarget() instanceof GetoCat g&&engaged(y,g))return g;
        return y.level().getEntitiesOfClass(GetoCat.class,y.getBoundingBox().inflate(128),
                g->engaged(y,g)).stream().min(Comparator.comparingDouble(y::distanceToSqr)).orElse(null);
    }
    private static void remember(){
        if(yuta==null||geto==null)return;
        remember(new Pair(yuta.getUUID(),geto.getUUID()));
    }
    private static void remember(Pair pair){
        SEEN.add(pair);
        while(SEEN.size()>64)SEEN.remove(SEEN.iterator().next());
    }
    static void suppressNearby(){
        ensureWorld();
        for(YutaCat y:nearby()){GetoCat g=partner(y);if(g!=null)remember(new Pair(y.getUUID(),g.getUUID()));}
    }
    private static void ensureWorld(){
        ClientLevel current=Minecraft.getInstance().level;
        if(current!=memoryWorld){SEEN.clear();shakeActors=List.of();memoryWorld=current;}
    }
    static int automatic(){
        ensureWorld();
        Minecraft mc=Minecraft.getInstance();
        if(!VfxConfigScreen.cinematic()||mc.screen!=null||!live(mc.player)||mc.getCameraEntity()!=mc.player)return 0;
        return nearby().stream().filter(y->{
            GetoCat g=partner(y);return g!=null&&!SEEN.contains(new Pair(y.getUUID(),g.getUUID()));
        }).min(Comparator.comparingDouble(y->y.distanceToSqr(mc.player)))
                .map(y->start(y,true)).orElse(0);
    }
    static int nearest(){
        Minecraft mc=Minecraft.getInstance();
        return nearby().stream().filter(y->partner(y)!=null)
                .min(Comparator.comparingDouble(y->y.distanceToSqr(mc.player))).map(y->start(y,false)).orElse(0);
    }
    static int start(YutaCat next,boolean auto){
        Minecraft mc=Minecraft.getInstance();ensureWorld();
        GetoCat rival=next==null?null:partner(next);
        if(!live(next)||!live(mc.player)||rival==null)return 0;
        if(!active()){
            previousCamera=mc.getCameraEntity();previousType=mc.options.getCameraType();world=mc.level;
        }
        YutaGetoSound.releaseView(yuta,geto);
        yuta=next;geto=rival;subject=microphone=next;automatic=auto;scripted=managed(next,rival);
        quietSince=Long.MIN_VALUE;remember();resetFrame();
        mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(next);
        return 1;
    }
    static void resetFrame(){frame=null;eye=aim=forward=null;compositionActor=null;lastSample=Double.NaN;seconds=0;fov=65;SHOTS.reset();}
    static int stop(){
        if(!active())return 0;
        YutaGetoSound.releaseView(yuta,geto);
        Minecraft mc=Minecraft.getInstance();if(world==mc.level)remember();
        if(mc.getCameraEntity()==subject)mc.setCameraEntity(live(previousCamera)?previousCamera:mc.player);
        if(mc.options.getCameraType()==CameraType.FIRST_PERSON&&previousType!=null)mc.options.setCameraType(previousType);
        yuta=null;geto=null;subject=microphone=null;previousCamera=null;previousType=null;world=null;automatic=scripted=false;
        quietSince=Long.MIN_VALUE;resetFrame();return 1;
    }
    static void tick(){
        ensureWorld();
        YutaGetoSound.tick(yuta,geto);
        if(!Minecraft.getInstance().isPaused())shakeActors=nearby();
        if(!active())return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=world||!live(mc.player)||!live(yuta)||!live(geto)||!live(subject)
                ||mc.getCameraEntity()!=subject||mc.options.getCameraType()!=CameraType.FIRST_PERSON
                ||automatic&&!VfxConfigScreen.cinematic()){
            CatCinematicCamera.stop(false);return;
        }
        CompoundTag state=scene();
        int phase=state.getInt("Phase");
        if(phase==YutaGetoBeat.DONE||scripted&&phase==YutaGetoBeat.NONE){
            CatCinematicCamera.stop(false);return;
        }
        if(phase>YutaGetoBeat.NONE)scripted=true;
        // Leave a brief recovery beat, but do not observe an abandoned fight forever.
        if(engaged(yuta,geto))quietSince=Long.MIN_VALUE;
        else if(quietSince==Long.MIN_VALUE)quietSince=ClientEffectClock.ticks();
        else if(ClientEffectClock.ticks()-quietSince>60)CatCinematicCamera.stop(false);
    }
    static CatCinematicCamera.Frame sample(Entity entity,float partial,Mode mode,double side){
        Minecraft mc=Minecraft.getInstance();
        if(!active()||entity!=subject||mc.level!=world||!live(yuta)||!live(geto))return null;
        if(mc.isPaused()&&frame!=null)return frame;
        double now=ClientEffectClock.ticks()+Mth.clamp(partial,0,1);
        if(frame!=null&&now==lastSample)return frame;
        double dt=Double.isNaN(lastSample)?1.0/60:CinematicFraming.clamp((now-lastSample)*.05,0,.10);
        lastSample=now;seconds+=dt;
        CompoundTag screenplay=scene();
        int phase=screenplay.getInt("Phase");
        if(mode==Mode.AUTO&&(phase==YutaGetoBeat.RING||YutaGetoBeat.finale(phase)))
            return finale(screenplay,phase,partial,dt,side);
        int action=yuta.bladeAction();float age=yuta.bladeAge(partial),getoAge=geto.actionAge(partial),vortexAge=geto.vortexAge(partial);
        var domain=YutaDomainRenderer.cameraDomain(yuta.getId(),partial);
        var cast=CatSpells.cameraCast(yuta.getUUID());
        var rika=yuta.rikaState();
        boolean spirit=!rika.isEmpty()&&rika.getInt("Phase")!=RikaResidentClock.PREPARING
                &&rika.getInt("Remaining")>0;
        float rikaAge=mc.level.getGameTime()-rika.getLong("Start")+partial;
        boolean rikaWave=spirit&&rika.getInt("Action")==dev.jujutsuvfx.core.RikaSupportClock.WAVE&&rikaAge<36;
        boolean summonReveal=spirit&&rika.getBoolean("Resident")&&mc.level.getGameTime()-rika.getLong("ResidentStart")<24;
        boolean copied=yuta.copiedSpellActive()||cast!=null&&cast.phase()<CatSpell.FLIGHT+1;
        boolean domainReveal=domain!=null&&domain.age()<dev.jujutsuvfx.core.YutaDomainRules.REVEAL_TICKS;
        boolean ring=action==YutaBladeMotion.RIKA_SUMMON;
        boolean gesture=action==YutaBladeMotion.DOMAIN;
        boolean speech=action==YutaBladeMotion.SPEECH;
        boolean vortex=(geto.action()==GetoCat.UZUMAKI||geto.action()==GetoCat.BLAST)&&vortexAge<GetoVortexMotion.END;
        boolean getoInsert=mode==Mode.AUTO&&!ring&&!gesture&&!speech&&!domainReveal
                &&(vortex||!copied&&!domainReveal&&!rikaWave&&geto.action()==GetoCat.SUMMON&&getoAge<40);
        boolean getoShot=getoInsert&&vortex;
        // Changing camera entity here would invalidate the Camera.setup mixin's current frame.
        // Keep the camera attached to Yuta; Geto is the composition/audio subject during his insert.
        LivingEntity actor=getoInsert?geto:yuta;microphone=actor;
        LivingEntity target=live(actor==yuta?yuta.cameraTarget():geto.cameraTarget())
                ?(actor==yuta?yuta.cameraTarget():geto.cameraTarget()):(actor==yuta?geto:yuta);
        V hero=v(actor.getEyePosition(partial)),other=v(target.getEyePosition(partial));
        V desired=other.sub(hero).horizontal();
        if(forward==null||compositionActor!=actor)forward=desired;
        else forward=CinematicFraming.turnToward(forward,desired,CinematicFraming.blend(6,dt));
        compositionActor=actor;
        V effect=cast==null?hero.add(forward.scale(2)):v(cast.point());
        double radius=cast==null?1:Math.min(8,cast.radius());
        Shot shot=Shot.DUEL;boolean insert=true;
        if(ring)shot=Shot.GESTURE;
        else if(gesture)shot=Shot.GESTURE;
        else if(speech)shot=age<CursedSpeechShape.RELEASE?Shot.REVEAL:Shot.RELEASE;
        else if(getoShot)shot=vortexAge<GetoVortexMotion.FIRE?Shot.CHARGE:Shot.RELEASE;
        else if(copied){
            boolean released=cast!=null?cast.phase()>=CatSpell.FLIGHT:
                    yuta.copiedSpellAge()>=CatSpell.launchTick(yuta.copiedSpellKind(),yuta.copiedSpellLarge());
            shot=released?Shot.RELEASE:Shot.DETAIL;
        }else if(domainReveal){
            shot=Shot.DOMAIN;effect=v(domain.center().add(0,3,0));radius=domain.radius();
        }else if(rikaWave||summonReveal){
            shot=Shot.SUMMON;effect=new V(rika.getDouble("X"),rika.getDouble("Y")+2.3,rika.getDouble("Z"));radius=3.2;
        }else if(geto.action()==GetoCat.SUMMON&&getoAge<40){
            shot=Shot.SUMMON;effect=v(geto.getEyePosition(partial));radius=2;
        }else{
            insert=false;
            if(action==YutaBladeMotion.LEAP||!yuta.onGround())shot=Shot.AIR;
            else if(YutaBladeMotion.isTravel(action))shot=Shot.CHASE;
            else if(action==YutaBladeMotion.GUARD)shot=Shot.DEFENSE;
            else if(action==YutaBladeMotion.DRAW||action==YutaBladeMotion.PICK_SWORD)shot=Shot.DETAIL;
            else if(hero.sub(other).length()<7)shot=Shot.MELEE;
        }
        if(!insert)shot=SHOTS.choose(shot,seconds);else SHOTS.reset();
        double aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        Scene scene=new Scene(hero,other,forward,v(actor.getDeltaMovement()).scale(20).limited(40),
                effect,radius,Math.max(actor.getBbWidth(),actor.getBbHeight())*.6,aspect,true,seconds,side);
        Plan plan=CinematicFraming.plan(scene,shot,mode);
        V origin=null,tip=null;
        if(mode==Mode.AUTO){
            if(ring||gesture)plan=YutaCinematicFraming.ring(scene);
            else if(speech||copied&&!getoInsert&&shot==Shot.DETAIL)
                plan=YutaCinematicFraming.casting(scene,speech?hero.add(forward.scale(.4)):effect,Math.min(2.2,radius));
            if(getoShot){
                origin=v(geto.spellOrigin());
                Vec3 axis=geto.beamDirection();
                if(axis.lengthSqr()<.5)axis=geto.getLookAngle();
                tip=origin.add(v(axis.normalize()).scale(GetoVortexMotion.reach(vortexAge,geto.beamLength()/geto.vortexScale())*geto.vortexScale()));
                double sourceRadius=(GetoVortexMotion.radius(vortexAge)+.4)*geto.vortexScale();
                plan=vortexAge<GetoVortexMotion.FIRE?YutaCinematicFraming.casting(scene,origin,sourceRadius)
                        :YutaCinematicFraming.vortex(scene,origin,tip,sourceRadius,true);
            }else if((rikaWave||summonReveal)&&shot==Shot.SUMMON&&!getoInsert){
                plan=YutaCinematicFraming.casting(scene,effect,2.4);
                if(rikaWave&&rika.contains("WaveX")){
                    V wave=new V(rika.getDouble("WaveX"),rika.getDouble("WaveY"),rika.getDouble("WaveZ"));
                    V axis=new V(rika.getDouble("WaveDX"),rika.getDouble("WaveDY"),rika.getDouble("WaveDZ"));
                    plan=YutaCinematicFraming.vortex(scene,wave,wave.add(axis.scale(rika.getDouble("WaveDistance"))),1.4,true);
                }
            }
        }
        if(domain!=null)plan=CinematicFraming.insideDomain(plan,v(domain.center()),domain.radius());
        V wanted=clearEye(hero,plan.eye());
        boolean snap=eye==null||wanted.sub(eye).length()>48||mode==Mode.POV;
        double follow=CinematicFraming.blend(insert?11:7,dt);
        eye=snap?wanted:eye.lerp(wanted,follow);
        aim=snap||aim==null?plan.aim():aim.lerp(plan.aim(),CinematicFraming.blend(insert?14:9,dt));
        fov+=(plan.fov()-fov)*CinematicFraming.blend(7,dt);
        // Fit the fast projectile after damping so its head cannot outrun the lens.
        if(getoShot&&mode==Mode.AUTO){
            Plan fit=new Plan(eye,aim,fov,0,true);
            fit=CinematicFraming.includeSphere(fit,hero,1.3,aspect);
            fit=CinematicFraming.includeSphere(fit,origin,(GetoVortexMotion.radius(vortexAge)+.4)*geto.vortexScale(),aspect);
            if(vortexAge>=GetoVortexMotion.FIRE){
                fit=CinematicFraming.includeSphere(fit,tip,2.2,aspect);
                fit=CinematicFraming.includeSphere(fit,other,1.3,aspect);
            }
            eye=fit.eye();
        }
        if(mode!=Mode.POV)eye=clearEye(hero,eye);
        if(domain!=null){
            Plan inside=CinematicFraming.insideDomain(new Plan(eye,aim,fov,0,true),v(domain.center()),domain.radius());
            eye=inside.eye();aim=inside.aim();
        }
        if(!eye.finite()||!aim.finite()){eye=hero;aim=hero.add(forward);}
        frame=new CatCinematicCamera.Frame(vec(eye),(float)CinematicFraming.yaw(eye,aim),
                (float)CinematicFraming.pitch(eye,aim),0,fov,mode!=Mode.POV&&eye.sub(v(yuta.getEyePosition(partial))).length()>.8);
        return frame;
    }
    /** During Geto's charge the microphone follows him without changing the rendered view entity. */
    static LivingEntity audioSubject(){
        return live(microphone)?microphone:yuta;
    }

    /** Existing global camera-event hook also reaches players who declined automatic framing. */
    static void camera(ViewportEvent.ComputeCameraAngles event,float partial,float scale){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=memoryWorld||scale<=0)return;
        YutaCinematicFraming.Shake strongest=null;double power=0;
        for(YutaCat actor:active()?List.of(yuta):shakeActors){
            if(!live(actor))continue;
            CompoundTag state=actor.duelState();
            if(!(mc.level.getEntity(state.getInt("Peer")) instanceof GetoCat rival)||!live(rival))continue;
            int phase=state.getInt("Phase");
            V center=anchor(state,"M",v(actor.position()).lerp(v(rival.position()),.5));
            double distance=active()?0:center.sub(v(event.getCamera().getPosition())).length();
            double age=mc.level.getGameTime()-state.getLong("Start")+(mc.isPaused()?0:partial);
            var shake=YutaCinematicFraming.shake(phase,age,distance,scale);
            double energy=shake.yaw()*shake.yaw()+shake.pitch()*shake.pitch()+shake.roll()*shake.roll();
            if(energy>power){strongest=shake;power=energy;}
        }
        if(strongest==null)return;
        event.setYaw(event.getYaw()+(float)strongest.yaw());
        event.setPitch(event.getPitch()+(float)strongest.pitch());
        event.setRoll(event.getRoll()+(float)strongest.roll());
    }

    private static CatCinematicCamera.Frame finale(CompoundTag state,int phase,float partial,double dt,double side){
        Minecraft mc=Minecraft.getInstance();
        double aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        double age=Math.max(0,world.getGameTime()-state.getLong("Start")+partial);
        V yp=v(yuta.getPosition(partial)),gp=v(geto.getPosition(partial));
        if(phase>=YutaGetoBeat.SEPARATE&&phase<YutaGetoBeat.AFTERMATH){
            yp=anchor(state,"Y",yp);gp=anchor(state,"G",gp);
        }
        V axis=gp.sub(yp).horizontal();
        V rp=anchor(state,"R",yp.sub(axis.scale(2.4)));
        V getoSource=anchor(state,"E",gp.add(V.UP.scale(2.2)));
        V rikaSource=anchor(state,"L",rp.add(V.UP.scale(2.25)));
        V midpoint=anchor(state,"M",rikaSource.lerp(getoSource,.5));
        var story=new YutaCinematicFraming.Finale(yp,gp,rp,midpoint,getoSource,rikaSource,aspect,side);
        Plan plan=YutaCinematicFraming.finale(story,phase,age);
        microphone=phase==YutaGetoBeat.CHARGE&&age<46||phase==YutaGetoBeat.BREAKTHROUGH
                ||phase==YutaGetoBeat.AFTERMATH||phase==YutaGetoBeat.RETREAT&&age<42?geto:yuta;
        forward=axis;compositionActor=microphone;SHOTS.reset();
        boolean snap=eye==null||plan.eye().sub(eye).length()>96;
        eye=snap?plan.eye():eye.lerp(plan.eye(),CinematicFraming.blend(8,dt));
        aim=snap||aim==null?plan.aim():aim.lerp(plan.aim(),CinematicFraming.blend(10,dt));
        fov=snap?plan.fov():fov+(plan.fov()-fov)*CinematicFraming.blend(8,dt);
        Plan fitted=YutaCinematicFraming.fitFinale(new Plan(eye,aim,fov,0,true),story,phase,age);
        eye=clearFinaleEye(story,fitted,phase);
        if(eye==null){CatCinematicCamera.stop(false);return null;}
        if(!eye.finite()||!aim.finite()){eye=plan.eye();aim=plan.aim();}
        var shake=YutaCinematicFraming.shake(phase,age,0,mc.options.screenEffectScale().get());
        V shaken=clearFinaleEye(story,new Plan(eye.add(shake.offset()),aim,fov,0,true),phase);
        if(shaken==null)shaken=eye;
        // The damped composition stays unshaken; the offset cannot accumulate or drag into a beam.
        frame=new CatCinematicCamera.Frame(vec(shaken),(float)CinematicFraming.yaw(eye,aim),
                (float)CinematicFraming.pitch(eye,aim),0,fov,true);
        return frame;
    }

    private static V anchor(CompoundTag state,String key,V fallback){
        if(!state.contains(key+"X")||!state.contains(key+"Y")||!state.contains(key+"Z"))return fallback;
        V p=new V(state.getDouble(key+"X"),state.getDouble(key+"Y"),state.getDouble(key+"Z"));
        return p.finite()?p:fallback;
    }

    private static V clearFinaleEye(YutaCinematicFraming.Finale story,Plan plan,int phase){
        boolean beams=phase==YutaGetoBeat.CLASH||phase==YutaGetoBeat.BREAKTHROUGH;
        V wanted=beams?YutaCinematicFraming.clearBeamEye(story,plan.eye()):plan.eye();
        V best=clearEye(plan.aim(),wanted);
        if(best.sub(wanted).length()<.05&&(!beams||YutaCinematicFraming.beamClearance(story,best)>2.3))return best;
        // A blocked boom can otherwise retract into the opaque beam. Try clear, same-side booms.
        double score=Double.POSITIVE_INFINITY;
        for(V from:new V[]{plan.aim(),story.yutaBody(),story.getoBody()}){
            for(double rise:new double[]{0,4,8}){
                V candidate=clearEye(from,wanted.add(V.UP.scale(rise)));
                double penalty=candidate.sub(wanted).length();
                if(beams&&YutaCinematicFraming.beamClearance(story,candidate)<2.3)penalty+=1000;
                if(penalty<score){best=candidate;score=penalty;}
            }
        }
        // If terrain leaves no legal boom outside the beam, return control instead of filming opacity.
        return beams&&YutaCinematicFraming.beamClearance(story,best)<2.3?null:best;
    }
    private static V clearEye(V hero,V wanted){
        if(!world.hasChunkAt(BlockPos.containing(vec(wanted))))wanted=hero.add(wanted.sub(hero).limited(4));
        V delta=wanted.sub(hero);double length=delta.length();if(length<.001)return hero;
        double distance=length;
        for(V offset:new V[]{new V(0,0,0),new V(.18,.16,0),new V(-.18,.16,0),new V(0,-.16,.18),new V(0,.16,-.18)}){
            Vec3 from=vec(hero.add(offset)),to=vec(wanted.add(offset));
            HitResult hit=world.clip(new ClipContext(from,to,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,subject));
            if(hit.getType()!=HitResult.Type.MISS)distance=Math.min(distance,Math.max(0,hit.getLocation().distanceTo(from)-.3));
        }
        return hero.add(delta.scale(distance/length));
    }
    private static V v(Vec3 p){return new V(p.x,p.y,p.z);}
    private static Vec3 vec(V p){return new Vec3(p.x(),p.y(),p.z());}
}
