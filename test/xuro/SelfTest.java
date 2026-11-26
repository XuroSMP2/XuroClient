package xuro;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Offline-Selbsttest: JSON, Regeln, Startzeile, servers.dat und Screenshots der Oberfläche. */
public class SelfTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "ok    " : "FAIL  ") + what); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("xuro");
        // JSON
        Object j = Json.parse("{\"a\":[1,2.5,true,null,\"x\\n\\u00e4\"],\"b\":{\"c\":\"d\"}}");
        check(Json.at(j, "a", 4).equals("x\nä"), "json unicode/escape");
        check(Json.l(Json.at(j, "a", 0), 0) == 1 && Json.at(j, "b", "c").equals("d"), "json pfad");
        check(Json.write(Json.parse(Json.write(j))).equals(Json.write(j)), "json roundtrip");

        // Regeln
        Object win = Json.parse("[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"osx\"}}]");
        check(Rules.allowed(win, Map.of(), "linux", "x86_64") && !Rules.allowed(win, Map.of(), "osx", "arm64"), "rules os");
        Object arm = Json.parse("[{\"action\":\"allow\",\"os\":{\"name\":\"windows\",\"arch\":\"arm64\"}}]");
        check(!Rules.allowed(arm, Map.of(), "windows", "x86_64") && Rules.allowed(arm, Map.of(), "windows", "arm64"), "rules arch");
        Object feat = Json.parse("[{\"action\":\"allow\",\"features\":{\"has_custom_resolution\":true}}]");
        check(Rules.allowed(feat, Map.of("has_custom_resolution", true)) && !Rules.allowed(feat, Map.of()), "rules features");

        // Startzeile
        Config cfg = new Config(tmp);
        cfg.ramMb = 3072; cfg.width = 1000; cfg.height = 600;
        Installer.Plan p = new Installer.Plan();
        p.mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient"; p.assetsId = "19"; p.versionName = "fabric-loader-0.16.9-1.21.4";
        p.assetsDir = tmp.resolve("assets"); p.librariesDir = tmp.resolve("libraries"); p.nativesDir = tmp.resolve("natives");
        p.classpath.add(tmp.resolve("a.jar")); p.classpath.add(tmp.resolve("b.jar"));
        p.jvm.addAll(Json.a(Json.parse("[\"-Djava.library.path=${natives_directory}\",{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}],\"value\":[\"-XstartOnFirstThread\"]},\"-cp\",\"${classpath}\"]")));
        p.game.addAll(Json.a(Json.parse("[\"--username\",\"${auth_player_name}\",\"--accessToken\",\"${auth_access_token}\",{\"rules\":[{\"action\":\"allow\",\"features\":{\"has_custom_resolution\":true}}],\"value\":[\"--width\",\"${resolution_width}\",\"--height\",\"${resolution_height}\"]},{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}],\"value\":\"--demo\"}]")));
        Config.Account acc = Auth.offline("Tester");
        Remote rem = new Remote();
        List<String> cmd = Launcher.command(cfg, p, acc, Paths.get("/usr/bin/java"), rem);
        String line = String.join(" ", cmd);
        check(line.contains("-Xmx3072M") && line.contains("--width 1000 --height 600"), "args: ram+auflösung");
        check(!line.contains("--demo") && !line.contains("${"), "args: keine Platzhalter/Demo");
        check(line.contains("--username Tester") && line.endsWith("--quickPlayMultiplayer xurosmp.daronlo.com"), "args: name+quickplay");
        check(cmd.indexOf("-cp") < cmd.indexOf(p.mainClass), "args: cp vor mainclass");
        check(Auth.offline("Tester").uuid.equals(acc.uuid) && acc.uuid.length() == 32, "offline uuid");

        // servers.dat
        Servers.ensure(tmp, "XuroSMP", "xurosmp.daronlo.com");
        try (DataInputStream in = new DataInputStream(new FileInputStream(tmp.resolve("servers.dat").toFile()))) {
            check(in.readByte() == 10 && in.readUTF().isEmpty() && in.readByte() == 9 && in.readUTF().equals("servers") && in.readByte() == 10 && in.readInt() == 1, "nbt kopf");
            check(in.readByte() == 1 && in.readUTF().equals("acceptTextures") && in.readByte() == 1, "nbt acceptTextures");
            check(in.readByte() == 8 && in.readUTF().equals("ip") && in.readUTF().equals("xurosmp.daronlo.com"), "nbt ip");
        }
        // Config
        Packs.Pack cp = rem.packs.get(0).copy("mein-pack", "Mein Pack"); cp.entries.get(1).on = false; cfg.custom.add(cp); cfg.activePack = "mein-pack";
        cfg.accounts.add(acc); cfg.modules.put("xurosmp:sodium", false); cfg.save();
        Config c2 = Config.load(tmp);
        check(c2.ramMb == 3072 && c2.accounts.size() == 1 && c2.modules.get("xurosmp:sodium") == false, "config roundtrip");
        check(c2.custom.size() == 1 && c2.custom.get(0).entries.size() == rem.packs.get(0).entries.size() && !c2.custom.get(0).entries.get(1).on && c2.activePack.equals("mein-pack"), "custom pack roundtrip");
        check(c2.instance().endsWith(Paths.get("instances", "mein-pack")), "instanz pro pack");
        Packs.Pack std = rem.packs.get(0);
        check(std.find("simple-voice-chat", "mod") != null && std.find("low-static-fire", "resourcepack") != null && std.find("low-shields", "resourcepack") != null, "xurosmp pack: voice chat, low fire, low shield");
        List<Packs.Entry> eff = Packs.effective(std, c2);
        check(eff.stream().anyMatch(e -> e.slug.equals("fabric-api")) && eff.stream().noneMatch(e -> e.slug.equals("sodium")) && eff.stream().noneMatch(e -> e.slug.equals("iris")), "effective: sperre an, sodium aus, iris default aus");
        check(Packs.validSlug("sodium") && !Packs.validSlug("../x") && !Packs.validId("A b") && Packs.makeId("Mein Pack!", Set.of("mein-pack")).equals("mein-pack-2"), "slug/id");
        check(Modrinth.searchUrl("voice", "mod", "1.21.4").contains("categories%3Afabric") && Modrinth.parse("{\"hits\":[{\"slug\":\"ok\",\"title\":\"T\"},{\"slug\":\"../bad\"}]}", "mod").size() == 1, "modrinth url/parse");
        String o1 = Mods.withResourcePack("lang:de_de\nresourcePacks:[\"vanilla\"]\n", "a.zip", true);
        check(o1.contains("resourcePacks:[\"vanilla\",\"file/a.zip\"]") && o1.contains("lang:de_de"), "options: pack aktivieren");
        check(Mods.withResourcePack(o1, "a.zip", false).contains("resourcePacks:[\"vanilla\"]") && Mods.withResourcePack("", "b.zip", true).contains("file/b.zip"), "options: pack entfernen/neu");
        check(Installer.mavenPath("org.lwjgl:lwjgl:3.3.3:natives-linux").endsWith("lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar"), "maven pfad");
        check(Servers.motd(Json.parse("{\"text\":\"§aHi\",\"extra\":[{\"text\":\" du\"}]}")).equals("Hi du"), "motd");

        // Screenshots
        Config uc = new Config(tmp.resolve("ui"));
        Ui ui = new Ui(uc);
        ui.remote.news.clear(); ui.remote.news.add(new Remote.News("XuroSMP - Jetzt live!", "Tritt unserem Netzwerk bei und erlebe ein einzigartiges Minecraft-Erlebnis!", "28.11.2026"));
        ui.status.st = new Servers.Status(); ui.status.st.online = true;
        String out = a.length > 0 ? a[0] : tmp.toString();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("t"); ui.f = f; ui.build(f); f.setSize(1280, 760); f.setVisible(true);
            uc.accounts.add(Auth.offline("Marvex123")); ui.refreshAccounts();
        });
        Thread.sleep(600);
        for (String t : new String[]{"Startseite", "Versionen", "Einstellungen", "Skins", "Mods", "Ressourcenpakete", "XuroSMP", "Konto", "Konsole"}) {
            SwingUtilities.invokeAndWait(() -> ui.tab(t));
            Thread.sleep(250);
            BufferedImage im = new BufferedImage(1280, 760, BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> { ui.f.getContentPane().paint(im.getGraphics()); });
            javax.imageio.ImageIO.write(im, "png", new File(out, "ui-" + t + ".png"));
        }
        System.out.println(fails == 0 ? "ALLE TESTS OK" : fails + " FEHLER");
        System.exit(fails == 0 ? 0 : 1);
    }
}
