package dev.jujutsuvfx.config;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.core.DomainDuration;
import dev.jujutsuvfx.core.DomainVolume;
import java.util.EnumMap;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Visual preferences belong to each client; terrain authority belongs to the current server. */
public final class VfxConfig {
    public static final ModConfigSpec CLIENT, SERVER;
    private static final ModConfigSpec.IntValue LIGHTING, MUSIC_VOLUME, VOID_SECONDS, SHRINE_SECONDS;
    private static final EnumMap<TechniqueDamage, ModConfigSpec.IntValue> DAMAGE = new EnumMap<>(TechniqueDamage.class);
    private static final ModConfigSpec.BooleanValue FLASHES, DESTRUCTION, CLEAVE_PHYSICS, CINEMATIC, UNLIMITED_DESTRUCTION, MUSIC;
    static {
        var client = new ModConfigSpec.Builder();
        LIGHTING = client.comment("Colored technique lighting: 0 off, 1-5 increase brightness and reach.")
                .defineInRange("lightingIntensity", 2, 0, 5);
        FLASHES = client.comment("Allow screen-white transitions and flash accents. Other animated effects remain.")
                .define("screenFlashes", true);
        MUSIC = client.comment("Play the supplied Rain Love soundtrack while watching a cinematic cat duel. Local preference.")
                .define("cinematicMusic", true);
        MUSIC_VOLUME = client.comment("Cinematic soundtrack volume, 0-100. Also respects Minecraft master/music volume.")
                .defineInRange("cinematicMusicVolume", 50, 0, 100);
        CLIENT = client.build();
        var server = new ModConfigSpec.Builder();
        DESTRUCTION = server.comment("Allow technique terrain destruction and physics cutting. Independent of vanilla game rules.",
                        "Changed in-game only by operators (permission level 2). Applies to players and NPCs.")
                .define("techniqueBlockDestruction", false);
        CLEAVE_PHYSICS = server.comment("Allow Cleave to assemble, split and destroy dynamic structures. Requires Aeronautics and techniqueBlockDestruction.",
                        "When disabled, Cleave only cuts ordinary world blocks. Operator-only in-game setting.")
                .define("cleaveDynamicStructures", true);
        CINEMATIC = server.comment("Enable authored cat battles, automatic camera and scripted phases. Operator-only.")
                .define("cinematicMode", true);
        UNLIMITED_DESTRUCTION = server.comment("Allow Unlimited Purple terrain destruction. Also requires techniqueBlockDestruction. Operator-only.")
                .define("unlimitedPurpleBlockDestruction", false);
        server.comment("Damage percentages for techniques cast by players and cats. 100 keeps original damage; 0 disables damage.",
                "Sustained techniques apply the percentage to each hit. Scripted fatal Unlimited Purple remains independent.").push("techniqueDamage");
        for (TechniqueDamage technique : TechniqueDamage.values())
            DAMAGE.put(technique, server.defineInRange(technique.key(), 100, 0, 1000));
        server.pop();
        server.comment("Normal player and cat domains, in seconds. Existing casts and authored cinematic scenes keep their timing.")
                .push("domainDuration");
        VOID_SECONDS = server.defineInRange("unlimitedVoidSeconds", DomainDuration.DEFAULT_SECONDS, DomainDuration.MIN_SECONDS, DomainDuration.MAX_SECONDS);
        SHRINE_SECONDS = server.defineInRange("malevolentShrineSeconds", DomainDuration.DEFAULT_SECONDS, DomainDuration.MIN_SECONDS, DomainDuration.MAX_SECONDS);
        server.pop();
        SERVER = server.build();
    }
    private VfxConfig() {}
    public static int voidSeconds() { return SERVER.isLoaded() ? VOID_SECONDS.get() : DomainDuration.DEFAULT_SECONDS; }
    public static int shrineSeconds() { return SERVER.isLoaded() ? SHRINE_SECONDS.get() : DomainDuration.DEFAULT_SECONDS; }
    public static int domainSeconds(int kind) { return kind == DomainVolume.UNLIMITED_VOID ? voidSeconds() : shrineSeconds(); }
    /** Validate the whole transaction before modifying either value. */
    public static void domainSeconds(int voidSeconds, int shrineSeconds) {
        if (!DomainDuration.valid(voidSeconds) || !DomainDuration.valid(shrineSeconds))
            throw new IllegalArgumentException("Domain duration out of range");
        if (!SERVER.isLoaded()) throw new IllegalStateException("Server configuration is not loaded");
        int oldVoid = voidSeconds(), oldShrine = shrineSeconds();
        try {
            VOID_SECONDS.set(voidSeconds); SHRINE_SECONDS.set(shrineSeconds); SERVER.save();
        } catch (RuntimeException failure) {
            VOID_SECONDS.set(oldVoid); SHRINE_SECONDS.set(oldShrine); throw failure;
        }
    }
    public static int damagePercent(TechniqueDamage technique) {
        return SERVER.isLoaded() ? DAMAGE.get(technique).get() : 100;
    }
    public static int[] damagePercents() {
        TechniqueDamage[] techniques = TechniqueDamage.values();
        int[] values = new int[techniques.length];
        for (int i = 0; i < values.length; i++) values[i] = damagePercent(techniques[i]);
        return values;
    }
    /** Atomic in-memory update, with rollback if persistence fails. Server handler authenticates first. */
    public static void damagePercents(int[] values) {
        TechniqueDamage[] techniques = TechniqueDamage.values();
        if (values == null || values.length != techniques.length) throw new IllegalArgumentException("Invalid damage count");
        for (int value : values) if (value < 0 || value > 1000) throw new IllegalArgumentException("Damage percentage out of range");
        if (!SERVER.isLoaded()) throw new IllegalStateException("Server configuration is not loaded");
        int[] before = damagePercents();
        try {
            for (int i = 0; i < values.length; i++) DAMAGE.get(techniques[i]).set(values[i]);
            SERVER.save();
        } catch (RuntimeException failure) {
            for (int i = 0; i < before.length; i++) DAMAGE.get(techniques[i]).set(before[i]);
            throw failure;
        }
    }
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT);
        container.registerConfig(ModConfig.Type.SERVER, SERVER);
    }
    public static boolean music() { return !CLIENT.isLoaded() || MUSIC.get(); }
    public static int musicVolume() { return CLIENT.isLoaded() ? MUSIC_VOLUME.get() : 50; }
    public static void music(boolean value) { if(CLIENT.isLoaded()){MUSIC.set(value);CLIENT.save();} }
    public static void musicVolume(int value) {
        if(CLIENT.isLoaded()){MUSIC_VOLUME.set(Math.max(0,Math.min(100,value)));CLIENT.save();}
    }
    public static int lighting() { return CLIENT.isLoaded() ? LIGHTING.get() : 2; }
    public static boolean flashes() { return !CLIENT.isLoaded() || FLASHES.get(); }
    public static float flashScale() { return flashes() ? 1f : 0f; }
    public static void lighting(int value) {
        if (!CLIENT.isLoaded()) return;
        LIGHTING.set(Math.clamp(value, 0, 5)); CLIENT.save();
    }
    public static void flashes(boolean value) {
        if (!CLIENT.isLoaded()) return;
        FLASHES.set(value); CLIENT.save();
    }
    public static boolean cinematic() { return !SERVER.isLoaded() || CINEMATIC.get(); }
    public static boolean unlimitedDestruction() { return destruction() && UNLIMITED_DESTRUCTION.get(); }
    public static void cinematic(boolean value) { save(CINEMATIC,value); }
    public static void unlimitedDestruction(boolean value) { save(UNLIMITED_DESTRUCTION,value); }
    private static void save(ModConfigSpec.BooleanValue field,boolean value) {
        if(!SERVER.isLoaded())return;
        boolean before=field.get();field.set(value);
        try{SERVER.save();}catch(RuntimeException failure){field.set(before);throw failure;}
    }
    public static boolean destruction() { return SERVER.isLoaded() && DESTRUCTION.get(); }
    public static boolean cleavePhysics() { return !SERVER.isLoaded() || CLEAVE_PHYSICS.get(); }
    public static void cleavePhysics(boolean value) {
        if (!SERVER.isLoaded()) return;
        boolean before = CLEAVE_PHYSICS.get();
        CLEAVE_PHYSICS.set(value);
        try { SERVER.save(); }
        catch (RuntimeException failure) { CLEAVE_PHYSICS.set(before); throw failure; }
    }
    /** Called only by the authenticated server handler. Never call this from a client screen. */
    public static void destruction(boolean value) {
        if (!SERVER.isLoaded()) return;
        boolean before = DESTRUCTION.get();
        DESTRUCTION.set(value);
        try { SERVER.save(); }
        catch (RuntimeException failure) { DESTRUCTION.set(before); throw failure; }
    }
}
