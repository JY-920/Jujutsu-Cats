package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.DamageScaling;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.ToolActions;

import net.minecraftforge.event.entity.living.LivingDamageEvent;

import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import java.util.*;

/** Armor remains useful; only damage that reaches health/absorption spends the burst budget. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class TechniquePlayerRules {
    private record Hit(long tick,float amount) {}
    private static final Map<MinecraftServer,Map<UUID,Deque<Hit>>> HITS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Item>> BROKEN_GUARDS=new WeakHashMap<>();
    private TechniquePlayerRules() {}

    public static boolean canAffect(LivingEntity owner,Entity target){
        if(target==owner||!target.isAlive()||target.isRemoved()||target.isSpectator()||owner.isAlliedTo(target))return false;
        if(!(target instanceof Player player))return true;
        if(player.isCreative()||player.getAbilities().invulnerable)return false;
        if(owner instanceof SorcererCat cat)return cat.canFight(player);
        return !(owner instanceof Player caster)||(player.getServer()==null||player.getServer().isPvpAllowed())&&caster.canHarmPlayer(player);
    }
    private static String technique(DamageSource source){
        return source.typeHolder().unwrapKey().filter(k->k.location().getNamespace().equals(JujutsuVfx.ID))
                .map(k->k.location().getPath()).orElse("");
    }
    private static boolean managed(DamageSource source){
        return !technique(source).isEmpty()||source.getEntity() instanceof SorcererCat
                ||source.getEntity() instanceof Player p&&VfxItems.isHeld(p.getMainHandItem())&&source.is(DamageTypes.PLAYER_ATTACK);
    }
    private static float catDamage(String kind,float original,boolean blackFlash){
        return switch(kind){
            case "shrine_cut" -> Math.min(6f,original*.20f);
            case "sorcerer_combo" -> Math.min(blackFlash?9f:4.5f,original*.42f);
            case "limitless_blue" -> Math.min(6f,original*.80f);
            case "limitless_red" -> Math.min(20f,original*.35f);
            case "hollow_purple" -> Math.min(28f,original*.12f);
            case "furnace" -> Math.min(24f,original*.12f);
            case "sukuna_cleave" -> Math.min(14f,original*.24f);
            default -> Math.min(blackFlash?26f:14f,original*.85f);
        };
    }
    /** Runs before vanilla shield/armor processing, matching the NeoForge incoming stage. */
    public static float incoming(LivingEntity entity,DamageSource source,float original) {
        if(!(entity instanceof ServerPlayer player)||!managed(source)||original<=0)return original;
        String kind=technique(source);
        if(source.getEntity() instanceof LivingEntity owner&&!canAffect(owner,player))return 0;
        float multiplier=TechniqueDamageRules.multiplier(source);
        if(kind.equals("unlimited_purple"))return DamageScaling.scaled(original,multiplier);
        float amount=source.getEntity() instanceof SorcererCat?catDamage(kind,original,source instanceof CatMeleeDamage strike&&strike.blackFlash())
                :kind.equals("shrine_cut")?Math.min(.5f,original*.025f)
                :kind.equals("sorcerer_combo")?Math.min(.8f,original*.12f)
                :kind.isEmpty()?Math.min(3f,original*.18f):Math.min(6f,original*.08f);
        return DamageScaling.scaled(amount,multiplier);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void shield(ShieldBlockEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!managed(event.getDamageSource())
                ||event.isCanceled()||event.getBlockedDamage()<=0)return;
        var shield=player.getUseItem();
        if(!shield.canPerformAction(ToolActions.SHIELD_BLOCK))return;
        // A second contact in the same tick must not reuse an already broken guard.
        if(player.getCooldowns().isOnCooldown(shield.getItem())){event.setCanceled(true);return;}
        String kind=technique(event.getDamageSource());
        boolean spell=!kind.isEmpty()&&!kind.equals("sorcerer_combo");
        float incoming=event.getOriginalBlockedDamage();
        if(spell){
            event.setBlockedDamage(Math.min(event.getBlockedDamage(),incoming*.30f));
            player.level().playSound(null,player.blockPosition(),SoundEvents.SHIELD_BLOCK,player.getSoundSource(),.75f,1f);
        }
        // Vanilla ignores shield wear below 3 damage. Keep its actual durability,
        // Unbreaking, item break callback and equipment synchronization intact.
        if(event.shieldTakesDamage()&&incoming<3f){
            shield.hurtAndBreak(1,player,p->p.broadcastBreakEvent(p.getUsedItemHand()));
            if(shield.isEmpty())player.stopUsingItem();
        }
        int disable=spell?switch(kind){
            case "shrine_cut" -> 10;
            case "sukuna_cleave" -> 20;
            case "limitless_red" -> 30;
            case "hollow_purple","unlimited_purple","furnace" -> 40;
            default -> 0;
        }:0;
        DamageSource source=event.getDamageSource();
        if(source.is(DamageTypeTags.BYPASSES_ARMOR))
            disable=Math.max(disable,20);
        if(disable>0){
            player.getCooldowns().addCooldown(shield.getItem(),disable);
            // Do not stop using the shield here: hurtCurrentlyUsedShield runs AFTER
            // this callback and still needs the held stack to apply durability.
            BROKEN_GUARDS.computeIfAbsent(player.getServer(),key->new HashMap<>()).put(player.getUUID(),shield.getItem());
        }
    }
    private static Deque<Hit> recent(ServerPlayer player){
        long now=player.getServer().getTickCount();
        Deque<Hit> hits=HITS.computeIfAbsent(player.getServer(),key->new HashMap<>())
                .computeIfAbsent(player.getUUID(),key->new ArrayDeque<>());
        while(!hits.isEmpty()&&now-hits.peekFirst().tick>=20)hits.removeFirst();
        return hits;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void limitBurst(LivingDamageEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!managed(event.getSource())||event.getAmount()<=0)return;
        if(technique(event.getSource()).equals("unlimited_purple"))return;
        float spent=0;for(Hit hit:recent(player))spent+=hit.amount;
        float budget=event.getSource().getEntity() instanceof SorcererCat?12f:6f;
        float multiplier=TechniqueDamageRules.multiplier(event.getSource());
        event.setAmount(DamageScaling.budgeted(event.getAmount(),spent,budget,multiplier));
        if(event.getAmount()>0)recent(player).addLast(new Hit(player.getServer().getTickCount(),
                DamageScaling.baseSpent(event.getAmount(),multiplier)));
    }
    @SubscribeEvent public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Map<UUID,Item> guards=BROKEN_GUARDS.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        if(guards!=null)guards.forEach((id,item)->{
            ServerPlayer player=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(id);
            if(player==null||!player.isAlive())return;
            if(player.getUseItem().is(item))player.stopUsingItem();
            player.level().broadcastEntityEvent(player,(byte)30);
        });
        if(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount()%200!=0)return;
        Map<UUID,Deque<Hit>> players=HITS.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());if(players==null)return;
        long now=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount();
        players.values().removeIf(hits->hits.isEmpty()||now-hits.peekLast().tick>=20);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){HITS.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());BROKEN_GUARDS.remove(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());}
}
