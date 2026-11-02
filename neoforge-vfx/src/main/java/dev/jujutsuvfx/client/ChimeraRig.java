package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.jujutsuvfx.core.ChimeraAnatomy;
import dev.jujutsuvfx.core.ChimeraAnatomy.Box;
import dev.jujutsuvfx.core.ChimeraAnatomy.Organ;
import dev.jujutsuvfx.core.ChimeraAnatomy.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/**
 * Dissects the real client models of absorbed mobs into organs (head, body, legs, arms, wings, tail)
 * and stitches organs of several species into one curse. Only the donor's own baked bones, texture
 * and UVs are drawn; a cube mask selects one organ at a time, so every species keeps its look.
 */
final class ChimeraRig {
    private ChimeraRig() {}

    /** One dissected species. */
    static final class Donor {
        final LivingEntity standIn; final EntityModel<?> model; final ResourceLocation texture;
        final List<ModelPart> roots, all; final List<Organ> organs = new ArrayList<>(); final List<Set<ModelPart>> members = new ArrayList<>();
        Donor(LivingEntity standIn, EntityModel<?> model, ResourceLocation texture, List<ModelPart> roots, List<ModelPart> all) {
            this.standIn = standIn; this.model = model; this.texture = texture; this.roots = roots; this.all = all;
        }
    }
    /** An assembled chimera: placements index into the flattened organ list. */
    static final class Plan {
        final List<Placement> placements; final List<Donor> organDonor; final List<Set<ModelPart>> organMembers; final Set<Donor> donors;
        Plan(List<Placement> placements, List<Donor> organDonor, List<Set<ModelPart>> organMembers) {
            this.placements = placements; this.organDonor = organDonor; this.organMembers = organMembers;
            this.donors = Collections.newSetFromMap(new IdentityHashMap<>()); this.donors.addAll(organDonor);
        }
    }

    private static final Map<String, Optional<Donor>> DONORS = new HashMap<>();
    private static Level owner;
    private static int generation;
    static int generation() { return generation; }
    static void clear() { DONORS.clear(); owner = null; generation++; }

    static Donor donor(String id, Level level) {
        if (owner != level) { DONORS.clear(); owner = level; generation++; }
        if (DONORS.size() > 96) DONORS.clear();
        return DONORS.computeIfAbsent(id, key -> Optional.ofNullable(dissect(key, level))).orElse(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Donor dissect(String id, Level level) {
        try {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)) return null;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
            Entity entity = type.create(level);
            if (!(entity instanceof LivingEntity living)) return null;
            if (living instanceof Mob mob) mob.setNoAi(true);
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(living);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> lr)) return null;
            EntityModel model = lr.getModel();
            ResourceLocation texture = ((EntityRenderer) renderer).getTextureLocation(living);
            if (model == null || texture == null) return null;
            rest(model, living);
            List<ModelPart> fields = NativeDanceRig.fieldParts(model);
            Set<ModelPart> nested = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ModelPart part : fields) descendants(part, nested, false);
            List<ModelPart> roots = new ArrayList<>(), all = new ArrayList<>();
            Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ModelPart part : fields) if (!nested.contains(part) && seen.add(part)) roots.add(part);
            for (ModelPart root : roots) { Set<ModelPart> tree = Collections.newSetFromMap(new IdentityHashMap<>()); descendants(root, tree, true); all.addAll(tree); }
            if (roots.isEmpty()) return null;
            Donor donor = new Donor(living, model, texture, List.copyOf(roots), List.copyOf(all));
            classify(donor);
            return donor.organs.isEmpty() ? null : donor;
        } catch (RuntimeException | LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger().debug("Cannot dissect {} for a curse chimera", id, failure);
            return null;
        }
    }

    private static void descendants(ModelPart part, Set<ModelPart> out, boolean includeSelf) {
        if (includeSelf && !out.add(part)) return;
        NativeDanceRig.Structure s = NativeDanceRig.structure(part);
        if (s == null) return;
        for (ModelPart child : s.children().values()) descendants(child, out, true);
    }

    /** Rest pose straight from the donor's own animation with zero motion; no state from live mobs. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void rest(EntityModel model, LivingEntity standIn) {
        try { model.setupAnim(standIn, 0, 0, 0, 0, 0); } catch (RuntimeException ignored) { /* model keeps its baked pose */ }
    }

    private record Node(ModelPart part, int kind, int parentKind, Box own) {}

    private static void walk(ModelPart part, PoseStack pose, int parentKind, List<Node> out) {
        if (!part.visible) return;
        NativeDanceRig.Structure s = NativeDanceRig.structure(part);
        pose.pushPose();
        try {
            part.translateAndRotate(pose);
            int named = ChimeraAnatomy.kind(NativeDanceRig.name(part));
            int kind = named >= 0 ? named : parentKind;
            Box own = Box.EMPTY;
            if (s != null) {
                Matrix4f m = pose.last().pose(); Vector3f v = new Vector3f();
                for (ModelPart.Cube c : s.cubes())
                    for (int i = 0; i < 8; i++) {
                        m.transformPosition((i & 1) == 0 ? c.minX / 16f : c.maxX / 16f, (i & 2) == 0 ? c.minY / 16f : c.maxY / 16f, (i & 4) == 0 ? c.minZ / 16f : c.maxZ / 16f, v);
                        own = own.include(v.x, v.y, v.z);
                    }
            }
            out.add(new Node(part, kind, parentKind, own));
            if (s != null) for (ModelPart child : s.children().values()) walk(child, pose, kind, out);
        } finally { pose.popPose(); }
    }

    static void classify(Donor donor) {
        List<Node> nodes = new ArrayList<>();
        for (ModelPart root : donor.roots) walk(root, new PoseStack(), -1, nodes);
        // Unnamed geometry is judged against the named body, or against the biggest unnamed block.
        Box body = Box.EMPTY;
        for (Node n : nodes) if (n.kind == ChimeraAnatomy.BODY) body = body.union(n.own);
        if (body.empty()) {
            Node biggest = null;
            for (Node n : nodes) if (n.kind < 0 && !n.own.empty() && (biggest == null || n.own.volume() > biggest.own.volume())) biggest = n;
            if (biggest != null) body = biggest.own;
        }
        int[] kinds = new int[nodes.size()];
        Map<ModelPart, Integer> resolved = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            int kind = n.kind;
            if (kind < 0) {
                // An unnamed child inherits a parent that was itself judged by geometry.
                Integer inherited = null;
                for (int j = i - 1; j >= 0 && inherited == null; j--) {
                    NativeDanceRig.Structure s = NativeDanceRig.structure(nodes.get(j).part);
                    if (s != null && s.children().containsValue(n.part)) inherited = resolved.get(nodes.get(j).part);
                }
                kind = inherited != null && n.own.empty() ? inherited : n.own.empty() ? -1 : ChimeraAnatomy.geometric(n.own, body);
            }
            kinds[i] = kind; resolved.put(n.part, kind);
        }
        // Group: head/body/tail merge into one organ each; every leg, arm and wing is its own organ.
        Map<Integer, Integer> merged = new HashMap<>();
        Map<ModelPart, Integer> organOf = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            int kind = kinds[i]; Node n = nodes.get(i);
            if (kind < 0) continue;
            Integer organ = null;
            if (kind == ChimeraAnatomy.HEAD || kind == ChimeraAnatomy.BODY || kind == ChimeraAnatomy.TAIL) organ = merged.get(kind);
            else {
                // Same-kind parent continues the limb (upper leg -> lower leg); a new limb starts otherwise.
                for (int j = i - 1; j >= 0; j--) {
                    NativeDanceRig.Structure s = NativeDanceRig.structure(nodes.get(j).part);
                    if (s != null && s.children().containsValue(n.part)) { if (kinds[j] == kind) organ = organOf.get(nodes.get(j).part); break; }
                }
            }
            if (organ == null) {
                organ = donor.organs.size();
                donor.organs.add(new Organ(kind, Box.EMPTY, 0)); donor.members.add(Collections.newSetFromMap(new IdentityHashMap<>()));
                if (kind == ChimeraAnatomy.HEAD || kind == ChimeraAnatomy.BODY || kind == ChimeraAnatomy.TAIL) merged.put(kind, organ);
            }
            organOf.put(n.part, organ);
            donor.members.get(organ).add(n.part);
            Organ o = donor.organs.get(organ);
            donor.organs.set(organ, new Organ(kind, o.rest().union(n.own), 0));
        }
        // Drop empty pivots, then record which side of the donor each limb hangs from.
        for (int i = donor.organs.size() - 1; i >= 0; i--) {
            Organ o = donor.organs.get(i);
            if (o.rest().empty()) { donor.organs.remove(i); donor.members.remove(i); continue; }
            int side = Math.abs(o.rest().cx()) < .02 ? 0 : o.rest().cx() > 0 ? 1 : -1;
            donor.organs.set(i, new Organ(o.kind(), o.rest(), side));
        }
    }

    /** Builds the stitched body for one recipe; organs missing from a donor are borrowed from the others. */
    static Plan plan(String[] recipe, boolean preferWings, Level level) {
        List<String> unique = new ArrayList<>();
        for (String id : recipe) if (id != null && !id.isEmpty() && !unique.contains(id)) unique.add(id);
        List<Donor> donors = new ArrayList<>(); List<String> resolvedIds = new ArrayList<>();
        for (String id : unique) { Donor d = donor(id, level); if (d != null) { donors.add(d); resolvedIds.add(id); } }
        return plan(donors, resolvedIds, recipe, preferWings);
    }

    static Plan plan(List<Donor> donors, List<String> resolvedIds, String[] recipe, boolean preferWings) {
        if (donors.isEmpty()) return null;
        int[] slotDonor = new int[ChimeraAnatomy.SLOTS];
        for (int slot = 0; slot < slotDonor.length; slot++) slotDonor[slot] = Math.max(0, resolvedIds.indexOf(recipe[slot]));
        List<List<Organ>> organs = new ArrayList<>();
        List<Organ> flat = new ArrayList<>(); List<Donor> organDonor = new ArrayList<>(); List<Set<ModelPart>> organMembers = new ArrayList<>();
        for (Donor d : donors) {
            organs.add(d.organs);
            for (int i = 0; i < d.organs.size(); i++) { flat.add(d.organs.get(i)); organDonor.add(d); organMembers.add(d.members.get(i)); }
        }
        int[][] chosen = ChimeraAnatomy.choose(organs, slotDonor, preferWings);
        List<Placement> placements = ChimeraAnatomy.assemble(flat, chosen);
        return placements.isEmpty() ? null : new Plan(placements, organDonor, organMembers);
    }

    /** Motion inputs sampled once per frame by the entity renderer. */
    record Motion(float swing, float amount, float age, float headYaw, float headPitch, float attack, boolean flying) {}

    /** Draws in the entity's model space (after the usual 180° turn, flip and 1.501 lift). */
    /** Buffers for one donor texture: the solid organ and its emissive curse film. */
    interface Layers { VertexConsumer solid(ResourceLocation texture); VertexConsumer film(ResourceLocation texture); }

    static void draw(Plan plan, Motion m, PoseStack pose, MultiBufferSource buffers, int palette, int light, int overlay, float aura) {
        draw(plan, m, pose, new Layers() {
            public VertexConsumer solid(ResourceLocation t) { return buffers.getBuffer(RenderType.entityCutoutNoCull(SpiritTextures.colored(t, palette))); }
            public VertexConsumer film(ResourceLocation t) { return buffers.getBuffer(RenderType.entityTranslucentEmissive(SpiritTextures.colored(t, palette))); }
        }, palette, light, overlay, aura);
    }

    static void draw(Plan plan, Motion m, PoseStack pose, Layers layers, int palette, int light, int overlay, float aura) {
        for (Donor d : plan.donors) if (d.model != null) rest(d.model, d.standIn);
        for (Placement p : plan.placements) {
            Donor donor = plan.organDonor.get(p.organ());
            Set<ModelPart> members = plan.organMembers.get(p.organ());
            pose.pushPose();
            try {
                pose.translate(p.px(), p.py(), p.pz());
                animate(pose, p, m);
                float s = (float) p.scale();
                pose.scale(s, s, s);
                pose.translate(-p.ax(), -p.ay(), -p.az());
                masked(donor, members, pose, layers.solid(donor.texture), light, overlay, -1);
                if (aura > .003f) {
                    // The curse film: an emissive skin a hair larger than the organ, breathing slowly.
                    pose.translate(p.ax(), p.ay(), p.az());
                    pose.scale(1.045f, 1.045f, 1.045f);
                    pose.translate(-p.ax(), -p.ay(), -p.az());
                    int alpha = Math.max(0, Math.min(255, (int) (aura * 255)));
                    masked(donor, members, pose, layers.film(donor.texture), 0xF000F0, overlay,
                            alpha << 24 | (dev.jujutsuvfx.core.SpiritRules.tint(palette) & 0xFFFFFF));
                }
            } finally { pose.popPose(); }
        }
    }

    private static void animate(PoseStack pose, Placement p, Motion m) {
        float swing = m.swing(), amount = Math.min(1, m.amount()), age = m.age();
        switch (p.kind()) {
            case ChimeraAnatomy.LEG -> {
                float phase = (p.index() % 2 == 0 ? 0 : (float) Math.PI) + (p.side() > 0 ? 0 : (float) Math.PI);
                float stride = (float) Math.cos(swing * .6662f + phase) * amount;
                if (p.sprawled()) { pose.mulPose(Axis.YP.rotation(stride * .45f)); pose.mulPose(Axis.ZP.rotation(p.side() * Math.abs(stride) * .25f)); }
                else pose.mulPose(Axis.XP.rotation(stride * 1.1f));
            }
            case ChimeraAnatomy.ARM -> {
                float stride = (float) Math.cos(swing * .6662f + (p.side() > 0 ? Math.PI : 0)) * .8f * amount;
                float strike = (float) Math.sin(Math.sqrt(m.attack()) * Math.PI) * 1.5f;
                pose.mulPose(Axis.XP.rotation(stride - strike + (float) Math.sin(age * .09f + p.side()) * .06f));
                pose.mulPose(Axis.ZP.rotation(p.side() * (.10f + .05f * (float) Math.sin(age * .07f))));
            }
            case ChimeraAnatomy.WING -> {
                float rate = m.flying() ? .95f : .28f, depth = m.flying() ? .80f : .22f;
                pose.mulPose(Axis.ZP.rotation(p.side() * (float) Math.sin(age * rate) * depth));
            }
            case ChimeraAnatomy.HEAD -> {
                pose.mulPose(Axis.YP.rotationDegrees(m.headYaw()));
                pose.mulPose(Axis.XP.rotationDegrees(m.headPitch()));
                pose.mulPose(Axis.ZP.rotation((float) Math.sin(age * .11f) * .05f));
            }
            case ChimeraAnatomy.TAIL -> {
                pose.mulPose(Axis.YP.rotation((float) Math.sin(age * .17f) * .45f + (float) Math.cos(swing * .6662f) * .25f * amount));
                pose.mulPose(Axis.XP.rotation(.18f));
            }
            default -> pose.translate(0, Math.sin(age * .15f) * .02f, 0);
        }
    }

    /** Draws only {@code members}; other bones still pass their transforms to nested organs. */
    private static void masked(Donor donor, Set<ModelPart> members, PoseStack pose, VertexConsumer out, int light, int overlay, int color) {
        boolean[] previous = new boolean[donor.all.size()];
        for (int i = 0; i < previous.length; i++) { ModelPart part = donor.all.get(i); previous[i] = part.skipDraw; part.skipDraw = !members.contains(part); }
        try { for (ModelPart root : donor.roots) ChimeraDraw.render(root, pose, out, light, overlay, color); }
        finally { for (int i = 0; i < previous.length; i++) donor.all.get(i).skipDraw = previous[i]; }
    }
}
