package dev.jujutsuvfx;

import net.minecraftforge.fml.common.Mod;

@Mod(JujutsuVfx.ID)
public final class JujutsuVfx {
    public static final String ID = "jujutsuvfx";
    public JujutsuVfx() {
        var bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        dev.jujutsuvfx.config.VfxConfig.register();
        dev.jujutsuvfx.entity.VfxEntities.register(bus);
        dev.jujutsuvfx.effect.VfxEffects.register(bus);
        dev.jujutsuvfx.item.VfxItems.register(bus);
        dev.jujutsuvfx.network.DomainControl.register(bus);
    }
}
