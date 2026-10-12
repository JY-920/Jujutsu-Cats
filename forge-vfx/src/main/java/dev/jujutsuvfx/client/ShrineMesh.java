package dev.jujutsuvfx.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Shared with the inspected Blockbench project by tools/generate_shrine_model.mjs. */
final class ShrineMesh {
    record P(float x, float y, float z) {}
    record Vertex(P p, int color, float material) {}
    static final List<Vertex> VERTICES = load();
    static final float MODEL_HEIGHT = (float) VERTICES.stream().mapToDouble(v -> v.p.y).max().orElseThrow() + .04f;
    static final float HALF_WIDTH = (float) VERTICES.stream().mapToDouble(v -> Math.abs(v.p.x)).max().orElseThrow();
    static final float HALF_DEPTH = (float) VERTICES.stream().mapToDouble(v -> Math.abs(v.p.z)).max().orElseThrow();
    static final float BOUND_RADIUS = (float) VERTICES.stream().mapToDouble(v ->
            Math.max(Math.abs(v.p.x), Math.max(Math.abs(v.p.y), Math.abs(v.p.z)))).max().orElseThrow() + 1;

    private static List<Vertex> load() {
        String resource = "/assets/jujutsuvfx/models/domain/malevolent_shrine.mesh";
        var stream = ShrineMesh.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing shrine mesh: " + resource);
        try (var in = new DataInputStream(new BufferedInputStream(stream))) {
            if (in.readInt() != 0x5348524E || in.readInt() != 1) throw new IOException("Unknown shrine mesh format");
            int count = in.readInt();
            if (count < 3 || count > 1_000_000 || count % 3 != 0) throw new IOException("Invalid shrine vertex count");
            var vertices = new ArrayList<Vertex>(count);
            for (int i = 0; i < count; i++) {
                float x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                int color = in.readInt();
                float material = in.readFloat();
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                        || !Float.isFinite(material)) throw new IOException("Non-finite shrine vertex");
                vertices.add(new Vertex(new P(x, y, z), color, material));
            }
            if (in.read() != -1) throw new IOException("Trailing shrine mesh bytes");
            return List.copyOf(vertices);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load shrine mesh", error);
        }
    }

    // Legacy builder retained for the alternate ToriiMawMesh; its geometry remains unchanged.
    private static final int STONE = 0xFF302932, RED = 0xFF741C24, BONE = 0xFFE3C9A0;
    private static final int ROOF = 0xFF202B30, GOLD = 0xFF957254, BLACK = 0xFF10080D, FIRE = 0xFFFF3227;
    static final class Builder {
        final List<Vertex> vertices = new ArrayList<>();
        void tri(P a, P b, P c, int color, float material) {
            float ux=b.x-a.x, uy=b.y-a.y, uz=b.z-a.z, vx=c.x-a.x, vy=c.y-a.y, vz=c.z-a.z;
            float nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            float light=material == 1 ? 1 : .60f + .40f*Math.abs((nx*.35f+ny*.82f+nz*.45f)/Math.max(.001f,length));
            int shaded = 0xFF000000 | ((int)((color>>16&255)*light)<<16)
                    | ((int)((color>>8&255)*light)<<8) | (int)((color&255)*light);
            vertices.add(new Vertex(a,shaded,material));
            vertices.add(new Vertex(b,shaded,material));
            vertices.add(new Vertex(c,shaded,material));
        }
        void quad(P a,P b,P c,P d,int color,float mat) { tri(a,b,c,color,mat);tri(a,c,d,color,mat); }
        void box(float x0,float y0,float z0,float x1,float y1,float z1,int color,float mat) {
            P a=new P(x0,y0,z0),b=new P(x1,y0,z0),c=new P(x1,y1,z0),d=new P(x0,y1,z0);
            P e=new P(x0,y0,z1),f=new P(x1,y0,z1),g=new P(x1,y1,z1),h=new P(x0,y1,z1);
            quad(a,b,c,d,color,mat);quad(f,e,h,g,color,mat);quad(e,a,d,h,color,mat);
            quad(b,f,g,c,color,mat);quad(d,c,g,h,color,mat);quad(e,f,b,a,color,mat);
        }
        void fang(float x,float y,float z,float width,float length,int color) { fang(x,y,z,width,length,color,0); }
        void fang(float x,float y,float z,float w,float len,int color,float mat) {
            P a=new P(x-w,y,z-w),b=new P(x+w,y,z-w),c=new P(x+w,y,z+w),d=new P(x-w,y,z+w);
            P tip=new P(x+len*.08f,y+len,z);
            tri(a,b,tip,color,mat);tri(b,c,tip,color,mat);tri(c,d,tip,color,mat);tri(d,a,tip,color,mat);
            quad(a,d,c,b,color,mat);
        }
        void beam(P a,P b,float r,int color,float mat) {
            float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
            float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=len;dy/=len;dz/=len;
            float ax=Math.abs(dy)>.95f?1:0,ay=Math.abs(dy)>.95f?0:1;
            float ux=-dz*ay,uy=dz*ax,uz=dx*ay-dy*ax;
            float ul=(float)Math.sqrt(ux*ux+uy*uy+uz*uz);ux/=ul;uy/=ul;uz/=ul;
            float vx=dy*uz-dz*uy,vy=dz*ux-dx*uz,vz=dx*uy-dy*ux;
            for(int i=0;i<8;i++) {
                float t0=(float)(i*Math.PI/4),t1=(float)((i+1)*Math.PI/4);
                P p0=ring(a,r,t0,ux,uy,uz,vx,vy,vz),p1=ring(a,r,t1,ux,uy,uz,vx,vy,vz);
                P p2=ring(b,r,t1,ux,uy,uz,vx,vy,vz),p3=ring(b,r,t0,ux,uy,uz,vx,vy,vz);
                quad(p0,p1,p2,p3,color,mat);tri(a,p1,p0,color,mat);tri(b,p3,p2,color,mat);
            }
        }
        P ring(P p,float r,float t,float ux,float uy,float uz,float vx,float vy,float vz) {
            float a=(float)Math.cos(t)*r,b=(float)Math.sin(t)*r;
            return new P(p.x+a*ux+b*vx,p.y+a*uy+b*vy,p.z+a*uz+b*vz);
        }
        void horn(P root,int sign,float h) {
            P previous=root;
            for(int i=1;i<=8;i++) {
                float t=i/8f;
                P next=new P(root.x+sign*(float)Math.sin(t*Math.PI*.85)*h*.32f,root.y+t*h,root.z-t*h*.10f);
                beam(previous,next,(1-t)*h*.105f+.014f,BONE,0);previous=next;
            }
        }
        void skull(float x,float y,float z,float s) {
            box(x-s,y,z-s*.6f,x+s,y+s*1.2f,z+s*.6f,BONE,0);
            for(int side:new int[]{-1,1}) {
                box(x+side*s*.48f-s*.24f,y+s*.48f,z+s*.61f,
                        x+side*s*.48f+s*.24f,y+s*.86f,z+s*.65f,BLACK,2);
            }
            for(int i=0;i<4;i++) box(x-s*.65f+i*s*.35f,y-s*.15f,z+s*.30f,
                    x-s*.42f+i*s*.35f,y+s*.18f,z+s*.65f,BONE,0);
        }
        void roof(float y,float w,float d,float height) {
            float[] ws={w,w*.82f,w*.33f,w*.28f};
            float[] ds={d,d*.82f,d*.14f,d*.05f};
            float[] ys={y+.26f,y,y+height*.88f,y+height};
            for(int ring=0;ring<3;ring++) {
                int j=ring+1;
                for(int s:new int[]{-1,1}) {
                    quad(new P(-ws[ring],ys[ring],s*ds[ring]),new P(ws[ring],ys[ring],s*ds[ring]),
                            new P(ws[j],ys[j],s*ds[j]),new P(-ws[j],ys[j],s*ds[j]),ROOF,0);
                    quad(new P(s*ws[ring],ys[ring],-ds[ring]),new P(s*ws[ring],ys[ring],ds[ring]),
                            new P(s*ws[j],ys[j],ds[j]),new P(s*ws[j],ys[j],-ds[j]),ROOF,0);
                }
            }
            for(int s:new int[]{-1,1}) {
                beam(new P(-w,y+.25f,s*d),new P(w,y+.25f,s*d),.09f,RED,0);
                beam(new P(s*w,y+.25f,-d),new P(s*w,y+.25f,d),.09f,RED,0);
                for(int i=-10;i<=10;i++) {
                    float t=i/10f;
                    for(int ring=0;ring<3;ring++) beam(new P(t*ws[ring],ys[ring]+.025f,s*ds[ring]),
                            new P(t*ws[ring+1],ys[ring+1]+.025f,s*ds[ring+1]),.025f,GOLD,0);
                }
            }
            box(-w*.28f,y+height-.04f,-d*.05f,w*.28f,y+height+.04f,d*.05f,RED,0);
        }
    }
}
