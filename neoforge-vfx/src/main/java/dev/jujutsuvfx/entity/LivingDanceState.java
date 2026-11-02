package dev.jujutsuvfx.entity;
import net.minecraft.nbt.CompoundTag;
/** Transient, tracked dance data. Not serialized to the creature's saved AI state. */
public interface LivingDanceState {
 CompoundTag jujutsu$dance();
 void jujutsu$dance(CompoundTag state);
}
