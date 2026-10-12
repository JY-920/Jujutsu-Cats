package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Far-depth sky replacement. Does not read Iris color/depth textures or modify its settings. */
final class DomainSkyRenderer {
    private static ShaderInstance shader;

    static void setShader(ShaderInstance value) { shader = value; }
    static boolean ready() { return shader != null; }

    static void render(RenderLevelStageEvent event, MalevolentShrine shrine) {
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.level == null) return;
        long tick = ClientEffectClock.ticks();
        float partial = event.getPartialTick();
        float strength = shrine.visibility(tick,partial) * shrine.expansion(tick,partial);
        if (strength < 0.001f) return;
        ShaderInstance previous = RenderSystem.getShader();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int sr = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dr = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sa = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), da = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            shader.safeGetUniform("InverseViewProjection").set(new Matrix4f(event.getProjectionMatrix())
                    .mul(event.getPoseStack().last().pose()).invert());
            shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()));
            var relative=shrine.center.subtract(event.getCamera().getPosition());
            shader.safeGetUniform("CenterRelative").set((float)relative.x,(float)relative.y,(float)relative.z);
            shader.safeGetUniform("BoundaryRadius").set(Math.max(.1f,shrine.radius*shrine.expansion(tick,partial)));
            shader.safeGetUniform("Enclosed").set(0f);
            shader.safeGetUniform("SoftHorizon").set(1f);
            shader.safeGetUniform("InteriorView").set(CatSpells.interior(shrine,event.getCamera().getPosition(),tick,partial)?1f:0f);
            shader.safeGetUniform("Time").set(shrine.age(tick,partial));
            shader.safeGetUniform("Strength").set(strength);
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferBuilder b = dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            b.vertex(-1,-1,1).uv(0,0).color(-1).endVertex();
            b.vertex(1,-1,1).uv(1,0).color(-1).endVertex();
            b.vertex(1,1,1).uv(1,1).color(-1).endVertex();
            b.vertex(-1,1,1).uv(0,1).color(-1).endVertex();
            BufferUploader.drawWithShader(b.end());
        } finally {
            RenderSystem.depthMask(write); RenderSystem.depthFunc(depthFunc);
            if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(sr,dr,sa,da);
            if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previous);
        }
    }
}
