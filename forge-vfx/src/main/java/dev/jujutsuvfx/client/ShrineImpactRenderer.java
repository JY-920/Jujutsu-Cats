package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Short local shock fronts sampled from the completed domain, including its sky and cuts. */
final class ShrineImpactRenderer {
    private static ShaderInstance shader;
    static void setShader(ShaderInstance value) { shader = value; }
    static boolean ready() { return shader != null; }

    static void render(RenderLevelStageEvent event, ShrineFeedback feedback) {
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.level == null || mc.options.screenEffectScale().get() < .001
                || CatCinematicCamera.steadyShrineShot()) return;
        long now = ClientEffectClock.ticks();
        float partial = event.getPartialTick();
        var pulses = feedback.active(now, partial);
        if (pulses.isEmpty()) return;
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        Vec3 camera = event.getCamera().getPosition();
        for (int i=0; i<4; i++) {
            shader.safeGetUniform("Impact"+i).set(0f,0f,0f,0f);
            shader.safeGetUniform("Axis"+i).set(1f,0f,0f,0f);
        }
        int count = 0;
        for (int index = pulses.size()-1; index>=0 && count<4; index--) {
            ShrineFeedback.Impulse p = pulses.get(index);
            float age = p.age(now, partial), strength = p.envelope(age)*p.power();
            if (strength < .002f) continue;
            Vec3 relative = p.point().subtract(camera);
            Vector4f origin = project(projection, relative);
            float x, y, dx=1, dy=0;
            if (relative.lengthSqr() < 4) { x=.5f; y=.5f; }
            else {
                if (origin.w < .05f) continue;
                x=origin.x/origin.w*.5f+.5f; y=origin.y/origin.w*.5f+.5f;
                if (x<-.3 || x>1.3 || y<-.3 || y>1.3) continue;
                Vector4f tip = project(projection, relative.add(p.direction().scale(2)));
                if (tip.w > .05f) {
                    dx=tip.x/tip.w*.5f+.5f-x; dy=tip.y/tip.w*.5f+.5f-y;
                }
            }
            shader.safeGetUniform("Impact"+count).set(x,y,age/p.duration(),strength);
            shader.safeGetUniform("Axis"+count).set(dx,dy,p.seed(),p.duration());
            count++;
        }
        if (count == 0) return;
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            if (!WaterSceneCapture.capture()) return;
            shader.setSampler("SceneColor", WaterSceneCapture.color());
            shader.setSampler("SceneDepth", WaterSceneCapture.depth());
            shader.safeGetUniform("Strength").set(mc.options.screenEffectScale().get().floatValue());
            RenderSystem.setShader(() -> shader);
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.disableBlend();
            BufferBuilder b=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            b.vertex(-1,-1,1).uv(0,0).color(-1).endVertex();
            b.vertex( 1,-1,1).uv(1,0).color(-1).endVertex();
            b.vertex( 1, 1,1).uv(1,1).color(-1).endVertex();
            b.vertex(-1, 1,1).uv(0,1).color(-1).endVertex();
            BufferUploader.drawWithShader(b.end());
        }
    }
    private static Vector4f project(Matrix4f matrix, Vec3 point) {
        return matrix.transform(new Vector4f((float)point.x,(float)point.y,(float)point.z,1));
    }
}
