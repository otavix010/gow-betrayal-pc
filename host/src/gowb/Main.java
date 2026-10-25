package gowb;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.microedition.midlet.MIDlet;

/**
 * Ponto de entrada do port.
 *
 * <pre>
 *   java -jar gowb.jar                         janela (Legion/PC)
 *   java -jar gowb.jar --headless --frames 600 --dump out --dump-every 60 --input teclas.txt
 * </pre>
 */
public final class Main {
    private Main() {}

    /** Idioma da tradução ("pt-BR") ou "en" para o texto original. */
    static String idioma = "pt-BR";
    /** --fps N: ritmo por relógio (N quadros por segundo) em vez de seguir a tela. */
    static boolean fpsFixo;
    /** Widescreen automático pela proporção da tela (--largura N fixa; --largura 240 = original). */
    static boolean larguraAuto = true;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        boolean headless = false;
        long frames = 0, dumpEvery = 0;
        Set<Long> dumpAt = new HashSet<>();
        Path dumpDir = Paths.get("frames"), input = null, jogo = null;
        List<String> watches = new ArrayList<>();
        Platform.saveDir = Localizador.appDir().resolve("save");
        Platform.soundfont = Localizador.appDir().resolve("sons").resolve("GeneralUser-GS.sf2");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jogo" -> {
                    // vazio: procura sozinho (o launcher passa vazio quando ainda não sabe onde está o jogo)
                    String v = args[++i];
                    if (!v.isBlank()) jogo = Paths.get(v);
                }
                case "--idioma" -> idioma = args[++i];
                case "--watch" -> watches.add(args[++i]);
                case "--headless" -> headless = true;
                case "--frames" -> frames = Long.parseLong(args[++i]);
                case "--dump" -> dumpDir = Paths.get(args[++i]);
                case "--dump-every" -> dumpEvery = Long.parseLong(args[++i]);
                case "--dump-at" -> {
                    for (String s : args[++i].split(",")) dumpAt.add(Long.parseLong(s.trim()));
                }
                case "--input" -> input = Paths.get(args[++i]);
                case "--mute" -> Platform.audio = false;
                case "--largura" -> {
                    // "auto": a proporção da tela (o padrão); um número fixa a largura (240 = original)
                    String v = args[++i];
                    if (!v.equals("auto")) {
                        larguraAuto = false;
                        Platform.larguraJogo = Integer.parseInt(v);
                        Platform.width = Platform.larguraJogo;
                    }
                }
                case "--efeitos" -> Platform.efeitosRealcados = !args[++i].equals("original");
                case "--sem-musica-fases" -> Trilha.ligada = false;
                case "--volume-musica" -> Trilha.volume = Math.max(0, Math.min(127, Integer.parseInt(args[++i]) * 127 / 100));
                case "--save" -> Platform.saveDir = Paths.get(args[++i]);
                case "--frame-ms" -> Platform.frameMs = Integer.parseInt(args[++i]);
                case "--fps" -> {
                    Platform.frameMs = Math.max(1, 1000 / Integer.parseInt(args[++i]));
                    fpsFixo = true;
                }
                case "--soundfont" -> {
                    String s = args[++i];
                    Platform.soundfont = s.equals("nenhuma") ? null : Paths.get(s);
                }
                default -> i += Window.option(args, i);
            }
        }

        GameJar jar = jogo != null ? Localizador.abrir(jogo) : Localizador.encontrar();
        if (!headless) {
            Window.run(jar);
            return;
        }

        if (jar == null) {
            System.err.println("jogo não encontrado: use --jogo arquivo.jar");
            System.exit(2);
        }
        if (headless) {
            Platform.audio = false;
            Platform.virtualClock = true;
            Platform.syncKeys = true;
            List<long[]> script = input == null ? List.of() : readScript(input);
            Files.createDirectories(dumpDir);
            long total = frames, every = dumpEvery;
            Path dir = dumpDir;
            Watch watch = new Watch(watches);
            Platform.listener = (px, w, h, n) -> {
                if (!watch.isEmpty()) watch.poll(n);
                for (long[] ev : script) if (ev[0] == n) Platform.key((int) ev[2], ev[1] == 1);
                if ((every > 0 && n % every == 0) || dumpAt.contains(n)) dump(px, w, h, dir.resolve(String.format("f%05d.png", n)));
                if (total > 0 && n >= total) Platform.exit();
            };
        }

        String erro = iniciar(jar);
        if (erro != null) {
            System.err.println(erro);
            System.exit(2);
        }
        Thread.currentThread().join();
    }

    static volatile MIDlet midlet;
    private static final java.util.concurrent.atomic.AtomicBoolean encerrado = new java.util.concurrent.atomic.AtomicBoolean();

    /** Avisa o jogo que vai fechar (uma vez só): ele grava o progresso. */
    static void encerrar() {
        MIDlet m = midlet;
        if (m == null || !encerrado.compareAndSet(false, true)) return;
        try {
            MIDlet.callDestroy(m);
        } catch (RuntimeException e) {
            // fechado antes de o jogo terminar de carregar: não há progresso a gravar
            System.out.println("[aviso] o jogo fechou antes de terminar de carregar: " + e);
        }
    }

    /** Carrega o jogo do jar (com os ajustes e a tradução) e chama startApp. Devolve o erro ou null. */
    static synchronized String iniciar(GameJar jar) {
        if (midlet != null) return null;
        String p = jar.problema();
        if (p != null) return p;
        if (!jar.conhecido()) System.out.println("[aviso] jar diferente do testado (sha256 " + jar.sha256 + "); tentando assim mesmo");
        if (Platform.audio) MidiPlayer.preparar();
        GameLoader loader = new GameLoader(jar);
        Hooks.loader = loader;
        if (!idioma.equals("en")) {
            try {
                if (!jar.sha256Textos().equals(GameJar.SHA256_TEXTOS))
                    System.out.println("[aviso] textos diferentes da versão testada: jogo fica em inglês");
                else if (!Hooks.carregaTraducao(idioma)) System.out.println("[aviso] sem tradução para " + idioma);
            } catch (IOException e) {
                System.out.println("[aviso] tradução com erro: " + e);
            }
        }
        try {
            String cls = jar.midletClass();
            MIDlet m = (MIDlet) loader.loadClass(cls == null ? "GOWMIDlet" : cls).getDeclaredConstructor().newInstance();
            midlet = m;
            Platform.onExit = () -> MIDlet.callPause(m);
            // fechar pela Steam (SIGTERM) ou pela janela: o jogo grava o progresso no destroyApp
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Platform.desligando = true;
                encerrar();
            }, "salvar-ao-sair"));
            Platform.events.execute(() -> {
                try {
                    MIDlet.callStart(m);
                } catch (Throwable t) {
                    t.printStackTrace();
                    Platform.exit();
                }
            });
            System.out.println("[jogo] " + jar.path + (jar.conhecido() ? " (versão testada)" : ""));
            return null;
        } catch (Throwable t) {
            t.printStackTrace();
            return "não foi possível iniciar o jogo: " + t;
        }
    }

    /** Linhas "quadro down|up|tap código" (tap = aperta no quadro e solta 3 quadros depois); # comenta. */
    static List<long[]> readScript(Path p) throws IOException {
        List<long[]> out = new ArrayList<>();
        for (String line : Files.readAllLines(p)) {
            line = line.replaceAll("#.*", "").trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            long f = Long.parseLong(t[0]);
            int code = Integer.parseInt(t[2]);
            switch (t[1]) {
                case "down" -> out.add(new long[] {f, 1, code});
                case "up" -> out.add(new long[] {f, 0, code});
                case "tap" -> {
                    out.add(new long[] {f, 1, code});
                    out.add(new long[] {f + 3, 0, code});
                }
                default -> throw new IOException("ação desconhecida: " + line);
            }
        }
        return out;
    }

    static void dump(int[] px, int w, int h, Path f) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            ImageIO.write(img, "png", f.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
