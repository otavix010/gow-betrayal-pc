import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/** Volume por janela e espectrograma de um WAV: java tools/Espectro.java entrada.wav saida.png */
public class Espectro {
    public static void main(String[] a) throws Exception {
        AudioInputStream in = AudioSystem.getAudioInputStream(new File(a[0]));
        AudioFormat f = in.getFormat();
        AudioInputStream pcm = AudioSystem.getAudioInputStream(new AudioFormat(f.getSampleRate(), 16, 1, true, false), in);
        byte[] b = pcm.readAllBytes();
        int n = b.length / 2;
        double[] s = new double[n];
        for (int i = 0; i < n; i++) s[i] = (short) ((b[2 * i] & 255) | b[2 * i + 1] << 8) / 32768.0;
        int sr = (int) f.getSampleRate();
        System.out.printf("%s: %d Hz, %.2f s%n", a[0], sr, n / (double) sr);
        int win = sr / 4;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + win <= n; i += win) {
            double e = 0;
            for (int j = i; j < i + win; j++) e += s[j] * s[j];
            double db = 10 * Math.log10(e / win + 1e-12);
            sb.append(String.format("%.0f ", db));
        }
        System.out.println("dBFS a cada 250 ms: " + sb);
        // espectrograma: FFT de 2048 pontos, salto de 512
        int N = 2048, hop = 512, cols = Math.max(1, (n - N) / hop), rows = 256;
        BufferedImage img = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB);
        for (int c = 0; c < cols; c++) {
            double[] re = new double[N], im = new double[N];
            for (int k = 0; k < N; k++) re[k] = s[c * hop + k] * (0.5 - 0.5 * Math.cos(2 * Math.PI * k / N));
            fft(re, im);
            for (int r = 0; r < rows; r++) {
                // eixo de frequência logarítmico de 40 Hz a 16 kHz
                double hz = 40 * Math.pow(16000 / 40.0, (double) r / rows);
                int bin = (int) Math.min(N / 2 - 1, hz * N / sr);
                double mag = Math.hypot(re[bin], im[bin]);
                double db = 20 * Math.log10(mag + 1e-9);
                float v = (float) Math.max(0, Math.min(1, (db + 40) / 70));
                img.setRGB(c, rows - 1 - r, Color.HSBtoRGB(0.7f - 0.7f * v, 1f, v));
            }
        }
        ImageIO.write(img, "png", new File(a[1]));
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            for (int i = 0; i < n; i += len)
                for (int k = 0; k < len / 2; k++) {
                    double wr = Math.cos(ang * k), wi = Math.sin(ang * k);
                    double ur = re[i + k], ui = im[i + k];
                    double vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi;
                    double vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr;
                    re[i + k] = ur + vr; im[i + k] = ui + vi;
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi;
                }
        }
    }
}
