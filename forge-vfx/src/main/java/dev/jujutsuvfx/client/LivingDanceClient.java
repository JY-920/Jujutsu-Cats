package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.*;
import dev.jujutsuvfx.server.LivingDance;
import net.minecraft.client.model.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
public final class LivingDanceClient {
 public static boolean active(LivingEntity e){return !(e instanceof Player)&&LivingDance.active(e);}
 public static void root(LivingEntity e,EntityModel<?> model,PoseStack p,float partial){
  NativeDanceRig.restore(model);
  if(!active(e))return;
  float time=LivingDance.age(e,partial),w=ShuffleDanceMotion.weight(time);
  float[] k=ShuffleDanceMotion.sample(time);
  boolean shuffle=LivingDance.style(e)==2;
  float side=(shuffle?ShuffleDanceMotion.LATERAL_TRAVEL*k[2]:.07f*YutaDanceMotion.step(time))*w;
  float hop=(shuffle?k[0]:.14f*YutaDanceMotion.bounce(time))*w;
  double yaw=Math.toRadians(e.yBodyRot);
  p.translate(Math.cos(yaw)*side,hop,Math.sin(yaw)*side);
  p.mulPose(Axis.YP.rotationDegrees(-e.yBodyRot));p.mulPose(Axis.ZP.rotation((shuffle?-ShuffleDanceMotion.LEAN*k[2]:.04f*YutaDanceMotion.step(time))*w));p.mulPose(Axis.YP.rotationDegrees(e.yBodyRot));
 }
 public static void pose(LivingEntity e,EntityModel<?> model,float partial){
  if(!active(e)||!NativeDanceRig.available(model))return;
  float time=LivingDance.age(e,partial),w=ShuffleDanceMotion.weight(time);
  NativeDanceRig.apply(model,LivingDance.style(e),time,w);
 }
 public static void restore(EntityModel<?> model){NativeDanceRig.restore(model);if(model instanceof HumanoidModel<?> h){MartialClient.restore(h);FlexibleLimbRenderer.clear(h);}}
}
