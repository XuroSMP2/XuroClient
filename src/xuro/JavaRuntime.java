package xuro;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import xuro.Http.Dl;
import xuro.Http.Prog;

/** Findet oder lädt die passende Java-Laufzeit (Mojangs eigene Runtime, wie der offizielle Launcher). */
public final class JavaRuntime {
    private JavaRuntime() {}

    static final String ALL = "https://launchermeta.mojang.com/v1/products/java-vm/all.json";

    static String platformKey() {
        String os = Rules.os(), a = Rules.arch();
        if (os.equals("windows")) return a.equals("arm64") ? "windows-arm64" : a.equals("x86") ? "windows-x86" : "windows-x64";
        if (os.equals("osx")) return a.equals("arm64") ? "mac-os-arm64" : "mac-os";
        return a.equals("x86") ? "linux-i386" : "linux";
    }

    static String exeName() { return Rules.os().equals("windows") ? "javaw.exe" : "java"; }

    /** Pfad zur java-Datei einer Runtime, oder null. */
    public static Path inRuntime(Path runtimeDir) {
        for (Path c : new Path[]{runtimeDir.resolve("bin").resolve(exeName()),
                runtimeDir.resolve("jre.bundle/Contents/Home/bin/java"),
                runtimeDir.resolve("Contents/Home/bin/java")})
            if (Files.isExecutable(c)) return c;
        return null;
    }

    /** Hauptversion einer Java-Installation anhand der "release"-Datei. 0 = unbekannt. */
    public static int majorOf(Path javaExe) {
        try {
            Path home = javaExe.toRealPath().getParent().getParent();
            Path rel = home.resolve("release");
            if (!Files.isRegularFile(rel)) return 0;
            for (String line : Files.readAllLines(rel, StandardCharsets.UTF_8))
                if (line.startsWith("JAVA_VERSION=")) {
                    String v = line.substring(13).replace("\"", "").trim();
                    if (v.startsWith("1.")) v = v.substring(2);
                    int end = 0;
                    while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
                    return Integer.parseInt(v.substring(0, end));
                }
        } catch (Exception ignored) {}
        return 0;
    }

    public static Path ensure(Config cfg, String component, int major, Prog p) throws IOException {
        if (cfg.javaPath != null && !cfg.javaPath.isBlank()) {
            Path c = Paths.get(cfg.javaPath.trim());
            if (Files.isDirectory(c)) { Path e = inRuntime(c); if (e != null) return e; }
            if (Files.isExecutable(c)) return c;
            throw new IOException("Der eingestellte Java-Pfad existiert nicht: " + cfg.javaPath);
        }
        Path dir = cfg.dir.resolve("runtime").resolve(component);
        Path have = inRuntime(dir);
        if (have != null && Files.isRegularFile(dir.resolve(".complete"))) return have;
        try {
            p.status("Java " + major + " laden …"); p.frac(0);
            download(dir, component, p);
            Files.writeString(dir.resolve(".complete"), "ok");
            Path e = inRuntime(dir);
            if (e != null) return e;
        } catch (IOException e) {
            p.status("Java-Download nicht möglich, suche installiertes Java …");
            Path sys = findSystem(major);
            if (sys != null) return sys;
            throw new IOException("Java " + major + " konnte nicht geladen werden und ist nicht installiert: " + e.getMessage());
        }
        Path sys = findSystem(major);
        if (sys != null) return sys;
        throw new IOException("Keine Java-" + major + "-Laufzeit gefunden.");
    }

    static void download(Path dir, String component, Prog p) throws IOException {
        Map<String, Object> all = Json.o(Json.parse(Http.get(ALL)));
        String murl = Json.s(Json.at(all, platformKey(), component, 0, "manifest", "url"));
        if (murl == null) throw new IOException("Keine Runtime '" + component + "' für " + platformKey());
        Map<String, Object> files = Json.o(Json.o(Json.parse(Http.get(murl))).get("files"));
        List<Dl> dls = new ArrayList<>();
        List<Path> exec = new ArrayList<>();
        Map<Path, String> links = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : files.entrySet()) {
            Map<String, Object> f = Json.o(e.getValue());
            Path t = dir.resolve(e.getKey()).normalize();
            if (!t.startsWith(dir)) continue;
            String type = Json.s(f.get("type"));
            if ("directory".equals(type)) Files.createDirectories(t);
            else if ("link".equals(type)) links.put(t, Json.s(f.get("target")));
            else {
                Map<String, Object> raw = Json.o(Json.at(f, "downloads", "raw"));
                dls.add(new Dl(Json.s(raw.get("url")), t, Json.s(raw.get("sha1")), Json.l(raw.get("size"), 0)));
                if (Json.b(f.get("executable"), false)) exec.add(t);
            }
        }
        Http.all(dls, "Java", p);
        for (Path t : exec) t.toFile().setExecutable(true);
        for (Map.Entry<Path, String> l : links.entrySet()) {
            try {
                Files.createDirectories(l.getKey().getParent());
                if (!Files.exists(l.getKey(), LinkOption.NOFOLLOW_LINKS)) Files.createSymbolicLink(l.getKey(), Paths.get(l.getValue()));
            } catch (IOException | UnsupportedOperationException ignored) {}
        }
    }

    /** Sucht ein installiertes Java mit mindestens der gewünschten Hauptversion. */
    public static Path findSystem(int major) {
        List<Path> cand = new ArrayList<>();
        cand.add(Paths.get(System.getProperty("java.home"), "bin", exeName()));
        String jh = System.getenv("JAVA_HOME");
        if (jh != null) cand.add(Paths.get(jh, "bin", exeName()));
        String path = System.getenv("PATH");
        if (path != null) for (String d : path.split(File.pathSeparator)) cand.add(Paths.get(d, exeName()));
        for (String base : new String[]{"/usr/lib/jvm", "C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft", "/Library/Java/JavaVirtualMachines"}) {
            Path b = Paths.get(base);
            if (!Files.isDirectory(b)) continue;
            try (var s = Files.list(b)) {
                s.forEach(x -> { cand.add(x.resolve("bin").resolve(exeName())); cand.add(x.resolve("Contents/Home/bin/java")); });
            } catch (IOException ignored) {}
        }
        Path best = null; int bestMajor = Integer.MAX_VALUE;
        for (Path c : cand) {
            try {
                if (!Files.isExecutable(c)) continue;
                int m = majorOf(c);
                if (m >= major && m < bestMajor) { best = c; bestMajor = m; }
            } catch (Exception ignored) {}
        }
        return best;
    }
}
