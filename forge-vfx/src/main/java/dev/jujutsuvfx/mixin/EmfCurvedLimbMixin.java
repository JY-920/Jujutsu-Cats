package dev.jujutsuvfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jujutsuvfx.client.CurvedCubeHolder;
import dev.jujutsuvfx.client.FlexibleLimbRenderer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity Model Features wraps every vanilla model part in EMFModelPart, whose compile() override never reaches
 * ModelPart.compile, so MartialLimbMixin's curved elbows/knees/torso were skipped. Hook the override as well.
 * Only applied when EMF is installed; the method is named by its runtime (SRG) name because EMF is not remapped.
 */
@Pseudo
@Mixin(targets="traben.entity_model_features.models.parts.EMFModelPart",remap=false)
abstract class EmfCurvedLimbMixin {
    @Inject(method="m_104290_(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void jujutsu$curvedSkin(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,float r,float g,float b,float a,CallbackInfo ci){
        ModelPart part=(ModelPart)(Object)this;
        if(FlexibleLimbRenderer.render(part,((CurvedCubeHolder)(Object)part).jujutsu$cubes(),pose,out,light,overlay,((int)(a*255)<<24)|((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(b*255)))ci.cancel();
    }
}
