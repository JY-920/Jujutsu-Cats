package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.SlashBlade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Server -> player caster or NPC-domain observers. Clients never choose damaging blade frames. */
public record BladeBatch(int session,long gameTick,List<SlashBlade> blades) implements CustomPacketPayload {
    public static final Type<BladeBatch> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"blade_batch_v7"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BladeBatch> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.session);b.writeLong(p.gameTick);b.writeVarInt(p.blades.size());
        for(SlashBlade blade:p.blades){
            b.writeVarInt(blade.id());write(b,blade.center());write(b,blade.right());write(b,blade.up());
            b.writeFloat(blade.length());b.writeFloat(blade.lifetime());b.writeFloat(blade.seed());b.writeVarInt(blade.delay());b.writeBoolean(blade.persistent());
        }
    },b->{
        int session=b.readVarInt();long tick=b.readLong();int count=b.readVarInt();
        if(count<0||count>64)throw new IllegalArgumentException("Invalid blade batch size");
        List<SlashBlade> blades=new ArrayList<>(count);
        for(int i=0;i<count;i++)blades.add(new SlashBlade(b.readVarInt(),read(b),read(b),read(b),b.readFloat(),b.readFloat(),b.readFloat(),b.readVarInt(),b.readBoolean()));
        return new BladeBatch(session,tick,List.copyOf(blades));
    });
    private static void write(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3 read(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
