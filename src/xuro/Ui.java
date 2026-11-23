package xuro;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import xuro.Widgets.*;

public final class Ui {
    final Config cfg;
    Remote remote = new Remote();
    JFrame f;
    CardLayout cards = new CardLayout();
    JPanel center = new JPanel(cards);
    final Map<String, Btn> tabs = new LinkedHashMap<>();
    Btn playBtn, versionBtn;
    Bar bar = new Bar();
    JLabel accLabel = new JLabel(), hint = new JLabel(" ");
    JTextArea console = new JTextArea();
    ServerCard card = new ServerCard();
    JPanel newsBox = new JPanel(), modsBox = new JPanel(), accBox = new JPanel(), authBox = new JPanel();
    volatile boolean busy, authCancel;
    List<String> versions = new ArrayList<>();

    public Ui(Config cfg) {
        this.cfg = cfg;
        versions.add(cfg.mcVersion);
        if (!versions.contains(Installer.MIN_VERSION)) versions.add(Installer.MIN_VERSION);
    }

    // ---------------------------------------------------------------- Aufbau
    public void show() {
        f = new JFrame("XuroClient " + Main.VERSION);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setIconImage(Mc.crystal(4));
        build(f);
        f.setSize(1020, 660); f.setMinimumSize(new Dimension(900, 600));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        background();
    }

    /** Baut die gesamte Oberfläche in ein beliebiges JFrame (auch für Tests). */
    void build(JFrame fr) {
        Dirt root = new Dirt(new BorderLayout());
        root.add(header(), BorderLayout.NORTH);
        center.setOpaque(false);
        center.add(playPage(), "Spielen");
        center.add(modsPage(), "Module");
        center.add(accountPage(), "Konto");
        center.add(settingsPage(), "Einstellungen");
        center.add(consolePage(), "Konsole");
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(22, 26, 26, 26));
        wrap.add(center);
        root.add(wrap);
        fr.setContentPane(root);
        tab("Spielen");
        refreshAccounts(); refreshMods(); refreshNews();
        setVersionText();
    }

    JComponent header() {
        JPanel h = new JPanel(new BorderLayout()); h.setBackground(Mc.HEADER);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, Color.BLACK));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); left.setOpaque(false);
        left.setBorder(new EmptyBorder(10, 14, 0, 0));
        JLabel logo = new JLabel("XuroClient", new ImageIcon(Mc.crystal(2)), SwingConstants.LEFT);
        logo.setFont(Mc.font(22, true)); logo.setForeground(Color.WHITE); logo.setIconTextGap(10);
        logo.setBorder(new EmptyBorder(0, 0, 8, 18));
        left.add(logo);
        for (String t : new String[]{"Spielen", "Module", "Konto", "Einstellungen", "Konsole"}) {
            Btn b = new Btn(t, Kind.TAB, () -> tab(t)).size(14);
            tabs.put(t, b); left.add(b);
        }
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); right.setOpaque(false);
        right.setBorder(new EmptyBorder(0, 0, 8, 14));
        right.add(new Btn("Discord", Kind.GREY, () -> open(remote.discord)).size(13));
        right.add(new Btn("Website", Kind.GREY, () -> open(remote.website)).size(13));
        h.add(left, BorderLayout.WEST); h.add(right, BorderLayout.EAST);
        // Tabs sollen unten an der Kante sitzen
        left.setAlignmentY(Component.BOTTOM_ALIGNMENT);
        return h;
    }

    void tab(String name) {
        cards.show(center, name);
        tabs.forEach((k, b) -> b.setSelected(k.equals(name)));
    }

    // ---------------------------------------------------------------- Spielen
    JComponent playPage() {
        Pn p = new Pn(new BorderLayout(0, 16)); p.setBorder(new EmptyBorder(22, 24, 20, 24));
        JPanel top = new JPanel(); top.setOpaque(false); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        card.setAlignmentX(0f); top.add(card);
        top.add(Box.createVerticalStrut(18));
        JLabel nh = Widgets.lbl("Neuigkeiten", 18, true, Mc.INK); nh.setAlignmentX(0f); top.add(nh);
        top.add(Box.createVerticalStrut(8));
        newsBox.setOpaque(false); newsBox.setLayout(new BoxLayout(newsBox, BoxLayout.Y_AXIS)); newsBox.setAlignmentX(0f);
        top.add(newsBox);
        p.add(top, BorderLayout.CENTER);

        JPanel bottom = new JPanel(); bottom.setOpaque(false); bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JPanel row = new JPanel(new BorderLayout(14, 0)); row.setOpaque(false);
        JPanel leftCol = new JPanel(); leftCol.setOpaque(false); leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        versionBtn = new Btn("", Kind.GREY, this::pickVersion).size(14); versionBtn.setAlignmentX(0f);
        leftCol.add(versionBtn); leftCol.add(Box.createVerticalStrut(8));
        accLabel.setFont(Mc.font(14, true)); accLabel.setForeground(Mc.INK); accLabel.setAlignmentX(0f);
        leftCol.add(accLabel);
        playBtn = new Btn("SPIELEN", Kind.GREEN, this::play).size(26);
        playBtn.setPreferredSize(new Dimension(300, 66));
        JPanel pb = new JPanel(new GridBagLayout()); pb.setOpaque(false); pb.add(playBtn);
        row.add(leftCol, BorderLayout.WEST); row.add(pb, BorderLayout.EAST);
        hint.setFont(Mc.sans(13, false)); hint.setForeground(Mc.INK2); hint.setAlignmentX(0f);
        bar.setAlignmentX(0f); bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); bar.set(0, "Bereit");
        bottom.add(row); bottom.add(Box.createVerticalStrut(12)); bottom.add(bar); bottom.add(Box.createVerticalStrut(6)); bottom.add(hint);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    void setVersionText() { versionBtn.setText("Minecraft " + cfg.mcVersion + "  (Fabric)  ▾"); }

    void pickVersion() {
        JPopupMenu m = new JPopupMenu();
        m.setBackground(Mc.HEADER);
        for (String v : versions) {
            JMenuItem it = new JMenuItem("Minecraft " + v);
            it.setFont(Mc.font(14, true)); it.setBackground(Mc.HEADER); it.setForeground(Color.WHITE);
            it.addActionListener(e -> { cfg.mcVersion = v; cfg.save(); setVersionText(); });
            m.add(it);
        }
        m.show(versionBtn, 0, versionBtn.getHeight());
    }

    static final class ServerCard extends JComponent {
        volatile Servers.Status st; String name = "XuroSMP", host = "xurosmp.daronlo.com"; final Image icon = Mc.crystal(5);
        ServerCard() { setPreferredSize(new Dimension(400, 104)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 104)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Mc.aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Color.BLACK); g.fillRect(0, 0, w, h);
            g.setColor(new Color(0xa0a0a0)); g.drawRect(1, 1, w - 3, h - 3); g.drawRect(2, 2, w - 5, h - 5);
            g.drawImage(icon, 18, (h - icon.getHeight(null)) / 2, null);
            int x = 18 + icon.getWidth(null) + 18;
            g.setFont(Mc.font(20, true)); g.setColor(Color.WHITE); g.drawString(name, x, 34);
            g.setFont(Mc.font(13, false));
            Servers.Status s = st;
            String line2 = s == null ? "Status wird geladen …" : s.online ? (s.motd.isEmpty() ? host : s.motd) : "Server ist gerade nicht erreichbar";
            g.setColor(new Color(0xaaaaaa)); g.drawString(clip(g, line2, w - x - 150), x, 58);
            g.setColor(s != null && s.online ? Mc.GREEN_TXT : s == null ? new Color(0x888888) : Mc.RED_TXT);
            g.fillRect(x, 74, 10, 10);
            g.setColor(new Color(0xcccccc));
            String t = s == null ? "…" : s.online ? "Online · Version " + s.version + " · " + s.pingMs + " ms" : "Offline";
            g.drawString(clip(g, t, w - x - 40), x + 18, 84);
            if (s != null && s.online) {
                String pl = s.players + " / " + s.max + " Spieler";
                g.setFont(Mc.font(15, true)); g.setColor(Color.WHITE);
                g.drawString(pl, w - 18 - g.getFontMetrics().stringWidth(pl), 34);
            }
            g.setFont(Mc.font(13, true)); g.setColor(Mc.GOLD);
            g.drawString(host, w - 18 - g.getFontMetrics().stringWidth(host), 58);
            g.dispose();
        }
        static String clip(Graphics2D g, String s, int max) {
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(s) <= max) return s;
            while (s.length() > 3 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }
    }

    void refreshNews() {
        newsBox.removeAll();
        for (Remote.News n : remote.news) {
            JLabel t = Widgets.lbl(n.title + (n.date.isEmpty() ? "" : "  (" + n.date + ")"), 14, true, Mc.INK); t.setAlignmentX(0f);
            JLabel x = new JLabel("<html><body style='width:560px'>" + esc(n.text) + "</body></html>");
            x.setFont(Mc.sans(13.5f, false)); x.setForeground(Mc.INK2); x.setAlignmentX(0f);
            newsBox.add(t); newsBox.add(x); newsBox.add(Box.createVerticalStrut(10));
        }
        newsBox.revalidate(); newsBox.repaint();
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ---------------------------------------------------------------- Module
    JComponent modsPage() {
        Pn p = new Pn(new BorderLayout(0, 10)); p.setBorder(new EmptyBorder(20, 24, 20, 24));
        JPanel head = new JPanel(); head.setOpaque(false); head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel t = Widgets.lbl("Module", 20, true, Mc.INK); t.setAlignmentX(0f); head.add(t);
        JLabel s = Widgets.sans("Nur Mods, die auf XuroSMP erlaubt sind. Änderungen gelten beim nächsten Spielstart.", 13, Mc.INK2);
        s.setAlignmentX(0f); head.add(s); head.add(Box.createVerticalStrut(8));
        p.add(head, BorderLayout.NORTH);
        modsBox.setOpaque(false); modsBox.setLayout(new BoxLayout(modsBox, BoxLayout.Y_AXIS));
        JScrollPane sp = new JScrollPane(modsBox); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createLineBorder(new Color(0x555555), 2)); sp.getVerticalScrollBar().setUnitIncrement(18);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    void refreshMods() {
        modsBox.removeAll();
        for (Remote.Mod m : remote.mods) {
            boolean on = m.locked || cfg.modules.getOrDefault(m.slug, m.def);
            modsBox.add(new Chk(m.name, m.desc, on, m.locked, v -> { cfg.modules.put(m.slug, v); cfg.save(); }));
        }
        modsBox.revalidate(); modsBox.repaint();
    }

    // ---------------------------------------------------------------- Konto
    JComponent accountPage() {
        Pn p = new Pn(new BorderLayout(0, 12)); p.setBorder(new EmptyBorder(20, 24, 20, 24));
        JPanel col = new JPanel(); col.setOpaque(false); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel t = Widgets.lbl("Konto", 20, true, Mc.INK); t.setAlignmentX(0f); col.add(t); col.add(Box.createVerticalStrut(10));
        accBox.setOpaque(false); accBox.setLayout(new BoxLayout(accBox, BoxLayout.Y_AXIS)); accBox.setAlignmentX(0f);
        col.add(accBox); col.add(Box.createVerticalStrut(12));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); btns.setOpaque(false); btns.setAlignmentX(0f);
        btns.add(new Btn("Mit Microsoft anmelden", Kind.GREEN, this::msLogin).size(14));
        btns.add(Box.createHorizontalStrut(10));
        btns.add(new Btn("Offline-Name hinzufügen", Kind.GREY, this::offlineLogin).size(14));
        col.add(btns); col.add(Box.createVerticalStrut(10));
        authBox.setOpaque(false); authBox.setLayout(new BoxLayout(authBox, BoxLayout.Y_AXIS)); authBox.setAlignmentX(0f);
        col.add(authBox); col.add(Box.createVerticalStrut(14));
        JLabel n = new JLabel("<html><body style='width:640px'>Offline-Konten funktionieren nur auf Servern ohne Mojang-Anmeldung. "
                + "XuroSMP braucht in der Regel ein Microsoft-Konto mit Minecraft Java.</body></html>");
        n.setFont(Mc.sans(13, false)); n.setForeground(Mc.INK2); n.setAlignmentX(0f); col.add(n); col.add(Box.createVerticalStrut(16));
        JLabel cid = Widgets.lbl("Microsoft-Client-ID (einmalig, siehe README)", 13, true, Mc.INK); cid.setAlignmentX(0f); col.add(cid);
        col.add(Box.createVerticalStrut(4));
        JTextField fld = Widgets.field(cfg.msClientId, 36); fld.setAlignmentX(0f);
        fld.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.msClientId = fld.getText().trim(); cfg.save(); }));
        col.add(fld);
        p.add(col, BorderLayout.NORTH);
        return p;
    }

    void refreshAccounts() {
        accBox.removeAll();
        if (cfg.accounts.isEmpty()) {
            JLabel e = Widgets.sans("Noch kein Konto. Melde dich an, um zu spielen.", 14, Mc.INK2); e.setAlignmentX(0f); accBox.add(e);
        }
        for (int i = 0; i < cfg.accounts.size(); i++) {
            final int idx = i; Config.Account a = cfg.accounts.get(i);
            JPanel r = new JPanel(new BorderLayout(10, 0)); r.setOpaque(false); r.setAlignmentX(0f);
            r.setBorder(new EmptyBorder(3, 0, 3, 0)); r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            boolean act = idx == cfg.active;
            JLabel l = Widgets.lbl((act ? "▶ " : "   ") + a.name + (a.type.equals("microsoft") ? "   Microsoft" : "   Offline"), 15, act, Mc.INK);
            JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); b.setOpaque(false);
            if (!act) b.add(new Btn("Nutzen", Kind.GREY, () -> { cfg.active = idx; cfg.save(); refreshAccounts(); }).size(12));
            b.add(new Btn("Entfernen", Kind.GREY, () -> {
                cfg.accounts.remove(idx); cfg.active = 0; cfg.save(); refreshAccounts(); }).size(12));
            r.add(l, BorderLayout.CENTER); r.add(b, BorderLayout.EAST);
            accBox.add(r);
        }
        Config.Account a = cfg.account();
        accLabel.setText(a == null ? "Nicht angemeldet" : "Spieler: " + a.name);
        accBox.revalidate(); accBox.repaint();
    }

    void offlineLogin() {
        String n = JOptionPane.showInputDialog(f, "Spielername (3–16 Zeichen: Buchstaben, Zahlen, _):", "Offline-Konto", JOptionPane.PLAIN_MESSAGE);
        if (n == null) return;
        n = n.trim();
        if (!Auth.validName(n)) { JOptionPane.showMessageDialog(f, "Ungültiger Name.", "Offline-Konto", JOptionPane.WARNING_MESSAGE); return; }
        addAccount(Auth.offline(n));
    }

    void addAccount(Config.Account a) {
        cfg.accounts.removeIf(x -> x.type.equals(a.type) && x.name.equalsIgnoreCase(a.name));
        cfg.accounts.add(a); cfg.active = cfg.accounts.size() - 1; cfg.save(); refreshAccounts();
    }

    void msLogin() {
        if (cfg.msClientId.isBlank()) { setHint("Trage zuerst die Microsoft-Client-ID ein (siehe README)."); return; }
        authCancel = false; authBox.removeAll();
        authBox.add(Widgets.sans("Verbinde mit Microsoft …", 14, Mc.INK2));
        authBox.revalidate(); authBox.repaint();
        new Thread(() -> {
            try {
                Auth.DeviceCode d = Auth.start(cfg.msClientId);
                SwingUtilities.invokeLater(() -> {
                    authBox.removeAll();
                    JLabel a = Widgets.sans("Öffne " + d.verificationUri + " und gib diesen Code ein:", 14, Mc.INK); a.setAlignmentX(0f);
                    JLabel c = Widgets.lbl(d.userCode, 30, true, new Color(0x1a3aa6)); c.setAlignmentX(0f);
                    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); row.setOpaque(false); row.setAlignmentX(0f);
                    row.add(new Btn("Seite öffnen", Kind.GREY, () -> open(d.verificationUri)).size(13)); row.add(Box.createHorizontalStrut(8));
                    row.add(new Btn("Code kopieren", Kind.GREY, () -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(d.userCode), null)).size(13));
                    row.add(Box.createHorizontalStrut(8));
                    row.add(new Btn("Abbrechen", Kind.GREY, () -> { authCancel = true; authBox.removeAll(); authBox.repaint(); }).size(13));
                    authBox.add(a); authBox.add(c); authBox.add(Box.createVerticalStrut(6)); authBox.add(row);
                    authBox.revalidate(); authBox.repaint();
                    open(d.verificationUri);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(d.userCode), null);
                });
                Config.Account acc = Auth.finish(cfg.msClientId, d, () -> authCancel);
                SwingUtilities.invokeLater(() -> { authBox.removeAll(); authBox.repaint(); addAccount(acc); setHint("Angemeldet als " + acc.name); });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    authBox.removeAll();
                    if (!authCancel) { JLabel m = Widgets.sans("Anmeldung fehlgeschlagen: " + e.getMessage(), 13, new Color(0xa02014)); authBox.add(m); }
                    authBox.revalidate(); authBox.repaint();
                });
            }
        }, "ms-login").start();
    }

    // ---------------------------------------------------------------- Einstellungen
    JComponent settingsPage() {
        Pn p = new Pn(new BorderLayout()); p.setBorder(new EmptyBorder(20, 24, 20, 24));
        JPanel col = new JPanel(); col.setOpaque(false); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel t = Widgets.lbl("Einstellungen", 20, true, Mc.INK); t.setAlignmentX(0f); col.add(t); col.add(Box.createVerticalStrut(12));

        long phys = 8192;
        try { phys = ((com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() / (1024 * 1024); } catch (Throwable ignored) {}
        int max = (int) Math.max(2048, Math.min(16384, phys * 3 / 4 / 512 * 512));
        col.add(left(Widgets.lbl("Arbeitsspeicher für Minecraft", 14, true, Mc.INK))); col.add(Box.createVerticalStrut(4));
        Slide ram = new Slide(1024, max, 512, Math.min(cfg.ramMb, max), v -> String.format("%.1f GB", v / 1024.0), v -> { cfg.ramMb = v; cfg.save(); });
        ram.setAlignmentX(0f); ram.setMaximumSize(new Dimension(520, 36)); col.add(ram); col.add(Box.createVerticalStrut(14));

        col.add(left(Widgets.lbl("Java-Pfad (leer = automatisch die richtige Version laden)", 14, true, Mc.INK))); col.add(Box.createVerticalStrut(4));
        JTextField java = Widgets.field(cfg.javaPath, 36); java.setAlignmentX(0f); java.setMaximumSize(new Dimension(520, java.getPreferredSize().height));
        java.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.javaPath = java.getText().trim(); cfg.save(); }));
        col.add(java); col.add(Box.createVerticalStrut(14));

        col.add(left(Widgets.lbl("Fenstergröße", 14, true, Mc.INK))); col.add(Box.createVerticalStrut(4));
        JPanel res = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); res.setOpaque(false); res.setAlignmentX(0f);
        JTextField w = Widgets.field(String.valueOf(cfg.width), 5), h = Widgets.field(String.valueOf(cfg.height), 5);
        w.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.width = num(w.getText(), cfg.width); cfg.save(); }));
        h.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.height = num(h.getText(), cfg.height); cfg.save(); }));
        res.add(w); res.add(Widgets.lbl("  x  ", 14, true, Mc.INK)); res.add(h);
        col.add(res); col.add(Box.createVerticalStrut(10));
        col.add(left(new Chk("Vollbild", "", cfg.fullscreen, false, v -> { cfg.fullscreen = v; cfg.save(); })));
        col.add(left(new Chk("Direkt mit XuroSMP verbinden", "Nach dem Start springt Minecraft sofort auf den Server.", cfg.quickConnect, false, v -> { cfg.quickConnect = v; cfg.save(); })));
        col.add(left(new Chk("Launcher nach dem Start schließen", "", cfg.closeOnLaunch, false, v -> { cfg.closeOnLaunch = v; cfg.save(); })));
        col.add(Box.createVerticalStrut(12));
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); b.setOpaque(false); b.setAlignmentX(0f);
        b.add(new Btn("Spielordner öffnen", Kind.GREY, () -> { try { Files.createDirectories(cfg.instance()); Desktop.getDesktop().open(cfg.instance().toFile()); } catch (Exception e) { setHint("Ordner konnte nicht geöffnet werden: " + cfg.instance()); } }).size(14));
        col.add(b);
        p.add(col, BorderLayout.NORTH);
        return p;
    }

    static int num(String s, int def) { try { int v = Integer.parseInt(s.trim()); return v >= 200 && v <= 10000 ? v : def; } catch (Exception e) { return def; } }

    static <T extends JComponent> T left(T c) { c.setAlignmentX(0f); return c; }

    // ---------------------------------------------------------------- Konsole
    JComponent consolePage() {
        Pn p = new Pn(new BorderLayout(0, 10)); p.setBorder(new EmptyBorder(20, 24, 20, 24));
        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false);
        head.add(Widgets.lbl("Konsole", 20, true, Mc.INK), BorderLayout.WEST);
        head.add(new Btn("Log kopieren", Kind.GREY, () -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(console.getText()), null)).size(13), BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);
        console.setEditable(false); console.setBackground(Color.BLACK); console.setForeground(Mc.GREEN_TXT);
        console.setFont(Mc.font(12, false)); console.setLineWrap(true); console.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane sp = new JScrollPane(console); sp.setBorder(BorderFactory.createLineBorder(new Color(0x555555), 2));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    void log(String s) {
        SwingUtilities.invokeLater(() -> {
            console.append(s + "\n");
            if (console.getLineCount() > 4000) try { console.replaceRange("", 0, console.getLineEndOffset(1000)); } catch (Exception ignored) {}
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    // ---------------------------------------------------------------- Hintergrund + Start
    void background() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "status"); t.setDaemon(true); return t; });
        new Thread(() -> {
            Remote r = Remote.load();
            SwingUtilities.invokeLater(() -> { remote = r; card.name = r.serverName; card.host = r.serverHost; refreshMods(); refreshNews(); });
            try {
                List<String> v = Installer.versions();
                if (!v.isEmpty()) SwingUtilities.invokeLater(() -> { versions = v; });
            } catch (Exception ignored) {}
        }, "remote").start();
        ex.scheduleWithFixedDelay(() -> {
            card.st = Servers.ping(remote.serverHost, remote.serverPort);
            card.repaint();
        }, 0, 20, TimeUnit.SECONDS);
    }

    void setHint(String s) { SwingUtilities.invokeLater(() -> hint.setText(s.isEmpty() ? " " : s)); }

    void setBusy(boolean b) {
        busy = b;
        SwingUtilities.invokeLater(() -> { playBtn.setEnabled(!b); playBtn.setText(b ? "LÄUFT …" : "SPIELEN"); });
    }

    void play() {
        if (busy) return;
        Config.Account acc = cfg.account();
        if (acc == null) { tab("Konto"); return; }
        setBusy(true); setHint("");
        Http.Prog prog = new Http.Prog() {
            String last = "";
            public void status(String s) { last = s; SwingUtilities.invokeLater(() -> bar.set(bar.getClientProperty("f") instanceof Double ? (Double) bar.getClientProperty("f") : 0, s)); }
            public void frac(double fr) { bar.putClientProperty("f", fr); SwingUtilities.invokeLater(() -> bar.set(fr, last)); }
        };
        new Thread(() -> {
            try {
                log("=== Start: Minecraft " + cfg.mcVersion + " mit Fabric, Konto " + acc.name + " ===");
                prog.status("Anmeldung prüfen …");
                try { Auth.refreshIfNeeded(cfg, acc); }
                catch (IOException e) { throw new IOException(e.getMessage()); }
                Installer.Plan plan = new Installer(cfg).prepare(cfg.mcVersion, prog);
                for (String w : new Mods(cfg).sync(cfg.mcVersion, remote.mods, prog)) log("Mod-Hinweis: " + w);
                Servers.ensure(cfg.instance(), remote.serverName, remote.serverHost);
                Path java = JavaRuntime.ensure(cfg, plan.javaComponent, plan.javaMajor, prog);
                List<String> cmd = Launcher.command(cfg, plan, acc, java, remote);
                log(Launcher.safe(cmd, acc));
                prog.frac(1); prog.status("Minecraft startet …");
                Process pr = Launcher.start(cfg, cmd);
                setHint("Minecraft läuft.");
                if (cfg.closeOnLaunch) { Thread.sleep(2500); System.exit(0); }
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = r.readLine()) != null) log(line);
                }
                int code = pr.waitFor();
                log("=== Minecraft beendet (Code " + code + ") ===");
                SwingUtilities.invokeLater(() -> bar.set(0, code == 0 ? "Bereit" : "Minecraft wurde mit Fehler beendet (Konsole ansehen)"));
                setHint(code == 0 ? "" : "Minecraft ist abgestürzt oder wurde beendet. Details stehen in der Konsole.");
            } catch (Exception e) {
                log("FEHLER: " + e);
                SwingUtilities.invokeLater(() -> bar.set(0, "Fehler"));
                setHint(e.getMessage() == null ? e.toString() : e.getMessage());
            } finally { setBusy(false); }
        }, "launch").start();
    }

    static void open(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
    }

    /** DocumentListener, der bei jeder Änderung eine Aktion ausführt. */
    static final class SimpleDoc implements javax.swing.event.DocumentListener {
        final Runnable r; SimpleDoc(Runnable r) { this.r = r; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }
}
