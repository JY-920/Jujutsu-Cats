package dev.jujutsuvfx.item;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.entity.PartEntity;

/** Two confirmed ordinary contacts charge one strike. Vanilla stack synchronization also syncs its aura. */
public final class FistCombo {
    private static final String KEY = "jujutsuvfx_fist_combo";
    private FistCombo() {}

    public static int hits(ItemStack stack) {
        if (!VfxItems.isHeld(stack)) return 0;
        var data = stack.getTag();
        return data == null ? 0 : dev.jujutsuvfx.platform.Numbers.clamp(data.getInt(KEY), 0, 2);
    }
    public static boolean ready(ItemStack stack) { return hits(stack) == 2; }

    /** Called only from the server's successful item post-hit hook, after native knockback. */
    public static boolean confirmed(ItemStack stack) {
        int hits = hits(stack);
        var tag=stack.getOrCreateTag();
        {
            if (hits == 2) tag.remove(KEY); else tag.putInt(KEY, hits + 1);
        }
        return hits == 2;
    }

    public static boolean empoweredHit(Entity attacker, Entity target, DamageSource source) {
        boolean living = target instanceof LivingEntity || target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity;
        return attacker instanceof ServerPlayer player && !player.isSpectator() && living
                && true
                && source.getEntity() == player && source.getDirectEntity() == player
                && source.is(DamageTypes.PLAYER_ATTACK) && ready(player.getMainHandItem());
    }
}
