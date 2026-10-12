package dev.jujutsuvfx.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SukunaCat extends SorcererCat {
    public SukunaCat(EntityType<? extends TamableAnimal> type, Level level) { super(type, level); }
    @Override public boolean isSukuna() { return true; }
    @Override protected int defaultCursedHands() { return 0; }
    @Override protected int defaultBlackFlashHands() { return 0; }
    @Override protected float punchDamageMultiplier(int kind) { return kind == BLACK_FLASH ? 4 : 1; }
    @Override protected void onPunchHit(ServerLevel level, LivingEntity target, Vec3 direction, int kind) {
        cursedHit(level, target, direction, kind);
    }
}
