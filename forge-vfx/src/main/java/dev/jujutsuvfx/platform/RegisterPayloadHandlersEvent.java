package dev.jujutsuvfx.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Registers bounded messages with a separate decoder table for each network direction. */
public final class RegisterPayloadHandlersEvent {
    static final Map<ResourceLocation,Entry<?>> TO_SERVER=new HashMap<>(),TO_CLIENT=new HashMap<>();
    record Entry<T extends CustomPacketPayload>(StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,Context> handler) {}
    public record Context(NetworkEvent.Context context) {
        public ServerPlayer player() { return context.getSender(); }
        public void enqueueWork(Runnable work) { context.enqueueWork(work); }
    }
    public RegisterPayloadHandlersEvent registrar(String version) { return this; }
    public RegisterPayloadHandlersEvent optional() { return this; }
    public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,Context> handler) {
        put(TO_SERVER,type,codec,handler);
    }
    public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,Context> handler) {
        put(TO_CLIENT,type,codec,handler);
    }
    private static <T extends CustomPacketPayload> void put(Map<ResourceLocation,Entry<?>> map,CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,Context> handler) {
        if(map.putIfAbsent(type.id(),new Entry<>(codec,handler))!=null)throw new IllegalStateException("Duplicate packet "+type.id());
    }
}
