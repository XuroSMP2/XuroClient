package xuro;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.function.Consumer;

/** Eigene Bedienelemente im blauen Neon-Look des XuroClient. */
public final class Widgets {
    private Widgets() {}

    // ------------------------------------------------------------ Farben
    public static final Color BG0 = new Color(0x030715), BG1 = new Color(0x071331), PANEL = new Color(8, 18, 50, 225);
    public static final Color EDGE = new Color(0x1f6fff), EDGE2 = new Color(0x3bb7ff), TXT = Color.WHITE, MUTED = new Color(0x9fb6e8);
    public static final Color OK = new Color(0x2ee66b), BAD = new Color(0xff5a6a), GOLD = new Color(0xffc23d);

    public static void hq(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public static Font f(float size, boolean bold) { return Mc.sans(size, bold); }

    /** Leuchtende Kontur: mehrere breite, schwach deckende Linien unter einer scharfen. */
    public static void glow(Graphics2D g, Shape s, Color c, float width, int layers) {
        for (int i = layers; i >= 1; i--) {
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, 26 / i + 6)));
            g.setStroke(new BasicStroke(width + i * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(s);
        }
        g.setColor(c); g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(s);
    }

    public static void text(Graphics2D g, String s, float x, float y, Font font, Color c) {
        g.setFont(font); g.setColor(c); g.drawString(s, x, y);
    }

    public static String clip(FontMetrics fm, String s, int max) {
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 3 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    // ------------------------------------------------------------ Hintergrund
    /** Dunkelblauer Hintergrund mit Lichtstrahl und Block-Silhouette. */
    public static class Backdrop extends JPanel {
        private BufferedImage cache;
        public Backdrop(LayoutManager lm) { super(lm); setOpaque(true); }

        static BufferedImage render(int w, int h) {
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics(); hq(g);
            g.setPaint(new GradientPaint(0, 0, BG0, 0, h, BG1)); g.fillRect(0, 0, w, h);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.5f, h * 0.30f), Math.max(w, h) * 0.55f,
                    new float[]{0f, 1f}, new Color[]{new Color(20, 70, 190, 120), new Color(20, 70, 190, 0)}));
            g.fillRect(0, 0, w, h);
            Random r = new Random(11);
            int cell = Math.max(14, w / 70);
            int cols = w / cell + 1;
            for (int layer = 0; layer < 3; layer++) {
                int base = (int) (h * (0.52 + layer * 0.12));
                for (int cx = 0; cx < cols; cx++) {
                    double mid = 1 - Math.abs(cx - cols / 2.0) / (cols / 2.0);
                    int hgt = (int) ((r.nextInt(5) + 2 + mid * (6 - layer * 2)) * cell);
                    int a = 70 + layer * 30;
                    for (int y = base - hgt; y < h; y += cell) {
                        int sh = r.nextInt(30);
                        g.setColor(new Color(10 + sh / 3, 28 + sh, 80 + sh * 2, a));
                        g.fillRect(cx * cell, y, cell - 1, cell - 1);
                        if (layer == 0 && r.nextInt(40) == 0) { g.setColor(new Color(60, 180, 255, 150)); g.fillRect(cx * cell + 2, y + 2, cell - 5, cell - 5); }
                    }
                }
            }
            g.setPaint(new GradientPaint(w / 2f, 0, new Color(120, 190, 255, 140), w / 2f, h * 0.45f, new Color(120, 190, 255, 0)));
            g.fillRect(w / 2 - 2, 0, 4, (int) (h * 0.45));
            g.setPaint(new GradientPaint(0, 0, new Color(3, 7, 21, 120), 0, h, new Color(3, 7, 21, 40)));
            g.fillRect(0, 0, w, h);
            g.dispose();
            return im;
        }

        @Override protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (cache == null || cache.getWidth() != w || cache.getHeight() != h) cache = render(w, h);
            g.drawImage(cache, 0, 0, null);
        }
    }

    /** Dunkles Glas-Panel mit leuchtender Kontur. */
    public static class Glass extends JPanel {
        private final int arc; private final Color edge;
        public Glass(LayoutManager lm) { this(lm, 22, EDGE); }
        public Glass(LayoutManager lm, int arc, Color edge) { super(lm); this.arc = arc; this.edge = edge; setOpaque(false); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hq(g);
            RoundRectangle2D s = new RoundRectangle2D.Float(2, 2, getWidth() - 5, getHeight() - 5, arc, arc);
            g.setColor(PANEL); g.fill(s);
            glow(g, s, edge, 1.5f, 2);
            g.dispose();
        }
    }

    // ------------------------------------------------------------ Icons
    public static void icon(Graphics2D g, String k, float x, float y, float s, Color c) {
        g.setColor(c); g.setStroke(new BasicStroke(s / 12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Float();
        switch (k) {
            case "home":
                p.moveTo(x + s * .5, y + s * .08); p.lineTo(x + s * .95, y + s * .5); p.lineTo(x + s * .82, y + s * .5); p.lineTo(x + s * .82, y + s * .92);
                p.lineTo(x + s * .6, y + s * .92); p.lineTo(x + s * .6, y + s * .62); p.lineTo(x + s * .4, y + s * .62); p.lineTo(x + s * .4, y + s * .92);
                p.lineTo(x + s * .18, y + s * .92); p.lineTo(x + s * .18, y + s * .5); p.lineTo(x + s * .05, y + s * .5); p.closePath();
                g.fill(p); break;
            case "cube": case "pack":
                p.moveTo(x + s * .5, y + s * .06); p.lineTo(x + s * .92, y + s * .28); p.lineTo(x + s * .92, y + s * .72); p.lineTo(x + s * .5, y + s * .94);
                p.lineTo(x + s * .08, y + s * .72); p.lineTo(x + s * .08, y + s * .28); p.closePath();
                g.fill(p); g.setColor(new Color(8, 18, 50)); g.draw(new Line2D.Double(x + s * .5, y + s * .48, x + s * .5, y + s * .94));
                g.draw(new Line2D.Double(x + s * .5, y + s * .48, x + s * .08, y + s * .28)); g.draw(new Line2D.Double(x + s * .5, y + s * .48, x + s * .92, y + s * .28)); break;
            case "gear": {
                Area a = new Area(new Ellipse2D.Float(x + s * .2f, y + s * .2f, s * .6f, s * .6f));
                for (int i = 0; i < 8; i++) {
                    AffineTransform t = AffineTransform.getRotateInstance(i * Math.PI / 4, x + s / 2, y + s / 2);
                    a.add(new Area(t.createTransformedShape(new Rectangle2D.Float(x + s * .41f, y + s * .02f, s * .18f, s * .22f))));
                }
                a.subtract(new Area(new Ellipse2D.Float(x + s * .36f, y + s * .36f, s * .28f, s * .28f)));
                g.fill(a); break;
            }
            case "shirt":
                p.moveTo(x + s * .3, y + s * .08); p.lineTo(x + s * .42, y + s * .08); p.quadTo(x + s * .5, y + s * .22, x + s * .58, y + s * .08); p.lineTo(x + s * .7, y + s * .08);
                p.lineTo(x + s * .97, y + s * .28); p.lineTo(x + s * .84, y + s * .48); p.lineTo(x + s * .76, y + s * .42); p.lineTo(x + s * .76, y + s * .92);
                p.lineTo(x + s * .24, y + s * .92); p.lineTo(x + s * .24, y + s * .42); p.lineTo(x + s * .16, y + s * .48); p.lineTo(x + s * .03, y + s * .28); p.closePath();
                g.fill(p); break;
            case "puzzle": {
                Area a = new Area(new Rectangle2D.Float(x + s * .12f, y + s * .22f, s * .76f, s * .7f));
                a.add(new Area(new Ellipse2D.Float(x + s * .36f, y + s * .04f, s * .28f, s * .28f)));
                a.add(new Area(new Ellipse2D.Float(x + s * .78f, y + s * .46f, s * .2f, s * .26f)));
                a.subtract(new Area(new Ellipse2D.Float(x + s * .02f, y + s * .46f, s * .2f, s * .26f)));
                g.fill(a); break;
            }
            case "star":
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 5, r = (i % 2 == 0) ? s * .46 : s * .2;
                    double px = x + s / 2 + Math.cos(ang) * r, py = y + s / 2 + Math.sin(ang) * r;
                    if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
                }
                p.closePath(); g.fill(p); break;
            case "folder":
                g.fill(new RoundRectangle2D.Float(x + s * .05f, y + s * .25f, s * .9f, s * .65f, s * .12f, s * .12f));
                g.fill(new RoundRectangle2D.Float(x + s * .05f, y + s * .12f, s * .4f, s * .22f, s * .08f, s * .08f)); break;
            case "term":
                g.draw(new RoundRectangle2D.Float(x + s * .06f, y + s * .14f, s * .88f, s * .72f, s * .12f, s * .12f));
                g.draw(new Line2D.Float(x + s * .22f, y + s * .36f, x + s * .4f, y + s * .5f)); g.draw(new Line2D.Float(x + s * .4f, y + s * .5f, x + s * .22f, y + s * .64f));
                g.draw(new Line2D.Float(x + s * .5f, y + s * .66f, x + s * .74f, y + s * .66f)); break;
            case "bars":
                for (int i = 0; i < 4; i++) g.fill(new Rectangle2D.Float(x + s * (.1f + i * .22f), y + s * (.8f - i * .22f), s * .14f, s * (.12f + i * .22f)));
                break;
            default:
                g.fill(new Ellipse2D.Float(x + s * .2f, y + s * .2f, s * .6f, s * .6f));
        }
    }

    // ------------------------------------------------------------ Buttons
    public enum Kind { PRIMARY, GHOST, DANGER }

    public static class Btn extends JComponent {
        private String text; public Kind kind; private boolean hover, down, selected;
        private final Runnable action; private float fs = 14; private String ico;

        public Btn(String text, Kind kind, Runnable action) {
            this.text = text; this.kind = kind; this.action = action;
            setFocusable(true); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; down = false; repaint(); }
                public void mousePressed(MouseEvent e) { if (isEnabled()) { down = true; requestFocusInWindow(); repaint(); } }
                public void mouseReleased(MouseEvent e) {
                    boolean fire = down && hover && isEnabled(); down = false; repaint();
                    if (fire && Btn.this.action != null) Btn.this.action.run();
                }
            });
            addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) && isEnabled() && Btn.this.action != null) Btn.this.action.run(); } });
            addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { repaint(); } public void focusLost(FocusEvent e) { repaint(); } });
        }
        public Btn size(float fs) { this.fs = fs; revalidate(); return this; }
        public Btn icon(String i) { ico = i; revalidate(); return this; }
        public void setText(String t) { text = t; revalidate(); repaint(); }
        public String getText() { return text; }
        public void setSelected(boolean s) { selected = s; repaint(); }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(f(fs, true));
            return new Dimension(fm.stringWidth(text) + 36 + (ico != null ? (int) (fs * 1.5) : 0), Math.round(fs * 2.5f));
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hq(g);
            int w = getWidth(), h = getHeight();
            RoundRectangle2D s = new RoundRectangle2D.Float(1.5f, 1.5f, w - 4, h - 4, h * .5f, h * .5f);
            Color edge, c1, c2, fg = TXT;
            switch (kind) {
                case PRIMARY: edge = EDGE2; c1 = hover ? new Color(0x2f8bff) : new Color(0x1c6df0); c2 = hover ? new Color(0x1a54d0) : new Color(0x1148b8); break;
                case DANGER: edge = BAD; c1 = hover ? new Color(0x5a1522) : new Color(0x3a0f1a); c2 = new Color(0x2a0a12); break;
                default: edge = hover ? EDGE2 : EDGE; c1 = hover ? new Color(0x12275e) : new Color(0x0a1840); c2 = new Color(0x081230);
            }
            if (!isEnabled()) { edge = new Color(0x3a4666); c1 = new Color(0x141c33); c2 = c1; fg = new Color(0x7482a8); }
            if (down && isEnabled()) { Color t = c1; c1 = c2; c2 = t; }
            g.setPaint(new GradientPaint(0, 0, c1, 0, h, c2)); g.fill(s);
            glow(g, s, edge, 1.3f, isEnabled() && (hover || kind == Kind.PRIMARY) ? 2 : 0);
            g.setFont(f(fs, true)); FontMetrics fm = g.getFontMetrics();
            int iw = ico != null ? (int) (fs * 1.5) : 0, tw = fm.stringWidth(text);
            int tx = (w - tw - iw) / 2 + iw, ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            if (ico != null) Widgets.icon(g, ico, tx - iw, (h - fs * 1.1f) / 2, fs * 1.1f, fg);
            g.setColor(fg); g.drawString(text, tx, ty);
            if (isFocusOwner()) { g.setColor(new Color(255, 255, 255, 120)); g.setStroke(new BasicStroke(1f)); g.draw(new RoundRectangle2D.Float(4, 4, w - 9, h - 9, h * .4f, h * .4f)); }
            g.dispose();
        }
    }

    /** Eintrag der Seitenleiste. */
    public static class Nav extends JComponent {
        private final String text, ico, badge; private boolean sel, hover; private final Runnable action;
        public Nav(String text, String ico, String badge, Runnable action) {
            this.text = text; this.ico = ico; this.badge = badge; this.action = action;
            setPreferredSize(new Dimension(240, 54)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            setFocusable(true); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { requestFocusInWindow(); Nav.this.action.run(); }
            });
            addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) Nav.this.action.run(); } });
            addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { repaint(); } public void focusLost(FocusEvent e) { repaint(); } });
        }
        public void setSelected(boolean s) { sel = s; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hq(g);
            int w = getWidth(), h = getHeight();
            RoundRectangle2D s = new RoundRectangle2D.Float(3, 4, w - 7, h - 8, 18, 18);
            if (sel) {
                g.setPaint(new GradientPaint(0, 0, new Color(0x1d6bff), w, 0, new Color(0x1246c4))); g.fill(s);
                glow(g, s, EDGE2, 1.6f, 3);
            } else if (hover) { g.setColor(new Color(40, 100, 255, 40)); g.fill(s); }
            icon(g, ico, 20, (h - 26) / 2f, 26, sel ? Color.WHITE : new Color(0xdbe6ff));
            g.setFont(f(17, sel)); g.setColor(Color.WHITE); FontMetrics fm = g.getFontMetrics();
            g.drawString(text, 62, (h - fm.getHeight()) / 2 + fm.getAscent());
            if (badge != null) {
                g.setFont(f(12, true)); fm = g.getFontMetrics(); int bw = fm.stringWidth(badge) + 20, bx = 62 + getFontMetrics(f(17, sel)).stringWidth(text) + 12;
                RoundRectangle2D b = new RoundRectangle2D.Float(bx, h / 2f - 11, bw, 22, 22, 22);
                g.setColor(new Color(0x1a68ff)); g.fill(b); g.setColor(Color.WHITE); g.drawString(badge, bx + 10, h / 2f + 4.5f);
            }
            if (isFocusOwner() && !sel) { g.setColor(new Color(255, 255, 255, 90)); g.setStroke(new BasicStroke(1f)); g.draw(s); }
            g.dispose();
        }
    }

    /** Großer Spielen-Button in Sechseck-Form. */
    public static class PlayBtn extends JComponent {
        private String title = "Spielen", sub = "Auf XuroSMP verbinden!"; private boolean hover, down; private final Runnable action;
        public PlayBtn(Runnable action) {
            this.action = action; setPreferredSize(new Dimension(520, 96)); setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; down = false; repaint(); }
                public void mousePressed(MouseEvent e) { if (isEnabled()) { down = true; requestFocusInWindow(); repaint(); } }
                public void mouseReleased(MouseEvent e) { boolean fire = down && hover && isEnabled(); down = false; repaint(); if (fire) PlayBtn.this.action.run(); }
            });
            addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) { if ((e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) && isEnabled()) PlayBtn.this.action.run(); } });
            addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { repaint(); } public void focusLost(FocusEvent e) { repaint(); } });
        }
        public void set(String title, String sub) { this.title = title; this.sub = sub; repaint(); }

        static Path2D hex(float x, float y, float w, float h, float cut) {
            Path2D p = new Path2D.Float();
            p.moveTo(x + cut, y); p.lineTo(x + w - cut, y); p.lineTo(x + w, y + h / 2); p.lineTo(x + w - cut, y + h);
            p.lineTo(x + cut, y + h); p.lineTo(x, y + h / 2); p.closePath(); return p;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hq(g);
            int w = getWidth(), h = getHeight();
            float cut = h * .42f;
            // Seitliche Zierlinien
            Path2D outer = hex(2, 4, w - 5, h - 8, cut + 2);
            g.setColor(new Color(30, 110, 255, 60));
            for (int i = 0; i < 3; i++) { g.setStroke(new BasicStroke(3f)); g.draw(new Line2D.Float(w * .02f + i * 10, h * .12f, w * .02f + i * 10 + cut * .5f, h * .5f)); }
            Path2D in = hex(14, 10, w - 29, h - 20, cut);
            boolean on = isEnabled();
            Color a = on ? (hover ? new Color(0x4aa0ff) : new Color(0x2a82ff)) : new Color(0x2a3558), b = on ? new Color(0x0c3fb8) : new Color(0x182040);
            if (down && on) { Color t = a; a = b; b = t; }
            g.setPaint(new GradientPaint(0, 10, a, 0, h - 10, b)); g.fill(in);
            g.setPaint(new GradientPaint(0, 10, new Color(255, 255, 255, 70), 0, h / 2f, new Color(255, 255, 255, 0)));
            g.fill(hex(18, 12, w - 37, h / 2f - 12, cut * .9f));
            glow(g, in, on ? new Color(0x6fd0ff) : new Color(0x4a5680), 2.2f, on ? 4 : 0);
            glow(g, hex(8, 6, w - 17, h - 12, cut + 6), new Color(30, 110, 255, 150), 1.2f, on ? 2 : 0);
            // Play-Dreieck + Text
            g.setFont(f(38, true)); FontMetrics fm = g.getFontMetrics(); int tw = fm.stringWidth(title);
            g.setFont(f(15, false)); int sw = g.getFontMetrics().stringWidth(sub);
            int block = Math.max(tw, sw), x0 = (w - block - 64) / 2 + 64;
            Path2D tri = new Path2D.Float(); float tx = x0 - 70, ty = h / 2f;
            tri.moveTo(tx, ty - 24); tri.lineTo(tx + 40, ty); tri.lineTo(tx, ty + 24); tri.closePath();
            g.setColor(on ? Color.WHITE : new Color(0x8e9bc0)); g.fill(tri);
            g.setFont(f(38, true)); g.setColor(on ? Color.WHITE : new Color(0x8e9bc0)); g.drawString(title, x0 - 14 + (block - tw) / 2f, h / 2f + 2);
            g.setFont(f(15, false)); g.setColor(on ? new Color(0xdcebff) : new Color(0x7482a8)); g.drawString(sub, x0 - 14 + (block - sw) / 2f, h / 2f + 28);
            if (isFocusOwner()) { g.setColor(new Color(255, 255, 255, 140)); g.setStroke(new BasicStroke(1f)); g.draw(hex(20, 16, w - 41, h - 32, cut * .9f)); }
            g.dispose();
        }
    }

    /** Fortschrittsbalken mit Text. */
    public static class Bar extends JComponent {
        private double v; private String text = "";
        public Bar() { setPreferredSize(new Dimension(100, 26)); }
        public void set(double v, String text) { this.v = Math.max(0, Math.min(1, v)); this.text = text; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hq(g);
            int w = getWidth(), h = getHeight();
            RoundRectangle2D s = new RoundRectangle2D.Float(1, 1, w - 3, h - 3, h, h);
            g.setColor(new Color(6, 14, 40, 230)); g.fill(s);
            Shape old = g.getClip(); g.setClip(s);
            int fw = (int) ((w - 2) * v);
            if (fw > 0) { g.setPaint(new GradientPaint(0, 0, new Color(0x2f8bff), fw, 0, new Color(0x3bd0ff))); g.fillRect(1, 1, fw, h - 2); }
            g.setClip(old);
            glow(g, s, EDGE, 1.2f, 0);
            g.setFont(f(12.5f, true)); FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE); g.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** Schalter mit Titel und Beschreibung. */
    public static class Chk extends JComponent {
        private boolean on; private final String title, desc; private final Consumer<Boolean> cb; private final boolean locked; private boolean hover;
        public Chk(String title, String desc, boolean on, boolean locked, Consumer<Boolean> cb) {
            this.title = title; this.desc = desc; this.on = on; this.locked = locked; this.cb = cb;
            setFocusable(true);
            if (!locked) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { toggle(); requestFocusInWindow(); }
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
            addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) toggle(); } });
            addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { repaint(); } public void focusLost(FocusEvent e) { repaint(); } });
        }
        private void toggle() { if (locked) return; on = !on; repaint(); if (cb != null) cb.accept(on); }
        public boolean isOn() { return on; }
        @Override public Dimension getPreferredSize() { return new Dimension(400, desc == null || desc.isEmpty() ? 44 : 58); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hq(g);
            int w = getWidth(), h = getHeight();
            if (hover && !locked) { g.setColor(new Color(40, 100, 255, 28)); g.fillRoundRect(0, 2, w, h - 4, 14, 14); }
            int sx = w - 62, sy = h / 2 - 12;
            RoundRectangle2D t = new RoundRectangle2D.Float(sx, sy, 46, 24, 24, 24);
            g.setColor(on ? (locked ? new Color(0x2a58b0) : new Color(0x1c6df0)) : new Color(0x1a2447)); g.fill(t);
            glow(g, t, on ? EDGE2 : new Color(0x3a4a80), 1.2f, on && !locked ? 2 : 0);
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Float(on ? sx + 25 : sx + 3, sy + 3, 18, 18));
            g.setFont(f(15.5f, true)); FontMetrics fm = g.getFontMetrics();
            boolean d = desc != null && !desc.isEmpty();
            int ty = d ? 24 : (h - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(Color.WHITE); g.drawString(clip(fm, title + (locked ? "  (immer an)" : ""), w - 90), 12, ty);
            if (d) { g.setFont(f(12.5f, false)); g.setColor(MUTED); g.drawString(clip(g.getFontMetrics(), desc, w - 90), 12, ty + 20); }
            if (isFocusOwner()) { g.setColor(new Color(255, 255, 255, 90)); g.setStroke(new BasicStroke(1f)); g.drawRoundRect(1, 2, w - 3, h - 5, 14, 14); }
            g.dispose();
        }
    }

    /** Regler mit Stufen. */
    public static class Slide extends JComponent {
        private final int min, max, step; private int val; private final Consumer<Integer> cb; private final java.util.function.IntFunction<String> fmt;
        public Slide(int min, int max, int step, int val, java.util.function.IntFunction<String> fmt, Consumer<Integer> cb) {
            this.min = min; this.max = max; this.step = step; this.val = Math.max(min, Math.min(max, val)); this.cb = cb; this.fmt = fmt;
            setFocusable(true); setPreferredSize(new Dimension(420, 38));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { requestFocusInWindow(); set(e.getX()); }
                public void mouseDragged(MouseEvent e) { set(e.getX()); }
            };
            addMouseListener(m); addMouseMotionListener(m);
            addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_LEFT) setVal(Slide.this.val - Slide.this.step); else if (e.getKeyCode() == KeyEvent.VK_RIGHT) setVal(Slide.this.val + Slide.this.step); } });
            addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { repaint(); } public void focusLost(FocusEvent e) { repaint(); } });
        }
        private void set(int x) { double fr = (x - 12.0) / Math.max(1, getWidth() - 24); setVal((int) Math.round((min + fr * (max - min)) / step) * step); }
        private void setVal(int v) { v = Math.max(min, Math.min(max, v)); if (v != val) { val = v; repaint(); if (cb != null) cb.accept(val); } }
        public int value() { return val; }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hq(g);
            int w = getWidth(), h = getHeight(), cy = h / 2;
            g.setColor(new Color(0x16224a)); g.fillRoundRect(8, cy - 4, w - 16, 8, 8, 8);
            int kx = 12 + (int) ((w - 24) * (double) (val - min) / Math.max(1, max - min));
            g.setPaint(new GradientPaint(8, 0, new Color(0x2f8bff), w, 0, new Color(0x3bd0ff))); g.fillRoundRect(8, cy - 4, kx - 8, 8, 8, 8);
            g.setColor(new Color(40, 120, 255, 70)); g.fillOval(kx - 13, cy - 13, 26, 26);
            g.setColor(Color.WHITE); g.fillOval(kx - 8, cy - 8, 16, 16);
            g.setFont(f(13, true)); String t = fmt.apply(val); FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE); g.drawString(t, w - fm.stringWidth(t) - 2, cy - 12);
            if (isFocusOwner()) { g.setColor(new Color(255, 255, 255, 90)); g.setStroke(new BasicStroke(1f)); g.drawRoundRect(1, 1, w - 3, h - 3, 10, 10); }
            g.dispose();
        }
    }

    public static JTextField field(String text, int cols) {
        JTextField fld = new JTextField(text, cols);
        fld.setBackground(new Color(5, 12, 34)); fld.setForeground(Color.WHITE); fld.setCaretColor(Color.WHITE);
        fld.setFont(f(14, false)); fld.setSelectionColor(new Color(0x1c6df0));
        fld.setBorder(new CompoundBorder(new LineBorder(EDGE, 1, true), new EmptyBorder(7, 10, 7, 10)));
        fld.setMaximumSize(new Dimension(Integer.MAX_VALUE, fld.getPreferredSize().height));
        return fld;
    }

    public static JLabel lbl(String text, float size, boolean bold, Color c) {
        JLabel l = new JLabel(text); l.setFont(f(size, bold)); l.setForeground(c); return l;
    }

    public static JLabel sans(String text, float size, Color c) { return lbl(text, size, false, c); }

    /** Dunkles Scroll-Feld ohne Rand. */
    public static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder()); sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = new Color(0x1f4fb8); trackColor = new Color(0, 0, 0, 0); }
            @Override protected JButton createDecreaseButton(int o) { return zero(); }
            @Override protected JButton createIncreaseButton(int o) { return zero(); }
            JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        });
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        return sp;
    }
}
