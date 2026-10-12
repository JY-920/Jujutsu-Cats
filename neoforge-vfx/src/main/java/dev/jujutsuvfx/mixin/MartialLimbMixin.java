package dev.jujutsuvfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jujutsuvfx.client.FlexibleLimbRenderer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

// Embeddium also intercepts compile at HEAD; curved limbs must precede its rigid-cuboid fast path.
@Mixin(value=ModelPart.class,priority=1100)
abstract class MartialLimbMixin {
    @Shadow @Final private List<ModelPart.Cube> cubes;
    @Inject(method="compile",at=@At("HEAD"),cancellable=true)
    private void jujutsu$curvedSkin(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci){
        if(FlexibleLimbRenderer.render((ModelPart)(Object)this,cubes,pose,out,light,overlay,color))ci.cancel();
    }
    @Inject(method="copyFrom",at=@At("RETURN"))
    private void jujutsu$copyJoint(ModelPart source,CallbackInfo ci){FlexibleLimbRenderer.copy(source,(ModelPart)(Object)this);}
}
