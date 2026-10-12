package dev.jujutsuvfx.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Restricts silent block destruction to the current terrain operation and its exact level. */
public final class SkillBlockDrops {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<HangingEntity> SILENT_ATTACHMENT = new ThreadLocal<>();

    private SkillBlockDrops() {}

    /** Enter only around terrain edits, never around entity damage or an entire server tick. */
    public static Scope suppress(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        requireServerThread(level);
        Scope previous = ACTIVE.get();
        Scope scope = new Scope(level, previous, previous != null && previous.level == level && previous.quietEffects);
        ACTIVE.set(scope);
        return scope;
    }

    public static boolean suppressed(Level level) {
        Scope scope = ACTIVE.get();
        return scope != null && scope.level == level;
    }

    /** A rendered large core supplies its own debris/sound; avoid hundreds of vanilla level events. */
    static Scope quietTerrain(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        requireServerThread(level);
        Scope scope = new Scope(level, ACTIVE.get(), true);
        ACTIVE.set(scope);
        return scope;
    }

    public static boolean quietBreakEffects(Level level) {
        Scope scope = ACTIVE.get();
        return scope != null && scope.level == level && scope.quietEffects;
    }

    /** A separately checked paired connector may be stored in another distant Sable plot. */
    public static boolean destroyWithoutDrops(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        return destroyWithoutDrops(level, pos, (net.minecraft.world.entity.LivingEntity) owner);
    }
    public static boolean destroyWithoutDrops(ServerLevel level, BlockPos pos, net.minecraft.world.entity.LivingEntity owner) {
        if (!TechniqueTerrainRules.enabled()) return false;
        try (Scope ignored = suppress(level);
             SkillHangingBreaks attachments = SkillHangingBreaks.capture(level, java.util.List.of(pos))) {
            return level.destroyBlock(pos, false, owner);
        }
    }

    /**
     * Commit one broken-support decoration without item entities. Callers retain normal Fixed
     * item-frame checks. The vanilla drop callback still clears framed items and map markers.
     */
    public static void discardAttachment(HangingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) return;
        requireServerThread(level);
        HangingEntity previous = SILENT_ATTACHMENT.get();
        SILENT_ATTACHMENT.set(entity);
        try {
            entity.discard();
            entity.dropItem(null);
        } finally {
            if (previous == null) SILENT_ATTACHMENT.remove();
            else SILENT_ATTACHMENT.set(previous);
        }
    }

    /** Used only by the HangingEntity spawn hook; other entity loot remains unaffected. */
    public static boolean attachmentSuppressed(HangingEntity entity) {
        return SILENT_ATTACHMENT.get() == entity;
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Skill terrain edits must run on the server thread");
    }

    public static final class Scope implements AutoCloseable {
        private final ServerLevel level;
        private final Scope previous;
        private final boolean quietEffects;
        private final Thread thread = Thread.currentThread();
        private boolean closed;

        private Scope(ServerLevel level, Scope previous, boolean quietEffects) {
            this.level = level;
            this.previous = previous;
            this.quietEffects = quietEffects;
        }

        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != thread || ACTIVE.get() != this)
                throw new IllegalStateException("Skill drop scopes must close on their owning thread in reverse order");
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
            closed = true;
        }
    }
}
