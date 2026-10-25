import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Junta PNGs numa grade: java tools/Montage.java saida.png colunas a.png b.png ... */
public class Montage {
    public static void main(String[] a) throws Exception {
        int cols = Integer.parseInt(a[1]), n = a.length - 2;
        BufferedImage first = ImageIO.read(new File(a[2]));
        int w = first.getWidth(), h = first.getHeight(), rows = (n + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * (w + 4), rows * (h + 16), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int i = 0; i < n; i++) {
            BufferedImage im = ImageIO.read(new File(a[i + 2]));
            int x = (i % cols) * (w + 4), y = (i / cols) * (h + 16);
            g.drawImage(im, x, y + 14, null);
            g.setColor(Color.WHITE);
            g.drawString(new File(a[i + 2]).getName(), x + 2, y + 11);
        }
        ImageIO.write(out, "png", new File(a[0]));
    }
}
