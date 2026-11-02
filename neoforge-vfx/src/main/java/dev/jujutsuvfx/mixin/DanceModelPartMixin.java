package dev.jujutsuvfx.mixin;

import dev.jujutsuvfx.client.NativeDanceRig;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
abstract class DanceModelPartMixin {
    @Inject(method="<init>", at=@At("TAIL"))
    private void jujutsu$rememberDanceBones(List<ModelPart.Cube> cubes, Map<String, ModelPart> children, CallbackInfo ci) {
        NativeDanceRig.observe((ModelPart)(Object)this, cubes, children);
    }
}
