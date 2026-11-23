package xuro;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Baut die Startzeile für Minecraft zusammen und startet den Prozess. */
public final class Launcher {
    private Launcher() {}

    static String sub(String s, Map<String, String> vars) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int a = s.indexOf("${", i);
            if (a < 0) { b.append(s, i, s.length()); break; }
            int e = s.indexOf('}', a);
            if (e < 0) { b.append(s, i, s.length()); break; }
            b.append(s, i, a);
            String k = s.substring(a + 2, e);
            b.append(vars.getOrDefault(k, "${" + k + "}"));
            i = e + 1;
        }
        return b.toString();
    }

    static void expand(List<Object> args, Map<String, String> vars, Map<String, Boolean> features, List<String> out) {
        for (Object o : args) {
            if (o instanceof String) { out.add(sub((String) o, vars)); continue; }
            Map<String, Object> m = Json.o(o);
            if (!Rules.allowed(m.get("rules"), features)) continue;
            Object v = m.get("value");
            if (v instanceof List) for (Object x : (List<?>) v) out.add(sub(String.valueOf(x), vars));
            else if (v != null) out.add(sub(String.valueOf(v), vars));
        }
    }

    public static List<String> command(Config cfg, Installer.Plan plan, Config.Account acc, Path java, Remote remote) {
        String sep = File.pathSeparator;
        StringBuilder cp = new StringBuilder();
        for (Path p : plan.classpath) { if (cp.length() > 0) cp.append(sep); cp.append(p.toAbsolutePath()); }
        Map<String, String> v = new HashMap<>();
        v.put("auth_player_name", acc.name); v.put("version_name", plan.versionName);
        v.put("game_directory", cfg.instance().toAbsolutePath().toString());
        v.put("assets_root", plan.assetsDir.toAbsolutePath().toString());
        v.put("assets_index_name", plan.assetsId);
        v.put("auth_uuid", acc.uuid); v.put("auth_access_token", acc.access);
        v.put("clientid", ""); v.put("auth_xuid", "");
        v.put("user_type", "msa"); v.put("version_type", "release");
        v.put("natives_directory", plan.nativesDir.toAbsolutePath().toString());
        v.put("launcher_name", "XuroClient"); v.put("launcher_version", Main.VERSION);
        v.put("classpath", cp.toString()); v.put("classpath_separator", sep);
        v.put("library_directory", plan.librariesDir.toAbsolutePath().toString());
        v.put("user_properties", "{}"); v.put("auth_session", acc.access);
        v.put("resolution_width", String.valueOf(cfg.width)); v.put("resolution_height", String.valueOf(cfg.height));

        Map<String, Boolean> features = new HashMap<>();
        features.put("has_custom_resolution", !cfg.fullscreen);

        List<String> cmd = new ArrayList<>();
        cmd.add(java.toAbsolutePath().toString());
        cmd.add("-Xmx" + cfg.ramMb + "M");
        cmd.add("-Xms" + Math.min(512, cfg.ramMb) + "M");
        expand(plan.jvm, v, features, cmd);
        if (plan.logArg != null && !plan.logArg.isEmpty()) cmd.add(plan.logArg);
        if (!cmd.contains("-cp") && !cmd.contains("-classpath")) { cmd.add("-cp"); cmd.add(cp.toString()); }
        cmd.add(plan.mainClass);
        expand(plan.game, v, features, cmd);
        if (cfg.fullscreen) cmd.add("--fullscreen");
        if (cfg.quickConnect && remote != null && !cmd.contains("--quickPlayMultiplayer")) {
            cmd.add("--quickPlayMultiplayer");
            cmd.add(remote.serverHost + (remote.serverPort == 25565 ? "" : ":" + remote.serverPort));
        }
        return cmd;
    }

    public static Process start(Config cfg, List<String> cmd) throws IOException {
        Files.createDirectories(cfg.instance());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cfg.instance().toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /** Zeigt die Startzeile ohne Zugangstoken (für das Log). */
    public static String safe(List<String> cmd, Config.Account acc) {
        StringBuilder b = new StringBuilder();
        for (String s : cmd) {
            if (s.equals(acc.access) && acc.access.length() > 4) s = "***";
            b.append(s.contains(" ") ? "\"" + s + "\"" : s).append(' ');
        }
        return b.toString().trim();
    }
}
