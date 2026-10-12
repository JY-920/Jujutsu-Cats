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
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/** An explicit technique condition; ordinary attacks and attributes are unaffected. */
public final class VfxEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, JujutsuVfx.ID);
    private static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, JujutsuVfx.ID);

    public static final int AMPLIFICATION_DURATION = 3 * 60 * 20;
    public static final RegistryObject<MobEffect> TECHNIQUE_AMPLIFICATION =
            EFFECTS.register("technique_amplification", Amplification::new);
    public static final RegistryObject<Potion> AMPLIFICATION_POTION =
            POTIONS.register("technique_amplification", () -> new Potion(
                    "jujutsuvfx.technique_amplification",
                    new MobEffectInstance(TECHNIQUE_AMPLIFICATION.get(), AMPLIFICATION_DURATION)));

    private VfxEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
        POTIONS.register(bus);
        bus.addListener(VfxEffects::brewing);
    }

    public static boolean amplified(LivingEntity entity) {
        return entity != null && entity.hasEffect(TECHNIQUE_AMPLIFICATION.get());
    }

    public static ItemStack amplificationPotion() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), AMPLIFICATION_POTION.get());
    }

    private static void brewing(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                new net.minecraftforge.common.brewing.IBrewingRecipe() {
                    public boolean isInput(ItemStack stack) { return stack.is(Items.POTION)&&PotionUtils.getPotion(stack)==Potions.AWKWARD; }
                    public boolean isIngredient(ItemStack stack) { return stack.is(Items.AMETHYST_SHARD); }
                    public ItemStack getOutput(ItemStack input,ItemStack ingredient) {
                        return isInput(input)&&isIngredient(ingredient)?amplificationPotion():ItemStack.EMPTY;
                    }
                }));
    }

    private static final class Amplification extends MobEffect {
        private Amplification() {
            super(MobEffectCategory.BENEFICIAL, 0x9369F2);
        }
    }
}
