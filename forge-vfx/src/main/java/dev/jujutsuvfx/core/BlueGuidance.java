package dev.jujutsuvfx.core;

import net.minecraft.world.phys.Vec3;

/** Player-controlled Blue: a slowly extending radial tether, then a brief free coast. */
public final class BlueGuidance {
    public static final double REACH=12, EXTEND_PER_TICK=.15, MAX_STEP=.95;
    public static final int COAST_TICKS=8;
    private Vec3 point, axis, eye, velocity=Vec3.ZERO;
    private double reach,coastReach;
    private int coast;
    private boolean released;

    public BlueGuidance(Vec3 point,Vec3 eye,Vec3 look){
        this.point=point;this.eye=eye;axis=look.normalize();reach=Math.min(REACH,point.distanceTo(eye));
    }
    public Vec3 next(Vec3 eye,Vec3 look){
        if(released){
            Vec3 desired=point.add(velocity),offset=desired.subtract(this.eye);
            return offset.lengthSqr()>coastReach*coastReach?this.eye.add(offset.normalize().scale(coastReach)):desired;
        }
        this.eye=eye;reach=Math.min(REACH,reach+EXTEND_PER_TICK);
        axis=turn(axis,look.normalize());
        Vec3 desired=eye.add(axis.scale(reach));
        return point.add(limit(desired.subtract(point),MAX_STEP));
    }
    public void moved(Vec3 reached){
        velocity=reached.subtract(point).scale(released?.58:1);point=reached;
        if(released&&coast>0)coast--;
    }
    public void release(){
        if(released)return;released=true;coast=COAST_TICKS;velocity=limit(velocity,.32);
        coastReach=Math.max(REACH,point.distanceTo(eye))+.6;
    }
    public boolean coasting(){return released&&coast>0&&velocity.lengthSqr()>1e-6;}
    private static Vec3 limit(Vec3 delta,double maximum){return delta.lengthSqr()>maximum*maximum?delta.normalize().scale(maximum):delta;}
    private static Vec3 turn(Vec3 from,Vec3 to){
        if(to.lengthSqr()<.5)return from;
        double angle=Math.acos(dev.jujutsuvfx.platform.Numbers.clamp(from.dot(to),-1,1)),step=Math.toRadians(2.2);
        if(angle<=step)return to;
        Vec3 normal=from.cross(to);if(normal.lengthSqr()<1e-6)normal=FurnaceFlight.right(from);
        return from.scale(Math.cos(step)).add(normal.normalize().cross(from).scale(Math.sin(step))).normalize();
    }
}
