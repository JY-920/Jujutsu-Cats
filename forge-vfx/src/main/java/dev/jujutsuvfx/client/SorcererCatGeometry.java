package dev.jujutsuvfx.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.*;

/** Imports the supplied geometry in Minecraft's model basis (-X, -Y, +Z), with independent UV sizes. */
final class SorcererCatGeometry {
    private static final Vector3f ROOT = new Vector3f(0, 24, 0);
    private static final float RAD = (float) Math.PI / 180;
    static final int CAT = 0, DETAILS = 1, EYES = 2;
    private final List<Node> roots;
    private final float duration;
    private final CatTailChain.Anchors tailAnchors;

    private record Vertex(float x, float y, float z, float u, float v) {}
    private record Quad(int texture, Vertex[] vertices, Vector3f normal) {}
    private record Key(float time, Vector3f value) {}
    private record Track(List<Key> position, List<Key> rotation, List<Key> scale) {}
    private record Node(String name, boolean bone, Vector3f origin, Vector3f rotation,
                        List<Quad> faces, List<Quad> upperArm, List<Quad> forearm, int hand, Vector3f elbowPivot, String attachment, boolean blindfold,
                        List<Node> children, Track animation, int materials) {}

    SorcererCatGeometry(ResourceManager resources, boolean sukuna) {
        this(resources, sukuna ? "sukuna_cat" : "gojo_cat", null);
    }
    SorcererCatGeometry(ResourceManager resources, String name, String clipName) {
        boolean sukuna=name.equals("sukuna_cat"), gojo=name.equals("gojo_cat");
        ResourceLocation file = new ResourceLocation(JujutsuVfx.ID, "models/entity/" + name + ".bbmodel");
        try (var reader = resources.openAsReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, JsonObject> groups = index(json.getAsJsonArray("groups"));
            Map<String, JsonObject> elements = index(json.getAsJsonArray("elements"));
            Map<String, Track> animation = new HashMap<>();
            float clipDuration = 0;
            // Only the supplied animated clip is imported; no expressions or effect scripts run.
            for (var entry : json.getAsJsonArray("animations")) {
                JsonObject clip = entry.getAsJsonObject();
                if (clipName != null && !clipName.equals(clip.get("name").getAsString())) continue;
                for (var animator : clip.getAsJsonObject("animators").entrySet()) {
                    JsonObject value = animator.getValue().getAsJsonObject();
                    if (!value.has("keyframes") || value.getAsJsonArray("keyframes").isEmpty()) continue;
                    animation.put(animator.getKey(), track(value));
                    clipDuration = Math.max(clipDuration, clip.get("length").getAsFloat());
                }
                if (!animation.isEmpty()) break;
            }
            if ((gojo || clipName != null) && animation.isEmpty()) throw new IllegalArgumentException("Gojo cat has no blindfold animation");
            duration = clipDuration;
            List<Node> loaded = new ArrayList<>();
            for (var node : json.getAsJsonArray("outliner"))
                loaded.add(readNode(node, groups, elements, json.getAsJsonArray("textures"), animation, sukuna, gojo, 0));
            roots = List.copyOf(loaded);
            tailAnchors=readTailAnchors(groups,elements);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load the sorcerer cat model " + file, error);
        }
    }

    CatTailChain.Anchors tailAnchors(){return tailAnchors;}
    private static CatTailChain.Anchors readTailAnchors(Map<String,JsonObject> groups,Map<String,JsonObject> elements){
        Vector3f end=tailCap(groups,elements,"tail1","tail_base_cube",true);
        Vector3f root=tailCap(groups,elements,"tail2","tail_tip_cube",false);
        // Standalone wheel geometry has no tail; only the cat model consumes these anchors.
        return end==null||root==null?new CatTailChain.Anchors(0,8,.5f,0,0,.5f)
                :new CatTailChain.Anchors(end.x,end.y,end.z,root.x,root.y,root.z);
    }
    private static Vector3f tailCap(Map<String,JsonObject> groups,Map<String,JsonObject> elements,
                                    String boneName,String cubeName,boolean end){
        JsonObject bone=groups.values().stream().filter(g->g.get("name").getAsString().equals(boneName)).findFirst().orElse(null);
        JsonObject cube=elements.values().stream().filter(e->e.get("name").getAsString().equals(cubeName)).findFirst().orElse(null);
        if(bone==null||cube==null||!cube.has("from")||!cube.has("to"))return null;
        Vector3f min=vector(cube,"from",0),max=vector(cube,"to",0),pivot=vector(cube,"origin",0);
        Vector3f parent=vector(bone,"origin",0),rotation=vector(cube,"rotation",0);
        Vector3f cap=new Vector3f(pivot.x-(min.x+max.x)*.5f,pivot.y-(end?min.y:max.y),(min.z+max.z)*.5f-pivot.z);
        new Quaternionf().rotationZYX(rotation.z*RAD,-rotation.y*RAD,-rotation.x*RAD).transform(cap);
        return cap.add(parent.x-pivot.x,parent.y-pivot.y,pivot.z-parent.z);
    }

    void render(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color,
                int texture, SorcererCatModel<?> model, float progress) {
        for (Node root : roots) renderNode(root, ROOT, poses, vertices, light, overlay, color,
                texture, model, Mth.clamp(progress, 0, 1) * duration);
    }

    /** Authored wheel only, centered on its rotation pivot for the narrative insert shot. */
    void renderDetachedWheel(PoseStack poses,VertexConsumer vertices,int light,int overlay,float progress) {
        poses.pushPose();
        try {
            // Authored pivot (0,18.6,-7.4), converted from the imported (0,24,0) basis.
            poses.translate(0,-5.4/16.0,7.4/16.0);
            render(poses,vertices,light,overlay,-1,DETAILS,null,progress);
        } finally {poses.popPose();}
    }

    private static void renderNode(Node node, Vector3f parent, PoseStack poses, VertexConsumer vertices,
                                   int light, int overlay, int color, int texture, SorcererCatModel<?> model, float time) {
        if ((node.materials & (1 << texture)) == 0) return;
        if (node.blindfold && model!=null && model.hideBlindfold()) return;
        poses.pushPose();
        try {
            if (node.attachment != null && model!=null) {
                // These artist groups are authored in absolute rest coordinates outside the bones.
                poses.translate(parent.x / 16, (parent.y - 24) / 16, -parent.z / 16);
                model.attachmentPose(poses, node.attachment);
                poses.translate(-parent.x / 16, (24 - parent.y) / 16, parent.z / 16);
            }
            ModelPart vanilla = node.bone && model!=null ? model.bone(node.name) : null;
            if (vanilla != null) {
                // The eight cat bones use native walking, sitting and sleeping poses; all artist
                // meshes remain below those same bones and retain their original local pivots.
                poses.translate((vanilla.x + parent.x) / 16, (vanilla.y - 24 + parent.y) / 16,
                        (vanilla.z - parent.z) / 16);
                poses.mulPose(new Quaternionf().rotationZYX(vanilla.zRot, vanilla.yRot, vanilla.xRot));
            } else {
                Vector3f move = sample(node.animation == null ? List.of() : node.animation.position, time, 0);
                Vector3f turn = sample(node.animation == null ? List.of() : node.animation.rotation, time, 0);
                poses.translate((parent.x - node.origin.x - move.x) / 16,
                        (parent.y - node.origin.y - move.y) / 16, (node.origin.z - parent.z + move.z) / 16);
                // LivingEntityRenderer turns both X and Y over. Convert the entire imported
                // hierarchy into that basis, including animated pivots and rotations. Turning
                // only Y over reflects the artist's model and exposes the opposite eye.
                poses.mulPose(new Quaternionf().rotationZYX((node.rotation.z + turn.z) * RAD,
                        -(node.rotation.y + turn.y) * RAD, -(node.rotation.x + turn.x) * RAD));
                if (node.animation != null) {
                    Vector3f scale = sample(node.animation.scale, time, 1);
                    poses.scale(scale.x, scale.y, scale.z);
                }
            }
            if (node.hand != 0 && model!=null) {
                if (model.handUnavailable(node.hand)) {
                    drawFaces(node.upperArm, poses.last(), vertices, texture, light, overlay, color);
                } else if (Math.abs(model.elbow(node.hand)) > .0001f) {
                    drawFaces(node.upperArm, poses.last(), vertices, texture, light, overlay, color);
                    poses.pushPose();
                    try {
                        poses.translate(node.elbowPivot.x, node.elbowPivot.y, node.elbowPivot.z);
                        poses.mulPose(Axis.XP.rotation(model.elbow(node.hand)));
                        poses.translate(-node.elbowPivot.x, -node.elbowPivot.y, -node.elbowPivot.z);
                        drawFaces(node.forearm, poses.last(), vertices, texture, light, overlay, color);
                    } finally { poses.popPose(); }
                } else drawFaces(node.faces, poses.last(), vertices, texture, light, overlay, color);
            } else {
                drawFaces(node.faces, poses.last(), vertices, texture, light, overlay, color);
            }
            for (Node child : node.children)
                renderNode(child, node.origin, poses, vertices, light, overlay, color, texture, model, time);
        } finally { poses.popPose(); }
    }

    private static void drawFaces(List<Quad> faces, PoseStack.Pose pose, VertexConsumer vertices,
                                  int texture, int light, int overlay, int color) {
        for (Quad quad : faces) {
            if (quad.texture != texture) continue;
            for (Vertex v : quad.vertices)
                vertices.vertex(pose.pose(), v.x, v.y, v.z).color(color).uv(v.u, v.v)
                        .overlayCoords(overlay).uv2(light).normal(pose.normal(), quad.normal.x, quad.normal.y, quad.normal.z).endVertex();
        }
    }

    private static Node readNode(JsonElement raw, Map<String, JsonObject> groups, Map<String, JsonObject> elements,
                                 JsonArray textures, Map<String, Track> animations, boolean sukuna, boolean gojo, int parentHand) {
        boolean bone = raw.isJsonObject();
        String id = bone ? raw.getAsJsonObject().get("uuid").getAsString() : raw.getAsString();
        JsonObject data = (bone ? groups : elements).get(id);
        if (data == null) throw new IllegalArgumentException("Missing Gojo cat node " + id);
        Vector3f origin = vector(data, "origin", 0), rotation = vector(data, "rotation", 0);
        String attachment = sukuna && bone ? switch (data.get("name").getAsString()) {
            case "group6" -> "right_front_leg";
            case "group7" -> "left_front_leg";
            case "group8" -> "left_hind_leg";
            case "group9" -> "right_hind_leg";
            case "group10" -> "head";
            case "group11" -> "body";
            default -> null;
        } : null;
        int inheritedHand = attachment == null ? parentHand : switch (attachment) {
            case "left_front_leg" -> SorcererCat.RIGHT;
            case "right_front_leg" -> SorcererCat.LEFT;
            default -> 0;
        };
        List<Node> children = new ArrayList<>();
        List<Quad> faces = new ArrayList<>();
        List<Quad> upperArm = new ArrayList<>(), forearm = new ArrayList<>();
        int hand = switch (data.get("name").getAsString()) {
            // Use the corrected model basis, not the misleading imported limb labels.
            case "left_front_leg_cube" -> SorcererCat.RIGHT;
            case "right_front_leg_cube" -> SorcererCat.LEFT;
            default -> bone ? 0 : inheritedHand;
        };
        if (bone) {
            for (var child : raw.getAsJsonObject().getAsJsonArray("children"))
                children.add(readNode(child, groups, elements, textures, animations, sukuna, gojo, inheritedHand));
        } else if (data.get("type").getAsString().equals("mesh")) {
            JsonObject positions = data.getAsJsonObject("vertices");
            for (var entry : data.getAsJsonObject("faces").entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                if (!hasTexture(face)) continue;
                int texture = face.get("texture").getAsInt();
                float[] size = textureSize(textures, texture);
                JsonArray ids = face.getAsJsonArray("vertices");
                if (ids.size() != 4) throw new IllegalArgumentException("Expected a Gojo cat quad: " + id);
                Vertex[] corners = new Vertex[4];
                for (int i = 0; i < 4; i++) {
                    String vertex = ids.get(i).getAsString();
                    Vector3f position = vector(positions.getAsJsonArray(vertex));
                    JsonArray uv = face.getAsJsonObject("uv").getAsJsonArray(vertex);
                    corners[i] = local(position, new Vector3f(), uv.get(0).getAsFloat() / size[0], uv.get(1).getAsFloat() / size[1]);
                }
                addQuad(faces, texture, corners);
            }
        } else {
            Vector3f min = vector(data, "from", 0), max = vector(data, "to", 0);
            for (var entry : data.getAsJsonObject("faces").entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                if (!hasTexture(face)) continue;
                int texture = face.get("texture").getAsInt();
                float[] size = textureSize(textures, texture);
                JsonArray uv = face.getAsJsonArray("uv");
                addQuad(faces, texture, cubeFace(entry.getKey(), min, max, origin,
                        uv.get(0).getAsFloat() / size[0], uv.get(1).getAsFloat() / size[1],
                        uv.get(2).getAsFloat() / size[0], uv.get(3).getAsFloat() / size[1]));
                if (hand != 0) {
                    float u0 = uv.get(0).getAsFloat() / size[0], u1 = uv.get(2).getAsFloat() / size[0];
                    float v0 = uv.get(1).getAsFloat() / size[1], v1 = uv.get(3).getAsFloat() / size[1];
                    boolean side = !entry.getKey().equals("up") && !entry.getKey().equals("down");
                    float midpoint = (min.y + max.y) * .5f;
                    addQuad(upperArm, texture, cubeFace(entry.getKey(), new Vector3f(min.x, midpoint, min.z), max, origin,
                            u0, v0, u1, side ? (v0 + v1) * .5f : v1));
                    addQuad(forearm, texture, cubeFace(entry.getKey(), min, new Vector3f(max.x, midpoint, max.z), origin,
                            u0, side ? (v0 + v1) * .5f : v0, u1, v1));
                }
            }
            if (gojo && data.get("name").getAsString().equals("head_main")) addEyes(faces, min, max, origin);
        }
        Vector3f elbowPivot = new Vector3f((origin.x - (hand == SorcererCat.RIGHT ? 1.2f : -1.2f)) / 16,
                (origin.y - 4.9f) / 16, (-5 - origin.z) / 16);
        if (hand != 0 && data.has("type") && data.get("type").getAsString().equals("mesh")) {
            // Split tattoo quads as well as the skin, so cut-off forearm markings cannot float.
            for (Quad face : faces) {
                clipY(upperArm, face, elbowPivot.y, true);
                clipY(forearm, face, elbowPivot.y, false);
            }
        }
        int materials = 0;
        for (Quad quad : faces) materials |= 1 << quad.texture;
        for (Node child : children) materials |= child.materials;
        if (data.has("visibility") && !data.get("visibility").getAsBoolean()
                || data.has("export") && !data.get("export").getAsBoolean()) materials = 0;
        return new Node(data.get("name").getAsString(), bone, origin, rotation,
                List.copyOf(faces), List.copyOf(upperArm), List.copyOf(forearm), hand, elbowPivot, attachment,
                id.equals("c2748f8a-9a65-6174-0ce3-772c518eb7e7"), List.copyOf(children), animations.get(id), materials);
    }

    private static void clipY(List<Quad> output, Quad face, float plane, boolean upper) {
        List<Vertex> clipped = new ArrayList<>();
        Vertex previous = face.vertices[3];
        boolean before = upper ? previous.y <= plane : previous.y >= plane;
        for (Vertex current : face.vertices) {
            boolean after = upper ? current.y <= plane : current.y >= plane;
            if (before != after) {
                float t = (plane - previous.y) / (current.y - previous.y);
                clipped.add(new Vertex(Mth.lerp(t, previous.x, current.x), plane, Mth.lerp(t, previous.z, current.z),
                        Mth.lerp(t, previous.u, current.u), Mth.lerp(t, previous.v, current.v)));
            }
            if (after) clipped.add(current);
            previous = current; before = after;
        }
        for (int i = 1; i + 1 < clipped.size(); i++)
            addQuad(output, face.texture, new Vertex[]{clipped.get(0), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1)});
    }

    /** Four pixels already painted in the supplied atlas: two cyan irises and two icy white highlights. */
    private static void addEyes(List<Quad> faces, Vector3f min, Vector3f max, Vector3f origin) {
        for (int u : new int[] {5, 6, 8, 9}) {
            float right = max.x - (u - 5), left = right - 1;
            float z = min.z - .006f;
            // The two-axis conversion preserves winding for every material, including
            // the culled emissive pass. Do not apply a second eye-only reversal here.
            addQuad(faces, EYES, cubeFace("north", new Vector3f(left, max.y - 2, z),
                    new Vector3f(right, max.y - 1, z), origin, u / 64f, 6 / 32f, (u + 1) / 64f, 7 / 32f));
        }
    }

    private static Vertex[] cubeFace(String side, Vector3f a, Vector3f b, Vector3f origin,
                                      float u0, float v0, float u1, float v1) {
        // The per-face atlas orientation is kept exactly, including reversed top/bottom UVs.
        Vector3f[] positions;
        float[][] uv;
        switch (side) {
            case "east" -> {
                positions = new Vector3f[] {new Vector3f(b.x,b.y,a.z), new Vector3f(b.x,b.y,b.z), new Vector3f(b.x,a.y,b.z), new Vector3f(b.x,a.y,a.z)};
                uv = new float[][] {{u1,v0},{u0,v0},{u0,v1},{u1,v1}};
            }
            case "west" -> {
                positions = new Vector3f[] {new Vector3f(a.x,a.y,b.z), new Vector3f(a.x,b.y,b.z), new Vector3f(a.x,b.y,a.z), new Vector3f(a.x,a.y,a.z)};
                uv = new float[][] {{u1,v1},{u1,v0},{u0,v0},{u0,v1}};
            }
            case "up" -> {
                positions = new Vector3f[] {new Vector3f(a.x,b.y,b.z), new Vector3f(b.x,b.y,b.z), new Vector3f(b.x,b.y,a.z), new Vector3f(a.x,b.y,a.z)};
                uv = new float[][] {{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
            }
            case "down" -> {
                positions = new Vector3f[] {new Vector3f(b.x,a.y,a.z), new Vector3f(b.x,a.y,b.z), new Vector3f(a.x,a.y,b.z), new Vector3f(a.x,a.y,a.z)};
                uv = new float[][] {{u1,v1},{u1,v0},{u0,v0},{u0,v1}};
            }
            case "south" -> {
                positions = new Vector3f[] {new Vector3f(b.x,a.y,b.z), new Vector3f(b.x,b.y,b.z), new Vector3f(a.x,b.y,b.z), new Vector3f(a.x,a.y,b.z)};
                uv = new float[][] {{u1,v1},{u1,v0},{u0,v0},{u0,v1}};
            }
            default -> {
                positions = new Vector3f[] {new Vector3f(a.x,b.y,a.z), new Vector3f(b.x,b.y,a.z), new Vector3f(b.x,a.y,a.z), new Vector3f(a.x,a.y,a.z)};
                uv = new float[][] {{u1,v0},{u0,v0},{u0,v1},{u1,v1}};
            }
        }
        Vertex[] result = new Vertex[4];
        for (int i = 0; i < 4; i++) result[i] = local(positions[i], origin, uv[i][0], uv[i][1]);
        return result;
    }

    private static Vertex local(Vector3f point, Vector3f origin, float u, float v) {
        return new Vertex((origin.x - point.x) / 16, (origin.y - point.y) / 16, (point.z - origin.z) / 16, u, v);
    }

    private static void addQuad(List<Quad> faces, int texture, Vertex[] vertices) {
        Vertex a = vertices[0], b = vertices[1], c = vertices[2];
        Vector3f normal = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z)
                .cross(c.x - a.x, c.y - a.y, c.z - a.z);
        if (normal.lengthSquared() < 1e-10f) {
            Vertex d = vertices[3];
            normal.set(c.x - a.x, c.y - a.y, c.z - a.z)
                    .cross(d.x - a.x, d.y - a.y, d.z - a.z);
        }
        // The supplied collar includes zero-thickness meshes. Keep their front/back surfaces,
        // omit collapsed side faces, and still accept a quad whose first triangle has collapsed.
        if (normal.lengthSquared() >= 1e-10f) faces.add(new Quad(texture, vertices, normal.normalize()));
    }

    private static Track track(JsonObject data) {
        Map<String, List<Key>> channels = new HashMap<>();
        for (var raw : data.getAsJsonArray("keyframes")) {
            JsonObject key = raw.getAsJsonObject();
            if (!key.get("interpolation").getAsString().equals("linear"))
                throw new IllegalArgumentException("Unsupported Gojo cat key interpolation");
            JsonObject point = key.getAsJsonArray("data_points").get(0).getAsJsonObject();
            Vector3f value = new Vector3f(point.get("x").getAsFloat(), point.get("y").getAsFloat(), point.get("z").getAsFloat());
            channels.computeIfAbsent(key.get("channel").getAsString(), ignored -> new ArrayList<>())
                    .add(new Key(key.get("time").getAsFloat(), value));
        }
        channels.values().forEach(keys -> keys.sort(Comparator.comparingDouble(Key::time)));
        return new Track(List.copyOf(channels.getOrDefault("position", List.of())),
                List.copyOf(channels.getOrDefault("rotation", List.of())), List.copyOf(channels.getOrDefault("scale", List.of())));
    }

    private static Vector3f sample(List<Key> keys, float time, float fallback) {
        if (keys.isEmpty()) return new Vector3f(fallback);
        Key before = keys.get(0);
        if (time <= before.time) return new Vector3f(before.value);
        for (int i = 1; i < keys.size(); i++) {
            Key after = keys.get(i);
            if (time <= after.time) return new Vector3f(before.value).lerp(after.value, (time - before.time) / (after.time - before.time));
            before = after;
        }
        return new Vector3f(before.value); // Hold the final key instead of looping.
    }

    private static Map<String, JsonObject> index(JsonArray values) {
        Map<String, JsonObject> result = new HashMap<>();
        for (var value : values) { JsonObject data = value.getAsJsonObject(); result.put(data.get("uuid").getAsString(), data); }
        return result;
    }

    private static Vector3f vector(JsonObject value, String name, float fallback) {
        return value.has(name) ? vector(value.getAsJsonArray(name)) : new Vector3f(fallback);
    }

    private static Vector3f vector(JsonArray value) {
        return new Vector3f(value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat());
    }

    private static boolean hasTexture(JsonObject face) { return face.has("texture") && !face.get("texture").isJsonNull(); }

    private static float[] textureSize(JsonArray textures, int index) {
        JsonObject texture = textures.get(index).getAsJsonObject();
        return new float[] {texture.get("uv_width").getAsFloat(), texture.get("uv_height").getAsFloat()};
    }
}
