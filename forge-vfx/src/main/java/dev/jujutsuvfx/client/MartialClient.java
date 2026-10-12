package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.MartialArts;
import dev.jujutsuvfx.core.MartialPose;
import dev.jujutsuvfx.item.FistCombo;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.MartialInput;
import dev.jujutsuvfx.network.MartialState;
import dev.jujutsuvfx.network.MartialHit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import dev.jujutsuvfx.platform.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;

/** Predict input locally; replay server action clocks on other players, never their vanilla arm swing. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class MartialClient {
    private static final Map<AbstractClientPlayer,Motion> MOTIONS=new WeakHashMap<>();
    private record Saved(ModelPart[] parts,net.minecraft.client.model.geom.PartPose[] poses) {}
    private static final Map<HumanoidModel<?>,Saved> SAVED=new WeakHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static AbstractClientPlayer local;
    private static boolean mouseDown;
    private static int serial,firstPersonDepth;
    private static MartialPose handPose;
    private static float handWeight;
    private static final class Motion {
        final MartialArts.Sequence combo=new MartialArts.Sequence();
        final MartialArts.ImpactClock impact=new MartialArts.ImpactClock();
        boolean black;
        int renderedMove=-1,renderedAction=-1;
        float lastAge=-1,equipped,lastYaw,lastPitch,lagYaw,lagPitch;
        long lagTick=-1;
        MartialPose previous=MartialPose.GUARD,last=MartialPose.GUARD;
    }
    private MartialClient() {}
    private static void ensureWorld(){
        Minecraft mc=Minecraft.getInstance();
        if(world!=mc.level||local!=mc.player){MOTIONS.clear();world=mc.level;local=mc.player;mouseDown=false;serial=0;}
    }
    public static boolean owns(LivingEntity actor){return actor instanceof AbstractClientPlayer p&&p.isAlive()&&!p.isSpectator()&&VfxItems.isHeld(p.getMainHandItem());}
    private static Motion motion(AbstractClientPlayer actor){
        ensureWorld();return MOTIONS.computeIfAbsent(actor,k->{Motion m=new Motion();m.equipped=world==null?0:ClientEffectClock.ticks();return m;});
    }
    private static boolean available(){
        Minecraft mc=Minecraft.getInstance();
        return mc.player!=null&&mc.level!=null&&mc.screen==null&&!mc.isPaused()&&owns(mc.player)
                &&!mc.player.isPassenger()&&!mc.player.isFallFlying()&&!mc.player.isUsingItem()
                &&!ClientEntityStasis.localLocked()&&!CatCinematicCamera.active();
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!owns(mc.player)||!event.isAttack())return;
        event.setCanceled(true);event.setSwingHand(false);
    }
    /** Observe real edges, including a press and release between two 20 Hz game ticks. */
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event){
        if(Minecraft.getInstance().options.keyAttack.matchesMouse(event.getButton()))edge(event.getAction());
    }
    @SubscribeEvent public static void key(InputEvent.Key event){
        if(Minecraft.getInstance().options.keyAttack.matches(event.getKey(),event.getScanCode()))edge(event.getAction());
    }
    private static void edge(int action){
        if(!available())return;
        if(action==1)press();else if(action==0)release();
    }
    private static void release(){
        if(!mouseDown)return;
        Minecraft mc=Minecraft.getInstance();Motion m=motion(mc.player);int previous=m.combo.action();
        mouseDown=false;m.combo.release(serial,ClientEffectClock.ticks());
        if(previous!=m.combo.action())m.black=FistCombo.ready(mc.player.getMainHandItem());
        PacketDistributor.sendToServer(new MartialInput(serial,MartialInput.RELEASE));
    }
    private static void press(){
        if(mouseDown)return;
        Minecraft mc=Minecraft.getInstance();Motion m=motion(mc.player);mouseDown=true;
        int id=++serial;if(id<=0){serial=1;id=1;}
        long now=ClientEffectClock.ticks();
        if(m.combo.press(id,now,!mc.player.onGround()&&!mc.player.isInWaterOrBubble())) {
            if(!m.combo.busy(now))m.black=FistCombo.ready(mc.player.getMainHandItem());
            PacketDistributor.sendToServer(new MartialInput(id,MartialInput.PRESS));
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        ensureWorld();Minecraft mc=Minecraft.getInstance();
        if(!available()) {
            if(mouseDown&&mc.getConnection()!=null)PacketDistributor.sendToServer(new MartialInput(serial,MartialInput.CANCEL));
            mouseDown=false;
            if(mc.player!=null){Motion m=MOTIONS.get(mc.player);if(m!=null)m.combo.cancel();}
            return;
        }
        Motion m=motion(mc.player);int action=m.combo.action();boolean pressed=mc.options.keyAttack.isDown();
        if(pressed&&!mouseDown)press();
        else if(!pressed&&mouseDown)release();
        m.combo.tick(ClientEffectClock.ticks());
        if(action!=m.combo.action())m.black=FistCombo.ready(mc.player.getMainHandItem());
    }
    public static void receive(MartialState packet){
        ensureWorld();if(world==null||packet.move()>MartialArts.LAND||packet.next()>3||packet.age()<0||packet.age()>1000
                ||packet.held()<0||packet.held()>1000||packet.sequence()<0||packet.action()<0||packet.action()>packet.sequence())return;
        if(!(world.getEntity(packet.actor()) instanceof AbstractClientPlayer actor)||!actor.getUUID().equals(packet.identity()))return;
        Motion m=motion(actor);boolean own=actor==local;
        if((packet.flags()&MartialState.REJECT)!=0&&own){m.combo.cancel();return;}
        m.combo.adopt(packet.sequence(),packet.action(),packet.move(),packet.age(),packet.next(),packet.held(),
                (packet.flags()&MartialState.HOLD)!=0,ClientEffectClock.ticks(),own);
        if(packet.action()==m.combo.action())m.black=(packet.flags()&MartialState.BLACK)!=0;
    }
    public static void receive(MartialHit packet){
        ensureWorld();if(world==null||!MartialArts.contactAt(packet.move(),packet.age()))return;
        if(!(world.getEntity(packet.actor()) instanceof AbstractClientPlayer actor)||!actor.getUUID().equals(packet.identity())||!owns(actor))return;
        Motion m=motion(actor);long now=ClientEffectClock.ticks();
        if(m.combo.action()!=packet.action()||m.combo.move()!=packet.move()||!m.combo.busy(now))return;
        float age=Math.max(m.combo.age(now),Math.max(m.lastAge,packet.age()));
        m.impact.hit(packet.action(),packet.age(),age,now,MartialArts.hitPause(packet.move(),packet.black()));
    }
    private static float visualAge(Motion m,float partial){
        float now=ClientEffectClock.ticks()+partial;
        return m.impact.sample(m.combo.action(),m.combo.age(ClientEffectClock.ticks())+partial,now);
    }
    static float aura(AbstractClientPlayer actor,HumanoidArm arm,float partial){
        Motion m=motion(actor);return MartialArts.aura(m.combo.move(),visualAge(m,partial),arm==actor.getMainArm());
    }
    public static boolean empowered(AbstractClientPlayer player){
        Motion m=motion(player);return m.combo.busy(ClientEffectClock.ticks())?m.black:FistCombo.ready(player.getMainHandItem());
    }
    private static MartialPose sample(AbstractClientPlayer actor,float partial){
        Motion m=motion(actor);long now=ClientEffectClock.ticks();int move=m.combo.move();float age=visualAge(m,partial);
        MartialPose p=m.combo.charging()&&!m.combo.busy(now)
                ?MartialPose.charge(m.combo.next(),!actor.onGround()&&!actor.isInWaterOrBubble(),m.combo.held(now)+partial)
                :MartialPose.sample(move,age);
        if(move!=m.renderedMove||m.renderedAction!=m.combo.action()){
            m.previous=m.last;m.renderedMove=move;m.renderedAction=m.combo.action();
        }
        if(move!=MartialArts.IDLE&&age<.85f)p=m.previous.blend(p,MartialArts.smooth(age/.85f));
        m.lastAge=age;return m.last=p;
    }
    public static void pose(LivingEntity entity,HumanoidModel<?> model,float ageInTicks){
        FlexibleLimbRenderer.clear(model);
        if(!owns(entity)||!(entity instanceof AbstractClientPlayer actor))return;
        boolean mirrored=actor.getMainArm()==HumanoidArm.LEFT;
        if(firstPersonDepth>0&&actor==local){
            if(handPose!=null){
                applyBend(model,mirrored?handPose.leftArm():handPose.rightArm(),mirrored?handPose.rightArm():handPose.leftArm(),handWeight);
            }
            return;
        }
        if(actor.isSleeping()||actor.isFallFlying()||actor.isSwimming()||actor.isPassenger())return;
        save(model);
        float partial=Mth.clamp(ageInTicks-actor.tickCount,0,1);MartialPose p=sample(actor,partial);
        float sign=mirrored?-1:1;
        var rig=p.rig(motion(actor).combo.move(),actor.onGround());
        place(model.body,rig.body(),sign,1);
        model.head.x=rig.head().x()*sign;model.head.y=rig.head().y();model.head.z=rig.head().z();
        model.head.yRot+=rig.head().yaw()*sign*Mth.DEG_TO_RAD;
        model.head.zRot+=rig.head().roll()*sign*Mth.DEG_TO_RAD;model.hat.copyFrom(model.head);
        limb(model.rightArm,mirrored?rig.leftArm():rig.rightArm(),sign,1,false);
        limb(model.leftArm,mirrored?rig.rightArm():rig.leftArm(),sign,1,false);
        model.rightArm.xRot+=model.head.xRot*.15f;model.leftArm.xRot+=model.head.xRot*.15f;
        limb(model.rightLeg,mirrored?rig.leftLeg():rig.rightLeg(),sign,p.activity(),true);
        limb(model.leftLeg,mirrored?rig.rightLeg():rig.leftLeg(),sign,p.activity(),true);
        if(model instanceof PlayerModel<?> skin){skin.rightSleeve.copyFrom(model.rightArm);skin.leftSleeve.copyFrom(model.leftArm);
            skin.rightPants.copyFrom(model.rightLeg);skin.leftPants.copyFrom(model.leftLeg);skin.jacket.copyFrom(model.body);}
    }
    /** Humanoid models are shared between players. Restore all authored transforms before vanilla setup. */
    public static void restore(HumanoidModel<?> model){
        Saved saved=SAVED.remove(model);if(saved==null)return;
        for(int i=0;i<saved.parts.length;i++)saved.parts[i].loadPose(saved.poses[i]);
    }
    private static void save(HumanoidModel<?> model){
        ModelPart[] parts=model instanceof PlayerModel<?> p
                ?new ModelPart[]{p.head,p.hat,p.body,p.rightArm,p.leftArm,p.rightLeg,p.leftLeg,p.rightSleeve,p.leftSleeve,p.rightPants,p.leftPants,p.jacket}
                :new ModelPart[]{model.head,model.hat,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        var poses=new net.minecraft.client.model.geom.PartPose[parts.length];
        for(int i=0;i<parts.length;i++)poses[i]=parts[i].storePose();SAVED.put(model,new Saved(parts,poses));
    }
    private static void place(ModelPart part,MartialPose.Part p,float mirror,float weight){
        part.x=Mth.lerp(weight,part.x,p.x()*mirror);part.y=Mth.lerp(weight,part.y,p.y());part.z=Mth.lerp(weight,part.z,p.z());
        part.xRot=Mth.lerp(weight,part.xRot,p.pitch()*Mth.DEG_TO_RAD);
        part.yRot=Mth.lerp(weight,part.yRot,p.yaw()*mirror*Mth.DEG_TO_RAD);
        part.zRot=Mth.lerp(weight,part.zRot,p.roll()*mirror*Mth.DEG_TO_RAD);
    }
    private static void limb(ModelPart part,MartialPose.Part p,float mirror,float weight,boolean leg){
        place(part,p,mirror,weight);FlexibleLimbRenderer.bend(part,p.bend()*weight,leg);
    }
    private static void applyBend(HumanoidModel<?> model,MartialPose.Limb right,MartialPose.Limb left,float weight){
        FlexibleLimbRenderer.bend(model.rightArm,right.bend()*weight,false);FlexibleLimbRenderer.bend(model.leftArm,left.bend()*weight,false);
        if(model instanceof PlayerModel<?> p){FlexibleLimbRenderer.copy(model.rightArm,p.rightSleeve);FlexibleLimbRenderer.copy(model.leftArm,p.leftSleeve);}
    }
    /** Called before the legacy fist handler. Both arms use the existing shader-safe skin pass. */
    public static boolean hands(RenderHandEvent event){
        Minecraft mc=Minecraft.getInstance();AbstractClientPlayer actor=mc.player;
        if(actor==null||!owns(actor)||actor.isInvisible())return false;
        if(event.getHand()==InteractionHand.OFF_HAND){if(actor.getOffhandItem().isEmpty())event.setCanceled(true);return true;}
        if(!VfxItems.isHeld(event.getItemStack()))return false;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof PlayerRenderer renderer))return false;
        event.setCanceled(true);Motion m=motion(actor);float partial=event.getPartialTick();MartialPose p=sample(actor,partial);
        long now=ClientEffectClock.ticks();
        if(m.lagTick!=now){
            if(m.lagTick<0){m.lastYaw=actor.getYRot();m.lastPitch=actor.getXRot();}
            m.lagYaw=Mth.lerp(.32f,m.lagYaw,Mth.clamp(Mth.wrapDegrees(actor.getYRot()-m.lastYaw),-22,22));
            m.lagPitch=Mth.lerp(.32f,m.lagPitch,Mth.clamp(actor.getXRot()-m.lastPitch,-18,18));
            m.lastYaw=actor.getYRot();m.lastPitch=actor.getXRot();m.lagTick=now;
        }
        handWeight=MartialArts.smooth((now+partial-m.equipped)/5);handPose=p;
        firstPersonDepth++;
        try {
            drawHand(event,renderer,actor,actor.getMainArm(),p.rightArm(),m,false);
            if(actor.getOffhandItem().isEmpty())drawHand(event,renderer,actor,actor.getMainArm().getOpposite(),p.leftArm(),m,true);
        }finally{firstPersonDepth--;handPose=null;}
        return true;
    }
    private static void drawHand(RenderHandEvent event,PlayerRenderer renderer,AbstractClientPlayer actor,HumanoidArm arm,
                                 MartialPose.Limb limb,Motion m,boolean support){
        float side=arm==HumanoidArm.RIGHT?1:-1,primary=actor.getMainArm()==HumanoidArm.RIGHT?1:-1;
        float reach=Mth.clamp((55+limb.bend())/50,0,1),lift=Mth.clamp((-limb.pitch()-95)/60,0,1);
        float sway=Mth.sin((ClientEffectClock.ticks()+event.getPartialTick())*.35f)*(float)Math.min(.018,actor.getDeltaMovement().horizontalDistance()*.06);
        PoseStack poses=event.getPoseStack();Matrix4f parent=new Matrix4f(poses.last().pose());poses.pushPose();
        try {
            poses.translate(side*(.40-.18*reach)-m.lagYaw*.002,-.39+.12*reach+.32*lift+sway-m.lagPitch*.0015,-.63-.34*reach);
            poses.mulPose(Axis.YP.rotationDegrees(side*12+limb.yaw()*primary*.32f-m.lagYaw*.2f));
            poses.mulPose(Axis.XP.rotationDegrees(-72+(limb.pitch()+38)*.38f));
            poses.mulPose(Axis.ZP.rotationDegrees(side*7+limb.roll()*primary*.40f));
            FirstPersonArmSpace.apply(poses,arm);
            FirstPersonArmSpace.fromEmpty(poses,parent,arm,event.getEquipProgress(),0,handWeight,support);
            FirstPersonSkin.draw(renderer,poses,event.getMultiBufferSource(),event.getPackedLight(),actor,arm);
            var shape=CursedFistHands.palmPose(renderer.getModel(),poses,arm);
            FistAuraRenderer.draw(poses,(ClientEffectClock.ticks()%24000L+event.getPartialTick())*.05f,
                    .86f+.36f*reach,true,actor,arm,event.getPartialTick(),shape);
        }finally{poses.popPose();}
    }
    @SubscribeEvent public static void trails(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        ensureWorld();if(world==null)return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        var out=buffers.getBuffer(PlayfulCloudRenderer.TRAIL);PoseStack poses=event.getPoseStack();
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        int count=0;
        for(var entry:MOTIONS.entrySet()){
            AbstractClientPlayer actor=entry.getKey();Motion m=entry.getValue();int move=m.combo.move();
            if(!owns(actor)||actor.isInvisible()||actor.position().distanceToSqr(camera)>48*48||move<MartialArts.PALM||move==MartialArts.DIVE)continue;
            float age=visualAge(m,partial);
            if(age<1||age>Math.min(19,MartialArts.duration(move)-3))continue;
            if(++count>8)break;
            float fade=MartialArts.smooth(age/2)*(1-MartialArts.smooth((age-10)/9));
            Vec3 center=actor.getPosition(partial).subtract(camera).add(0,move==MartialArts.LAND?.08:actor.getBbHeight()*.57,0);
            Vec3 forward=new Vec3(actor.getLookAngle().x,0,actor.getLookAngle().z).normalize(),right=forward.cross(new Vec3(0,1,0));
            int color=m.black?0xED2847:0x27D6E9;
            int segments=move==MartialArts.LAND?28:18;
            for(int i=0;i<segments;i++){
                float t=i/(float)segments,u=(i+1)/(float)segments;
                Vec3 a,b,width;double radius;
                if(move==MartialArts.LAND){
                    radius=.35+Math.min(1,age/10)*2.8;
                    a=center.add(Math.cos(t*Math.PI*2)*radius,0,Math.sin(t*Math.PI*2)*radius);
                    b=center.add(Math.cos(u*Math.PI*2)*radius,0,Math.sin(u*Math.PI*2)*radius);width=new Vec3(0,.028*fade,0);
                }else if(move==MartialArts.SPIN){
                    double turn=MartialPose.sample(move,age).turn()*Math.PI/180;
                    a=center.add(forward.scale(Math.cos(turn-t*1.65)*1.35)).add(right.scale(Math.sin(turn-t*1.65)*1.35));
                    b=center.add(forward.scale(Math.cos(turn-u*1.65)*1.35)).add(right.scale(Math.sin(turn-u*1.65)*1.35));width=new Vec3(0,.04*(1-t)*fade,0);
                }else if(move==MartialArts.UPPERCUT){
                    double angle=-1.1+Math.min(age/9,1)*2.4;
                    a=center.add(forward.scale(Math.cos(angle-t)*.9)).add(0,Math.sin(angle-t)*1.05,0);
                    b=center.add(forward.scale(Math.cos(angle-u)*.9)).add(0,Math.sin(angle-u)*1.05,0);width=right.scale(.032*(1-t)*fade);
                }else {
                    float phase=move==MartialArts.BARRAGE?(age%3)/3:Math.min(1,age/7);
                    a=center.add(forward.scale(.25+phase*1.3-t*.65)).add(right.scale(.10*Math.sin(t*5+age)));
                    b=center.add(forward.scale(.25+phase*1.3-u*.65)).add(right.scale(.10*Math.sin(u*5+age)));width=new Vec3(0,.035*(1-t)*fade,0);
                }
                PlayfulCloudRenderer.ribbon(out,point(poses,a.subtract(width)),point(poses,a.add(width)),point(poses,b.add(width)),point(poses,b.subtract(width)),fade*(1-t)*.58f,color);
                width=width.scale(.26);
                PlayfulCloudRenderer.ribbon(out,point(poses,a.subtract(width)),point(poses,a.add(width)),point(poses,b.add(width)),point(poses,b.subtract(width)),fade*(1-t)*.85f,0xE6FFFF);
            }
        }
        buffers.endBatch(PlayfulCloudRenderer.TRAIL);
    }
    private static Vector3f point(PoseStack poses,Vec3 v){return poses.last().pose().transformPosition(new Vector3f((float)v.x,(float)v.y,(float)v.z));}
}
