package javax.microedition.lcdui;

import java.util.Arrays;

/** Desenho em software direto no buffer ARGB da imagem alvo, com a semântica exata do MIDP. */
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;
    public static final int SOLID = 0, DOTTED = 1;

    private final int[] buf;
    private final int bw, bh;
    private int tx, ty;
    private int cx0, cy0, cx1, cy1; // recorte absoluto, fim exclusivo
    private int color = 0xFF000000;
    private int stroke = SOLID;
    private Font font = Font.getDefaultFont();

    Graphics(Image target) {
        buf = target.pixels;
        bw = target.w;
        bh = target.h;
        cx1 = bw;
        cy1 = bh;
    }

    // ---- estado ----

    public void translate(int x, int y) {
        tx += x;
        ty += y;
    }

    public int getTranslateX() {
        return tx;
    }

    public int getTranslateY() {
        return ty;
    }

    public int getColor() {
        return color & 0xFFFFFF;
    }

    public int getRedComponent() {
        return (color >> 16) & 0xFF;
    }

    public int getGreenComponent() {
        return (color >> 8) & 0xFF;
    }

    public int getBlueComponent() {
        return color & 0xFF;
    }

    public int getGrayScale() {
        return (getRedComponent() + getGreenComponent() + getBlueComponent()) / 3;
    }

    public void setColor(int r, int g, int b) {
        if ((r | g | b) < 0 || r > 255 || g > 255 || b > 255) throw new IllegalArgumentException();
        color = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public void setColor(int rgb) {
        color = 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public void setGrayScale(int v) {
        setColor(v, v, v);
    }

    public int getDisplayColor(int c) {
        return c & 0xFFFFFF;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font f) {
        font = f == null ? Font.getDefaultFont() : f;
    }

    public int getStrokeStyle() {
        return stroke;
    }

    public void setStrokeStyle(int s) {
        stroke = s;
    }

    public int getClipX() {
        return cx0 - tx;
    }

    public int getClipY() {
        return cy0 - ty;
    }

    public int getClipWidth() {
        return cx1 - cx0;
    }

    public int getClipHeight() {
        return cy1 - cy0;
    }

    public void setClip(int x, int y, int w, int h) {
        x += tx;
        y += ty;
        cx0 = Math.max(x, 0);
        cy0 = Math.max(y, 0);
        cx1 = Math.min(x + Math.max(w, 0), bw);
        cy1 = Math.min(y + Math.max(h, 0), bh);
        if (cx1 < cx0) cx1 = cx0;
        if (cy1 < cy0) cy1 = cy0;
    }

    public void clipRect(int x, int y, int w, int h) {
        x += tx;
        y += ty;
        cx0 = Math.max(cx0, x);
        cy0 = Math.max(cy0, y);
        cx1 = Math.min(cx1, x + Math.max(w, 0));
        cy1 = Math.min(cy1, y + Math.max(h, 0));
        if (cx1 < cx0) cx1 = cx0;
        if (cy1 < cy0) cy1 = cy0;
    }

    // ---- primitivas ----

    private void plot(int x, int y) {
        if (x >= cx0 && x < cx1 && y >= cy0 && y < cy1) buf[y * bw + x] = color;
    }

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        x += tx;
        y += ty;
        int x0 = Math.max(x, cx0), y0 = Math.max(y, cy0);
        int x1 = Math.min(x + w, cx1), y1 = Math.min(y + h, cy1);
        if (x0 >= x1 || y0 >= y1) return;
        for (int yy = y0; yy < y1; yy++) Arrays.fill(buf, yy * bw + x0, yy * bw + x1, color);
    }

    public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) return;
        if (w == 0 || h == 0) {
            fillRect(x, y, w + 1, h + 1);
            return;
        }
        fillRect(x, y, w + 1, 1);
        fillRect(x, y + h, w + 1, 1);
        fillRect(x, y + 1, 1, h - 1);
        fillRect(x + w, y + 1, 1, h - 1);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        x1 += tx;
        y1 += ty;
        x2 += tx;
        y2 += ty;
        int dx = Math.abs(x2 - x1), dy = -Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            plot(x1, y1);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x1 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        fillRect(x, y, w, h);
    }

    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        drawRect(x, y, w, h);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        int minY = Math.min(y1, Math.min(y2, y3)), maxY = Math.max(y1, Math.max(y2, y3));
        for (int y = minY; y <= maxY; y++) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            int[][] e = {{x1, y1, x2, y2}, {x2, y2, x3, y3}, {x3, y3, x1, y1}};
            for (int[] s : e) {
                int ya = s[1], yb = s[3];
                if (y < Math.min(ya, yb) || y > Math.max(ya, yb)) continue;
                int x = ya == yb ? s[0] : s[0] + (s[2] - s[0]) * (y - ya) / (yb - ya);
                if (ya == yb) {
                    lo = Math.min(lo, Math.min(s[0], s[2]));
                    hi = Math.max(hi, Math.max(s[0], s[2]));
                } else {
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
            }
            if (lo <= hi) fillRect(lo, y, hi - lo + 1, 1);
        }
    }

    public void fillArc(int x, int y, int w, int h, int start, int arc) {
        if (w <= 0 || h <= 0) return;
        double rx = w / 2.0, ry = h / 2.0, cx = x + rx, cy = y + ry;
        for (int yy = 0; yy < h; yy++) {
            double dy = (yy + 0.5 - ry) / ry;
            for (int xx = 0; xx < w; xx++) {
                double dx = (xx + 0.5 - rx) / rx;
                if (dx * dx + dy * dy > 1) continue;
                if (inArc(Math.toDegrees(Math.atan2(-(dy), dx)), start, arc)) plotRel(x + xx, y + yy);
            }
        }
    }

    public void drawArc(int x, int y, int w, int h, int start, int arc) {
        if (w < 0 || h < 0) return;
        double rx = w / 2.0, ry = h / 2.0, cx = x + rx, cy = y + ry;
        int n = Math.max(16, (w + h) * 2);
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(start + arc * (double) i / n);
            plotRel((int) Math.round(cx + rx * Math.cos(a)), (int) Math.round(cy - ry * Math.sin(a)));
        }
    }

    private static boolean inArc(double ang, int start, int arc) {
        if (Math.abs(arc) >= 360) return true;
        double a = ((ang - start) % 360 + 360) % 360;
        return arc >= 0 ? a <= arc : a >= 360 + arc;
    }

    private void plotRel(int x, int y) {
        plot(x + tx, y + ty);
    }

    // ---- imagens ----

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) throw new NullPointerException();
        if ((anchor & HCENTER) != 0) x -= img.w / 2;
        else if ((anchor & RIGHT) != 0) x -= img.w;
        if ((anchor & VCENTER) != 0) y -= img.h / 2;
        else if ((anchor & BOTTOM) != 0) y -= img.h;
        blit(img.pixels, img.w, 0, 0, img.w, img.h, Sprite_NONE, x, y, img.alpha);
    }

    public void drawRegion(Image src, int xs, int ys, int w, int h, int transform, int xd, int yd, int anchor) {
        if (src == null) throw new NullPointerException();
        if (w <= 0 || h <= 0) return;
        boolean swap = transform >= 4; // ROT90, ROT270 e variantes trocam largura e altura
        int tw = swap ? h : w, th = swap ? w : h;
        if ((anchor & HCENTER) != 0) xd -= tw / 2;
        else if ((anchor & RIGHT) != 0) xd -= tw;
        if ((anchor & VCENTER) != 0) yd -= th / 2;
        else if ((anchor & BOTTOM) != 0) yd -= th;
        gowb.UltimoBlit.anota(src, xs, ys, xd, yd, tw, th);
        blit(src.pixels, src.w, xs, ys, w, h, transform, xd, yd, src.alpha);
    }

    private static final int Sprite_NONE = 0;

    /**
     * Copia a região (xs,ys,w,h) de src aplicando a transformação do Sprite MIDP
     * (0 NONE, 1 MIRROR_ROT180, 2 MIRROR, 3 ROT180, 4 MIRROR_ROT270, 5 ROT90, 6 ROT270, 7 MIRROR_ROT90).
     */
    private void blit(int[] src, int sw, int xs, int ys, int w, int h, int tr, int xd, int yd, boolean alpha) {
        int sh = src.length / sw;
        boolean swap = tr >= 4;
        int tw = swap ? h : w, th = swap ? w : h;
        xd += tx;
        yd += ty;
        int u0 = Math.max(0, cx0 - xd), v0 = Math.max(0, cy0 - yd);
        int u1 = Math.min(tw, cx1 - xd), v1 = Math.min(th, cy1 - yd);
        for (int v = v0; v < v1; v++) {
            int drow = (yd + v) * bw + xd;
            for (int u = u0; u < u1; u++) {
                int i, j;
                switch (tr) {
                    case 0: i = u; j = v; break;
                    case 1: i = u; j = h - 1 - v; break;
                    case 2: i = w - 1 - u; j = v; break;
                    case 3: i = w - 1 - u; j = h - 1 - v; break;
                    case 4: i = v; j = u; break;
                    case 5: i = v; j = h - 1 - u; break;
                    case 6: i = w - 1 - v; j = u; break;
                    default: i = w - 1 - v; j = h - 1 - u; break;
                }
                int sx = xs + i, sy = ys + j;
                if (sx < 0 || sy < 0 || sx >= sw || sy >= sh) continue;
                int s = src[sy * sw + sx];
                if (!alpha) {
                    buf[drow + u] = s | 0xFF000000;
                    continue;
                }
                int a = s >>> 24;
                if (a == 0) continue;
                if (a == 255) buf[drow + u] = s;
                else buf[drow + u] = blend(buf[drow + u], s, a);
            }
        }
    }

    private static int blend(int d, int s, int a) {
        int na = 255 - a;
        int r = (((s >> 16) & 0xFF) * a + ((d >> 16) & 0xFF) * na) / 255;
        int g = (((s >> 8) & 0xFF) * a + ((d >> 8) & 0xFF) * na) / 255;
        int b = ((s & 0xFF) * a + (d & 0xFF) * na) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public void drawRGB(int[] rgb, int offset, int scan, int x, int y, int w, int h, boolean processAlpha) {
        if (rgb == null) throw new NullPointerException();
        if (w <= 0 || h <= 0) return;
        x += tx;
        y += ty;
        for (int j = 0; j < h; j++) {
            int yy = y + j;
            if (yy < cy0 || yy >= cy1) continue;
            for (int i = 0; i < w; i++) {
                int xx = x + i;
                if (xx < cx0 || xx >= cx1) continue;
                int s = rgb[offset + j * scan + i];
                int p = yy * bw + xx;
                if (!processAlpha) {
                    buf[p] = s | 0xFF000000;
                    continue;
                }
                int a = s >>> 24;
                if (a == 0) continue;
                buf[p] = a == 255 ? s : blend(buf[p], s, a);
            }
        }
    }

    public void copyArea(int xs, int ys, int w, int h, int xd, int yd, int anchor) {
        int[] tmp = new int[w * h];
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                int sx = xs + tx + i, sy = ys + ty + j;
                if (sx >= 0 && sy >= 0 && sx < bw && sy < bh) tmp[j * w + i] = buf[sy * bw + sx];
            }
        if ((anchor & HCENTER) != 0) xd -= w / 2;
        else if ((anchor & RIGHT) != 0) xd -= w;
        if ((anchor & VCENTER) != 0) yd -= h / 2;
        else if ((anchor & BOTTOM) != 0) yd -= h;
        drawRGB(tmp, 0, w, xd, yd, w, h, false);
    }

    // ---- texto ----

    public void drawString(String s, int x, int y, int anchor) {
        drawText(s, x, y, anchor);
    }

    public void drawSubstring(String s, int off, int len, int x, int y, int anchor) {
        drawText(s.substring(off, off + len), x, y, anchor);
    }

    public void drawChar(char c, int x, int y, int anchor) {
        drawText(String.valueOf(c), x, y, anchor);
    }

    public void drawChars(char[] data, int off, int len, int x, int y, int anchor) {
        drawText(new String(data, off, len), x, y, anchor);
    }

    private void drawText(String s, int x, int y, int anchor) {
        if (s.isEmpty()) return;
        if (anchor == 0) anchor = TOP | LEFT;
        int w = font.stringWidth(s), h = font.getHeight();
        if ((anchor & HCENTER) != 0) x -= w / 2;
        else if ((anchor & RIGHT) != 0) x -= w;
        if ((anchor & BASELINE) != 0) y -= font.getBaselinePosition();
        else if ((anchor & BOTTOM) != 0) y -= h;
        int[] px = font.render(s, color);
        if (px.length == 0) return;
        drawRGB(px, 0, w, x, y, w, h, true);
    }
}
