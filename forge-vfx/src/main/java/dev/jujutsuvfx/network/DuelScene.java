package dev.jujutsuvfx.network;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.network.FriendlyByteBuf;
import dev.jujutsuvfx.platform.StreamCodec;
import dev.jujutsuvfx.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Server-authored shot cue. It never permits clients to change combat or another player's camera. */
public record DuelScene(long duel,int gojo,int sukuna,int mahoraga,int phase,int age,int duration,
                        Vec3 focus,double radius,int flags) implements CustomPacketPayload {
    public static final int NONE=0,OPENING_CHARGE=1,OPENING_GUARD=2,OPENING_RETURN=3,
            DOMAIN_GOJO=4,DOMAIN_SUKUNA=5,DOMAIN_CLASH=6,DOMAIN_GOJO_WIN=7,DOMAIN_SUKUNA_WIN=8,
            WHEEL_SUMMON=9,MAHORAGA_DESCENT=10,ARM_CUT=11,ARM_RECOVER=12,
            FINALE_BLUE=13,FINALE_COMBAT=14,FINALE_RED=15,FINALE_FUSION=16,FINALE_BLAST=17,MAHORAGA_STRIKE=18,BATTLE=19,APPROACH=20,STRUCTURE_CLASH=21,
            DOMAIN_BREAK=22,SOLO_GOJO=23,SOLO_HOLD=24,RESCUE_WHEEL=25,RESCUE_EMERGE=26,RESCUE_CUT=27,RESCUE_EXIT=28;
    public static final int SLOW_MOTION=1;
    public static final Type<DuelScene> TYPE=new Type<>(new ResourceLocation(JujutsuVfx.ID,"duel_scene_v1"));
    public static final StreamCodec<FriendlyByteBuf,DuelScene> CODEC=StreamCodec.of(
            (b,p)->{b.writeLong(p.duel);b.writeVarInt(p.gojo);b.writeVarInt(p.sukuna);b.writeVarInt(p.mahoraga);
                b.writeVarInt(p.phase);b.writeVarInt(p.age);b.writeVarInt(p.duration);
                b.writeDouble(p.focus.x);b.writeDouble(p.focus.y);b.writeDouble(p.focus.z);
                b.writeDouble(p.radius);b.writeVarInt(p.flags);},
            b->new DuelScene(b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),
                    b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readDouble(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
