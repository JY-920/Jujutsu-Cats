package dev.jujutsuvfx.server;
import dev.jujutsuvfx.entity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
/** One server clock, synchronized with the entity to new and existing tracking clients. */
public final class LivingDance {
 private LivingDance(){}
 public static CompoundTag state(LivingEntity e){return e instanceof LivingDanceState d?d.jujutsu$dance():new CompoundTag();}
 public static int style(LivingEntity e){return e instanceof DancingCat?0:state(e).getInt("Style");}
 public static boolean active(LivingEntity e){return e.isAlive()&&style(e)>0;}
 public static float age(LivingEntity e,float partial){return Math.max(0,e.level().getGameTime()-state(e).getLong("Start")+partial);}
 public static void toggle(LivingEntity e,int style){
  if(e.level().isClientSide||!e.isAlive())return;
  if(e instanceof DancingCat cat){cat.setDanceStyle(cat.danceStyle()==style?0:style);return;}
  if(!(e instanceof LivingDanceState d))return;
  CompoundTag tag=new CompoundTag();
  if(style(e)!=style){
   tag.putInt("Style",style);tag.putLong("Start",e.level().getGameTime());tag.putFloat("Yaw",e.getYRot());
   tag.putDouble("X",e.getX());tag.putDouble("Y",e.getY());tag.putDouble("Z",e.getZ());
   e.stopRiding();
   if(e instanceof Mob m){m.getNavigation().stop();m.setTarget(null);}
  }
  d.jujutsu$dance(tag);
 }
 public static void hold(LivingEntity e){
  if(!active(e))return;
  var t=state(e);
  // Teleportation wins over an old anchor (portals, commands or another mod).
  if(!e.level().isClientSide&&e.distanceToSqr(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"))>16){((LivingDanceState)e).jujutsu$dance(new CompoundTag());return;}
  e.setDeltaMovement(0,0,0);e.xxa=0;e.yya=0;e.zza=0;e.setSpeed(0);
  float yaw=t.getFloat("Yaw");e.setYRot(yaw);e.yRotO=yaw;e.yBodyRot=e.yBodyRotO=yaw;e.yHeadRot=e.yHeadRotO=yaw;e.setXRot(0);e.xRotO=0;
  if(!e.level().isClientSide){e.setPos(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));if(e instanceof Mob m){m.getNavigation().stop();m.setTarget(null);}}
 }
}
