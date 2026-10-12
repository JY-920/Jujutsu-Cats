package dev.jujutsuvfx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.item.FistCombo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Fit the final fifth of the arm independently from the upward-rising flame around it. */
final class FistAuraRenderer {
    private static final float FRONT_COVERAGE = .20f;
    record ArmShape(Vector3f palm, Vector3f low, Vector3f high) {}
    private record Hand(Matrix4f transform, Matrix4f projection, ArmShape shape,
                        Vector3f drag, float time, float power, float seed, boolean empowered, float red) {}
    private static final List<Hand> HANDS = new ArrayList<>();
    private static final Hand[] FIRST_PERSON = new Hand[2];
    private static final Map<LivingEntity, Motion[]> MOTION = new WeakHashMap<>();
    private static final class Motion {
        Vec3 anchor, velocity = Vec3.ZERO;
        double stamp = -1;
    }
    private FistAuraRenderer() {}
    static boolean ready() { return FistHeldRenderer.ready(); }
    static boolean hasWorldHands() { return !HANDS.isEmpty(); }
    static void clear() { HANDS.clear(); }
    static void reset() { clear(); beginFirstPersonFrame(); MOTION.clear(); }
    static void beginFirstPersonFrame() { FIRST_PERSON[0] = null; FIRST_PERSON[1] = null; }

    static void draw(PoseStack poses, float time, float power, boolean firstPerson,
                     AbstractClientPlayer player, HumanoidArm arm, float partial, ArmShape shape) {
        if (!ready()) return;
        if(MartialClient.owns(player)) {
            float pulse=MartialClient.aura(player,arm,partial);
            if(pulse<=.02f)return;
            power*=.65f+.55f*pulse;
        }
        Matrix4f transform = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        Vector3f center = transform.transformPosition(new Vector3f());
        if (center.lengthSquared() > 96 * 96 || !Float.isFinite(transform.determinant())
                || Math.abs(transform.determinant()) < 1e-8f) return;
        Vector3f drag = drag(player, arm, partial, transform, firstPerson);
        boolean empowered = MartialClient.owns(player) ? MartialClient.empowered(player)
                : FistCombo.ready(arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem());
        Hand hand = new Hand(transform, new Matrix4f(RenderSystem.getProjectionMatrix()), shape, drag,
                time, power, Math.floorMod(player.getId() * 19 + arm.ordinal() * 83, 1021),
                empowered, empowered ? 1 : 0);
        // Iris buffers the actual skin draw. Compose only after its hand pass, with this same projection.
        if (firstPerson) FIRST_PERSON[arm == HumanoidArm.RIGHT ? 0 : 1] = hand;
        else if (HANDS.size() < 96) HANDS.add(hand);
    }

    static void drawMob(PoseStack poses, float time, float power, LivingEntity entity, HumanoidArm arm,
                        float partial, ArmShape shape, boolean red) {
        if (!ready() || HANDS.size() >= 96) return;
        Matrix4f transform = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        if (transform.transformPosition(new Vector3f()).lengthSquared() > 96 * 96
                || !Float.isFinite(transform.determinant()) || Math.abs(transform.determinant()) < 1e-8f) return;
        HANDS.add(new Hand(transform, new Matrix4f(RenderSystem.getProjectionMatrix()), shape,
                drag(entity, arm, partial, transform, false), time, power,
                Math.floorMod(entity.getId() * 19 + arm.ordinal() * 83 + (red ? 419 : 0), 1021), red, red ? 1 : 0));
    }

    /** Palm motion in world space includes camera turns, locomotion and jabs. */
    private static Vector3f drag(LivingEntity player, HumanoidArm arm, float partial,
                                 Matrix4f transform, boolean firstPerson) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Quaternionf cameraRotation = camera.rotation();
        Vector3f viewAnchor = transform.transformPosition(new Vector3f());
        Vector3f worldOffset = new Vector3f(viewAnchor).rotate(cameraRotation);
        Vec3 anchor = camera.getPosition().add(worldOffset.x, worldOffset.y, worldOffset.z);
        Motion[] states = MOTION.computeIfAbsent(player, key -> new Motion[]{new Motion(), new Motion(), new Motion(), new Motion()});
        Motion motion = states[(firstPerson ? 0 : 2) + (arm == HumanoidArm.RIGHT ? 0 : 1)];
        double stamp = (ClientEffectClock.ticks() + (double) partial) * .05;
        double dt = stamp - motion.stamp;
        if (motion.anchor == null || dt < 0 || dt > .25 || anchor.distanceToSqr(motion.anchor) > 9) {
            motion.velocity = Vec3.ZERO; motion.anchor = anchor; motion.stamp = stamp;
        } else if (dt > .0001) {
            Vec3 velocity = anchor.subtract(motion.anchor).scale(1 / dt);
            if (velocity.lengthSqr() > 28 * 28) velocity = velocity.normalize().scale(28);
            motion.velocity = motion.velocity.lerp(velocity, 1 - Math.exp(-dt * 19));
            motion.anchor = anchor; motion.stamp = stamp;
        }
        Vector3f local = new Vector3f((float) motion.velocity.x, (float) motion.velocity.y, (float) motion.velocity.z)
                .rotate(new Quaternionf(cameraRotation).conjugate());
        new Matrix4f(transform).invert().transformDirection(local);
        local.mul(-.014f);
        if (local.lengthSquared() > .22f * .22f) local.normalize(.22f);
        return local;
    }

    static void renderFirstPerson(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ready() || mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isInvisible()
                || mc.player.isSpectator() || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()
                || (!VfxItems.isHeld(mc.player.getMainHandItem()) && !VfxItems.isHeld(mc.player.getOffhandItem()))) {
            beginFirstPersonFrame(); return;
        }
        if (FIRST_PERSON[0] == null && FIRST_PERSON[1] == null) return;
        event.getGuiGraphics().flush();
        try (DomainRenderer.State ignored = new DomainRenderer.State()) {
            for (Hand hand : FIRST_PERSON) if (hand != null) render(hand, true);
        } finally { beginFirstPersonFrame(); }
    }

    static void renderWorld(RenderLevelStageEvent event) {
        try {
            HANDS.sort(Comparator.comparingDouble(hand -> hand.transform.getTranslation(new Vector3f()).lengthSquared()));
            // One bounded 2D batch per hand, drawn back to front after the impact batch.
            for (int i = Math.min(12, HANDS.size()) - 1; i >= 0; i--) render(HANDS.get(i), false);
        } finally { clear(); }
    }

    private static void render(Hand hand, boolean overlay) {
        ArmShape shape = hand.shape;
        float aspect = FistSpriteRenderer.aspect(hand.projection);
        // Measure from the knuckle end of the actual skin cube, before camera projection or jab scaling.
        float rearY = shape.high.y - (shape.high.y - shape.low.y) * FRONT_COVERAGE;
        float centerY = (rearY + shape.high.y) * .5f;
        Vector3f anchor = screen(hand, new Vector3f(0, centerY, 0), aspect);
        Vector3f tip = screen(hand, new Vector3f(0, shape.high.y, 0), aspect);
        Vector3f cuff = screen(hand, new Vector3f(0, rearY, 0), aspect);
        if (anchor == null || tip == null || cuff == null) return;
        Vector2f armUp = new Vector2f(tip.x - cuff.x, tip.y - cuff.y);
        if (armUp.lengthSquared() < .000004f) {
            Vector3f knuckle = screen(hand, new Vector3f(.12f, centerY, 0), aspect);
            if (knuckle != null) armUp.set(-(knuckle.y - anchor.y), knuckle.x - anchor.x);
            if (armUp.lengthSquared() < .0000001f) armUp.set(0, 1);
        }
        armUp.normalize();
        Vector2f right = new Vector2f(armUp.y, -armUp.x);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, depth = 1;
        int points = 0;
        for (int i = 0; i < 8; i++) {
            Vector3f point = screen(hand, new Vector3f((i & 1) == 0 ? shape.low.x : shape.high.x,
                    (i & 2) == 0 ? rearY : shape.high.y,
                    (i & 4) == 0 ? shape.low.z : shape.high.z), aspect);
            if (point == null) continue;
            float x = (point.x - anchor.x) * right.x + (point.y - anchor.y) * right.y;
            float y = (point.x - anchor.x) * armUp.x + (point.y - anchor.y) * armUp.y;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            depth = Math.min(depth, point.z); points++;
        }
        if (points < 4) return;
        float coreX = (maxX - minX) * .5f, coreY = (maxY - minY) * .5f;
        if (coreX < .0001f || coreY < .0001f) return;
        // The fitted core and chains keep the arm's axes; the flame's separate axes always rise upward.
        Vector2f rise = flameRise(overlay);
        float riseX = rise.dot(right), riseY = rise.dot(armUp);
        float flameWidth = coreX * 2.10f, flameHeight = Math.max(coreY * 2, coreX * 2.45f);
        // Bound the rotated flame so sideways or downward-pointing hands cannot clip its upper tips.
        float halfX = Math.max(coreX * 1.15f, Math.abs(riseY) * flameWidth + Math.abs(riseX) * flameHeight);
        float halfY = Math.max(coreY * 1.15f, Math.abs(riseX) * flameWidth + Math.abs(riseY) * flameHeight);
        float middleX = (maxX + minX) * .5f, middleY = (maxY + minY) * .5f;
        float x = anchor.x + right.x * middleX + armUp.x * middleY;
        float y = anchor.y + right.y * middleX + armUp.y * middleY;
        float bendX = 0, bendY = 0;
        Vector3f tail = screen(hand, new Vector3f(hand.drag).add(0, centerY, 0), aspect);
        if (tail != null) {
            float dx = tail.x - anchor.x, dy = tail.y - anchor.y;
            bendX = Math.clamp((dx * right.x + dy * right.y) / halfX, -.40f, .40f);
            bendY = Math.clamp((dx * armUp.x + dy * armUp.y) / halfY, -.32f, .32f);
        }
        // The short fitted core follows the skin; only free tips are displaced by airflow in the material.
        FistHeldRenderer.renderColored(hand.time, hand.power, hand.seed, hand.empowered, hand.red, overlay,
                x / aspect, y, Math.max(-.999f, depth - .00001f),
                right.x * halfX / aspect, right.y * halfX, armUp.x * halfY / aspect, armUp.y * halfY,
                bendX, bendY, coreX / halfX, coreY / halfY,
                new FistHeldRenderer.FlameSpace(riseX, riseY, halfX / flameWidth,
                        halfY / flameWidth, flameHeight / flameWidth));
    }

    private static Vector2f flameRise(boolean firstPerson) {
        // In the hand overlay, upward means toward the top of the view even while aiming up/down.
        if (firstPerson) return new Vector2f(0, 1);
        // World-Y projected onto the billboard plane, independent of player bones and attack poses.
        Vector3f worldUp = new Vector3f(0, 1, 0).rotate(new Quaternionf(
                Minecraft.getInstance().gameRenderer.getMainCamera().rotation()).conjugate());
        Vector2f rise = new Vector2f(worldUp.x, worldUp.y);
        // Looking vertically makes world-up project to a point; keep a stable upright billboard there.
        return rise.lengthSquared() < .0001f ? new Vector2f(0, 1) : rise.normalize();
    }
    private static Vector3f screen(Hand hand, Vector3f local, float aspect) {
        return FistSpriteRenderer.project(hand.projection, hand.transform.transformPosition(local), aspect);
    }
}
