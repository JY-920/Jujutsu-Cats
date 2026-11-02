package dev.jujutsuvfx.entity;
import net.minecraft.nbt.CompoundTag;
/** One tracked clock shared by the body, companion and both afterimages. */
public interface DancingCat {
 CompoundTag danceState();
 void danceState(CompoundTag state);
 void setDanceStyle(int style);
 default int danceStyle(){return danceState().getInt("Style");}
 default float danceAge(float partial){
  var mob=(net.minecraft.world.entity.Mob)this;
  return Math.max(0,(float)(mob.level().getGameTime()-danceState().getLong("Start"))+partial);
 }
}
