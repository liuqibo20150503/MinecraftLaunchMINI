package mini.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class JavaDetector {

    public static List<Path> detect() {
        Set<Path> result = new LinkedHashSet<>();
        String os = PathDetector.osKey();
        boolean win = os.equals("windows");
        String exe = win ? "javaw.exe" : "java";

        String home = System.getenv("JAVA_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Paths.get(home, "bin", exe);
            if (Files.exists(p)) result.add(p);
        }

        if (win) detectWindows(result, exe);
        else if (os.equals("osx")) detectMac(result);
        else detectLinux(result, exe);

        String path = System.getenv("PATH");
        if (path != null) {
            for (String d : path.split(File.pathSeparator)) {
                Path p = Paths.get(d, exe);
                if (Files.exists(p)) result.add(p);
            }
        }
        return new ArrayList<>(result);
    }

    private static void detectWindows(Set<Path> out, String exe) {
        String[][] regRoots = {
                {"HKLM\\SOFTWARE\\JavaSoft\\JDK"},
                {"HKLM\\SOFTWARE\\JavaSoft\\Java Development Kit"},
                {"HKLM\\SOFTWARE\\JavaSoft\\JRE"},
                {"HKCU\\SOFTWARE\\JavaSoft\\JDK"}
        };
        for (String[] arr : regRoots) {
            String root = arr[0];
            for (String version : regListKeys(root)) {
                String home = regQuery(root + "\\" + version, "JavaHome");
                if (home != null) {
                    Path p = Paths.get(home, "bin", exe);
                    if (Files.exists(p)) out.add(p);
                }
            }
        }

        List<String> bases = new ArrayList<>();
        for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"}) {
            String v = System.getenv(env);
            if (v != null) bases.add(v);
        }
        String[] vendors = {"Java", "Eclipse Adoptium", "Zulu", "Microsoft",
                "BellSoft", "Amazon Corretto", "Semeru"};
        for (String base : bases) {
            for (String vendor : vendors) {
                Path dir = Paths.get(base, vendor);
                if (!Files.exists(dir)) continue;
                try (Stream<Path> s = Files.list(dir)) {
                    s.filter(Files::isDirectory)
                            .map(d -> d.resolve("bin").resolve(exe))
                            .filter(Files::exists)
                            .forEach(out::add);
                } catch (Exception ignored) {}
            }
            Path progs = Paths.get(base, "Programs");
            if (Files.exists(progs)) {
                try (Stream<Path> s = Files.list(progs)) {
                    s.filter(Files::isDirectory)
                            .map(d -> d.resolve("bin").resolve(exe))
                            .filter(Files::exists)
                            .forEach(out::add);
                } catch (Exception ignored) {}
            }
        }
    }

    private static List<String> regListKeys(String key) {
        try {
            Process p = new ProcessBuilder("reg", "query", key)
                    .redirectErrorStream(true).start();
            List<String> keys = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith(key + "\\"))
                        keys.add(line.substring((key + "\\").length()).trim());
                }
            }
            p.waitFor();
            return keys;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private static String regQuery(String key, String value) {
        try {
            Process p = new ProcessBuilder("reg", "query", key, "/v", value)
                    .redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains(value)) {
                        String[] parts = line.trim().split("\\s{2,}");
                        if (parts.length >= 3) return parts[parts.length - 1].trim();
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return null;
    }

    private static void detectMac(Set<Path> out) {
        try {
            Process p = new ProcessBuilder("/usr/libexec/java_home", "-V")
                    .redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    int idx = line.indexOf('"');
                    if (idx >= 0) {
                        String path = line.substring(idx + 1).replace("\"", "").trim();
                        Path exe = Paths.get(path, "bin", "java");
                        if (Files.exists(exe)) out.add(exe);
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
    }

    private static void detectLinux(Set<Path> out, String exe) {
        Path jvm = Paths.get("/usr/lib/jvm");
        if (Files.exists(jvm)) {
            try (Stream<Path> s = Files.list(jvm)) {
                s.filter(Files::isDirectory)
                        .map(d -> d.resolve("bin").resolve(exe))
                        .filter(Files::exists)
                        .forEach(out::add);
            } catch (Exception ignored) {}
        }
    }
}