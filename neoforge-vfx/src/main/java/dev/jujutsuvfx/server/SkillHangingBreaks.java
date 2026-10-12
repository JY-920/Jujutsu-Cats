package dev.jujutsuvfx.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves only attachments whose valid supports this terrain edit actually destroys. */
final class SkillHangingBreaks implements AutoCloseable {
    private record Attachment(HangingEntity entity, BlockPos anchor, Map<BlockPos, BlockState> supports) {}
    private final ServerLevel level;
    private final List<Attachment> attachments = new ArrayList<>();

    private SkillHangingBreaks(ServerLevel level) { this.level = level; }

    static SkillHangingBreaks capture(ServerLevel level, Collection<BlockPos> blocks) {
        SkillHangingBreaks result = new SkillHangingBreaks(level);
        if (blocks.isEmpty()) return result;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0).inflate(2);
        List<HangingEntity> candidates = nearby(level, blocks, bounds);
        if (candidates.isEmpty()) return result;
        var changedBlocks = new HashSet<>(blocks);
        for (HangingEntity entity : candidates) {
            try {
                // Query the native virtual method, including support geometry from other mods.
                AABB supportBox = (AABB) SupportGeometry.METHOD.invoke(entity);
                Map<BlockPos, BlockState> supports = new LinkedHashMap<>();
                BlockPos low = BlockPos.containing(supportBox.minX, supportBox.minY, supportBox.minZ);
                BlockPos high = BlockPos.containing(Math.nextDown(supportBox.maxX), Math.nextDown(supportBox.maxY), Math.nextDown(supportBox.maxZ));
                double cells = ((double) high.getX() - low.getX() + 1)
                        * ((double) high.getY() - low.getY() + 1) * ((double) high.getZ() - low.getZ() + 1);
                // Ordinary frames inspect one support cell, even in a thousands-block explosion.
                // Oversized custom paintings instead scan only the bounded edit, with no per-block AABB allocation.
                Iterable<BlockPos> scan = cells > 0 && cells <= changedBlocks.size()
                        ? BlockPos.betweenClosed(low, high) : changedBlocks;
                for (BlockPos pos : scan) {
                    if (changedBlocks.contains(pos) && supportBox.maxX > pos.getX() && supportBox.minX < pos.getX() + 1.0
                            && supportBox.maxY > pos.getY() && supportBox.minY < pos.getY() + 1.0
                            && supportBox.maxZ > pos.getZ() && supportBox.minZ < pos.getZ() + 1.0)
                        supports.put(pos.immutable(), level.getBlockState(pos));
                }
                if (!supports.isEmpty() && entity.survives())
                    result.attachments.add(new Attachment(entity, entity.getPos().immutable(), supports));
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
                LogUtils.getLogger().warn("Could not inspect hanging support for terrain edit: {}", entity.getUUID(), failure);
            }
        }
        return result;
    }

    /** A physics explosion can contain distant storage plots; never query their enormous combined AABB. */
    private static List<HangingEntity> nearby(ServerLevel level,Collection<BlockPos> blocks,AABB bounds){
        if(bounds.getXsize()<=132&&bounds.getYsize()<=132&&bounds.getZsize()<=132)
            return level.getEntitiesOfClass(HangingEntity.class,bounds,e->!e.isRemoved());
        Map<Long,AABB> regions=new LinkedHashMap<>();
        for(BlockPos pos:blocks){
            long key=BlockPos.asLong(pos.getX()>>5,pos.getY()>>5,pos.getZ()>>5);
            AABB cell=new AABB(pos),previous=regions.get(key);
            regions.put(key,previous==null?cell:previous.minmax(cell));
        }
        Map<java.util.UUID,HangingEntity> unique=new LinkedHashMap<>();
        for(AABB region:regions.values())for(HangingEntity entity:level.getEntitiesOfClass(HangingEntity.class,region.inflate(2),e->!e.isRemoved()))
            unique.put(entity.getUUID(),entity);
        return new ArrayList<>(unique.values());
    }

    @Override
    public void close() {
        for (Attachment attachment : attachments) {
            HangingEntity entity = attachment.entity;
            if (entity.isRemoved() || entity.level() != level || !entity.getPos().equals(attachment.anchor)) continue;
            try {
                boolean changed = attachment.supports.entrySet().stream().anyMatch(entry -> level.isLoaded(entry.getKey())
                        && !level.getBlockState(entry.getKey()).equals(entry.getValue()));
                // Fixed frames survive without backing blocks. Cancelled edits keep their original
                // supports. Neither is discarded; unrelated pre-existing invalid attachments were skipped.
                if (changed && !entity.survives()) SkillBlockDrops.discardAttachment(entity);
            } catch (RuntimeException failure) {
                LogUtils.getLogger().warn("Could not resolve hanging support after terrain edit: {}", entity.getUUID(), failure);
            }
        }
        attachments.clear();
    }

    private static final class SupportGeometry {
        private static final Method METHOD = method();

        private static Method method() {
            try {
                Method method = HangingEntity.class.getDeclaredMethod("calculateSupportBox");
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }
}
