package dev.jujutsuvfx.review;

import com.google.gson.GsonBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/** Check isolated character classes against the exact installed binary, excluding debug metadata. */
final class ShinjukuBytecodeReview {
    public static void main(String[] args) throws Exception {
        Path jarPath = Path.of(args[0]), root = Path.of(args[1]);
        List<Map<String, Object>> modified = new ArrayList<>(), compiled = new ArrayList<>();
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            for (String name : new String[]{"entity/VfxEntities", "item/VfxItems", "client/ClientHooks"}) {
                String entry = "dev/jujutsuvfx/" + name + ".class";
                byte[] installed = jar.getInputStream(jar.getEntry(entry)).readAllBytes();
                byte[] equivalent = Files.readAllBytes(root.resolve("baseline-classes").resolve(entry));
                String installedCode = sha(normalize(installed)), equivalentCode = sha(normalize(equivalent));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("class", entry); result.put("normalizedInstalledSha256", installedCode);
                result.put("normalizedSourceWithoutOurChangesSha256", equivalentCode);
                result.put("onlyOurChangesSinceInstalledBaseline", installedCode.equals(equivalentCode));
                modified.add(result);
            }
            try (var files = Files.walk(root.resolve("classes"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                    String entry = root.resolve("classes").relativize(file).toString().replace('\\', '/');
                    compiled.add(Map.of("class", entry, "sha256", sha(Files.readAllBytes(file)),
                            "operation", jar.getEntry(entry) == null ? "add" : "replace"));
                }
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("baselineJar", jarPath.toString()); report.put("baselineSha256", sha(Files.readAllBytes(jarPath)));
        report.put("compiledAgainstInstalledBaselineOnly", true); report.put("modifiedClasses", modified);
        report.put("classOverlay", compiled);
        boolean safe = modified.stream().allMatch(m -> Boolean.TRUE.equals(m.get("onlyOurChangesSinceInstalledBaseline")));
        report.put("baselineParityPassed", safe);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
        Files.writeString(root.resolve("release-class-review.json"), json + System.lineSeparator());
        System.out.println(json);
        if (!safe) throw new IllegalStateException("A shared source contains changes beyond this character feature; inspect the baseline comparison.");
    }

    private static byte[] normalize(byte[] bytes) {
        ClassWriter output = new ClassWriter(0);
        new ClassReader(bytes).accept(output, ClassReader.SKIP_DEBUG);
        return output.toByteArray();
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
