package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.VfxEntities;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;


import java.io.IOException;

@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientHooks {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VfxEntities.GOJO_CAT.get(), context -> new SorcererCatRenderer<>(context, false));
        event.registerEntityRenderer(VfxEntities.SUKUNA_CAT.get(), context -> new SorcererCatRenderer<>(context, true));
        event.registerEntityRenderer(VfxEntities.MAHORAGA_CAT.get(), context -> new SorcererCatRenderer<>(context, "mahoraga_cat"));
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc,parent)->new VfxConfigScreen(parent)));
        event.enqueueWork(() -> { ClientEvents.installSkillItems(); SukunaSlashHands.install(); PlayfulCloudHands.install(); });
    }
    @SubscribeEvent
    public static void keyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        VfxConfigScreen.registerKeys(event);
        CatCinematicCamera.registerKeys(event);
    }
    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) { CursedFistHands.addLayers(event); PurpleChargeHands.addLayers(event); }
    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws IOException {
        RenderDiagnostics.beginReload();
        registerLighting(event);
        registerInfinity(event);
        BlastWarningRenderer.setShader(null);
        load(event, "blast_warning", DefaultVertexFormat.POSITION_TEX_COLOR, BlastWarningRenderer::setShader);
        MahoragaBladeRenderer.setShader(null);
        try {
            load(event, "mahoraga_blade", DefaultVertexFormat.POSITION_TEX_COLOR, MahoragaBladeRenderer::setShader);
        }catch(IOException failure){com.mojang.logging.LogUtils.getLogger().warn("Could not load Mahoraga blade trails",failure);}
        CatFistEffects.setShader(null);
        try{
            load(event, "cat_fist_technique", DefaultVertexFormat.POSITION_TEX_COLOR, CatFistEffects::setShader);
        }catch(IOException failure){com.mojang.logging.LogUtils.getLogger().warn("Could not load Blue/Red fist effects; other VFX will still load",failure);}
        load(event, "shrine_focus", DefaultVertexFormat.POSITION_TEX_COLOR, ClientShrineFocus::setShader);
        load(event, "unlimited_purple_volume", DefaultVertexFormat.POSITION_TEX_COLOR, UnlimitedPurpleEffects::setVolume);
        load(event, "unlimited_purple_composite", DefaultVertexFormat.POSITION_TEX_COLOR, UnlimitedPurpleEffects::setComposite);
        FirstPersonSkin.setShader(null);
        try{
            load(event, "first_person_skin", DefaultVertexFormat.NEW_ENTITY, FirstPersonSkin::setShader);
        }catch(IOException failure){com.mojang.logging.LogUtils.getLogger().warn("First-person skin pass unavailable; using native hands",failure);}
        load(event, "cat_technique", DefaultVertexFormat.POSITION_TEX_COLOR, CatTechniqueEffects::setShader);
        load(event, "red_technique", DefaultVertexFormat.POSITION_TEX_COLOR, RedRenderer::setShader);
        load(event, "red_hand", DefaultVertexFormat.POSITION_TEX_COLOR, RedRenderer::setHandShader);
        load(event, "purple_hand", DefaultVertexFormat.POSITION_TEX_COLOR, PurpleChargeHands::setShader);
        load(event, "technique_accent", DefaultVertexFormat.POSITION_TEX_COLOR, TechniqueAccents::setShader);
        load(event, "fist_held", DefaultVertexFormat.POSITION_TEX_COLOR, FistHeldRenderer::setShader);
        load(event, "fist_ink", DefaultVertexFormat.POSITION_TEX_COLOR, FistSpriteRenderer::setShader);
        load(event, "limitless_volume", DefaultVertexFormat.POSITION_TEX_COLOR, LimitlessRenderer::setVolume);
        load(event, "limitless_composite", DefaultVertexFormat.POSITION_TEX_COLOR, LimitlessRenderer::setComposite);
        load(event, "limitless_stream", DefaultVertexFormat.POSITION_TEX_COLOR, LimitlessStreams::setShader);
        load(event, "furnace_volume", DefaultVertexFormat.POSITION_TEX_COLOR, FurnaceRenderer::setVolume);
        load(event, "furnace_composite", DefaultVertexFormat.POSITION_TEX_COLOR, FurnaceRenderer::setComposite);
        load(event, "void_stream", DefaultVertexFormat.POSITION_TEX_COLOR, VoidStreamRenderer::setShader);
        load(event, "domain_clash", DefaultVertexFormat.POSITION_TEX_COLOR, DomainClashRenderer::setComposite);
        load(event, "domain_clash_particle", DefaultVertexFormat.POSITION_TEX_COLOR, DomainClashRenderer::setParticles);
        load(event, "shrine_impact", DefaultVertexFormat.POSITION_TEX_COLOR, ShrineImpactRenderer::setShader);
        load(event, "unlimited_void", DefaultVertexFormat.POSITION_TEX_COLOR, UnlimitedVoidRenderer::setShader);
        load(event, "void_terrain_clear", DefaultVertexFormat.POSITION_TEX_COLOR, DomainWorldCapture::setShader);
        load(event, "slash_batch", DefaultVertexFormat.POSITION_TEX_COLOR, SlashBatchRenderer::setShader);
        load(event, "rift_edge", DefaultVertexFormat.POSITION_TEX_COLOR, RiftEdgeRenderer::setShader);
        load(event, "domain_water", DefaultVertexFormat.POSITION_TEX_COLOR, WaterDomainRenderer::setWater);
        load(event, "water_reflection", DefaultVertexFormat.POSITION_TEX_COLOR, WaterDomainRenderer::setReflection);
        load(event, "persistent_scar", DefaultVertexFormat.POSITION_TEX_COLOR, PersistentScarRenderer::setShader);
        load(event, "spatial_rift", DefaultVertexFormat.POSITION_TEX_COLOR, RiftRenderer::setShader);
        load(event, "shrine_geometry", DefaultVertexFormat.POSITION_TEX_COLOR, DomainRenderer::setGeometry);
        load(event, "domain_sky", DefaultVertexFormat.POSITION_TEX_COLOR, DomainSkyRenderer::setShader);
    }

    private static void load(RegisterShadersEvent event,String name,com.mojang.blaze3d.vertex.VertexFormat format,
                             java.util.function.Consumer<ShaderInstance> setter) throws IOException {
        setter.accept(null);
        try { event.registerShader(new EffectShader(event.getResourceProvider(),new ResourceLocation(JujutsuVfx.ID,name),format),setter); }
        catch(IOException | RuntimeException failure) {
            RenderDiagnostics.fail("shader "+name+": "+failure.getMessage());
            com.mojang.logging.LogUtils.getLogger().warn("Could not load original VFX material {}; inspect this shader error and the graphics diagnostic",name,failure);
        }
    }

    private static void registerInfinity(RegisterShadersEvent event) {
        InfinityBarrierRenderer.setCross(null);
        try {
            load(event, "recovery_cross", DefaultVertexFormat.POSITION_TEX_COLOR, InfinityBarrierRenderer::setCross);
        } catch(IOException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not load recovery cross shader; model outlines will still load",failure);
        }
        try {
            ShaderInstance mask = new EffectShader(event.getResourceProvider(),
                    new ResourceLocation(JujutsuVfx.ID,"infinity_mask"),DefaultVertexFormat.NEW_ENTITY);
            ShaderInstance outline;
            try {
                outline = new EffectShader(event.getResourceProvider(),
                        new ResourceLocation(JujutsuVfx.ID,"infinity_boundary"),DefaultVertexFormat.POSITION_TEX_COLOR);
            } catch(IOException failure) { mask.close(); throw failure; }
            event.registerShader(mask,InfinityBarrierRenderer::setMask);
            event.registerShader(outline,InfinityBarrierRenderer::setShader);
        } catch(IOException failure) {
            InfinityBarrierRenderer.setMask(null);InfinityBarrierRenderer.setShader(null);
            com.mojang.logging.LogUtils.getLogger().warn("Could not load Infinity silhouette shaders; other VFX will still load",failure);
        }
    }

    private static void registerLighting(RegisterShadersEvent event) {
        // Lighting is optional: a resource-pack/driver shader error must not prevent
        // the established hand, domain and projectile materials from reloading.
        try {
            ShaderInstance surface = new EffectShader(event.getResourceProvider(),
                    new ResourceLocation(JujutsuVfx.ID, "technique_light"), DefaultVertexFormat.POSITION_TEX_COLOR);
            ShaderInstance composite;
            try {
                composite = new EffectShader(event.getResourceProvider(),
                        new ResourceLocation(JujutsuVfx.ID, "technique_light_composite"), DefaultVertexFormat.POSITION_TEX_COLOR);
            } catch (IOException failure) { surface.close(); throw failure; }
            event.registerShader(surface, TechniqueLighting::setSurface);
            event.registerShader(composite, TechniqueLighting::setComposite);
        } catch (IOException failure) {
            TechniqueLighting.setSurface(null); TechniqueLighting.setComposite(null);
            com.mojang.logging.LogUtils.getLogger().warn("Could not load technique lighting shaders; other VFX will still load", failure);
        }
    }
}
