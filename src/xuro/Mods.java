package xuro;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import xuro.Http.Dl;
import xuro.Http.Prog;

/** Installiert/entfernt Mods, Ressourcenpakete und Shader über Modrinth. Fasst nur Dateien an, die der Launcher selbst installiert hat. */
public final class Mods {
    private final Config cfg;
    private final Path inst, state;
    private final Map<String, String> installed = new LinkedHashMap<>(); // "typ:slug" oder "dep:slug" -> relativer Pfad

    public Mods(Config cfg) {
        this.cfg = cfg;
        this.inst = cfg.instance();
        this.state = inst.resolve("xuro-mods.json");
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

    /** Gewünschten Zustand herstellen: aktivierte Einträge installieren, nicht mehr gewünschte entfernen. */
    public List<String> sync(String mc, List<Packs.Entry> list, Prog p) throws IOException {
        List<String> warnings = new ArrayList<>();
        List<Packs.Entry> wanted = new ArrayList<>(list);
        boolean anyMod = false, hasApi = false;
        for (Packs.Entry e : wanted) { if (e.type.equals("mod")) anyMod = true; if (e.slug.equals("fabric-api")) hasApi = true; }
        if (anyMod && !hasApi) wanted.add(0, new Packs.Entry("fabric-api", "Fabric API", "mod", "", true, true));
        Set<String> want = new LinkedHashSet<>();
        for (Packs.Entry e : wanted) want.add(e.type + ":" + e.slug);

        for (Iterator<Map.Entry<String, String>> it = installed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (e.getKey().startsWith("dep:") || want.contains(e.getKey())) continue;
            dropFile(e.getKey(), e.getValue());
            it.remove();
        }
        int i = 0;
        Set<String> keep = new HashSet<>();
        for (Packs.Entry e : wanted) {
            p.status("Prüfe " + e.name); p.frac((double) i++ / Math.max(1, wanted.size()));
            try { install(e.slug, e.type, mc, keep, 0); }
            catch (IOException ex) { warnings.add(e.name + ": " + ex.getMessage()); }
        }
        for (Iterator<Map.Entry<String, String>> it = installed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (e.getKey().startsWith("dep:") && !keep.contains(e.getKey())) { dropFile(e.getKey(), e.getValue()); it.remove(); }
        }
        saveState();
        p.frac(1);
        return warnings;
    }

    /** Schreibt den Ressourcenpaket-Eintrag in options.txt, damit das Pack beim ersten Start schon aktiv ist. */
    public static String withResourcePack(String options, String fileName, boolean enable) {
        String entry = "file/" + fileName;
        List<String> lines = new ArrayList<>(Arrays.asList(options.split("\r?\n", -1)));
        if (lines.size() > 0 && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("resourcePacks:")) continue;
            found = true;
            List<Object> arr;
            try { arr = new ArrayList<>(Json.a(Json.parse(lines.get(i).substring("resourcePacks:".length())))); } catch (Exception e) { arr = new ArrayList<>(); }
            arr.remove(entry);
            if (enable) arr.add(entry);
            lines.set(i, "resourcePacks:" + Json.write(arr).replaceAll("\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", ""));
        }
        if (!found && enable) lines.add("resourcePacks:[\"vanilla\",\"" + entry + "\"]");
        return String.join("\n", lines) + "\n";
    }

    private void setResourcePack(String fileName, boolean enable) {
        try {
            Path o = inst.resolve("options.txt");
            String cur = Files.isRegularFile(o) ? Files.readString(o, StandardCharsets.UTF_8) : "";
            if (cur.isEmpty() && !enable) return;
            Files.createDirectories(inst);
            Files.writeString(o, withResourcePack(cur, fileName, enable), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private void install(String project, String type, String mc, Set<String> keep, int depth) throws IOException {
        String key = depth == 0 ? type + ":" + project : "dep:" + project;
        if (depth > 0 && installed.containsKey(key) && Files.isRegularFile(inst.resolve(installed.get(key)))) { keep.add(key); return; }
        boolean mod = type.equals("mod");
        String url = "https://api.modrinth.com/v2/project/" + q(project) + "/version?game_versions=" + q("[\"" + mc + "\"]")
                + (mod ? "&loaders=" + q("[\"fabric\"]") : "");
        List<Object> versions = Json.a(Json.parse(Http.get(url)));
        if (versions.isEmpty()) throw new IOException("keine Version für Minecraft " + mc);
        Map<String, Object> pick = null;
        for (Object o : versions) if ("release".equals(Json.s(Json.o(o).get("version_type")))) { pick = Json.o(o); break; }
        if (pick == null) pick = Json.o(versions.get(0));
        Map<String, Object> file = null;
        for (Object f : Json.a(pick.get("files"))) if (Json.b(Json.o(f).get("primary"), false)) { file = Json.o(f); break; }
        if (file == null) file = Json.o(Json.at(pick, "files", 0));
        String fn = Json.s(file.get("filename"));
        String ext = mod ? ".jar" : ".zip";
        if (fn == null || fn.contains("/") || fn.contains("\\") || fn.contains("..") || !fn.toLowerCase(Locale.ROOT).endsWith(ext)) throw new IOException("ungültiger Dateiname");
        String dir = depth > 0 ? "mods" : Packs.dirOf(type);
        String rel = dir + "/" + fn;
        String old = installed.get(key);
        if (old != null && !old.equals(rel)) { dropFile(key, old); }
        Path dest = inst.resolve(rel).normalize();
        if (!dest.startsWith(inst.resolve(dir).normalize())) throw new IOException("ungültiger Pfad");
        Files.createDirectories(dest.getParent());
        Http.file(new Dl(Json.s(file.get("url")), dest, Json.s(Json.at(file, "hashes", "sha1")), Json.l(file.get("size"), 0)));
        installed.put(key, rel);
        keep.add(key);
        if (type.equals("resourcepack") && depth == 0) setResourcePack(fn, true);
        if (!mod) return;
        for (Object d : Json.a(pick.get("dependencies"))) {
            Map<String, Object> dep = Json.o(d);
            if (!"required".equals(Json.s(dep.get("dependency_type"))) || dep.get("project_id") == null) continue;
            try { install(Json.s(dep.get("project_id")), "mod", mc, keep, depth + 1); } catch (IOException e) { /* Abhängigkeit nicht verfügbar */ }
        }
    }

    /** Löscht eine Datei nur, wenn kein anderer Eintrag sie noch braucht. */
    private void dropFile(String key, String rel) throws IOException {
        for (Map.Entry<String, String> e : installed.entrySet()) if (!e.getKey().equals(key) && e.getValue().equals(rel)) return;
        Path p = inst.resolve(rel).normalize();
        if (!p.startsWith(inst.normalize()) || rel.contains("..")) return;
        Files.deleteIfExists(p);
        if (rel.startsWith("resourcepacks/")) setResourcePack(rel.substring("resourcepacks/".length()), false);
    }
}
