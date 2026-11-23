package xuro;

import java.util.*;

/** Minimaler JSON-Parser/Writer ohne Abhängigkeiten. Objekte = LinkedHashMap, Arrays = ArrayList, Zahlen = Long/Double. */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String s) {
        Json j = new Json(s);
        return j.val();
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object val() {
        ws();
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return num();
        }
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = str();
            ws(); i++; // ':'
            m.put(k, val());
            ws();
            char c = s.charAt(i++);
            if (c == '}') break;
        }
        return m;
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            l.add(val());
            ws();
            char c = s.charAt(i++);
            if (c == ']') break;
        }
        return l;
    }

    private String str() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
        return b.toString();
    }

    private Object num() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String t = s.substring(st, i);
        if (t.isEmpty()) throw new IllegalArgumentException("JSON: unerwartetes Zeichen an Position " + st);
        if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) return Long.parseLong(t);
        return Double.parseDouble(t);
    }

    // ---------- Zugriffshilfen ----------
    @SuppressWarnings("unchecked")
    public static Map<String, Object> o(Object x) { return x instanceof Map ? (Map<String, Object>) x : new LinkedHashMap<>(); }

    @SuppressWarnings("unchecked")
    public static List<Object> a(Object x) { return x instanceof List ? (List<Object>) x : new ArrayList<>(); }

    public static String s(Object x) { return x == null ? null : x.toString(); }

    public static String s(Object x, String def) { return x == null ? def : x.toString(); }

    public static long l(Object x, long def) { return x instanceof Number ? ((Number) x).longValue() : def; }

    public static boolean b(Object x, boolean def) { return x instanceof Boolean ? (Boolean) x : def; }

    /** Pfad-Zugriff: String = Schlüssel, Integer = Index. Liefert null, wenn etwas fehlt. */
    public static Object at(Object root, Object... path) {
        Object cur = root;
        for (Object p : path) {
            if (cur == null) return null;
            if (p instanceof Integer) {
                List<Object> l = a(cur);
                int idx = (Integer) p;
                cur = idx < l.size() ? l.get(idx) : null;
            } else cur = o(cur).get(p.toString());
        }
        return cur;
    }

    // ---------- Writer ----------
    public static String write(Object x) {
        StringBuilder b = new StringBuilder();
        w(b, x, 0);
        return b.toString();
    }

    private static void w(StringBuilder b, Object x, int d) {
        if (x == null) b.append("null");
        else if (x instanceof String) q(b, (String) x);
        else if (x instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) x;
            if (m.isEmpty()) { b.append("{}"); return; }
            b.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(b, d + 1);
                q(b, String.valueOf(e.getKey()));
                b.append(": ");
                w(b, e.getValue(), d + 1);
                if (++n < m.size()) b.append(',');
                b.append('\n');
            }
            pad(b, d); b.append('}');
        } else if (x instanceof List) {
            List<?> l = (List<?>) x;
            if (l.isEmpty()) { b.append("[]"); return; }
            b.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                pad(b, d + 1);
                w(b, l.get(k), d + 1);
                if (k < l.size() - 1) b.append(',');
                b.append('\n');
            }
            pad(b, d); b.append(']');
        } else b.append(x.toString());
    }

    private static void pad(StringBuilder b, int d) { for (int k = 0; k < d; k++) b.append("  "); }

    private static void q(StringBuilder b, String t) {
        b.append('"');
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }
}
