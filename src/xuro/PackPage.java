package xuro;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

import xuro.Widgets.*;

/** Seite für Mods (mit Pack-Verwaltung) oder Ressourcenpakete/Shader des aktiven Modpacks. */
final class PackPage extends Glass {
    final Ui ui; final boolean modsMode;
    final JPanel packList = new JPanel(), entries = new JPanel();
    final JLabel title = Widgets.lbl("", 22, true, Color.WHITE), sub = Widgets.sans("", 13, Widgets.MUTED);

    PackPage(Ui ui, boolean modsMode) {
        super(new BorderLayout(18, 0));
        this.ui = ui; this.modsMode = modsMode;
        setBorder(new EmptyBorder(22, 24, 22, 24));
        if (modsMode) {
            JPanel left = new JPanel(new BorderLayout(0, 10)); left.setOpaque(false); left.setPreferredSize(new Dimension(250, 10));
            left.add(Widgets.lbl("Modpacks", 17, true, Color.WHITE), BorderLayout.NORTH);
            packList.setOpaque(false); packList.setLayout(new BoxLayout(packList, BoxLayout.Y_AXIS));
            left.add(Widgets.scroll(packList), BorderLayout.CENTER);
            JPanel b = new JPanel(new GridLayout(0, 2, 6, 6)); b.setOpaque(false);
            b.add(new Btn("Neu", Kind.PRIMARY, this::newPack).size(13));
            b.add(new Btn("Kopie", Kind.GHOST, this::copyPack).size(13));
            b.add(new Btn("Umbenennen", Kind.GHOST, this::rename).size(12));
            b.add(new Btn("Löschen", Kind.DANGER, this::delete).size(13));
            left.add(b, BorderLayout.SOUTH);
            add(left, BorderLayout.WEST);
        }
        JPanel right = new JPanel(new BorderLayout(0, 12)); right.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout(10, 0)); head.setOpaque(false);
        JPanel t = new JPanel(); t.setOpaque(false); t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        title.setAlignmentX(0f); sub.setAlignmentX(0f); t.add(title); t.add(Box.createVerticalStrut(2)); t.add(sub);
        head.add(t, BorderLayout.CENTER);
        head.add(new Btn(modsMode ? "Mod hinzufügen" : "Hinzufügen", Kind.PRIMARY, this::add).size(14), BorderLayout.EAST);
        right.add(head, BorderLayout.NORTH);
        entries.setOpaque(false); entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
        right.add(Widgets.scroll(entries), BorderLayout.CENTER);
        add(right, BorderLayout.CENTER);
        refresh();
    }

    boolean shows(Packs.Entry e) { return modsMode ? e.type.equals("mod") : !e.type.equals("mod"); }

    void refresh() {
        Config cfg = ui.cfg; Packs.Pack ap = ui.activePack();
        if (modsMode) {
            packList.removeAll();
            for (Packs.Pack p : ui.remote.all(cfg)) {
                boolean act = p.id.equals(ap.id);
                JPanel row = new JPanel(new GridLayout(1, 1)); row.setOpaque(false); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46)); row.setBorder(new EmptyBorder(0, 0, 6, 0));
                row.add(new Btn((p.builtin ? "★ " : "") + p.name + (act ? "  ▶" : ""), act ? Kind.PRIMARY : Kind.GHOST, () -> ui.setActivePack(p.id)).size(14));
                packList.add(row);
            }
            packList.revalidate(); packList.repaint();
        }
        title.setText(modsMode ? ap.name : "Ressourcenpakete & Shader");
        sub.setText(modsMode ? (ap.desc.isEmpty() ? "Eigenes Modpack" : ap.desc) : "Aus dem Modpack „" + ap.name + "“. Änderungen gelten beim nächsten Start.");
        entries.removeAll();
        int n = 0;
        for (Packs.Entry e : ap.entries) {
            if (!shows(e)) continue;
            n++;
            JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            String kind = e.type.equals("shader") ? "Shader" : e.type.equals("resourcepack") ? "Ressourcenpaket" : "Mod";
            Chk c = new Chk(e.name + (modsMode ? "" : "  ·  " + kind), e.desc, Packs.isOn(ap, e, cfg), e.locked, v -> { Packs.setOn(ap, e, v, cfg); cfg.save(); });
            row.add(c, BorderLayout.CENTER);
            if (!e.locked) {
                JPanel x = new JPanel(new GridBagLayout()); x.setOpaque(false);
                x.add(new Btn("✕", Kind.GHOST, () -> removeEntry(e)).size(13));
                row.add(x, BorderLayout.EAST);
            }
            entries.add(row);
        }
        if (n == 0) { JLabel l = Widgets.sans(modsMode ? "Dieses Pack hat noch keine Mods." : "Dieses Pack hat keine Ressourcenpakete oder Shader.", 14, Widgets.MUTED); l.setBorder(new EmptyBorder(10, 4, 0, 0)); entries.add(l); }
        entries.revalidate(); entries.repaint();
    }

    /** Eingebaute Packs bleiben unverändert: Änderungen landen in einer eigenen Kopie. */
    Packs.Pack editable() {
        Packs.Pack ap = ui.activePack();
        if (!ap.builtin) return ap;
        Packs.Pack c = ap.copy(Packs.makeId(ap.name + " eigenes", ui.usedIds()), ap.name + " (eigenes)");
        for (Packs.Entry e : c.entries) e.on = Packs.isOn(ap, ap.find(e.slug, e.type), ui.cfg);
        ui.cfg.custom.add(c); ui.cfg.activePack = c.id; ui.cfg.save();
        return c;
    }

    void removeEntry(Packs.Entry e) {
        Packs.Pack p = editable(); Packs.Entry x = p.find(e.slug, e.type);
        if (x != null) p.entries.remove(x);
        ui.cfg.save(); ui.refreshPacks();
    }

    String ask(String msg, String def) {
        String n = (String) JOptionPane.showInputDialog(ui.f, msg, "Modpack", JOptionPane.PLAIN_MESSAGE, null, null, def);
        if (n == null) return null; n = n.trim();
        return n.isEmpty() || n.length() > 40 ? null : n;
    }

    void newPack() {
        String n = ask("Name des neuen Modpacks:", "Mein Pack"); if (n == null) return;
        Packs.Pack p = new Packs.Pack(Packs.makeId(n, ui.usedIds()), n, "", false);
        p.entries.add(new Packs.Entry("fabric-api", "Fabric API", "mod", "Grundlage für fast alle Mods.", true, true));
        ui.cfg.custom.add(p); ui.cfg.activePack = p.id; ui.cfg.save(); ui.refreshPacks();
    }

    void copyPack() {
        Packs.Pack ap = ui.activePack();
        String n = ask("Name der Kopie:", ap.name + " Kopie"); if (n == null) return;
        Packs.Pack c = ap.copy(Packs.makeId(n, ui.usedIds()), n);
        for (Packs.Entry e : c.entries) e.on = Packs.isOn(ap, ap.find(e.slug, e.type), ui.cfg);
        ui.cfg.custom.add(c); ui.cfg.activePack = c.id; ui.cfg.save(); ui.refreshPacks();
    }

    void rename() {
        Packs.Pack ap = ui.activePack();
        if (ap.builtin) { ui.setHint("Eingebaute Packs lassen sich nicht umbenennen. Mit „Kopie“ machst du ein eigenes."); return; }
        String n = ask("Neuer Name:", ap.name); if (n == null) return;
        ap.name = n; ui.cfg.save(); ui.refreshPacks();
    }

    void delete() {
        Packs.Pack ap = ui.activePack();
        if (ap.builtin) { ui.setHint("Eingebaute Packs lassen sich nicht löschen."); return; }
        if (JOptionPane.showConfirmDialog(ui.f, "Pack „" + ap.name + "“ löschen? Die installierten Dateien bleiben im Spielordner.", "Löschen", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        ui.cfg.custom.remove(ap); ui.cfg.activePack = "xurosmp"; ui.cfg.save(); ui.refreshPacks();
    }

    // ------------------------------------------------------------ Modrinth-Suche
    void add() {
        JDialog d = new JDialog(ui.f, "Auf Modrinth suchen", true);
        Backdrop root = new Backdrop(new BorderLayout(0, 10)); root.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel top = new JPanel(new BorderLayout(8, 0)); top.setOpaque(false);
        JTextField q = Widgets.field("", 24); top.add(q, BorderLayout.CENTER);
        JComboBox<String> kind = new JComboBox<>(new String[]{"Ressourcenpakete", "Shader"});
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); east.setOpaque(false);
        if (!modsMode) east.add(kind);
        JPanel res = new JPanel(); res.setOpaque(false); res.setLayout(new BoxLayout(res, BoxLayout.Y_AXIS));
        JLabel st = Widgets.sans("Tippe einen Suchbegriff oder drücke Suchen für beliebte Treffer.", 13, Widgets.MUTED);
        Runnable go = () -> {
            String type = modsMode ? "mod" : kind.getSelectedIndex() == 0 ? "resourcepack" : "shader";
            st.setText("Suche …");
            new Thread(() -> {
                try {
                    List<Modrinth.Hit> hits = Modrinth.search(q.getText(), type, ui.cfg.mcVersion);
                    SwingUtilities.invokeLater(() -> {
                        res.removeAll();
                        st.setText(hits.isEmpty() ? "Nichts gefunden für Minecraft " + ui.cfg.mcVersion + "." : hits.size() + " Treffer für Minecraft " + ui.cfg.mcVersion);
                        for (Modrinth.Hit h : hits) res.add(hitRow(h, type, d));
                        res.revalidate(); res.repaint();
                    });
                } catch (Exception ex) { SwingUtilities.invokeLater(() -> st.setText("Suche fehlgeschlagen: " + ex.getMessage())); }
            }, "modrinth").start();
        };
        east.add(new Btn("Suchen", Kind.PRIMARY, go).size(14));
        top.add(east, BorderLayout.EAST);
        q.addActionListener(e -> go.run());
        root.add(top, BorderLayout.NORTH);
        Glass mid = new Glass(new BorderLayout(0, 6)); mid.setBorder(new EmptyBorder(14, 16, 14, 16));
        mid.add(st, BorderLayout.NORTH); mid.add(Widgets.scroll(res), BorderLayout.CENTER);
        root.add(mid, BorderLayout.CENTER);
        d.setContentPane(root); d.setSize(680, 560); d.setLocationRelativeTo(ui.f); d.setVisible(true);
    }

    JComponent hitRow(Modrinth.Hit h, String type, JDialog d) {
        JPanel r = new JPanel(new BorderLayout(10, 0)); r.setOpaque(false); r.setBorder(new EmptyBorder(6, 0, 6, 0)); r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        JPanel t = new JPanel(); t.setOpaque(false); t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        JLabel a = Widgets.lbl(h.title, 15, true, Color.WHITE), b = Widgets.sans(Widgets.clip(r.getFontMetrics(Widgets.f(12.5f, false)), h.desc, 420), 12.5f, Widgets.MUTED);
        t.add(a); t.add(b); r.add(t, BorderLayout.CENTER);
        boolean have = ui.activePack().find(h.slug, type) != null;
        Btn add = new Btn(have ? "Schon drin" : "Hinzufügen", have ? Kind.GHOST : Kind.PRIMARY, null);
        add = new Btn(have ? "Schon drin" : "Hinzufügen", have ? Kind.GHOST : Kind.PRIMARY, () -> {
            Packs.Pack p = editable();
            if (p.find(h.slug, type) == null) p.entries.add(new Packs.Entry(h.slug, h.title, type, h.desc, true, false));
            ui.cfg.save(); ui.refreshPacks(); d.dispose();
        }).size(13);
        add.setEnabled(!have);
        JPanel x = new JPanel(new GridBagLayout()); x.setOpaque(false); x.add(add); r.add(x, BorderLayout.EAST);
        return r;
    }
}
