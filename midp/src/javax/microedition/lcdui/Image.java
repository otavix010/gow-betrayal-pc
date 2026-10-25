package javax.microedition.lcdui;

import gowb.Platform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;

public class Image {
    final int w, h;
    final int[] pixels; // ARGB
    final boolean mutable;
    /** false = todos os pixels opacos, o blit pode copiar direto. */
    final boolean alpha;

    private Image(int w, int h, int[] px, boolean mutable) {
        this.w = w;
        this.h = h;
        this.pixels = px;
        this.mutable = mutable;
        boolean a = false;
        if (!mutable)
            for (int p : px)
                if ((p >>> 24) != 255) {
                    a = true;
                    break;
                }
        this.alpha = a;
    }

    public static Image createImage(int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException();
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFFFFFFFF);
        return new Image(w, h, px, true);
    }

    public static Image createImage(byte[] data, int off, int len) {
        try {
            return decode(new ByteArrayInputStream(data, off, len));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Image createImage(String name) throws IOException {
        InputStream in = Platform.resource(name);
        if (in == null) throw new IOException("recurso não encontrado: " + name);
        try (in) {
            return decode(in);
        }
    }

    public static Image createImage(InputStream in) throws IOException {
        if (in == null) throw new NullPointerException();
        return decode(in);
    }

    public static Image createImage(Image src) {
        if (!src.mutable) return src;
        return new Image(src.w, src.h, src.pixels.clone(), false);
    }

    public static Image createImage(Image src, int x, int y, int w, int h, int transform) {
        boolean swap = transform >= 4;
        int tw = swap ? h : w, th = swap ? w : h;
        Image out = new Image(tw, th, new int[tw * th], true);
        new Graphics(out).drawRegion(src, x, y, w, h, transform, 0, 0, Graphics.TOP | Graphics.LEFT);
        return new Image(tw, th, out.pixels, false);
    }

    public static Image createRGBImage(int[] rgb, int w, int h, boolean processAlpha) {
        int[] px = Arrays.copyOf(rgb, w * h);
        if (!processAlpha) for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;
        return new Image(w, h, px, false);
    }

    private static Image decode(InputStream in) throws IOException {
        BufferedImage bi = ImageIO.read(in);
        if (bi == null) throw new IOException("imagem inválida");
        int w = bi.getWidth(), h = bi.getHeight();
        return new Image(w, h, bi.getRGB(0, 0, w, h, null, 0, w), false);
    }

    public Graphics getGraphics() {
        if (!mutable) throw new IllegalStateException();
        return new Graphics(this);
    }

    public int getWidth() {
        return w;
    }

    public int getHeight() {
        return h;
    }

    public boolean isMutable() {
        return mutable;
    }

    public void getRGB(int[] out, int offset, int scan, int x, int y, int w, int h) {
        for (int j = 0; j < h; j++) System.arraycopy(pixels, (y + j) * this.w + x, out, offset + j * scan, w);
    }
}
