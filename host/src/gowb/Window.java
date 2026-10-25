package gowb;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

import java.nio.IntBuffer;
import javax.microedition.midlet.MIDlet;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

/** Janela do port: tela do celular ampliada com filtro nítido e fundo "ambiente" desfocado nas laterais. */
final class Window {
    private Window() {}

    private static boolean windowed, integer, ambient = true, logFps;
    private static long fpsT0, fpsN0, ultLog;
    private static int ultW, ultH;
    private static String filter = "sharp";

    /** Opções da janela; devolve quantos argumentos extras consumiu. */
    static int option(String[] args, int i) {
        switch (args[i]) {
            case "--windowed" -> windowed = true;
            case "--integer" -> integer = true;
            case "--no-ambient" -> ambient = false;
            case "--log-fps" -> logFps = true;
            case "--filter" -> {
                filter = args[i + 1];
                return 1;
            }
            default -> throw new IllegalArgumentException("opção desconhecida: " + args[i]);
        }
        return 0;
    }

    private static final String VS = """
            #version 120
            attribute vec2 pos;
            attribute vec2 tc;
            varying vec2 uv;
            void main() { uv = tc; gl_Position = vec4(pos, 0.0, 1.0); }
            """;

    // "sharp bilinear": pixels nítidos, só a borda entre eles é suavizada (bom para escala fracionária)
    private static final String FS_GAME = """
            #version 120
            uniform sampler2D tex;
            uniform vec2 texSize;
            uniform float scale;
            uniform int mode;
            varying vec2 uv;
            void main() {
              if (mode == 1) { gl_FragColor = texture2D(tex, uv); return; }
              vec2 texel = uv * texSize;
              vec2 s = fract(texel);
              vec2 range = vec2(0.5 - 0.5 / scale);
              vec2 d = s - 0.5;
              vec2 f = (d - clamp(d, -range, range)) * scale + 0.5;
              gl_FragColor = texture2D(tex, (floor(texel) + f) / texSize);
            }
            """;

    // fundo: nível de mipmap bem baixo (desfoque barato), escurecido
    private static final String FS_BG = """
            #version 120
            uniform sampler2D tex;
            varying vec2 uv;
            void main() {
              vec3 c = vec3(0.0);
              for (int i = -2; i <= 2; i++)
                for (int j = -2; j <= 2; j++)
                  c += texture2D(tex, uv + vec2(i, j) * 0.02, 5.0).rgb;
              gl_FragColor = vec4(c / 25.0 * 0.35, 1.0);
            }
            """;

    static void run(GameJar jar) {
        if (!glfwInit()) throw new IllegalStateException("GLFW não iniciou");
        glfwDefaultWindowHints();
        long mon = glfwGetPrimaryMonitor();
        GLFWVidMode vm = glfwGetVideoMode(mon);
        int sw = vm.width(), sh = vm.height();
        StringBuilder modos = new StringBuilder();
        var lista = glfwGetVideoModes(mon);
        if (lista != null) for (int i = 0; i < lista.limit(); i++) modos.append(lista.get(i).width()).append('x').append(lista.get(i).height()).append('@').append(lista.get(i).refreshRate()).append(' ');
        System.out.println("[tela] monitor " + glfwGetMonitorName(mon) + " modo atual " + sw + "x" + sh + "@" + vm.refreshRate() + " | modos: " + modos);
        int ww = windowed ? Platform.width * 3 : sw, wh = windowed ? Platform.height * 3 : sh;
        if (!windowed) glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        // janela sem borda do tamanho da tela (não usa modo exclusivo: no gamescope ele cai para 1920x1080)
        long win = glfwCreateWindow(ww, wh, "God of War: Betrayal", 0, 0);
        if (win == 0) throw new IllegalStateException("não abriu a janela");
        if (!windowed) glfwSetWindowPos(win, 0, 0);
        glfwMakeContextCurrent(win);
        glfwSwapInterval(1);
        GL.createCapabilities();
        glfwSetInputMode(win, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);

        int pGame = program(VS, FS_GAME), pBg = program(VS, FS_BG);
        // widescreen: o jogo enxerga uma tela com a proporção do monitor (só na versão testada, cujos ajustes conhecemos)
        if (Main.larguraAuto && jar != null && jar.conhecido()) {
            int[] fw0 = new int[1], fh0 = new int[1];
            glfwGetFramebufferSize(win, fw0, fh0);
            int l = (int) Math.round(320.0 * fw0[0] / Math.max(1, fh0[0]) / 2) * 2;
            Platform.larguraJogo = Math.max(240, Math.min(640, l));
            Platform.width = Platform.larguraJogo;
            System.out.println("[tela] widescreen: jogo em " + Platform.width + "x" + Platform.height);
        }
        int tw = Platform.width, th = Platform.height;
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter.equals("nearest") ? GL_NEAREST : GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, tw, th, 0, GL_BGRA, GL_UNSIGNED_BYTE, (IntBuffer) null);

        int[] frame = new int[tw * th];
        IntBuffer up = BufferUtils.createIntBuffer(tw * th);
        long shown = 0;
        Input input = new Input(win);

        glfwSetWindowCloseCallback(win, w -> {
            sair();
        });

        // sem o jogo: tela pedindo o .jar, que pode ser arrastado para a janela
        // ritmo: 40 ms por quadro como no original, sincronizado com a tela quando ela tiver um múltiplo próximo
        Platform.ritmoTela = true;
        String erro = jar == null ? null : Main.iniciar(jar);
        if (jar == null || erro != null) Mensagem.semJogo(erro);
        glfwSetDropCallback(win, (w, count, names) -> {
            if (Main.midlet != null) return;
            for (int i = 0; i < count; i++) {
                java.nio.file.Path p = java.nio.file.Paths.get(org.lwjgl.glfw.GLFWDropCallback.getName(names, i));
                GameJar j = Localizador.abrir(p);
                String e = j == null ? "não consegui ler " + p.getFileName() : j.problema();
                if (e == null) {
                    try {
                        e = Main.iniciar(Localizador.guardar(j));
                    } catch (java.io.IOException ex) {
                        e = "não consegui copiar o arquivo: " + ex.getMessage();
                    }
                }
                if (e == null) return;
                Mensagem.semJogo(e);
            }
        });

        while (!glfwWindowShouldClose(win)) {
            glfwPollEvents();
            input.update();
            Trilha.atualiza();

            long n = Platform.latestFrame(frame);
            if (logFps && System.nanoTime() - fpsT0 > 5_000_000_000L) {
                if (fpsT0 != 0) System.out.printf("[fps] jogo %.1f%n", (n - fpsN0) / ((System.nanoTime() - fpsT0) / 1e9));
                fpsT0 = System.nanoTime();
                fpsN0 = n;
            }
            if (n != shown && n != 0) {
                shown = n;
                up.clear();
                up.put(frame).flip();
                glBindTexture(GL_TEXTURE_2D, tex);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, tw, th, GL_BGRA, GL_UNSIGNED_BYTE, up);
                glGenerateMipmap(GL_TEXTURE_2D);
            }

            int[] fw = new int[1], fh = new int[1];
            glfwGetFramebufferSize(win, fw, fh);
            if (fw[0] != ultW || fh[0] != ultH || logFps && System.nanoTime() - ultLog > 5_000_000_000L) {
                ultW = fw[0];
                ultH = fh[0];
                ultLog = System.nanoTime();
                System.out.printf("[tela] janela %dx%d, tela %.1f Hz, ritmo pela tela: %s%n", ultW, ultH, Platform.hzTela(), Platform.pacing);
            }
            glViewport(0, 0, fw[0], fh[0]);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);

            // widescreen: a tela de evoluir arma (estado 106) foi desenhada para 240 de largura: mostra só essa parte
            int vw = Platform.larguraJogo != 240 && Hooks.estado() == 106 ? 240 : tw;
            float ur = (float) vw / tw;
            double scale = Math.min((double) fw[0] / vw, (double) fh[0] / th);
            if (integer && scale >= 1) scale = Math.floor(scale);
            double gw = vw * scale, gh = th * scale;
            float x0 = (float) ((fw[0] - gw) / 2), y0 = (float) ((fh[0] - gh) / 2);

            if (ambient && gw < fw[0] - 2) {
                // fundo cobre a tela toda, mostrando a faixa central da imagem
                double cover = Math.max((double) fw[0] / vw, (double) fh[0] / th);
                double cu = fw[0] / (vw * cover) * ur, cv = fh[0] / (th * cover);
                glUseProgram(pBg);
                glUniform1i(glGetUniformLocation(pBg, "tex"), 0);
                quad(pBg, fw[0], fh[0], 0, 0, fw[0], fh[0], (float) (ur / 2 - cu / 2), (float) (0.5 - cv / 2), (float) (ur / 2 + cu / 2), (float) (0.5 + cv / 2));
            }

            glUseProgram(pGame);
            glUniform1i(glGetUniformLocation(pGame, "tex"), 0);
            glUniform2f(glGetUniformLocation(pGame, "texSize"), tw, th);
            glUniform1f(glGetUniformLocation(pGame, "scale"), (float) scale);
            glUniform1i(glGetUniformLocation(pGame, "mode"), filter.equals("smooth") ? 1 : 0);
            quad(pGame, fw[0], fh[0], x0, y0, (float) (x0 + gw), (float) (y0 + gh), 0, 0, ur, 1);

            glfwSwapBuffers(win);
            Platform.vsync();
        }
        sair();
    }

    private static void sair() {
        Main.encerrar();
        Platform.exit();
    }

    /** Desenha um retângulo em pixels da tela (origem no canto superior esquerdo). */
    private static void quad(int prog, int fw, int fh, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        float ax = x0 / fw * 2 - 1, bx = x1 / fw * 2 - 1;
        float ay = 1 - y0 / fh * 2, by = 1 - y1 / fh * 2;
        float[] pos = {ax, ay, bx, ay, bx, by, ax, by};
        float[] tc = {u0, v0, u1, v0, u1, v1, u0, v1};
        int lp = glGetAttribLocation(prog, "pos"), lt = glGetAttribLocation(prog, "tc");
        glEnableVertexAttribArray(lp);
        glEnableVertexAttribArray(lt);
        glVertexAttribPointer(lp, 2, GL_FLOAT, false, 0, BufferUtils.createFloatBuffer(8).put(pos).flip());
        glVertexAttribPointer(lt, 2, GL_FLOAT, false, 0, BufferUtils.createFloatBuffer(8).put(tc).flip());
        glDrawArrays(GL_QUADS, 0, 4);
        glDisableVertexAttribArray(lp);
        glDisableVertexAttribArray(lt);
    }

    private static int program(String vs, String fs) {
        int p = glCreateProgram();
        for (int[] s : new int[][] {{GL_VERTEX_SHADER}, {GL_FRAGMENT_SHADER}}) {
            int sh = glCreateShader(s[0]);
            glShaderSource(sh, s[0] == GL_VERTEX_SHADER ? vs : fs);
            glCompileShader(sh);
            if (glGetShaderi(sh, GL_COMPILE_STATUS) == 0) throw new IllegalStateException(glGetShaderInfoLog(sh));
            glAttachShader(p, sh);
        }
        glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) throw new IllegalStateException(glGetProgramInfoLog(p));
        return p;
    }
}
