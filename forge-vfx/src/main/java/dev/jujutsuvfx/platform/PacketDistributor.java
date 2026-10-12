package dev.jujutsuvfx.platform;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class PacketDistributor {
    // This envelope has no per-payload negotiation: older peers cannot decode damage settings.
    private static final String VERSION="1025-forge-1";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(JujutsuVfx.ID,"spells"),
            ()->VERSION,VERSION::equals,VERSION::equals);
    private record ServerPacket(CustomPacketPayload payload) {}
    private record ClientPacket(CustomPacketPayload payload) {}
    private PacketDistributor() {}
    public static void register() {
        CHANNEL.registerMessage(0,ServerPacket.class,(p,b)->encode(p.payload,b,RegisterPayloadHandlersEvent.TO_SERVER),
                b->new ServerPacket(decode(b,RegisterPayloadHandlersEvent.TO_SERVER)),
                (p,c)->handle(p.payload,c,RegisterPayloadHandlersEvent.TO_SERVER),Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,ClientPacket.class,(p,b)->encode(p.payload,b,RegisterPayloadHandlersEvent.TO_CLIENT),
                b->new ClientPacket(decode(b,RegisterPayloadHandlersEvent.TO_CLIENT)),
                (p,c)->handle(p.payload,c,RegisterPayloadHandlersEvent.TO_CLIENT),Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> RegisterPayloadHandlersEvent.Entry<T> entry(ResourceLocation id,Map<ResourceLocation,RegisterPayloadHandlersEvent.Entry<?>> table) {
        var entry=table.get(id);if(entry==null)throw new IllegalArgumentException("Unsupported packet direction/type: "+id);
        return (RegisterPayloadHandlersEvent.Entry<T>)entry;
    }
    private static void encode(CustomPacketPayload payload,FriendlyByteBuf buffer,Map<ResourceLocation,RegisterPayloadHandlersEvent.Entry<?>> table) {
        buffer.writeResourceLocation(payload.type().id());entry(payload.type().id(),table).codec().encode(buffer,payload);
    }
    private static CustomPacketPayload decode(FriendlyByteBuf buffer,Map<ResourceLocation,RegisterPayloadHandlersEvent.Entry<?>> table) {
        if(buffer.readableBytes()>1_048_576)throw new IllegalArgumentException("Oversized technique packet");
        return entry(buffer.readResourceLocation(),table).codec().decode(buffer);
    }
    private static void handle(CustomPacketPayload payload,Supplier<NetworkEvent.Context> supplied,Map<ResourceLocation,RegisterPayloadHandlersEvent.Entry<?>> table) {
        var context=supplied.get();
        // Retain receipt-time sampling for Purple; each handler queues world work itself.
        entry(payload.type().id(),table).handler().accept(payload,new RegisterPayloadHandlersEvent.Context(context));
        context.setPacketHandled(true);
    }
    public static boolean hasChannel(ServerGamePacketListenerImpl connection,CustomPacketPayload.Type<?> type) {
        return connection!=null&&CHANNEL.isRemotePresent(connection.connection);
    }
    public static boolean hasChannel(Object listener,CustomPacketPayload.Type<?> type) {
        return listener instanceof ConnectionAccess access&&CHANNEL.isRemotePresent(access.jujutsuvfx$connection());
    }
    public static void sendToPlayer(ServerPlayer player,CustomPacketPayload payload) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new ClientPacket(payload));
    }
    public static void sendToServer(CustomPacketPayload payload) { CHANNEL.sendToServer(new ServerPacket(payload)); }
}
