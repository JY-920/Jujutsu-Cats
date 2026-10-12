package dev.jujutsuvfx.server;

import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Keep the committed strike's energy through damage events, even during alternating rapid punches. */
public final class CatMeleeDamage extends DamageSource {
    private final boolean blackFlash;
    private final int energy;
    public CatMeleeDamage(Holder<DamageType> type,SorcererCat attacker,int energy){
        super(type,attacker,attacker);
        this.energy=energy;blackFlash=energy==SorcererCat.BLACK_FLASH;
    }
    public int energy(){return energy;}
    public boolean blackFlash(){return blackFlash;}
}
