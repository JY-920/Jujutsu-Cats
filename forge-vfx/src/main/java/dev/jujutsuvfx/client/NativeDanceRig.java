package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.ShuffleDanceMotion;
import dev.jujutsuvfx.core.YutaDanceMotion;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Uses baked bone names, which survive production obfuscation, and keeps each rig's proportions. */
public final class NativeDanceRig {
    private NativeDanceRig() {}
    private record Info(String name, WeakReference<ModelPart> parent, float length) {}
    record Role(boolean arm, int side, boolean lower, boolean front) {}
    private record Limb(ModelPart part, Role role, float length, boolean joint) {}
    private record Saved(PartPose pose, float sx, float sy, float sz) {
        Saved(ModelPart p) { this(p.storePose(), p.xScale, p.yScale, p.zScale); }
        void restore(ModelPart p) { p.loadPose(pose); p.xScale = sx; p.yScale = sy; p.zScale = sz; }
    }
    private static final class Rig {
        final List<Limb> limbs;
        final IdentityHashMap<ModelPart, Saved> saved = new IdentityHashMap<>();
        Rig(List<Limb> limbs) { this.limbs = List.copyOf(limbs); }
        void restore() { saved.forEach((part, pose) -> pose.restore(part)); saved.clear(); }
    }
    // Weak keys and weak parent links avoid retaining model trees after resource reloads.
    private static final Map<ModelPart, Info> BONES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<EntityModel<?>, Rig> RIGS = new WeakHashMap<>();
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (!(ModelPart.class.isAssignableFrom(t) || t == ModelPart[].class || Iterable.class.isAssignableFrom(t) || Map.class.isAssignableFrom(t))) continue;
                if (f.trySetAccessible()) fields.add(f);
            }
            return List.copyOf(fields);
        }
    };

    /** Called after ModelPart construction; no client singleton/GL dependency on resource worker threads. */
    public static void observe(ModelPart part, List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {
        synchronized (STRUCTURE) { STRUCTURE.put(part, new Structure(cubes, children)); }
        float length = 0;
        for (ModelPart.Cube c : cubes) length = Math.max(length, Math.max(c.maxY-c.minY, Math.max(c.maxX-c.minX,c.maxZ-c.minZ)));
        synchronized (BONES) {
            Info previous = BONES.get(part);
            BONES.put(part, new Info(previous == null ? "" : previous.name, previous == null ? new WeakReference<>(null) : previous.parent, length));
            children.forEach((name, child) -> {
                Info old = BONES.get(child);
                BONES.put(child, new Info(name, new WeakReference<>(part), old == null ? 0 : old.length));
            });
        }
    }

    /** Baked cube list and named children of one bone; the curse chimera dissects arbitrary mobs with it. */
    record Structure(List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {}
    private static final Map<ModelPart, Structure> STRUCTURE = new WeakHashMap<>();
    static Structure structure(ModelPart part) { synchronized (STRUCTURE) { return STRUCTURE.get(part); } }
    static String name(ModelPart part) { Info info = BONES.get(part); return info == null ? "" : info.name; }
    /** Every bone stored in a model field; nested bones are reachable through {@link #structure}. */
    static List<ModelPart> fieldParts(Object model) {
        List<ModelPart> parts = new ArrayList<>();
        for (Field f : FIELDS.get(model.getClass())) try {
            Object value = f.get(model);
            if (value instanceof ModelPart part) parts.add(part);
            else if (value instanceof ModelPart[] array) { for (ModelPart part : array) if (part != null) parts.add(part); }
            else if (value instanceof Map<?, ?> values) { for (Object item : values.values()) if (item instanceof ModelPart part) parts.add(part); }
            else if (value instanceof Iterable<?> values) { for (Object item : values) if (item instanceof ModelPart part) parts.add(part); }
        } catch (IllegalAccessException ignored) { /* Unsupported fields do not disable other bones. */ }
        return parts;
    }

    static Role classify(String name) {
        String tokens = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        String n = tokens.replace("_", "");
        for (String excluded : new String[]{"wing", "tail", "horn", "finger", "thumb", "爪", "翅", "尾"}) if (n.contains(excluded)) return null;
        if (n.endsWith("ear") || n.endsWith("ears")) return null;
        int side = n.contains("left") || n.contains("左") ? 1 : n.contains("right") || n.contains("右") ? -1 : 0;
        if (side == 0) for (String token : tokens.split("_")) { if (token.equals("l")) side = 1; else if (token.equals("r")) side = -1; }
        if (side == 0) return null;
        boolean arm = n.contains("arm") || n.contains("bicep") || n.contains("臂");
        boolean leg = n.contains("leg") || n.contains("thigh") || n.contains("shin") || n.contains("calf") || n.contains("腿");
        if (!arm && !leg) return null;
        boolean front = leg && (n.contains("front") || n.contains("fore") || n.contains("前"));
        boolean lower = n.contains("forearm") || n.contains("lower") || n.contains("shin") || n.contains("calf") || n.contains("小臂") || n.contains("小腿");
        return new Role(arm || front, side, lower, front);
    }

    private static void collect(ModelPart part, Set<ModelPart> parts) { part.getAllParts().forEach(parts::add); }
    private static Rig resolve(EntityModel<?> model) {
        Set<ModelPart> parts = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Field f : FIELDS.get(model.getClass())) try {
            Object value = f.get(model);
            if (value instanceof ModelPart part) collect(part, parts);
            else if (value instanceof ModelPart[] array) { for (ModelPart part : array) if (part != null) collect(part, parts); }
            else if (value instanceof Map<?, ?> values) { for (Object item : values.values()) if (item instanceof ModelPart part) collect(part, parts); }
            else if (value instanceof Iterable<?> values) { for (Object item : values) if (item instanceof ModelPart part) collect(part, parts); }
        } catch (IllegalAccessException ignored) { /* Unsupported fields do not disable other bones. */ }
        Map<ModelPart, Role> roles = new IdentityHashMap<>();
        for (ModelPart part : parts) {
            Info info = BONES.get(part);
            Role role = info == null ? null : classify(info.name);
            if (role != null) roles.put(part, role);
        }
        // Covers old/custom humanoid models even if they did not bake named children.
        if (model instanceof HumanoidModel<?> h) {
            roles.put(h.leftArm, new Role(true,1,false,false)); roles.put(h.rightArm, new Role(true,-1,false,false));
            roles.put(h.leftLeg, new Role(false,1,false,false)); roles.put(h.rightLeg, new Role(false,-1,false,false));
        }
        List<Limb> limbs = new ArrayList<>();
        for (var entry : roles.entrySet()) {
            ModelPart part = entry.getKey(); Role role = entry.getValue(); Info info = BONES.get(part);
            boolean ancestor = false;
            ModelPart parent = info == null ? null : info.parent.get();
            Set<ModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            while (parent != null && visited.add(parent)) {
                Role pr = roles.get(parent);
                if (pr != null && pr.arm == role.arm && pr.side == role.side) { ancestor = true; break; }
                Info pi = BONES.get(parent); parent = pi == null ? null : pi.parent.get();
            }
            // Overlay sleeves and duplicate upper joints must not apply a second full limb swing.
            if (ancestor && !role.lower) continue;
            float length = info == null || info.length == 0 ? 6 : info.length;
            limbs.add(new Limb(part, role, Math.max(2, Math.min(24, length)), ancestor));
        }
        return new Rig(limbs);
    }
    private static Rig rig(EntityModel<?> model) { return RIGS.computeIfAbsent(model, NativeDanceRig::resolve); }
    public static int limbCount(EntityModel<?> model) { return model == null ? 0 : (int) rig(model).limbs.stream().filter(l -> !l.joint).count(); }
    public static boolean available(EntityModel<?> model) { return limbCount(model) != 0; }
    public static void restore(EntityModel<?> model) { Rig rig = RIGS.get(model); if (rig != null) rig.restore(); }

    /** Relative motion on native pivots; no hard-coded human floor, limb replacement or scaling. */
    public static void apply(EntityModel<?> model, int style, float time, float weight) {
        Rig rig = rig(model); rig.restore();
        float[] k = ShuffleDanceMotion.sample(time);
        float step = style == 2 ? k[1] : YutaDanceMotion.step(time);
        float legCenter = 0; int legs = 0; boolean left = false, right = false;
        for (Limb limb : rig.limbs) if (!limb.joint && !limb.role.arm) {
            legCenter += limb.part.x; legs++; left |= limb.role.side > 0; right |= limb.role.side < 0;
        }
        if (legs != 0) legCenter /= legs;
        for (Limb limb : rig.limbs) {
            ModelPart p = limb.part; Role r = limb.role;
            rig.saved.put(p, new Saved(p));
            float lift = style == 2 ? ShuffleDanceMotion.lift(step, r.side) : r.side == 1 ? .72f : 0;
            if (limb.joint) {
                p.xRot += (r.arm ? style == 2 ? -.26f-.20f*Math.abs(step) : -.85f : .75f*lift)*weight;
            } else if (r.arm) {
                if (style == 2) {
                    p.xRot += (-.12f + r.side*.34f*step)*weight;
                    p.zRot += r.side*.07f*weight;
                } else {
                    p.xRot += (r.front ? -.70f : -1.03f)*weight;
                    p.zRot += r.side*(.20f+.24f*YutaDanceMotion.open(time))*weight;
                }
            } else {
                p.xRot -= ((style == 2 ? .55f : .62f)*lift)*weight;
                p.y -= limb.length*.25f*lift*weight;
                p.z -= limb.length*.13f*lift*weight;
                p.zRot += r.side*(style == 2 ? .025f : .07f)*weight;
                if (style == 2 && left && right) p.x += (legCenter-p.x)*.10f*weight;
            }
        }
    }
}
