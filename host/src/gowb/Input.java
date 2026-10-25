package gowb;

import static org.lwjgl.glfw.GLFW.*;

import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Controle (e teclado) -> teclas do celular, com mapeamento que muda conforme a tela do jogo.
 *
 * <pre>
 * Jogando:  direcional/analógico andar | A pular | X atacar | B, LB, LT defender/agarrar
 *           Y trocar arma/magia (soft key esq.) | RB, RT atacar | Select menu de evolução (#) | Start pausa
 * Menus:    direcional navegar | A, Start confirmar | B voltar
 * Teclado:  setas | Z pular/confirmar | X atacar | C defender | A troca (*) | S evolução (#)
 *           Enter pausa/confirmar | Esc voltar | numpad e Q/W (soft keys) direto
 * </pre>
 */
final class Input {
    // teclas do celular (códigos MIDP)
    static final int UP = -1, DOWN = -2, LEFT = -3, RIGHT = -4, FIRE = -5, LSK = -6, RSK = -7;
    static final int STAR = 42, POUND = 35;

    // estados do jogo (a.p) vistos nos testes
    private static final int ST_JOGANDO = 100;

    private final long window;
    private final GLFWGamepadState pad = GLFWGamepadState.create();
    private final boolean[] held = new boolean[256];
    private String lastSeen = "";

    Input(long window) {
        this.window = window;
    }

    /** 102 = menu de pausa, 105 = animação de entrada da pausa. */
    private static boolean pausa(int st) {
        return st == 102 || st == 105;
    }

    private int gameState() {
        return Hooks.estado();
    }

    private boolean key(int k) {
        return glfwGetKey(window, k) == GLFW_PRESS;
    }

    // estado somado de todos os controles conectados (qualquer um controla o jogo)
    private final boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
    private final float[] axes = new float[GLFW_GAMEPAD_AXIS_LAST + 1];

    private boolean btn(boolean has, int b) {
        return has && buttons[b];
    }

    private float axis(boolean has, int a) {
        return has ? axes[a] : 0f;
    }

    void update() {
        boolean has = false;
        java.util.Arrays.fill(buttons, false);
        java.util.Arrays.fill(axes, 0f);
        axes[GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] = axes[GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] = -1f;
        StringBuilder seen = new StringBuilder();
        for (int j = GLFW_JOYSTICK_1; j <= GLFW_JOYSTICK_LAST; j++) {
            if (!glfwJoystickPresent(j)) continue;
            seen.append(j).append(':').append(glfwGetJoystickName(j)).append(glfwJoystickIsGamepad(j) ? " [pad]" : " [sem mapa " + glfwGetJoystickGUID(j) + "]").append("; ");
            if (!glfwJoystickIsGamepad(j) || !glfwGetGamepadState(j, pad)) continue;
            has = true;
            for (int b = 0; b < buttons.length; b++) buttons[b] |= pad.buttons(b) == GLFW_PRESS;
            for (int a = 0; a < axes.length; a++) {
                float v = pad.axes(a);
                boolean trigger = a == GLFW_GAMEPAD_AXIS_LEFT_TRIGGER || a == GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER;
                if (trigger ? v > axes[a] : Math.abs(v) > Math.abs(axes[a])) axes[a] = v;
            }
        }
        if (!seen.toString().equals(lastSeen)) {
            lastSeen = seen.toString();
            System.out.println("[controles] " + (lastSeen.isEmpty() ? "nenhum" : lastSeen));
        }

        float lx = axis(has, GLFW_GAMEPAD_AXIS_LEFT_X), ly = axis(has, GLFW_GAMEPAD_AXIS_LEFT_Y);
        boolean lt = axis(has, GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.3f;
        boolean rt = axis(has, GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.3f;

        boolean left = btn(has, GLFW_GAMEPAD_BUTTON_DPAD_LEFT) || lx < -0.5f || key(GLFW_KEY_LEFT);
        boolean right = btn(has, GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) || lx > 0.5f || key(GLFW_KEY_RIGHT);
        boolean dUp = btn(has, GLFW_GAMEPAD_BUTTON_DPAD_UP) || key(GLFW_KEY_UP);
        boolean dDown = btn(has, GLFW_GAMEPAD_BUTTON_DPAD_DOWN) || key(GLFW_KEY_DOWN);
        boolean a = btn(has, GLFW_GAMEPAD_BUTTON_A) || key(GLFW_KEY_Z) || key(GLFW_KEY_SPACE);
        boolean b = btn(has, GLFW_GAMEPAD_BUTTON_B) || key(GLFW_KEY_ESCAPE) || key(GLFW_KEY_BACKSPACE);
        boolean x = btn(has, GLFW_GAMEPAD_BUTTON_X) || key(GLFW_KEY_X);
        boolean y = btn(has, GLFW_GAMEPAD_BUTTON_Y) || key(GLFW_KEY_A);
        boolean lb = btn(has, GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) || lt || key(GLFW_KEY_C);
        boolean rb = btn(has, GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) || rt;
        boolean back = btn(has, GLFW_GAMEPAD_BUTTON_BACK) || key(GLFW_KEY_S);
        boolean start = btn(has, GLFW_GAMEPAD_BUTTON_START) || key(GLFW_KEY_ENTER);

        boolean[] want = new boolean[256];
        int st = gameState();
        if (st == ST_JOGANDO) {
            set(want, LEFT, left);
            set(want, RIGHT, right);
            set(want, UP, a || dUp);
            set(want, DOWN, b || lb || dDown || ly > 0.7f);
            set(want, FIRE, x || rb);
            set(want, LSK, y); // soft key esquerda = "Arma" (troca arma/magia; os tutoriais pedem ela)
            set(want, POUND, back);
            set(want, RSK, start);
        } else {
            set(want, LEFT, left);
            set(want, RIGHT, right);
            set(want, UP, dUp || ly < -0.6f);
            set(want, DOWN, dDown || ly > 0.6f);
            // nas telas com "Yes/No", "Select", "Continue" quem confirma é a soft key esquerda
            set(want, LSK, a || y || (start && !pausa(st)));
            set(want, FIRE, x);
            // na pausa o Start volta ao jogo ("Resume" é a soft key direita)
            set(want, RSK, b || (start && pausa(st)));
        }
        // teclas diretas do celular no teclado
        for (int i = 0; i <= 9; i++) if (key(GLFW_KEY_KP_0 + i)) set(want, 48 + i, true);
        if (key(GLFW_KEY_KP_MULTIPLY)) set(want, STAR, true);
        if (key(GLFW_KEY_KP_DIVIDE)) set(want, POUND, true);
        if (key(GLFW_KEY_Q)) set(want, LSK, true);
        if (key(GLFW_KEY_W)) set(want, RSK, true);

        // solta primeiro, depois aperta (o jogo trata uma tecla por vez)
        for (int i = 0; i < 256; i++)
            if (held[i] && !want[i]) {
                held[i] = false;
                Platform.key(code(i), false);
            }
        for (int i = 0; i < 256; i++)
            if (!held[i] && want[i]) {
                held[i] = true;
                Platform.key(code(i), true);
            }
    }

    /** Índice no vetor: códigos negativos (-1..-7) ficam em 1..7, os positivos (35..57) ficam neles mesmos. */
    private static int idx(int code) {
        return code < 0 ? -code : code;
    }

    private static int code(int idx) {
        return idx < 10 ? -idx : idx;
    }

    private static void set(boolean[] w, int code, boolean v) {
        if (v) w[idx(code)] = true;
    }
}
