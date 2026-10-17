package dev.jujutsuvfx.core;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Includes an enclosing target: vanilla AABB.clip alone only reports an entry from outside. */
public final class WorldCutPath {
 private WorldCutPath(){}
 public static boolean intersects(AABB bounds,Vec3 from,Vec3 to){
  AABB expanded=bounds.inflate(.55);
  return expanded.contains(from)||expanded.clip(from,to).isPresent();
 }
}
