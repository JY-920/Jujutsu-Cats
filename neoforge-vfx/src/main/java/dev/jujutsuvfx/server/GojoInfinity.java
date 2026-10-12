package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/** Passive projectile rejection does not interrupt a cast or require an active guard pose. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class GojoInfinity {
    private GojoInfinity() {}
    static boolean projectile(DamageSource source){
        return source.is(DamageTypeTags.IS_PROJECTILE)||source.getDirectEntity() instanceof Projectile;
    }
    /** Residual burning/contact heat is not a fresh incoming attack that can renew a stance. */
    static boolean ambientFire(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) && source.getEntity()==null
                && source.getDirectEntity()==null && !technique(source);
    }
    static boolean shrine(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k->k.location().getNamespace().equals(JujutsuVfx.ID)
                &&k.location().getPath().equals("shrine_cut")).orElse(false);
    }
    static boolean technique(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k->k.location().getNamespace().equals(JujutsuVfx.ID)
                &&!k.location().getPath().equals("sorcerer_combo")).orElse(false);
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void impact(ProjectileImpactEvent event){
        Projectile shot=event.getProjectile();
        if(shot.level().isClientSide||!(event.getRayTraceResult() instanceof EntityHitResult hit)
                ||!(hit.getEntity() instanceof SorcererCat cat)||!cat.isGojo()||!cat.isAlive())return;
        if(CatAdaptation.piercesInfinity(shot.getOwner()))return;
        cat.combat().attacked();
        // Arrow/trident hit code handles a rejected hurt() itself: arrows bounce,
        // pickup is retained, and Loyalty tridents can return to their owner.
        if(shot instanceof AbstractArrow)return;
        cat.provokeAttacker(shot.getOwner());
        cat.combat().projectileBlocked(shot,hit.getLocation());
        event.setCanceled(true);
        // Stop bottles, snowballs and fireballs before their impact/status/explosion callbacks.
        shot.discard();
    }
}
