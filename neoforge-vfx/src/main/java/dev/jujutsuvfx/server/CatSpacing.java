package dev.jujutsuvfx.server;

import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Keep room for the long cat heads and extended paws, including navigation and residual momentum. */
public final class CatSpacing {
    private CatSpacing(){}
    public static double distance(SorcererCat cat,LivingEntity target){
        return Math.max(cat.isMahoraga() || target instanceof SorcererCat other && other.isMahoraga() ? 2.9 : 2.45,
                (cat.getBbWidth()+target.getBbWidth())*.5+.95);
    }
    public static Vec3 limit(SorcererCat cat,Vec3 step){
        if(cat.level().isClientSide||!cat.isAlive()||CatDuel.pinned(cat)||cat.behavior()==SorcererCat.STILL
                ||!(cat.getTarget() instanceof SorcererCat target)||!target.isAlive()||!cat.isRival(target)
                ||Math.abs(cat.getY()+step.y-target.getY())>Math.max(cat.getBbHeight(),target.getBbHeight())*.65)return step;
        Vec3 offset=new Vec3(cat.getX()-target.getX(),0,cat.getZ()-target.getZ());
        double minimum=distance(cat,target),length=offset.length();
        if(length>minimum+step.horizontalDistance()+.3)return step;
        Vec3 outward=length<.001?new Vec3(cat.getId()<target.getId()?1:-1,0,0):offset.scale(1/length);
        Vec3 next=offset.add(step.x,0,step.z);
        if(length<minimum){
            double inward=step.x*outward.x+step.z*outward.z;
            if(inward<0)step=step.subtract(outward.scale(inward));
            if(!CatSorcery.busy(cat))step=step.add(outward.scale(Math.min(.18,(minimum-length)*.32)));
            return step;
        }
        double a=step.x*step.x+step.z*step.z,b=2*(offset.x*step.x+offset.z*step.z);
        double c=length*length-minimum*minimum,disc=b*b-4*a*c;
        if(a>.000001&&b<0&&disc>=0){
            double first=(-b-Math.sqrt(disc))/(2*a);
            if(first>=0&&first<=1)return new Vec3(step.x*Math.max(0,first-.015),step.y,step.z*Math.max(0,first-.015));
        }
        return next.lengthSqr()<minimum*minimum?new Vec3(0,step.y,0):step;
    }
}
