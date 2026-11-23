package xuro;

import java.util.*;

/** Auswertung der "rules"-Blöcke aus Mojangs Versions-JSON. */
public final class Rules {
    private Rules() {}

    public static String os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "osx";
        return "linux";
    }

    public static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.equals("aarch64") || a.equals("arm64")) return "arm64";
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) return "x86";
        return "x86_64";
    }

    static boolean osMatches(Map<String, Object> os, String curOs, String curArch) {
        if (os == null || os.isEmpty()) return true;
        String n = Json.s(os.get("name"));
        if (n != null && !n.equals(curOs)) return false;
        String a = Json.s(os.get("arch"));
        if (a != null && !a.equals(curArch)) return false;
        return true;
    }

    public static boolean allowed(Object rules, Map<String, Boolean> features) {
        return allowed(rules, features, os(), arch());
    }

    public static boolean allowed(Object rules, Map<String, Boolean> features, String curOs, String curArch) {
        List<Object> l = Json.a(rules);
        if (l.isEmpty()) return true;
        boolean res = false;
        for (Object ro : l) {
            Map<String, Object> r = Json.o(ro);
            boolean applies = osMatches(r.containsKey("os") ? Json.o(r.get("os")) : null, curOs, curArch);
            if (applies && r.containsKey("features")) {
                for (Map.Entry<String, Object> f : Json.o(r.get("features")).entrySet())
                    if (features.getOrDefault(f.getKey(), false) != Json.b(f.getValue(), false)) applies = false;
            }
            if (applies) res = "allow".equals(Json.s(r.get("action")));
        }
        return res;
    }
}
