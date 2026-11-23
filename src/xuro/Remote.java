package xuro;

import java.util.*;

/** Server-Einstellungen, News und Mod-Liste. Standardwerte sind eingebaut, die Website kann sie überschreiben. */
public final class Remote {
    public static final String CONFIG_URL = "https://xurosmp.daronlo.com/client/config.json";

    public static final class Mod {
        public final String slug, name, desc; public final boolean def, locked;
        Mod(String slug, String name, String desc, boolean def, boolean locked) { this.slug = slug; this.name = name; this.desc = desc; this.def = def; this.locked = locked; }
    }

    public static final class News {
        public final String title, text, date;
        News(String t, String x, String d) { title = t; text = x; date = d; }
    }

    public String serverName = "XuroSMP";
    public String serverHost = "xurosmp.daronlo.com";
    public int serverPort = 25565;
    public String discord = "https://discord.gg/snQvzggK7q";
    public String website = "https://xurosmp.daronlo.com";
    public final List<Mod> mods = new ArrayList<>();
    public final List<News> news = new ArrayList<>();

    public Remote() {
        mods.add(new Mod("fabric-api", "Fabric API", "Grundlage für fast alle Mods. Muss an bleiben.", true, true));
        mods.add(new Mod("sodium", "Sodium", "Deutlich mehr FPS durch neue Grafik-Engine.", true, false));
        mods.add(new Mod("lithium", "Lithium", "Weniger Lag bei Spiellogik und Weltberechnung.", true, false));
        mods.add(new Mod("ferrite-core", "FerriteCore", "Spart Arbeitsspeicher.", true, false));
        mods.add(new Mod("immediatelyfast", "ImmediatelyFast", "Schnelleres Zeichnen von Menüs, Chat und Items.", true, false));
        mods.add(new Mod("entityculling", "Entity Culling", "Zeichnet nur Mobs, die du sehen kannst.", true, false));
        mods.add(new Mod("sodium-extra", "Sodium Extra", "Zusätzliche Grafik-Optionen für Sodium.", false, false));
        mods.add(new Mod("dynamic-fps", "Dynamic FPS", "Spart Strom, wenn das Fenster im Hintergrund ist.", true, false));
        mods.add(new Mod("modmenu", "Mod Menu", "Zeigt installierte Mods und ihre Einstellungen.", true, false));
        mods.add(new Mod("zoomify", "Zoomify", "Zoom per Taste (Standard: C).", true, false));
        mods.add(new Mod("appleskin", "AppleSkin", "Zeigt Hunger- und Sättigungswerte von Essen.", true, false));
        mods.add(new Mod("mouse-tweaks", "Mouse Tweaks", "Schneller Items im Inventar verschieben.", true, false));
        mods.add(new Mod("iris", "Iris Shaders", "Shader-Packs. Braucht Sodium und eine gute Grafikkarte.", false, false));
        news.add(new News("Willkommen im XuroClient", "Der Launcher installiert Minecraft, Fabric und erlaubte Mods für dich.", ""));
    }

    /** Versucht die Online-Konfiguration zu laden; bei Fehlern bleiben die Standardwerte. */
    public static Remote load() {
        Remote r = new Remote();
        try {
            Map<String, Object> m = Json.o(Json.parse(Http.get(CONFIG_URL)));
            r.serverName = Json.s(m.get("serverName"), r.serverName);
            r.serverHost = Json.s(m.get("serverHost"), r.serverHost);
            r.serverPort = (int) Json.l(m.get("serverPort"), r.serverPort);
            r.discord = Json.s(m.get("discord"), r.discord);
            r.website = Json.s(m.get("website"), r.website);
            List<Object> ms = Json.a(m.get("mods"));
            if (!ms.isEmpty()) {
                r.mods.clear();
                for (Object o : ms) {
                    Map<String, Object> x = Json.o(o);
                    String slug = Json.s(x.get("slug"));
                    if (slug == null || !slug.matches("[a-z0-9._-]+")) continue;
                    r.mods.add(new Mod(slug, Json.s(x.get("name"), slug), Json.s(x.get("desc"), ""),
                            Json.b(x.get("default"), true), Json.b(x.get("locked"), false)));
                }
            }
            List<Object> ns = Json.a(m.get("news"));
            if (!ns.isEmpty()) {
                r.news.clear();
                for (Object o : ns) {
                    Map<String, Object> x = Json.o(o);
                    r.news.add(new News(Json.s(x.get("title"), ""), Json.s(x.get("text"), ""), Json.s(x.get("date"), "")));
                }
            }
        } catch (Exception ignored) {}
        return r;
    }
}
