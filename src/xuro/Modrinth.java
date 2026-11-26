package xuro;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Suche auf Modrinth. Ergebnisse mit ungültigem Slug werden verworfen. */
public final class Modrinth {
    private Modrinth() {}

    public static final class Hit {
        public final String slug, title, desc, type; public final long downloads;
        Hit(String s, String t, String d, String ty, long dl) { slug = s; title = t; desc = d; type = ty; downloads = dl; }
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public static String searchUrl(String query, String type, String mc) {
        String facets = "[[\"project_type:" + type + "\"]";
        if (type.equals("mod")) facets += ",[\"categories:fabric\"]";
        if (mc != null && !mc.isEmpty()) facets += ",[\"versions:" + mc + "\"]";
        facets += "]";
        return "https://api.modrinth.com/v2/search?limit=20&index=" + (query.isBlank() ? "downloads" : "relevance")
                + "&query=" + enc(query.trim()) + "&facets=" + enc(facets);
    }

    public static List<Hit> parse(String body, String type) {
        List<Hit> out = new ArrayList<>();
        for (Object o : Json.a(Json.o(Json.parse(body)).get("hits"))) {
            Map<String, Object> h = Json.o(o);
            String slug = Json.s(h.get("slug"));
            if (!Packs.validSlug(slug)) continue;
            out.add(new Hit(slug, Json.s(h.get("title"), slug), Json.s(h.get("description"), ""), type, Json.l(h.get("downloads"), 0)));
        }
        return out;
    }

    public static List<Hit> search(String query, String type, String mc) throws IOException {
        return parse(Http.get(searchUrl(query, type, mc)), type);
    }
}
