package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

public final class SorcererCatRenderer<T extends SorcererCat> extends MobRenderer<T, SorcererCatModel<T>> {
    private final ResourceLocation catTexture, detailsTexture;
    private final ItemRenderer itemRenderer;
    private final ItemStack transformationProp;

    public SorcererCatRenderer(EntityRendererProvider.Context context, boolean sukuna) {
        this(context, sukuna ? "sukuna_cat" : "gojo_cat");
    }
    public SorcererCatRenderer(EntityRendererProvider.Context context, String name) {
        super(context, new SorcererCatModel<>(context.bakeLayer(ModelLayers.CAT), context.getResourceManager(), name), name.equals("mahoraga_cat")?.85f:.65f);
        catTexture = texture(name);
        detailsTexture = texture(name + "_details");
        itemRenderer = context.getItemRenderer();
        transformationProp = new ItemStack(name.equals("sukuna_cat") ? VfxItems.SUKUNA_FINGER.get()
                : name.equals("mahoraga_cat") ? VfxItems.MAHORAGA_WHEEL.get() : VfxItems.GOJO_TEACHER.get());
        addLayer(new DetailsLayer<>(this));
        addLayer(new BladeLayer<>(this));
        addLayer(new WheelLayer<>(this));
        addLayer(new AuraLayer<>(this));
        addLayer(new InfinityLayer<>(this));
    }

    @Override public ResourceLocation getTextureLocation(T entity) { return catTexture; }

    @Override protected void scale(T entity, PoseStack poses, float partialTick) {
        // Same resting model scale used by laowu's ButterCatRenderer.
        float size = entity.isTransforming() ? 1f : entity.isMahoraga() ? 1.95f : 1.5f;
        poses.scale(size, size, size);
    }

    @Override public boolean shouldRender(T cat, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(cat, frustum, x, y, z)
                || (cat.isTransforming() || cat.hasWheel()) && cat.shouldRender(x, y, z)
                && frustum.isVisible(cat.getBoundingBox().expandTowards(0, 6.5, 0).inflate(1.5));
    }

    @Override public void render(T cat, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        try{
            MultiBufferSource visible=buffers;
            if(cat.isMahoraga()&&cat.combatMove()==dev.jujutsuvfx.core.CatCombatMoves.MAHORAGA_ARRIVAL){
                float reveal=dev.jujutsuvfx.core.ShadowMotion.reveal(cat.punchAge(partialTick),
                        cat.punchKind()==dev.jujutsuvfx.core.CatCombatMoves.SHADOW_RETREAT);
                if(reveal<=.001f)return;
                visible=ShadowClipBuffer.above(buffers,new org.joml.Matrix4f(poses.last().pose()));
                // The invisible part stays below the portal, even when seen from beneath in midair.
                poses.translate(0,-5.2*(1-reveal),0);
            }
            super.render(cat, yaw, partialTick, poses, visible, light);
        }finally{poses.popPose();}
        if (!cat.isTransforming() || cat.isInvisible()) return;
        float progress = cat.transformationProgress(partialTick);
        float eased = progress * progress * (3 - 2 * progress);
        float elapsed = progress * SorcererCat.TRANSFORMATION_TICKS;
        float size = 2.35f + Mth.sin(elapsed * .18f) * .06f;
        poses.pushPose();
        try {
            // Match the reference's three-second rotating prop descent; this is rendered
            // with the cat, not a real dropped item that can be picked up or duplicated.
            poses.translate(0, Mth.lerp(eased, 5.2, cat.isBaby() ? .36 : .72), 0);
            poses.mulPose(Axis.YP.rotationDegrees(elapsed * 5));
            poses.mulPose(Axis.XP.rotationDegrees(18));
            poses.scale(size, size, size);
            itemRenderer.renderStatic(transformationProp, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                    poses, buffers, cat.level(), cat.getId());
        } finally { poses.popPose(); }
    }

    @Override protected void setupRotations(T cat, PoseStack poses, float age, float yaw, float partial) {
        super.setupRotations(cat, poses, age, yaw, partial);
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(JujutsuVfx.ID, "textures/entity/" + name + ".png");
    }

    private static final class DetailsLayer<T extends SorcererCat> extends RenderLayer<T, SorcererCatModel<T>> {
        private final SorcererCatRenderer<T> renderer;
        DetailsLayer(SorcererCatRenderer<T> parent) { super(parent); renderer = parent; }

        @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, T cat,
                                     float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
            if (cat.isInvisible()) return;
            SorcererCatModel<T> model = getParentModel();
            if (!cat.isTransforming())
                model.renderMaterial(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(renderer.detailsTexture)), light,
                        LivingEntityRenderer.getOverlayCoords(cat, 0), -1, SorcererCatGeometry.DETAILS);
            // Only the four supplied eye pixels enter the emissive pass. The cloth's depth
            // still occludes them while it slides away, so glow does not shine through the band.
            if (cat.isGojo()) model.renderMaterial(poses, buffers.getBuffer(RenderType.eyes(renderer.catTexture)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, -1, SorcererCatGeometry.EYES);
        }
    }

    private static final class BladeLayer<T extends SorcererCat> extends RenderLayer<T,SorcererCatModel<T>> {
        BladeLayer(SorcererCatRenderer<T> parent){super(parent);}
        @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,T cat,
                                     float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch){
            if(!cat.isMahoraga()||cat.isInvisible()||cat.isTransforming())return;
            poses.pushPose();
            try {
                if(cat.isBaby()){poses.scale(.5f,.5f,.5f);poses.translate(0,1.5f,0);}
                getParentModel().handPose(poses,SorcererCat.RIGHT);
                MahoragaBladeModel.render(poses,buffers,light,LivingEntityRenderer.getOverlayCoords(cat,0));
            }finally{poses.popPose();}
        }
    }

    private static final class WheelLayer<T extends SorcererCat> extends RenderLayer<T, SorcererCatModel<T>> {
        private static final ResourceLocation TEXTURE = texture("mahoraga_cat_details");
        WheelLayer(SorcererCatRenderer<T> parent) { super(parent); }
        @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, T cat,
                                      float swing, float amount, float partial, float age, float yaw, float pitch) {
            if (!cat.hasWheel() || cat.isInvisible()) return;
            long clock = ClientEffectClock.ticks();
            float progress = cat.wheelProgress(partial, clock);
            getParentModel().renderWheel(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                    light, LivingEntityRenderer.getOverlayCoords(cat,0), progress);
            float glow = cat.adaptationFlash(clock, partial);
            if (dev.jujutsuvfx.config.VfxConfig.flashes() && glow > .01f) {
                // Eyes use additive RGB: fade the light itself instead of alpha. Only the wheel glows.
                int color = 0xff000000 | Math.round(180 * glow) << 16 | Math.round(130 * glow) << 8 | Math.round(40 * glow);
                getParentModel().renderWheel(poses, buffers.getBuffer(RenderType.eyes(TEXTURE)),
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, progress, color);
            }
        }
    }
    private static final class InfinityLayer<T extends SorcererCat> extends RenderLayer<T, SorcererCatModel<T>> {
        private final SorcererCatRenderer<T> renderer;
        InfinityLayer(SorcererCatRenderer<T> parent){super(parent);renderer=parent;}
        @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,T cat,
                                     float swing,float amount,float partial,float age,float yaw,float pitch){
            InfinityBarrierRenderer.capture(cat,getParentModel(),poses,partial,renderer.catTexture,renderer.detailsTexture);
        }
    }
    private static final class AuraLayer<T extends SorcererCat> extends RenderLayer<T, SorcererCatModel<T>> {
        private static final FistAuraRenderer.ArmShape PAW = new FistAuraRenderer.ArmShape(new Vector3f(),
                new Vector3f(-1 / 16f, -9 / 16f, -1 / 16f), new Vector3f(1 / 16f, 1 / 16f, 1 / 16f));
        AuraLayer(SorcererCatRenderer<T> parent) { super(parent); }
        @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, T cat,
                                     float limbSwing, float amount, float partial, float age, float yaw, float pitch) {
            if (cat.isMahoraga() || cat.isInvisible() || cat.isTransforming() || ClientShrineFocus.silhouette(cat)) return;
            if(dev.jujutsuvfx.core.CatCombatMoves.spell(cat.combatMove())){
                // Spell catalysts follow the animated paw. Ordinary fist presets return after the cast.
                int hand=cat.punchHand(),move=cat.combatMove();
                if(cat.handUnavailable(hand))return;
                poses.pushPose();
                try {
                    if(cat.isBaby()){poses.scale(.5f,.5f,.5f);poses.translate(0,1.5f,0);}
                    getParentModel().handPose(poses,hand);
                    if(move==dev.jujutsuvfx.core.CatCombatMoves.PURPLE_CAST)CatSpells.purpleHand(poses,cat,partial,false);
                    if(move==dev.jujutsuvfx.core.CatCombatMoves.BLUE_CAST||move==dev.jujutsuvfx.core.CatCombatMoves.RED_CAST){
                        int kind=move==dev.jujutsuvfx.core.CatCombatMoves.BLUE_CAST?dev.jujutsuvfx.core.CatSpell.BLUE:dev.jujutsuvfx.core.CatSpell.RED;
                        float frame=cat.punchAge(partial),launch=dev.jujutsuvfx.core.CatSpell.launchTick(kind,(cat.punchKind()&1)!=0);
                        float power=Mth.clamp(frame/6,0,1)*(1-Mth.clamp((frame-launch)/5,0,1));
                        if(power>.01f)CatTechniqueEffects.hand(poses,frame*.05f,.75f*power,cat,hand,kind==dev.jujutsuvfx.core.CatSpell.RED);
                    }
                }finally{poses.popPose();}
                if(move==dev.jujutsuvfx.core.CatCombatMoves.PURPLE_CAST&&(cat.punchKind()&2)!=0&&!cat.handUnavailable(hand==SorcererCat.LEFT?SorcererCat.RIGHT:SorcererCat.LEFT)){
                    poses.pushPose();
                    try{
                        if(cat.isBaby()){poses.scale(.5f,.5f,.5f);poses.translate(0,1.5f,0);}
                        getParentModel().handPose(poses,hand==SorcererCat.LEFT?SorcererCat.RIGHT:SorcererCat.LEFT);
                        CatSpells.purpleHand(poses,cat,partial,true);
                    }finally{poses.popPose();}
                }
                return;
            }
            for (int hand : new int[]{SorcererCat.LEFT, SorcererCat.RIGHT}) {
                if (cat.handUnavailable(hand)) continue;
                boolean blue = cat.hasEnergy(SorcererCat.BLUE, hand), red = cat.hasEnergy(SorcererCat.RED, hand);
                boolean cursed = cat.hasEnergy(SorcererCat.CURSED, hand), blackFlash = cat.hasEnergy(SorcererCat.BLACK_FLASH, hand);
                if (!blue && !red && !cursed && !blackFlash) continue;
                poses.pushPose();
                try {
                    if (cat.isBaby()) { poses.scale(.5f, .5f, .5f); poses.translate(0, 1.5f, 0); }
                    getParentModel().handPose(poses, hand);
                    float power = cat.punching() && cat.punchHand() == hand ? 1.45f : 1.1f;
                    HumanoidArm arm = hand == SorcererCat.LEFT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                    float time = ((ClientEffectClock.ticks() % 24000) + partial) * .05f;
                    if (cursed) FistAuraRenderer.drawMob(poses, time, power, cat, arm, partial, PAW, false);
                    if (blackFlash) FistAuraRenderer.drawMob(poses, time + .17f, power, cat, arm, partial, PAW, true);
                    if (blue) CatFistEffects.hand(poses, time, power, cat, hand, false);
                    if (red) CatFistEffects.hand(poses, time + .17f, power, cat, hand, true);
                } finally { poses.popPose(); }
            }
        }
    }
}
