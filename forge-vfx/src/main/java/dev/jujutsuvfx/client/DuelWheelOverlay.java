package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.network.DuelScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** The real detached wheel, drawn after the world so the insert is still filmed with F1 hiding the HUD. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class DuelWheelOverlay {
    private static final ResourceLocation TEXTURE=new ResourceLocation(JujutsuVfx.ID,"textures/entity/mahoraga_cat_details.png");
    /** One second for the camera to settle, then the vanilla forty-tick totem presentation. */
    static final int CAMERA_TICKS=20,ACTIVATION_TICKS=40;
    private static SorcererCatGeometry wheel;
    record Activation(float scale,float offsetX,float offsetY,float yaw,float sway,float opacity) {}
    // Minecraft GameRenderer.renderItemActivationAnimation (1.20.1 and 1.21.1).
    static float activationCurve(float p){
        float p2=p*p,p3=p*p2;
        return 10.25f*p3*p2-24.95f*p2*p2+25.5f*p3-13.8f*p2+4*p;
    }
    private static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    static Activation activation(float age,long duel){
        float elapsed=age-CAMERA_TICKS;
        if(!Float.isFinite(age)||elapsed<0||elapsed>=ACTIVATION_TICKS)return null;
        float p=elapsed/ACTIVATION_TICKS,angle=activationCurve(p)*(float)Math.PI;
        float sine=(float)Math.sin(angle),travel=Math.abs((float)Math.sin(angle*2));
        // The packet's stable duel identity replaces nextFloat: retransmission cannot change the path.
        long seed=duel*0x9e3779b97f4a7c15L;
        float offX=((seed>>>16)&65535)/32767.5f-1;
        float offY=((seed>>>40)&65535)/32767.5f-1;
        float opacity=smooth(elapsed/3)*smooth((ACTIVATION_TICKS-elapsed)/4);
        float scale=(50+175*sine)/225f;
        return new Activation(scale*(.30f+.70f*opacity),offX*.5f*travel,-offY*.5f*travel,
                900*Math.abs(sine),6*(float)Math.cos(p*8),opacity);
    }
    record Placement(float scale,float x,float y) {}
    static Placement placement(Activation motion,float aspect){
        // The imported 192 vertices fit a disc of radius .39868 and depth .15625.
        // Use its rotated cylinder, so the vanilla spin cannot clip the screen at peak size.
        Matrix4f turn=new Matrix4f().rotationY((float)Math.toRadians(motion.yaw()))
                .rotateX((float)Math.toRadians(motion.sway())).rotateZ((float)Math.toRadians(motion.sway()));
        float extentX=.399f*(float)Math.hypot(turn.m00(),turn.m10())+.078125f*Math.abs(turn.m20());
        float extentY=.399f*(float)Math.hypot(turn.m01(),turn.m11())+.078125f*Math.abs(turn.m21());
        float shortSide=Math.min(1,aspect),scale=.80f*shortSide*motion.scale()/Math.max(extentX,extentY);
        float roomX=Math.max(0,aspect-extentX*scale-.025f*shortSide);
        float roomY=Math.max(0,1-extentY*scale-.025f*shortSide);
        return new Placement(scale,Math.max(-roomX,Math.min(roomX,motion.offsetX()*aspect)),
                Math.max(-roomY,Math.min(roomY,motion.offsetY())));
    }
    private DuelWheelOverlay(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||!CatCinematicCamera.active())return;
        DuelScenes.Scene scene=DuelScenes.filming();
        if(scene==null||(scene.packet().phase()!=DuelScene.WHEEL_SUMMON&&scene.packet().phase()!=DuelScene.RESCUE_WHEEL))return;
        float partial=event.getPartialTick();
        Activation motion=activation(scene.age(partial),scene.packet().duel());
        if(motion==null||motion.opacity()<=.001f)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        if(wheel==null)wheel=new SorcererCatGeometry(mc.getResourceManager(),"mahoraga_wheel","animation2");
        float aspect=mc.getWindow().getWidth()/(float)Math.max(1,mc.getWindow().getHeight());
        if(!Float.isFinite(aspect)||aspect<=0)return;
        Placement placement=placement(motion,aspect);
        Matrix4f previousProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting=RenderSystem.getVertexSorting();
        float[] previousColor=RenderSystem.getShaderColor().clone();int previousTexture=RenderSystem.getShaderTexture(0);
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            // Vanilla item activation also uses an independent projection. A fixed depth keeps
            // this much larger 3-D wheel away from the near plane at every spin/FOV/GUI scale.
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-aspect,aspect,-1,1,.1f,32),VertexSorting.ORTHOGRAPHIC_Z);
            PoseStack poses=new PoseStack();
            // Cancel the world view matrix: the model stays in the exact optical centre, not at the player.
            poses.mulPoseMatrix(new Matrix4f(RenderSystem.getModelViewMatrix()).invert());
            poses.translate(placement.x(),placement.y(),-4);
            poses.scale(placement.scale(),placement.scale(),placement.scale());
            poses.mulPose(Axis.YP.rotationDegrees(motion.yaw()));
            poses.mulPose(Axis.XP.rotationDegrees(motion.sway()));
            poses.mulPose(Axis.ZP.rotationDegrees(motion.sway()));
            // Centre the physical thickness as well as the authored rotation pivot.
            poses.translate(0,0,-.125);
            poses.mulPose(Axis.XP.rotationDegrees(90));
            RenderSystem.setShader(GameRenderer::getRendertypeEyesShader);
            RenderSystem.setShaderTexture(0,TEXTURE);RenderSystem.setShaderColor(1,1,1,motion.opacity());
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            // Keep the imported wheel at its rest frame; vanilla activation already supplies the spin.
            wheel.renderDetachedWheel(poses,b,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);
            BufferBuilder.RenderedBuffer mesh=b.end();BufferUploader.drawWithShader(mesh);
        }finally{
            RenderSystem.setProjectionMatrix(previousProjection,previousSorting);
            RenderSystem.setShaderColor(previousColor[0],previousColor[1],previousColor[2],previousColor[3]);
            RenderSystem.setShaderTexture(0,previousTexture);
        }
    }
}
