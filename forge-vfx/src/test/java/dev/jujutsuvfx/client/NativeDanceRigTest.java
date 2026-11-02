package dev.jujutsuvfx.client;

import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;
import java.lang.reflect.*;
import java.util.*;

/** Real baked vanilla rigs, without starting a game or replacing native limb proportions. */
public final class NativeDanceRigTest {
    // Unit JVMs do not apply Mixins. Feed exactly the constructor metadata used in-game.
    static void observe(ModelPart part) throws Exception {
        Map<String, ModelPart> children = null;
        List<ModelPart.Cube> cubes = null;
        for (Field f : ModelPart.class.getDeclaredFields()) {
            if (f.getType() == Map.class) { f.setAccessible(true); children = (Map<String, ModelPart>) f.get(part); }
            if (f.getType() == List.class) { f.setAccessible(true); cubes = (List<ModelPart.Cube>) f.get(part); }
        }
        NativeDanceRig.observe(part, cubes, children);
        for (ModelPart child : children.values()) observe(child);
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void verify(EntityModel<?> model, ModelPart root, int limbs) throws Exception {
        observe(root);
        check(NativeDanceRig.limbCount(model) == limbs, model.getClass()+" limbs="+NativeDanceRig.limbCount(model));
        var originals = new IdentityHashMap<ModelPart, PartPose>();
        root.getAllParts().forEach(p -> originals.put(p, p.storePose()));
        ModelPart scaled = root.getAllParts().filter(p -> p != root).findFirst().orElseThrow();
        scaled.xScale = .84f; scaled.yScale = 1.13f; scaled.zScale = .92f;
        int moved = 0;
        for (int style : new int[]{1, 2}) for (float age = 10; age < 85; age += .25f) {
            NativeDanceRig.apply(model, style, age, 1);
            for (var entry : originals.entrySet()) {
                ModelPart p = entry.getKey();
                check(Float.isFinite(p.xRot+p.y+p.z), "Invalid animation");
                if (!LivingDanceModelTest.same(p.storePose(), entry.getValue())) moved++;
            }
            NativeDanceRig.restore(model);
            for (var entry : originals.entrySet()) check(LivingDanceModelTest.same(entry.getKey().storePose(), entry.getValue()), "Shared pose leaked");
            check(scaled.xScale == .84f && scaled.yScale == 1.13f && scaled.zScale == .92f, "Native scale lost");
        }
        check(moved > 50, "Native limbs did not move");
    }
    public static void main(String[] args) throws Exception {
        ModelPart root = CowModel.createBodyLayer().bakeRoot(); verify(new CowModel<LivingEntity>(root), root, 4);
        root = ChickenModel.createBodyLayer().bakeRoot();
        var chicken = new ChickenModel<LivingEntity>(root); verify(chicken, root, 2);
        PartPose wing = root.getChild("left_wing").storePose();
        NativeDanceRig.apply(chicken, 2, 14, 1);
        check(LivingDanceModelTest.same(wing, root.getChild("left_wing").storePose()), "Wing mistaken for arm");
        NativeDanceRig.restore(chicken);
        root = SpiderModel.createSpiderBodyLayer().bakeRoot(); verify(new SpiderModel<LivingEntity>(root), root, 8);
        root = IronGolemModel.createBodyLayer().bakeRoot(); verify(new IronGolemModel(root), root, 4);
        root = EndermanModel.createBodyLayer().bakeRoot();
        var tall = new EndermanModel<LivingEntity>(root); verify(tall, root, 4);
        float x = tall.leftArm.x, y = tall.leftArm.y, z = tall.leftArm.z;
        NativeDanceRig.apply(tall, 1, 14, 1);
        check(tall.leftArm.x == x && tall.leftArm.y == y && tall.leftArm.z == z, "Long arm pivot replaced with player coordinates");
        NativeDanceRig.restore(tall);

        var mesh = new MeshDefinition();
        var arm = mesh.getRoot().addOrReplaceChild("LeftArm", CubeListBuilder.create().addBox(-1,0,-1,2,6,2), PartPose.offset(5,3,0));
        arm.addOrReplaceChild("left_forearm", CubeListBuilder.create().addBox(-1,0,-1,2,5,2), PartPose.offset(0,6,0));
        mesh.getRoot().addOrReplaceChild("right_wing_arm", CubeListBuilder.create().addBox(0,0,0,2,4,2), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("tail_left_leg", CubeListBuilder.create().addBox(0,0,0,2,4,2), PartPose.ZERO);
        root = LayerDefinition.create(mesh,32,32).bakeRoot();
        Fixture custom = new Fixture(root); verify(custom, root, 1);
        NativeDanceRig.apply(custom, 1, 14, 1);
        check(root.getChild("LeftArm").getChild("left_forearm").xRot < -.1, "Named elbow not articulated");
        // A second apply must restore the first pose rather than accumulate rotations.
        PartPose once = root.getChild("LeftArm").storePose();
        NativeDanceRig.apply(custom, 1, 14, 1);
        check(LivingDanceModelTest.same(once, root.getChild("LeftArm").storePose()), "Pose accumulated across frames");
        NativeDanceRig.restore(custom);
        check(NativeDanceRig.classify("arm_l") != null && NativeDanceRig.classify("右后腿") != null, "Side aliases not recognized");
        check(NativeDanceRig.classify("tentacle") == null && NativeDanceRig.classify("right_wing") == null, "Unsafe guessed limb");
        LivingDanceModelTest.method(LivingDanceModelTest.read("net/minecraft/client/model/geom/ModelPart"), "<init>", "(Ljava/util/List;Ljava/util/Map;)V");
        System.out.println("Native dance: cow/chicken/spider/golem/enderman, named elbow, exclusions, proportions and restoration passed");
    }
    static final class Fixture extends HierarchicalModel<LivingEntity> {
        private final ModelPart rig;
        Fixture(ModelPart rig) { this.rig = rig; }
        public ModelPart root() { return rig; }
        public void setupAnim(LivingEntity e,float a,float b,float c,float d,float f) {}
    }
}
