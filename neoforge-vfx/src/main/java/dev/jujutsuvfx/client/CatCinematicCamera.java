package dev.jujutsuvfx.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.core.CinematicFraming;
import dev.jujutsuvfx.core.CameraAutoStartPolicy;
import dev.jujutsuvfx.core.CinematicFraming.*;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatSpellState;
import dev.jujutsuvfx.network.DuelScene;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import java.util.Comparator;
import java.util.List;

/** A local spectator camera. It never changes actor AI, position, motion, or damage. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class CatCinematicCamera {
    public record Frame(Vec3 eye,float yaw,float pitch,float roll,double fov,boolean detached) {}
    private static KeyMapping toggleKey,nextKey,modeKey;
    private static SorcererCat subject,protagonist;
    private static long sceneDuel=Long.MIN_VALUE;
    private static int scenePhase=-1;
    private static LivingEntity opponent;
    private static Entity previousCamera;
    private static ClientLevel world;
    private static CameraType previousType;
    private static Mode mode=Mode.AUTO;
    private static final ShotClock SHOTS=new ShotClock();
    private static Frame frame;
    private static V eye,aim,forward;
    private static double lastSample=Double.NaN,seconds,fov=65,roll,side=1;
    private static double collisionAngle,collisionLift;
    private static long contextTick=Long.MIN_VALUE,collisionTick=Long.MIN_VALUE;
    private static boolean structureClearance;
    private static final StructureTrack STRUCTURE_TRACK=new StructureTrack();
    private static long structureDuel=Long.MIN_VALUE;
    private static CatSpellState ownCast,foeCast;
    private static long audioTick=Long.MIN_VALUE;
    private static final CameraAutoStartPolicy AUTO_START=new CameraAutoStartPolicy();
    private static Long automaticDuel;
    private static double softMoveUntil;
    private static long shrineQuietUntil=Long.MIN_VALUE;
    private CatCinematicCamera() {}

    static void registerKeys(RegisterKeyMappingsEvent event){
        toggleKey=key(event,"toggle",GLFW.GLFW_KEY_F6);
        nextKey=key(event,"next",GLFW.GLFW_KEY_F7);
        modeKey=key(event,"mode",GLFW.GLFW_KEY_F8);
    }
    private static KeyMapping key(RegisterKeyMappingsEvent event,String name,int code){
        KeyMapping key=new KeyMapping("key.jujutsuvfx.camera_"+name,KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,code,"key.categories.jujutsuvfx");
        event.register(key);return key;
    }
    public static boolean active(){return subject!=null;}
    public static SorcererCat protagonist(){return active()?protagonist:null;}
    static void resetAutomaticMemory(){AUTO_START.clear();automaticDuel=null;shrineQuietUntil=Long.MIN_VALUE;}
    /** Damage tilt includes burning ticks; it should never shake a camera attached to Gojo. */
    public static boolean steadyGojoView(){
        Minecraft mc=Minecraft.getInstance();
        return active()&&live(subject)&&subject.isGojo()&&mc.level==world&&mc.getCameraEntity()==subject;
    }
    /** Protect only the filmed Gojo receiving Shrine's concentrated/domain settlement cuts. */
    public static boolean steadyShrineShot(){
        Minecraft mc=Minecraft.getInstance();
        if(!active()||!live(subject)||!subject.isGojo()||mc.level!=world||mc.getCameraEntity()!=subject){
            shrineQuietUntil=Long.MIN_VALUE;return false;
        }
        long now=ClientEffectClock.ticks();
        DuelScenes.Scene scene=DuelScenes.forActor(protagonist);
        if(ClientShrineFocus.silhouette(subject)||scene!=null&&scene.packet().phase()==DuelScene.DOMAIN_SUKUNA_WIN)
            shrineQuietUntil=now+12; // Include the last cut's ten-tick vanilla hurt tail.
        return now<=shrineQuietUntil;
    }
    static void disableAutomatic(){if(automaticDuel!=null)stop(false);DuelScenes.clear();}
    static void authoritativeEnd(long duel){if(active()&&automaticDuel!=null&&automaticDuel==duel)stop(false);}
    /** A boom microphone near the actor keeps dialogue and punches audible during wide shots. */
    public static Vec3 audioPosition(Vec3 camera){
        if(!active()||!live(subject))return camera;
        Vec3 head=subject.getEyePosition(),offset=camera.subtract(head);
        return head.add(offset.scale(Math.min(1,6/Math.max(.001,offset.length()))));
    }
    private static void audio(boolean stopping){
        Minecraft mc=Minecraft.getInstance();
        if(mc.getConnection()==null||!mc.getConnection().getConnection().isConnected()||!mc.getConnection().hasChannel(dev.jujutsuvfx.network.CameraAudio.TYPE))return;
        dev.jujutsuvfx.network.CameraAudio packet=new dev.jujutsuvfx.network.CameraAudio(stopping?-1:subject.getId(),
                stopping?Vec3.ZERO:audioPosition(viewpoint()));
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);audioTick=ClientEffectClock.ticks();
    }
    static Vec3 viewpoint(){
        Minecraft mc=Minecraft.getInstance();
        return active()?(frame!=null?frame.eye():subject.getEyePosition()):mc.player==null?Vec3.ZERO:mc.player.position();
    }
    private static void message(String key,Object... args){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("camera.jujutsuvfx."+key,args),true);
    }
    private static boolean live(Entity e){return e!=null&&e.isAlive()&&!e.isRemoved()&&e.level()==Minecraft.getInstance().level;}
    private static List<SorcererCat> nearby(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return List.of();
        return mc.level.getEntitiesOfClass(SorcererCat.class,mc.player.getBoundingBox().inflate(128),
                cat->live(cat)&&cat.distanceToSqr(mc.player)<=128*128);
    }
    private static int kind(SorcererCat cat){return cat.isGojo()?0:cat.isSukuna()?1:2;}
    private static SorcererCat nearest(int kind){
        Minecraft mc=Minecraft.getInstance();
        return nearby().stream().filter(c->kind<0||kind(c)==kind)
                .min(Comparator.comparingDouble(c->mc.player.distanceToSqr(c)+(c.cameraTarget()==null?256:0))).orElse(null);
    }
    private static int start(SorcererCat next){
        Minecraft mc=Minecraft.getInstance();
        if(next==null||!live(next)||mc.player==null||!mc.player.isAlive()){message("missing");return 0;}
        if(!active()){
            previousCamera=mc.getCameraEntity();previousType=mc.options.getCameraType();world=mc.level;
            var camera=mc.gameRenderer.getMainCamera();eye=v(camera.getPosition());
            aim=eye.add(v(new Vec3(camera.getLookVector())).scale(8));
            fov=mc.options.fov().get();roll=0;seconds=0;
        }
        protagonist=next;subject=next;opponent=null;forward=null;frame=null;ownCast=foeCast=null;
        automaticDuel=null;
        sceneDuel=Long.MIN_VALUE;scenePhase=-1;
        contextTick=collisionTick=Long.MIN_VALUE;collisionAngle=collisionLift=0;
        lastSample=Double.NaN;SHOTS.reset();
        mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(next);
        audio(false);announce();return 1;
    }
    private static void announce(){message("selected",subject.getDisplayName(),Component.translatable("camera.jujutsuvfx.mode."+mode.name().toLowerCase(java.util.Locale.ROOT)));}
    public static int stop(boolean notify){
        Minecraft mc=Minecraft.getInstance();
        if(!active())return 0;
        DuelScenes.Scene story=DuelScenes.forActor(protagonist);
        if(story!=null)AUTO_START.suppress(story.packet().duel());
        if(notify)DuelScenes.suppressKnown(AUTO_START);
        automaticDuel=null;
        audio(true);audioTick=Long.MIN_VALUE;
        if(mc.getCameraEntity()==subject)mc.setCameraEntity(live(previousCamera)?previousCamera:mc.player);
        if(mc.options.getCameraType()==CameraType.FIRST_PERSON&&previousType!=null)mc.options.setCameraType(previousType);
        structureClearance=false;STRUCTURE_TRACK.reset();structureDuel=Long.MIN_VALUE;subject=null;protagonist=null;opponent=null;previousCamera=null;world=null;previousType=null;shrineQuietUntil=Long.MIN_VALUE;
        sceneDuel=Long.MIN_VALUE;scenePhase=-1;
        frame=null;eye=aim=forward=null;ownCast=foeCast=null;lastSample=Double.NaN;SHOTS.reset();
        if(notify)message("off");return 1;
    }
    private static int toggle(){
        if(active())return stop(true);
        Minecraft mc=Minecraft.getInstance();
        SorcererCat picked=mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof SorcererCat cat&&cat.isGojo()?cat:null;
        return start(picked!=null?picked:nearest(0));
    }
    private static int next(){
        if(!active())return toggle();
        // F7 returns to the leading Gojo; authored reaction shots handle the other actors.
        mode=Mode.AUTO;return start(live(protagonist)?protagonist:nearest(0));
    }
    private static int mode(Mode next){
        mode=next;frame=null;lastSample=Double.NaN;collisionTick=Long.MIN_VALUE;
        if(!active())return toggle();announce();return 1;
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("rift").then(Commands.literal("camera").executes(c->toggle())
                .then(Commands.literal("off").executes(c->stop(true)))
                .then(Commands.literal("next").executes(c->next()))
                .then(Commands.literal("gojo").executes(c->start(nearest(0))))
                .then(Commands.literal("auto").executes(c->mode(Mode.AUTO)))
                .then(Commands.literal("shoulder").executes(c->mode(Mode.SHOULDER)))
                .then(Commands.literal("pov").executes(c->mode(Mode.POV)))
                .then(Commands.literal("side").executes(c->{side=-side;collisionTick=Long.MIN_VALUE;return 1;}))));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(active()&&mc.level==world&&mc.getCameraEntity()==subject&&!live(subject)&&live(protagonist))retarget(protagonist,true);
        if(active()&&(mc.level!=world||!live(mc.player)||!live(subject)||mc.getCameraEntity()!=subject
                ||mc.options.getCameraType()!=CameraType.FIRST_PERSON))stop(true);
        if(active())direct();
        if(active()&&(audioTick==Long.MIN_VALUE||ClientEffectClock.ticks()-audioTick>=4))audio(false);
        while(toggleKey!=null&&toggleKey.consumeClick())if(mc.screen==null)toggle();
        while(nextKey!=null&&nextKey.consumeClick())if(mc.screen==null)next();
        while(modeKey!=null&&modeKey.consumeClick())if(mc.screen==null)mode(Mode.values()[(mode.ordinal()+1)%Mode.values().length]);
        automatic();
    }
    private static void automatic(){
        Minecraft mc=Minecraft.getInstance();
        if(active()||mc.screen!=null||mc.level==null||!live(mc.player)||mc.getCameraEntity()!=mc.player)return;
        DuelScenes.Scene scene=DuelScenes.automaticCandidate(AUTO_START);
        if(scene==null)return;
        long duel=scene.packet().duel();
        if(!AUTO_START.claim(duel,true))return;
        mode=Mode.AUTO;
        if(start(scene.gojo())>0){automaticDuel=duel;direct();}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){stop(false);DuelScenes.clear();}
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event){
        if(!active()||event.getEntity()!=Minecraft.getInstance().player)return;
        var input=event.getInput();input.forwardImpulse=input.leftImpulse=0;
        input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event){
        if(active()){event.setCanceled(true);event.setSwingHand(false);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event){
        Frame view=sample(Minecraft.getInstance().getCameraEntity(),(float)event.getPartialTick());
        if(view!=null){event.setYaw(view.yaw());event.setPitch(view.pitch());event.setRoll(view.roll());}
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event){
        if(!event.usedConfiguredFov())return;
        Frame view=sample(Minecraft.getInstance().getCameraEntity(),(float)event.getPartialTick());
        if(view!=null)event.setFOV(view.fov());
    }

    // Keep impact recoil out of this insert, then add only the domain atmosphere.
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void steadyAngles(ViewportEvent.ComputeCameraAngles event){
        if(!steadyShrineShot())return;
        Frame view=sample(Minecraft.getInstance().getCameraEntity(),(float)event.getPartialTick());
        if(view!=null){
            Minecraft mc=Minecraft.getInstance();float scale=mc.options.screenEffectScale().get().floatValue();
            DuelScenes.Scene scene=DuelScenes.forActor(protagonist);
            boolean settling=ClientShrineFocus.silhouette(subject)||scene!=null&&scene.packet().phase()==DuelScene.DOMAIN_SUKUNA_WIN;
            double time=(ClientEffectClock.ticks()+event.getPartialTick())/20.0;
            float strength=settling?.20f*scale:0;
            event.setYaw(view.yaw()+(float)Math.sin(time*17.3)*strength*.45f);
            event.setPitch(view.pitch()+(float)(Math.sin(time*23.1)+.3*Math.sin(time*39.7))*strength);
            event.setRoll((float)Math.sin(time*19.7)*strength*.50f);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void steadyFov(ViewportEvent.ComputeFov event){
        if(!event.usedConfiguredFov()||!steadyShrineShot())return;
        Frame view=sample(Minecraft.getInstance().getCameraEntity(),(float)event.getPartialTick());
        if(view!=null)event.setFOV(view.fov());
    }

    private static void retarget(SorcererCat actor,boolean cut){
        if(actor==null||actor==subject)return;
        subject=actor;opponent=null;forward=null;frame=null;ownCast=foeCast=null;shrineQuietUntil=Long.MIN_VALUE;
        contextTick=collisionTick=Long.MIN_VALUE;collisionAngle=collisionLift=0;
        lastSample=Double.NaN;SHOTS.reset();if(cut)eye=aim=null;
        Minecraft.getInstance().setCameraEntity(actor);audio(false);
    }
    private static void direct(){
        DuelScenes.Scene story=DuelScenes.forActor(protagonist);
        if(mode!=Mode.AUTO){if(live(protagonist))retarget(protagonist,true);return;}
        if(story==null){
            if(live(protagonist))retarget(protagonist,false);
            sceneDuel=Long.MIN_VALUE;scenePhase=-1;return;
        }
        DuelScene p=story.packet();
        boolean changed=sceneDuel!=p.duel()||scenePhase!=p.phase();
        SorcererCat actor=story.cameraActor();
        boolean gentle=p.phase()==DuelScene.WHEEL_SUMMON||p.phase()==DuelScene.RESCUE_WHEEL||p.phase()==DuelScene.RESCUE_EXIT||p.phase()==DuelScene.OPENING_RETURN
                ||p.phase()==DuelScene.ARM_RECOVER||p.phase()==DuelScene.BATTLE||p.phase()==DuelScene.APPROACH||p.phase()==DuelScene.STRUCTURE_CLASH;
        if(changed&&gentle)softMoveUntil=seconds+1.0;
        if(live(actor))retarget(actor,changed&&!gentle);
        if(changed){collisionTick=contextTick=Long.MIN_VALUE;SHOTS.reset();sceneDuel=p.duel();scenePhase=p.phase();}
    }
    private static Shot narrative(DuelScenes.Scene story,Shot fallback){
        if(story==null)return fallback;
        return switch(story.packet().phase()){
            case DuelScene.OPENING_CHARGE->Shot.CHARGE;
            case DuelScene.OPENING_GUARD->Shot.OPENING_DEFENSE;
            case DuelScene.OPENING_RETURN,DuelScene.APPROACH->Shot.DUEL;
            case DuelScene.DOMAIN_GOJO,DuelScene.DOMAIN_SUKUNA,DuelScene.SOLO_GOJO->
                    subject.combatMove()==CatCombatMoves.DOMAIN_REVEAL?Shot.REVEAL:Shot.GESTURE;
            case DuelScene.DOMAIN_CLASH->Shot.DOMAIN;
            case DuelScene.DOMAIN_BREAK->Shot.BARRIER;
            case DuelScene.SOLO_HOLD->Shot.VOID_VICTORY;
            case DuelScene.DOMAIN_GOJO_WIN->Shot.VOID_VICTORY;
            case DuelScene.DOMAIN_SUKUNA_WIN->Shot.SHRINE_VICTORY;
            case DuelScene.WHEEL_SUMMON,DuelScene.RESCUE_WHEEL->Shot.SUMMON;
            case DuelScene.MAHORAGA_DESCENT,DuelScene.RESCUE_EMERGE,DuelScene.RESCUE_EXIT->Shot.DESCENT;
            case DuelScene.MAHORAGA_STRIKE,DuelScene.RESCUE_CUT->Shot.BLADE_CONTACT;
            case DuelScene.ARM_CUT->Shot.DUEL;
            case DuelScene.ARM_RECOVER->subject.combatMove()==CatCombatMoves.REVERSE_CURSED?Shot.RECOVER:Shot.CHASE;
            case DuelScene.FINALE_BLUE->Shot.CHARGE;
            case DuelScene.FINALE_RED->Shot.FUSION;
            case DuelScene.FINALE_FUSION->Shot.FUSION;
            case DuelScene.FINALE_BLAST->Shot.FINALE;
            case DuelScene.STRUCTURE_CLASH->Shot.STRUCTURE;
            default->fallback;
        };
    }
    private static void context(){
        long tick=ClientEffectClock.ticks();if(contextTick==tick)return;
        LivingEntity actual=subject.cameraTarget();
        DuelScenes.Scene story=mode==Mode.AUTO?DuelScenes.forActor(protagonist):null;
        if(story!=null&&choreographedTarget(story.packet().phase())){
            SorcererCat other=subject.isGojo()?story.sukuna():story.gojo();
            if(story.packet().phase()==DuelScene.MAHORAGA_STRIKE)other=story.mahoraga();
            if(live(other))actual=other;
        }
        if(live(actual))opponent=actual;
        else if(!live(opponent)||opponent.distanceToSqr(subject)>128*128||tick%10==0){
            opponent=world.getEntitiesOfClass(SorcererCat.class,subject.getBoundingBox().inflate(128),
                    c->live(c)&&subject.isRival(c)&&c.distanceToSqr(subject)<128*128)
                    .stream().min(Comparator.comparingDouble(subject::distanceToSqr)).orElse(null);
        }
        ownCast=CatSpells.cameraCast(subject.getUUID());
        foeCast=opponent instanceof SorcererCat cat?CatSpells.cameraCast(cat.getUUID()):null;
        contextTick=tick;
    }
    private static boolean choreographedTarget(int phase){
        // Normal exchanges must follow the server's current target, including a newly joined Mahoraga.
        return switch(phase){
            case DuelScene.DOMAIN_BREAK,DuelScene.SOLO_GOJO,DuelScene.SOLO_HOLD,DuelScene.RESCUE_WHEEL,DuelScene.RESCUE_EMERGE,DuelScene.RESCUE_CUT,DuelScene.RESCUE_EXIT,
                    DuelScene.OPENING_CHARGE,DuelScene.OPENING_GUARD,DuelScene.OPENING_RETURN,
                    DuelScene.DOMAIN_GOJO,DuelScene.DOMAIN_SUKUNA,DuelScene.DOMAIN_CLASH,
                    DuelScene.DOMAIN_GOJO_WIN,DuelScene.DOMAIN_SUKUNA_WIN,DuelScene.WHEEL_SUMMON,
                    DuelScene.MAHORAGA_DESCENT,DuelScene.MAHORAGA_STRIKE,DuelScene.FINALE_BLUE,
                    DuelScene.FINALE_RED,DuelScene.FINALE_FUSION,DuelScene.FINALE_BLAST,DuelScene.STRUCTURE_CLASH->true;
            default->false;
        };
    }
    private static Shot action(float partial){
        int move=subject.combatMove();float age=subject.punchAge(partial);
        if(move==CatCombatMoves.DOMAIN_REVEAL)return Shot.REVEAL;
        if(move==CatCombatMoves.DOMAIN_CAST)return Shot.GESTURE;
        if(openingDomain(ownCast)||openingDomain(foeCast))return Shot.DOMAIN;
        if(ownCast!=null&&!CatSpell.domain(ownCast.kind())){
            if(ownCast.phase()==CatSpell.FLIGHT&&ownCast.stageAge()<18)return Shot.RELEASE;
            if(ownCast.phase()<CatSpell.FLIGHT){
                if(ownCast.age()>=CatSpell.launchTick(ownCast.kind(),ownCast.large())-3)return Shot.RELEASE;
                return ownCast.kind()==CatSpell.BLUE||ownCast.kind()==CatSpell.RED?Shot.DETAIL:Shot.CHARGE;
            }
        }
        if(CatCombatMoves.spell(move)){
            int kind=switch(move){case CatCombatMoves.BLUE_CAST->CatSpell.BLUE;case CatCombatMoves.RED_CAST->CatSpell.RED;
                case CatCombatMoves.PURPLE_CAST->CatSpell.PURPLE;case CatCombatMoves.FURNACE_CAST->CatSpell.FURNACE;
                case CatCombatMoves.CLEAVE_CAST->CatSpell.CLEAVE;default->CatSpell.DISMANTLE;};
            return age>=CatSpell.launchTick(kind,(subject.punchKind()&1)!=0)-3?Shot.RELEASE:Shot.CHARGE;
        }
        if(move==CatCombatMoves.DIVE_KICK)return Shot.DIVE;
        if(move==CatCombatMoves.AIR_CHASE||!subject.onGround()&&subject.isNoGravity()
                ||live(opponent)&&Math.abs(subject.getY()-opponent.getY())>5)return Shot.AIR;
        if(move==CatCombatMoves.DASH||move==CatCombatMoves.BLINK||move==CatCombatMoves.REPOSITION)return Shot.CHASE;
        if(CatCombatMoves.defense(move))return Shot.DEFENSE;
        if(CatCombatMoves.melee(move)||live(opponent)&&subject.distanceToSqr(opponent)<36)return Shot.MELEE;
        if(foeCast!=null&&foeCast.phase()<=CatSpell.FLIGHT)return Shot.DEFENSE;
        if(opponent instanceof SorcererCat cat&&CatCombatMoves.melee(cat.combatMove())&&cat.distanceToSqr(subject)<144)return Shot.MELEE;
        return Shot.DUEL;
    }
    private static boolean openingDomain(CatSpellState cast){return cast!=null&&CatSpell.domain(cast.kind())&&cast.age()<65;}
    public static Frame sample(Entity entity,float partial){
        Minecraft mc=Minecraft.getInstance();
        if(!active()||entity!=subject||mc.level!=world||!live(subject))return null;
        if(mc.isPaused()&&frame!=null)return frame;
        double now=ClientEffectClock.ticks()+Mth.clamp(partial,0,1);
        if(frame!=null&&now==lastSample)return frame;
        double dt=Double.isNaN(lastSample)?1.0/60:CinematicFraming.clamp((now-lastSample)*.05,0,.10);
        lastSample=now;seconds+=dt;context();
        V hero=v(subject.getEyePosition(partial));boolean paired=live(opponent);
        V target=paired?v(opponent.getEyePosition(partial)):hero.add(v(subject.getLookAngle()).scale(8));
        V desired=target.sub(hero).horizontal();
        DuelScenes.Scene story=mode==Mode.AUTO?DuelScenes.forActor(protagonist):null;
        if(story!=null&&(story.packet().phase()==DuelScene.FINALE_RED||story.packet().phase()==DuelScene.FINALE_BLAST||story.packet().phase()==DuelScene.FINALE_FUSION))
            desired=v(Vec3.directionFromRotation(0,Mth.rotLerp(partial,subject.yBodyRotO,subject.yBodyRot))).horizontal();
        if(forward==null)forward=desired;else forward=CinematicFraming.turnToward(forward,desired,CinematicFraming.blend(5,dt));
        var burst=UnlimitedPurpleEffects.cameraFocus(subject.getUUID(),paired?opponent.getUUID():null,partial);
        if(subject.combatMove()==CatCombatMoves.DOMAIN_REVEAL)burst=null;
        Shot ordinary=burst!=null?Shot.FINALE:action(partial);
        Shot shot=story==null?SHOTS.choose(ordinary,seconds):narrative(story,ordinary);
        V effect=ownCast!=null?v(ownCast.point()):hero.add(v(subject.getLookAngle()).scale(3));
        double radius=ownCast!=null?ownCast.kind()==CatSpell.BLUE?Math.min(3,ownCast.radius()*.24):
                ownCast.kind()==CatSpell.RED?.65:ownCast.radius():shot==Shot.DOMAIN?40:1;
        if(burst!=null){effect=v(burst.point());radius=burst.frameExtent();}
        if(story!=null){
            DuelScene p=story.packet();
            boolean authoredFocus=switch(p.phase()){
                case DuelScene.DOMAIN_BREAK,DuelScene.SOLO_GOJO,DuelScene.SOLO_HOLD,DuelScene.RESCUE_WHEEL,DuelScene.RESCUE_EMERGE,DuelScene.RESCUE_CUT,DuelScene.RESCUE_EXIT,
                        DuelScene.OPENING_CHARGE,DuelScene.OPENING_GUARD,DuelScene.DOMAIN_GOJO,DuelScene.DOMAIN_SUKUNA,
                        DuelScene.DOMAIN_CLASH,DuelScene.DOMAIN_GOJO_WIN,DuelScene.DOMAIN_SUKUNA_WIN,
                        DuelScene.WHEEL_SUMMON,DuelScene.MAHORAGA_DESCENT,DuelScene.MAHORAGA_STRIKE,
                        DuelScene.FINALE_BLUE,DuelScene.FINALE_RED,DuelScene.FINALE_FUSION,DuelScene.FINALE_BLAST,DuelScene.STRUCTURE_CLASH->true;
                default->false;
            };
            if(authoredFocus&&p.radius()>0){effect=v(p.focus());radius=p.radius();}
            if(p.phase()==DuelScene.DOMAIN_GOJO||p.phase()==DuelScene.DOMAIN_SUKUNA||p.phase()==DuelScene.SOLO_GOJO){effect=hero.add(forward.scale(1.5));radius=1;}
            if(p.phase()==DuelScene.FINALE_FUSION&&burst!=null){effect=v(burst.point());radius=burst.frameExtent();}
            if(p.phase()==DuelScene.DOMAIN_GOJO_WIN||p.phase()==DuelScene.SOLO_HOLD){
                UnlimitedVoid domain=story.gojo()==null?null:CatSpells.cameraVoid(story.gojo().getUUID());
                V axis=domain==null?forward:v(domain.axis);
                effect=hero.add(axis.scale(20));radius=1;
            }
            if(p.phase()==DuelScene.DOMAIN_SUKUNA_WIN){
                MalevolentShrine shrine=story.sukuna()==null?null:CatSpells.cameraShrine(story.sukuna().getUUID());
                if(shrine!=null){effect=v(shrine.shrineBase.add(0,ShrineMesh.MODEL_HEIGHT*shrine.scale*.48,0));
                    radius=ShrineMesh.MODEL_HEIGHT*shrine.scale*.54;}
                else{effect=v(p.focus());radius=4;}
            }
        }
        boolean structureShot=shot==Shot.STRUCTURE&&mode==Mode.AUTO&&story!=null;
        if(structureShot){
            if(structureDuel!=story.packet().duel()){
                structureDuel=story.packet().duel();structureClearance=false;STRUCTURE_TRACK.reset();
            }
            STRUCTURE_TRACK.update(effect,radius,dt);effect=STRUCTURE_TRACK.focus();radius=STRUCTURE_TRACK.radius();
        }else{structureClearance=false;STRUCTURE_TRACK.reset();structureDuel=Long.MIN_VALUE;}
        UnlimitedVoid rescueField=rescueField(story);
        double aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        Scene scene=new Scene(hero,target,forward,v(subject.getDeltaMovement()).scale(20).limited(40),effect,radius,
                Math.max(subject.getBbWidth(),subject.getBbHeight())*.60,aspect,
                paired&&!(rescueField!=null&&shot==Shot.DESCENT),seconds,side);
        Plan plan=shot==Shot.STRUCTURE&&story!=null&&mode==Mode.AUTO
                ?CinematicFraming.structure(scene,story.progress(partial)):CinematicFraming.plan(scene,shot,mode);
        if(rescueField!=null)plan=CinematicFraming.insideDomain(plan,v(rescueField.center),
                rescueField.radius*rescueField.expansion(ClientEffectClock.ticks(),partial));
        V wanted=plan.eye();
        if(mode!=Mode.POV&&shot!=Shot.BARRIER){
            if(structureShot?!structureClearance:collisionTick==Long.MIN_VALUE||ClientEffectClock.ticks()-collisionTick>=3){
                chooseClearance(hero,target,plan,paired);collisionTick=ClientEffectClock.ticks();
                if(structureShot)structureClearance=true;
            }
            wanted=alternative(plan,collisionAngle,collisionLift);
        }
        if(structureShot){
            // Keep the chosen side throughout the throw. Sweep from the previous lens position,
            // not from Gojo: an intervening wall must not yank the camera back to his body.
            wanted=clearEye(eye==null?hero:eye,wanted);
        }
        // A teleport needs a new establishing position; ordinary movement uses time-based damping.
        boolean soft=seconds<softMoveUntil;
        boolean snap=eye==null||!structureShot&&!soft&&(eye.sub(hero).length()>(shot==Shot.FINALE?190:72)||wanted.sub(eye).length()>48)||mode==Mode.POV;
        double speed=structureShot?8:shot==Shot.CHASE||shot==Shot.DIVE||shot==Shot.AIR?9:6;
        if(soft)speed=3.8;
        double follow=CinematicFraming.blend(speed,dt);
        eye=snap?wanted:soft&&story!=null&&story.packet().phase()==DuelScene.WHEEL_SUMMON
                ?CinematicFraming.orbitBlend(eye,wanted,hero,follow):eye.lerp(wanted,follow);
        aim=aim==null||snap?plan.aim():aim.lerp(plan.aim(),CinematicFraming.blend(structureShot?12:8,dt));

        fov+= (plan.fov()-fov)*CinematicFraming.blend(4,dt);
        roll=steadyShrineShot()?0:roll+(plan.roll()-roll)*CinematicFraming.blend(5,dt);
        if(shot==Shot.GESTURE&&mode==Mode.AUTO){
            Plan complete=CinematicFraming.includeSphere(new Plan(eye,aim,fov,roll,true),hero.add(V.UP.scale(.35)),1.45,aspect);
            eye=complete.eye();
        }
        if(shot==Shot.DESCENT&&mode==Mode.AUTO){
            // The landing/raised-blade model is larger than its resting collision box.
            // Enforce its full silhouette after damping, while the actor drops eighteen blocks.
            Plan complete=CinematicFraming.includeSphere(new Plan(eye,aim,fov,roll,true),hero.add(V.UP.scale(.10)),3.0,aspect);
            eye=complete.eye();
        }
        if(burst!=null&&mode==Mode.AUTO){
            Plan complete=CinematicFraming.includeSphere(new Plan(eye,aim,fov,roll,true),v(burst.point()),burst.extent(),aspect);
            eye=complete.eye();
        }
        if(story!=null&&story.packet().phase()==DuelScene.FINALE_BLAST&&mode==Mode.AUTO){
            // The charge insert can be tight; the blast wide shot also keeps the ground victims visible.
            Plan complete=new Plan(eye,aim,fov,roll,true);
            for(SorcererCat victim:new SorcererCat[]{story.sukuna(),story.mahoraga()})if(live(victim))
                complete=CinematicFraming.includeSphere(complete,v(victim.getEyePosition(partial)),victim.isMahoraga()?2.5:1.8,aspect);
            eye=complete.eye();
        }
        if(mode!=Mode.POV&&shot!=Shot.BARRIER&&!structureShot)eye=clearEye(hero,eye);
        if(shot==Shot.BARRIER&&mode==Mode.AUTO){
            // A ray from an actor inside a building would incorrectly pull this exterior shot indoors.
            eye=plan.eye();aim=effect;fov=70;roll=0;
            if(world.hasChunkAt(BlockPos.containing(vec(eye)))){
                int floor=world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Mth.floor(eye.x()),Mth.floor(eye.z()));
                eye=new V(eye.x(),Math.max(eye.y(),floor+3),eye.z());
            }
            eye=CinematicFraming.includeSphere(new Plan(eye,aim,fov,0,true),effect,radius*1.04,aspect).eye();
        }
        if(rescueField!=null){
            // Apply again after camera damping, model fitting and collision offsets.
            Plan inside=CinematicFraming.insideDomain(new Plan(eye,aim,fov,roll,true),v(rescueField.center),
                    rescueField.radius*rescueField.expansion(ClientEffectClock.ticks(),partial));
            eye=inside.eye();aim=inside.aim();fov=inside.fov();
        }
        if(!eye.finite()||!aim.finite()){eye=hero;aim=hero.add(forward);}
        frame=new Frame(vec(eye),(float)CinematicFraming.yaw(eye,aim),(float)CinematicFraming.pitch(eye,aim),
                (float)roll,fov,plan.detached()&&eye.sub(hero).length()>Math.max(.8,subject.getBbWidth()*.65));
        return frame;
    }
    private static UnlimitedVoid rescueField(DuelScenes.Scene story){
        if(story==null||mode!=Mode.AUTO||story.gojo()==null)return null;
        int phase=story.packet().phase();
        return phase>=DuelScene.RESCUE_WHEEL&&phase<=DuelScene.RESCUE_EXIT
                ?CatSpells.cameraVoid(story.gojo().getUUID()):null;
    }
    private static V clearShrineEye(V hero,V wanted,MalevolentShrine shrine){
        if(shrine==null)return wanted;
        Vec3 a=vec(hero).subtract(shrine.shrineBase),b=vec(wanted).subtract(shrine.shrineBase);
        Vec3 from=new Vec3(a.dot(shrine.right),a.y,a.dot(shrine.front));
        Vec3 to=new Vec3(b.dot(shrine.right),b.y,b.dot(shrine.front));
        AABB model=new AABB(-ShrineMesh.HALF_WIDTH*shrine.scale,-.3*shrine.scale,-ShrineMesh.HALF_DEPTH*shrine.scale,
                ShrineMesh.HALF_WIDTH*shrine.scale,ShrineMesh.MODEL_HEIGHT*shrine.scale,ShrineMesh.HALF_DEPTH*shrine.scale).inflate(.25);
        if(model.contains(from))return wanted;
        var hit=model.clip(from,to);
        if(hit.isEmpty())return wanted;
        double length=from.distanceTo(to),clear=Math.max(0,from.distanceTo(hit.get())-.25);
        return length<1e-6?hero:hero.lerp(wanted,Math.min(1,clear/length));
    }
    private static V alternative(Plan plan,double angle,double lift){
        V d=plan.eye().sub(plan.aim());double c=Math.cos(angle),s=Math.sin(angle);
        return plan.aim().add(new V(d.x()*c-d.z()*s,d.y()+lift,d.x()*s+d.z()*c));
    }
    private static void chooseClearance(V hero,V target,Plan plan,boolean paired){
        double best=Double.NEGATIVE_INFINITY,bestAngle=collisionAngle,bestLift=collisionLift;
        double[][] candidates={{collisionAngle,collisionLift},{0,0},{.45,0},{-.45,0},{0,3},{.85,3},{-.85,3}};
        for(double[] candidate:candidates){
            V wanted=alternative(plan,candidate[0],candidate[1]);
            if(!world.hasChunkAt(BlockPos.containing(vec(wanted))))continue;
            V clear=clearEye(hero,wanted);double lost=wanted.sub(clear).length();
            double score=20-lost*2-Math.abs(candidate[0])*.8-candidate[1]*.15;
            if(clear.sub(hero).length()<2)score-=10;
            if(paired&&blocked(clear,target))score-=5;
            if(candidate[0]==collisionAngle&&candidate[1]==collisionLift)score+=.7;
            if(score>best){best=score;bestAngle=candidate[0];bestLift=candidate[1];}
        }
        collisionAngle=bestAngle;collisionLift=bestLift;
    }
    private static boolean blocked(V a,V b){return world.clip(new ClipContext(vec(a),vec(b),ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,subject)).getType()!=HitResult.Type.MISS;}
    private static V clearEye(V hero,V wanted){
        if(!world.hasChunkAt(BlockPos.containing(vec(wanted))))wanted=hero.add(wanted.sub(hero).limited(4));
        // Four near-plane probes also keep the corners of the view out of adjacent walls.
        V delta=wanted.sub(hero);double length=delta.length();if(length<.001)return hero;
        double distance=length;
        for(V offset:new V[]{new V(0,0,0),new V(.18,.16,0),new V(-.18,.16,0),new V(0,-.16,.18),new V(0,.16,-.18)}){
            Vec3 from=vec(hero.add(offset)),to=vec(wanted.add(offset));
            HitResult hit=world.clip(new ClipContext(from,to,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,subject));
            if(hit.getType()!=HitResult.Type.MISS)distance=Math.min(distance,Math.max(0,hit.getLocation().distanceTo(from)-.30));
        }
        return hero.add(delta.scale(distance/length));
    }
    private static V v(Vec3 v){return new V(v.x,v.y,v.z);}
    private static Vec3 vec(V v){return new Vec3(v.x(),v.y(),v.z());}
}
