package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Terrain is a separate vanilla explosion, after the skill's entity damage and visibility checks. */
@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class SkillTerrain {
    private static final Map<Explosion, TerrainOnly> IN_FLIGHT = Collections.synchronizedMap(new IdentityHashMap<>());

    private SkillTerrain() {}

    static void red(LivingEntity owner, DamageSource damage, Vec3 point, float radius, BooleanSupplier current) {
        explode(owner, damage, point, Math.clamp(radius * .22f, 1f, 6f), false, current);
    }

    static void furnace(LivingEntity owner, DamageSource damage, Vec3 point, float radius, BooleanSupplier current) {
        explode(owner, damage, point, Math.clamp(radius * .30f, 1f, 8f), true, current);
    }

    private static void explode(LivingEntity owner, DamageSource damage, Vec3 point, float power,
                                boolean flame, BooleanSupplier current) {
        ServerLevel level = (ServerLevel) owner.level();
        if (!current.getAsBoolean() || !level.isInWorldBounds(BlockPos.containing(point))
                || !TechniqueTerrainRules.canGrief(owner)
                || !loadedEnvelope(level, point, power)) return;
        boolean fire = flame;
        TerrainOnly calculator = new TerrainOnly(level, current);
        try (SkillBlockDrops.Scope ignored = SkillBlockDrops.suppress(level)) {
            // BLOCK does not reapply mobGriefing. Protection Start/Detonate hooks remain intact,
            // and our own canGrief check above lets protection mods veto the operation.
            level.explode(owner, damage, calculator, point, power, fire, Level.ExplosionInteraction.BLOCK);
        } finally {
            if (calculator.explosion != null) IN_FLIGHT.remove(calculator.explosion);
            if (calculator.attachments != null) calculator.attachments.close();
        }
    }

    private static boolean loadedEnvelope(ServerLevel level, Vec3 point, float power) {
        // Vanilla's longest ray is under 1.74 * power. Include the entity query, block
        // rounding and adjacent block updates, using getChunkNow so this cannot load a chunk.
        double reach = power * 2 + 4;
        int minX = ((int) Math.floor(point.x - reach)) >> 4;
        int maxX = ((int) Math.floor(point.x + reach)) >> 4;
        int minZ = ((int) Math.floor(point.z - reach)) >> 4;
        int maxZ = ((int) Math.floor(point.z + reach)) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void terrainOnly(ExplosionEvent.Detonate event) {
        TerrainOnly calculator = IN_FLIGHT.get(event.getExplosion());
        if (calculator == null) return;
        // The calculator suppresses vanilla damage and force. Also omit the entity pass
        // entirely, including its exposure rays and secondary knockback events.
        event.getAffectedEntities().clear();
        if (!calculator.allowed() || !event.getExplosion().interactsWithBlocks()) {
            event.getAffectedBlocks().clear();
        } else {
            event.getAffectedBlocks().removeIf(pos -> !calculator.level.isLoaded(pos)
                    || !calculator.level.getWorldBorder().isWithinBounds(pos));
            calculator.attachments = SkillHangingBreaks.capture(calculator.level, event.getAffectedBlocks());
        }
    }

    private static final class TerrainOnly extends ExplosionDamageCalculator {
        private final ServerLevel level;
        private final BooleanSupplier current;
        private Explosion explosion;
        private SkillHangingBreaks attachments;

        private TerrainOnly(ServerLevel level, BooleanSupplier current) {
            this.level = level;
            this.current = current;
        }

        private boolean allowed() {
            return current.getAsBoolean() && TechniqueTerrainRules.enabled();
        }

        @Override
        public Optional<Float> getBlockExplosionResistance(Explosion source, BlockGetter world, BlockPos pos,
                                                          BlockState state, FluidState fluid) {
            // The calculator receives the exact explosion identity before Detonate; unrelated
            // nested explosions from another mod are never stripped of their entity effects.
            if (explosion == null) { explosion = source; IN_FLIGHT.put(source, this); }
            if (!allowed()) return Optional.of(Float.MAX_VALUE);
            return super.getBlockExplosionResistance(source, world, pos, state, fluid)
                    .map(value -> Float.isFinite(value) ? Math.max(0, value) : Float.MAX_VALUE);
        }

        @Override
        public boolean shouldBlockExplode(Explosion source, BlockGetter world, BlockPos pos,
                                          BlockState state, float power) {
            // KEEP must also exclude fire placement when a protection hook denies griefing.
            return allowed() && source.interactsWithBlocks() && level.getWorldBorder().isWithinBounds(pos)
                    && super.shouldBlockExplode(source, world, pos, state, power);
        }

        @Override
        public boolean shouldDamageEntity(Explosion source, Entity entity) { return false; }

        @Override
        public float getEntityDamageAmount(Explosion source, Entity entity) { return 0; }

        @Override
        public float getKnockbackMultiplier(Entity entity) { return 0; }
    }
}
