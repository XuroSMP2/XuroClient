package xuro;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import xuro.Http.Dl;
import xuro.Http.Prog;

/** Lädt Minecraft (Client, Bibliotheken, Assets) und Fabric herunter und bereitet den Start vor. */
public final class Installer {
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String ASSET_URL = "https://resources.download.minecraft.net/";
    public static final String MIN_VERSION = "1.21.4";

    public static final class Plan {
        public String mainClass, assetsId, versionName, javaComponent = "java-runtime-delta", logArg;
        public int javaMajor = 21;
        public final List<Path> classpath = new ArrayList<>();
        public final List<Object> jvm = new ArrayList<>(), game = new ArrayList<>();
        public Path assetsDir, nativesDir, librariesDir;
    }

    private final Config cfg;
    private final Path root;

    public Installer(Config cfg) { this.cfg = cfg; this.root = cfg.dir; }

    /** Alle Release-Versionen von neu bis MIN_VERSION. */
    public static List<String> versions() throws IOException {
        List<String> out = new ArrayList<>();
        for (Object o : Json.a(Json.o(Json.parse(Http.get(MANIFEST))).get("versions"))) {
            Map<String, Object> v = Json.o(o);
            if (!"release".equals(Json.s(v.get("type")))) continue;
            out.add(Json.s(v.get("id")));
            if (MIN_VERSION.equals(Json.s(v.get("id")))) break;
        }
        return out;
    }

    private Map<String, Object> versionJson(String mc, Prog p) throws IOException {
        Path f = root.resolve("versions").resolve(mc).resolve(mc + ".json");
        String url = null, sha1 = null;
        try {
            for (Object o : Json.a(Json.o(Json.parse(Http.get(MANIFEST))).get("versions"))) {
                Map<String, Object> v = Json.o(o);
                if (mc.equals(Json.s(v.get("id")))) { url = Json.s(v.get("url")); sha1 = Json.s(v.get("sha1")); break; }
            }
        } catch (IOException e) {
            if (Files.isRegularFile(f)) return Json.o(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
            throw e;
        }
        if (url == null) throw new IOException("Minecraft " + mc + " gibt es nicht.");
        Http.file(new Dl(url, f, sha1, 0));
        return Json.o(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
    }

    /** Neuester stabiler Fabric-Loader und dessen Profil-JSON. */
    private Map<String, Object> fabricProfile(String mc) throws IOException {
        Path dirF = root.resolve("versions").resolve("fabric-" + mc);
        Path f = dirF.resolve("profile.json");
        try {
            String loader = null;
            for (Object o : Json.a(Json.parse(Http.get("https://meta.fabricmc.net/v2/versions/loader/" + mc)))) {
                if (Json.b(Json.at(o, "loader", "stable"), false)) { loader = Json.s(Json.at(o, "loader", "version")); break; }
            }
            if (loader == null) throw new IOException("Für Minecraft " + mc + " gibt es noch kein stabiles Fabric.");
            String body = Http.get("https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loader + "/profile/json");
            Files.createDirectories(dirF);
            Files.writeString(f, body, StandardCharsets.UTF_8);
            return Json.o(Json.parse(body));
        } catch (IOException e) {
            if (Files.isRegularFile(f)) return Json.o(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
            throw e;
        }
    }

    static String[] coord(String name) { return name.split(":"); }

    static String mavenPath(String name) {
        String[] c = coord(name);
        String cls = c.length > 3 ? "-" + c[3] : "";
        return c[0].replace('.', '/') + "/" + c[1] + "/" + c[2] + "/" + c[1] + "-" + c[2] + cls + ".jar";
    }

    public Plan prepare(String mc, Prog p) throws IOException {
        Plan plan = new Plan();
        plan.librariesDir = root.resolve("libraries");
        plan.assetsDir = root.resolve("assets");
        plan.nativesDir = root.resolve("natives").resolve(mc);

        p.status("Minecraft " + mc + " suchen …"); p.frac(0);
        Map<String, Object> v = versionJson(mc, p);
        Map<String, Object> fab = fabricProfile(mc);
        plan.versionName = Json.s(fab.get("id"), mc);
        plan.mainClass = Json.s(fab.get("mainClass"), Json.s(v.get("mainClass")));
        plan.assetsId = Json.s(Json.at(v, "assetIndex", "id"), mc);
        plan.javaMajor = (int) Json.l(Json.at(v, "javaVersion", "majorVersion"), 21);
        plan.javaComponent = Json.s(Json.at(v, "javaVersion", "component"), "java-runtime-delta");

        plan.jvm.addAll(Json.a(Json.at(v, "arguments", "jvm")));
        plan.jvm.addAll(Json.a(Json.at(fab, "arguments", "jvm")));
        plan.game.addAll(Json.a(Json.at(v, "arguments", "game")));
        plan.game.addAll(Json.a(Json.at(fab, "arguments", "game")));

        List<Dl> dls = new ArrayList<>();
        List<Path> natives = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Object> libs = new ArrayList<>(Json.a(fab.get("libraries")));
        libs.addAll(Json.a(v.get("libraries")));
        Map<String, Boolean> noFeatures = Map.of();
        for (Object lo : libs) {
            Map<String, Object> lib = Json.o(lo);
            if (!Rules.allowed(lib.get("rules"), noFeatures)) continue;
            String name = Json.s(lib.get("name"));
            if (name == null) continue;
            String[] c = coord(name);
            String key = c[0] + ":" + c[1] + (c.length > 3 ? ":" + c[3] : "");
            if (!seen.add(key)) continue; // erster Eintrag gewinnt (Fabric vor Vanilla)
            Map<String, Object> art = Json.o(Json.at(lib, "downloads", "artifact"));
            String path, url, sha1; long size;
            if (!art.isEmpty() && art.get("url") != null) {
                path = Json.s(art.get("path"), mavenPath(name)); url = Json.s(art.get("url"));
                sha1 = Json.s(art.get("sha1")); size = Json.l(art.get("size"), 0);
            } else {
                path = mavenPath(name);
                String base = Json.s(lib.get("url"), "https://libraries.minecraft.net/");
                if (!base.endsWith("/")) base += "/";
                url = base + path; sha1 = Json.s(lib.get("sha1")); size = Json.l(lib.get("size"), 0);
            }
            Path dest = plan.librariesDir.resolve(path);
            dls.add(new Dl(url, dest, sha1, size));
            plan.classpath.add(dest);
            if (c.length > 3 && c[3].startsWith("natives")) natives.add(dest);
        }

        Path client = root.resolve("versions").resolve(mc).resolve(mc + ".jar");
        Map<String, Object> cl = Json.o(Json.at(v, "downloads", "client"));
        dls.add(new Dl(Json.s(cl.get("url")), client, Json.s(cl.get("sha1")), Json.l(cl.get("size"), 0)));
        plan.classpath.add(client);

        Map<String, Object> log = Json.o(Json.at(v, "logging", "client"));
        if (log.get("file") != null) {
            Map<String, Object> lf = Json.o(log.get("file"));
            Path lp = plan.assetsDir.resolve("log_configs").resolve(Json.s(lf.get("id"), "client-log.xml"));
            dls.add(new Dl(Json.s(lf.get("url")), lp, Json.s(lf.get("sha1")), Json.l(lf.get("size"), 0)));
            plan.logArg = Json.s(log.get("argument"), "").replace("${path}", lp.toString());
        }

        p.status("Bibliotheken prüfen …");
        Http.all(dls, "Bibliotheken", p);

        // Assets
        Map<String, Object> ai = Json.o(v.get("assetIndex"));
        Path idx = plan.assetsDir.resolve("indexes").resolve(plan.assetsId + ".json");
        Http.file(new Dl(Json.s(ai.get("url")), idx, Json.s(ai.get("sha1")), 0));
        Map<String, Object> objs = Json.o(Json.o(Json.parse(Files.readString(idx, StandardCharsets.UTF_8))).get("objects"));
        List<Dl> assets = new ArrayList<>();
        for (Object ao : objs.values()) {
            Map<String, Object> a = Json.o(ao);
            String h = Json.s(a.get("hash"));
            String pre = h.substring(0, 2);
            assets.add(new Dl(ASSET_URL + pre + "/" + h, plan.assetsDir.resolve("objects").resolve(pre).resolve(h), h, Json.l(a.get("size"), 0)));
        }
        p.frac(0);
        Http.all(assets, "Spieldateien", p);

        extractNatives(natives, plan.nativesDir);
        return plan;
    }

    static void extractNatives(List<Path> jars, Path out) throws IOException {
        Files.createDirectories(out);
        for (Path j : jars) {
            try (ZipFile z = new ZipFile(j.toFile())) {
                for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName().toLowerCase(Locale.ROOT);
                    if (e.isDirectory() || n.startsWith("meta-inf")) continue;
                    if (!(n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib") || n.endsWith(".jnilib"))) continue;
                    Path t = out.resolve(Paths.get(e.getName()).getFileName().toString());
                    try (InputStream in = z.getInputStream(e)) { Files.copy(in, t, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        }
    }
}
