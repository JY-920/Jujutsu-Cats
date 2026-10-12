package dev.jujutsuvfx.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A suspended entity clock and pose. Momentum is saved rather than discarded. */
public final class FrozenPose {
    public final Vec3 position,velocity;
    public final float yaw,pitch,fallDistance;
    private final int ticks;
    private final float head,body,attack;

    public FrozenPose(Entity entity){
        position=entity.position();velocity=entity.getDeltaMovement();
        yaw=entity.getYRot();pitch=entity.getXRot();fallDistance=entity.fallDistance;ticks=entity.tickCount;
        if(entity instanceof LivingEntity living){head=living.yHeadRot;body=living.yBodyRot;attack=living.attackAnim;}
        else {head=body=attack=0;}
    }
    public void hold(Entity entity){
        entity.setPos(position);entity.setYRot(yaw);entity.setXRot(pitch);
        entity.setOldPosAndRot();entity.tickCount=ticks;
        entity.setDeltaMovement(Vec3.ZERO);entity.fallDistance=fallDistance;
        if(entity instanceof LivingEntity living){
            living.yHeadRot=living.yHeadRotO=head;living.yBodyRot=living.yBodyRotO=body;
            living.attackAnim=living.oAttackAnim=attack;
        }
    }
    public void release(Entity entity){
        if(entity.isRemoved()||!entity.isAlive())return;
        entity.setDeltaMovement(velocity);entity.fallDistance=fallDistance;entity.hasImpulse=true;
    }
}
