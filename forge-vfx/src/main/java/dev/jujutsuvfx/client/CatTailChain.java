package dev.jujutsuvfx.client;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Links the imported sibling tail bones without changing their authored pose angles. */
final class CatTailChain {
    /** Cap centers in the same pixel coordinates as ModelPart pivots. */
    record Anchors(float endX,float endY,float endZ,float rootX,float rootY,float rootZ) {}
    private final Vector3f bodyBefore=new Vector3f(), point=new Vector3f(), tipPoint=new Vector3f(), angles=new Vector3f();
    private final Quaternionf rotationBefore=new Quaternionf(), inverse=new Quaternionf(), delta=new Quaternionf(), rotation=new Quaternionf();

    void capture(ModelPart body) {
        bodyBefore.set(body.x,body.y,body.z);
        rotationBefore.rotationZYX(body.zRot,body.yRot,body.xRot);
    }

    void connect(ModelPart body,ModelPart base,ModelPart tip,Anchors anchors) {
        // Compose the actual rendered rotations; adding Euler angles is not equivalent
        // when a pose turns the torso and the tail around different axes.
        delta.rotationZYX(body.zRot,body.yRot,body.xRot).mul(inverse.set(rotationBefore).conjugate());
        follow(body,base);follow(body,tip);
        rotation.rotationZYX(base.zRot,base.yRot,base.xRot);
        rotation.transform(point.set(anchors.endX,anchors.endY,anchors.endZ));
        rotation.rotationZYX(tip.zRot,tip.yRot,tip.xRot);
        rotation.transform(tipPoint.set(anchors.rootX,anchors.rootY,anchors.rootZ));
        // Tail1 receives extra charging/swing angles. Its end therefore moves even
        // when the torso stays still; tail2 must follow that endpoint every frame.
        tip.x=base.x+point.x-tipPoint.x;
        tip.y=base.y+point.y-tipPoint.y;
        tip.z=base.z+point.z-tipPoint.z;
    }

    private void follow(ModelPart body,ModelPart part) {
        delta.transform(point.set(part.x,part.y,part.z).sub(bodyBefore));
        part.x=body.x+point.x;part.y=body.y+point.y;part.z=body.z+point.z;
        rotation.set(delta).mul(inverse.rotationZYX(part.zRot,part.yRot,part.xRot));
        rotation.getEulerAnglesZYX(angles);
        part.xRot=angles.x;part.yRot=angles.y;part.zRot=angles.z;
    }
}
