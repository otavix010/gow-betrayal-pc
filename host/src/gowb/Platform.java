package gowb;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

/** Ponte entre a camada MIDP (o "celular") e o frontend (janela ou modo de teste). */
public final class Platform {
    private Platform() {}

    /** Resolução lógica da tela do celular. */
    public static int width = 240, height = 320;
    /** Largura que o jogo enxerga (240 no original; maior = widescreen, ver Patcher.LarguraHook). */
    public static int larguraJogo = 240;
    /** Onde ficam os saves (RecordStore). */
    public static Path saveDir = Paths.get("save");
    public static boolean audio = true;
    /** Sobe as notas graves demais dos efeitos MIDI (ver Realce). */
    public static boolean efeitosRealcados = true;
    /** SoundFont (.sf2) opcional para a música MIDI; null = banco interno do Java. */
    public static Path soundfont;
    /**
     * Tempo de quadro do motor: 40 ms = 25 fps, como no original. A lógica do jogo arredonda contas com o tempo do
     * quadro (velocidade × tempo >> 8 etc.), então quadros mais curtos mudam a física; abaixo de 16 ms o jogo trava.
     */
    public static int frameMs = 40;

    public interface FrameListener {
        /** Chamado na thread do jogo a cada quadro apresentado. */
        void onFrame(int[] argb, int w, int h, long frameNo);
    }

    public static volatile FrameListener listener;
    public static volatile Runnable onExit;

    private static final Object frameLock = new Object();
    private static int[] front = new int[0];
    private static int frontW, frontH;
    private static long frameNo;

    /** Fila única de eventos (teclas, repaint, callSerially), como a thread de eventos do MIDP. */
    public static final ExecutorService events = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "midp-eventos");
        t.setDaemon(true);
        return t;
    });

    public static void present(int[] pixels, int w, int h) {
        present(pixels, w, h, true);
    }

    /** ritmo=false: telas do próprio app, que não devem esperar o vsync. */
    public static void present(int[] pixels, int w, int h, boolean ritmo) {
        long n;
        synchronized (frameLock) {
            if (front.length != w * h) front = new int[w * h];
            System.arraycopy(pixels, 0, front, 0, w * h);
            frontW = w;
            frontH = h;
            n = ++frameNo;
        }
        FrameListener l = listener;
        if (l != null) l.onFrame(pixels, w, h, n);
        if (pacing && ritmo) esperaVsync();
    }

    // ---- ritmo pela tela ----

    /**
     * Com a janela aberta, cada quadro do jogo pode esperar um número inteiro de atualizações da tela, para o
     * movimento ficar liso: em 120 Hz, 5 atualizações = 41,7 ms (24 fps). O motor mede o tempo real entre quadros,
     * então a velocidade continua certa. Se a tela não tiver um múltiplo perto de frameMs (60 Hz: 3 × 16,7 = 50 ms),
     * o ritmo fica pelo relógio (sleep de frameMs).
     */
    public static volatile boolean ritmoTela;
    /** true enquanto o ritmo estiver pela tela (decidido a partir da frequência medida). */
    public static volatile boolean pacing;
    private static volatile int vsyncDivisor = 1;
    private static final Object vsyncLock = new Object();
    private static long vsyncTick, vsyncVisto, vsyncUltimo;
    private static double vsyncMedia = 16.7;
    private static int vsyncAmostras;

    /** Chamado pela janela depois de cada troca de buffer; mede a frequência da tela. */
    public static void vsync() {
        long t = System.nanoTime();
        synchronized (vsyncLock) {
            if (vsyncUltimo != 0) {
                double ms = (t - vsyncUltimo) / 1e6;
                if (ms > 2 && ms < 100) {
                    vsyncMedia = vsyncAmostras < 10 ? ms : vsyncMedia * 0.95 + ms * 0.05;
                    vsyncAmostras++;
                }
                int d = Math.max(1, (int) Math.ceil((frameMs - 0.5) / vsyncMedia));
                vsyncDivisor = d;
                pacing = ritmoTela && vsyncAmostras >= 30 && d * vsyncMedia <= frameMs * 1.15;
            }
            vsyncUltimo = t;
            vsyncTick++;
            vsyncLock.notifyAll();
        }
    }

    /** Frequência medida da tela (Hz), para o log. */
    public static double hzTela() {
        return 1000 / vsyncMedia;
    }

    /** O sleep do laço do motor: com o ritmo pela tela, quem segura o quadro é o vsync. */
    public static void sleepQuadro(long ms) throws InterruptedException {
        if (pacing && ms <= frameMs) return;
        sleep(ms);
    }

    private static void esperaVsync() {
        synchronized (vsyncLock) {
            long limite = System.nanoTime() + 100_000_000L;
            while (vsyncTick < vsyncVisto + vsyncDivisor) {
                long falta = limite - System.nanoTime();
                if (falta <= 0) break;
                try {
                    vsyncLock.wait(Math.max(1, falta / 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            vsyncVisto = vsyncTick;
        }
    }

    /** Copia o último quadro para dst (tamanho w*h) e devolve o número dele (0 = nenhum ainda). */
    public static long latestFrame(int[] dst) {
        synchronized (frameLock) {
            if (frameNo == 0 || dst.length < front.length) return 0;
            System.arraycopy(front, 0, dst, 0, front.length);
            return frameNo;
        }
    }

    public static long frameCount() {
        synchronized (frameLock) {
            return frameNo;
        }
    }

    /** Tecla do celular (códigos MIDP: -1 cima, -2 baixo, -3 esq., -4 dir., -5 OK, -6/-7 soft keys, 48..57 números, 42 *, 35 #). */
    public static void key(int code, boolean down) {
        Runnable r = () -> {
            Displayable d = Display.current();
            if (d instanceof Canvas c) {
                if (down) c.dispatchKeyPressed(code);
                else c.dispatchKeyReleased(code);
            }
        };
        if (syncKeys) r.run();
        else events.execute(r);
    }

    // ---- relógio ----

    /** Modo de teste: tempo virtual que só anda quando o jogo dorme (rápido e repetível). */
    public static volatile boolean virtualClock;
    private static final java.util.concurrent.atomic.AtomicLong vclock = new java.util.concurrent.atomic.AtomicLong(1_000_000);

    public static long now() {
        return virtualClock ? vclock.get() : System.currentTimeMillis();
    }

    public static void sleep(long ms) throws InterruptedException {
        if (!virtualClock) {
            Thread.sleep(ms);
            return;
        }
        vclock.addAndGet(Math.max(ms, 0));
        Thread.yield();
    }

    /** No modo de teste as teclas entram na hora, na thread do jogo, para o resultado ser repetível. */
    public static volatile boolean syncKeys;

    public static InputStream resource(String name) {
        if (!name.startsWith("/")) name = "/" + name;
        return Platform.class.getResourceAsStream(name);
    }

    /** true quando o app já está fechando (ex.: SIGTERM da Steam): aí ninguém pode chamar System.exit de novo. */
    public static volatile boolean desligando;

    public static void exit() {
        // o jogo chama notifyDestroyed dentro do destroyApp; durante o desligamento, System.exit travaria o processo
        if (desligando) return;
        desligando = true;
        Runnable r = onExit;
        if (r != null) r.run();
        System.exit(0);
    }
}
