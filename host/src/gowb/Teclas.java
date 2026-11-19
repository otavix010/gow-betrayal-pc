package gowb;

import static org.lwjgl.glfw.GLFW.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O mapa do teclado (e dos botões do mouse) nos botões do controle, configurável pela página Controles do launcher.
 *
 * <pre>
 * GOWB_TECLAS="id=nomes;id=nomes"  as teclas de cada botão (várias separadas por espaço; "id=" = nenhuma).
 *                                  Sem a variável, ou para um id que ela não traz, vale o padrão abaixo.
 * GOWB_TECLADO=0                   o teclado e o mouse ficam de fora (só o controle).
 * </pre>
 *
 * Os nomes são os do launcher, pela POSIÇÃO da tecla (a do teclado americano), como as constantes GLFW_KEY_*:
 * num teclado AZERTY, "a" é a tecla Q. mouse1..mouse5 são os botões do mouse.
 * Testes sem foco: GOWB_TESTE_TECLAS_ARQ=<arquivo> com os nomes das teclas seguradas ("z left"); o arquivo é
 * relido a cada 50 ms e cada mudança dos botões sai no log ("[teclado] botões ...").
 */
final class Teclas {
    /** id do botão, padrão (o mapa de antes desta opção). */
    static final String[][] PADRAO = {
        {"up", "up"}, {"down", "down"}, {"left", "left"}, {"right", "right"},
        {"a", "z space"}, {"b", "escape backspace"}, {"x", "x"}, {"y", "a"},
        {"lb", "c"}, {"rb", ""}, {"back", "s"}, {"start", "enter"},
    };

    private static final int MOUSE = 0x10000;
    private static final Map<String, Integer> NOMES = new HashMap<>();
    static {
        for (char c = 'a'; c <= 'z'; c++) NOMES.put(String.valueOf(c), GLFW_KEY_A + (c - 'a'));
        for (char c = '0'; c <= '9'; c++) NOMES.put(String.valueOf(c), GLFW_KEY_0 + (c - '0'));
        for (int i = 1; i <= 12; i++) NOMES.put("f" + i, GLFW_KEY_F1 + (i - 1));
        for (int i = 0; i <= 9; i++) NOMES.put("num" + i, GLFW_KEY_KP_0 + i);
        Object[][] outros = {
            {"escape", GLFW_KEY_ESCAPE}, {"esc", GLFW_KEY_ESCAPE}, {"enter", GLFW_KEY_ENTER}, {"space", GLFW_KEY_SPACE},
            {"tab", GLFW_KEY_TAB}, {"backspace", GLFW_KEY_BACKSPACE}, {"capslock", GLFW_KEY_CAPS_LOCK},
            {"lshift", GLFW_KEY_LEFT_SHIFT}, {"rshift", GLFW_KEY_RIGHT_SHIFT}, {"lctrl", GLFW_KEY_LEFT_CONTROL},
            {"rctrl", GLFW_KEY_RIGHT_CONTROL}, {"lalt", GLFW_KEY_LEFT_ALT}, {"ralt", GLFW_KEY_RIGHT_ALT},
            {"minus", GLFW_KEY_MINUS}, {"equals", GLFW_KEY_EQUAL}, {"lbracket", GLFW_KEY_LEFT_BRACKET},
            {"rbracket", GLFW_KEY_RIGHT_BRACKET}, {"semicolon", GLFW_KEY_SEMICOLON}, {"apostrophe", GLFW_KEY_APOSTROPHE},
            {"grave", GLFW_KEY_GRAVE_ACCENT}, {"backslash", GLFW_KEY_BACKSLASH}, {"comma", GLFW_KEY_COMMA},
            {"period", GLFW_KEY_PERIOD}, {"slash", GLFW_KEY_SLASH}, {"iso", GLFW_KEY_WORLD_2}, {"abnt", GLFW_KEY_WORLD_1},
            {"up", GLFW_KEY_UP}, {"down", GLFW_KEY_DOWN}, {"left", GLFW_KEY_LEFT}, {"right", GLFW_KEY_RIGHT},
            {"home", GLFW_KEY_HOME}, {"end", GLFW_KEY_END}, {"pageup", GLFW_KEY_PAGE_UP}, {"pagedown", GLFW_KEY_PAGE_DOWN},
            {"insert", GLFW_KEY_INSERT}, {"delete", GLFW_KEY_DELETE},
            {"nummul", GLFW_KEY_KP_MULTIPLY}, {"numdiv", GLFW_KEY_KP_DIVIDE}, {"numadd", GLFW_KEY_KP_ADD},
            {"numsub", GLFW_KEY_KP_SUBTRACT}, {"numdec", GLFW_KEY_KP_DECIMAL}, {"numenter", GLFW_KEY_KP_ENTER},
            {"mouse1", MOUSE | GLFW_MOUSE_BUTTON_LEFT}, {"mouse2", MOUSE | GLFW_MOUSE_BUTTON_RIGHT},
            {"mouse3", MOUSE | GLFW_MOUSE_BUTTON_MIDDLE}, {"mouse4", MOUSE | GLFW_MOUSE_BUTTON_4},
            {"mouse5", MOUSE | GLFW_MOUSE_BUTTON_5},
        };
        for (Object[] o : outros) NOMES.put((String) o[0], (Integer) o[1]);
    }

    private final long window;
    private final boolean ligado;
    private final Map<String, int[]> mapa = new HashMap<>();
    private final java.nio.file.Path teste;
    private final java.util.Set<Integer> testeSeg = new java.util.HashSet<>();
    private long testeLido;

    Teclas(long window) {
        this.window = window;
        String t = System.getenv("GOWB_TECLADO");
        ligado = !(t != null && t.trim().equals("0"));
        for (String[] p : PADRAO) mapa.put(p[0], codigos(p[0], p[1]));
        String e = System.getenv("GOWB_TECLAS");
        int trocados = 0;
        if (e != null) {
            for (String item : e.split(";")) {
                int igual = item.indexOf('=');
                if (igual <= 0) continue;
                String id = item.substring(0, igual).trim().toLowerCase();
                if (!mapa.containsKey(id)) {
                    System.out.println("[teclado] GOWB_TECLAS: nenhum botão se chama \"" + id + "\"");
                    continue;
                }
                mapa.put(id, codigos(id, item.substring(igual + 1)));
                trocados++;
            }
        }
        String ta = System.getenv("GOWB_TESTE_TECLAS_ARQ");
        teste = ta != null && !ta.isBlank() ? java.nio.file.Paths.get(ta) : null;
        System.out.println("[teclado] " + (ligado ? "mapa " + (trocados > 0 ? "do launcher (" + trocados + " botões)" : "padrão") : "desligado (GOWB_TECLADO=0)"));
    }

    private static int[] codigos(String id, String nomes) {
        List<Integer> l = new ArrayList<>();
        for (String n : nomes.trim().toLowerCase().split("\\s+")) {
            if (n.isEmpty()) continue;
            Integer c = NOMES.get(n);
            if (c == null) System.out.println("[teclado] nenhuma tecla se chama \"" + n + "\" (botão " + id + ")");
            else l.add(c);
        }
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    boolean ligado() {
        return ligado;
    }

    boolean teste() {
        return teste != null;
    }

    private void leTeste() {
        long agora = System.nanoTime();
        if (agora - testeLido < 50_000_000L) return;
        testeLido = agora;
        try {
            String txt = java.nio.file.Files.readString(teste).trim().toLowerCase();
            testeSeg.clear();
            for (String n : txt.split("\\s+")) { Integer c = NOMES.get(n); if (c != null) testeSeg.add(c); }
        } catch (java.io.IOException e) {
            testeSeg.clear();
        }
    }

    private boolean apertada(int c) {
        if (teste != null) { leTeste(); return testeSeg.contains(c); }
        return (c & MOUSE) != 0 ? glfwGetMouseButton(window, c & 0xFF) == GLFW_PRESS : glfwGetKey(window, c) == GLFW_PRESS;
    }

    /** Alguma tecla (ou botão do mouse) do botão id está apertada. */
    boolean seg(String id) {
        if (!ligado) return false;
        int[] cs = mapa.get(id);
        if (cs == null) return false;
        for (int c : cs) if (apertada(c)) return true;
        return false;
    }

    /** Uma tecla fixa (as do teclado do celular), também desligada com GOWB_TECLADO=0. */
    boolean fixa(int glfwKey) {
        return ligado && apertada(glfwKey);
    }
}
