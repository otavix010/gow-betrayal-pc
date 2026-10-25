package gowb;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Tela do próprio app (antes do jogo carregar), desenhada na resolução do celular. */
final class Mensagem {
    private Mensagem() {}

    static void semJogo(String erro) {
        List<String> l = new ArrayList<>(List.of("GOD OF WAR", "BETRAYAL", "", "port nativo", "", "Coloque o arquivo .jar do jogo",
                "na pasta \"jogo\" do app", "ou arraste o arquivo", "para esta janela.", "", "Versão suportada:", "v0.1.95, inglês, 240x320"));
        if (erro != null) {
            l.add("");
            l.add("Erro:");
            l.addAll(quebra(erro, 34));
        }
        mostra(l);
    }

    private static List<String> quebra(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            if (cur.length() + w.length() + 1 > max && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static void mostra(List<String> linhas) {
        int w = Platform.width, h = Platform.height;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        int y = 34;
        for (int i = 0; i < linhas.size(); i++) {
            String s = linhas.get(i);
            boolean titulo = i < 2;
            Font f = new Font(Font.SERIF, Font.BOLD, titulo ? 22 : 11);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(titulo ? new Color(0xE0, 0x50, 0x10) : s.startsWith("Erro") ? new Color(0xFF, 0x70, 0x70) : new Color(0xDD, 0xDD, 0xDD));
            g.drawString(s, (w - fm.stringWidth(s)) / 2, y);
            y += s.isEmpty() ? 8 : fm.getHeight();
        }
        g.dispose();
        Platform.present(img.getRGB(0, 0, w, h, null, 0, w), w, h, false);
    }
}
