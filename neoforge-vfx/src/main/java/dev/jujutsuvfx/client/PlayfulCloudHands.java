package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.PlayfulCloudMotion;
import dev.jujutsuvfx.core.PlayfulCloudMotion.Pose;
import dev.jujutsuvfx.item.PlayfulCloudItem;
import dev.jujutsuvfx.network.PlayfulCloudState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Vector3f;

/** Skin-anchored articulated weapon and continuous first/third-person poses. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class PlayfulCloudHands {
    private static final Map<LivingEntity,Motion> MOTIONS=new WeakHashMap<>();
    private static final ArrayList<Impact> IMPACTS=new ArrayList<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static int firstPersonDepth;
    private static final class Motion {
        final PlayfulCloudMotion.Combo combo=new PlayfulCloudMotion.Combo();
        int step=0;
        float start=-1000;
        float cancelled=-1000;
        Pose previous=PlayfulCloudMotion.IDLE;
        Pose lastRendered=PlayfulCloudMotion.IDLE;
        float lastYaw,lastPitch,lagYaw,lagPitch;
        int lastLagTick=-1;
    }
    private record Impact(Vec3 position,Vec3 direction,long born,float power,int step) {}
    private PlayfulCloudHands() {}
    public static void install() {
        PlayfulCloudItem.setClientActions(new PlayfulCloudItem.ClientActions() {
            @Override public void swing(LivingEntity actor) {
                if(actor!=Minecraft.getInstance().player)return;
                Motion m=motion(actor);
                if(m.combo.begin(actor.tickCount))start(actor,m,m.combo.step(),false);
            }
            @Override public void release(LivingEntity actor,int charge) {
                if(actor!=Minecraft.getInstance().player)return;
                Motion m=motion(actor);m.combo.heavy(actor.tickCount);start(actor,m,3,true);
            }
            @Override public void cancel(LivingEntity actor) {
                if(actor!=Minecraft.getInstance().player)return;
                Motion m=motion(actor);m.previous=m.lastRendered;m.cancelled=actor.tickCount;
                m.start=-1000;
            }
        });
    }
    private static void ensureWorld() {
        var level=Minecraft.getInstance().level;
        if(level!=world){MOTIONS.clear();IMPACTS.clear();world=level;}
    }
    private static Motion motion(LivingEntity actor) { ensureWorld();return MOTIONS.computeIfAbsent(actor,k->new Motion()); }
    private static void start(LivingEntity actor,Motion m,int step,boolean heavy) {
        m.previous=heavy?m.lastRendered:sample(actor,m,0);
        m.start=actor.tickCount;m.step=step;m.cancelled=-1000;
    }
    public static void receive(PlayfulCloudState packet) {
        ensureWorld();if(world==null||packet.step()>3||packet.event()>2||!Float.isFinite(packet.power()))return;
        if(packet.event()==PlayfulCloudState.HIT) {
            if(!Double.isFinite(packet.point().lengthSqr())||!Double.isFinite(packet.direction().lengthSqr()))return;
            while(IMPACTS.size()>=32)IMPACTS.remove(0);
            IMPACTS.add(new Impact(packet.point(),packet.direction(),ClientEffectClock.ticks(),packet.power(),packet.step()));
            return;
        }
        if(!(world.getEntity(packet.actor()) instanceof LivingEntity actor))return;
        Motion m=motion(actor);
        // Acknowledgement corrects the combo, never restarts the local animation after network latency.
        if(actor==Minecraft.getInstance().player&&actor.tickCount-m.start<PlayfulCloudMotion.duration(m.step)) {
            m.step=packet.step();return;
        }
        start(actor,m,packet.step(),packet.event()==PlayfulCloudState.HEAVY);
    }
    private static float charge(LivingEntity actor,float partial) {
        return actor.isUsingItem()&&actor.getUseItem().getItem() instanceof PlayfulCloudItem ? actor.getTicksUsingItem()+partial:0;
    }
    private static Pose sample(LivingEntity actor,Motion m,float partial) {
        float charging=charge(actor,partial);
        if(charging>0&&actor.tickCount-m.start>=1)return m.lastRendered=PlayfulCloudMotion.charge(charging);
        float cancelledAge=actor.tickCount+partial-m.cancelled;
        if(cancelledAge>=0&&cancelledAge<4)return m.lastRendered=m.previous.blend(PlayfulCloudMotion.IDLE,PlayfulCloudMotion.smooth(cancelledAge/4));
        float age=actor.tickCount+partial-m.start;
        Pose sampled=PlayfulCloudMotion.sample(m.step,age);
        if(age>=0&&age<1.5f)return m.lastRendered=m.previous.blend(sampled,PlayfulCloudMotion.smooth(age/1.5f));
        return m.lastRendered=sampled;
    }
    public static boolean pose(LivingEntity entity,HumanoidModel<?> model,float ageInTicks) {
        if(firstPersonDepth>0||!(entity instanceof AbstractClientPlayer actor)
                ||!(actor.getMainHandItem().getItem() instanceof PlayfulCloudItem)||actor.isSpectator())return false;
        Motion m=motion(actor);float partial=Mth.clamp(ageInTicks-actor.tickCount,0,1);
        Pose p=sample(actor,m,partial);float side=actor.getMainArm()==HumanoidArm.RIGHT?1:-1;
        float activity=actor.tickCount+partial-m.start<PlayfulCloudMotion.duration(m.step)?1:0;
        if(charge(actor,partial)>0)activity=PlayfulCloudMotion.smooth(charge(actor,partial)/8);
        model.body.yRot=p.body()*side*Mth.DEG_TO_RAD;
        ModelPart arm=side>0?model.rightArm:model.leftArm,other=side>0?model.leftArm:model.rightArm;
        arm.x=-side*Mth.cos(model.body.yRot)*5;
        arm.z=side*Mth.sin(model.body.yRot)*5;
        other.x=side*Mth.cos(model.body.yRot)*5;
        other.z=-side*Mth.sin(model.body.yRot)*5;
        arm.xRot=(p.pitch()-5)*Mth.DEG_TO_RAD+model.head.xRot*.45f;
        arm.yRot=p.yaw()*side*Mth.DEG_TO_RAD+model.body.yRot;
        arm.zRot=p.roll()*side*Mth.DEG_TO_RAD;
        if(actor.getOffhandItem().isEmpty()) {
            other.xRot=Mth.lerp(.7f+.3f*activity,other.xRot,(-28-35*activity)*Mth.DEG_TO_RAD+model.head.xRot*.15f);
            other.yRot=side*(.17f+.15f*activity)+model.body.yRot;
            other.zRot=side*(.13f+.22f*activity);
        }
        // A small planted stance; retain vanilla walking, swimming, sneaking and leg pivots.
        if(!actor.isFallFlying()&&!actor.isSwimming()) {
            model.rightLeg.yRot+=model.body.yRot*.16f;model.leftLeg.yRot+=model.body.yRot*.16f;
        }
        return true;
    }
    public static void thirdPerson(LivingEntity entity,HumanoidArm arm,PoseStack poses,MultiBufferSource buffers,int light) {
        if(!(entity instanceof AbstractClientPlayer actor)||actor.isInvisible())return;
        if(!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(actor) instanceof PlayerRenderer renderer))return;
        float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),side=arm==HumanoidArm.RIGHT?1:-1;
        Motion m=motion(actor);Pose p=sample(actor,m,partial);float age=actor.tickCount+partial-m.start;
        poses.pushPose();
        try {
            ModelPart part=arm==HumanoidArm.RIGHT?renderer.getModel().rightArm:renderer.getModel().leftArm;
            part.translateAndRotate(poses);
            poses.translate(-side*.0625,.625,0);
            poses.mulPose(Axis.XP.rotationDegrees(-110));poses.mulPose(Axis.ZP.rotationDegrees(side*-8));
            PlayfulCloudRenderer.draw(poses,buffers.getBuffer(PlayfulCloudRenderer.SOLID),p,side,light,OverlayTexture.NO_OVERLAY);
            if(charge(actor,partial)==0)PlayfulCloudRenderer.trail(poses,buffers.getBuffer(PlayfulCloudRenderer.TRAIL),p,m.step,age,side);
        }finally{poses.popPose();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void firstPerson(RenderHandEvent event) {
        Minecraft mc=Minecraft.getInstance();AbstractClientPlayer actor=mc.player;
        if(actor==null||!actor.isAlive()||actor.isInvisible()||actor.isSpectator())return;
        if(!(actor.getMainHandItem().getItem() instanceof PlayfulCloudItem))return;
        if(event.getHand()==InteractionHand.OFF_HAND) {
            if(actor.getOffhandItem().isEmpty())event.setCanceled(true);
            return;
        }
        if(!(event.getItemStack().getItem() instanceof PlayfulCloudItem))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof PlayerRenderer renderer))return;
        event.setCanceled(true);Motion m=motion(actor);float partial=event.getPartialTick();
        Pose p=sample(actor,m,partial);float side=actor.getMainArm()==HumanoidArm.RIGHT?1:-1;
        float age=actor.tickCount+partial-m.start;
        if(m.lastLagTick!=actor.tickCount) {
            if(m.lastLagTick<0){m.lastYaw=actor.getYRot();m.lastPitch=actor.getXRot();}
            m.lagYaw=Mth.lerp(.38f,m.lagYaw,Mth.clamp(Mth.wrapDegrees(actor.getYRot()-m.lastYaw),-18,18));
            m.lagPitch=Mth.lerp(.38f,m.lagPitch,Mth.clamp(actor.getXRot()-m.lastPitch,-15,15));
            m.lastYaw=actor.getYRot();m.lastPitch=actor.getXRot();m.lastLagTick=actor.tickCount;
        }
        PoseStack poses=event.getPoseStack();poses.pushPose();firstPersonDepth++;
        try {
            float moving=(float)Math.min(1,actor.getDeltaMovement().horizontalDistance()*5);
            float sway=Mth.sin((actor.tickCount+partial)*.30f)*.012f*moving;
            poses.translate(side*p.x()-m.lagYaw*.0018,p.y()+sway-m.lagPitch*.0015,p.z());
            poses.mulPose(Axis.YP.rotationDegrees(side*p.yaw()-m.lagYaw*.22f));
            poses.mulPose(Axis.XP.rotationDegrees(p.pitch()));poses.mulPose(Axis.ZP.rotationDegrees(side*p.roll()));
            poses.pushPose();
            FirstPersonArmSpace.apply(poses,actor.getMainArm());
            FirstPersonSkin.draw(renderer,poses,event.getMultiBufferSource(),event.getPackedLight(),actor,actor.getMainArm());
            poses.popPose();
            poses.mulPose(Axis.XP.rotationDegrees(90));poses.mulPose(Axis.ZP.rotationDegrees(-8*side));
            boolean attached=FirstPersonSkin.attachment(actor,PlayfulCloudRenderer.SURFACE,vertices -> {
                PlayfulCloudRenderer.draw(poses,vertices,p,side,event.getPackedLight(),OverlayTexture.NO_OVERLAY);
                if(charge(actor,partial)==0)PlayfulCloudRenderer.trail(poses,vertices,p,m.step,age,side);
            });
            if(!attached) {
                PlayfulCloudRenderer.draw(poses,event.getMultiBufferSource().getBuffer(PlayfulCloudRenderer.SOLID),p,side,event.getPackedLight(),OverlayTexture.NO_OVERLAY);
                if(charge(actor,partial)==0)PlayfulCloudRenderer.trail(poses,event.getMultiBufferSource().getBuffer(PlayfulCloudRenderer.TRAIL),p,m.step,age,side);
            }
        }finally{firstPersonDepth--;poses.popPose();}
        if(actor.getOffhandItem().isEmpty()) {
            poses.pushPose();firstPersonDepth++;
            try {
                float weight=charge(actor,partial)>0?PlayfulCloudMotion.smooth(charge(actor,partial)/8)
                        :PlayfulCloudMotion.trail(m.step,age)*.55f;
                poses.translate(-side*(.47-.18*weight),-.61+.20*weight,-.67-.05*weight);
                poses.mulPose(Axis.YP.rotationDegrees(-side*(8+20*weight)));
                poses.mulPose(Axis.XP.rotationDegrees(-63+15*weight));poses.mulPose(Axis.ZP.rotationDegrees(-side*(10+27*weight)));
                FirstPersonArmSpace.apply(poses,actor.getMainArm().getOpposite());
                FirstPersonSkin.draw(renderer,poses,event.getMultiBufferSource(),event.getPackedLight(),actor,actor.getMainArm().getOpposite());
            }finally{firstPersonDepth--;poses.popPose();}
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        ensureWorld();if(world==null||IMPACTS.isEmpty())return;
        long now=ClientEffectClock.ticks();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        IMPACTS.removeIf(i->now-i.born>9||now<i.born);
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        var vertices=buffers.getBuffer(PlayfulCloudRenderer.TRAIL);
        PoseStack poses=event.getPoseStack();Vec3 camera=event.getCamera().getPosition();
        for(Impact hit:IMPACTS) {
            float age=now-hit.born+partial,t=Math.min(1,age/9),fade=(1-t)*(1-t),size=(.2f+t*1.9f)*(hit.step==3?1.35f:1)*hit.power;
            Vec3 forward=hit.direction.normalize(),right=forward.cross(new Vec3(0,1,0)).normalize();
            if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
            Vec3 up=right.cross(forward).normalize(),origin=hit.position.subtract(camera).add(forward.scale(t*.7));
            for(int i=0;i<22;i++) {
                double a=i*Math.PI*2/22,b=(i+1)*Math.PI*2/22;
                float ripple=1+.09f*Mth.sin(i*2.37f+hit.step);
                Vec3 r=right.scale(Math.cos(a)).add(up.scale(Math.sin(a))),r2=right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
                Vector3f p0=point(poses,origin.add(r.scale(size*ripple))),p1=point(poses,origin.add(r.scale(size*ripple+.035f*fade)));
                Vector3f p2=point(poses,origin.add(r2.scale(size+.035f*fade))),p3=point(poses,origin.add(r2.scale(size)));
                PlayfulCloudRenderer.ribbon(vertices,p0,p1,p2,p3,fade*.72f,0xFFE8DE);
            }
            for(int i=0;i<8;i++) {
                double angle=i*2.399+hit.step;Vec3 radial=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                Vec3 transverse=forward.cross(radial).scale(.025f*fade);
                Vec3 a=origin.add(radial.scale(size*.35)),b=origin.add(radial.scale(size*(1.20+.18*(i%3)))).add(forward.scale(t*.4));
                PlayfulCloudRenderer.ribbon(vertices,point(poses,a.subtract(transverse)),point(poses,a.add(transverse)),
                        point(poses,b),point(poses,b),fade*.7f,i%3==0?0xC73948:0xFFF8EE);
            }
        }
        buffers.endBatch(PlayfulCloudRenderer.TRAIL);
    }
    private static Vector3f point(PoseStack poses,Vec3 v) { return poses.last().pose().transformPosition(new Vector3f((float)v.x,(float)v.y,(float)v.z)); }
}
