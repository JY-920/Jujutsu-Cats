package dev.jujutsuvfx.client;

import net.minecraft.world.phys.Vec3;

/** A cast pair, with a contact disk inside both spheres even when one contains the other. */
final class DomainClash {
    final int shrineSession,voidSession;
    final long born;
    DomainClash(int shrineSession,int voidSession,long born){
        this.shrineSession=shrineSession;this.voidSession=voidSession;this.born=born;
    }
    boolean matches(int shrine,int gojo){return shrineSession==shrine&&voidSession==gojo;}
    float age(long now,float partial){return (now-born+partial)/20f;}
    record Contact(Vec3 point,Vec3 normal,Vec3 right,Vec3 up,float radius,float width) {}

    static boolean surrounds(MalevolentShrine shrine,UnlimitedVoid gojo,long now,float partial){
        return shrine!=null&&gojo!=null&&dev.jujutsuvfx.core.DomainVolume.encloses(
                shrine.center,shrine.radius*shrine.expansion(now,partial),gojo.center,gojo.radius*gojo.expansion(now,partial));
    }
    static Contact contact(MalevolentShrine shrine,UnlimitedVoid gojo,long now,float partial){
        float a=shrine.radius*shrine.expansion(now,partial),b=gojo.radius*gojo.expansion(now,partial);
        Vec3 delta=gojo.center.subtract(shrine.center);
        double distance=delta.length();
        if(a<.05f||b<.05f||distance>=a+b)return null;
        Vec3 normal=distance>.75?delta.scale(1/distance):new Vec3(gojo.axis.x,0,gojo.axis.z).normalize();
        double separation=delta.dot(normal);
        double low=Math.max(-a,separation-b),high=Math.min(a,separation+b);
        double middle=(low+high)*.5;
        Vec3 point=shrine.center.add(normal.scale(middle));
        float disc=(float)Math.sqrt(Math.max(0,Math.min(a*a-middle*middle,
                b*b-(middle-separation)*(middle-separation))));
        if(disc<.08f)return null;
        Vec3 reference=Math.abs(normal.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=reference.cross(normal).normalize(),up=normal.cross(right).normalize();
        return new Contact(point,normal,right,up,disc,Math.clamp(Math.min(a,b)*.07f,.55f,2.3f));
    }
}
