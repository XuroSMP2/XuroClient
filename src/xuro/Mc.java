package xuro;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Gemeinsame Farben, Schrift und Zeichenfunktionen im Minecraft-Menü-Stil. */
public final class Mc {
    private Mc() {}

    public static final Color PN = new Color(0xc6c6c6), PN_HI = Color.WHITE, PN_LO = new Color(0x6b6b6b);
    public static final Color INK = new Color(0x202020), INK2 = new Color(0x4a4a4a);
    public static final Color HEADER = new Color(0x1e1e1e), BLACK = Color.BLACK;
    public static final Color GOLD = new Color(0xffaa00), GREEN_TXT = new Color(0x55ff55), RED_TXT = new Color(0xff5555);
    public static final Color SLOT = new Color(0x8b8b8b), SLOT_DARK = new Color(0x373737);

    public static Font font(float size, boolean bold) {
        return new Font(Font.MONOSPACED, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    public static Font sans(float size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    /** Erdtextur: 16x16 Pixel, 8-fach vergrößert. */
    public static BufferedImage dirt() {
        int[] pal = {0x33251a, 0x3b2b1e, 0x2b1f15, 0x40301f, 0x4a3722, 0x2f2217};
        Random r = new Random(7);
        BufferedImage im = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int c = pal[r.nextInt(pal.length)];
                for (int yy = 0; yy < 8; yy++) for (int xx = 0; xx < 8; xx++) im.setRGB(x * 8 + xx, y * 8 + yy, c);
            }
        return im;
    }

    /** Kristall als Pixelbild. */
    static final String[] CRYSTAL = {
            "....aaaa....",
            "...abbbba...",
            "..abbccbba..",
            "..abccddcba.",
            ".abcdddddcba",
            ".abcddeedcba",
            ".abcdeeeedcb",
            ".abcdeeeedcb",
            "..abcdeedcb.",
            "..abcddddcb.",
            "...abcddcb..",
            "....abccb...",
            ".....gggg...",
            ".....gggg...",
    };

    static int pix(char c) {
        switch (c) {
            case 'a': return 0x3a1a6b;
            case 'b': return 0x7a3fd0;
            case 'c': return 0x9a5cf0;
            case 'd': return 0xb98cff;
            case 'e': return 0xe6d4ff;
            case 'g': return 0xc08a10;
            default: return -1;
        }
    }

    public static BufferedImage crystal(int px) {
        int w = CRYSTAL[0].length(), h = CRYSTAL.length;
        BufferedImage im = new BufferedImage(w * px, h * px, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = pix(CRYSTAL[y].charAt(x));
                if (c < 0) continue;
                for (int yy = 0; yy < px; yy++) for (int xx = 0; xx < px; xx++) im.setRGB(x * px + xx, y * px + yy, 0xff000000 | c);
            }
        return im;
    }

    /** Abgeschrägte Fläche im Minecraft-Look. */
    public static void bevel(Graphics2D g, int x, int y, int w, int h, Color face, Color hi, Color lo, int t) {
        g.setColor(BLACK); g.fillRect(x, y, w, h);
        g.setColor(face); g.fillRect(x + 2, y + 2, w - 4, h - 4);
        g.setColor(hi); g.fillRect(x + 2, y + 2, w - 4, t); g.fillRect(x + 2, y + 2, t, h - 4);
        g.setColor(lo); g.fillRect(x + 2, y + h - 2 - t, w - 4, t); g.fillRect(x + w - 2 - t, y + 2, t, h - 4);
    }

    public static void shadowText(Graphics2D g, String s, int x, int y, Color c, Color sh) {
        g.setColor(sh); g.drawString(s, x + 2, y + 2);
        g.setColor(c); g.drawString(s, x, y);
    }

    public static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }
}
