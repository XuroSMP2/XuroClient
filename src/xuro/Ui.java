package xuro;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.*;
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
    final CardLayout cards = new CardLayout();
    final JPanel center = new JPanel(cards);
    final Map<String, Nav> navs = new LinkedHashMap<>();
    PlayBtn playBtn;
    final Bar bar = new Bar();
    final JLabel hint = new JLabel(" ");
    final JTextArea console = new JTextArea();
    final ServerTile status = new ServerTile();
    final Profile profile = new Profile();
    final JPanel newsBox = new JPanel(), accBox = new JPanel(), authBox = new JPanel(), versionBox = new JPanel();
    final List<PackPage> packPages = new ArrayList<>();
    final VersionTile versionTile = new VersionTile();
    final JoinTile joinTile = new JoinTile();
    volatile boolean busy, authCancel;
    List<String> versions = new ArrayList<>();
    JLabel statusText;

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
        f.setSize(1280, 760); f.setMinimumSize(new Dimension(1120, 700));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        background();
    }

    /** Baut die gesamte Oberfläche in ein beliebiges JFrame (auch für Tests). */
    void build(JFrame fr) {
        Backdrop root = new Backdrop(new BorderLayout());
        root.add(header(), BorderLayout.NORTH);
        root.add(sidebar(), BorderLayout.WEST);
        center.setOpaque(false);
        center.add(homePage(), "Startseite");
        center.add(versionsPage(), "Versionen");
        center.add(settingsPage(), "Einstellungen");
        center.add(skinsPage(), "Skins");
        PackPage mods = new PackPage(this, true), res = new PackPage(this, false);
        packPages.add(mods); packPages.add(res);
        center.add(wrap(mods), "Mods");
        center.add(wrap(res), "Ressourcenpakete");
        center.add(partnerPage(), "XuroSMP");
        center.add(accountPage(), "Konto");
        center.add(consolePage(), "Konsole");
        JPanel c = new JPanel(new BorderLayout()); c.setOpaque(false); c.setBorder(new EmptyBorder(6, 14, 10, 18)); c.add(center);
        root.add(c, BorderLayout.CENTER);
        root.add(statusBar(), BorderLayout.SOUTH);
        fr.setContentPane(root);
        tab("Startseite");
        refreshAccounts(); refreshNews(); refreshVersions(); refreshPacks();
    }

    static JComponent wrap(JComponent c) { return c; }

    JComponent header() {
        JPanel h = new JPanel(new BorderLayout()); h.setOpaque(false); h.setBorder(new EmptyBorder(14, 22, 4, 22));
        h.add(new Logo(), BorderLayout.WEST);
        Glass pill = new Glass(new FlowLayout(FlowLayout.LEFT, 14, 8), 40, Widgets.EDGE2);
        JLabel hs = new JLabel(); hs.setPreferredSize(new Dimension(34, 30)); hs.setIcon(new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) { Graphics2D g2 = (Graphics2D) g.create(); Widgets.hq(g2); g2.translate(x, y);
                g2.setColor(Widgets.EDGE2); g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Float(2, 18, 12, 9)); g2.draw(new Line2D.Float(30, 18, 20, 9)); g2.draw(new Line2D.Float(12, 9, 20, 9));
                g2.draw(new Line2D.Float(6, 22, 14, 26)); g2.draw(new Line2D.Float(26, 22, 18, 26)); g2.draw(new Line2D.Float(14, 26, 18, 26)); g2.dispose(); }
            public int getIconWidth() { return 34; } public int getIconHeight() { return 30; } });
        pill.add(hs);
        JPanel tx = new JPanel(); tx.setOpaque(false); tx.setLayout(new BoxLayout(tx, BoxLayout.Y_AXIS));
        tx.add(Widgets.sans("Offizielle Partnerschaft mit", 12.5f, Widgets.MUTED)); tx.add(Widgets.lbl("XuroSMP", 20, true, Color.WHITE));
        pill.add(tx);
        JPanel r = new JPanel(new GridBagLayout()); r.setOpaque(false); r.add(pill);
        h.add(r, BorderLayout.EAST);
        return h;
    }

    JComponent sidebar() {
        JPanel s = new Glass(new BorderLayout(0, 8), 26, Widgets.EDGE); s.setPreferredSize(new Dimension(276, 10));
        s.setBorder(new EmptyBorder(14, 12, 12, 12));
        JPanel nav = new JPanel(); nav.setOpaque(false); nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        String[][] items = {{"Startseite", "home", null}, {"Versionen", "cube", null}, {"Einstellungen", "gear", null}, {"Skins", "shirt", null},
                {"Mods", "puzzle", null}, {"Ressourcenpakete", "pack", null}, {"XuroSMP", "star", "Partner"}};
        for (String[] it : items) {
            Nav n = new Nav(it[0], it[1], it[2], () -> tab(it[0]));
            navs.put(it[0], n); nav.add(n); nav.add(Box.createVerticalStrut(4));
        }
        s.add(nav, BorderLayout.NORTH);
        JPanel bottom = new JPanel(); bottom.setOpaque(false); bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        profile.setAlignmentX(0f); bottom.add(profile); bottom.add(Box.createVerticalStrut(8));
        JPanel soc = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); soc.setOpaque(false); soc.setAlignmentX(0f);
        soc.add(new Btn("Discord", Kind.GHOST, () -> open(remote.discord)).size(12));
        soc.add(new Btn("Website", Kind.GHOST, () -> open(remote.website)).size(12));
        bottom.add(soc);
        s.add(bottom, BorderLayout.SOUTH);
        return s;
    }

    JComponent statusBar() {
        JPanel b = new JPanel(new BorderLayout(18, 0)); b.setOpaque(false); b.setBorder(new EmptyBorder(4, 40, 12, 40));
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); l.setOpaque(false);
        statusText = Widgets.sans("Serverstatus (XuroSMP):", 14, Color.WHITE); l.add(statusText); l.add(status);
        b.add(l, BorderLayout.WEST);
        bar.setPreferredSize(new Dimension(300, 24)); JPanel bw = new JPanel(new GridBagLayout()); bw.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints(); gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1; bw.add(bar, gc);
        bar.set(0, "Bereit");
        b.add(bw, BorderLayout.CENTER);
        hint.setFont(Widgets.f(14, false)); hint.setForeground(Widgets.MUTED); hint.setText("Bereit für dein nächstes Abenteuer!");
        hint.setPreferredSize(new Dimension(380, 20)); hint.setHorizontalAlignment(SwingConstants.RIGHT);
        b.add(hint, BorderLayout.EAST);
        return b;
    }

    void tab(String name) {
        cards.show(center, name);
        navs.forEach((k, b) -> b.setSelected(k.equals(name)));
    }

    // ---------------------------------------------------------------- Kleine Komponenten
    /** Logo oben links: gezeichnetes X und Schriftzug. */
    static final class Logo extends JComponent {
        Logo() { setPreferredSize(new Dimension(400, 88)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g);
            Hero.drawX(g, 10, 6, 76);
            g.setFont(Widgets.f(44, true).deriveFont(Font.BOLD | Font.ITALIC)); FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE); g.drawString("Xuro", 98, 52);
            int w = fm.stringWidth("Xuro"); g.setColor(new Color(0x3fa2ff)); g.drawString("Client", 98 + w, 52);
            g.setFont(Widgets.f(12, true)); g.setColor(new Color(0xdbe6ff));
            String t = "P L A Y .   C U S T O M I Z E .   E N J O Y ."; g.drawString(t, 100, 74);
            g.dispose();
        }
    }

    /** Großes Hero in der Mitte der Startseite. */
    static final class Hero extends JComponent {
        static void drawX(Graphics2D g, float x, float y, float s) {
            Path2D a = new Path2D.Float(); a.moveTo(x, y); a.lineTo(x + s * .34f, y); a.lineTo(x + s * .62f, y + s * .42f); a.lineTo(x + s * .98f, y); a.lineTo(x + s * .58f, y + s * .52f);
            a.lineTo(x + s * .98f, y + s * 1f); a.lineTo(x + s * .66f, y + s * 1f); a.lineTo(x + s * .42f, y + s * .62f); a.lineTo(x + s * .02f, y + s * 1f); a.lineTo(x + s * .36f, y + s * .5f); a.closePath();
            g.setPaint(new GradientPaint(x, y, new Color(0xffffff), x + s, y + s, new Color(0x2a7dff))); g.fill(a);
            Widgets.glow(g, a, new Color(0x3bb7ff), 1.4f, 3);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g);
            int w = getWidth(), h = getHeight();
            float sc = Math.min(w / 780f, h / 340f);
            g.translate(w / 2f, 0); g.scale(sc, sc); g.translate(-390, 0);
            drawX(g, 10, 20, 170);
            Font tf = Widgets.f(104, true).deriveFont(Font.BOLD | Font.ITALIC); g.setFont(tf);
            g.setColor(Color.WHITE); g.drawString("Xuro", 190, 150);
            int xw = g.getFontMetrics().stringWidth("Xuro");
            g.setPaint(new GradientPaint(0, 60, new Color(0x3fa8ff), 0, 150, new Color(0x1560e8))); g.drawString("Client", 190 + xw, 150);
            g.setFont(Widgets.f(22, true)); g.setColor(Color.WHITE);
            String t = "P L A Y .   C U S T O M I Z E .   E N J O Y ."; int tw = g.getFontMetrics().stringWidth(t);
            g.drawString(t, 420 - tw / 2f + 40, 192);
            g.setColor(new Color(0x3bb7ff)); g.fillRect(60, 186, 90, 2); g.fillRect(690, 186, 90, 2);
            drawX(g, 215, 236, 66);
            g.setFont(Widgets.f(46, true)); g.setColor(new Color(0x3bb7ff)); g.drawString("×", 320, 290);
            g.setFont(Widgets.f(58, true).deriveFont(Font.BOLD | Font.ITALIC)); g.setColor(Color.WHITE); g.drawString("Xuro", 395, 285);
            g.setColor(new Color(0x3fa8ff)); g.drawString("SMP", 395 + g.getFontMetrics().stringWidth("Xuro"), 285);
            Path2D crown = new Path2D.Float(); crown.moveTo(450, 232); crown.lineTo(458, 214); crown.lineTo(468, 226); crown.lineTo(480, 208); crown.lineTo(492, 226); crown.lineTo(502, 214); crown.lineTo(510, 232); crown.closePath();
            g.setColor(Widgets.GOLD); g.fill(crown);
            g.setFont(Widgets.f(26, false).deriveFont(Font.ITALIC)); g.setColor(Color.WHITE); g.drawString("Gemeinsam. Stärker.", 330, 332);
            g.dispose();
        }
    }

    static final class ServerTile extends JComponent {
        volatile Servers.Status st;
        ServerTile() { setPreferredSize(new Dimension(110, 22)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g);
            Servers.Status s = st; Color c = s == null ? new Color(0x8892b0) : s.online ? Widgets.OK : Widgets.BAD;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60)); g.fillOval(0, 3, 16, 16);
            g.setColor(c); g.fillOval(3, 6, 10, 10);
            g.setFont(Widgets.f(14, true)); g.setColor(c); g.drawString(s == null ? "Prüfe …" : s.online ? "Online" : "Offline", 22, 17);
            g.dispose();
        }
    }

    /** Auswahlfeld-Kachel der Startseite. */
    abstract static class Tile extends Glass {
        Tile() { super(null, 20, Widgets.EDGE); setPreferredSize(new Dimension(200, 80)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); }
    }

    final class VersionTile extends Tile {
        VersionTile() { addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { pickVersion(this_()); } }); }
        JComponent this_() { return this; }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g);
            int h = getHeight();
            g.setColor(new Color(0x5a3a22)); g.fillRoundRect(18, h / 2 - 22, 44, 44, 6, 6);
            g.setColor(new Color(0x4cae3a)); g.fillRoundRect(18, h / 2 - 22, 44, 17, 6, 6);
            g.setColor(new Color(0x6fd04e)); g.fillRect(24, h / 2 - 20, 12, 5);
            Widgets.text(g, versions.isEmpty() || cfg.versionChosen || versions.get(0).equals(cfg.mcVersion) && !cfg.versionChosen ? (cfg.versionChosen ? "Version" : "Neueste Version") : "Version", 76, h / 2 - 6, Widgets.f(13.5f, false), Widgets.MUTED);
            Widgets.text(g, cfg.mcVersion, 76, h / 2 + 18, Widgets.f(22, true), Color.WHITE);
            Widgets.text(g, "▾", getWidth() - 34, h / 2 + 6, Widgets.f(18, true), Color.WHITE);
            g.dispose();
        }
    }

    final class JoinTile extends Tile {
        JoinTile() { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { cfg.quickConnect = !cfg.quickConnect; cfg.save(); repaint(); } }); }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g);
            int w = getWidth(), h = getHeight();
            Widgets.glow(g, new Ellipse2D.Float(18, h / 2f - 20, 40, 40), Widgets.EDGE2, 2f, 1);
            g.setColor(Widgets.EDGE2); g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(new Line2D.Float(38, h / 2f, 48, h / 2f - 10)); g.fill(new Ellipse2D.Float(35, h / 2f - 3, 6, 6));
            Widgets.text(g, "Direkt beitreten", 66, h / 2 - 4, Widgets.f(14.5f, true), Color.WHITE);
            Servers.Status s = status.st;
            Widgets.text(g, "IP: " + remote.serverHost, 66, h / 2 + 18, Widgets.f(11.5f, false), Widgets.MUTED);
            g.setColor(s != null && s.online ? Widgets.OK : s == null ? new Color(0x8892b0) : Widgets.BAD); g.fillOval(70 + g.getFontMetrics(Widgets.f(11.5f, false)).stringWidth("IP: " + remote.serverHost) + 6, h / 2 + 9, 9, 9);
            boolean on = cfg.quickConnect;
            RoundRectangle2D t = new RoundRectangle2D.Float(w - 62, h / 2f - 12, 46, 24, 24, 24);
            g.setColor(on ? new Color(0x1c6df0) : new Color(0x1a2447)); g.fill(t); Widgets.glow(g, t, on ? Widgets.EDGE2 : new Color(0x3a4a80), 1.2f, on ? 2 : 0);
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Float(on ? w - 38 : w - 58, h / 2f - 9, 18, 18));
            g.dispose();
        }
    }

    /** Konto-Karte unten links. */
    final class Profile extends Glass {
        volatile Image head; String loadedFor = "";
        Profile() { super(null, 22, Widgets.EDGE); setPreferredSize(new Dimension(250, 84)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 84)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) {
                JPopupMenu m = new JPopupMenu();
                JMenuItem a = new JMenuItem("Konto verwalten"); a.addActionListener(x -> tab("Konto")); m.add(a);
                JMenuItem c = new JMenuItem("Konsole"); c.addActionListener(x -> tab("Konsole")); m.add(c);
                m.show(Profile.this, 0, -m.getPreferredSize().height); } }); }
        void load(String name) {
            if (name == null || name.equals(loadedFor)) return;
            loadedFor = name;
            new Thread(() -> { try { Image im = javax.imageio.ImageIO.read(new URI("https://mc-heads.net/avatar/" + name + "/64").toURL()); if (im != null) { head = im; repaint(); } } catch (Exception ignored) {} }, "avatar").start();
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g);
            int h = getHeight();
            Config.Account a = cfg.account();
            Shape clip = new Ellipse2D.Float(14, h / 2f - 26, 52, 52); g.setClip(clip);
            if (head != null) g.drawImage(head, 14, h / 2 - 26, 52, 52, null);
            else { g.setColor(new Color(0xb98563)); g.fillRect(14, h / 2 - 26, 52, 52); g.setColor(new Color(0x4a2f1c)); g.fillRect(14, h / 2 - 26, 52, 16); g.setColor(new Color(0x2a4aa0)); g.fillRect(24, h / 2 - 4, 8, 8); g.fillRect(48, h / 2 - 4, 8, 8); }
            g.setClip(null); Widgets.glow(g, clip, Widgets.EDGE2, 1.6f, 1);
            Widgets.text(g, a == null ? "Nicht angemeldet" : a.name, 78, h / 2 - 4, Widgets.f(17, true), Color.WHITE);
            g.setColor(a == null ? new Color(0x8892b0) : Widgets.OK); g.fillOval(78, h / 2 + 8, 10, 10);
            Widgets.text(g, a == null ? "Zum Anmelden klicken" : a.type.equals("microsoft") ? "Online" : "Offline-Konto", 94, h / 2 + 18, Widgets.f(13.5f, false), Widgets.MUTED);
            Widgets.text(g, "▾", getWidth() - 30, h / 2 + 6, Widgets.f(16, true), Color.WHITE);
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- Startseite
    JComponent homePage() {
        JPanel p = new JPanel(new BorderLayout(16, 0)); p.setOpaque(false);
        JPanel mid = new JPanel(new BorderLayout(0, 12)); mid.setOpaque(false);
        mid.add(new Hero(), BorderLayout.CENTER);
        JPanel south = new JPanel(); south.setOpaque(false); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        JPanel tiles = new JPanel(new GridLayout(1, 3, 14, 0)); tiles.setOpaque(false); tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        tiles.add(versionTile);
        Tile folder = new Tile() {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0); Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g); int h = getHeight();
                Widgets.icon(g, "folder", 18, h / 2f - 20, 40, new Color(0xe9f1ff));
                Widgets.text(g, "Spielordner", 74, h / 2 - 4, Widgets.f(15, false), Color.WHITE);
                Widgets.text(g, Widgets.clip(g.getFontMetrics(Widgets.f(13.5f, false)), "…/" + cfg.instance().getFileName(), getWidth() - 130), 74, h / 2 + 18, Widgets.f(13.5f, false), Widgets.MUTED);
                Widgets.text(g, "↗", getWidth() - 36, h / 2 + 7, Widgets.f(20, true), Color.WHITE); g.dispose();
            }
        };
        folder.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { openFolder(); } });
        tiles.add(folder); tiles.add(joinTile);
        south.add(tiles); south.add(Box.createVerticalStrut(16));
        playBtn = new PlayBtn(this::play); playBtn.setAlignmentX(0.5f); playBtn.setMaximumSize(new Dimension(560, 96));
        south.add(playBtn);
        mid.add(south, BorderLayout.SOUTH);
        p.add(mid, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridBagLayout()); right.setOpaque(false); right.setPreferredSize(new Dimension(300, 10));
        GridBagConstraints gc = new GridBagConstraints(); gc.gridx = 0; gc.fill = GridBagConstraints.BOTH; gc.weightx = 1;
        Glass news = new Glass(new BorderLayout(0, 8), 24, Widgets.EDGE); news.setBorder(new EmptyBorder(14, 16, 14, 16));
        JPanel nh = new JPanel(new BorderLayout()); nh.setOpaque(false);
        nh.add(Widgets.lbl("Neuigkeiten", 19, true, Color.WHITE), BorderLayout.WEST);
        news.add(nh, BorderLayout.NORTH);
        newsBox.setOpaque(false); newsBox.setLayout(new BoxLayout(newsBox, BoxLayout.Y_AXIS));
        news.add(Widgets.scroll(newsBox), BorderLayout.CENTER);
        gc.gridy = 0; gc.weighty = 1; right.add(news, gc);
        Glass hl = new Glass(new BorderLayout(0, 6), 24, Widgets.EDGE) {
            @Override protected void paintChildren(Graphics g) { super.paintChildren(g); }
        };
        hl.setBorder(new EmptyBorder(14, 16, 14, 16));
        hl.add(Widgets.lbl("Highlights", 19, true, Color.WHITE), BorderLayout.NORTH);
        JPanel list = new JPanel(); list.setOpaque(false); list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (String s : new String[]{"Perfekte Performance", "Exklusive XuroSMP-Features", "Regelmäßige Updates", "Eigene Modpacks mit Modrinth"}) list.add(highlight(s));
        hl.add(list, BorderLayout.CENTER);
        hl.setPreferredSize(new Dimension(300, 215)); hl.setMinimumSize(new Dimension(300, 215));
        gc.gridy = 1; gc.weighty = 0; gc.insets = new Insets(14, 0, 0, 0); right.add(hl, gc);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    JComponent highlight(String t) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g); int h = getHeight();
                g.setColor(new Color(0x1c6df0)); g.fillOval(0, h / 2 - 11, 22, 22);
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Float(6, h / 2f, 10, h / 2f + 4)); g.draw(new Line2D.Float(10, h / 2f + 4, 16, h / 2f - 4));
                Widgets.text(g, t, 34, h / 2 + 5, Widgets.f(15, false), Color.WHITE); g.dispose();
            }
        };
        c.setPreferredSize(new Dimension(200, 34)); c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34)); return c;
    }

    void refreshNews() {
        newsBox.removeAll();
        for (Remote.News n : remote.news) {
            JLabel t = Widgets.lbl(n.title, 16, true, Color.WHITE); t.setAlignmentX(0f);
            JLabel x = new JLabel("<html><body style='width:250px'>" + esc(n.text) + "</body></html>");
            x.setFont(Widgets.f(13.5f, false)); x.setForeground(Widgets.MUTED); x.setAlignmentX(0f);
            newsBox.add(t); newsBox.add(Box.createVerticalStrut(4)); newsBox.add(x);
            if (!n.date.isEmpty()) { JLabel d = Widgets.lbl(n.date, 12.5f, true, Widgets.EDGE2); d.setAlignmentX(0f); newsBox.add(Box.createVerticalStrut(6)); newsBox.add(d); }
            newsBox.add(Box.createVerticalStrut(16));
        }
        newsBox.revalidate(); newsBox.repaint();
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    void openFolder() {
        try { Files.createDirectories(cfg.instance()); Desktop.getDesktop().open(cfg.instance().toFile()); }
        catch (Exception e) { setHint("Ordner konnte nicht geöffnet werden: " + cfg.instance()); }
    }

    // ---------------------------------------------------------------- Packs
    Packs.Pack activePack() { return remote.find(cfg.activePack, cfg); }

    Set<String> usedIds() { Set<String> s = new HashSet<>(); for (Packs.Pack p : remote.all(cfg)) s.add(p.id); return s; }

    void setActivePack(String id) { cfg.activePack = id; cfg.save(); refreshPacks(); }

    void refreshPacks() {
        if (!remote.all(cfg).contains(activePack())) cfg.activePack = "xurosmp";
        for (PackPage p : packPages) p.refresh();
        if (playBtn != null && !busy) playBtn.set("Spielen", playSub());
        if (joinTile != null) joinTile.repaint();
    }

    // ---------------------------------------------------------------- Allgemeine Seiten-Bausteine
    Glass page(String title, String subtitle, JComponent body) {
        Glass g = new Glass(new BorderLayout(0, 14)); g.setBorder(new EmptyBorder(22, 26, 22, 26));
        JPanel h = new JPanel(); h.setOpaque(false); h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        JLabel t = Widgets.lbl(title, 24, true, Color.WHITE); t.setAlignmentX(0f); h.add(t);
        if (subtitle != null) { JLabel s = Widgets.sans(subtitle, 13.5f, Widgets.MUTED); s.setAlignmentX(0f); h.add(Box.createVerticalStrut(3)); h.add(s); }
        g.add(h, BorderLayout.NORTH); g.add(body, BorderLayout.CENTER);
        return g;
    }

    // ---------------------------------------------------------------- Versionen
    JComponent versionsPage() {
        versionBox.setOpaque(false); versionBox.setLayout(new BoxLayout(versionBox, BoxLayout.Y_AXIS));
        return page("Versionen", "Minecraft mit Fabric, ab " + Installer.MIN_VERSION + ". Die neueste Version ist vorgewählt.", Widgets.scroll(versionBox));
    }

    void refreshVersions() {
        versionBox.removeAll();
        for (String v : versions) {
            boolean sel = v.equals(cfg.mcVersion);
            JPanel row = new JPanel(new GridLayout(1, 1)); row.setOpaque(false); row.setMaximumSize(new Dimension(420, 50)); row.setBorder(new EmptyBorder(0, 0, 8, 0)); row.setAlignmentX(0f);
            row.add(new Btn("Minecraft " + v + (sel ? "   ✓ ausgewählt" : ""), sel ? Kind.PRIMARY : Kind.GHOST, () -> { cfg.mcVersion = v; cfg.versionChosen = true; cfg.save(); refreshVersions(); versionTile.repaint(); }).size(15));
            versionBox.add(row);
        }
        versionBox.revalidate(); versionBox.repaint(); versionTile.repaint();
    }

    void pickVersion(JComponent anchor) {
        JPopupMenu m = new JPopupMenu();
        for (String v : versions) {
            JMenuItem it = new JMenuItem("Minecraft " + v); it.setFont(Widgets.f(14, true));
            it.addActionListener(e -> { cfg.mcVersion = v; cfg.versionChosen = true; cfg.save(); refreshVersions(); });
            m.add(it);
        }
        m.show(anchor, 0, anchor.getHeight());
    }

    // ---------------------------------------------------------------- Skins
    volatile Image skinImg; String skinFor = "";

    JComponent skinsPage() {
        JPanel body = new JPanel(new BorderLayout(24, 0)); body.setOpaque(false);
        JComponent view = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); Widgets.hq(g);
                Config.Account a = cfg.account();
                if (a != null && !a.name.equals(skinFor)) { skinFor = a.name; loadSkin(a.name); }
                Image im = skinImg; int w = getWidth(), h = getHeight();
                if (im != null) { int hh = Math.min(h, 420), ww = hh * im.getWidth(null) / Math.max(1, im.getHeight(null)); g.drawImage(im, (w - ww) / 2, (h - hh) / 2, ww, hh, null); }
                else { String s = a == null ? "Melde dich an, um deinen Skin zu sehen." : "Skin wird geladen …"; Widgets.text(g, s, 10, 30, Widgets.f(14, false), Widgets.MUTED); }
                g.dispose();
            }
        };
        body.add(view, BorderLayout.CENTER);
        JPanel r = new JPanel(); r.setOpaque(false); r.setLayout(new BoxLayout(r, BoxLayout.Y_AXIS)); r.setPreferredSize(new Dimension(320, 10));
        JLabel t = new JLabel("<html><body style='width:290px'>Skins werden bei Mojang verwaltet und gelten überall, auch auf XuroSMP. Ändere deinen Skin auf minecraft.net, beim nächsten Start ist er da.</body></html>");
        t.setFont(Widgets.f(14, false)); t.setForeground(Widgets.MUTED); t.setAlignmentX(0f); r.add(t); r.add(Box.createVerticalStrut(18));
        Btn a = new Btn("Skin bei Mojang ändern", Kind.PRIMARY, () -> open("https://www.minecraft.net/profile/skin")).size(14); a.setAlignmentX(0f); r.add(a); r.add(Box.createVerticalStrut(10));
        Btn b = new Btn("Skins auf NameMC suchen", Kind.GHOST, () -> open("https://namemc.com/minecraft-skins")).size(14); b.setAlignmentX(0f); r.add(b); r.add(Box.createVerticalStrut(10));
        Btn c = new Btn("Vorschau neu laden", Kind.GHOST, () -> { skinFor = ""; skinImg = null; center.repaint(); }).size(14); c.setAlignmentX(0f); r.add(c);
        body.add(r, BorderLayout.EAST);
        return page("Skins", "Dein aktueller Skin", body);
    }

    void loadSkin(String name) {
        new Thread(() -> { try { Image im = javax.imageio.ImageIO.read(new URI("https://mc-heads.net/body/" + name + "/right").toURL()); skinImg = im; } catch (Exception ignored) {}
            SwingUtilities.invokeLater(center::repaint); }, "skin").start();
    }

    // ---------------------------------------------------------------- Partner
    JComponent partnerPage() {
        JPanel col = new JPanel(); col.setOpaque(false); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel t = new JLabel("<html><body style='width:640px'>XuroClient ist der offizielle Launcher von XuroSMP. Er installiert Minecraft mit Fabric, lädt das passende Modpack mit Performance-Mods, Simple Voice Chat, Low Fire und Low Shield, trägt den Server in deine Serverliste ein und verbindet dich auf Wunsch direkt.</body></html>");
        t.setFont(Widgets.f(15, false)); t.setForeground(Color.WHITE); t.setAlignmentX(0f); col.add(t); col.add(Box.createVerticalStrut(18));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); row.setOpaque(false); row.setAlignmentX(0f);
        row.add(new Btn("Jetzt spielen", Kind.PRIMARY, () -> { tab("Startseite"); play(); }).size(15)); row.add(Box.createHorizontalStrut(10));
        row.add(new Btn("Discord", Kind.GHOST, () -> open(remote.discord)).size(15)); row.add(Box.createHorizontalStrut(10));
        row.add(new Btn("Website", Kind.GHOST, () -> open(remote.website)).size(15));
        col.add(row); col.add(Box.createVerticalStrut(24));
        JLabel ip = Widgets.lbl("Server-IP:  " + remote.serverHost, 16, true, Widgets.EDGE2); ip.setAlignmentX(0f); col.add(ip); col.add(Box.createVerticalStrut(14));
        for (String s : new String[]{"Perfekte Performance durch Sodium, Lithium und FerriteCore", "Proximity-Voice-Chat mit Simple Voice Chat", "Weniger Bildschirmstörung durch Low Fire und Low Shield", "Eigene Modpacks erstellen und über Modrinth erweitern"}) { JComponent h = highlight(s); h.setAlignmentX(0f); col.add(h); }
        return page("XuroSMP", "Offizielle Partnerschaft", col);
    }

    // ---------------------------------------------------------------- Konto
    JComponent accountPage() {
        JPanel col = new JPanel(); col.setOpaque(false); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        accBox.setOpaque(false); accBox.setLayout(new BoxLayout(accBox, BoxLayout.Y_AXIS)); accBox.setAlignmentX(0f);
        col.add(accBox); col.add(Box.createVerticalStrut(12));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); btns.setOpaque(false); btns.setAlignmentX(0f);
        btns.add(new Btn("Mit Microsoft anmelden", Kind.PRIMARY, this::msLogin).size(14)); btns.add(Box.createHorizontalStrut(10));
        btns.add(new Btn("Offline-Name hinzufügen", Kind.GHOST, this::offlineLogin).size(14));
        col.add(btns); col.add(Box.createVerticalStrut(10));
        authBox.setOpaque(false); authBox.setLayout(new BoxLayout(authBox, BoxLayout.Y_AXIS)); authBox.setAlignmentX(0f);
        col.add(authBox); col.add(Box.createVerticalStrut(14));
        JLabel n = new JLabel("<html><body style='width:640px'>Offline-Konten funktionieren nur auf Servern ohne Mojang-Anmeldung. XuroSMP braucht in der Regel ein Microsoft-Konto mit Minecraft Java.</body></html>");
        n.setFont(Widgets.f(13, false)); n.setForeground(Widgets.MUTED); n.setAlignmentX(0f); col.add(n); col.add(Box.createVerticalStrut(16));
        JLabel cid = Widgets.lbl("Microsoft-Client-ID (einmalig, siehe README)", 13, true, Color.WHITE); cid.setAlignmentX(0f); col.add(cid); col.add(Box.createVerticalStrut(4));
        JTextField fld = Widgets.field(cfg.msClientId, 36); fld.setAlignmentX(0f); fld.setMaximumSize(new Dimension(520, fld.getPreferredSize().height));
        fld.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.msClientId = fld.getText().trim(); cfg.save(); }));
        col.add(fld);
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); top.add(col, BorderLayout.NORTH);
        return page("Konto", "Spielerkonten verwalten", top);
    }

    void refreshAccounts() {
        accBox.removeAll();
        if (cfg.accounts.isEmpty()) { JLabel e = Widgets.sans("Noch kein Konto. Melde dich an, um zu spielen.", 14, Widgets.MUTED); e.setAlignmentX(0f); accBox.add(e); }
        for (int i = 0; i < cfg.accounts.size(); i++) {
            final int idx = i; Config.Account a = cfg.accounts.get(i);
            JPanel r = new JPanel(new BorderLayout(10, 0)); r.setOpaque(false); r.setAlignmentX(0f);
            r.setBorder(new EmptyBorder(3, 0, 3, 0)); r.setMaximumSize(new Dimension(620, 50));
            boolean act = idx == cfg.active;
            JLabel l = Widgets.lbl((act ? "▶ " : "   ") + a.name + (a.type.equals("microsoft") ? "   Microsoft" : "   Offline"), 15, act, Color.WHITE);
            JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); b.setOpaque(false);
            if (!act) b.add(new Btn("Nutzen", Kind.GHOST, () -> { cfg.active = idx; cfg.save(); refreshAccounts(); }).size(12));
            b.add(new Btn("Entfernen", Kind.DANGER, () -> { cfg.accounts.remove(idx); cfg.active = 0; cfg.save(); refreshAccounts(); }).size(12));
            r.add(l, BorderLayout.CENTER); r.add(b, BorderLayout.EAST);
            accBox.add(r);
        }
        Config.Account a = cfg.account();
        profile.load(a == null ? null : a.name); profile.repaint();
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
        authBox.add(Widgets.sans("Verbinde mit Microsoft …", 14, Widgets.MUTED));
        authBox.revalidate(); authBox.repaint();
        new Thread(() -> {
            try {
                Auth.DeviceCode d = Auth.start(cfg.msClientId);
                SwingUtilities.invokeLater(() -> {
                    authBox.removeAll();
                    JLabel a = Widgets.sans("Öffne " + d.verificationUri + " und gib diesen Code ein:", 14, Color.WHITE); a.setAlignmentX(0f);
                    JLabel c = Widgets.lbl(d.userCode, 30, true, Widgets.EDGE2); c.setAlignmentX(0f);
                    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); row.setOpaque(false); row.setAlignmentX(0f);
                    row.add(new Btn("Seite öffnen", Kind.GHOST, () -> open(d.verificationUri)).size(13)); row.add(Box.createHorizontalStrut(8));
                    row.add(new Btn("Code kopieren", Kind.GHOST, () -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(d.userCode), null)).size(13)); row.add(Box.createHorizontalStrut(8));
                    row.add(new Btn("Abbrechen", Kind.DANGER, () -> { authCancel = true; authBox.removeAll(); authBox.repaint(); }).size(13));
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
                    if (!authCancel) authBox.add(Widgets.sans("Anmeldung fehlgeschlagen: " + e.getMessage(), 13, Widgets.BAD));
                    authBox.revalidate(); authBox.repaint();
                });
            }
        }, "ms-login").start();
    }

    // ---------------------------------------------------------------- Einstellungen
    JComponent settingsPage() {
        JPanel col = new JPanel(); col.setOpaque(false); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        long phys = 8192;
        try { phys = ((com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() / (1024 * 1024); } catch (Throwable ignored) {}
        int max = (int) Math.max(2048, Math.min(16384, phys * 3 / 4 / 512 * 512));
        col.add(left(Widgets.lbl("Arbeitsspeicher für Minecraft", 14, true, Color.WHITE))); col.add(Box.createVerticalStrut(4));
        Slide ram = new Slide(1024, max, 512, Math.min(cfg.ramMb, max), v -> String.format("%.1f GB", v / 1024.0), v -> { cfg.ramMb = v; cfg.save(); });
        ram.setAlignmentX(0f); ram.setMaximumSize(new Dimension(520, 38)); col.add(ram); col.add(Box.createVerticalStrut(14));
        col.add(left(Widgets.lbl("Java-Pfad (leer = automatisch die richtige Version laden)", 14, true, Color.WHITE))); col.add(Box.createVerticalStrut(4));
        JTextField java = Widgets.field(cfg.javaPath, 36); java.setAlignmentX(0f); java.setMaximumSize(new Dimension(520, java.getPreferredSize().height));
        java.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.javaPath = java.getText().trim(); cfg.save(); }));
        col.add(java); col.add(Box.createVerticalStrut(14));
        col.add(left(Widgets.lbl("Fenstergröße", 14, true, Color.WHITE))); col.add(Box.createVerticalStrut(4));
        JPanel res = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); res.setOpaque(false); res.setAlignmentX(0f);
        JTextField w = Widgets.field(String.valueOf(cfg.width), 5), h = Widgets.field(String.valueOf(cfg.height), 5);
        w.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.width = num(w.getText(), cfg.width); cfg.save(); }));
        h.getDocument().addDocumentListener(new SimpleDoc(() -> { cfg.height = num(h.getText(), cfg.height); cfg.save(); }));
        res.add(w); res.add(Widgets.lbl("  x  ", 14, true, Color.WHITE)); res.add(h);
        col.add(res); col.add(Box.createVerticalStrut(10));
        JPanel chk = new JPanel(); chk.setOpaque(false); chk.setLayout(new BoxLayout(chk, BoxLayout.Y_AXIS)); chk.setAlignmentX(0f); chk.setMaximumSize(new Dimension(620, 400));
        chk.add(new Chk("Vollbild", "", cfg.fullscreen, false, v -> { cfg.fullscreen = v; cfg.save(); }));
        chk.add(new Chk("Direkt mit XuroSMP verbinden", "Nach dem Start springt Minecraft sofort auf den Server.", cfg.quickConnect, false, v -> { cfg.quickConnect = v; cfg.save(); joinTile.repaint(); }));
        chk.add(new Chk("Launcher nach dem Start schließen", "", cfg.closeOnLaunch, false, v -> { cfg.closeOnLaunch = v; cfg.save(); }));
        col.add(chk); col.add(Box.createVerticalStrut(12));
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); b.setOpaque(false); b.setAlignmentX(0f);
        b.add(new Btn("Spielordner öffnen", Kind.GHOST, this::openFolder).size(14).icon("folder")); b.add(Box.createHorizontalStrut(10));
        b.add(new Btn("Konsole", Kind.GHOST, () -> tab("Konsole")).size(14).icon("term")); b.add(Box.createHorizontalStrut(10));
        b.add(new Btn("Konto", Kind.GHOST, () -> tab("Konto")).size(14));
        col.add(b);
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); top.add(col, BorderLayout.NORTH);
        return page("Einstellungen", "Alles speichert sich automatisch.", Widgets.scroll(top));
    }

    static int num(String s, int def) { try { int v = Integer.parseInt(s.trim()); return v >= 200 && v <= 10000 ? v : def; } catch (Exception e) { return def; } }

    static <T extends JComponent> T left(T c) { c.setAlignmentX(0f); return c; }

    // ---------------------------------------------------------------- Konsole
    JComponent consolePage() {
        console.setEditable(false); console.setBackground(new Color(3, 8, 24)); console.setForeground(new Color(0x7fe3ff));
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); console.setLineWrap(true); console.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane sp = Widgets.scroll(console); sp.setOpaque(true); sp.getViewport().setOpaque(true); sp.getViewport().setBackground(new Color(3, 8, 24));
        Glass g = page("Konsole", "Log von Installation und Spiel", sp);
        JPanel head = (JPanel) ((BorderLayout) g.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        Btn copy = new Btn("Log kopieren", Kind.GHOST, () -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(console.getText()), null)).size(13);
        JPanel north = new JPanel(new BorderLayout()); north.setOpaque(false); g.remove(head); north.add(head, BorderLayout.CENTER); north.add(copy, BorderLayout.EAST); g.add(north, BorderLayout.NORTH);
        return g;
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
            SwingUtilities.invokeLater(() -> { remote = r; refreshNews(); refreshPacks(); });
            try {
                List<String> v = Installer.versions();
                if (!v.isEmpty()) SwingUtilities.invokeLater(() -> {
                    versions = v;
                    if (!cfg.versionChosen) { cfg.mcVersion = v.get(0); cfg.save(); }
                    refreshVersions();
                });
            } catch (Exception ignored) {}
        }, "remote").start();
        ex.scheduleWithFixedDelay(() -> {
            status.st = Servers.ping(remote.serverHost, remote.serverPort);
            SwingUtilities.invokeLater(() -> { status.repaint(); joinTile.repaint(); });
        }, 0, 20, TimeUnit.SECONDS);
    }

    String playSub() { return activePack().id.equals("xurosmp") ? "Auf XuroSMP verbinden!" : "Modpack: " + activePack().name; }

    void setHint(String s) { SwingUtilities.invokeLater(() -> hint.setText(s.isEmpty() ? " " : s)); }

    void setBusy(boolean b) {
        busy = b;
        SwingUtilities.invokeLater(() -> { playBtn.setEnabled(!b); if (b) playBtn.set("Läuft …", "Bitte warten"); else playBtn.set("Spielen", playSub()); });
    }

    void play() {
        if (busy) return;
        Config.Account acc = cfg.account();
        if (acc == null) { tab("Konto"); return; }
        Packs.Pack ap = activePack();
        setBusy(true); setHint("");
        Http.Prog prog = new Http.Prog() {
            String last = "";
            public void status(String s) { last = s; SwingUtilities.invokeLater(() -> bar.set(bar.getClientProperty("f") instanceof Double ? (Double) bar.getClientProperty("f") : 0, s)); }
            public void frac(double fr) { bar.putClientProperty("f", fr); SwingUtilities.invokeLater(() -> bar.set(fr, last)); }
        };
        new Thread(() -> {
            try {
                log("=== Start: Minecraft " + cfg.mcVersion + " mit Fabric, Modpack " + ap.name + ", Konto " + acc.name + " ===");
                prog.status("Anmeldung prüfen …");
                Auth.refreshIfNeeded(cfg, acc);
                Installer.Plan plan = new Installer(cfg).prepare(cfg.mcVersion, prog);
                for (String w : new Mods(cfg).sync(cfg.mcVersion, Packs.effective(ap, cfg), prog)) log("Hinweis: " + w);
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
                setHint(code == 0 ? "Bereit für dein nächstes Abenteuer!" : "Minecraft ist abgestürzt oder wurde beendet. Details stehen in der Konsole.");
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
