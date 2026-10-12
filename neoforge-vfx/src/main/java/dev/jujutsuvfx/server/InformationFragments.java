package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class InformationFragments {
    private InformationFragments() {}
    static void fill(ServerPlayer player){
        boolean changed=false;
        // Only the 36 storage/hotbar slots. Never replace gear, a carried cursor stack or other items.
        for(int i=0;i<player.getInventory().items.size();i++)if(player.getInventory().items.get(i).isEmpty()){
            player.getInventory().items.set(i,new ItemStack(VfxItems.INFORMATION_FRAGMENT.get()));changed=true;
        }
        if(changed){player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();}
    }
    private static boolean fragment(ItemStack stack){return stack.is(VfxItems.INFORMATION_FRAGMENT.get());}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%10!=0)return;
        for(ServerPlayer player:event.getServer().getPlayerList().getPlayers()){
            if(!player.isAlive()||player.isSpectator())continue;
            if(DomainFreeze.inVoid(player))continue;
            boolean found=fragment(player.containerMenu.getCarried());
            for(int i=0;i<player.getInventory().getContainerSize();i++)if(fragment(player.getInventory().getItem(i))){found=true;break;}
            if(!found)continue;
            // Brief refreshes expire naturally after disposal; do not remove another potion's effects.
            for(var effect:java.util.List.of(MobEffects.MOVEMENT_SLOWDOWN,MobEffects.WEAKNESS,MobEffects.DIG_SLOWDOWN))
                player.addEffect(new MobEffectInstance(effect,30,0,false,false,true));
        }
    }
    @SubscribeEvent public static void toss(ItemTossEvent event){
        if(fragment(event.getEntity().getItem())){event.setCanceled(true);event.getEntity().discard();}
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getEntity() instanceof ItemEntity item&&fragment(item.getItem())){
            event.setCanceled(true);item.discard();
        }
    }
}
