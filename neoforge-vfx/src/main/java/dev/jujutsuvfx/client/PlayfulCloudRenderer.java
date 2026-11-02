package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.PlayfulCloudMesh;
import dev.jujutsuvfx.core.PlayfulCloudMotion;
import dev.jujutsuvfx.core.PlayfulCloudPhysics;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

/** Native lit entity mesh: no screen copy, HDR target, custom world shader or particle dependency. */
public final class PlayfulCloudRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation SURFACE=ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/white_concrete.png");
    public static final RenderType SOLID=RenderType.entityCutoutNoCull(SURFACE);
    public static final RenderType TRAIL=RenderType.entityTranslucent(SURFACE);
    public PlayfulCloudRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());
    }
    @Override public void renderByItem(ItemStack item,ItemDisplayContext context,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        poses.pushPose();
        try {
            poses.translate(.5,.5,.5);
            if(context==ItemDisplayContext.GUI) {
                poses.translate(.08,-.25,0);poses.mulPose(Axis.XP.rotationDegrees(15));poses.mulPose(Axis.YP.rotationDegrees(-25));
                poses.mulPose(Axis.ZP.rotationDegrees(-31));poses.scale(.91f,.91f,.91f);
            } else if(context==ItemDisplayContext.GROUND) {
                poses.translate(0,-.20,0);poses.mulPose(Axis.XP.rotationDegrees(80));poses.scale(.62f,.62f,.62f);
            } else if(context==ItemDisplayContext.FIXED) {
                poses.translate(0,-.29,.02);poses.scale(.8f,.8f,.8f);
            } else {
                poses.translate(0,-.1,0);poses.mulPose(Axis.ZP.rotationDegrees(-12));poses.scale(.85f,.85f,.85f);
            }
            draw(poses,buffers.getBuffer(SOLID),PlayfulCloudMotion.IDLE,1,light,overlay);
        }finally{poses.popPose();}
    }
    public static void draw(PoseStack poses,VertexConsumer vertices,PlayfulCloudMotion.Pose motion,float side,int light,int overlay) {
        poses.pushPose();
        try {
            poses.translate(0,-PlayfulCloudMotion.GRIP,0);
            mesh(PlayfulCloudMesh.ROD,poses,vertices,light,overlay,1);
            joint(poses,vertices,motion.bend1()*side,motion.twist1()*side,light,overlay);
            mesh(PlayfulCloudMesh.ROD,poses,vertices,light,overlay,1);
            joint(poses,vertices,motion.bend2()*side,motion.twist2()*side,light,overlay);
            mesh(PlayfulCloudMesh.ROD,poses,vertices,light,overlay,1);
        }finally{poses.popPose();}
    }
    public static void physical(PoseStack poses,VertexConsumer out,PlayfulCloudPhysics rig,PlayfulCloudPhysics.Frame frame,int light,int overlay){
        poses.pushPose();poses.translate(0,-PlayfulCloudMotion.GRIP,0);
        mesh(PlayfulCloudMesh.ROD,poses,out,light,overlay,1);poses.popPose();
        for(int i:new int[]{0,2}){
            var a=frame.local(rig.point(i));var b=frame.local(rig.point(i+1));var d=b.sub(a);
            poses.pushPose();try{
                poses.translate(a.x(),a.y(),a.z());
                var direction=d.unit();poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)direction.x(),(float)direction.y(),(float)direction.z())));
                poses.scale(1,(float)(d.length()/PlayfulCloudMotion.CHAIN_LENGTH),1);
                mesh(PlayfulCloudMesh.LINK,poses,out,light,overlay,1);
            }finally{poses.popPose();}
        }
        for(int i:new int[]{1,3}){
            var a=frame.local(rig.point(i));var b=frame.local(rig.point(i+1));var d=b.sub(a).unit();
            poses.pushPose();
            try{poses.translate(a.x(),a.y(),a.z());poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)d.x(),(float)d.y(),(float)d.z())));
                mesh(PlayfulCloudMesh.ROD,poses,out,light,overlay,1);
            }finally{poses.popPose();}
        }
    }
    public static void physicalTrail(PoseStack poses,VertexConsumer out,PlayfulCloudPhysics rig,PlayfulCloudPhysics.Frame frame,float power){
        if(power<.01f)return;Vector3f previousNear=null,previousFar=null;var history=rig.history();int i=0;
        for(var trace:history){
            var a=frame.local(trace.near());var b=frame.local(trace.tip());
            Vector3f near=poses.last().pose().transformPosition(new Vector3f((float)a.x(),(float)a.y(),(float)a.z()));
            Vector3f far=poses.last().pose().transformPosition(new Vector3f((float)b.x(),(float)b.y(),(float)b.z()));
            if(previousNear!=null)ribbon(out,previousNear,previousFar,far,near,.32f*power*i/history.size(),0xF0D6D7);
            previousNear=near;previousFar=far;i++;
        }
    }
    private static void joint(PoseStack poses,VertexConsumer vertices,float bend,float twist,int light,int overlay) {
        poses.translate(0,PlayfulCloudMotion.ROD_LENGTH,0);
        poses.mulPose(Axis.ZP.rotationDegrees(bend));poses.mulPose(Axis.XP.rotationDegrees(twist));
        mesh(PlayfulCloudMesh.LINK,poses,vertices,light,overlay,1);
        poses.translate(0,PlayfulCloudMotion.CHAIN_LENGTH,0);
    }
    private static void mesh(List<PlayfulCloudMesh.Quad> mesh,PoseStack poses,VertexConsumer out,int light,int overlay,float alpha) {
        PoseStack.Pose pose=poses.last();
        for(var q:mesh) { vertex(out,pose,q.a(),light,overlay,alpha);vertex(out,pose,q.b(),light,overlay,alpha);
            vertex(out,pose,q.c(),light,overlay,alpha);vertex(out,pose,q.d(),light,overlay,alpha); }
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,PlayfulCloudMesh.Vertex v,int light,int overlay,float alpha) {
        out.addVertex(pose.pose(),v.x(),v.y(),v.z()).setColor(((int)(alpha*255)<<24)|v.rgb())
                .setUv(v.u(),v.v()).setOverlay(overlay).setLight(light).setNormal(pose,v.nx(),v.ny(),v.nz());
    }
    public static void ribbon(VertexConsumer out,Vector3f a,Vector3f b,Vector3f c,Vector3f d,float alpha,int color) {
        plain(out,a,alpha,color);plain(out,b,alpha,color);plain(out,c,alpha,color);plain(out,d,alpha,color);
    }
    private static void plain(VertexConsumer out,Vector3f point,float alpha,int rgb) {
        out.addVertex(point.x,point.y,point.z).setColor(((int)(Math.min(1,alpha)*255)<<24)|rgb).setUv(.5f,.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0,1,0);
    }
}
