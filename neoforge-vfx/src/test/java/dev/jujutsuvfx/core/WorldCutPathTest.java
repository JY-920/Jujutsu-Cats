package dev.jujutsuvfx.core;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public final class WorldCutPathTest {
 public static void main(String[] args){
  Vec3 from=new Vec3(0,1,0),to=from.add(0,0,48);
  for(int z:new int[]{2,9,24,47})if(!WorldCutPath.intersects(new AABB(-.5,0,z-.5,.5,2,z+.5),from,to))throw new AssertionError("Piercing path lost target at "+z);
  if(!WorldCutPath.intersects(new AABB(-3,-3,-3,3,3,3),from,to))throw new AssertionError("Enclosing target lost");
  if(WorldCutPath.intersects(new AABB(3,0,20,4,2,21),from,to))throw new AssertionError("Side target hit");
  if(WorldCutPath.intersects(new AABB(-.5,0,50,.5,2,51),from,to))throw new AssertionError("Out of range hit");
  if(WorldCutPath.intersects(new AABB(-.5,0,-4,.5,2,-3),from,to))throw new AssertionError("Target behind hit");
  System.out.println("Space slash: multiple path hits, enclosing target, side/rear/range rejection passed");
 }
}
