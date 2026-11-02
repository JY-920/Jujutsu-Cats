package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
/** Native bone poses consumed by the supplied Blockbench geometry. */
interface CatGeometryPose {
 ModelPart bone(String name);
 void attachmentPose(PoseStack poses,String name);
 boolean hideBlindfold();
 boolean handUnavailable(int hand);
 float elbow(int hand);
 default void detailPose(PoseStack poses,String name){}
 default CatMeshBend meshBend(String bone){return null;}
 default boolean smoothArms(){return false;}
 default float palmTwist(int hand){return 0;}
}
