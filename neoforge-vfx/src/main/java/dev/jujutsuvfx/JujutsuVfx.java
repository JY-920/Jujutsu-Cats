package dev.jujutsuvfx;

import net.neoforged.fml.common.Mod;

@Mod(JujutsuVfx.ID)
public final class JujutsuVfx {
    public static final String ID = "jujutsuvfx";
    public JujutsuVfx(net.neoforged.bus.api.IEventBus bus, net.neoforged.fml.ModContainer container) {
        dev.jujutsuvfx.config.VfxConfig.register(container);
        dev.jujutsuvfx.entity.VfxEntities.register(bus);
        dev.jujutsuvfx.effect.VfxEffects.register(bus);
        dev.jujutsuvfx.item.VfxItems.register(bus);
        dev.jujutsuvfx.network.DomainControl.register(bus);
    }
}
