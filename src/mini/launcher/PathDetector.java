package mini.launcher;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class PathDetector {

    public static String osKey() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    /** 自动扫描常见 .minecraft 目录 */
    public static List<Path> autoDetectGameDirs() {
        Set<Path> dirs = new LinkedHashSet<>();
        String os = osKey();

        if (os.equals("windows")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) dirs.add(Paths.get(appData, ".minecraft"));
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
                Path ms = Paths.get(local, "Packages",
                        "Microsoft.4297127D64EC6_8wekyb3d8bbwe",
                        "LocalCache", "Local", "Minecraft");
                if (Files.exists(ms)) dirs.add(ms);
            }
        } else if (os.equals("osx")) {
            dirs.add(Paths.get(System.getProperty("user.home"),
                    "Library", "Application Support", "minecraft"));
        } else {
            dirs.add(Paths.get(System.getProperty("user.home"), ".minecraft"));
        }

        Path cwd = Paths.get("").toAbsolutePath();
        for (Path p = cwd; p != null && p.getParent() != null; p = p.getParent()) {
            Path c = p.resolve(".minecraft");
            if (Files.exists(c.resolve("versions"))) dirs.add(c);
        }

        return dirs.stream().filter(Files::exists).collect(Collectors.toList());
    }

    public static List<String> listVersions(Path gameDir) {
        Path versionsRoot = gameDir.resolve("versions");
        if (!Files.exists(versionsRoot)) return Collections.emptyList();
        try (Stream<Path> s = Files.list(versionsRoot)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> Files.exists(versionsRoot.resolve(n).resolve(n + ".json")))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    public static Path resolveGameDir(Path rootGameDir, Path versionDir, boolean jsonIsolationFlag) {
        boolean isolated = jsonIsolationFlag
                || Files.exists(versionDir.resolve("mods"))
                || Files.exists(versionDir.resolve("saves"))
                || Files.exists(versionDir.resolve("options.txt"))
                || Files.exists(versionDir.resolve("logs"))
                || Files.exists(versionDir.resolve("resourcepacks"))
                || Files.exists(versionDir.resolve(".minecraft"));
        if (!isolated) return rootGameDir;

        Path hmcl = versionDir.resolve(".minecraft");
        if (Files.exists(hmcl)) return hmcl;
        return versionDir;
    }
}