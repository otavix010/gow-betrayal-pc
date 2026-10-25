import java.io.*;
import java.nio.file.*;

/** Extrai os PNGs embutidos nos pacotes RP: java tools/ExtractPng.java pasta_saida RP1 RP2 ... */
public class ExtractPng {
    public static void main(String[] a) throws Exception {
        Path out = Paths.get(a[0]);
        Files.createDirectories(out);
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G'};
        for (int k = 1; k < a.length; k++) {
            byte[] d = Files.readAllBytes(Paths.get(a[k]));
            int n = 0;
            for (int i = 0; i + 8 < d.length; i++) {
                if (d[i] != sig[0] || d[i + 1] != sig[1] || d[i + 2] != sig[2] || d[i + 3] != sig[3]) continue;
                // procura o IEND
                for (int j = i + 8; j + 8 <= d.length; j++)
                    if (d[j] == 'I' && d[j + 1] == 'E' && d[j + 2] == 'N' && d[j + 3] == 'D') {
                        Files.write(out.resolve(Paths.get(a[k]).getFileName() + "_" + (n++) + ".png"), java.util.Arrays.copyOfRange(d, i, j + 8));
                        i = j;
                        break;
                    }
            }
        }
    }
}
