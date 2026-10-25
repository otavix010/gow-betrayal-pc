package gowb;

import javax.microedition.lcdui.Image;

/** Último drawRegion feito (posição na tela e região da imagem), usado pelo Hooks para acentuar glifos. */
public final class UltimoBlit {
    private UltimoBlit() {}

    public static int contador;
    public static int x, y, w, h;
    public static Image src;
    public static int sx, sy;

    public static void anota(Image img, int srcX, int srcY, int dx, int dy, int dw, int dh) {
        contador++;
        src = img;
        sx = srcX;
        sy = srcY;
        x = dx;
        y = dy;
        w = dw;
        h = dh;
    }
}
