package dev.jujutsuvfx.server;

import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static dev.jujutsuvfx.core.CatCombatMoves.SPACE_BLINK;

/** Awareness survives occlusion. Pursuit only runs between committed combat actions. */
final class CatPursuit {
    private final SorcererCat cat;
    private UUID targetId;
    private Vec3 previous;
    private int sampledAt, unseenTicks, stalledTicks, blinkSearchAt, pathAt;

    CatPursuit(SorcererCat cat) { this.cat = cat; }

    void reset() {
        targetId = null; previous = null; unseenTicks = stalledTicks = 0;
        // Neither failed-search backoff nor the actor-owned blink cooldown resets with an attack.
    }

    boolean needed(LivingEntity target) {
        if (!cat.canFight(target) || cat.distanceToSqr(target) > SorcererCat.RIVAL_RANGE * SorcererCat.RIVAL_RANGE) {
            reset(); return false;
        }
        boolean changed = !target.getUUID().equals(targetId);
        if (changed || cat.tickCount - sampledAt > 3) {
            previous = null; unseenTicks = stalledTicks = 0;
            if (changed) pathAt = 0;
        }
        targetId = target.getUUID(); sampledAt = cat.tickCount;
        boolean visible = cat.getSensing().hasLineOfSight(target);
        unseenTicks = visible ? 0 : unseenTicks + 1;
        stalledTicks = previous != null && cat.position().distanceToSqr(previous) < .0064 ? stalledTicks + 1 : 0;
        previous = cat.position();
        double reach = spacing(cat, target) - .05;
        return !visible || stalledTicks >= 24 && cat.distanceToSqr(target) > reach * reach;
    }

    CatTactics.Maneuver blink(ServerLevel level, LivingEntity target) {
        if (!cat.canCombatBlink() || cat.tickCount < blinkSearchAt || unseenTicks < 8 && stalledTicks < 24) return null;
        blinkSearchAt = cat.tickCount + 20;
        Vec3 destination = blinkDestination(cat, level, target);
        return destination == null ? null : new CatTactics.Maneuver(cat, target, destination, SPACE_BLINK, 12, false).pursuing();
    }

    /** Correct node-centre rounding when navigation stops just outside the extended paws. */
    boolean closeGap(ServerLevel level,LivingEntity target){
        if(!cat.onGround()||cat.combatHovering()||!cat.getSensing().hasLineOfSight(target)
                ||Math.abs(cat.getY()-target.getY())>1.2||cat.distanceToSqr(target)>8*8)return false;
        Vec3 delta=target.position().subtract(cat.position()).multiply(1,0,1);
        double distance=delta.length(),gap=target instanceof SorcererCat?CatSpacing.distance(cat,target)+.08:spacing(cat,target);
        if(distance<=gap||distance<.001)return false;
        Vec3 step=delta.scale(Math.min(.28,distance-gap)/distance),next=cat.position().add(step);
        Vec3 floor=CatTactics.supported(cat,level,next);
        if(floor==null||Math.abs(floor.y-cat.getY())>.5||!CatTactics.airRoute(cat,level,cat.position(),next))return false;
        cat.getNavigation().stop();cat.setDeltaMovement(step.add(0,cat.getDeltaMovement().y,0));
        cat.hasImpulse=true;cat.hurtMarked=true;return true;
    }
    void advance(ServerLevel level, LivingEntity target) {
        if(closeGap(level,target))return;
        // A partial tunnel is useful progress even before a full path to the enemy exists.
        if (CatTerrain.mover(cat) && breach(cat, level, target)) {
            pathAt = cat.tickCount + 2; return;
        }
        if (cat.tickCount >= pathAt) {
            pathAt = cat.tickCount + 10;
            CatArena.navigate(cat, target, cat.isSukuna() ? 1.3 : 1.16);
        }
    }

    static double spacing(SorcererCat cat, LivingEntity target) {
        return target instanceof SorcererCat ? CatSpacing.distance(cat, target) + .25
                : Math.max(2.05, (cat.getBbWidth() + target.getBbWidth()) * .5 + .65);
    }

    /** Recomputed on the actual teleport frame, so a moving target cannot occupy an old landing point. */
    static Vec3 blinkDestination(SorcererCat cat, ServerLevel level, LivingEntity target) {
        if (!cat.canFight(target) || target.level() != level
                || cat.distanceToSqr(target) > SorcererCat.RIVAL_RANGE * SorcererCat.RIVAL_RANGE) return null;
        Vec3 start = cat.position(), end = target.position(), difference = end.subtract(start);
        double distance = difference.length(), gap = spacing(cat, target);
        boolean terminal = distance <= 48;
        Vec3 center = terminal ? end : start.add(difference.normalize().scale(46));
        Vec3 back = start.subtract(end).multiply(1, 0, 1).normalize();
        if (back.lengthSqr() < .01) back = new Vec3(0, 0, 1);
        Vec3 best = null; double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 24; i++) {
            float angle = (float) ((i % 8) * Math.PI / 4);
            double radius = terminal ? gap + (i / 8) * 1.25 : 1 + (i / 8) * 2;
            Vec3 desired = center.add(back.yRot(angle).scale(radius));
            Vec3 point = CatTactics.supported(cat, level, desired);
            if (point == null) continue;
            double travel = point.distanceTo(start), remaining = point.distanceTo(end);
            if (travel < 1.5 || travel > 52 || remaining < gap - .2) continue;
            var body = cat.getBoundingBox().move(point.subtract(start));
            if (body.intersects(target.getBoundingBox().inflate(.55, .10, .55))) continue;
            boolean visible = CatTactics.sight(cat, level, point, target);
            // Close pursuit reappears beside the enemy. Long pursuit uses loaded, supported waypoints.
            if (terminal ? !visible || remaining > distance + 3 : remaining > distance - 8) continue;
            double score = (visible ? 8 : 0) - remaining - travel * .04 - Math.abs(point.y - end.y) * .10;
            if (score > bestScore) { bestScore = score; best = point; }
        }
        return best;
    }

    /** Break only the next body-sized corridor; movement still requires a clear, supported route. */
    static boolean breach(SorcererCat cat, ServerLevel level, LivingEntity target) {
        if (!CatTerrain.mover(cat) || !cat.canFight(target) || !cat.onGround() || cat.combatHovering()
                || cat.isPassenger() || cat.isInWaterOrBubble() || cat.isInLava()) return false;
        Vec3 start = cat.position(), offset = target.position().subtract(start).multiply(1, 0, 1);
        double gap = spacing(cat, target), distance = offset.length();
        if (distance < .1) return false;
        boolean close = Math.abs(target.getY() - cat.getY()) < cat.getBbHeight() && distance <= gap;
        if (close && cat.getSensing().hasLineOfSight(target)) return false;
        Vec3 direction = offset.normalize(), probe = start.add(direction.scale(1.25));
        // No speculative destruction out in the open; ordinary pathfinding handles that leg.
        if (CatTactics.empty(cat, level, probe)) return false;
        CatTerrain.clearStep(cat, start, probe);
        if (close) return false; // Clear a wall between adjacent opponents without pushing their heads together.
        double speed = Math.min(.58, Math.max(.1, distance - gap));
        Vec3 next = start.add(direction.scale(speed));
        if (!CatArena.walkable(cat, level, next)
                || !CatTactics.airRoute(cat, level, start.add(0, .02, 0), next.add(0, .02, 0))) return false;
        cat.getNavigation().stop();
        cat.setDeltaMovement(direction.scale(speed).add(0, Math.min(0, cat.getDeltaMovement().y), 0));
        cat.hasImpulse = true; cat.hurtMarked = true;
        if (cat.tickCount % 3 == 0)
            level.getChunkSource().broadcastAndSend(cat, new ClientboundSetEntityMotionPacket(cat));
        return true;
    }
}
