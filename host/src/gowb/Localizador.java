package gowb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Encontra o .jar do jogo: pasta jogo/ do app, último usado, e pastas comuns (roms do EmuDeck, Downloads...). */
final class Localizador {
    private Localizador() {}

    static Path appDir() {
        try {
            Path p = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }

    static Path pastaJogo() {
        return appDir().resolve("jogo");
    }

    /** O primeiro jar válido encontrado, ou null. */
    static GameJar encontrar() {
        String home = System.getProperty("user.home");
        List<Path> pastas = new ArrayList<>(List.of(pastaJogo(), appDir()));
        for (String[] p : new String[][] {{"Emulation", "roms", "j2me"}, {"Downloads"}, {"Desktop"}, {"Área de Trabalho"}}) {
            try {
                pastas.add(Paths.get(home, p));
            } catch (java.nio.file.InvalidPathException e) {
                // nome com acento num sistema sem locale UTF-8: pula
            }
        }
        for (Path d : pastas) {
            for (Path f : jars(d)) {
                GameJar j = abrir(f);
                if (j != null && j.problema() == null) return j;
            }
        }
        return null;
    }

    private static List<Path> jars(Path d) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(d)) return out;
        try (Stream<Path> s = Files.list(d)) {
            s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().equals("gowb.jar"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            // pasta sem acesso: ignora
        }
        return out;
    }

    /** Um jar, ou o primeiro jar válido de uma pasta. */
    static GameJar abrir(Path f) {
        if (Files.isDirectory(f)) {
            for (Path j : jars(f)) {
                GameJar g = abrir(j);
                if (g != null && g.problema() == null) return g;
            }
            return null;
        }
        try {
            if (Files.size(f) > 16 << 20) return null;
            return new GameJar(f);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Guarda uma cópia do jar na pasta jogo/ do app, para não depender do arquivo original. */
    static GameJar guardar(GameJar j) throws IOException {
        Path destino = pastaJogo().resolve("God of War Betrayal.jar");
        if (j.path.toAbsolutePath().equals(destino.toAbsolutePath())) return j;
        Files.createDirectories(pastaJogo());
        Files.copy(j.path, destino, StandardCopyOption.REPLACE_EXISTING);
        return new GameJar(destino);
    }
}
