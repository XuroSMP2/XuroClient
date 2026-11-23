package xuro;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Microsoft-Login (Device-Code-Flow) -> Xbox Live -> XSTS -> Minecraft. Dazu Offline-Konten. */
public final class Auth {
    private Auth() {}

    static final String TENANT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    static final String SCOPE = "XboxLive.signin offline_access";

    public static final class DeviceCode {
        public String userCode, verificationUri, deviceCode;
        public int interval = 5, expiresIn = 900;
    }

    public static class AuthException extends IOException {
        public AuthException(String m) { super(m); }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public static DeviceCode start(String clientId) throws IOException {
        if (clientId == null || clientId.isBlank())
            throw new AuthException("Keine Microsoft-Client-ID eingetragen (Konto-Seite oder README).");
        Map<String, Object> r = Json.o(Json.parse(Http.postLenient(TENANT + "devicecode",
                "client_id=" + enc(clientId) + "&scope=" + enc(SCOPE), "application/x-www-form-urlencoded")));
        if (r.get("device_code") == null)
            throw new AuthException("Microsoft: " + Json.s(r.get("error_description"), Json.s(r.get("error"), "unbekannter Fehler")));
        DeviceCode d = new DeviceCode();
        d.userCode = Json.s(r.get("user_code")); d.deviceCode = Json.s(r.get("device_code"));
        d.verificationUri = Json.s(r.get("verification_uri"), "https://microsoft.com/link");
        d.interval = (int) Json.l(r.get("interval"), 5); d.expiresIn = (int) Json.l(r.get("expires_in"), 900);
        return d;
    }

    /** Wartet, bis der Nutzer den Code eingegeben hat, und liefert das fertige Konto. cancelled wird regelmäßig geprüft. */
    public static Config.Account finish(String clientId, DeviceCode d, java.util.function.BooleanSupplier cancelled) throws IOException {
        long end = System.currentTimeMillis() + d.expiresIn * 1000L;
        int wait = Math.max(d.interval, 2);
        while (System.currentTimeMillis() < end) {
            if (cancelled.getAsBoolean()) throw new AuthException("Abgebrochen.");
            try { Thread.sleep(wait * 1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AuthException("Abgebrochen."); }
            Map<String, Object> r = Json.o(Json.parse(Http.postLenient(TENANT + "token",
                    "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code") + "&client_id=" + enc(clientId)
                            + "&device_code=" + enc(d.deviceCode), "application/x-www-form-urlencoded")));
            if (r.get("access_token") != null) return xbox(Json.s(r.get("access_token")), Json.s(r.get("refresh_token"), ""));
            String err = Json.s(r.get("error"), "");
            if (err.equals("authorization_pending")) continue;
            if (err.equals("slow_down")) { wait += 5; continue; }
            throw new AuthException("Microsoft: " + Json.s(r.get("error_description"), err));
        }
        throw new AuthException("Der Code ist abgelaufen. Bitte neu anmelden.");
    }

    /** Erneuert ein Microsoft-Konto, falls das Token (fast) abgelaufen ist. */
    public static void refreshIfNeeded(Config cfg, Config.Account a) throws IOException {
        if (!a.type.equals("microsoft")) return;
        if (a.expires - 300 > System.currentTimeMillis() / 1000) return;
        if (a.refresh.isEmpty()) throw new AuthException("Anmeldung abgelaufen. Bitte neu anmelden.");
        Map<String, Object> r = Json.o(Json.parse(Http.postLenient(TENANT + "token",
                "grant_type=refresh_token&client_id=" + enc(cfg.msClientId) + "&refresh_token=" + enc(a.refresh)
                        + "&scope=" + enc(SCOPE), "application/x-www-form-urlencoded")));
        if (r.get("access_token") == null)
            throw new AuthException("Anmeldung abgelaufen. Bitte neu anmelden. (" + Json.s(r.get("error"), "?") + ")");
        Config.Account n = xbox(Json.s(r.get("access_token")), Json.s(r.get("refresh_token"), a.refresh));
        a.access = n.access; a.refresh = n.refresh; a.expires = n.expires; a.name = n.name; a.uuid = n.uuid;
        cfg.save();
    }

    static Config.Account xbox(String msAccess, String refresh) throws IOException {
        String json = "application/json";
        String xbl = Http.post("https://user.auth.xboxlive.com/user/authenticate",
                Json.write(map("Properties", map("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", "d=" + msAccess),
                        "RelyingParty", "http://auth.xboxlive.com", "TokenType", "JWT")), json);
        Map<String, Object> x = Json.o(Json.parse(xbl));
        String xblToken = Json.s(x.get("Token"));
        String uhs = Json.s(Json.at(x, "DisplayClaims", "xui", 0, "uhs"));

        String xstsRes;
        try {
            xstsRes = Http.post("https://xsts.auth.xboxlive.com/xsts/authorize",
                    Json.write(map("Properties", map("SandboxId", "RETAIL", "UserTokens", List.of(xblToken)),
                            "RelyingParty", "rp://api.minecraftservices.com/", "TokenType", "JWT")), json);
        } catch (IOException e) {
            throw new AuthException("Xbox-Anmeldung fehlgeschlagen. Hat das Konto ein Xbox-Profil? (" + e.getMessage() + ")");
        }
        String xsts = Json.s(Json.o(Json.parse(xstsRes)).get("Token"));

        String mc = Http.post("https://api.minecraftservices.com/authentication/login_with_xbox",
                Json.write(map("identityToken", "XBL3.0 x=" + uhs + ";" + xsts)), json);
        Map<String, Object> m = Json.o(Json.parse(mc));
        String token = Json.s(m.get("access_token"));
        long exp = System.currentTimeMillis() / 1000 + Json.l(m.get("expires_in"), 86400);

        String prof;
        try {
            prof = Http.get("https://api.minecraftservices.com/minecraft/profile", "Authorization", "Bearer " + token);
        } catch (IOException e) {
            throw new AuthException("Auf diesem Microsoft-Konto gibt es kein Minecraft Java. (" + e.getMessage() + ")");
        }
        Map<String, Object> p = Json.o(Json.parse(prof));
        Config.Account a = new Config.Account();
        a.type = "microsoft"; a.name = Json.s(p.get("name"), "?"); a.uuid = Json.s(p.get("id"), "");
        a.access = token; a.refresh = refresh; a.expires = exp;
        return a;
    }

    public static Config.Account offline(String name) {
        Config.Account a = new Config.Account();
        a.type = "offline"; a.name = name;
        a.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        a.access = "0";
        return a;
    }

    public static boolean validName(String n) { return n != null && n.matches("[A-Za-z0-9_]{3,16}"); }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
