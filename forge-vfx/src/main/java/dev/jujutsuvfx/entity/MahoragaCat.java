package dev.jujutsuvfx.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A neutral, physical fighter. The wheel is an authored model animation, not a spell. */
public final class MahoragaCat extends SorcererCat {
    public MahoragaCat(EntityType<? extends TamableAnimal> type, Level level) { super(type, level); }
    @Override public boolean isSukuna() { return false; }
    @Override public boolean isMahoraga() { return true; }
    public static AttributeSupplier.Builder createAttributes() {
        return SorcererCat.createAttributes().add(Attributes.ATTACK_DAMAGE, 17).add(Attributes.KNOCKBACK_RESISTANCE, .55);
    }
    @Override protected float punchDamageMultiplier(int kind) { return 1; }
    @Override protected void onPunchHit(ServerLevel level, LivingEntity target, Vec3 direction, int kind) {
        Vec3 point = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }
}
