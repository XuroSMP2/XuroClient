package xuro;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import xuro.Http.Dl;
import xuro.Http.Prog;

/** Installiert/entfernt Mods über Modrinth. Fasst nur Dateien an, die der Launcher selbst installiert hat. */
public final class Mods {
    private final Config cfg;
    private final Path modsDir, state;
    private final Map<String, String> installed = new LinkedHashMap<>(); // Projekt -> Dateiname

    public Mods(Config cfg) {
        this.cfg = cfg;
        this.modsDir = cfg.instance().resolve("mods");
        this.state = cfg.instance().resolve("xuro-mods.json");
        try {
            if (Files.isRegularFile(state))
                for (Map.Entry<String, Object> e : Json.o(Json.parse(Files.readString(state, StandardCharsets.UTF_8))).entrySet())
                    installed.put(e.getKey(), Json.s(e.getValue()));
        } catch (Exception ignored) {}
    }

    private void saveState() throws IOException {
        Files.createDirectories(state.getParent());
        Files.writeString(state, Json.write(new LinkedHashMap<String, Object>(installed)), StandardCharsets.UTF_8);
    }

    static String q(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** Gewünschten Zustand herstellen: aktivierte Mods installieren, deaktivierte entfernen. */
    public List<String> sync(String mc, List<Remote.Mod> list, Prog p) throws IOException {
        Files.createDirectories(modsDir);
        List<String> warnings = new ArrayList<>();
        // Zustand nach Version: bei neuer MC-Version alles neu bestimmen
        Set<String> wanted = new LinkedHashSet<>();
        for (Remote.Mod m : list) if (m.locked || cfg.modules.getOrDefault(m.slug, m.def)) wanted.add(m.slug);

        // Entfernen
        for (Iterator<Map.Entry<String, String>> it = installed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (e.getKey().startsWith("dep:")) continue;
            if (!wanted.contains(e.getKey())) {
                dropFile(e.getKey(), e.getValue());
                it.remove();
            }
        }
        int i = 0;
        Set<String> keep = new HashSet<>();
        for (String slug : wanted) {
            p.status("Mod prüfen: " + slug); p.frac((double) i++ / Math.max(1, wanted.size()));
            try { install(slug, mc, keep, 0); }
            catch (IOException e) { warnings.add(slug + ": " + e.getMessage()); }
        }
        // verwaiste Abhängigkeiten entfernen
        for (Iterator<Map.Entry<String, String>> it = installed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (e.getKey().startsWith("dep:") && !keep.contains(e.getKey())) {
                dropFile(e.getKey(), e.getValue());
                it.remove();
            }
        }
        saveState();
        p.frac(1);
        return warnings;
    }

    private void install(String project, String mc, Set<String> keep, int depth) throws IOException {
        String key = depth == 0 ? project : "dep:" + project;
        if (depth > 0 && installed.containsKey(key) && Files.isRegularFile(modsDir.resolve(installed.get(key)))) { keep.add(key); return; }
        String url = "https://api.modrinth.com/v2/project/" + q(project) + "/version?loaders=" + q("[\"fabric\"]")
                + "&game_versions=" + q("[\"" + mc + "\"]");
        List<Object> versions = Json.a(Json.parse(Http.get(url)));
        if (versions.isEmpty()) throw new IOException("keine Fabric-Version für Minecraft " + mc);
        Map<String, Object> pick = null;
        for (Object o : versions) if ("release".equals(Json.s(Json.o(o).get("version_type")))) { pick = Json.o(o); break; }
        if (pick == null) pick = Json.o(versions.get(0));
        Map<String, Object> file = null;
        for (Object f : Json.a(pick.get("files"))) if (Json.b(Json.o(f).get("primary"), false)) { file = Json.o(f); break; }
        if (file == null) file = Json.o(Json.at(pick, "files", 0));
        String fn = Json.s(file.get("filename"));
        if (fn == null || fn.contains("/") || fn.contains("\\") || !fn.endsWith(".jar")) throw new IOException("ungültiger Dateiname");
        String old = installed.get(key);
        if (old != null && !old.equals(fn)) dropFile(key, old);
        Path dest = modsDir.resolve(fn);
        Http.file(new Dl(Json.s(file.get("url")), dest, Json.s(Json.at(file, "hashes", "sha1")), Json.l(file.get("size"), 0)));
        installed.put(key, fn);
        keep.add(key);
        for (Object d : Json.a(pick.get("dependencies"))) {
            Map<String, Object> dep = Json.o(d);
            if (!"required".equals(Json.s(dep.get("dependency_type"))) || dep.get("project_id") == null) continue;
            String pid = Json.s(dep.get("project_id"));
            try { install(pid, mc, keep, depth + 1); } catch (IOException e) { /* Abhängigkeit nicht verfügbar */ }
        }
    }

    /** Löscht eine Datei nur, wenn kein anderer Eintrag (z. B. Abhängigkeit + direkte Installation) sie noch braucht. */
    private void dropFile(String key, String file) throws IOException {
        for (Map.Entry<String, String> e : installed.entrySet())
            if (!e.getKey().equals(key) && e.getValue().equals(file)) return;
        Files.deleteIfExists(modsDir.resolve(file));
    }
}
