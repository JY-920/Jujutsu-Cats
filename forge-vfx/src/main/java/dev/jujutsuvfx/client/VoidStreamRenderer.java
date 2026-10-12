package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** GPU streaks share one world direction and keep moving while ordinary entities are suspended. */
final class VoidStreamRenderer {
    private static ShaderInstance shader;
    private static final int[] PALETTE = {0xD12D57, 0x5119C8, 0xE999F7};
    static void setShader(ShaderInstance value){shader=value;}
    static void render(RenderLevelStageEvent event,UnlimitedVoid domain){
        Minecraft mc=Minecraft.getInstance();if(shader==null||mc.level==null)return;
        long tick=ClientEffectClock.ticks();float partial=event.getPartialTick();
        float age=domain.age(tick,partial);
        float strength=domain.visibility(tick,partial)*smooth((age-.86f)/.32f)*(1-.48f*smooth((age-2.9f)/1.1f));
        if(strength<.001f)return;
        float radius=domain.radius;
        Vec3 camera=event.getCamera().getPosition(),direction=domain.axis;
        if(camera.distanceToSqr(domain.center)>(radius+160)*(radius+160))return;
        Vec3 right=new Vec3(0,1,0).cross(direction).normalize(),up=direction.cross(right).normalize();
        int count=dev.jujutsuvfx.platform.Numbers.clamp((int)(radius*radius*1.7),420,1800);
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()));
            RenderSystem.setShader(()->shader);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
            BufferBuilder b=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(int i=0;i<count;i++){
                double seed=hash(i*11.7+2.8),angle=hash(i*5.17+9.1)*Math.PI*2;
                double across=radius*Math.sqrt(hash(i*17.3+3.7))*.94;
                double reach=Math.sqrt(radius*radius-across*across);
                double speed=96+seed*80,phase=fract(age*speed/(reach*2)+seed*17);
                double along=(phase*2-1)*reach;
                Vec3 point=domain.center.add(right.scale(Math.cos(angle)*across)).add(up.scale(Math.sin(angle)*across))
                        .add(direction.scale(along));
                if(point.y<domain.center.y+.10)continue;
                float length=(float)Math.min(1.9+seed*5.6,Math.max(0,2*(reach-Math.abs(along))));
                float opacity=(float)(Math.min(phase*12,1)*Math.min((1-phase)*12,1))*strength*(.52f+(float)seed*.44f);
                if(length<.08||opacity<.015f)continue;
                Vec3 facing=camera.subtract(point).normalize(),side=direction.cross(facing);
                if(side.lengthSqr()<.0001)side=right;
                // The wider quad holds a soft colored halo; the shader keeps the luminous core narrow.
                side=side.normalize().scale(.048+seed*.078);
                Vec3 half=direction.scale(length*.5);
                int rgb=PALETTE[i%PALETTE.length];
                int color=(dev.jujutsuvfx.platform.Numbers.clamp(Math.round(opacity*255),0,255)<<24)|rgb;
                vertex(b,point.subtract(side).subtract(half).subtract(camera),-1,-1,color);
                vertex(b,point.add(side).subtract(half).subtract(camera),1,-1,color);
                vertex(b,point.add(side).add(half).subtract(camera),1,1,color);
                vertex(b,point.subtract(side).add(half).subtract(camera),-1,1,color);
            }
            BufferBuilder.RenderedBuffer mesh=b.endOrDiscardIfEmpty();if(mesh!=null)BufferUploader.drawWithShader(mesh);
        }
    }
    private static void vertex(BufferBuilder b,Vec3 p,float u,float v,int color){b.vertex((float)p.x,(float)p.y,(float)p.z).uv(u,v).color(color).endVertex();}
    private static float smooth(float value){value=dev.jujutsuvfx.platform.Numbers.clamp(value,0,1);return value*value*(3-2*value);}
    private static double hash(double n){return fract(Math.sin(n*12.9898)*43758.5453);}
    private static double fract(double n){return n-Math.floor(n);}
}
