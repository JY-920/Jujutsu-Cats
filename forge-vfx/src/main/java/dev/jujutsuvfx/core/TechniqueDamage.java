package dev.jujutsuvfx.core;

/** Stable settings keys; separate cast variants may share a vanilla damage type. */
public enum TechniqueDamage {
    BLUE("blue"), RED("red"), PURPLE_SMALL("purple_small"), PURPLE_LARGE("purple_large"),
    PURPLE_AMPLIFIED("purple_amplified"), PURPLE_UNLIMITED("purple_unlimited"),
    DISMANTLE("dismantle"), CLEAVE("cleave"), FURNACE("furnace"), SHRINE("shrine");

    private final String key;
    TechniqueDamage(String key) { this.key=key; }
    public String key() { return key; }
    public String translationKey() { return "gui.jujutsuvfx.damage."+key; }
}
