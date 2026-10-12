package dev.jujutsuvfx.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.io.StringReader;
import java.io.BufferedReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded asset/pose review only: no client, world, native renderer or shader compiler starts. */
final class ShinjukuRuntimeReview {
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        REPORT.put("reviewedClassLocation", ShinjukuActorGeometry.class.getProtectionDomain().getCodeSource().getLocation().toString());
        checkBasisAndWheel();
        Path resources = Path.of(args[0]);
        List<Map<String, Object>> models = new ArrayList<>();
        for (String id : new String[]{"gojo_shinjuku", "sukuna_shinjuku", "mahoraga_shinjuku"}) {
            Path file = resources.resolve("assets/jujutsuvfx/models/entity/shinjuku/" + id + ".json");
            if (!Files.exists(file)) continue;
            ResourceManager manager = manager(null, resources);
            ShinjukuActorGeometry.clearCache();
            ShinjukuActorGeometry geometry = ShinjukuActorGeometry.get(manager, id);
            require(ShinjukuActorGeometry.get(manager, id) == geometry, "Geometry was not cached");
            ShinjukuActorGeometry.clearCache();
            require(ShinjukuActorGeometry.get(manager, id) != geometry, "Reload did not invalidate cache");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            checkDuration(json, "attack", .05f * dev.jujutsuvfx.entity.ShinjukuActor.ATTACK_TICKS);
            checkDuration(json, "signature", .05f * dev.jujutsuvfx.entity.ShinjukuActor.SIGNATURE_TICKS);
            ShinjukuActorModel model = new ShinjukuActorModel(manager, id);
            int frames = 0, vertices = 0;
            for (String action : new String[]{"", "attack", "signature"}) {
                for (float seconds : new float[]{0, .4f, .8f, 1.19f, 2f, 2.59f, 3.999f, 4.001f}) {
                    model.seconds = seconds; model.action = action; model.actionSeconds = seconds;
                    model.walkSeconds = seconds; model.walkWeight = action.isEmpty() ? .75f : 0;
                    List<Vector3f> rendered = capture(geometry, model, id.startsWith("mahoraga") ? 1 : .9375f, 0);
                    require(!rendered.isEmpty(), "Model has no rendered vertices: " + id);
                    for (Vector3f vertex : rendered) require(vertex.isFinite(), "Non-finite animated vertex: " + id);
                    vertices = rendered.size(); frames++;
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id); result.put("bones", json.getAsJsonArray("bones").size());
            result.put("triangles", vertices / 4); result.put("sampledFrames", frames);
            result.put("cacheAndReload", true); result.put("finiteAnimatedVertices", true);
            models.add(result);
        }
        REPORT.put("models", models);
        REPORT.put("gameStarted", false);
        String report = new GsonBuilder().setPrettyPrinting().create().toJson(REPORT);
        Files.writeString(Path.of(args[1]), report + System.lineSeparator());
        System.out.println(report);
    }

    private static void checkDuration(JsonObject json, String name, float ticks) {
        float duration = json.getAsJsonObject("animations").getAsJsonObject(name).get("length").getAsFloat();
        require(Math.abs(duration - ticks) < .001f, name + " clip and server action duration differ");
    }

    private static void checkBasisAndWheel() throws Exception {
        // Two absolute-pivot bones exercise the same loader and PoseStack traversal as actual assets.
        String json = """
                {"format":1,"texture":[256,8],"bones":[
                  {"name":"root","parent":null,"origin":[0,16,0],"rotation":[0,0,0],"triangles":[]},
                  {"name":"wheel","parent":"root","origin":[0,32,0],"rotation":[0,0,0],"triangles":[
                    {"p":[[8,32,0],[8,32,1],[8,33,0]],"uv":[[0,0],[0,0],[0,0]]}]}],
                 "animations":{
                   "idle":{"length":4,"loop":true,"tracks":{}},
                   "signature":{"length":2.6,"loop":false,"tracks":{"wheel":{"rotation":[[0,0,20,0],[2.6,0,20,0]]}}},
                   "wheel_spin":{"length":4,"loop":true,"tracks":{"wheel":{"rotation":[[0,0,0,0],[4,0,360,0]]}}}
                 }}
                """;
        ResourceManager manager = manager(json, null);
        ShinjukuActorGeometry.clearCache();
        ShinjukuActorGeometry geometry = ShinjukuActorGeometry.get(manager, "runtime_review");
        ShinjukuActorModel model = new ShinjukuActorModel(manager, "runtime_review");
        model.action = ""; model.seconds = 0;
        Vector3f rest = capture(geometry, model, 1, 0).getFirst();
        near(rest, new Vector3f(.5f, 2.001f, 0), .0001f, "Absolute bone pivots and root ground offset");
        Vector3f restScaled = capture(geometry, model, .9375f, 0).getFirst();
        near(restScaled, new Vector3f(.46875f, 2.001f * .9375f, 0), .0001f, "Humanoid player scale");
        Vector3f yawed = capture(geometry, model, 1, 90).getFirst();
        near(yawed, new Vector3f(0, 2.001f, .5f), .0001f, "Body facing matches Minecraft yaw");
        model.seconds = 1;
        near(capture(geometry, model, 1, 0).getFirst(), new Vector3f(0, 2.001f, -.5f), .0001f, "Wheel quarter turn");
        model.seconds = 2;
        near(capture(geometry, model, 1, 0).getFirst(), new Vector3f(-.5f, 2.001f, 0), .0001f, "Wheel is not shortest-path interpolation");
        model.seconds = 1; model.action = "signature"; model.actionSeconds = .8f;
        float turn = (float) Math.toRadians(110);
        near(capture(geometry, model, 1, 0).getFirst(), new Vector3f(.5f * (float)Math.cos(turn), 2.001f,
                -.5f * (float)Math.sin(turn)), .0001f, "Signature cannot suppress permanent wheel rotation");
        model.action = ""; model.seconds = 3.999f;
        Vector3f before = capture(geometry, model, 1, 0).getFirst();
        model.seconds = 4.001f;
        Vector3f after = capture(geometry, model, 1, 0).getFirst();
        require(before.distance(after) < .002f, "Wheel discontinuity across loop boundary");
        String rotated = json.replace("\"origin\":[0,16,0],\"rotation\":[0,0,0]", "\"origin\":[0,16,0],\"rotation\":[18,31,12]")
                .replace("\"origin\":[0,32,0],\"rotation\":[0,0,0]", "\"origin\":[0,32,0],\"rotation\":[9,-17,21]");
        ShinjukuActorGeometry.clearCache();
        ShinjukuActorGeometry rotatedGeometry = ShinjukuActorGeometry.get(manager(rotated, null), "rotated_review");
        float rad = (float) Math.PI / 180;
        Vector3f expected = new Vector3f(8, 0, 0).rotate(new Quaternionf().rotationZYX(21 * rad, -17 * rad, 9 * rad));
        expected.add(0, 16, 0).rotate(new Quaternionf().rotationZYX(12 * rad, 31 * rad, 18 * rad));
        expected.add(0, 16, 0).div(16).add(0, .001f, 0);
        model.seconds = 0;
        near(capture(rotatedGeometry, model, 1, 0).getFirst(), expected, .0001f, "Nested XYZ rotation basis");
        ShinjukuActorGeometry headGeometry = ShinjukuActorGeometry.get(manager(json.replace("wheel", "head"), null), "head_review");
        model.headPitch = 20; model.headYaw = 15;
        Vector3f headExpected = new Vector3f(8, 0, 1).rotate(new Quaternionf().rotationZYX(0, -15 * rad, 20 * rad));
        headExpected.add(0, 32, 0).div(16).add(0, .001f, 0);
        near(capture(headGeometry, model, 1, 0).get(1), headExpected, .0001f, "Head pitch and yaw point toward native look direction");
        REPORT.put("absolutePivotsAndGroundOrigin", true);
        REPORT.put("nestedRotationBasisAndHeadLook", true);
        REPORT.put("vanillaBodyYawAndFacing", true);
        REPORT.put("humanoidScale", .9375);
        REPORT.put("wheelContinuousAndAddedAfterSignature", true);
    }

    private static ResourceManager manager(String inline, Path root) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("openAsReader")) {
                        if (inline != null) return new BufferedReader(new StringReader(inline));
                        ResourceLocation file = (ResourceLocation) args[0];
                        return Files.newBufferedReader(root.resolve("assets/" + file.getNamespace() + "/" + file.getPath()));
                    }
                    if (method.getName().equals("toString")) return "ShinjukuReviewResourceManager";
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static List<Vector3f> capture(ShinjukuActorGeometry geometry, ShinjukuActorModel model, float scale, float yaw) {
        List<Vector3f> output = new ArrayList<>();
        VertexConsumer vertices = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("addVertex") && args.length == 3)
                        output.add(new Vector3f((float)args[0], (float)args[1], (float)args[2]));
                    if (method.getReturnType() == VertexConsumer.class) return proxy;
                    return null;
                });
        PoseStack poses = new PoseStack();
        // Verified against 1.21.1 LivingEntityRenderer bytecode. This is its ordinary standing path.
        poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        poses.scale(-1, -1, 1);
        poses.scale(scale, scale, scale);
        poses.mulPose(Axis.YP.rotationDegrees(180));
        poses.translate(0, -1.501f, 0);
        geometry.render(poses, vertices, 15728880, 0, -1, model);
        return output;
    }

    private static void near(Vector3f actual, Vector3f expected, float tolerance, String label) {
        require(actual.distance(expected) <= tolerance, label + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
