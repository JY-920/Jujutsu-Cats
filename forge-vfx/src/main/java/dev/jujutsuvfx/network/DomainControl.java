package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import dev.jujutsuvfx.platform.RegisterPayloadHandlersEvent;

public record DomainControl(int action, int session, double x, double y, double z, float radius, int ticks, int kind, Vec3 direction)
        implements CustomPacketPayload {
    public static final int START=0, STOP=1, CLOSE=3, FINISH=4, START_TIMED=5;
    public static final int DEFAULT_SECONDS=5;
    public static final int DEFAULT_RADIUS=40;
    public static final int MAX_RADIUS=50;
    public static final int MAX_MARKED_TARGETS=128; // Visual budget only; never a damage/freeze limit.
    public static final int CLOSE_TICKS=26;
    public static final int CLASH_TICKS=200;
    public static final float SLASH_DAMAGE=50f;
    // A new optional channel also lets older clients/servers fall back to visual-only operation.
    public static final Type<DomainControl> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"domain_control_v8"));
    public static final StreamCodec<FriendlyByteBuf,DomainControl> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.action);b.writeVarInt(p.session);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);
                b.writeFloat(p.radius);b.writeVarInt(p.ticks);b.writeVarInt(p.kind);b.writeDouble(p.direction.x);b.writeDouble(p.direction.y);b.writeDouble(p.direction.z);},
            b->new DomainControl(b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readVarInt(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static DomainControl start(int session,Vec3 p,float radius,float seconds,int kind,Vec3 direction){return new DomainControl(START,session,p.x,p.y,p.z,radius,Math.round(seconds*20),kind,direction);}
    /** Explicit duration from an operator command; ordinary item casts use server configuration. */
    public DomainControl timed(){return new DomainControl(START_TIMED,session,x,y,z,radius,ticks,kind,direction);}
    public static DomainControl stop(int session){return new DomainControl(STOP,session,0,0,0,0,0,-1,Vec3.ZERO);}
    public static DomainControl close(int session){return new DomainControl(CLOSE,session,0,0,0,0,0,-1,Vec3.ZERO);}
    public static DomainControl finish(int session){return new DomainControl(FINISH,session,0,0,0,0,0,-1,Vec3.ZERO);}
    public static void register(IEventBus bus){
        registerPayloads(new RegisterPayloadHandlersEvent());
        dev.jujutsuvfx.platform.PacketDistributor.register();
    }
    private static void registerPayloads(RegisterPayloadHandlersEvent event){
        event.registrar("1").optional().playToClient(DuelScene.TYPE,DuelScene.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.DuelScenes.receive(packet)));
        event.registrar("2").optional().playToServer(VfxSettingsRequest.TYPE,VfxSettingsRequest.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.server.TechniqueTerrainRules.handle(packet,context.player())));
        event.registrar("2").optional().playToClient(VfxSettingsState.TYPE,VfxSettingsState.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.VfxConfigScreen.receive(packet)));
        event.registrar("1").optional().playToServer(DamageSettingsRequest.TYPE,DamageSettingsRequest.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.server.TechniqueDamageSettings.handle(packet,context.player())));
        event.registrar("1").optional().playToClient(DamageSettingsState.TYPE,DamageSettingsState.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.TechniqueDamageScreen.receive(packet)));
        event.registrar("1").optional().playToServer(CameraAudio.TYPE,CameraAudio.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.server.CinematicAudio.handle(packet,context.player())));
        event.registrar("1").optional().playToServer(DomainSettingsRequest.TYPE,DomainSettingsRequest.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.server.DomainDurationSettings.handle(packet,context.player())));
        event.registrar("1").optional().playToClient(DomainSettingsState.TYPE,DomainSettingsState.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.DomainSettingsScreen.receive(packet)));
        FurnaceControl.registerPayloads(event);
        TechniqueControl.registerPayloads(event);
        FistImpact.registerPayloads(event);
        SukunaCut.registerPayloads(event);
        event.registrar("1").optional().playToServer(GojoCatEdit.TYPE,GojoCatEdit.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.server.GojoCatControl.edit(packet,context.player())));
        event.registrar("1").optional().playToClient(GojoCatView.TYPE,GojoCatView.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.GojoCatScreen.receive(packet)));
        event.registrar("1").optional().playToClient(ShrineFocus.TYPE,ShrineFocus.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.ClientShrineFocus.receive(packet)));
        var registrar=event.registrar("8").optional();
        registrar.playToServer(TYPE,CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.server.DomainFreeze.handle(packet,context.player())));
        registrar.playToClient(DomainState.TYPE,DomainState.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.ClientDomainControl.receive(packet)));
        registrar.playToClient(EntityStasis.TYPE,EntityStasis.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.ClientEntityStasis.receive(packet)));
        registrar.playToClient(BladeBatch.TYPE,BladeBatch.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.ClientDomainControl.receive(packet)));
        registrar.playToClient(BladeImpact.TYPE,BladeImpact.CODEC,(packet,context)->
                context.enqueueWork(()->dev.jujutsuvfx.client.ClientDomainControl.receive(packet)));
    }
}
