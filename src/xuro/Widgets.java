package xuro;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;

/** Eigene Bedienelemente im Minecraft-Look. */
public final class Widgets {
    private Widgets() {}

    public enum Kind { GREEN, GREY, TAB }

    public static class Btn extends JComponent {
        private String text; public Kind kind; public boolean selected;
        private boolean hover, down; private final Runnable action;
        private float fs = 15;

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
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if ((e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) && isEnabled() && Btn.this.action != null) Btn.this.action.run();
                }
            });
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        public Btn size(float fs) { this.fs = fs; revalidate(); return this; }
        public void setText(String t) { text = t; revalidate(); repaint(); }
        public String getText() { return text; }
        public void setSelected(boolean s) { selected = s; repaint(); }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Mc.font(fs, true));
            int padX = kind == Kind.TAB ? 18 : 28;
            return new Dimension(fm.stringWidth(text) + padX * 2 / 2 + padX, Math.round(fs * 2.3f) + (kind == Kind.TAB ? 2 : 6));
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Mc.aa(g);
            int w = getWidth(), h = getHeight();
            Color face, hi, lo, fg = Color.WHITE, sh = new Color(0x3e3e3e);
            switch (kind) {
                case GREEN: face = hover ? new Color(0x6db846) : new Color(0x5d9e3a); hi = new Color(0x92d764); lo = new Color(0x2d5a17); sh = new Color(0x2a4f16); break;
                case TAB:
                    if (selected) { face = Mc.PN; hi = Color.WHITE; lo = Mc.PN_LO; fg = Mc.INK; sh = new Color(0, 0, 0, 0); }
                    else { face = hover ? new Color(0x6a6a6a) : new Color(0x555555); hi = new Color(0x8a8a8a); lo = new Color(0x3a3a3a); fg = new Color(0xe8e8e8); }
                    break;
                default: face = hover ? new Color(0x8e90b8) : new Color(0x7d7d7d); hi = new Color(0xb9b9b9); lo = new Color(0x4a4a4a);
            }
            if (!isEnabled()) { face = new Color(0x5a5a5a); hi = new Color(0x6a6a6a); lo = new Color(0x444444); fg = new Color(0xa0a0a0); sh = new Color(0, 0, 0, 0); }
            if (down && isEnabled()) { Color t = hi; hi = lo; lo = t; }
            Mc.bevel(g, 0, 0, w, h, face, hi, lo, 3);
            if (kind == Kind.TAB && selected) { g.setColor(Mc.PN); g.fillRect(2, h - 3, w - 4, 3); }
            g.setFont(Mc.font(fs, true));
            FontMetrics fm = g.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2, ty = (h - fm.getHeight()) / 2 + fm.getAscent() - (down ? 0 : 1);
            if (sh.getAlpha() > 0) Mc.shadowText(g, text, tx, ty, fg, sh); else { g.setColor(fg); g.drawString(text, tx, ty); }
            if (isFocusOwner()) { g.setColor(Mc.GOLD); g.drawRect(3, 3, w - 7, h - 7); }
            g.dispose();
        }
    }

    /** Graues Inventar-Panel. */
    public static class Pn extends JPanel {
        public Pn(LayoutManager lm) { super(lm); setOpaque(false); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth(), h = getHeight();
            g.setColor(Mc.BLACK); g.fillRect(0, 0, w, h);
            g.setColor(Mc.PN); g.fillRect(2, 2, w - 4, h - 4);
            g.setColor(Mc.PN_HI); g.fillRect(2, 2, w - 4, 3); g.fillRect(2, 2, 3, h - 4);
            g.setColor(Mc.PN_LO); g.fillRect(2, h - 5, w - 4, 3); g.fillRect(w - 5, 2, 3, h - 4);
            g.dispose();
        }
    }

    public static class Dirt extends JPanel {
        private final TexturePaint tp = new TexturePaint(Mc.dirt(), new Rectangle2D.Double(0, 0, 128, 128));
        public Dirt(LayoutManager lm) { super(lm); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); g.setPaint(tp); g.fillRect(0, 0, getWidth(), getHeight()); g.dispose();
        }
    }

    /** Fortschrittsbalken mit Text. */
    public static class Bar extends JComponent {
        private double v; private String text = "";
        public Bar() { setPreferredSize(new Dimension(100, 30)); }
        public void set(double v, String text) { this.v = Math.max(0, Math.min(1, v)); this.text = text; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Mc.aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Mc.BLACK); g.fillRect(0, 0, w, h);
            g.setColor(new Color(0x373737)); g.fillRect(2, 2, w - 4, h - 4);
            int fw = (int) ((w - 4) * v);
            if (fw > 0) { g.setColor(new Color(0x5d9e3a)); g.fillRect(2, 2, fw, h - 4); g.setColor(new Color(0x92d764)); g.fillRect(2, 2, fw, 3); }
            g.setFont(Mc.font(13, true)); FontMetrics fm = g.getFontMetrics();
            Mc.shadowText(g, text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent(), Color.WHITE, new Color(0x303030));
            g.dispose();
        }
    }

    /** Checkbox mit Titel und Beschreibung. */
    public static class Chk extends JComponent {
        private boolean on; private final String title, desc; private final Consumer<Boolean> cb; private boolean locked, hover;
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
        @Override public Dimension getPreferredSize() { return new Dimension(400, desc == null || desc.isEmpty() ? 34 : 50); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Mc.aa(g);
            int w = getWidth();
            if (hover && !locked) { g.setColor(new Color(0, 0, 0, 25)); g.fillRect(0, 0, w, getHeight()); }
            g.setColor(Mc.BLACK); g.fillRect(6, 6, 24, 24);
            g.setColor(Mc.SLOT); g.fillRect(8, 8, 20, 20);
            g.setColor(Mc.SLOT_DARK); g.fillRect(8, 8, 20, 2); g.fillRect(8, 8, 2, 20);
            g.setColor(Color.WHITE); g.fillRect(8, 26, 20, 2); g.fillRect(26, 8, 2, 20);
            if (on) {
                g.setColor(locked ? new Color(0x4a6b3a) : new Color(0x2d7d1b)); g.setStroke(new BasicStroke(4f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                g.drawLine(12, 18, 16, 23); g.drawLine(16, 23, 25, 12);
            }
            g.setColor(Mc.INK); g.setFont(Mc.font(15, true));
            FontMetrics fm = g.getFontMetrics();
            int ty = (desc == null || desc.isEmpty()) ? 6 + 12 + fm.getAscent() / 2 : 6 + fm.getAscent() - 2;
            g.drawString(title + (locked ? "  (immer an)" : ""), 40, ty);
            if (desc != null && !desc.isEmpty()) { g.setColor(Mc.INK2); g.setFont(Mc.sans(12.5f, false)); g.drawString(desc, 40, ty + 18); }
            if (isFocusOwner()) { g.setColor(Mc.GOLD); g.drawRect(1, 1, w - 3, getHeight() - 3); }
            g.dispose();
        }
    }

    /** Regler mit Stufen. */
    public static class Slide extends JComponent {
        private int min, max, step, val; private final Consumer<Integer> cb; private final java.util.function.IntFunction<String> fmt;
        public Slide(int min, int max, int step, int val, java.util.function.IntFunction<String> fmt, Consumer<Integer> cb) {
            this.min = min; this.max = max; this.step = step; this.val = Math.max(min, Math.min(max, val)); this.cb = cb; this.fmt = fmt;
            setFocusable(true); setPreferredSize(new Dimension(420, 36));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { requestFocusInWindow(); set(e.getX()); }
                public void mouseDragged(MouseEvent e) { set(e.getX()); }
            };
            addMouseListener(m); addMouseMotionListener(m);
            addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_LEFT) setVal(val - step); else if (e.getKeyCode() == KeyEvent.VK_RIGHT) setVal(val + step); } });
            addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { repaint(); } public void focusLost(FocusEvent e) { repaint(); } });
        }
        private void set(int x) {
            double f = (x - 8.0) / Math.max(1, getWidth() - 16);
            setVal((int) Math.round((min + f * (max - min)) / step) * step);
        }
        private void setVal(int v) { v = Math.max(min, Math.min(max, v)); if (v != val) { val = v; repaint(); if (cb != null) cb.accept(val); } }
        public int value() { return val; }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Mc.aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Mc.BLACK); g.fillRect(0, 0, w, h);
            g.setColor(new Color(0x373737)); g.fillRect(2, 2, w - 4, h - 4);
            int tw = 16; int tx = 2 + (int) ((w - 4 - tw) * (double) (val - min) / Math.max(1, max - min));
            Mc.bevel(g, tx, 0, tw, h, new Color(0x7d7d7d), new Color(0xb9b9b9), new Color(0x4a4a4a), 3);
            g.setFont(Mc.font(14, true)); FontMetrics fm = g.getFontMetrics(); String t = fmt.apply(val);
            Mc.shadowText(g, t, (w - fm.stringWidth(t)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent(), Color.WHITE, new Color(0x303030));
            if (isFocusOwner()) { g.setColor(Mc.GOLD); g.drawRect(1, 1, w - 3, h - 3); }
            g.dispose();
        }
    }

    public static JTextField field(String text, int cols) {
        JTextField f = new JTextField(text, cols);
        f.setBackground(Color.BLACK); f.setForeground(Color.WHITE); f.setCaretColor(Color.WHITE);
        f.setFont(Mc.font(14, false)); f.setSelectionColor(new Color(0x5d9e3a));
        f.setBorder(new CompoundBorder(new LineBorder(new Color(0xa0a0a0), 2), new EmptyBorder(6, 8, 6, 8)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
        return f;
    }

    public static JLabel lbl(String text, float size, boolean bold, Color c) {
        JLabel l = new JLabel(text); l.setFont(Mc.font(size, bold)); l.setForeground(c); return l;
    }

    public static JLabel sans(String text, float size, Color c) {
        JLabel l = new JLabel(text); l.setFont(Mc.sans(size, false)); l.setForeground(c); return l;
    }
}
