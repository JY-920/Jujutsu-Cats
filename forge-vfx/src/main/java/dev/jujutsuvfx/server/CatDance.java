package dev.jujutsuvfx.server;
import dev.jujutsuvfx.entity.DancingCat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
/** Debug playback has no hitboxes or fake mobs. Leaving it restores the existing AI setting. */
public final class CatDance {
 private CatDance(){}
 public static void start(Mob mob,int style){
  if(mob.level().isClientSide||!(mob instanceof DancingCat cat)||style<0||style>2)return;
  var tag=new CompoundTag();
  if(style>0&&mob.isAlive()&&!mob.isPassenger()&&!mob.isInWaterOrBubble()&&!mob.isInLava()){
   tag.putInt("Style",style);tag.putLong("Start",mob.level().getGameTime());tag.putFloat("Yaw",mob.getYRot());
   tag.putDouble("X",mob.getX());tag.putDouble("Y",mob.getY());tag.putDouble("Z",mob.getZ());
   mob.setTarget(null);mob.getNavigation().stop();
  }
  cat.danceState(tag);hold(mob);
 }
 public static boolean hold(Mob mob){
  if(!(mob instanceof DancingCat cat)||cat.danceStyle()==0)return false;
  if(!mob.isAlive()){if(!mob.level().isClientSide)cat.danceState(new CompoundTag());return false;}
  var tag=cat.danceState();float yaw=tag.getFloat("Yaw");
  mob.setDeltaMovement(Vec3.ZERO);mob.setSpeed(0);mob.setXxa(0);mob.setYya(0);mob.setZza(0);
  mob.setYRot(yaw);mob.yRotO=yaw;mob.yBodyRot=mob.yBodyRotO=yaw;mob.setYHeadRot(yaw);mob.yHeadRotO=yaw;mob.setXRot(0);mob.xRotO=0;
  if(!mob.level().isClientSide){mob.setTarget(null);mob.getNavigation().stop();mob.setPos(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));}
  return true;
 }
 public static float loopAge(float age){return age<10?age:10+(age-10)%dev.jujutsuvfx.core.YutaDanceMotion.LOOP;}
}
