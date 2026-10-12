package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.LimbCurve;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import java.util.*;

/** Native skin UVs, sleeves and armor, tessellated only on the limbs currently doing martial arts. */
public final class FlexibleLimbRenderer {
    private record Bend(float angle,float center) {}
    private static final Map<ModelPart,Bend> BENDS=new WeakHashMap<>();
    private static final Map<ModelPart.Cube,V[]> MESHES=new WeakHashMap<>();
    private static final class V {float x,y,z,u,v,nx,ny,nz;}
    private FlexibleLimbRenderer() {}
    public static void bend(ModelPart part,float degrees,boolean leg){
        if(Math.abs(degrees)<.1f)BENDS.remove(part);
        else BENDS.put(part,new Bend(degrees*(float)Math.PI/180,(leg?6:4)/16f));
    }
    public static void copy(ModelPart source,ModelPart target){
        Bend b=BENDS.get(source);if(b==null)BENDS.remove(target);else BENDS.put(target,b);
    }
    public static void clear(HumanoidModel<?> model){
        BENDS.remove(model.rightArm);BENDS.remove(model.leftArm);BENDS.remove(model.rightLeg);BENDS.remove(model.leftLeg);
        if(model instanceof PlayerModel<?> p){BENDS.remove(p.rightSleeve);BENDS.remove(p.leftSleeve);BENDS.remove(p.rightPants);BENDS.remove(p.leftPants);}
    }
    public static boolean render(ModelPart part,List<ModelPart.Cube> cubes,PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color){
        Bend b=BENDS.get(part);if(b==null)return false;
        for(ModelPart.Cube cube:cubes)for(V v:MESHES.computeIfAbsent(cube,FlexibleLimbRenderer::mesh)){
            var p=LimbCurve.bend(v.y,v.z,b.angle,b.center);
            float c=(float)Math.cos(p.angle()),s=(float)Math.sin(p.angle());
            out.vertex(pose.pose(),v.x,p.y(),p.z()).color(color>>16&255,color>>8&255,color&255,color>>>24).uv(v.u,v.v).overlayCoords(overlay).uv2(light)
                    .normal(pose.normal(),v.nx,v.ny*c-v.nz*s,v.ny*s+v.nz*c).endVertex();
        }
        return true;
    }
    static Vector3f bent(ModelPart part,Vector3f point){
        Bend b=BENDS.get(part);if(b==null)return new Vector3f(point);
        var p=LimbCurve.bend(point.y,point.z,b.angle,b.center);return new Vector3f(point.x,p.y(),p.z());
    }
    static void palm(ModelPart part,PoseStack poses,Vector3f point){
        part.translateAndRotate(poses);Vector3f p=bent(part,point);poses.translate(p.x,p.y,p.z);
        Bend b=BENDS.get(part);if(b!=null)poses.mulPose(Axis.XP.rotation(LimbCurve.bend(point.y,point.z,b.angle,b.center).angle()));
    }
    private static V interpolate(V a,V b,float t){
        V v=new V();v.x=a.x+(b.x-a.x)*t;v.y=a.y+(b.y-a.y)*t;v.z=a.z+(b.z-a.z)*t;
        v.u=a.u+(b.u-a.u)*t;v.v=a.v+(b.v-a.v)*t;v.nx=a.nx;v.ny=a.ny;v.nz=a.nz;return v;
    }
    private static V[] mesh(ModelPart.Cube cube){
        Capture capture=new Capture();cube.compile(new PoseStack().last(),capture,0,0,1,1,1,1);
        List<V> result=new ArrayList<>();List<V> original=capture.vertices;
        for(int q=0;q+3<original.size();q+=4){
            V a=original.get(q),b=original.get(q+1),c=original.get(q+2),d=original.get(q+3);
            if(Math.abs(a.y-b.y)<.001f&&Math.abs(a.y-d.y)<.001f){Collections.addAll(result,a,b,c,d);continue;}
            if(Math.abs(a.y-b.y)>Math.abs(a.y-d.y)){
                for(int i=0;i<12;i++)Collections.addAll(result,interpolate(a,b,i/12f),interpolate(a,b,(i+1)/12f),interpolate(d,c,(i+1)/12f),interpolate(d,c,i/12f));
            }else{
                for(int i=0;i<12;i++)Collections.addAll(result,interpolate(a,d,i/12f),interpolate(b,c,i/12f),interpolate(b,c,(i+1)/12f),interpolate(a,d,(i+1)/12f));
            }
        }
        return result.toArray(V[]::new);
    }
    /** Capture original cube faces once, so mirrored/slim/custom skins retain exact vanilla UVs. */
    private static final class Capture implements VertexConsumer {
        final List<V> vertices=new ArrayList<>();V current;
        public void endVertex(){}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
        public VertexConsumer vertex(double x,double y,double z){current=new V();current.x=(float)x;current.y=(float)y;current.z=(float)z;vertices.add(current);return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float v){current.u=u;current.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){current.nx=x;current.ny=y;current.nz=z;return this;}
    }
}
