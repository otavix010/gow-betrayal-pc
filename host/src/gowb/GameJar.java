package gowb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/** O .jar do jogo fornecido pelo usuário, lido inteiro para a memória. */
final class GameJar {
    /** Jar testado: God of War: Betrayal v0.1.95, inglês, 240x320. */
    static final String SHA256_CONHECIDO = "dce20b0b40e00f151f38f73ac788ac04174807add1974b398379fdbd19265366";
    /** Tabela de textos (RP10) do jar testado: só com ela a tradução é aplicada. */
    static final String SHA256_TEXTOS = "2a91aa1ef94bb1e8adb4b4b3f86c0d15dbc656891d31ebc412566dc8bfc6e84c";

    final Path path;
    final Map<String, byte[]> entries = new HashMap<>();
    final Attributes manifest;
    final String sha256;

    GameJar(Path path) throws IOException {
        this.path = path;
        byte[] all = Files.readAllBytes(path);
        sha256 = sha256(all);
        Manifest mf;
        try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(all))) {
            mf = in.getManifest();
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory()) entries.put(e.getName(), in.readAllBytes());
            }
        }
        manifest = mf == null ? new Attributes() : mf.getMainAttributes();
    }

    /** Classe principal (MIDlet-1: nome, ícone, classe). */
    String midletClass() {
        String m = manifest.getValue("MIDlet-1");
        if (m == null) return null;
        String[] p = m.split(",");
        return p.length >= 3 ? p[2].trim() : null;
    }

    /** null se for o God of War: Betrayal que o port conhece; senão, o motivo. */
    String problema() {
        if (!entries.containsKey("GOWMIDlet.class")) return "este .jar não é o God of War: Betrayal";
        for (String c : new String[] {"a.class", "c.class", "d.class", "RP10"})
            if (!entries.containsKey(c)) return "versão do God of War: Betrayal não suportada (falta " + c + ")";
        return null;
    }

    boolean conhecido() {
        return SHA256_CONHECIDO.equals(sha256);
    }

    String sha256Textos() {
        byte[] b = entries.get("RP10");
        return b == null ? "" : sha256(b);
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
