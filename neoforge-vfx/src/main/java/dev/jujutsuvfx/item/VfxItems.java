package dev.jujutsuvfx.item;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.VfxEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VfxItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JujutsuVfx.ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JujutsuVfx.ID);
    public static final DeferredItem<CursedFistItem> CURSED_FIST = ITEMS.register("cursed_fist", CursedFistItem::new);
    public static final DeferredItem<PlayfulCloudItem> PLAYFUL_CLOUD = ITEMS.register("playful_cloud", PlayfulCloudItem::new);
    public static final DeferredItem<YutaKatanaItem> YUTA_KATANA = ITEMS.register("yuta_katana", YutaKatanaItem::new);
    public static final DeferredItem<SkillItem> BLUE = skill("blue", SkillItem.Kind.BLUE);
    public static final DeferredItem<SkillItem> RED = skill("red", SkillItem.Kind.RED);
    public static final DeferredItem<SkillItem> PURPLE = skill("purple", SkillItem.Kind.PURPLE);
    public static final DeferredItem<SkillItem> FUGA = skill("fuga", SkillItem.Kind.FUGA);
    public static final DeferredItem<SukunaSlashItem> DISMANTLE = ITEMS.register("dismantle",
            () -> new SukunaSlashItem(SukunaSlashItem.Kind.DISMANTLE));
    public static final DeferredItem<SukunaSlashItem> CLEAVE = ITEMS.register("cleave",
            () -> new SukunaSlashItem(SukunaSlashItem.Kind.CLEAVE));
    public static final DeferredItem<SukunaSlashItem> WORLD_CUT = ITEMS.register("world_cut",
            () -> new SukunaSlashItem(SukunaSlashItem.Kind.WORLD_CUT));
    public static final DeferredItem<SkillItem> UNLIMITED_VOID = skill("unlimited_void", SkillItem.Kind.VOID);
    public static final DeferredItem<SkillItem> MALEVOLENT_SHRINE = skill("malevolent_shrine", SkillItem.Kind.SHRINE);
    public static final DeferredItem<InformationFragmentItem> INFORMATION_FRAGMENT = ITEMS.register("information_fragment", InformationFragmentItem::new);
    public static final DeferredItem<DeferredSpawnEggItem> GOJO_CAT_SPAWN_EGG = ITEMS.register("gojo_cat_spawn_egg",
            () -> new DeferredSpawnEggItem(VfxEntities.GOJO_CAT, 0xFDF9FB, 0x00E5FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> YUTA_CAT_SPAWN_EGG = ITEMS.register("yuta_cat_spawn_egg",
            () -> new DeferredSpawnEggItem(VfxEntities.YUTA_CAT, 0xF6F1E8, 0x353746, new Item.Properties()));
    public static final DeferredItem<GojoCatDebugStick> GOJO_CAT_DEBUG_STICK = ITEMS.register("gojo_cat_debug_stick", GojoCatDebugStick::new);
    public static final DeferredItem<DeferredSpawnEggItem> SUKUNA_CAT_SPAWN_EGG = ITEMS.register("sukuna_cat_spawn_egg",
            () -> new DeferredSpawnEggItem(VfxEntities.SUKUNA_CAT, 0xEAC1B8, 0x371620, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MAHORAGA_CAT_SPAWN_EGG = ITEMS.register("mahoraga_cat_spawn_egg",
            () -> new DeferredSpawnEggItem(VfxEntities.MAHORAGA_CAT, 0xEEE6CB, 0xCFA934, new Item.Properties()));
    public static final DeferredItem<GojoTeacherItem> GOJO_TEACHER = ITEMS.register("gojo_teacher", GojoTeacherItem::new);
    public static final DeferredItem<SummoningRelicItem> MAHORAGA_WHEEL = ITEMS.register("mahoraga_wheel", () -> new SummoningRelicItem("mahoraga_wheel"));
    public static final DeferredItem<SummoningRelicItem> SUKUNA_FINGER = ITEMS.register("sukuna_finger", () -> new SummoningRelicItem("sukuna_finger"));
    public static final DeferredItem<MaximumUzumakiItem> MAXIMUM_UZUMAKI = ITEMS.register("maximum_uzumaki", () -> new MaximumUzumakiItem());
    public static final DeferredItem<MaximumUzumakiItem> LOVE_BEAM = ITEMS.register("love_beam", () -> new MaximumUzumakiItem(true));
    public static final DeferredItem<CursedSpeechItem> CURSED_SPEECH = ITEMS.register("cursed_speech", CursedSpeechItem::new);
    public static final DeferredItem<DanceItem> SHUFFLE_DANCE = ITEMS.register("shuffle_dance", ()->new DanceItem(2));
    public static final DeferredItem<DanceItem> YUTA_DANCE = ITEMS.register("yuta_dance", ()->new DanceItem());
    public static final DeferredItem<YutaDomainItem> YUTA_DOMAIN = ITEMS.register("yuta_domain", YutaDomainItem::new);
    public static final DeferredItem<DeferredSpawnEggItem> CURSED_SPIRIT_SPAWN_EGG=ITEMS.register("cursed_spirit_spawn_egg",()->new DeferredSpawnEggItem(VfxEntities.CURSED_SPIRIT,0x2A1838,0xB04A7E,new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GETO_CAT_SPAWN_EGG=ITEMS.register("geto_cat_spawn_egg",()->new DeferredSpawnEggItem(VfxEntities.GETO_CAT,0x1D1728,0x8666A1,new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.jujutsuvfx"))
            .icon(() -> new ItemStack(PURPLE.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((parameters, output) -> {
                output.accept(CURSED_FIST.get());
                output.accept(PLAYFUL_CLOUD.get());
                output.accept(YUTA_KATANA.get());
                output.accept(BLUE.get());
                output.accept(RED.get());
                output.accept(PURPLE.get());
                output.accept(dev.jujutsuvfx.effect.VfxEffects.amplificationPotion());
                output.accept(FUGA.get());
                output.accept(DISMANTLE.get());
                output.accept(CLEAVE.get());
                output.accept(WORLD_CUT.get());
                output.accept(UNLIMITED_VOID.get());
                output.accept(MALEVOLENT_SHRINE.get());
                output.accept(YUTA_DOMAIN.get());
                output.accept(CURSED_SPEECH.get());
                output.accept(MAXIMUM_UZUMAKI.get());
                output.accept(LOVE_BEAM.get());
                output.accept(YUTA_DANCE.get());output.accept(SHUFFLE_DANCE.get());
                output.accept(INFORMATION_FRAGMENT.get());
                output.accept(GOJO_TEACHER.get());
                output.accept(MAHORAGA_WHEEL.get());
                output.accept(SUKUNA_FINGER.get());
                output.accept(GOJO_CAT_SPAWN_EGG.get());
                output.accept(YUTA_CAT_SPAWN_EGG.get());
                output.accept(GETO_CAT_SPAWN_EGG.get());
                output.accept(CURSED_SPIRIT_SPAWN_EGG.get());
                output.accept(SUKUNA_CAT_SPAWN_EGG.get());
                output.accept(MAHORAGA_CAT_SPAWN_EGG.get());
                output.accept(GOJO_CAT_DEBUG_STICK.get());
            }).build());


    private VfxItems() {}

    private static DeferredItem<SkillItem> skill(String name, SkillItem.Kind kind) {
        return ITEMS.register(name, () -> new SkillItem(kind));
    }

    public static boolean isBlue(ItemStack stack) { return !stack.isEmpty() && stack.is(BLUE.get()); }
    public static boolean isRed(ItemStack stack) { return !stack.isEmpty() && stack.is(RED.get()); }
    public static boolean isPurple(ItemStack stack) { return !stack.isEmpty() && stack.is(PURPLE.get()); }
    public static boolean isSukunaSlash(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof SukunaSlashItem; }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

    public static boolean isTechniqueHand(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof MaximumUzumakiItem || stack.getItem() instanceof YutaDomainItem || stack.getItem() instanceof CursedSpeechItem
                || stack.getItem() instanceof DanceItem || stack.getItem() instanceof SkillItem
                || stack.getItem() instanceof SukunaSlashItem || stack.getItem() instanceof CursedFistItem);
    }

    public static boolean isHeld(ItemStack stack) {
        return !stack.isEmpty() && stack.is(CURSED_FIST.get());
    }

}
