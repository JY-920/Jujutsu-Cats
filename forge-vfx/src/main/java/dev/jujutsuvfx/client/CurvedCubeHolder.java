package dev.jujutsuvfx.client;

import net.minecraft.client.model.geom.ModelPart;
import java.util.List;

/** Implemented on every ModelPart by MartialLimbMixin, so subclass renderers can reach the cube list. */
public interface CurvedCubeHolder {
    List<ModelPart.Cube> jujutsu$cubes();
}
