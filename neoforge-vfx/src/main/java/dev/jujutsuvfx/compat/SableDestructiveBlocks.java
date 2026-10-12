package dev.jujutsuvfx.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Destructive handling of connection blocks, using the installed mod's removal lifecycle. */
final class SableDestructiveBlocks {
    private SableDestructiveBlocks() {}

    static boolean fragile(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.getNamespace().equals("simulated")) return switch (id.getPath()) {
            case "rope_connector", "rope_winch", "spring", "docking_connector", "paired_docking_connector",
                    "swivel_bearing", "swivel_bearing_link_block" -> true;
            default -> false;
        };
        return id.getNamespace().equals("aeronautics") && id.getPath().contains("balloon");
    }

    /** Include only loaded, reciprocal ends that native removal would otherwise destroy implicitly. */
    static Set<BlockPos> pairedEnds(ServerLevel level, Collection<BlockPos> positions) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            BlockPos other = partner(level, be);
            if (other == null || !loaded(level, other)) continue;
            BlockEntity peer = level.getBlockEntity(other);
            if (peer != null && pos.equals(partner(level, peer))) result.add(other.immutable());
        }
        return result;
    }

    static void beforeDestroy(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        String name = be.getClass().getName();
        try {
            if (name.equals("dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity")) {
                // Native remove() blindly destroys its saved Goal (including stale coordinates).
                // Reciprocal, permitted ends are already in the explicit destruction batch.
                be.getClass().getMethod("setPartnerPos", BlockPos.class, java.util.UUID.class).invoke(be, null, null);
            } else if (name.equals("dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity")
                    || name.equals("dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity")) {
                // Keep native invalidation/constraint cleanup; replace implicit partner destruction
                // with the separately checked batch instead of trusting an old remote position.
                be.getClass().getMethod("beforeAssembly").invoke(be);
            } else if (name.equals("dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity")) {
                CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                BlockPos other = NbtUtils.readBlockPos(tag, "OtherConnector").orElse(null);
                if (other != null && loaded(level, other)) {
                    BlockEntity peer = level.getBlockEntity(other);
                    if (peer != null && peer.getClass() == be.getClass()
                            && pos.equals(NbtUtils.readBlockPos(peer.saveWithFullMetadata(level.registryAccess()), "OtherConnector").orElse(null)))
                        peer.getClass().getMethod("unDock").invoke(peer);
                }
                // Removes the physics joint and fluid, energy and wired-network links.
                be.getClass().getMethod("unDock").invoke(be);
            }
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException wrapped ? wrapped.getCause() : failure;
            throw new IllegalStateException("Cannot prepare destructive connector cleanup at " + pos, cause);
        }
    }

    private static BlockPos partner(ServerLevel level, BlockEntity be) {
        String name = be.getClass().getName();
        CompoundTag tag;
        if (name.equals("dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity")) {
            tag = be.saveWithFullMetadata(level.registryAccess());
            return tag.contains("Goal", 4) ? BlockPos.of(tag.getLong("Goal")) : null;
        }
        if (name.equals("dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity")) {
            tag = be.saveWithFullMetadata(level.registryAccess());
            return NbtUtils.readBlockPos(tag, "ParentPos").orElse(null);
        }
        if (name.equals("dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity")) {
            try { return (BlockPos) be.getClass().getMethod("getPlatePos").invoke(be); }
            catch (ReflectiveOperationException failure) {
                LogUtils.getLogger().warn("Could not read swivel plate for destructive cut at {}", be.getBlockPos(), failure);
            }
        }
        return null;
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
