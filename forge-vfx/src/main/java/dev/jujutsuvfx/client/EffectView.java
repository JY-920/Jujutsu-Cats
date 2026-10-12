package dev.jujutsuvfx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Camera-space range checks also cover cinematic and third-person observers. */
final class EffectView {
    private EffectView(){}
    static double distanceToSqr(Vec3 point){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return Double.POSITIVE_INFINITY;
        return mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(point);
    }
}
