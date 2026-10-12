package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Confirmed collision feedback, emitted once per blade after at least one successful hurt call. */
public record BladeImpact(int session,Vec3 point,Vec3 direction) implements CustomPacketPayload {
    public static final Type<BladeImpact> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"blade_impact_v7"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BladeImpact> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.session);write(b,p.point);write(b,p.direction);
    },b->new BladeImpact(b.readVarInt(),read(b),read(b)));
    private static void write(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3 read(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
