package dev.jujutsuvfx.client;
import dev.jujutsuvfx.core.YutaKatanaMotion;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
public final class YutaKatanaModelTest {
 public static void main(String[] args){
  float maximum=0;
  for(boolean slim:new boolean[]{false,true})for(float t=8;t<=32;t+=.25f){
   var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
   PlayerModel<AbstractClientPlayer> m=new PlayerModel<>(root,slim);
   YutaKatanaHands.apply(m,YutaKatanaMotion.charge(t),t,true);
   var sword=new PoseStack();YutaKatanaHands.weapon(m,sword,t);
   Vector3f direction=sword.last().pose().transformDirection(new Vector3f(0,1,0)).normalize();
   if(direction.x<.999f||Math.abs(direction.y)>.001f)throw new AssertionError("Sword must be horizontal across the body");
   Vector3f contact=sword.last().pose().transformPosition(new Vector3f(.025f,.98f-.84f*YutaKatanaMotion.wipe(t),-.025f));
   var left=new PoseStack();CursedFistHands.palmPose(m,left,HumanoidArm.LEFT);
   float gap=left.last().pose().getTranslation(new Vector3f()).distance(contact);maximum=Math.max(maximum,gap);
   if(gap>.065f)throw new AssertionError("Wiping hand missed blade: "+gap+" at "+t+", slim="+slim+", goal="+contact+", actual="+left.last().pose().getTranslation(new Vector3f())+", shoulder="+new Vector3f(m.leftArm.x,m.leftArm.y,m.leftArm.z).div(16));
   for(float v:sword.last().pose().get(new float[16]))if(!Float.isFinite(v))throw new AssertionError("Invalid weapon attachment");
  }
  for(boolean slim:new boolean[]{false,true})for(float angle=-2;angle<2;angle+=.1f){
   var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
   PlayerModel<AbstractClientPlayer> m=new PlayerModel<>(root,slim);
   m.rightArm.xRot=angle;m.rightArm.yRot=.2f;
   var sword=new PoseStack();YutaKatanaHands.vanillaWeapon(m,sword,HumanoidArm.RIGHT);
   var hand=new PoseStack();m.rightArm.translateAndRotate(hand);hand.translate(-.0625,.625,0);
   Vector3f grip=sword.last().pose().getTranslation(new Vector3f());
   if(grip.distance(hand.last().pose().getTranslation(new Vector3f()))>.0001)throw new AssertionError("Vanilla sword detached from hand");
   Vector3f tip=sword.last().pose().transformPosition(new Vector3f(0,1,0));
   if(tip.distance(grip)<1.6f)throw new AssertionError("Player sword still too small");
   var axis=sword.last().pose().transformDirection(new Vector3f(0,1,0)).normalize();
   var expected=hand.last().pose().transformDirection(new Vector3f(0,0,-1)).normalize();
   if(axis.distance(expected)>.001)throw new AssertionError("Sword does not follow vanilla arm swing");
  }
  System.out.println("Katana: enlarged vanilla arm-following sword and continuous tip-to-base ritual contact; maximum gap="+maximum);
 }
}
