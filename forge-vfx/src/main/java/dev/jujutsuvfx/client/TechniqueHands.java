package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.SkillGesture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import java.util.*;
import org.joml.Matrix4f;

import static dev.jujutsuvfx.network.SkillGesture.*;

/** Shared first/third-person gestures for skills with synchronized player arm animations. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class TechniqueHands {
    private static final Map<UUID,Motion> MOTIONS=new HashMap<>();
    private static ClientLevel level;private static AbstractClientPlayer localPlayer;private static int firstPersonDepth;
    private static final class Motion {
        final int session,kind;InteractionHand hand;final double born;final boolean guided,local;
        final ItemStack bound;final int autoRelease;
        double release=Double.POSITIVE_INFINITY;long received;int seenAge=-1;
        Motion(int session,int kind,InteractionHand hand,double born,boolean guided,boolean local,ItemStack bound,boolean authoritative) {
            this.session=session;this.kind=kind;this.hand=hand;this.born=born;this.guided=guided;this.local=local;this.bound=bound;
            autoRelease=kind==BLUE?(guided||!local?200:14):kind==FURNACE?(authoritative?80:32):(kind==UZUMAKI||kind==LOVE_BEAM)?72000:kind==SPEECH?8:kind==YUTA?40:26;received=ClientEffectClock.ticks();
        }
        double age(float partial){return Math.max(0,ClientEffectClock.ticks()+partial-born);}
    }
    private record Pose(int kind,InteractionHand hand,float age,float release,float weight) {
        boolean twoHands(){return kind==FURNACE||kind==SHRINE||kind==YUTA||kind==UZUMAKI||kind==LOVE_BEAM;}
    }
    private TechniqueHands() {}
    private static void ensureWorld() {
        Minecraft mc=Minecraft.getInstance();if(level==mc.level&&localPlayer==mc.player)return;
        MOTIONS.clear();level=mc.level;localPlayer=mc.player;
    }
    static void clear(){MOTIONS.clear();level=null;localPlayer=null;}
    static void begin(int kind,int session,InteractionHand hand,boolean guided) {
        ensureWorld();Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(hand==null)hand=mc.player.isUsingItem()?mc.player.getUsedItemHand():InteractionHand.MAIN_HAND;
        ItemStack stack=mc.player.getItemInHand(hand);
        ItemStack binding=matches(stack,kind)?stack.copy():ItemStack.EMPTY;
        boolean authoritative=mc.getConnection()!=null&&dev.jujutsuvfx.platform.PacketDistributor.hasChannel(mc.getConnection(),SkillGesture.TYPE);
        Motion motion=new Motion(session,kind,hand,ClientEffectClock.ticks(),guided,true,binding,authoritative);
        MOTIONS.put(mc.player.getUUID(),motion);
    }
    static void release(int kind,int session) {
        ensureWorld();if(localPlayer==null)return;Motion motion=MOTIONS.get(localPlayer.getUUID());
        if(motion!=null&&motion.kind==kind&&motion.session==session)motion.release=Math.min(motion.release,ClientEffectClock.ticks());
    }
    static void cancel(int kind,int session) {
        ensureWorld();if(localPlayer==null)return;Motion motion=MOTIONS.get(localPlayer.getUUID());
        if(motion!=null&&motion.kind==kind&&motion.session==session)MOTIONS.remove(localPlayer.getUUID());
    }
    static void interrupt(){ensureWorld();if(localPlayer!=null)MOTIONS.remove(localPlayer.getUUID());}
    public static void receive(SkillGesture packet) {
        ensureWorld();Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||packet.kind()<BLUE||packet.kind()>LOVE_BEAM||packet.hand()>1||packet.age()<0||packet.age()>72000
                ||packet.release()<-2||packet.release()>packet.age())return;
        if(!(mc.level.getEntity(packet.actor()) instanceof AbstractClientPlayer player)||!player.getUUID().equals(packet.owner()))return;
        Motion motion=MOTIONS.get(packet.owner());
        boolean same=motion!=null&&motion.kind==packet.kind()&&motion.session==packet.session();
        // Predicted skills own their starts; speech/Yuta start after server acceptance.
        if(player==mc.player&&!same&&packet.kind()<SPEECH)return;
        if(packet.release()==-2){if(same)MOTIONS.remove(packet.owner());return;}
        if(same&&packet.age()<motion.seenAge)return;
        if(!same) {
            if(packet.release()>=0&&packet.age()>=packet.release()+RETURN_TICKS)return;
            if(MOTIONS.size()>=64)MOTIONS.remove(MOTIONS.keySet().iterator().next());
            motion=new Motion(packet.session(),packet.kind(),packet.hand()==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,
                    ClientEffectClock.ticks()-packet.age(),false,false,ItemStack.EMPTY,true);
            MOTIONS.put(packet.owner(),motion);
        }
        motion.seenAge=packet.age();motion.received=ClientEffectClock.ticks();
        if(!motion.local)motion.hand=packet.hand()==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        if(packet.release()>=0)motion.release=Math.min(motion.release,ClientEffectClock.ticks()-packet.age()+packet.release());
    }
    @SubscribeEvent public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        ensureWorld();Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        for(var iterator=MOTIONS.entrySet().iterator();iterator.hasNext();) {
            var entry=iterator.next();Motion motion=entry.getValue();
            var player=mc.level.getPlayerByUUID(entry.getKey());
            if(player==null||!player.isAlive()||player.isSpectator()){iterator.remove();continue;}
            if(motion.local&&!motion.bound.isEmpty()&&!ItemStack.isSameItemSameTags(motion.bound,player.getItemInHand(motion.hand))) {
                iterator.remove();continue;
            }
            double now=ClientEffectClock.ticks();
            if(motion.local&&motion.guided&&motion.release==Double.POSITIVE_INFINITY&&!player.isUsingItem())motion.release=now;
            if(now-motion.born>=motion.autoRelease)motion.release=Math.min(motion.release,motion.born+motion.autoRelease);
            if(now>=motion.release+RETURN_TICKS||!motion.local&&now-motion.received>80)iterator.remove();
        }
    }
    private static Pose pose(AbstractClientPlayer player,float partial) {
        ensureWorld();if(!player.isAlive()||player.isInvisible()||player.isSpectator())return null;
        Motion motion=MOTIONS.get(player.getUUID());if(motion==null)return null;
        float age=(float)motion.age(partial),released=Double.isFinite(motion.release)
                ?(float)Math.max(0,ClientEffectClock.ticks()+partial-motion.release):-1;
        if(released>=RETURN_TICKS)return null;
        float weight=smooth(age/6)*(released<0?1:1-smooth(released/RETURN_TICKS));
        return new Pose(motion.kind,motion.hand,age,released,weight);
    }
    public static boolean ownsHand(InteractionHand hand,float partial) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;
        Pose pose=pose(mc.player,partial);return pose!=null&&(pose.hand==hand||pose.twoHands());
    }
    public static boolean renderingHand(){return firstPersonDepth>0;}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive()||mc.player.isInvisible()||mc.player.isSpectator())return;
        Pose pose=pose(mc.player,event.getPartialTick());
        if(pose!=null&&event.getHand()!=pose.hand){if(pose.twoHands()){event.setCanceled(true);return;}pose=null;}
        if(pose==null){
            boolean held=matches(event.getItemStack(),BLUE)||matches(event.getItemStack(),FURNACE)
                    ||matches(event.getItemStack(),VOID)||matches(event.getItemStack(),SHRINE)
                    ||matches(event.getItemStack(),LOVE_BEAM)||matches(event.getItemStack(),UZUMAKI)||matches(event.getItemStack(),YUTA)||matches(event.getItemStack(),SPEECH);
            boolean empty=event.getHand()==InteractionHand.MAIN_HAND&&event.getItemStack().isEmpty()&&ClientEvents.visualVoid()!=null;
            if(!held&&!empty)return;
            pose=new Pose(-1,event.getHand(),0,-1,0);
        }
        if(!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer))return;
        event.setCanceled(true);HumanoidArm arm=arm(mc.player,pose.hand);firstPersonDepth++;
        try {
            draw(event,renderer,mc.player,arm,pose,false);
            if(pose.twoHands())draw(event,renderer,mc.player,arm.getOpposite(),pose,true);
        }finally{firstPersonDepth--;}
    }
    private static void draw(RenderHandEvent event,PlayerRenderer renderer,AbstractClientPlayer player,HumanoidArm arm,Pose pose,boolean support) {
        PoseStack stack=event.getPoseStack();Matrix4f parent=new Matrix4f(stack.last().pose());stack.pushPose();
        try {
            float side=arm==HumanoidArm.RIGHT?1:-1,w=pose.weight;
            float x=support?.47f:.39f,y=support?-.79f:-.35f,z=-.75f,pitch=-74,yaw=10,roll=8,twist=0;
            float draw=smooth(pose.age/26),kick=pose.release<0?0:smooth(pose.release/2)*(1-smooth((pose.release-2)/6));
            if(pose.kind==BLUE){x=.28f;y=-.23f;z=-.98f-.10f*kick;pitch=-84;yaw=3;roll=5;twist=48;}
            else if(pose.kind==FURNACE){
                if(support){x=mix(.15f,.30f,draw);y=-.24f;z=mix(-.92f,-.38f,draw)+.10f*kick;pitch=-53;yaw=30;roll=38;twist=-28;}
                else{x=.43f;y=-.29f;z=-1.12f+.08f*kick;pitch=-84+5*kick;yaw=-3;roll=5;}
            }else if(pose.kind==VOID){x=.22f;y=-.20f;z=-.66f;pitch=-38;yaw=7;roll=12;twist=18;}
            else if(pose.kind==SPEECH){x=.10f+.16f*kick;y=-.12f;z=-.48f-.24f*kick;pitch=-28-32*kick;yaw=24;roll=24;twist=10;}
            else if(pose.kind==UZUMAKI){
                if(support){x=.28f;y=-.24f;z=-.78f-.12f*kick;pitch=-70;yaw=22;roll=24;twist=-20;}
                else{x=.28f;y=-.24f;z=-.87f-.22f*kick;pitch=-77-7*kick;yaw=4;roll=15;twist=24;}
            }else if(pose.kind==LOVE_BEAM){
                float push=smooth((pose.age-64)/8);
                x=mix(.18f,.30f,push);y=mix(-.34f,-.24f,push);z=mix(-.50f,-1.06f,push);
                pitch=mix(-48,-79,push);yaw=support?20:5;roll=support?25:12;twist=support?-25:30;
            }else if(pose.kind==YUTA){
                // Upright lead hand and horizontal support across the wrist form Yuta's own seal.
                if(support){x=.02f;y=-.39f;z=-.62f;pitch=-47;yaw=36;roll=72;twist=-20;}
                else{x=.12f;y=-.18f;z=-.67f;pitch=-27;yaw=12;roll=9;twist=26;}
            }
            else{x=.115f;y=-.31f+(support?.02f:0);z=-.72f+(support?.055f:0);pitch=-49;yaw=23;roll=39;twist=-35;}
            stack.translate(side*mix(support?.47f:.39f,x,w),mix(support?-.79f:-.35f,y,w),mix(-.75f,z,w));
            stack.mulPose(Axis.YP.rotationDegrees(side*mix(10,yaw,w)));
            stack.mulPose(Axis.XP.rotationDegrees(mix(-74,pitch,w)));
            stack.mulPose(Axis.ZP.rotationDegrees(side*mix(8,roll,w)));
            stack.mulPose(Axis.YP.rotationDegrees(side*twist*w));
            if(support)stack.scale(1,1-.14f*w,1);
            FirstPersonArmSpace.apply(stack,arm);
            FirstPersonArmSpace.fromEmpty(stack,parent,arm,event.getEquipProgress(),event.getSwingProgress(),w,support);
            FirstPersonSkin.draw(renderer,stack,event.getMultiBufferSource(),event.getPackedLight(),player,arm);
        }finally{stack.popPose();}
    }
    /** Returns true when these gestures own setupAnim; existing Red/Purple/slash poses otherwise apply. */
    public static boolean poseArms(HumanoidModel<?> model,LivingEntity entity) {
        if(!(entity instanceof AbstractClientPlayer player)||renderingHand())return false;
        Pose pose=pose(player,Minecraft.getInstance().getFrameTime());if(pose==null)return false;
        HumanoidArm arm=arm(player,pose.hand);float side=arm==HumanoidArm.RIGHT?1:-1,w=pose.weight;
        ModelPart main=arm==HumanoidArm.RIGHT?model.rightArm:model.leftArm,other=arm==HumanoidArm.RIGHT?model.leftArm:model.rightArm;
        float yaw=model.head.yRot,pitch=model.head.xRot,kick=pose.release<0?0:smooth(pose.release/2)*(1-smooth((pose.release-2)/6));
        if(pose.kind==BLUE)blend(main,-1.52f+pitch*.85f-kick*.14f,yaw-side*.10f,side*.13f,w);
        else if(pose.kind==FURNACE){
            float draw=smooth(pose.age/26);
            blend(main,-1.58f+pitch*.85f+kick*.08f,yaw-side*.23f,side*.10f,w);
            blend(other,-1.72f+pitch*.55f,yaw+side*(.35f+.40f*draw),-side*.12f,w);
        }else if(pose.kind==VOID)blend(main,-2.10f+pitch*.40f,yaw*.60f-side*.22f,side*.14f,w);
        else if(pose.kind==SPEECH)blend(main,-2.35f+pitch*.45f+kick*.55f,yaw*.55f-side*.32f,side*.22f,w);
        else if(pose.kind==UZUMAKI){
            blend(main,-1.52f+pitch*.8f-kick*.18f,yaw-side*.24f,side*.22f,w);
            blend(other,-1.45f+pitch*.8f-kick*.12f,yaw+side*.34f,-side*.24f,w);
        }else if(pose.kind==LOVE_BEAM){
            float push=smooth((pose.age-64)/8);
            blend(main,mix(-1.10f,-1.59f,push)+pitch*.8f,yaw-side*.22f,side*.18f,w);
            blend(other,mix(-1.05f,-1.54f,push)+pitch*.8f,yaw+side*.28f,-side*.20f,w);
        }else if(pose.kind==YUTA){
            blend(main,-2.05f+pitch*.25f,yaw*.35f-side*.36f,side*.12f,w);
            blend(other,-1.48f+pitch*.20f,yaw*.30f+side*.88f,-side*.52f,w);
        }
        else{
            blend(main,-1.92f+pitch*.35f,yaw*.45f-side*.50f,side*.29f,w);
            blend(other,-1.92f+pitch*.35f,yaw*.45f+side*.50f,-side*.29f,w);
        }
        return true;
    }
    private static boolean matches(ItemStack stack,int kind) {
        return switch(kind){case BLUE->stack.is(VfxItems.BLUE.get());case FURNACE->stack.is(VfxItems.FUGA.get());
            case LOVE_BEAM->stack.is(VfxItems.LOVE_BEAM.get());case UZUMAKI->stack.is(VfxItems.MAXIMUM_UZUMAKI.get());case SPEECH->stack.is(VfxItems.CURSED_SPEECH.get());case YUTA->stack.is(VfxItems.YUTA_DOMAIN.get());
            case VOID->stack.is(VfxItems.UNLIMITED_VOID.get());case SHRINE->stack.is(VfxItems.MALEVOLENT_SHRINE.get());default->false;};
    }
    private static HumanoidArm arm(AbstractClientPlayer player,InteractionHand hand){return hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();}
    private static void blend(ModelPart part,float pitch,float yaw,float roll,float weight){part.xRot=mix(part.xRot,pitch,weight);part.yRot=mix(part.yRot,yaw,weight);part.zRot=mix(part.zRot,roll,weight);}
    private static float mix(float a,float b,float t){return Mth.lerp(t,a,b);}
    private static float smooth(float t){t=Mth.clamp(t,0,1);return t*t*(3-2*t);}
}
