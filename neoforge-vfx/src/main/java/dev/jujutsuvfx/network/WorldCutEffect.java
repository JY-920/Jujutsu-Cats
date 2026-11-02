package dev.jujutsuvfx.network;
import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
/** target -1 launches a traveling blade; nonnegative targets capture a contact before removal. */
public record WorldCutEffect(int target,UUID uuid,Vec3 center,Vec3 forward,Vec3 stroke,int seed,float bladeLength) implements CustomPacketPayload {
 public static final Type<WorldCutEffect> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"world_cut_effect_v2"));
 public static final StreamCodec<RegistryFriendlyByteBuf,WorldCutEffect> CODEC=StreamCodec.of((b,p)->{
  b.writeVarInt(p.target);b.writeUUID(p.uuid);write(b,p.center);write(b,p.forward);write(b,p.stroke);b.writeInt(p.seed);b.writeFloat(p.bladeLength);
 },b->new WorldCutEffect(b.readVarInt(),b.readUUID(),read(b),read(b),read(b),b.readInt(),b.readFloat()));
 private static void write(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
 private static Vec3 read(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
 public boolean valid(){return finite(center)&&finite(forward)&&finite(stroke)&&forward.lengthSqr()>.9&&forward.lengthSqr()<1.1&&stroke.lengthSqr()>.9&&stroke.lengthSqr()<1.1&&Math.abs(forward.dot(stroke))<.02&&Float.isFinite(bladeLength)&&bladeLength>=1&&bladeLength<=96;}
 private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
