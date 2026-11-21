package mini.launcher;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.*;
import java.util.zip.*;

public class LaunchCore {

    private final Consumer<String> log;
    public LaunchCore(Consumer<String> log) { this.log = log; }
    private void info(String s) { log.accept(s); }

    public int launch(Path gameDir, String version, AuthProvider auth, String javaExe) throws Exception {

        if (auth instanceof AuthProvider.Microsoft) {
            info("[Error] Microsoft login is not implemented.");
            info("        Uncheck Microsoft Login to launch in offline mode.");
            throw new IllegalStateException("MSA_NOT_IMPLEMENTED");
        }

        Path versionDir = gameDir.resolve("versions").resolve(version);
        Path jsonPath = versionDir.resolve(version + ".json");
        if (!Files.exists(jsonPath)) {
            info("[Error] Version JSON does not exist: " + jsonPath);
            return -1;
        }

        JsonObject root = JsonParser.parseString(
                Files.readString(jsonPath, StandardCharsets.UTF_8)).getAsJsonObject();

        String mainClass = root.get("mainClass").getAsString();
        String assetIndex = root.getAsJsonObject("assetIndex").get("id").getAsString();
        boolean jsonIso = root.has("isolation") && root.get("isolation").getAsBoolean();

        Path effectiveGameDir = PathDetector.resolveGameDir(gameDir, versionDir, jsonIso);
        info("Effective game directory: " + effectiveGameDir);

        Path nativesDir = versionDir.resolve("natives");
        Files.createDirectories(nativesDir);
        int n = autoExtractNatives(root, gameDir, nativesDir);
        info("Native libraries extracted: " + n);

        if (!nativesDirHasLibs(nativesDir)) {
            info("[Fatal] No native libraries were found; the game will report UnsatisfiedLinkError!");
            info("        Check for missing natives-* jars under .minecraft/libraries.");
            info("        Or manually place dll/so/dylib files in: " + nativesDir);
            throw new IllegalStateException("MISSING_NATIVES");
        }

        List<String> classpath = new ArrayList<>();
        JsonArray libs = root.getAsJsonArray("libraries");
        if (libs != null) {
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                if (!isLibAllowed(lib)) continue;
                JsonObject dl = lib.getAsJsonObject("downloads");
                if (dl == null || !dl.has("artifact")) continue;
                classpath.add(gameDir.resolve("libraries")
                        .resolve(dl.getAsJsonObject("artifact").get("path").getAsString()).toString());
            }
        }
        classpath.add(versionDir.resolve(version + ".jar").toString());
        String cp = String.join(File.pathSeparator, classpath);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Xmx2G");
        cmd.add("-Djava.library.path=" + nativesDir);
        cmd.add("-cp"); cmd.add(cp);
        cmd.add(mainClass);

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", auth.username());
        vars.put("version_name", version);
        vars.put("game_directory", effectiveGameDir.toString());
        vars.put("assets_root", gameDir.resolve("assets").toString());
        vars.put("assets_index_name", assetIndex);
        vars.put("auth_uuid", auth.uuid());
        vars.put("auth_access_token", auth.accessToken());
        vars.put("user_type", auth.userType());
        vars.put("version_type", "release");
        vars.put("user_properties", "{}");
        vars.put("clientid", "");
        vars.put("auth_session", auth.accessToken());
        vars.put("game_assets", gameDir.resolve("assets").toString());

        if (root.has("minecraftArguments")) {
            for (String a : root.get("minecraftArguments").getAsString().split(" "))
                if (!a.isBlank()) cmd.add(replaceVars(a, vars));
        }
        if (root.has("arguments")) {
            JsonObject args = root.getAsJsonObject("arguments");
            if (args.has("game")) {
                for (JsonElement el : args.getAsJsonArray("game")) {
                    if (el.isJsonPrimitive()) {
                        cmd.add(replaceVars(el.getAsString(), vars));
                    } else if (el.isJsonObject()) {
                        JsonObject o = el.getAsJsonObject();
                        if (!isArgAllowed(o)) continue;
                        if (o.has("value")) {
                            JsonElement v = o.get("value");
                            if (v.isJsonPrimitive()) cmd.add(replaceVars(v.getAsString(), vars));
                            else if (v.isJsonArray())
                                for (JsonElement vv : v.getAsJsonArray())
                                    cmd.add(replaceVars(vv.getAsString(), vars));
                        }
                    }
                }
            }
        }

        info("Launch command: " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(effectiveGameDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) info(line);
        }
        int code = p.waitFor();
        info("Game exited with code=" + code);
        return code;
    }

    private String replaceVars(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> e : vars.entrySet())
            s = s.replace("${" + e.getKey() + "}", e.getValue());
        if (s.contains("${")) {
            info("[Warning] Unresolved placeholder: " + s + " (game arguments may be invalid)");
        }
        return s;
    }

    boolean isArgAllowed(JsonObject arg) {
        JsonArray rules = arg.getAsJsonArray("rules");
        if (rules == null) return true;
        String mcOs = PathDetector.osKey();
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        boolean allowed = false;
        for (JsonElement r : rules) {
            JsonObject rule = r.getAsJsonObject();
            boolean match = true;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && !os.get("name").getAsString().equals(mcOs)) match = false;
                if (os.has("arch")) {
                    boolean want32 = "x86".equals(os.get("arch").getAsString());
                    boolean is32 = osArch.contains("x86") && !osArch.contains("64");
                    if (want32 != is32) match = false;
                }
                if (os.has("version") && !osName.contains(os.get("version").getAsString())) match = false;
            }
            if (rule.has("features")) {
                JsonObject f = rule.getAsJsonObject("features");
                for (String k : f.keySet())
                    if (f.get(k).getAsBoolean()) { match = false; break; }
            }
            if (match) allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }

    boolean isLibAllowed(JsonObject lib) {
        JsonArray rules = lib.getAsJsonArray("rules");
        if (rules == null) return true;
        String mcOs = PathDetector.osKey();
        String osArch = System.getProperty("os.arch").toLowerCase();
        boolean allowed = false;
        for (JsonElement r : rules) {
            JsonObject rule = r.getAsJsonObject();
            boolean match = true;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && !os.get("name").getAsString().equals(mcOs)) match = false;
                if (os.has("arch")) {
                    boolean want32 = "x86".equals(os.get("arch").getAsString());
                    boolean is32 = osArch.contains("x86") && !osArch.contains("64");
                    if (want32 != is32) match = false;
                }
            }
            if (rule.has("features")) {
                JsonObject f = rule.getAsJsonObject("features");
                for (String k : f.keySet())
                    if (f.get(k).getAsBoolean()) { match = false; break; }
            }
            if (match) allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }

    int autoExtractNatives(JsonObject root, Path gameDir, Path nativesDir) throws IOException {
        int count = 0;

        JsonArray libs = root.getAsJsonArray("libraries");
        if (libs != null) {
            String prefix = "natives-" + PathDetector.osKey();
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                JsonObject dl = lib.getAsJsonObject("downloads");
                if (dl == null || !dl.has("classifiers")) continue;
                JsonObject cls = dl.getAsJsonObject("classifiers");
                for (String key : cls.keySet()) {
                    if (!key.startsWith(prefix)) continue;
                    Path jar = gameDir.resolve("libraries")
                            .resolve(cls.getAsJsonObject(key).get("path").getAsString());
                    if (Files.exists(jar)) count += extractNatives(jar, nativesDir);
                }
            }
        }

        Path libRoot = gameDir.resolve("libraries");
        if (Files.exists(libRoot)) {
            String osKey = PathDetector.osKey();
            try (Stream<Path> s = Files.walk(libRoot, 6)) {
                List<Path> jars = s.filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.contains("natives-" + osKey)
                                    || name.contains("natives-" + (osKey.equals("osx") ? "macos" : osKey));
                        })
                        .toList();
                for (Path jar : jars) count += extractNatives(jar, nativesDir);
            } catch (Exception e) {
                info("[Warning] Failed to scan libraries: " + e.getMessage());
            }
        }

        if (count == 0 && Files.exists(libRoot)) {
            info("[Fallback] No natives matched by classifiers; scanning every library jar...");
            try (Stream<Path> s = Files.walk(libRoot, 6)) {
                for (Path jar : s.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".jar")).toList()) {
                    count += extractNatives(jar, nativesDir);
                }
            } catch (Exception ignored) {}
        }

        return count;
    }

    boolean nativesDirHasLibs(Path dir) {
        if (!Files.exists(dir)) return false;
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib");
            });
        } catch (Exception e) { return false; }
    }

    int extractNatives(Path jar, Path outDir) throws IOException {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.contains("/") || name.contains("\\")) continue;
                if (name.startsWith("META-INF")) continue;
                if (!(name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib"))) continue;
                Path out = outDir.resolve(name);
                if (Files.exists(out)) continue;
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }
}