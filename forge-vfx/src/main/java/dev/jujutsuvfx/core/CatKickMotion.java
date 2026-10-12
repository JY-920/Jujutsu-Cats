package dev.jujutsuvfx.core;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded leading and actual swept foot contact, shared by every cat's two kick moves. */
public final class CatKickMotion {
    private CatKickMotion() {}
    public static Vec3 lead(Vec3 point,Vec3 velocity,double ticks){
        Vec3 offset=velocity.scale(ticks);
        if(offset.lengthSqr()>2.25)offset=offset.normalize().scale(1.5);
        return point.add(offset);
    }
    public static Vec3 approach(Vec3 from,Vec3 goal,double speed,double vertical){
        Vec3 delta=goal.subtract(from),flat=new Vec3(delta.x,0,delta.z);
        if(flat.lengthSqr()>speed*speed)flat=flat.normalize().scale(speed);
        return new Vec3(flat.x,Math.max(-vertical,Math.min(vertical,delta.y*.55)),flat.z);
    }
    public static boolean touches(Vec3 previous,Vec3 current,double height,double width,Vec3 facing,AABB target){
        Vec3 toward=new Vec3(facing.x,0,facing.z).normalize();
        Vec3 hip=current.add(0,height*.45,0),oldHip=previous.add(0,height*.45,0);
        Vec3 foot=toward.scale(1.65+width*.35);
        double radius=.5+width*.25;
        // Check the travelled foot and the leg at this frame, never an untravelled future step.
        return SweptSphere.distanceSquared(oldHip.add(foot),hip.add(foot),target)<=radius*radius
                ||SweptSphere.distanceSquared(hip,hip.add(foot),target)<=radius*radius;
    }
}
