package dev.jujutsuvfx.mixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Mob.class)
public interface MobLeashAccessor {
    @Accessor("leashInfoTag") CompoundTag jujutsuvfx$getLeash();
    @Accessor("leashInfoTag") void jujutsuvfx$setLeash(CompoundTag tag);
}
