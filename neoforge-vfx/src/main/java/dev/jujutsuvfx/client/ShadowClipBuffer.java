package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

/** Clip animated model quads at the actual shadow plane, including blade and wheel materials. */
final class ShadowClipBuffer {
    private ShadowClipBuffer(){}
    static MultiBufferSource above(MultiBufferSource source,Matrix4f entityPose){
        Vector4f plane=new Matrix4f(entityPose).invert().transpose().transform(new Vector4f(0,1,0,-.09f));
        return type->new Clip(source.getBuffer(type),plane);
    }
    static List<float[]> clip(float[][] quad,Vector4f plane){
        List<float[]> out=new ArrayList<>(6);float[] a=quad[3];float da=distance(a,plane);
        for(float[] b:quad){float db=distance(b,plane);
            if((da>=0)!=(db>=0)){
                float t=da/(da-db);float[] p=new float[16];
                for(int i=0;i<16;i++)p[i]=a[i]+(b[i]-a[i])*t;
                out.add(p);
            }
            if(db>=0)out.add(b);a=b;da=db;
        }
        return out;
    }
    private static float distance(float[] v,Vector4f p){return v[0]*p.x+v[1]*p.y+v[2]*p.z+p.w;}
    private static final class Clip implements VertexConsumer {
        final VertexConsumer output;final Vector4f plane;final float[][] quad=new float[4][];
        float[] vertex=new float[16];int count;int dr=255,dg=255,db=255,da=255;
        Clip(VertexConsumer output,Vector4f plane){this.output=output;this.plane=plane;}
        private void begin(float x,float y,float z){vertex=new float[]{x,y,z,dr,dg,db,da,0,0,0,10,240,240,0,1,0};}
        private void end(){
            quad[count++]=vertex;if(count<4)return;count=0;
            boolean full=true,empty=true;for(float[] v:quad){boolean in=distance(v,plane)>=0;full&=in;empty&=!in;}
            if(empty)return;if(full){for(float[] v:quad)emit(v);return;}
            List<float[]> cut=clip(quad,plane);
            // Triangle fans use a repeated final vertex to retain the original QUADS render type.
            for(int i=1;i+1<cut.size();i++){emit(cut.get(0));emit(cut.get(i));emit(cut.get(i+1));emit(cut.get(i+1));}
        }
        public VertexConsumer addVertex(float x,float y,float z){begin(x,y,z);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){vertex[3]=r;vertex[4]=g;vertex[5]=b;vertex[6]=a;return this;}
        public VertexConsumer setUv(float u,float v){vertex[7]=u;vertex[8]=v;return this;}
        public VertexConsumer setUv1(int u,int v){vertex[9]=u;vertex[10]=v;return this;}
        public VertexConsumer setUv2(int u,int v){vertex[11]=u;vertex[12]=v;return this;}
        public VertexConsumer setNormal(float x,float y,float z){vertex[13]=x;vertex[14]=y;vertex[15]=z;end();return this;}
        private void emit(float[] v){output.addVertex(v[0],v[1],v[2]).setColor(Math.round(v[3]),Math.round(v[4]),Math.round(v[5]),Math.round(v[6])).setUv(v[7],v[8]).setUv1(Math.round(v[9]),Math.round(v[10])).setUv2(Math.round(v[11]),Math.round(v[12])).setNormal(v[13],v[14],v[15]);}
    }
}
