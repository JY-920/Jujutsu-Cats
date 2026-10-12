package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A cast-specific server result; delayed results cannot close a newer domain. */
public record DomainState(int session,int phase,int remainingTicks,int peerSession) implements CustomPacketPayload {
    public static final int SETTLED=0, CANCELED=1, ACCEPTED=2, CLASH=3;
    public DomainState(int session,int phase){this(session,phase,0,-1);}
    public static final Type<DomainState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID,"domain_state_v7"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DomainState> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.session);b.writeVarInt(p.phase);b.writeVarInt(p.remainingTicks);b.writeVarInt(p.peerSession);},
            b->new DomainState(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
