package dev.jujutsuvfx.entity;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VfxEntities {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, JujutsuVfx.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<GojoCat>> GOJO_CAT = TYPES.register("gojo_cat",
            () -> EntityType.Builder.of(GojoCat::new, MobCategory.CREATURE)
                    .sized(.9f, 1.25f).eyeHeight(.96f).clientTrackingRange(10).updateInterval(3)
                    .build(JujutsuVfx.ID + ":gojo_cat"));
    public static final DeferredHolder<EntityType<?>, EntityType<SukunaCat>> SUKUNA_CAT = TYPES.register("sukuna_cat",
            () -> EntityType.Builder.of(SukunaCat::new, MobCategory.CREATURE)
                    .sized(.9f, 1.25f).eyeHeight(.96f).clientTrackingRange(10).updateInterval(3)
                    .build(JujutsuVfx.ID + ":sukuna_cat"));
    public static final DeferredHolder<EntityType<?>, EntityType<MahoragaCat>> MAHORAGA_CAT = TYPES.register("mahoraga_cat",
            () -> EntityType.Builder.of(MahoragaCat::new, MobCategory.CREATURE)
                    .sized(1.17f, 1.625f).eyeHeight(1.248f).clientTrackingRange(10).updateInterval(3)
                    .build(JujutsuVfx.ID + ":mahoraga_cat"));

    private VfxEntities() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(VfxEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOJO_CAT.get(), GojoCat.createAttributes().build());
        event.put(SUKUNA_CAT.get(), SukunaCat.createAttributes().build());
        event.put(MAHORAGA_CAT.get(), MahoragaCat.createAttributes().build());
    }
}
