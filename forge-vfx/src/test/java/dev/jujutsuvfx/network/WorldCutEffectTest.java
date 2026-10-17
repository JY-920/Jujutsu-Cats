package dev.jujutsuvfx.network;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
public final class WorldCutEffectTest {
 public static void main(String[] args){
  for(int id:new int[]{-1,0,19234}){
   var original=new WorldCutEffect(id,UUID.randomUUID(),new Vec3(-237,80.5,120),new Vec3(0,0,1),new Vec3(.6,.8,0),17,24);
   if(!original.valid())throw new AssertionError("Valid cut rejected");
   var buffer=new FriendlyByteBuf(Unpooled.buffer());
   try{WorldCutEffect.CODEC.encode(buffer,original);if(!original.equals(WorldCutEffect.CODEC.decode(buffer))||buffer.isReadable())throw new AssertionError("Cut wire fields lost");}finally{buffer.release();}
  }
  for(Vec3 v:new Vec3[]{Vec3.ZERO,new Vec3(Double.NaN,0,1),new Vec3(Double.POSITIVE_INFINITY,0,1),new Vec3(0,0,5)}){
   if(new WorldCutEffect(0,UUID.randomUUID(),Vec3.ZERO,v,new Vec3(1,0,0),0,24).valid())throw new AssertionError("Invalid direction accepted");
  }
  for(float size:new float[]{0,97,Float.NaN,Float.POSITIVE_INFINITY})if(new WorldCutEffect(0,UUID.randomUUID(),Vec3.ZERO,new Vec3(0,0,1),new Vec3(1,0,0),0,size).valid())throw new AssertionError("Invalid length accepted");
  if(new WorldCutEffect(0,UUID.randomUUID(),Vec3.ZERO,new Vec3(0,0,1),new Vec3(0,0,1),0,24).valid())throw new AssertionError("Parallel plane accepted");
  System.out.println("World cut packet: wire round trips and invalid geometry rejected");
 }
}
