package gowb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.microedition.lcdui.Graphics;

/** Funções chamadas pelo código do jogo depois dos ajustes do Patcher. */
public final class Hooks {
    private Hooks() {}

    static GameLoader loader;
    private static volatile Map<Integer, String> traducao;
    private static char acentoPendente;

    // ---- recursos ----

    public static InputStream resource(Class<?> c, String name) {
        InputStream in = loader == null ? null : loader.resource(name);
        return in != null ? in : c.getResourceAsStream(name);
    }

    // ---- tradução ----

    /** Carrega traducao/&lt;idioma&gt;.txt do app (linhas "tabela|índice|texto", com \n e \\). */
    static boolean carregaTraducao(String idioma) throws IOException {
        InputStream in = Hooks.class.getResourceAsStream("/traducao/" + idioma + ".txt");
        if (in == null) return false;
        Map<Integer, String> m = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", 3);
                if (p.length < 3 || !p[0].equals("0")) continue;
                m.put(Integer.parseInt(p[1]), desescapa(p[2]));
            }
        }
        traducao = m;
        return true;
    }

    private static String desescapa(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String texto(int indice) {
        Map<Integer, String> m = traducao;
        return m == null ? null : m.get(indice);
    }

    // ---- acentos ----

    public static char glifo(char c) {
        char b = Acentos.base(c);
        acentoPendente = b != c ? c : 0;
        return b;
    }

    private static final Map<Class<?>, MethodHandle> DESENHO = new HashMap<>();

    public static void desenhaGlifo(Object glifo, Graphics g, int x, int y, int flags) {
        char acento = acentoPendente;
        acentoPendente = 0;
        int antes = UltimoBlit.contador;
        try {
            desenho(glifo.getClass()).invoke(glifo, g, x, y, flags);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        if (acento != 0 && UltimoBlit.contador != antes) Acentos.desenha(g, acento);
    }

    /** O método de desenho do glifo: o único público com (Graphics, int, int, int) e retorno void. */
    private static synchronized MethodHandle desenho(Class<?> c) throws ReflectiveOperationException {
        MethodHandle h = DESENHO.get(c);
        if (h != null) return h;
        Class<?>[] sig = {Graphics.class, int.class, int.class, int.class};
        for (Method m : c.getMethods()) {
            if (m.getReturnType() == void.class && java.util.Arrays.equals(m.getParameterTypes(), sig)) {
                h = MethodHandles.publicLookup().unreflect(m);
                DESENHO.put(c, h);
                return h;
            }
        }
        throw new NoSuchMethodException(c + ".(Graphics,int,int,int)");
    }

    // ---- estado do jogo (para o mapeamento do controle) ----

    private static Field estado;

    /** Tela atual do jogo (a.p): 79 áudio, 80 menu, 100 jogando, 101 diálogo, 105 pausa... -1 se indisponível. */
    static int estado() {
        try {
            if (estado == null) {
                if (loader == null) return -1;
                for (Field f : loader.loadClass("a").getDeclaredFields())
                    if (f.getName().equals("p") && f.getType() == int.class && Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        estado = f;
                    }
                if (estado == null) return -1;
            }
            return estado.getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
