package dev.jujutsuvfx.compat;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.server.SkillBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Preserves live wall decorations when Sable moves their supporting blocks between plots. */
final class SableHangingAttachments {
    private static final int MAX_ATTACHMENTS = 1024, MAX_SUPPORT_CELLS = 4096;
    private static final Method SUPPORT_BOX = supportBoxMethod();
    private final ServerLevel level;
    private final List<Attachment> attachments;
    private int groupCount = -1;
    private boolean parkFailed;

    private static final class Attachment {
        final HangingEntity entity;
        final UUID uuid;
        final BlockPos anchor;
        final Direction direction;
        final Map<BlockPos, BlockState> supports;
        final CompoundTag metadata;
        final boolean fixed;
        int owner = -1;
        boolean spansOwners, intentionalDrop;
        BlockPos rollbackAnchor;

        Attachment(ServerLevel level, HangingEntity entity, List<BlockPos> supports) {
            this.entity = entity;
            uuid = entity.getUUID();
            anchor = entity.getPos().immutable();
            direction = entity.getDirection();
            this.supports = new LinkedHashMap<>();
            for (BlockPos support : supports) this.supports.put(support, level.getBlockState(support));
            metadata = metadata(entity);
            fixed = entity instanceof ItemFrame && metadata.getBoolean("Fixed");
        }
    }

    private SableHangingAttachments(ServerLevel level, List<Attachment> attachments) {
        this.level = level;
        this.attachments = attachments;
    }

    static SableHangingAttachments capture(ServerLevel level, Set<BlockPos> sourceBlocks) {
        List<Attachment> captured = new ArrayList<>();
        if (sourceBlocks.isEmpty()) return new SableHangingAttachments(level, captured);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : sourceBlocks) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        // Native assembly expands its selected block bounds by one, then searches two more blocks.
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0).inflate(3);
        List<HangingEntity> candidates = level.getEntitiesOfClass(HangingEntity.class, bounds,
                entity -> !entity.isRemoved());
        candidates.sort(Comparator.comparingInt(HangingEntity::getId));
        for (HangingEntity entity : candidates) {
            AABB box = supportBox(entity);
            if (!finite(box)) throw new IllegalStateException("Hanging decoration has invalid support bounds");
            BlockPos low = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos high = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            double cells = ((double) high.getX() - low.getX() + 1)
                    * ((double) high.getY() - low.getY() + 1) * ((double) high.getZ() - low.getZ() + 1);
            if (cells <= 0 || cells > MAX_SUPPORT_CELLS) {
                // An oversized nearby decoration is irrelevant unless it actually uses a source block.
                if (sourceBlocks.stream().anyMatch(pos -> inside(pos, low, high)))
                    throw new IllegalStateException("One affected decoration has too many support cells");
                continue;
            }
            List<BlockPos> supports = BlockPos.betweenClosedStream(low, high).map(BlockPos::immutable).toList();
            if (supports.stream().noneMatch(sourceBlocks::contains)) continue;
            if (captured.size() >= MAX_ATTACHMENTS)
                throw new IllegalStateException("The selected structure has too many attached decorations");
            captured.add(new Attachment(level, entity, supports));
        }
        return new SableHangingAttachments(level, captured);
    }

    /** A kerf is committed destruction even when later assembly cannot complete. */
    void dropUnsupportedAfterCut() {
        for (Attachment attachment : attachments) {
            if (removedIntentionally(attachment) || attachment.fixed) continue;
            boolean changed = attachment.supports.entrySet().stream()
                    .anyMatch(entry -> !level.getBlockState(entry.getKey()).equals(entry.getValue()));
            if (changed && !attachment.entity.survives()) drop(attachment);
        }
    }

    /** Classify every original support cell, including cells retained in the world or original body. */
    void prepare(List<Set<BlockPos>> transferredGroups) {
        groupCount = transferredGroups.size();
        Map<BlockPos, Integer> owners = new HashMap<>();
        for (int index = 0; index < transferredGroups.size(); index++) {
            for (BlockPos block : transferredGroups.get(index)) {
                Integer prior = owners.put(block, index);
                if (prior != null && prior != index) throw new IllegalStateException("Decoration support has two physical owners");
            }
        }
        for (Attachment attachment : attachments) {
            if (removedIntentionally(attachment)) continue;
            verify(attachment);
            Map<Integer, Integer> counts = new LinkedHashMap<>();
            for (BlockPos support : attachment.supports.keySet()) counts.merge(owners.getOrDefault(support, -1), 1, Integer::sum);
            attachment.spansOwners = counts.size() > 1 && !attachment.fixed;
            BlockPos primarySupport = attachment.anchor.relative(attachment.direction.getOpposite());
            int preferred = owners.getOrDefault(primarySupport, -1);
            attachment.owner = counts.containsKey(preferred) ? preferred : counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(-1);
            // Spanning paintings remain alive until assembly commits, so failure can restore them intact.
        }
    }

    void finish(List<BlockPos> offsets) {
        if (groupCount < 0 || offsets.size() != groupCount)
            throw new IllegalStateException("Decoration relocation offsets do not match fragment ownership");
        // Validate every survivor before silently removing decorations with split supports.
        for (Attachment attachment : attachments) {
            if (removedIntentionally(attachment)) continue;
            BlockPos anchor = attachment.owner < 0 ? attachment.anchor
                    : attachment.anchor.offset(offsets.get(attachment.owner));
            relocate(attachment, anchor);
            verify(attachment);
        }
        for (Attachment attachment : attachments) {
            if (removedIntentionally(attachment) || attachment.spansOwners || attachment.fixed) continue;
            if (!attachment.entity.survives()) throw new IllegalStateException("Moved decoration lost a retained support block");
        }
        for (Attachment attachment : attachments) {
            if (!removedIntentionally(attachment) && attachment.spansOwners) drop(attachment);
        }
    }

    /** Move attachments out before clearing a failed body's final massive block can kick its entities. */
    void parkForRollback() {
        for (Attachment attachment : attachments) {
            if (attachment.entity.isRemoved()) {
                if (!attachment.intentionalDrop) {
                    parkFailed = true;
                    LogUtils.getLogger().error("Original hanging decoration {} disappeared before rollback", attachment.uuid);
                }
                continue;
            }
            attachment.rollbackAnchor = attachment.entity.getPos().immutable();
            try { relocate(attachment, attachment.anchor); }
            catch (RuntimeException failure) {
                parkFailed = true;
                LogUtils.getLogger().error("Could not park hanging decoration {} before block rollback", attachment.uuid, failure);
            }
        }
    }

    /** Bodies containing any unrecovered original must not be deleted by transaction cleanup. */
    List<BlockPos> liveAnchors() {
        return attachments.stream().filter(attachment -> !attachment.entity.isRemoved()
                        && attachment.entity.level() == level)
                .map(attachment -> attachment.entity.getPos().immutable()).toList();
    }

    /** Never revive decorations or reconstruct contents removed by a committed kerf. */
    boolean rollback() {
        boolean restored = !parkFailed;
        for (Attachment attachment : attachments) {
            if (attachment.entity.isRemoved()) {
                if (!attachment.intentionalDrop) restored = false;
                continue;
            }
            BlockPos current = attachment.rollbackAnchor == null ? attachment.entity.getPos().immutable()
                    : attachment.rollbackAnchor;
            try {
                verifyIdentity(attachment);
                relocate(attachment, attachment.anchor);
                if (!attachment.fixed && !attachment.entity.survives()) {
                    // An incomplete block rollback may leave the destination as its only intact support.
                    restoreValidFallback(attachment, current);
                    restored = false;
                    continue;
                }
                verify(attachment);
            } catch (RuntimeException failure) {
                restored = false;
                if (!attachment.entity.isRemoved()) {
                    try { restoreValidFallback(attachment, current); }
                    catch (RuntimeException revertFailure) { failure.addSuppressed(revertFailure); }
                }
                LogUtils.getLogger().error("Could not restore hanging decoration {} after cut rollback", attachment.uuid, failure);
            }
        }
        return restored;
    }

    private void restoreValidFallback(Attachment attachment, BlockPos destination) {
        relocate(attachment, destination);
        if (!attachment.fixed && !attachment.entity.survives()) relocate(attachment, attachment.anchor);
    }

    private void relocate(Attachment attachment, BlockPos anchor) {
        verifyIdentity(attachment);
        HangingEntity entity = attachment.entity;
        if (!entity.getPos().equals(anchor)) {
            // Assembly and rollback run within one tick; only the original anchor was registered.
            // Clearing an intermediate native anchor could erase another frame's map marker.
            cleanMapMarker(entity, attachment.anchor);
            entity.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        } else if (!anchor.equals(attachment.anchor)) {
            cleanMapMarker(entity, attachment.anchor);
        }
        if (!entity.getPos().equals(anchor)) throw new IllegalStateException("Hanging decoration did not accept its fragment anchor");
        // BlockAttachedEntity.setPos already sets hasImpulse. Sable's marked native tracking packets
        // must perform synchronization; manually sending an unmarked teleport loses plot coordinates.
    }

    private void verifyIdentity(Attachment attachment) {
        if (attachment.entity.level() != level || !attachment.entity.getUUID().equals(attachment.uuid)
                || level.getEntity(attachment.uuid) != attachment.entity)
            throw new IllegalStateException("Hanging decoration identity changed during assembly");
    }

    private static boolean removedIntentionally(Attachment attachment) {
        if (!attachment.entity.isRemoved()) return false;
        if (!attachment.intentionalDrop)
            throw new IllegalStateException("Original hanging decoration disappeared during assembly: " + attachment.uuid);
        return true;
    }

    private void verify(Attachment attachment) {
        verifyIdentity(attachment);
        if (attachment.entity.getDirection() != attachment.direction || !metadata(attachment.entity).equals(attachment.metadata))
            throw new IllegalStateException("Hanging decoration contents or appearance changed during assembly");
    }

    private void drop(Attachment attachment) {
        verifyIdentity(attachment);
        if (attachment.fixed || attachment.entity.isRemoved()) return;
        attachment.intentionalDrop = true;
        // Keep vanilla frame/map cleanup while suppressing only this attachment's item spawns.
        // Intact transferred frames keep the same entity, item components and fixed state.
        SkillBlockDrops.discardAttachment(attachment.entity);
    }

    private static void cleanMapMarker(HangingEntity entity, BlockPos oldAnchor) {
        if (entity instanceof ItemFrame frame && frame.hasFramedMap()) {
            MapItemSavedData map = MapItem.getSavedData(frame.getItem(), frame.level());
            if (map != null) {
                map.removedFromFrame(oldAnchor, frame.getId());
                map.setDirty();
                // ServerEntity updates all map frames every ten ticks and adds the new anchor.
            }
        }
    }

    private static CompoundTag metadata(HangingEntity entity) {
        CompoundTag data = new CompoundTag();
        entity.addAdditionalSaveData(data);
        data.remove("TileX"); data.remove("TileY"); data.remove("TileZ");
        return data;
    }

    private static Method supportBoxMethod() {
        try {
            Method method = HangingEntity.class.getDeclaredMethod("calculateSupportBox");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static AABB supportBox(HangingEntity entity) {
        try { return (AABB) SUPPORT_BOX.invoke(entity); }
        catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Cannot read hanging decoration support geometry", failure);
        }
    }

    private static boolean inside(BlockPos pos, BlockPos low, BlockPos high) {
        return pos.getX() >= low.getX() && pos.getX() <= high.getX()
                && pos.getY() >= low.getY() && pos.getY() <= high.getY()
                && pos.getZ() >= low.getZ() && pos.getZ() <= high.getZ();
    }

    private static boolean finite(AABB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }
}
