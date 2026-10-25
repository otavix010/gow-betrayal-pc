import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Lista os recursos de um pacote RP e despeja as tabelas de texto: java tools/Textos.java game/res/RP10 saida.txt */
public class Textos {
    public static void main(String[] a) throws Exception {
        DataInputStream in = new DataInputStream(new FileInputStream(a[0]));
        int flags = in.read();
        int sz = (1 + (flags & 1)) << 1;
        int n = in.readChar();
        int[] size = new int[n];
        for (int i = 0; i < n; i++) for (int b = sz - 1; b >= 0; b--) size[i] |= (in.read() & 255) << (b << 3);
        byte[] type = new byte[n];
        in.readFully(type);
        byte[] data = new byte[in.readInt()];
        in.readFully(data);
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[1]), StandardCharsets.UTF_8));
        int off = 0;
        for (int r = 0; r < n; r++) {
            System.out.println("recurso " + r + " tipo " + type[r] + " tamanho " + size[r]);
            try {
                DataInputStream s = new DataInputStream(new ByteArrayInputStream(data, off, size[r]));
                int cnt = s.readShort();
                int total = 2;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cnt; i++) {
                    int len = s.readShort();
                    byte[] b = new byte[len];
                    s.readFully(b);
                    total += 2 + len;
                    sb.append(r).append('|').append(i).append('|').append(new String(b, StandardCharsets.UTF_8).replace("\\", "\\\\").replace("\n", "\\n")).append('\n');
                }
                if (total == size[r]) {
                    out.print(sb);
                    System.out.println("  -> tabela de texto, " + cnt + " frases");
                }
            } catch (Exception e) {
            }
            off += size[r];
        }
        out.close();
    }
}
