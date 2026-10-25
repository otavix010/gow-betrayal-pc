import java.awt.*;
import java.awt.image.*;
import java.io.File;
import javax.imageio.ImageIO;

/** Gera as artes da Steam a partir da tela de título e do logo do jogo. */
public class Arte {
    static BufferedImage titulo, logo, icone;

    public static void main(String[] a) throws Exception {
        BufferedImage f = ImageIO.read(new File(a[0]));
        titulo = copia(f.getSubimage(0, 0, 240, 306));
        Graphics2D t = titulo.createGraphics();
        t.setColor(Color.BLACK);
        t.fillRect(0, 90, 176, 24); // apaga o "Play Game" do menu
        t.fillRect(176, 90, 30, 14);
        t.dispose();
        BufferedImage arte = ImageIO.read(new File(a[1]));
        logo = arte.getSubimage(125, 65, 240, 52);
        icone = ImageIO.read(new File(a[2]));
        String o = a[3];
        ImageIO.write(capa(600, 900), "png", new File(o, "p.png"));
        ImageIO.write(faixa(920, 430), "png", new File(o, "horizontal.png"));
        ImageIO.write(faixa(1920, 620), "png", new File(o, "hero.png"));
        ImageIO.write(escala(logo, 3), "png", new File(o, "logo.png"));
        ImageIO.write(escala(icone, 8), "png", new File(o, "icon.png"));
    }

    static BufferedImage copia(BufferedImage s) {
        BufferedImage d = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_RGB);
        d.createGraphics().drawImage(s, 0, 0, null);
        return d;
    }

    static BufferedImage escala(BufferedImage s, int k) {
        BufferedImage d = new BufferedImage(s.getWidth() * k, s.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = d.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(s, 0, 0, d.getWidth(), d.getHeight(), null);
        g.dispose();
        return d;
    }

    /** Fundo: a própria tela de título ampliada para cobrir, desfocada e escurecida. */
    static void fundo(Graphics2D g, int w, int h) {
        double k = Math.max(w / 240.0, h / 306.0);
        int sw = (int) (240 * k), sh = (int) (306 * k);
        BufferedImage peq = new BufferedImage(24, 31, BufferedImage.TYPE_INT_RGB);
        Graphics2D p = peq.createGraphics();
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        p.drawImage(titulo, 0, 0, 24, 31, null);
        p.dispose();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(peq, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, w, h);
    }

    static BufferedImage capa(int w, int h) {
        BufferedImage d = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = d.createGraphics();
        fundo(g, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int k = w / 240; // 2x
        int gw = 240 * k * 5 / 4 / 1, gh = 306 * k * 5 / 4;
        gw = 600; gh = 765;
        g.drawImage(titulo, 0, (h - gh) / 2, gw, gh, null);
        g.dispose();
        return d;
    }

    static BufferedImage faixa(int w, int h) {
        BufferedImage d = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = d.createGraphics();
        fundo(g, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int gh = h, gw = 240 * gh / 306;
        g.drawImage(titulo, (w - gw) / 2, 0, gw, gh, null);
        g.dispose();
        return d;
    }
}
