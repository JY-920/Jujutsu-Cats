package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.core.CatCombatMoves;
import net.minecraft.client.model.OcelotModel;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

/** Native cat locomotion drives the artist's complete model, not a second visible vanilla cat. */
final class SorcererCatModel<T extends SorcererCat> extends OcelotModel<T> {
    private final SorcererCatGeometry geometry, wheel;
    private final boolean sukuna, gojo;
    private float blindfold;
    private boolean transforming;
    private boolean hideBlindfold, brokenArm, brokenLeftArm;
    private final CatCombatPose pose = new CatCombatPose(), previousPose = new CatCombatPose();
    private final CatTailChain tailChain=new CatTailChain();

    SorcererCatModel(ModelPart root, ResourceManager resources, boolean sukuna) {
        this(root,resources,sukuna?"sukuna_cat":"gojo_cat");
    }
    SorcererCatModel(ModelPart root, ResourceManager resources, String name) {
        super(root);
        this.sukuna = name.equals("sukuna_cat"); this.gojo = name.equals("gojo_cat");
        geometry = new SorcererCatGeometry(resources, name, null);
        wheel = gojo ? null : new SorcererCatGeometry(resources,"mahoraga_wheel","animation2");
    }

    @Override public void prepareMobModel(T cat, float swing, float amount, float partial) {
        for (ModelPart part : new ModelPart[]{head, body, leftFrontLeg, rightFrontLeg, leftHindLeg, rightHindLeg, tail1, tail2}) part.resetPose();
        super.prepareMobModel(cat, swing, amount, partial);
        if (cat.isInSittingPose()) {
            body.xRot = Mth.PI / 4; body.y -= 4; body.z += 5; head.y -= 3.3f; head.z++;
            tail1.y += 8; tail1.z -= 2; tail2.y += 2; tail2.z -= .8f;
            tail1.xRot = 1.7278761f; tail2.xRot = 2.670354f;
            for (ModelPart leg : new ModelPart[]{leftFrontLeg, rightFrontLeg}) { leg.xRot = -Mth.PI / 20; leg.y = 16.1f; leg.z = -7; }
            for (ModelPart leg : new ModelPart[]{leftHindLeg, rightHindLeg}) { leg.xRot = -Mth.HALF_PI; leg.y = 21; leg.z = 1; }
            state = 3;
        }
    }

    @Override public void setupAnim(T cat, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        if (cat.behavior() == SorcererCat.STILL || cat.isTransforming()) { limbSwing = 0; limbAmount = 0; }
        float partial = Mth.clamp(age - cat.tickCount, 0, 1);
        super.setupAnim(cat, limbSwing, limbAmount, cat.animationTime(partial), yaw, pitch);
        blindfold = cat.blindfoldProgress(partial);
        transforming = cat.isTransforming();
        hideBlindfold = cat.appearance() == SorcererCat.NO_BLINDFOLD;
        brokenArm = cat.brokenArm();brokenLeftArm=cat.brokenLeftArm();
        int move = transforming || cat.behavior() == SorcererCat.STILL ? CatCombatMoves.IDLE : cat.combatMove();
        pose.sample(move, cat.punchHand(), cat.punchAge(partial),cat.punchKind());
        long before = cat.previousCombatAction();
        previousPose.sample(transforming || cat.behavior() == SorcererCat.STILL ? CatCombatMoves.IDLE : CatCombatMoves.move(before),
                CatCombatMoves.hand(before), cat.previousCombatAge(),CatCombatMoves.kind(before));
        pose.blendFrom(previousPose, cat.combatBlend(partial));
        applyCombatPose();
    }
    private void applyCombatPose(){
        tailChain.capture(body);
        apply(head, CatCombatPose.HEAD, false); apply(body, CatCombatPose.BODY, false);
        apply(leftFrontLeg, CatCombatPose.LEFT_ARM, true); apply(rightFrontLeg, CatCombatPose.RIGHT_ARM, true);
        apply(leftHindLeg, CatCombatPose.LEFT_LEG, true); apply(rightHindLeg, CatCombatPose.RIGHT_LEG, true);
        apply(tail1, CatCombatPose.TAIL, false); apply(tail2, CatCombatPose.TAIL_TIP, false);
        tailChain.connect(body,tail1,tail2,geometry.tailAnchors());
        followTorso(leftHindLeg,pose.hipFollow());followTorso(rightHindLeg,pose.hipFollow());
    }
    private void followTorso(ModelPart tail,float amount){
        if(amount<=0)return;float oldX=tail.x,oldY=tail.y,oldZ=tail.z;
        float pitch=pose.value(CatCombatPose.BODY,0),yaw=pose.value(CatCombatPose.BODY,1),roll=pose.value(CatCombatPose.BODY,2);
        float x=tail.x-body.x+pose.value(CatCombatPose.BODY,3);
        float y=tail.y-body.y+pose.value(CatCombatPose.BODY,4);
        float z=tail.z-body.z+pose.value(CatCombatPose.BODY,5);
        // Both supplied tail bones are siblings of the torso. Carry their pivots through
        // the same extra torso transform instead of rotating disconnected tail meshes.
        float py=y*Mth.cos(pitch)-z*Mth.sin(pitch),pz=y*Mth.sin(pitch)+z*Mth.cos(pitch);
        float px=x*Mth.cos(yaw)+pz*Mth.sin(yaw);pz=-x*Mth.sin(yaw)+pz*Mth.cos(yaw);
        tail.x=body.x+px*Mth.cos(roll)-py*Mth.sin(roll);
        tail.y=body.y+px*Mth.sin(roll)+py*Mth.cos(roll);tail.z=body.z+pz;
        tail.x=Mth.lerp(amount,oldX,tail.x);tail.y=Mth.lerp(amount,oldY,tail.y);tail.z=Mth.lerp(amount,oldZ,tail.z);
        tail.xRot+=pitch*amount;tail.yRot+=yaw*amount;tail.zRot+=roll*amount;
    }
    private void apply(ModelPart part, int bone, boolean replaceRotation) {
        float weight = pose.value(bone, 6);
        if (replaceRotation) {
            part.xRot = Mth.lerp(weight, part.xRot, pose.value(bone, 0));
            part.yRot = Mth.lerp(weight, part.yRot, pose.value(bone, 1));
            part.zRot = Mth.lerp(weight, part.zRot, pose.value(bone, 2));
        } else { part.xRot += pose.value(bone, 0); part.yRot += pose.value(bone, 1); part.zRot += pose.value(bone, 2); }
        part.x += pose.value(bone, 3); part.y += pose.value(bone, 4); part.z += pose.value(bone, 5);
    }
    private void combatRoot(PoseStack poses) {
        poses.translate(0, 15 / 16f, 0);
        poses.mulPose(Axis.YP.rotation(pose.rootYaw()));
        poses.mulPose(Axis.ZP.rotation(pose.rootRoll()));
        poses.mulPose(Axis.XP.rotation(pose.rootPitch()));
        poses.translate(0, -15 / 16f, 0);
    }
    boolean hideBlindfold() { return hideBlindfold; }
    boolean brokenArm() { return brokenArm; }
    boolean handUnavailable(int hand) { return hand==SorcererCat.LEFT?brokenLeftArm:brokenArm; }
    float elbow(int hand) { return pose.elbow(hand); }
    void handPose(PoseStack poses, int hand) {
        combatRoot(poses);
        (hand == SorcererCat.LEFT ? leftFrontLeg : rightFrontLeg).translateAndRotate(poses);
        poses.translate(0, 5 / 16f, 0); poses.mulPose(Axis.XP.rotation(elbow(hand)));
        poses.translate(0, 4 / 16f, 1 / 16f);
    }

    @Override public void renderToBuffer(PoseStack poses, VertexConsumer buffer, int light, int overlay,
                                         float red,float green,float blue,float alpha) {
        int color=((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);
        renderToBuffer(poses,buffer,light,overlay,color);
    }
    public void renderToBuffer(PoseStack poses, VertexConsumer buffer, int light, int overlay, int color) {
        renderMaterial(poses, buffer, light, overlay, color, SorcererCatGeometry.CAT);
    }

    void renderMaterial(PoseStack poses, VertexConsumer buffer, int light, int overlay, int color, int material) {
        if (material == SorcererCatGeometry.EYES && gojo && !transforming && blindfold <= 0 && !hideBlindfold) return;
        poses.pushPose();
        try {
            if (young) { poses.scale(.5f, .5f, .5f); poses.translate(0, 1.5f, 0); }
            combatRoot(poses);
            geometry.render(poses, buffer, light, overlay, color, material, this, blindfold);
        } finally { poses.popPose(); }
    }

    void renderWheel(PoseStack poses, VertexConsumer buffer, int light, int overlay, float progress) {
        renderWheel(poses, buffer, light, overlay, progress, -1);
    }
    void renderWheel(PoseStack poses, VertexConsumer buffer, int light, int overlay, float progress, int color) {
        if (wheel == null) return;
        poses.pushPose();
        try {
            if (young) { poses.scale(.5f,.5f,.5f); poses.translate(0,1.5f,0); }
            combatRoot(poses); attachmentPose(poses,"body");
            // Lower the decorative wheel towards Sukuna's shoulder blades; preserve donor UVs.
            if (sukuna) poses.translate(0,.22,.50);
            wheel.render(poses,buffer,light,overlay,color,SorcererCatGeometry.DETAILS,this,progress);
        } finally { poses.popPose(); }
    }
    void attachmentPose(PoseStack poses, String name) {
        ModelPart part = bone(name);
        if (part == null) return;
        part.translateAndRotate(poses);
        float x = 0, y = 15, z = -9, rotation = 0;
        switch (name) {
            case "body" -> { y = 12; z = -10; rotation = Mth.HALF_PI; }
            case "left_front_leg" -> { x = -1.2f; y = 14.1f; z = -5; }
            case "right_front_leg" -> { x = 1.2f; y = 14.1f; z = -5; }
            case "left_hind_leg" -> { x = -1.1f; y = 18; z = 5; }
            case "right_hind_leg" -> { x = 1.1f; y = 18; z = 5; }
        }
        poses.mulPose(Axis.XP.rotation(-rotation));
        poses.translate(-x / 16, -y / 16, -z / 16);
    }

    ModelPart bone(String name) {
        // The supplied template labels its +X legs "left". After converting X into
        // Minecraft's model basis those vertices are on the right. Bind by that side
        // so walking and lying-down poses follow the correct limbs; native head aim
        // is already in Minecraft coordinates and must not be mirrored.
        return switch (name) {
            case "head" -> head;
            case "body" -> body;
            case "left_hind_leg" -> rightHindLeg;
            case "right_hind_leg" -> leftHindLeg;
            case "left_front_leg" -> rightFrontLeg;
            case "right_front_leg" -> leftFrontLeg;
            case "tail1" -> tail1;
            case "tail2" -> tail2;
            default -> null;
        };
    }
}
