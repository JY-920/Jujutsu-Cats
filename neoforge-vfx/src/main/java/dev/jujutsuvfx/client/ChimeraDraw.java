package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;

/** Loader-specific bone draw call (packed ARGB on 1.21). */
final class ChimeraDraw {
    private ChimeraDraw() {}
    static void render(ModelPart part, PoseStack pose, VertexConsumer out, int light, int overlay, int argb) {
        part.render(pose, out, light, overlay, argb);
    }
}
