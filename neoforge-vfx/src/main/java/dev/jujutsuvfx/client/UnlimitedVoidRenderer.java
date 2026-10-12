package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Finite gameplay/occlusion volume, with an infinite directional cosmic background. */
final class UnlimitedVoidRenderer {
    private static ShaderInstance shader;
    static void setShader(ShaderInstance value) { shader = value; }
    static boolean ready() { return shader != null; }

    static void render(RenderLevelStageEvent event, UnlimitedVoid domain) {
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.level == null) return;
        long tick = ClientEffectClock.ticks();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float radius = domain.radius * domain.expansion(tick, partial);
        float visibility = domain.visibility(tick, partial);
        Vec3 camera = event.getCamera().getPosition();
        if (visibility < .001f) return;
        boolean interior=CatSpells.interior(domain,camera,tick,partial);
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            boolean sceneReady = WaterSceneCapture.capture();
            DomainWorldCapture.bind(shader,true);
            if (sceneReady) {
                shader.setSampler("SceneColor", WaterSceneCapture.color());
                shader.setSampler("SceneDepth", WaterSceneCapture.depth());
            }
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
            shader.safeGetUniform("ViewProjection").set(projection);
            shader.safeGetUniform("InverseViewProjection").set(new Matrix4f(projection).invert());
            // A sky direction must not contain camera translation or a finite sphere surface position.
            Matrix4f skyView = new Matrix4f(event.getModelViewMatrix()).setTranslation(0,0,0);
            shader.safeGetUniform("SkyInverseViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(skyView).invert());
            Vec3 center = domain.center.subtract(camera);
            shader.safeGetUniform("CenterRelative").set((float)center.x, (float)center.y, (float)center.z);
            shader.safeGetUniform("Axis").set((float)domain.axis.x, (float)domain.axis.y, (float)domain.axis.z);
            shader.safeGetUniform("Radius").set(Math.max(radius,.001f));
            shader.safeGetUniform("InteriorView").set(interior?1f:0f);
            shader.safeGetUniform("BreakProgress").set(DomainBarrierEffects.progress(domain,partial));
            shader.safeGetUniform("BreakStyle").set(DomainBarrierEffects.style(domain));
            MalevolentShrine attacker=DomainBarrierEffects.attacker(domain);
            Vec3 outer=attacker==null?Vec3.ZERO:attacker.center.subtract(camera);
            shader.safeGetUniform("OuterCenter").set((float)outer.x,(float)outer.y,(float)outer.z);
            shader.safeGetUniform("OuterRadius").set(attacker==null?0f:attacker.radius*attacker.expansion(tick,partial));
            shader.safeGetUniform("Time").set(domain.age(tick, partial));
            shader.safeGetUniform("Visibility").set(visibility);
            shader.safeGetUniform("Enclosed").set(domain.enclosed ? 1f : 0f);
            shader.safeGetUniform("UseScene").set(sceneReady ? 1f : 0f);
            shader.safeGetUniform("ScreenEffects").set(mc.options.screenEffectScale().get().floatValue());
            shader.safeGetUniform("FlashEnabled").set(dev.jujutsuvfx.config.VfxConfig.flashScale());
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(sceneReady?GL11.GL_ALWAYS:GL11.GL_LEQUAL);
            RenderSystem.depthMask(!interior);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            b.addVertex(-1, -1, 1).setUv(0, 0).setColor(-1);
            b.addVertex( 1, -1, 1).setUv(1, 0).setColor(-1);
            b.addVertex( 1,  1, 1).setUv(1, 1).setColor(-1);
            b.addVertex(-1,  1, 1).setUv(0, 1).setColor(-1);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        if(interior)VoidStreamRenderer.render(event,domain);
    }
}
