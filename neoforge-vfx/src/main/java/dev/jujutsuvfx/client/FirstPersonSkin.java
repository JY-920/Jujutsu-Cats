package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.mixin.HandLightTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Capture only the two skin meshes, then draw them after world/domain composition and before hand auras. */
@EventBusSubscriber(modid=JujutsuVfx.ID,value=Dist.CLIENT)
public final class FirstPersonSkin {
    private static ShaderInstance shader;
    private static final Frame[] HANDS=new Frame[3];
    private static final ByteBufferBuilder[] STORAGE=new ByteBufferBuilder[3];
    private record Frame(MeshData mesh,Matrix4f view,Matrix4f projection,ResourceLocation skin,
                         ClientLevel level,AbstractClientPlayer player){}
    private FirstPersonSkin(){}
    static void setShader(ShaderInstance value){clear();shader=value;}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){clear();}
    private static void clear(){for(int i=0;i<HANDS.length;i++)if(HANDS[i]!=null){HANDS[i].mesh.close();HANDS[i]=null;}}
    static void draw(PlayerRenderer renderer,PoseStack poses,MultiBufferSource buffers,int light,
                     AbstractClientPlayer player,HumanoidArm arm){
        Minecraft mc=Minecraft.getInstance();
        if(shader==null||mc.player!=player||mc.level==null||!mc.options.getCameraType().isFirstPerson()||mc.options.hideGui){
            nativeHand(renderer,poses,buffers,light,player,arm);return;
        }
        int index=arm==HumanoidArm.RIGHT?0:1;
        if(HANDS[index]!=null){HANDS[index].mesh.close();HANDS[index]=null;}
        if(STORAGE[index]==null)STORAGE[index]=new ByteBufferBuilder(8192);
        BufferBuilder mesh=new BufferBuilder(STORAGE[index],VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
        // Keep vanilla skin/sleeve selection, pose setup and RenderArmEvent hooks. No world draw is submitted.
        nativeHand(renderer,poses,type->mesh,light,player,arm);
        MeshData data=mesh.build();
        if(data!=null)HANDS[index]=new Frame(data,new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()),player.getSkin().texture(),mc.level,player);
    }
    /** A held 3D mesh shares the hand depth pass, so domains cannot cover it or detach it from the skin. */
    static boolean attachment(AbstractClientPlayer player,ResourceLocation texture,java.util.function.Consumer<VertexConsumer> draw){
        Minecraft mc=Minecraft.getInstance();
        if(shader==null||mc.player!=player||mc.level==null||!mc.options.getCameraType().isFirstPerson()||mc.options.hideGui)return false;
        int index=2;
        if(HANDS[index]!=null){HANDS[index].mesh.close();HANDS[index]=null;}
        if(STORAGE[index]==null)STORAGE[index]=new ByteBufferBuilder(131072);
        BufferBuilder mesh=new BufferBuilder(STORAGE[index],VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
        draw.accept(mesh);
        MeshData data=mesh.build();
        if(data!=null)HANDS[index]=new Frame(data,new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()),texture,mc.level,player);
        return true;
    }
    private static void nativeHand(PlayerRenderer renderer,PoseStack poses,MultiBufferSource buffers,int light,
                                   AbstractClientPlayer player,HumanoidArm arm){
        if(arm==HumanoidArm.RIGHT)renderer.renderRightHand(poses,buffers,light,player);
        else renderer.renderLeftHand(poses,buffers,light,player);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void overlay(RenderGuiEvent.Pre event){
        Minecraft mc=Minecraft.getInstance();
        if(shader==null||mc.level==null||mc.player==null||!mc.player.isAlive()||mc.player.isInvisible()
                ||mc.player.isSpectator()||mc.options.hideGui||!mc.options.getCameraType().isFirstPerson()){clear();return;}
        if(HANDS[0]==null&&HANDS[1]==null&&HANDS[2]==null)return;
        event.getGuiGraphics().flush();
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            // Like the vanilla hand pass, start a fresh hand depth layer, once, before either arm.
            RenderSystem.depthMask(true);RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(()->shader);
            for(int i=0;i<HANDS.length;i++){
                Frame hand=HANDS[i];if(hand==null)continue;
                if(hand.level!=mc.level||hand.player!=mc.player)continue;
                shader.safeGetUniform("HandView").set(hand.view);shader.safeGetUniform("HandProjection").set(hand.projection);
                shader.setSampler("SkinTexture",mc.getTextureManager().getTexture(hand.skin).getId());
                // Resolve the actual texture at draw time, after Iris has finished its world passes.
                // Texture unit 2 is mutable pipeline state, not an identity for Minecraft's lightmap.
                shader.setSampler("LightmapTexture",((HandLightTextureAccessor)mc.gameRenderer.lightTexture())
                        .jujutsuvfx$handLightTexture().getId());
                HANDS[i]=null;BufferUploader.drawWithShader(hand.mesh);
            }
        }finally{clear();}
    }
}
