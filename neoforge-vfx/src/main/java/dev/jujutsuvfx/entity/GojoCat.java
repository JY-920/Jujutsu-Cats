package dev.jujutsuvfx.entity;

import dev.jujutsuvfx.network.FistImpact;
import dev.jujutsuvfx.network.TechniqueFistImpact;
import dev.jujutsuvfx.server.DomainFreeze;
import dev.jujutsuvfx.server.CatAdaptation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Blue attraction and Red repulsion are Limitless techniques, not ink or Black Flash. */
public final class GojoCat extends SorcererCat {
    public GojoCat(EntityType<? extends TamableAnimal> type, Level level) { super(type, level); }
    @Override public boolean isSukuna() { return false; }

    @Override protected void onPunchHit(ServerLevel level, LivingEntity target, Vec3 direction, int kind) {
        if (kind == PLAIN) return;
        if (kind == CURSED || kind == BLACK_FLASH) { cursedHit(level, target, direction, kind); return; }
        if (kind == BLUE && target.isAlive()) DomainFreeze.brieflyFreeze(target, 10, CatAdaptation.Phenomenon.BLUE);
        if (kind == RED) {
            CatAdaptation.touch(target, CatAdaptation.Phenomenon.RED);
            float response=CatAdaptation.controlScale(target, CatAdaptation.Phenomenon.RED);
            if (response > 0) launchTarget(level, target, direction, 1.65*response, .65*response);
        }
        Vec3 point = target.getBoundingBox().getCenter().subtract(direction.scale(target.getBbWidth() * .4));
        var impact = new FistImpact(getId(), target.getId(), point, direction, kind == RED ? 1 : .8f);
        var packet = new TechniqueFistImpact(impact, kind == RED);
        for (ServerPlayer observer : level.players()) if (dev.jujutsuvfx.server.CinematicAudio.visualRange(observer,point,160)
                && observer.connection.hasChannel(TechniqueFistImpact.TYPE)) PacketDistributor.sendToPlayer(observer, packet);
    }
}
