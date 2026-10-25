package gowb;

import java.util.HashMap;
import java.util.Map;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** As fontes do jogo não têm acentos: desenha a letra base e o acento por cima, na cor do topo do glifo. */
final class Acentos {
    private Acentos() {}

    private static final String COM = "áàâãéêíóôõúüçÁÀÂÃÉÊÍÓÔÕÚÜÇ";
    private static final String SEM = "aaaaeeiooouucAAAAEEIOOOUUC";
    // ´ agudo, ` grave, ^ circunflexo, ~ til, : trema, , cedilha
    private static final String TIPO = "´`^~´^´´^~´:,´`^~´^´´^~´:,";

    private static final Map<Image, Map<Long, Integer>> CORES = new HashMap<>();

    static char base(char c) {
        int i = COM.indexOf(c);
        return i < 0 ? c : SEM.charAt(i);
    }

    /** Usa o último glifo desenhado (UltimoBlit) para posicionar e colorir o acento. */
    static void desenha(Graphics g, char c) {
        int i = COM.indexOf(c);
        if (i < 0) return;
        int topo = UltimoBlit.y, base = UltimoBlit.y + UltimoBlit.h;
        int cx = UltimoBlit.x + (UltimoBlit.w - 1) / 2;
        int antiga = g.getColor();
        g.setColor(cor());
        switch (TIPO.charAt(i)) {
            case '´' -> {
                px(g, cx, topo - 2);
                px(g, cx + 1, topo - 2);
                px(g, cx + 1, topo - 3);
                px(g, cx + 2, topo - 3);
            }
            case '`' -> {
                px(g, cx, topo - 2);
                px(g, cx - 1, topo - 2);
                px(g, cx - 1, topo - 3);
                px(g, cx - 2, topo - 3);
            }
            case '^' -> {
                px(g, cx - 2, topo - 2);
                px(g, cx - 1, topo - 3);
                px(g, cx, topo - 3);
                px(g, cx + 1, topo - 2);
            }
            case '~' -> {
                px(g, cx - 2, topo - 2);
                px(g, cx - 1, topo - 3);
                px(g, cx, topo - 3);
                px(g, cx + 1, topo - 2);
                px(g, cx + 2, topo - 2);
                px(g, cx + 3, topo - 3);
            }
            case ':' -> {
                px(g, cx - 2, topo - 2);
                px(g, cx + 2, topo - 2);
            }
            default -> { // cedilha
                px(g, cx, base);
                px(g, cx + 1, base + 1);
                px(g, cx, base + 2);
                px(g, cx - 1, base + 2);
            }
        }
        g.setColor(antiga);
    }

    private static void px(Graphics g, int x, int y) {
        g.fillRect(x, y, 1, 1);
    }

    /** Pixel opaco mais claro das duas primeiras linhas do glifo (o degradê da fonte começa no topo). */
    private static int cor() {
        Image img = UltimoBlit.src;
        int sx = Math.max(UltimoBlit.sx, 0), sy = Math.max(UltimoBlit.sy, 0);
        int w = Math.min(UltimoBlit.w, img.getWidth() - sx), h = Math.min(UltimoBlit.h, img.getHeight() - sy);
        long chave = (long) sx << 32 | sy;
        Integer c = CORES.computeIfAbsent(img, k -> new HashMap<>()).get(chave);
        if (c != null) return c;
        int melhor = 0xFFFFFF;
        if (w > 0 && h > 0) {
            int[] px = new int[w * h];
            img.getRGB(px, 0, w, sx, sy, w, h);
            int linhas = 0, brilho = -1;
            for (int j = 0; j < h && linhas < 2; j++) {
                boolean achou = false;
                for (int i = 0; i < w; i++) {
                    int p = px[j * w + i];
                    if (p >>> 24 < 128) continue;
                    achou = true;
                    int b = (p >> 16 & 255) + (p >> 8 & 255) + (p & 255);
                    if (b > brilho) {
                        brilho = b;
                        melhor = p & 0xFFFFFF;
                    }
                }
                if (achou) linhas++;
            }
        }
        CORES.get(img).put(chave, melhor);
        return melhor;
    }
}
