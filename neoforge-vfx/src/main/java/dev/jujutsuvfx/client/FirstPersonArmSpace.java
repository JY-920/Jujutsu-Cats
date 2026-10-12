package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts the forward-reaching gesture frame to the player's anatomical skin coordinates. */
final class FirstPersonArmSpace {
    private FirstPersonArmSpace() {}

    static void apply(PoseStack poses, HumanoidArm arm) {
        // In the skin model, right-arm -X faces outward and +Y runs toward the fist.
        // Our gesture frame already aims +Y forward, but its +X points screen-right.
        // renderRightHand/renderLeftHand do not perform the world renderer's basis conversion.
        // Turn around the arm's long axis: keep its reach, restore inner/outer and front/back
        // faces, and use the same transform for the native sleeve and attached hand effects.
        poses.mulPose(Axis.YP.rotationDegrees(180));
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poses.translate(side * .375f, -10.125f / 16f, 0);
    }

    /** The local transform used by 1.21.1 ItemInHandRenderer.renderPlayerArm, including swing/equip. */
    static Matrix4f empty(HumanoidArm arm,float equip,float swing){
        float side=arm==HumanoidArm.RIGHT?1:-1,root=Mth.sqrt(Math.clamp(swing,0,1));
        float a=Mth.sin(root*Mth.PI),b=Mth.sin(swing*swing*Mth.PI),r=Mth.DEG_TO_RAD;
        return new Matrix4f().translation(side*(.64000005f-.3f*a),
                -.6f-.6f*equip+.4f*Mth.sin(root*2*Mth.PI),-.71999997f-.4f*Mth.sin(swing*Mth.PI))
                .rotateY(side*45*r).rotateY(side*a*70*r).rotateZ(side*b*-20*r)
                .translate(side*-1,3.6f,3.5f).rotateZ(side*120*r).rotateX(200*r).rotateY(side*-135*r)
                .translate(side*5.6f,0,0);
    }

    /** Blend about the anatomical wrist, keeping the authored casting pose exact at full weight. */
    static void fromEmpty(PoseStack poses,Matrix4f parent,HumanoidArm arm,float equip,float swing,float weight,boolean support){
        float w=Math.clamp(weight,0,1),side=arm==HumanoidArm.RIGHT?1:-1;
        Matrix4f idle=empty(arm,equip,support?0:swing);
        if(support)idle.m31(idle.m31()-.8f*(1-w));
        if(w>=.99999f)return;
        Matrix4f result;
        if(w<=.00001f)result=idle;
        else{
            Matrix4f gesture=new Matrix4f(parent).invert().mul(poses.last().pose());
            Vector3f pivot=new Vector3f(-side*.375f,10.125f/16f,0);
            Vector3f wrist=idle.transformPosition(new Vector3f(pivot)).lerp(gesture.transformPosition(new Vector3f(pivot)),w);
            Quaternionf rotation=idle.getUnnormalizedRotation(new Quaternionf()).normalize()
                    .slerp(gesture.getUnnormalizedRotation(new Quaternionf()).normalize(),w);
            Vector3f scale=new Vector3f(1).lerp(gesture.getScale(new Vector3f()),w);
            result=new Matrix4f().translation(wrist).rotate(rotation).scale(scale).translate(-pivot.x,-pivot.y,-pivot.z);
        }
        poses.last().pose().set(parent).mul(result);
        poses.last().normal().set(poses.last().pose()).invert().transpose();
    }
}
