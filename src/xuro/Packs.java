package xuro;

import java.util.*;

/** Modpacks: Liste von Mods, Ressourcenpaketen und Shadern. Das XuroSMP-Pack ist eingebaut, eigene Packs sind frei. */
public final class Packs {
    private Packs() {}

    public static final class Entry {
        public final String slug, name, type, desc;
        public boolean on; public final boolean locked;
        public Entry(String slug, String name, String type, String desc, boolean on, boolean locked) {
            this.slug = slug; this.name = name; this.type = type; this.desc = desc; this.on = on; this.locked = locked;
        }
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug", slug); m.put("name", name); m.put("type", type); m.put("desc", desc); m.put("on", on);
            return m;
        }
    }

    public static final class Pack {
        public String id, name, desc; public boolean builtin;
        public final List<Entry> entries = new ArrayList<>();
        public Pack(String id, String name, String desc, boolean builtin) { this.id = id; this.name = name; this.desc = desc; this.builtin = builtin; }

        public Entry find(String slug, String type) {
            for (Entry e : entries) if (e.slug.equals(slug) && e.type.equals(type)) return e;
            return null;
        }
        public Pack copy(String newId, String newName) {
            Pack p = new Pack(newId, newName, desc, false);
            for (Entry e : entries) p.entries.add(new Entry(e.slug, e.name, e.type, e.desc, e.on, e.locked));
            return p;
        }
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("desc", desc);
            List<Object> l = new ArrayList<>();
            for (Entry e : entries) l.add(e.toMap());
            m.put("entries", l);
            return m;
        }
    }

    public static boolean validSlug(String s) { return s != null && s.matches("[A-Za-z0-9._-]{1,64}"); }
    public static boolean validId(String s) { return s != null && s.matches("[a-z0-9-]{1,40}"); }
    public static boolean validType(String s) { return "mod".equals(s) || "resourcepack".equals(s) || "shader".equals(s); }

    public static String dirOf(String type) {
        switch (type) { case "resourcepack": return "resourcepacks"; case "shader": return "shaderpacks"; default: return "mods"; }
    }

    /** Aus einem Namen eine freie, gültige Pack-ID bauen. */
    public static String makeId(String name, Collection<String> used) {
        String b = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (b.isEmpty()) b = "pack";
        if (b.length() > 34) b = b.substring(0, 34);
        String id = b; int i = 2;
        while (used.contains(id)) id = b + "-" + i++;
        return id;
    }

    static Entry entryFrom(Map<String, Object> x, boolean locked) {
        String slug = Json.s(x.get("slug")), type = Json.s(x.get("type"), "mod");
        if (!validSlug(slug) || !validType(type)) return null;
        return new Entry(slug, Json.s(x.get("name"), slug), type, Json.s(x.get("desc"), ""),
                Json.b(x.containsKey("on") ? x.get("on") : x.get("default"), true), locked || Json.b(x.get("locked"), false));
    }

    static Pack packFrom(Map<String, Object> m, boolean builtin) {
        String id = Json.s(m.get("id"));
        if (!validId(id)) return null;
        Pack p = new Pack(id, Json.s(m.get("name"), id), Json.s(m.get("desc"), ""), builtin);
        for (Object o : Json.a(m.get("entries"))) { Entry e = entryFrom(Json.o(o), false); if (e != null) p.entries.add(e); }
        return p;
    }

    /** Einträge, die beim Start installiert sein sollen. Bei eingebauten Packs steht der Schalter in der Config. */
    public static List<Entry> effective(Pack p, Config cfg) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : p.entries) {
            boolean on = e.locked || (p.builtin ? cfg.modules.getOrDefault(p.id + ":" + e.slug, e.on) : e.on);
            if (on) out.add(new Entry(e.slug, e.name, e.type, e.desc, true, e.locked));
        }
        return out;
    }

    public static boolean isOn(Pack p, Entry e, Config cfg) {
        return e.locked || (p.builtin ? cfg.modules.getOrDefault(p.id + ":" + e.slug, e.on) : e.on);
    }

    public static void setOn(Pack p, Entry e, boolean v, Config cfg) {
        if (e.locked) return;
        if (p.builtin) cfg.modules.put(p.id + ":" + e.slug, v); else e.on = v;
    }
}
