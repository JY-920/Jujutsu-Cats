package dev.jujutsuvfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.client.ClientShrineFocus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
abstract class ShrineSilhouetteMixin {
    @ModifyVariable(method="render",at=@At("HEAD"),ordinal=0,argsOnly=true)
    private MultiBufferSource jujutsu$inkVictim(MultiBufferSource value,Entity entity,double x,double y,double z,
                                               float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        return ClientShrineFocus.tint(entity,value);
    }
}
