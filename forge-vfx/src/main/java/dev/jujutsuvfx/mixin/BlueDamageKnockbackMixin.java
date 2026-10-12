package dev.jujutsuvfx.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Backport the damage tag so Blue's pull is not interrupted by vanilla hurt knockback. */
@Mixin(LivingEntity.class)
public abstract class BlueDamageKnockbackMixin {
    @Unique private static final TagKey<DamageType> JUJUTSUVFX_NO_KNOCKBACK=
            TagKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("minecraft","no_knockback"));
    // ModifyArgs generates an Args$ class which the Forge 1.20.1 module loader can fail to load.
    // Forward the call directly and keep Forge's knockback event and direction handling intact.
    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void jujutsuvfx$keepBluePull(LivingEntity target,double strength,double directionX,double directionZ,
                                       DamageSource source,float amount) {
        target.knockback(source.is(JUJUTSUVFX_NO_KNOCKBACK) ? 0d : strength,directionX,directionZ);
    }
}
