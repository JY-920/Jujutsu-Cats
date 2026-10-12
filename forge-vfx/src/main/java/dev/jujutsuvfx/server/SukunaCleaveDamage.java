package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One damage contact per actual slash, shared by player and NPC Cleave; independent of terrain success. */
final class SukunaCleaveDamage {
    private static final ResourceKey<DamageType> TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(JujutsuVfx.ID,"sukuna_cleave"));
    static DamageSource source(LivingEntity owner){return new DamageSource(owner.level().registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),owner,owner);}
    static boolean strike(LivingEntity owner,Vec3 center,Vec3 normal,Vec3 forward,double radius,Vec3 second,double scale) {
        if(!(owner.level() instanceof ServerLevel level)||!owner.isAlive())return false;
        DamageSource source=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),owner,owner);
        double range=Math.min(192,radius*Math.max(1,scale));int count=0;boolean hit=false;
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(range),e->
                TechniquePlayerRules.canAffect(owner,e)
                        &&(!(owner instanceof SorcererCat cat)||cat.canFight(e)))) {
            if(count++>=48)break;
            AABB box=target.getBoundingBox();
            if(!contact(box,center,normal,radius)&&(second==null||!contact(box,center,second,radius*scale)))continue;
            if(TechniqueDamageRules.hurt(target,source,owner instanceof SorcererCat cat && cat.isSukuna() ? 59 : 50,TechniqueDamage.CLEAVE))hit=true;
        }
        return hit;
    }
    private static boolean contact(AABB box,Vec3 center,Vec3 normal,double radius) {
        if(box.distanceToSqr(center)>radius*radius)return false;
        double extent=(Math.abs(normal.x)*box.getXsize()+Math.abs(normal.y)*box.getYsize()+Math.abs(normal.z)*box.getZsize())*.5;
        return Math.abs(box.getCenter().subtract(center).dot(normal))<=extent+.6;
    }
}
