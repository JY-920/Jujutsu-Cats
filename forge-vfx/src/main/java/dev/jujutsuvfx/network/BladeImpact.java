package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Confirmed collision feedback, emitted once per blade after at least one successful hurt call. */
public record BladeImpact(int session,Vec3 point,Vec3 direction) implements CustomPacketPayload {
    public static final Type<BladeImpact> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"blade_impact_v7"));
    public static final StreamCodec<FriendlyByteBuf,BladeImpact> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.session);write(b,p.point);write(b,p.direction);
    },b->new BladeImpact(b.readVarInt(),read(b),read(b)));
    private static void write(FriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3 read(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
