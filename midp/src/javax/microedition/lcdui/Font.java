package javax.microedition.lcdui;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Fonte do sistema do celular, desenhada com a fonte do Java (sem antisserrilhado, como num LCD). */
public final class Font {
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;
    public static final int FONT_STATIC_TEXT = 0, FONT_INPUT_TEXT = 1;

    private static final Map<Integer, Font> CACHE = new HashMap<>();
    private static final BufferedImage SCRATCH = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private final int face, style, size;
    private final java.awt.Font awt;
    private final FontMetrics fm;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        int px = size == SIZE_SMALL ? 12 : size == SIZE_LARGE ? 16 : 14;
        String name = face == FACE_MONOSPACE ? java.awt.Font.MONOSPACED : java.awt.Font.SANS_SERIF;
        int st = ((style & STYLE_BOLD) != 0 ? java.awt.Font.BOLD : 0) | ((style & STYLE_ITALIC) != 0 ? java.awt.Font.ITALIC : 0);
        awt = new java.awt.Font(name, st, px);
        Graphics2D g = SCRATCH.createGraphics();
        fm = g.getFontMetrics(awt);
        g.dispose();
    }

    public static synchronized Font getFont(int face, int style, int size) {
        return CACHE.computeIfAbsent(face << 16 | style << 8 | size, k -> new Font(face, style, size));
    }

    public static Font getDefaultFont() {
        return getFont(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
    }

    public static Font getFont(int specifier) {
        return getDefaultFont();
    }

    public int getFace() {
        return face;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public boolean isPlain() {
        return style == 0;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public int getHeight() {
        return fm.getHeight();
    }

    public int getBaselinePosition() {
        return fm.getAscent();
    }

    public int charWidth(char c) {
        return fm.charWidth(c);
    }

    public int charsWidth(char[] c, int off, int len) {
        return fm.charsWidth(c, off, len);
    }

    public int stringWidth(String s) {
        return fm.stringWidth(s);
    }

    public int substringWidth(String s, int off, int len) {
        return fm.stringWidth(s.substring(off, off + len));
    }

    /** Rasteriza o texto em ARGB (largura stringWidth, altura getHeight). */
    int[] render(String s, int argb) {
        int w = stringWidth(s), h = getHeight();
        if (w <= 0 || h <= 0) return new int[0];
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(awt);
        g.setColor(new java.awt.Color(argb, true));
        g.drawString(s, 0, fm.getAscent());
        if (isUnderlined()) g.drawLine(0, fm.getAscent() + 1, w - 1, fm.getAscent() + 1);
        g.dispose();
        return img.getRGB(0, 0, w, h, null, 0, w);
    }
}
