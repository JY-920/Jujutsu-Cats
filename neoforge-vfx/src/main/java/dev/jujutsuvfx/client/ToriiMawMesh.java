package dev.jujutsuvfx.client;

import java.util.List;
import dev.jujutsuvfx.client.ShrineMesh.Builder;
import dev.jujutsuvfx.client.ShrineMesh.P;
import dev.jujutsuvfx.client.ShrineMesh.Vertex;

/** Variant two: a broad torii silhouette wrapped around a deep, organic toothed mouth. */
final class ToriiMawMesh {
    private static final int RED=0xFFB42D20, DARK=0xFF10252A, BONE=0xFF93C9CD;
    private static final int GUM=0xFF69222D, THROAT=0xFF08040B;
    static final List<Vertex> VERTICES=build();

    private static List<Vertex> build() {
        Builder b=new Builder();
        b.box(-3.9f,0,-1.7f,3.9f,.22f,1.9f,0xFF303840,0);
        // Vermilion gate pillars frame the mouth, with a broad tiled eave above them.
        for(int side:new int[]{-1,1}) {
            b.box(side*3.65f-.43f,.2f,-.48f,side*3.65f+.43f,.78f,.48f,DARK,0);
            b.beam(new P(side*3.65f,.7f,0),new P(side*3.30f,6.95f,0),.31f,RED,0);
            b.beam(new P(side*3.63f,.8f,0),new P(side*3.58f,1.55f,0),.34f,DARK,0);
            b.box(side*3.32f-.41f,6.32f,-.41f,side*3.32f+.41f,6.65f,.41f,BONE,0);
        }
        b.box(-4.55f,5.70f,-.25f,4.55f,6.02f,.25f,RED,0);
        b.box(-.19f,5.88f,-.26f,.19f,7.03f,.26f,RED,0);
        referenceRoof(b);
        // An elongated jaw, rounded gums and a recessed funnel read as a mouth from both sides.
        for(int side:new int[]{-1,1}) mouth(b,side);
        for(int side:new int[]{-1,1}) {
            b.horn(new P(side*2.75f,3.4f,0),side,1.45f);
            for(int i=0;i<5;i++) {
                float y=1.0f+i*.62f;
                b.beam(new P(side*2.70f,y,-.92f),new P(side*3.05f,y+.22f,0),.13f,BONE,0);
                b.beam(new P(side*3.05f,y+.22f,0),new P(side*2.70f,y,.92f),.13f,BONE,0);
            }
        }
        for(int i=0;i<9;i++) b.skull(-2.8f+i*.7f,.18f,1.45f,.26f+(i%3)*.035f);
        return List.copyOf(b.vertices);
    }

    private static void referenceRoof(Builder b) {
        b.roof(6.05f,5.75f,2.70f,1.26f);
        // Three rows of projecting brackets and glowing, tightly repeated eave ends.
        for(int face:new int[]{-1,1}) {
            float z=face*2.7f;
            for(int i=-20;i<=20;i++) {
                float x=i*.265f, lift=.30f*(float)Math.pow(Math.abs(x)/5.3,3);
                b.beam(new P(x,6.01f+lift,z-face*.30f),new P(x,6.25f+lift,z+face*.16f),.065f,0xFFD46627,0);
                b.fang(x,6.10f+lift,z+face*.20f,.045f,-.12f,0xFFFF7A30,1);
            }
            for(int side:new int[]{-1,1}) for(int j=0;j<3;j++) {
                float x=side*3.35f,y=5.35f+j*.24f;
                b.box(x-.58f-j*.16f,y,face*1.4f-.19f,x+.58f+j*.16f,y+.14f,face*1.4f+.19f,RED,0);
            }
            // Central mountain gable and the small ivory-toothed crest in the supplied reference.
            b.tri(new P(-2.25f,6.9f,face*1.6f),new P(2.25f,6.9f,face*1.6f),new P(0,8.45f,face*1.6f),DARK,0);
            b.beam(new P(-2.30f,6.94f,face*1.65f),new P(0,8.51f,face*1.65f),.09f,0xFF537D75,0);
            b.beam(new P(0,8.51f,face*1.65f),new P(2.30f,6.94f,face*1.65f),.09f,0xFF537D75,0);
            b.box(-.46f,7.12f,face*1.69f-.02f,.46f,7.78f,face*1.69f+.02f,THROAT,2);
            for(int i=0;i<5;i++)b.fang(-.33f+i*.165f,7.66f,face*1.75f,.068f,-.34f,BONE);
        }
        for(int side:new int[]{-1,1}) {
            b.quad(new P(0,8.45f,-1.6f),new P(0,8.45f,1.6f),
                    new P(side*2.25f,6.9f,1.6f),new P(side*2.25f,6.9f,-1.6f),DARK,0);
            for(int i=0;i<19;i++) {
                float z=-1.6f+i*3.2f/18;
                b.beam(new P(0,8.49f,z),new P(side*2.25f,6.94f,z),.027f,0xFF52786C,0);
            }
            crescent(b,new P(side*3.45f,6.8f,-.85f),new P(side*6.1f,7.6f,-.8f),
                    new P(side*5.85f,10.2f,-.7f),new P(side*4.15f,10.45f,-.65f),.48f);
            crescent(b,new P(side*1.65f,7.25f,-1.15f),new P(side*3.45f,8.0f,-1.15f),
                    new P(side*3.3f,9.9f,-1.0f),new P(side*2.3f,10.12f,-.85f),.33f);
        }
    }

    private static void crescent(Builder b,P a,P c,P d,P end,float width) {
        P previous=a;
        for(int i=1;i<=24;i++) {
            float t=i/24f,u=1-t;
            P next=new P(u*u*u*a.x()+3*u*u*t*c.x()+3*u*t*t*d.x()+t*t*t*end.x(),
                    u*u*u*a.y()+3*u*u*t*c.y()+3*u*t*t*d.y()+t*t*t*end.y(),
                    u*u*u*a.z()+3*u*u*t*c.z()+3*u*t*t*d.z()+t*t*t*end.z());
            b.beam(previous,next,width*(float)Math.pow(1-t,.78)+.014f,0xFF253E58,0);
            previous=next;
        }
    }

    private static void mouth(Builder b,int side) {
        float z=side*.96f;
        for(int i=0;i<64;i++) {
            double a=i*Math.PI*2/64,c=(i+1)*Math.PI*2/64;
            P innerA=lip(a,2.88f,1.95f,z),innerB=lip(c,2.88f,1.95f,z);
            P gumA=lip(a,3.10f,2.18f,z+side*.13f),gumB=lip(c,3.10f,2.18f,z+side*.13f);
            P jawA=lip(a,3.31f,2.34f,z-side*.04f),jawB=lip(c,3.31f,2.34f,z-side*.04f);
            b.quad(innerA,innerB,gumB,gumA,GUM,0);
            b.quad(gumA,gumB,jawB,jawA,BONE,0);
            P deepA=lip(a,1.60f,1.12f,side*.30f),deepB=lip(c,1.60f,1.12f,side*.30f);
            b.quad(innerA,deepA,deepB,innerB,0xFF200C18,2);
            b.tri(deepA,new P(0,2.8f,side*.05f),deepB,THROAT,2);
        }
        for(int i=0;i<15;i++) {
            float x=-2.62f+i*5.24f/14;
            float curve=(float)Math.sqrt(Math.max(0,1-x*x/(2.9f*2.9f)));
            float tooth=.50f+.32f*(float)Math.pow(Math.sin(i*1.93),2);
            if(i==2||i==12)tooth=1.20f;
            b.fang(x,2.8f+1.93f*curve,z+side*.13f,.17f,-tooth,BONE);
            b.fang(x,2.8f-1.91f*curve,z+side*.20f,.16f,tooth*.76f,BONE);
        }
        // Raised, bowed lower jaw below the teeth, with no glowing cross inside the mouth.
        for(int i=0;i<20;i++) {
            double a=Math.PI+i*Math.PI/20,c=Math.PI+(i+1)*Math.PI/20;
            b.beam(lip(a,3.24f,2.28f,z+side*.13f),lip(c,3.24f,2.28f,z+side*.13f),.16f,BONE,0);
        }
    }
    private static P lip(double a,float width,float height,float z) {
        float ripple=1+.018f*(float)Math.sin(a*9);
        return new P((float)Math.cos(a)*width*ripple,2.8f+(float)Math.sin(a)*height,z);
    }
    private static void curvedBeam(Builder b,float x0,float y0,float x1,float y1,float depth,float thick,int color) {
        P a=new P(x0,y0,-depth),c=new P(x1,y1,-depth),d=new P(x1,y1,depth),e=new P(x0,y0,depth);
        P f=new P(x0,y0+thick,-depth),g=new P(x1,y1+thick,-depth);
        P h=new P(x1,y1+thick,depth),j=new P(x0,y0+thick,depth);
        b.quad(a,c,g,f,color,0);b.quad(e,j,h,d,color,0);b.quad(f,g,h,j,color,0);
        b.quad(a,e,d,c,color,0);b.quad(a,f,j,e,color,0);b.quad(c,d,h,g,color,0);
    }
}
