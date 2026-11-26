package xuro;

import java.util.*;

/** Server-Einstellungen, News und Mod-Liste. Standardwerte sind eingebaut, die Website kann sie überschreiben. */
public final class Remote {
    public static final String CONFIG_URL = "https://xurosmp.daronlo.com/client/config.json";

    public Packs.Pack find(String id, Config cfg) {
        for (Packs.Pack p : packs) if (p.id.equals(id)) return p;
        for (Packs.Pack p : cfg.custom) if (p.id.equals(id)) return p;
        return packs.get(0);
    }

    public List<Packs.Pack> all(Config cfg) {
        List<Packs.Pack> l = new ArrayList<>(packs); l.addAll(cfg.custom); return l;
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
    public final List<Packs.Pack> packs = new ArrayList<>();
    public final List<News> news = new ArrayList<>();

    public Remote() {
        Packs.Pack x = new Packs.Pack("xurosmp", "XuroSMP", "Das perfekte Pack für XuroSMP: Performance, Komfort, Voice Chat und weniger Bildschirmstörung.", true);
        x.entries.add(new Packs.Entry("fabric-api", "Fabric API", "mod", "Grundlage für fast alle Mods. Muss an bleiben.", true, true));
        x.entries.add(new Packs.Entry("sodium", "Sodium", "mod", "Deutlich mehr FPS durch neue Grafik-Engine.", true, false));
        x.entries.add(new Packs.Entry("lithium", "Lithium", "mod", "Weniger Lag bei Spiellogik und Weltberechnung.", true, false));
        x.entries.add(new Packs.Entry("ferrite-core", "FerriteCore", "mod", "Spart Arbeitsspeicher.", true, false));
        x.entries.add(new Packs.Entry("immediatelyfast", "ImmediatelyFast", "mod", "Schnelleres Zeichnen von Menüs, Chat und Items.", true, false));
        x.entries.add(new Packs.Entry("entityculling", "Entity Culling", "mod", "Zeichnet nur Mobs, die du sehen kannst.", true, false));
        x.entries.add(new Packs.Entry("dynamic-fps", "Dynamic FPS", "mod", "Spart Strom, wenn das Fenster im Hintergrund ist.", true, false));
        x.entries.add(new Packs.Entry("modmenu", "Mod Menu", "mod", "Zeigt installierte Mods und ihre Einstellungen.", true, false));
        x.entries.add(new Packs.Entry("zoomify", "Zoomify", "mod", "Zoom per Taste (Standard: C).", true, false));
        x.entries.add(new Packs.Entry("appleskin", "AppleSkin", "mod", "Zeigt Hunger- und Sättigungswerte von Essen.", true, false));
        x.entries.add(new Packs.Entry("mouse-tweaks", "Mouse Tweaks", "mod", "Schneller Items im Inventar verschieben.", true, false));
        x.entries.add(new Packs.Entry("simple-voice-chat", "Simple Voice Chat", "mod", "Proximity-Voice-Chat, wenn der Server ihn anbietet.", true, false));
        x.entries.add(new Packs.Entry("low-static-fire", "Low Fire", "resourcepack", "Niedrigeres Feuer, mehr Sicht im Kampf.", true, false));
        x.entries.add(new Packs.Entry("low-shields", "Low Shield", "resourcepack", "Kleinerer Schild, freie Sicht.", true, false));
        x.entries.add(new Packs.Entry("sodium-extra", "Sodium Extra", "mod", "Zusätzliche Grafik-Optionen für Sodium.", false, false));
        x.entries.add(new Packs.Entry("iris", "Iris Shaders", "mod", "Shader-Packs. Braucht Sodium und eine gute Grafikkarte.", false, false));
        packs.add(x);
        Packs.Pack f = new Packs.Pack("performance", "Nur Performance", "Minimal: nur FPS-Mods, sonst nichts.", true);
        f.entries.add(new Packs.Entry("fabric-api", "Fabric API", "mod", "Grundlage für fast alle Mods.", true, true));
        f.entries.add(new Packs.Entry("sodium", "Sodium", "mod", "Mehr FPS.", true, false));
        f.entries.add(new Packs.Entry("lithium", "Lithium", "mod", "Weniger Lag.", true, false));
        f.entries.add(new Packs.Entry("ferrite-core", "FerriteCore", "mod", "Spart Arbeitsspeicher.", true, false));
        f.entries.add(new Packs.Entry("immediatelyfast", "ImmediatelyFast", "mod", "Schnellere Menüs.", true, false));
        f.entries.add(new Packs.Entry("entityculling", "Entity Culling", "mod", "Weniger Mobs zeichnen.", true, false));
        packs.add(f);
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
            List<Packs.Pack> loaded = new ArrayList<>();
            for (Object o : Json.a(m.get("packs"))) { Packs.Pack p = Packs.packFrom(Json.o(o), true); if (p != null && !p.entries.isEmpty()) loaded.add(p); }
            if (!loaded.isEmpty()) { r.packs.clear(); r.packs.addAll(loaded); }
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
