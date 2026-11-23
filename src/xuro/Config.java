package xuro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Einstellungen des Launchers, gespeichert als settings.json im Datenordner. */
public final class Config {
    public static final class Account {
        public String type = "offline";   // "microsoft" oder "offline"
        public String name = "";
        public String uuid = "";
        public String access = "0";
        public String refresh = "";
        public long expires;              // Unix-Sekunden, wann das Minecraft-Token abläuft

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type); m.put("name", name); m.put("uuid", uuid);
            m.put("access", access); m.put("refresh", refresh); m.put("expires", expires);
            return m;
        }

        static Account from(Map<String, Object> m) {
            Account a = new Account();
            a.type = Json.s(m.get("type"), "offline"); a.name = Json.s(m.get("name"), "");
            a.uuid = Json.s(m.get("uuid"), ""); a.access = Json.s(m.get("access"), "0");
            a.refresh = Json.s(m.get("refresh"), ""); a.expires = Json.l(m.get("expires"), 0);
            return a;
        }
    }

    public final Path dir;
    public String mcVersion = "1.21.4";
    public int ramMb = 4096;
    public String javaPath = "";
    public int width = 1280, height = 720;
    public boolean fullscreen = false;
    public boolean quickConnect = true;
    public boolean closeOnLaunch = false;
    /** Hier die Azure-Client-ID eintragen (siehe README), dann müssen Spieler nichts eingeben. */
    public static final String DEFAULT_CLIENT_ID = "";
    public String msClientId = "";
    public final List<Account> accounts = new ArrayList<>();
    public int active = 0;
    public final Map<String, Boolean> modules = new LinkedHashMap<>();

    public Config(Path dir) { this.dir = dir; }

    public static Path defaultDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String ad = System.getenv("APPDATA");
            return Paths.get(ad != null ? ad : home, ".xuroclient");
        }
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "xuroclient");
        return Paths.get(home, ".xuroclient");
    }

    public Path instance() { return dir.resolve("instance"); }
    public Path file() { return dir.resolve("settings.json"); }

    public Account account() {
        return accounts.isEmpty() ? null : accounts.get(Math.max(0, Math.min(active, accounts.size() - 1)));
    }

    public static Config load(Path dir) {
        Config c = new Config(dir);
        try {
            Path f = c.file();
            if (Files.isRegularFile(f)) {
                Map<String, Object> m = Json.o(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
                c.mcVersion = Json.s(m.get("mcVersion"), c.mcVersion);
                c.ramMb = (int) Json.l(m.get("ramMb"), c.ramMb);
                c.javaPath = Json.s(m.get("javaPath"), "");
                c.width = (int) Json.l(m.get("width"), c.width);
                c.height = (int) Json.l(m.get("height"), c.height);
                c.fullscreen = Json.b(m.get("fullscreen"), false);
                c.quickConnect = Json.b(m.get("quickConnect"), true);
                c.closeOnLaunch = Json.b(m.get("closeOnLaunch"), false);
                c.msClientId = Json.s(m.get("msClientId"), "");
                c.active = (int) Json.l(m.get("active"), 0);
                for (Object o : Json.a(m.get("accounts"))) c.accounts.add(Account.from(Json.o(o)));
                for (Map.Entry<String, Object> e : Json.o(m.get("modules")).entrySet()) c.modules.put(e.getKey(), Json.b(e.getValue(), true));
            }
        } catch (Exception e) {
            System.err.println("settings.json nicht lesbar, starte mit Standardwerten: " + e);
        }
        if (c.msClientId.isEmpty()) c.msClientId = DEFAULT_CLIENT_ID;
        if (c.msClientId.isEmpty()) {
            String env = System.getenv("XURO_MS_CLIENT_ID");
            if (env != null) c.msClientId = env.trim();
        }
        return c;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(dir);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mcVersion", mcVersion); m.put("ramMb", ramMb); m.put("javaPath", javaPath);
            m.put("width", width); m.put("height", height); m.put("fullscreen", fullscreen);
            m.put("quickConnect", quickConnect); m.put("closeOnLaunch", closeOnLaunch);
            m.put("msClientId", msClientId); m.put("active", active);
            List<Object> acc = new ArrayList<>();
            for (Account a : accounts) acc.add(a.toMap());
            m.put("accounts", acc);
            m.put("modules", new LinkedHashMap<>(modules));
            Path tmp = file().resolveSibling("settings.json.tmp");
            Files.writeString(tmp, Json.write(m), StandardCharsets.UTF_8);
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Einstellungen konnten nicht gespeichert werden: " + e);
        }
    }
}
