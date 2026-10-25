package javax.microedition.lcdui.game;

import gowb.Platform;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public abstract class GameCanvas extends Canvas {
    public static final int UP_PRESSED = 1 << UP, DOWN_PRESSED = 1 << DOWN, LEFT_PRESSED = 1 << LEFT;
    public static final int RIGHT_PRESSED = 1 << RIGHT, FIRE_PRESSED = 1 << FIRE;
    public static final int GAME_A_PRESSED = 1 << GAME_A, GAME_B_PRESSED = 1 << GAME_B;
    public static final int GAME_C_PRESSED = 1 << GAME_C, GAME_D_PRESSED = 1 << GAME_D;

    private final Image buffer = Image.createImage(Platform.width, Platform.height);
    private final int[] pixels = new int[Platform.width * Platform.height];
    private volatile int keyStates, latched;

    protected GameCanvas(boolean suppressKeyEvents) {}

    protected Graphics getGraphics() {
        return buffer.getGraphics();
    }

    public void flushGraphics() {
        buffer.getRGB(pixels, 0, Platform.width, 0, 0, Platform.width, Platform.height);
        Platform.present(pixels, Platform.width, Platform.height);
    }

    public void flushGraphics(int x, int y, int w, int h) {
        flushGraphics();
    }

    public int getKeyStates() {
        int s = keyStates | latched;
        latched = 0;
        return s;
    }

    @Override
    public void paint(Graphics g) {
        g.drawImage(buffer, 0, 0, Graphics.TOP | Graphics.LEFT);
    }

    @Override
    public void dispatchKeyPressed(int keyCode) {
        int a = gameActionBit(keyCode);
        keyStates |= a;
        latched |= a;
        super.dispatchKeyPressed(keyCode);
    }

    @Override
    public void dispatchKeyReleased(int keyCode) {
        keyStates &= ~gameActionBit(keyCode);
        super.dispatchKeyReleased(keyCode);
    }

    private int gameActionBit(int keyCode) {
        int a = getGameAction(keyCode);
        return a == 0 ? 0 : 1 << a;
    }
}
