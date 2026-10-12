package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Small beveled forearm blade attached through the same elbow and paw transforms as the arm. */
final class MahoragaBladeModel {
    private static final ResourceLocation METAL=ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/iron_block.png");
    // Model-local forearm/paw coordinates. The point faces forward at rest, above ground level.
    private static final float[][] POINTS={
            {-.13f,-.15f,.04f},{.10f,-.15f,.04f},{-.145f,-.15f,-.47f},{.085f,-.15f,-.58f},
            {-.015f,-.15f,-.92f},{-.015f,-.215f,-.34f},{-.015f,-.085f,-.34f}};
    private static final int[][] FACES={{0,2,5},{2,4,5},{4,3,5},{3,1,5},{1,0,5},
            {2,0,6},{4,2,6},{3,4,6},{1,3,6},{0,1,6}};
    static void render(PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        VertexConsumer b=buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        PoseStack.Pose pose=poses.last();
        for(int i=0;i<FACES.length;i++){
            int[] face=FACES[i];float[] a=POINTS[face[0]],c=POINTS[face[1]],d=POINTS[face[2]];
            Vector3f normal=new Vector3f(c[0]-a[0],c[1]-a[1],c[2]-a[2])
                    .cross(d[0]-a[0],d[1]-a[1],d[2]-a[2]).normalize();
            int shade=i<5?255:190;
            vertex(b,pose,a,shade,shade,Math.min(255,shade+8),normal,light,overlay);
            vertex(b,pose,c,shade,shade,Math.min(255,shade+8),normal,light,overlay);
            vertex(b,pose,d,shade,shade,Math.min(255,shade+8),normal,light,overlay);
            vertex(b,pose,d,shade,shade,Math.min(255,shade+8),normal,light,overlay);
        }
        // A low, warm-metal mounting collar on the paw's outer surface.
        box(b,pose,-.16f,-.24f,-.03f,.14f,-.065f,.10f,light,overlay);
    }
    private static void box(VertexConsumer b,PoseStack.Pose pose,float x,float y,float z,float X,float Y,float Z,int light,int overlay){
        float[][] p={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        int[][] faces={{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{4,5,1,0},{3,2,6,7}};
        for(int[] f:faces){
            Vector3f n=new Vector3f(p[f[1]][0]-p[f[0]][0],p[f[1]][1]-p[f[0]][1],p[f[1]][2]-p[f[0]][2])
                    .cross(p[f[2]][0]-p[f[0]][0],p[f[2]][1]-p[f[0]][1],p[f[2]][2]-p[f[0]][2]).normalize();
            for(int i:f)vertex(b,pose,p[i],194,176,122,n,light,overlay);
        }
    }
    private static void vertex(VertexConsumer b,PoseStack.Pose pose,float[] p,int r,int g,int blue,Vector3f n,int light,int overlay){
        b.addVertex(pose,p[0],p[1],p[2]).setColor(r,g,blue,255).setUv(.45f,.45f)
                .setOverlay(overlay).setLight(light).setNormal(pose,n.x,n.y,n.z);
    }
}
