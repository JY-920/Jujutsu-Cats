package dev.jujutsuvfx.core;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

/** One authoritative blade frame, used for both the rendered slit and its short swept hit volume. */
public record SlashBlade(int id,Vec3 center,Vec3 right,Vec3 up,float length,float lifetime,float seed,
                         int delay,boolean persistent) {
    public static final int WAVE_TICKS=3,LEAD_TICKS=2,STRIKE_TICKS=2;
    private static final Vec3[] WORLD_AXES={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
    public Vec3 normal(){return right.cross(up).normalize();}
    public static float halfWidth(float length){return Math.clamp(length*.017f,.12f,.42f);}
    public static int count(float radius,int density){return Math.clamp((int)Math.ceil(3*(radius/16)*(radius/16)*density/4.0),2,48);}
    public static float ambientLength(float radius,Random random){
        float low=Math.clamp(radius*.38f,4.5f,9f),high=Math.clamp(radius*1.30f,12f,34f);
        return low+(high-low)*(float)Math.pow(random.nextFloat(),.72);
    }
    public static float materialSeed(Random random){
        // Keep each palette safely inside one byte-quantized quarter of the fractional seed.
        return random.nextInt(100)+random.nextInt(4)*.25f+.035f+random.nextFloat()*.18f;
    }
    public static SlashBlade create(int id,Vec3 aim,Random random,float length,float lifetime,int delay,boolean persistent){
        double azimuth=id*2.399963229728653+random.nextDouble()*.45,elevation=.18+random.nextDouble()*.9;
        Vec3 normal=new Vec3(Math.cos(azimuth)*Math.cos(elevation),Math.sin(elevation),Math.sin(azimuth)*Math.cos(elevation));
        Vec3 baseRight=WORLD_AXES[1].cross(normal).normalize(),baseUp=normal.cross(baseRight).normalize();
        double roll=(id%3-1)*Math.PI/3+(random.nextDouble()-.5)*.6;
        Vec3 right=baseRight.scale(Math.cos(roll)).add(baseUp.scale(-Math.sin(roll)));
        Vec3 up=baseRight.scale(Math.sin(roll)).add(baseUp.scale(Math.cos(roll)));
        return new SlashBlade(id,aim,right,up,length,lifetime,materialSeed(random),delay,persistent);
    }
    public static SlashBlade crossArm(int id,SlashBlade first,Random random,int delay,boolean persistent){
        return new SlashBlade(id,first.center,first.up.scale(-1),first.right,
                first.length*(.62f+random.nextFloat()*.48f),first.lifetime,first.seed,delay,persistent);
    }
    public static Vec3 targetPoint(AABB box,Vec3 domainCenter,float radius){
        Vec3 middle=box.getCenter();
        if(middle.distanceToSqr(domainCenter)<radius*radius)return middle;
        Vec3 nearest=new Vec3(Math.clamp(domainCenter.x,box.minX,box.maxX),
                Math.clamp(domainCenter.y,box.minY,box.maxY),Math.clamp(domainCenter.z,box.minZ,box.maxZ));
        // Aim in the part of a large or edge-straddling creature that is actually covered by the domain.
        double low=0,high=1;
        for(int i=0;i<16;i++){
            double t=(low+high)*.5;
            if(nearest.lerp(middle,t).distanceToSqr(domainCenter)<radius*radius)low=t;else high=t;
        }
        return nearest.lerp(middle,low*.65);
    }
    public AABB bounds(){
        Vec3 a=center.add(up.scale(length*.5)),b=center.add(up.scale(-length*.5));
        return new AABB(a,b).inflate(halfWidth(length)+.04);
    }
    public boolean hits(AABB target,float fromAge,float toAge,Vec3 domainCenter,float radius){
        // The shader reveals +Y towards -Y, with the bright tip just behind progress * 1.10.
        double from=Math.clamp(RiftShape.cutProgress(fromAge)*1.10-.025,0,1);
        double to=Math.clamp(RiftShape.cutProgress(toAge)*1.10-.025,0,1);
        if(to<=from)return false;
        double top=length*(.5-from),bottom=length*(.5-to);
        Vec3 relative=center.subtract(domainCenter);
        double along=relative.dot(up),disc=along*along+radius*radius-relative.lengthSqr();
        if(disc<=0)return false;
        double root=Math.sqrt(disc);
        bottom=Math.max(bottom,-along-root);top=Math.min(top,-along+root);
        if(top<=bottom)return false;
        Vec3 normal=normal();double width=halfWidth(length);
        if(!intersects(target,center.add(up.scale((top+bottom)*.5)),right,up,normal,width*1.08+.004,(top-bottom)*.5,.025))return false;
        int segments=Math.max(1,(int)Math.ceil((top-bottom)/1.0));
        for(int i=0;i<segments;i++){
            double low=bottom+(top-bottom)*i/segments,high=bottom+(top-bottom)*(i+1)/segments;
            float y0=(float)(low*2/length),y1=(float)(high*2/length),mid=(y0+y1)*.5f;
            double profile=Math.max(RiftShape.width(mid),Math.max(RiftShape.width(y0),RiftShape.width(y1)));
            double spine=RiftShape.spine(mid)*width;
            Vec3 point=center.add(up.scale((low+high)*.5)).add(right.scale(spine));
            if(intersects(target,point,right,up,normal,width*profile+.004,(high-low)*.5,.025))return true;
        }
        return false;
    }
    private static boolean intersects(AABB target,Vec3 center,Vec3 right,Vec3 up,Vec3 normal,double w,double h,double d){
        Vec3 delta=target.getCenter().subtract(center);
        double x=target.getXsize()*.5,y=target.getYsize()*.5,z=target.getZsize()*.5;
        for(Vec3 axis:WORLD_AXES)if(separated(axis,delta,x,y,z,right,up,normal,w,h,d))return false;
        if(separated(right,delta,x,y,z,right,up,normal,w,h,d)
                ||separated(up,delta,x,y,z,right,up,normal,w,h,d)
                ||separated(normal,delta,x,y,z,right,up,normal,w,h,d))return false;
        for(Vec3 world:WORLD_AXES)
            if(separated(world.cross(right),delta,x,y,z,right,up,normal,w,h,d)
                    ||separated(world.cross(up),delta,x,y,z,right,up,normal,w,h,d)
                    ||separated(world.cross(normal),delta,x,y,z,right,up,normal,w,h,d))return false;
        return true;
    }
    private static boolean separated(Vec3 axis,Vec3 delta,double x,double y,double z,Vec3 right,Vec3 up,Vec3 normal,double w,double h,double d){
        if(axis.lengthSqr()<1e-12)return false;
        double target=x*Math.abs(axis.x)+y*Math.abs(axis.y)+z*Math.abs(axis.z);
        double blade=w*Math.abs(axis.dot(right))+h*Math.abs(axis.dot(up))+d*Math.abs(axis.dot(normal));
        return Math.abs(delta.dot(axis))>target+blade+1e-6;
    }
}
