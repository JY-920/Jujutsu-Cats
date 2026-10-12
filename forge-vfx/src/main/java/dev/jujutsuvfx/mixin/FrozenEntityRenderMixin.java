package dev.jujutsuvfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.client.ClientEntityStasis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
abstract class FrozenEntityRenderMixin {
    @ModifyVariable(method="render",at=@At("HEAD"),ordinal=1,argsOnly=true)
    private float jujutsu$holdInterpolation(float value,Entity entity,double x,double y,double z,float yaw,
                                            float partial,PoseStack poses,MultiBufferSource buffers,int light){
        return ClientEntityStasis.renderPartial(entity,value);
    }
}
