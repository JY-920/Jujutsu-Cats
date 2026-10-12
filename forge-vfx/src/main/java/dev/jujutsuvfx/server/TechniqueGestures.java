package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.network.SkillGesture;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import java.util.*;

/** Cosmetic state only. Accepted skill handlers own every launch, hit, lock and terrain edit. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class TechniqueGestures {
    private static final Map<MinecraftServer,Map<UUID,Motion>> STATES=new WeakHashMap<>();
    private static final class Motion {
        final ServerPlayer owner;final ServerLevel level;final int session,kind;int hand;final long born;final int autoRelease;
        ItemStack bound;
        int release=-1;
        Motion(ServerPlayer owner,int session,int kind,InteractionHand hand,boolean guided) {
            this.owner=owner;level=owner.serverLevel();this.session=session;this.kind=kind;this.hand=hand==InteractionHand.OFF_HAND?1:0;
            ItemStack held=owner.getItemInHand(hand);bound=matches(held,kind)?held.copy():ItemStack.EMPTY;
            born=owner.getServer().getTickCount();autoRelease=kind==SkillGesture.BLUE?(guided?200:14):kind==SkillGesture.FURNACE?80:26;
        }
        int age(){return (int)Math.max(0,owner.getServer().getTickCount()-born);}
        SkillGesture snapshot(){return new SkillGesture(owner.getId(),owner.getUUID(),session,kind,hand,age(),release);}
        void broadcast(){SkillGesture packet=snapshot();for(ServerPlayer observer:level.players())if(release==-2||observer.distanceToSqr(owner)<112*112)packet.send(observer);}
    }
    private TechniqueGestures() {}
    public static void start(ServerPlayer owner,int session,int kind,InteractionHand hand,boolean guided) {
        Map<UUID,Motion> state=STATES.computeIfAbsent(owner.getServer(),key->new HashMap<>());
        Motion old=state.get(owner.getUUID());
        if(old!=null&&old.session==session&&old.kind==kind)return;
        if(hand==null)hand=owner.isUsingItem()?owner.getUsedItemHand():!matches(owner.getMainHandItem(),kind)&&matches(owner.getOffhandItem(),kind)
                ?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        Motion next=new Motion(owner,session,kind,hand,guided);state.put(owner.getUUID(),next);next.broadcast();
    }
    public static void release(ServerPlayer owner,int session,int kind) {
        Motion motion=find(owner,session,kind);if(motion==null||motion.release>=0)return;
        motion.release=motion.age();motion.broadcast();
    }
    public static void cancel(ServerPlayer owner,int session,int kind) {
        Motion motion=find(owner,session,kind);if(motion==null)return;
        motion.release=-2;motion.broadcast();STATES.get(owner.getServer()).remove(owner.getUUID(),motion);
    }
    public static void interrupt(ServerPlayer owner) {
        Map<UUID,Motion> state=STATES.get(owner.getServer());Motion motion=state==null?null:state.remove(owner.getUUID());
        if(motion!=null){motion.release=-2;motion.broadcast();}
    }
    private static Motion find(ServerPlayer owner,int session,int kind) {
        Map<UUID,Motion> state=STATES.get(owner.getServer());Motion motion=state==null?null:state.get(owner.getUUID());
        return motion!=null&&motion.session==session&&motion.kind==kind?motion:null;
    }
    @SubscribeEvent public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Map<UUID,Motion> state=STATES.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());if(state==null)return;
        for(var iterator=state.values().iterator();iterator.hasNext();) {
            Motion motion=iterator.next();int age=motion.age();
            if(!motion.owner.isAlive()||motion.owner.isRemoved()||motion.owner.isSpectator()||motion.owner.level()!=motion.level) {
                motion.release=-2;motion.broadcast();iterator.remove();continue;
            }
            if(age<=2&&motion.owner.isUsingItem()&&matches(motion.owner.getUseItem(),motion.kind)){
                int hand=motion.owner.getUsedItemHand()==InteractionHand.OFF_HAND?1:0;
                if(motion.hand!=hand){motion.hand=hand;motion.bound=motion.owner.getUseItem().copy();motion.broadcast();}
            }
            if(age>2&&!motion.bound.isEmpty()&&!ItemStack.isSameItemSameTags(motion.bound,
                    motion.owner.getItemInHand(motion.hand==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND))){
                motion.release=-2;motion.broadcast();iterator.remove();continue;
            }
            if(motion.release<0&&age>=motion.autoRelease){motion.release=motion.autoRelease;motion.broadcast();}
            if(motion.release>=0&&age>=motion.release+SkillGesture.RETURN_TICKS){iterator.remove();continue;}
            if(age%10==0)motion.broadcast();
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if(!(event.getEntity() instanceof ServerPlayer observer)||!(event.getTarget() instanceof ServerPlayer player))return;
        Map<UUID,Motion> state=STATES.get(observer.getServer());Motion motion=state==null?null:state.get(player.getUUID());
        if(motion!=null&&motion.level==observer.level())motion.snapshot().send(observer);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){STATES.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());}
    private static boolean matches(ItemStack stack,int kind){return switch(kind){
        case SkillGesture.BLUE->stack.is(VfxItems.BLUE.get());case SkillGesture.FURNACE->stack.is(VfxItems.FUGA.get());
        case SkillGesture.VOID->stack.is(VfxItems.UNLIMITED_VOID.get());case SkillGesture.SHRINE->stack.is(VfxItems.MALEVOLENT_SHRINE.get());default->false;};}
}
