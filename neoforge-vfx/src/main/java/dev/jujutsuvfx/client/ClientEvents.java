package dev.jujutsuvfx.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.DomainVolume;
import dev.jujutsuvfx.network.DomainControl;
import dev.jujutsuvfx.network.BladeBatch;
import dev.jujutsuvfx.network.BladeImpact;
import dev.jujutsuvfx.network.FurnaceState;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.core.LimitlessTechnique;
import dev.jujutsuvfx.network.TechniqueState;
import dev.jujutsuvfx.item.SkillItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class ClientEvents {
    private static final int MAX_EFFECTS=4096;
    static final List<Rift> RIFTS=new ArrayList<>();
    private static ClientLevel owner;
    private static DomainBarrage barrage;
    private static MalevolentShrine shrine;
    private static UnlimitedVoid unlimitedVoid;
    private static ShrineFeedback feedback;
    private static Timing shrineTime,voidTime;
    private static DomainClash clash;
    private static FurnaceCast furnace;
    private static int furnaceSerial;
    private static LimitlessCast blue,red,purple;
    private static int techniqueSerial;
    private static final java.util.Map<Integer,LimitlessCast> TECHNIQUES=new java.util.LinkedHashMap<>();
    private static final java.util.Map<Integer,FurnaceCast> FURNACES=new java.util.LinkedHashMap<>();

    static void installSkillItems(){
        SkillItem.setClientActions(new SkillItem.ClientActions(){
            @Override public void press(Player player,SkillItem.Kind kind,InteractionHand hand){
                Minecraft mc=Minecraft.getInstance();if(player!=mc.player||!inWorld(mc))return;
                switch(kind){
                    case BLUE -> {
                        if(blue!=null)blue.park();
                        blue=new LimitlessCast(mc,++techniqueSerial,LimitlessTechnique.BLUE,
                                LimitlessTechnique.DEFAULT_BLUE_RADIUS,LimitlessTechnique.DEFAULT_BLUE_SECONDS*20,hand);
                        TECHNIQUES.put(blue.session,blue);
                        TechniqueHands.begin(dev.jujutsuvfx.network.SkillGesture.BLUE,blue.session,hand,true);
                    }
                    case RED -> {
                        if(red!=null&&red.redCommitted())return;
                        TechniqueHands.interrupt();
                        if(red!=null&&red.phase()==0)red.releaseRed();
                        red=new LimitlessCast(mc,++techniqueSerial,LimitlessTechnique.RED,LimitlessTechnique.DEFAULT_RED_RADIUS,0,hand);
                        TECHNIQUES.put(red.session,red);
                    }
                    case PURPLE -> {
                        if(purple!=null&&purple.committedPurple())return;
                        TechniqueHands.interrupt();
                        if(purple!=null&&purple.preparingPurple())purple.release();
                        purple=new LimitlessCast(mc,++techniqueSerial,LimitlessTechnique.PURPLE,
                                LimitlessTechnique.PURPLE_RADIUS,0,hand);
                        TECHNIQUES.put(purple.session,purple);
                    }
                    case FUGA -> startFurnace(FurnaceFlight.DEFAULT_RADIUS);
                    case VOID -> start(true,DomainControl.DEFAULT_RADIUS,DomainSettingsScreen.seconds(true));
                    case SHRINE -> start(false,DomainVolume.DEFAULT_SHRINE_RADIUS,DomainSettingsScreen.seconds(false));
                }
            }
            @Override public void release(Player player,SkillItem.Kind kind){
                if(player==Minecraft.getInstance().player&&kind==SkillItem.Kind.BLUE&&blue!=null){blue.park();TechniqueHands.release(dev.jujutsuvfx.network.SkillGesture.BLUE,blue.session);}
                if(player==Minecraft.getInstance().player&&kind==SkillItem.Kind.PURPLE&&purple!=null)purple.release();
                if(player==Minecraft.getInstance().player&&kind==SkillItem.Kind.RED&&red!=null)red.releaseRed();
            }
        });
    }
    static final class Timing {
        final int session;
        long deadline,release=Long.MAX_VALUE;
        boolean waiting;
        Timing(int session,long deadline){this.session=session;this.deadline=deadline;}
        boolean closing(){return release!=Long.MAX_VALUE;}
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("rift").executes(c->spawn(30,12))
                .then(Commands.literal("config").executes(c->VfxConfigScreen.requestOpen()))
                .then(domainCommand("shrine",false)).then(domainCommand("void",true))
                .then(blueCommand())
                .then(Commands.literal("purple").executes(c->startTechnique(LimitlessTechnique.PURPLE,LimitlessTechnique.PURPLE_RADIUS,0)))
                .then(Commands.literal("red").executes(c->startTechnique(LimitlessTechnique.RED,LimitlessTechnique.DEFAULT_RED_RADIUS,0))
                        .then(Commands.argument("radius",FloatArgumentType.floatArg(3,LimitlessTechnique.MAX_RADIUS))
                                .executes(c->startTechnique(LimitlessTechnique.RED,FloatArgumentType.getFloat(c,"radius"),0))))
                .then(Commands.literal("fuga").executes(c->startFurnace(FurnaceFlight.DEFAULT_RADIUS))
                        .then(Commands.argument("radius",FloatArgumentType.floatArg(3,FurnaceFlight.MAX_RADIUS))
                                .executes(c->startFurnace(FloatArgumentType.getFloat(c,"radius")))))
                .then(Commands.literal("spawn").executes(c->spawn(30,12))
                        .then(Commands.argument("seconds",FloatArgumentType.floatArg(2,300))
                                .executes(c->spawn(FloatArgumentType.getFloat(c,"seconds"),12))
                                .then(Commands.argument("length",FloatArgumentType.floatArg(2,32))
                                        .executes(c->spawn(FloatArgumentType.getFloat(c,"seconds"),FloatArgumentType.getFloat(c,"length"))))))
                .then(Commands.literal("slash").executes(c->{
                    Minecraft mc=Minecraft.getInstance();if(!inWorld(mc))return 0;
                    DomainBarrage.burst(mc,DomainBarrage.focus(mc));return 1;
                }))
                .then(Commands.literal("barrage").executes(c->startBarrage()))
                .then(Commands.literal("stop").executes(c->{
                    if(LocalDomains.empty()&&FURNACES.isEmpty()&&TECHNIQUES.isEmpty())return 0;
                    cancelTechnique(LimitlessTechnique.BLUE);cancelTechnique(LimitlessTechnique.RED);
                    cancelTechnique(LimitlessTechnique.PURPLE);
                    cancelFurnace();
                    LocalDomains.closeAll();return 1;
                }))
                .then(Commands.literal("clear").executes(c->{clearAll();return 1;}))
                .then(Commands.literal("lighting").executes(c->TechniqueLighting.setStrength(2))
                        .then(Commands.argument("strength",FloatArgumentType.floatArg(0,5))
                                .executes(c->TechniqueLighting.setStrength(FloatArgumentType.getFloat(c,"strength")))))
                .then(Commands.literal("boundary")
                        .then(Commands.literal("on").executes(c->boundary(true)))
                        .then(Commands.literal("off").executes(c->boundary(false))))
                .then(Commands.literal("graphics").executes(c->{message(RenderDiagnostics.diagnostic());return 1;}))
                .then(Commands.literal("status").executes(c->{
                    message("宿傩 "+status(shrineTime)+"；五条 "+status(voidTime)+"；对抗 "+(clash!=null)
                            +"；刀痕 "+RIFTS.size()+"；领域着色器 "+DomainRenderer.ready()+" / "+UnlimitedVoidRenderer.ready()
                            +"；对抗着色器 "+DomainClashRenderer.ready()+"；灶·开 "+(furnace==null?"未开启":furnace.status())
                            +"；灶火着色器 "+FurnaceRenderer.ready()+"；苍 "+(blue==null?"未开启":blue.status())
                            +"；赫 "+(red==null?"未开启":red.status())+"；茈 "+(purple==null?"未开启":purple.status())
                            +"；术式着色器 "+LimitlessRenderer.ready()+"；环境光 "+TechniqueLighting.ready()+" / "+TechniqueLighting.strength());return 1;
                })));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> blueCommand(){
        return Commands.literal("blue").executes(c->startTechnique(LimitlessTechnique.BLUE,LimitlessTechnique.DEFAULT_BLUE_RADIUS,LimitlessTechnique.DEFAULT_BLUE_SECONDS))
                .then(Commands.argument("radius",FloatArgumentType.floatArg(3,LimitlessTechnique.MAX_RADIUS))
                        .executes(c->startTechnique(LimitlessTechnique.BLUE,FloatArgumentType.getFloat(c,"radius"),LimitlessTechnique.DEFAULT_BLUE_SECONDS))
                        .then(Commands.argument("seconds",FloatArgumentType.floatArg(2,30))
                                .executes(c->startTechnique(LimitlessTechnique.BLUE,FloatArgumentType.getFloat(c,"radius"),FloatArgumentType.getFloat(c,"seconds")))));
    }
    private static int startTechnique(int kind,float radius,float seconds){
        Minecraft mc=Minecraft.getInstance();if(!inWorld(mc))return 0;
        if(kind==LimitlessTechnique.PURPLE&&purple!=null&&purple.committedPurple())return 0;
        if(kind==LimitlessTechnique.RED&&red!=null&&red.redCommitted())return 0;
        if(kind==LimitlessTechnique.BLUE&&blue!=null)blue.park();
        LimitlessCast cast=new LimitlessCast(mc,++techniqueSerial,kind,radius,Math.round(seconds*20));
        if(kind==LimitlessTechnique.BLUE)TechniqueHands.begin(dev.jujutsuvfx.network.SkillGesture.BLUE,cast.session,null,false);
        else TechniqueHands.interrupt();
        TECHNIQUES.put(cast.session,cast);
        if(kind==LimitlessTechnique.BLUE)blue=cast;else if(kind==LimitlessTechnique.RED)red=cast;else purple=cast;return 1;
    }
    public static void techniqueState(TechniqueState packet){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.level!=owner)return;
        LimitlessCast cast=TECHNIQUES.get(packet.session());
        if(cast==null||!cast.networked||cast.kind!=packet.kind())return;
        if(packet.kind()==LimitlessTechnique.BLUE&&packet.phase()==TechniqueState.BLUE_PARKED)TechniqueHands.release(dev.jujutsuvfx.network.SkillGesture.BLUE,packet.session());
        if(packet.phase()==TechniqueState.CANCELED)cancelTechnique(cast);else cast.receive(packet);
    }
    private static void cancelTechnique(int kind){
        for(LimitlessCast cast:List.copyOf(TECHNIQUES.values()))if(cast.kind==kind)cancelTechnique(cast);
    }
    private static void cancelTechnique(LimitlessCast cast){
        TECHNIQUES.remove(cast.session,cast);cast.stop();
        if(cast.blue())TechniqueHands.cancel(dev.jujutsuvfx.network.SkillGesture.BLUE,cast.session);
        if(blue==cast)blue=latestTechnique(LimitlessTechnique.BLUE);
        if(red==cast)red=latestTechnique(LimitlessTechnique.RED);
        if(purple==cast)purple=latestTechnique(LimitlessTechnique.PURPLE);
        if(TECHNIQUES.isEmpty()&&CatSpells.techniques().isEmpty())LimitlessRenderer.release();
    }
    private static LimitlessCast latestTechnique(int kind){
        return TECHNIQUES.values().stream().filter(c->c.kind==kind).reduce((a,b)->b).orElse(null);
    }
    static void absorbTechnique(dev.jujutsuvfx.network.UnlimitedPurple.Source source,int kind){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.player.getUUID().equals(source.caster())){
            LimitlessCast cast=TECHNIQUES.get(source.session());if(cast!=null&&cast.kind==kind)cancelTechnique(cast);
        }
        CatSpells.absorb(source,kind);
    }
    static LimitlessCast purpleCast(){return purple;}
    static LimitlessCast redCast(){return red;}
    public static boolean purplePreparing(){
        Minecraft mc=Minecraft.getInstance();
        return mc.player!=null&&mc.player.isAlive()&&mc.level==owner&&purple!=null&&purple.preparingPurple();
    }
    public static boolean purpleInputLocked(){
        Minecraft mc=Minecraft.getInstance();
        return mc.player!=null&&mc.player.isAlive()&&mc.level==owner&&purple!=null&&purple.committedPurple();
    }
    private static int startFurnace(float radius){
        Minecraft mc=Minecraft.getInstance();if(!inWorld(mc))return 0;
        furnace=new FurnaceCast(mc,++furnaceSerial,radius);FURNACES.put(furnace.session,furnace);
        TechniqueHands.begin(dev.jujutsuvfx.network.SkillGesture.FURNACE,furnace.session,null,false);return 1;
    }
    public static void furnaceState(FurnaceState packet){
        Minecraft mc=Minecraft.getInstance();FurnaceCast cast=FURNACES.get(packet.session());
        if(mc.level==null||mc.level!=owner||cast==null||!cast.networked)return;
        if(packet.phase()==FurnaceState.LAUNCHED)TechniqueHands.release(dev.jujutsuvfx.network.SkillGesture.FURNACE,packet.session());
        if(packet.phase()==FurnaceState.CANCELED)cancelFurnace(cast);else cast.receive(packet);
    }
    private static void cancelFurnace(){for(FurnaceCast cast:List.copyOf(FURNACES.values()))cancelFurnace(cast);}
    private static void cancelFurnace(FurnaceCast cast){
        FURNACES.remove(cast.session,cast);TechniqueHands.cancel(dev.jujutsuvfx.network.SkillGesture.FURNACE,cast.session);cast.stop();
        if(furnace==cast)furnace=FURNACES.values().stream().reduce((a,b)->b).orElse(null);
        if(FURNACES.isEmpty()&&CatSpells.furnaces().isEmpty())FurnaceRenderer.release();
    }
    private static LiteralArgumentBuilder<CommandSourceStack> domainCommand(String name,boolean gojo){
        return Commands.literal(name).executes(c->start(gojo,gojo?DomainControl.DEFAULT_RADIUS:DomainVolume.DEFAULT_SHRINE_RADIUS,DomainSettingsScreen.seconds(gojo)))
                .then(Commands.argument("radius",FloatArgumentType.floatArg(3,DomainVolume.maxRadius(gojo?DomainVolume.UNLIMITED_VOID:DomainVolume.SHRINE)))
                        .executes(c->start(gojo,FloatArgumentType.getFloat(c,"radius"),DomainSettingsScreen.seconds(gojo)))
                        .then(Commands.argument("seconds",FloatArgumentType.floatArg(5,120))
                                .executes(c->startTimed(gojo,FloatArgumentType.getFloat(c,"radius"),FloatArgumentType.getFloat(c,"seconds")))));
    }
    private static int startTimed(boolean gojo,float radius,float seconds){
        Minecraft mc=Minecraft.getInstance();if(!inWorld(mc))return 0;
        int result=LocalDomains.start(mc,gojo,radius,seconds,true);selectDomains();return result;
    }
    private static int start(boolean gojo,float radius,float seconds){
        Minecraft mc=Minecraft.getInstance();if(!inWorld(mc))return 0;
        int result=LocalDomains.start(mc,gojo,radius,seconds);selectDomains();return result;
    }
    private static int startBarrage(){Minecraft mc=Minecraft.getInstance();if(!inWorld(mc))return 0;
        int result=LocalDomains.barrage(mc);selectDomains();return result;}
    static void reschedule(int session,int remaining){LocalDomains.reschedule(session,remaining);selectDomains();}
    static void clashConfirmed(int session,int peer,int remaining){LocalDomains.clash(session,peer,remaining);selectDomains();}
    static void damageSettled(int session){LocalDomains.settled(session);selectDomains();}
    static void domainCanceled(int session){LocalDomains.end(session);selectDomains();}
    static void blades(BladeBatch packet){LocalDomains.blades(packet);}
    static void bladeImpact(BladeImpact packet){LocalDomains.impact(packet);}
    private static void selectDomains(){
        LocalDomains.Slot s=LocalDomains.shrine(),v=LocalDomains.gojo();
        shrine=s==null?null:s.shrine;feedback=s==null?null:s.feedback;barrage=s==null?null:s.barrage;shrineTime=s==null?null:s.time;
        unlimitedVoid=v==null?null:v.gojo;voidTime=v==null?null:v.time;
        clash=s!=null&&v!=null?s.clash:null;
        CatSpells.select();
    }
    private static void clearClash(){clash=null;DomainClashRenderer.release();}
    private static void clearAll(){
        ClientShrineFocus.clear();
        UnlimitedPurpleEffects.clear();
        CatSpells.clear();
        ClientFistEffects.clear();
        ClientSukunaSlashes.clear();
        cancelTechnique(LimitlessTechnique.BLUE);cancelTechnique(LimitlessTechnique.RED);
        cancelTechnique(LimitlessTechnique.PURPLE);
        cancelFurnace();
        LocalDomains.clear();selectDomains();clearClash();ClientDomainControl.stopAll();RIFTS.clear();
        TechniqueHands.clear();
        RiftRenderer.release();WaterSceneCapture.release();TechniqueLighting.release();DomainWorldCapture.release();
    }
    private static int boundary(boolean enclosed){if(LocalDomains.empty())return 0;LocalDomains.boundary(enclosed);return 1;}
    private static String status(Timing time){
        if(time==null||owner==null)return "未开启";
        String phase=time.closing()?"消退":time.waiting?"等待确认":Math.max(0,(time.deadline-ClientEffectClock.ticks()+19)/20)+" 秒";
        return phase+(ClientDomainControl.networked(time.session)?"（服务端控制）":"（仅画面）");
    }
    static void addSlash(Rift rift){
        if(RIFTS.size()>=MAX_EFFECTS)for(int i=0;i<RIFTS.size();i++)
            if(RIFTS.get(i).slash()&&!RIFTS.get(i).persistent()){RIFTS.remove(i);break;}
        if(RIFTS.size()<MAX_EFFECTS)RIFTS.add(rift);
    }
    static void prepareSukunaEffects(ClientLevel level){ ensureLevel(level); }
    private static int spawn(float seconds,float length){
        Minecraft mc=Minecraft.getInstance();if(!inWorld(mc))return 0;
        Vec3 forward=mc.player.getLookAngle(),horizontal=new Vec3(forward.x,0,forward.z);
        if(horizontal.lengthSqr()<.001)horizontal=new Vec3(0,0,1);
        horizontal=horizontal.normalize();
        Vec3 normal=horizontal.scale(-1),right=new Vec3(-horizontal.z,0,horizontal.x);
        double tilt=Math.toRadians(18);
        Vec3 tiltedRight=right.scale(Math.cos(tilt)).add(0,-Math.sin(tilt),0);
        Vec3 up=right.scale(Math.sin(tilt)).add(0,Math.cos(tilt),0);
        Vec3 center=mc.player.getEyePosition().add(horizontal.scale(Math.max(7,length*.75))).add(0,length*.22,0);
        if(RIFTS.stream().filter(rift->!rift.slash()).count()>=4)
            for(int i=0;i<RIFTS.size();i++)if(!RIFTS.get(i).slash()){RIFTS.remove(i);break;}
        addSlash(new Rift(center,tiltedRight,up,normal,length,seconds,ClientEffectClock.ticks(),
                mc.level.random.nextFloat()*100,Vec3.ZERO,false,false));return 1;
    }
    private static boolean inWorld(Minecraft mc){
        if(mc.player==null||mc.level==null)return false;
        ensureLevel(mc.level);return true;
    }
    private static void message(String text){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null)mc.player.displayClientMessage(Component.literal("[咒术 VFX] "+text),false);
    }
    private static void ensureLevel(ClientLevel level){if(owner!=level){clearAll();owner=level;ClientEffectClock.changeLevel(level);ClientEntityStasis.changeLevel(level);ClientFistEffects.changeLevel(level);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void advanceEffects(ClientTickEvent.Pre event){
        Minecraft mc=Minecraft.getInstance();ensureLevel(mc.level);
        ClientEffectClock.tick(mc.level,mc.isPaused());
        // Observe a pause even while an integrated server is stuck inside a long simulation tick.
        if(mc.getSingleplayerServer()!=null)
            dev.jujutsuvfx.server.PurpleCastClock.observePause(mc.getSingleplayerServer(),mc.isPaused());
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event){ensureLevel(null);}
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();ensureLevel(mc.level);
        if(mc.level==null||mc.isPaused())return;
        UnlimitedPurpleEffects.tick();
        ClientFistEffects.tick(mc);
        CatSpells.tick(mc);
        ClientShrineFocus.tick();
        long now=ClientEffectClock.ticks();
        LocalDomains.tick(mc);selectDomains();
        RIFTS.removeIf(rift->rift.age(now,0)>=rift.duration());
        for(LimitlessCast cast:List.copyOf(TECHNIQUES.values())){
            if(mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()||cast.expired(now))cancelTechnique(cast);
            else cast.tick(mc);
        }
        for(FurnaceCast cast:List.copyOf(FURNACES.values())){
            if(mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()||cast.expired(now))cancelFurnace(cast);
            else cast.tick(mc);
        }
        if(RIFTS.isEmpty()){
            RiftRenderer.release();PersistentScarRenderer.release();
            if(LocalDomains.empty()&&CatSpells.shrine()==null&&CatSpells.gojo()==null)WaterSceneCapture.release();
        }
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){ClientFistEffects.beginFrame();InfinityBarrierRenderer.beginFrame();DomainWorldCapture.beginFrame();return;}
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS){DomainWorldCapture.terrain(event);return;}
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES){InfinityBarrierRenderer.endCapture();DomainWorldCapture.entities();return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        Minecraft mc=Minecraft.getInstance();ensureLevel(mc.level);if(mc.level==null)return;
        MalevolentShrine shrine=visualShrine();UnlimitedVoid unlimitedVoid=visualVoid();
        DomainClash clash=ClientEvents.visualClash();
        List<LimitlessCast> techniques=new ArrayList<>(CatSpells.techniques());
        techniques.addAll(TECHNIQUES.values());
        List<FurnaceCast> furnaces=new ArrayList<>(CatSpells.furnaces());furnaces.addAll(FURNACES.values());
        TechniqueLighting.render(event,techniques,furnaces,shrine,unlimitedVoid);
        boolean combined=shrine!=null&&unlimitedVoid!=null&&DomainClashRenderer.render(event,shrine,unlimitedVoid,clash,
                ()->renderShrine(event),()->UnlimitedVoidRenderer.render(event,unlimitedVoid));
        if(!combined){
            renderShrine(event);
            if(unlimitedVoid!=null)UnlimitedVoidRenderer.render(event,unlimitedVoid);
        }
        for(FurnaceCast cast:furnaces)FurnaceRenderer.render(event,cast);
        // Compose distant cores first; each technique samples the current domains and preceding cores.
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);Vec3 camera=event.getCamera().getPosition();
        techniques.sort((a,b)->Double.compare(b.center(mc,partial).distanceToSqr(camera),a.center(mc,partial).distanceToSqr(camera)));
        for(LimitlessCast cast:techniques)LimitlessRenderer.render(event,cast);
        UnlimitedPurpleEffects.render(event);
        ClientFistEffects.render(event);
        InfinityBarrierRenderer.render(event);
    }
    private static MalevolentShrine visualShrine(){return CatSpells.shrine();}
    static UnlimitedVoid visualVoid(){return CatSpells.gojo();}
    private static ShrineFeedback visualFeedback(){return CatSpells.feedback()!=null?CatSpells.feedback():feedback;}
    private static DomainClash visualClash(){return CatSpells.clash();}
    private static void renderShrine(RenderLevelStageEvent event){
        MalevolentShrine shrine=visualShrine();ShrineFeedback feedback=visualFeedback();
        java.util.Map<Integer,MalevolentShrine> domains=new java.util.LinkedHashMap<>();
        for(MalevolentShrine domain:LocalDomains.shrines())domains.put(domain.session,domain);
        for(MalevolentShrine domain:CatSpells.shrines())domains.put(domain.session,domain);
        for(MalevolentShrine domain:domains.values())DomainRenderer.render(event,domain);
        if(shrine!=null)DomainSkyRenderer.render(event,shrine);
        // Grade the scene first; the focused black/white cuts must remain untinted.
        ClientShrineFocus.render(event,shrine);
        if(!RIFTS.isEmpty()){
            for(var group:ClientShrineFocus.groups(RIFTS).entrySet()){
                var blades=group.getValue();MalevolentShrine boundary=domains.get(group.getKey());
                RiftEdgeRenderer.Frame edges=RiftEdgeRenderer.prepare(event,blades);
                RiftRenderer.render(event,blades,boundary);SlashBatchRenderer.render(event,blades,boundary);
                PersistentScarRenderer.render(event,blades,boundary);RiftEdgeRenderer.render(event,edges,boundary);
            }
        }
        if(feedback!=null)ShrineImpactRenderer.render(event,feedback);
    }
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=owner||mc.options.screenEffectScale().get()<.001)return;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        float scale=mc.options.screenEffectScale().get().floatValue();
        long now=ClientEffectClock.ticks();
        MalevolentShrine shrine=visualShrine();UnlimitedVoid unlimitedVoid=visualVoid();ShrineFeedback feedback=visualFeedback();
        float basePitch=event.getPitch(),baseYaw=event.getYaw(),baseRoll=event.getRoll();
        CatSpells.camera(event,now,partial,scale);
        if(feedback!=null)feedback.camera(event,now,partial,scale);
        if(unlimitedVoid!=null)VoidFeedback.camera(event,unlimitedVoid,now,partial,scale);
        for(FurnaceCast cast:FURNACES.values())cast.camera(event,now,partial,scale);
        for(LimitlessCast cast:TECHNIQUES.values())cast.camera(event,now,partial,scale);
        ClientFistEffects.camera(event,now,partial,scale);
        ClientSukunaSlashes.camera(event,now,partial,scale);
        float blast=UnlimitedPurpleEffects.shake(partial)*scale,blastTime=(now+partial)/20f;
        event.setPitch(event.getPitch()+(float)Math.sin(blastTime*73)*blast);
        event.setYaw(event.getYaw()+(float)Math.sin(blastTime*51+1.3)*blast*.55f);
        event.setRoll(event.getRoll()+(float)Math.sin(blastTime*63+2.1)*blast*.62f);
        if(shrine!=null){
            float distance=(float)event.getCamera().getPosition().distanceTo(shrine.center);
            float range=Math.clamp(1-distance/(shrine.radius+8),0,1),age=shrine.age(now,partial);
            float pulse=(float)Math.exp(-Math.pow((age-MalevolentShrine.WINDUP)/.30,2));
            float amplitude=pulse*range*.38f*shrine.visibility(now,partial)*scale;
            event.setPitch(event.getPitch()+(float)Math.sin(age*67)*amplitude);
            event.setRoll(event.getRoll()+(float)Math.sin(age*51)*amplitude*.65f);
        }
        // Clamp this mod's combined contribution during clashes without overwriting another camera effect.
        event.setPitch(basePitch+Math.clamp(event.getPitch()-basePitch,-2.0f*scale,2.0f*scale));
        event.setYaw(baseYaw+Math.clamp(event.getYaw()-baseYaw,-1.15f*scale,1.15f*scale));
        event.setRoll(baseRoll+Math.clamp(event.getRoll()-baseRoll,-1.20f*scale,1.20f*scale));
    }
}
