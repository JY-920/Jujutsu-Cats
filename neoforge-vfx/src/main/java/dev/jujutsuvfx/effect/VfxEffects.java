package dev.jujutsuvfx.effect;

import dev.jujutsuvfx.JujutsuVfx;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** An explicit technique condition; ordinary attacks and attributes are unaffected. */
public final class VfxEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, JujutsuVfx.ID);
    private static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, JujutsuVfx.ID);

    public static final int AMPLIFICATION_DURATION = 3 * 60 * 20;
    public static final DeferredHolder<MobEffect, MobEffect> TECHNIQUE_AMPLIFICATION =
            EFFECTS.register("technique_amplification", Amplification::new);
    public static final DeferredHolder<Potion, Potion> AMPLIFICATION_POTION =
            POTIONS.register("technique_amplification", () -> new Potion(
                    "jujutsuvfx.technique_amplification",
                    new MobEffectInstance(TECHNIQUE_AMPLIFICATION, AMPLIFICATION_DURATION)));

    private VfxEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
        POTIONS.register(bus);
        NeoForge.EVENT_BUS.addListener(VfxEffects::brewing);
    }

    public static boolean amplified(LivingEntity entity) {
        return entity != null && entity.hasEffect(TECHNIQUE_AMPLIFICATION);
    }

    public static ItemStack amplificationPotion() {
        return PotionContents.createItemStack(Items.POTION, AMPLIFICATION_POTION);
    }

    private static void brewing(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(Potions.AWKWARD, Items.AMETHYST_SHARD, AMPLIFICATION_POTION);
    }

    private static final class Amplification extends MobEffect {
        private Amplification() {
            super(MobEffectCategory.BENEFICIAL, 0x9369F2);
        }
    }
}
