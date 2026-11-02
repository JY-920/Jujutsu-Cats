package dev.jujutsuvfx.core;

import java.util.ArrayList;
import java.util.List;

/** Actual low-poly surfaces. Shared by the game renderer and the editable OBJ/Blockbench export. */
public final class PlayfulCloudMesh {
    public record Vertex(float x,float y,float z,float nx,float ny,float nz,float u,float v,int rgb) {}
    public record Quad(Vertex a,Vertex b,Vertex c,Vertex d) {}
    public static final List<Quad> ROD = rod();
    public static final List<Quad> LINK = chain();
    private static final int SIDES=12;
    private PlayfulCloudMesh() {}

    /** Three oval steel rings, alternating their planes, spanning one simulated socket. */
    private static List<Quad> chain() {
        List<Quad> out=new ArrayList<>();
        for(int link=0;link<3;link++)for(int segment=0;segment<16;segment++)for(int side=0;side<6;side++) {
            Vertex[] v=new Vertex[4];int n=0;
            for(int[] corner:new int[][]{{0,0},{1,0},{1,1},{0,1}}){
                double a=(segment+corner[0])*Math.PI*2/16,b=(side+corner[1])*Math.PI*2/6;
                double center=.022+link*.043,wire=.007;
                double x=(.020+wire*Math.cos(b))*Math.cos(a),y=center+(.022+wire*Math.cos(b))*Math.sin(a),z=wire*Math.sin(b);
                double nx=Math.cos(a)*Math.cos(b),ny=Math.sin(a)*Math.cos(b),nz=Math.sin(b);
                if(link%2==1){double swap=x;x=z;z=swap;swap=nx;nx=nz;nz=swap;}
                int color=side<3?0xDAD4DF:0x5E5867;
                v[n++]=new Vertex((float)x,(float)y,(float)z,(float)nx,(float)ny,(float)nz,.5f,.5f,color);
            }
            out.add(new Quad(v[0],v[1],v[2],v[3]));
        }
        return List.copyOf(out);
    }
    private static List<Quad> rod() {
        List<Quad> out=new ArrayList<>();
        // Carmine lacquer, recessed iron collars and bevelled silver end plates.
        cylinder(out,0,.035f,.046f,.055f,0x3F3545);
        cylinder(out,.035f,.076f,.055f,.055f,0x22202B);
        cylinder(out,.076f,.091f,.056f,.050f,0xD3CCD8);
        cylinder(out,.091f,.849f,.050f,.050f,0xC9283C);
        cylinder(out,.849f,.864f,.050f,.056f,0xD3CCD8);
        cylinder(out,.864f,.905f,.055f,.055f,0x22202B);
        cylinder(out,.905f,.94f,.055f,.046f,0x3F3545);
        cap(out,0,.046f,false,0xA7A2B0); cap(out,.94f,.046f,true,0xBDB6C4);
        // Black, forked cloud markings are flush polygon inlays, not alpha billboards.
        for(int end=0;end<2;end++) for(int side=0;side<SIDES;side++) {
            float y=end==0?.104f:.836f,dir=end==0?1:-1;
            float theta=(float)(side*2*Math.PI/SIDES),next=(float)((side+1)*2*Math.PI/SIDES);
            float length=.050f+.024f*(side%3);
            Vertex a=radial(theta,y,.05035f,0x231C29);
            Vertex b=radial(next,y,.05035f,0x231C29);
            Vertex c=radial(next-.10f,y+dir*length,.05035f,0x231C29);
            Vertex d=radial(theta+.12f,y+dir*length*.45f,.05035f,0x231C29);
            out.add(new Quad(a,b,c,d));
        }
        // Two restrained metallic grooves at each socket read clearly at a distance.
        for(float y:new float[]{.045f,.064f,.875f,.894f})cylinder(out,y,y+.004f,.056f,.056f,0x9D929F);
        return List.copyOf(out);
    }
    private static void cylinder(List<Quad> out,float lo,float hi,float r0,float r1,int color) {
        for(int i=0;i<SIDES;i++) {
            float a=(float)(i*2*Math.PI/SIDES),b=(float)((i+1)*2*Math.PI/SIDES);
            out.add(new Quad(radial(a,lo,r0,color),radial(a,hi,r1,color),radial(b,hi,r1,color),radial(b,lo,r0,color)));
        }
    }
    private static Vertex radial(float a,float y,float radius,int color) {
        float c=(float)Math.cos(a),s=(float)Math.sin(a);
        return new Vertex(radius*c,y,radius*s,c,0,s,a/(float)(Math.PI*2),y*3,color);
    }
    private static void cap(List<Quad> out,float y,float radius,boolean up,int color) {
        Vertex center=new Vertex(0,y,0,0,up?1:-1,0,.5f,.5f,color);
        for(int i=0;i<SIDES;i++) {
            float a=(float)(i*2*Math.PI/SIDES),b=(float)((i+1)*2*Math.PI/SIDES);
            Vertex v1=new Vertex(radius*(float)Math.cos(a),y,radius*(float)Math.sin(a),0,up?1:-1,0,0,0,color);
            Vertex v2=new Vertex(radius*(float)Math.cos(b),y,radius*(float)Math.sin(b),0,up?1:-1,0,1,0,color);
            out.add(up?new Quad(center,v2,v1,center):new Quad(center,v1,v2,center));
        }
    }
}
