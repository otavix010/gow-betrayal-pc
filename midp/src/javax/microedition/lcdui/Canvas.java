package javax.microedition.lcdui;

import gowb.Platform;

public abstract class Canvas extends Displayable {
    public static final int UP = 1, DOWN = 6, LEFT = 2, RIGHT = 5, FIRE = 8;
    public static final int GAME_A = 9, GAME_B = 10, GAME_C = 11, GAME_D = 12;
    public static final int KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51, KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53, KEY_NUM6 = 54, KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57;
    public static final int KEY_STAR = 42, KEY_POUND = 35;

    /** paint() e flush nunca rodam ao mesmo tempo. */
    static final Object PAINT_LOCK = new Object();

    private volatile boolean repaintPending;

    protected Canvas() {}

    protected abstract void paint(Graphics g);

    public void setFullScreenMode(boolean mode) {}

    public boolean isDoubleBuffered() {
        return true;
    }

    public boolean hasPointerEvents() {
        return false;
    }

    public boolean hasPointerMotionEvents() {
        return false;
    }

    public boolean hasRepeatEvents() {
        return true;
    }

    public int getGameAction(int keyCode) {
        switch (keyCode) {
            case -1: case KEY_NUM2: return UP;
            case -2: case KEY_NUM8: return DOWN;
            case -3: case KEY_NUM4: return LEFT;
            case -4: case KEY_NUM6: return RIGHT;
            case -5: case KEY_NUM5: return FIRE;
            case KEY_NUM1: return GAME_A;
            case KEY_NUM3: return GAME_B;
            case KEY_NUM7: return GAME_C;
            case KEY_NUM9: return GAME_D;
            default: return 0;
        }
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP: return -1;
            case DOWN: return -2;
            case LEFT: return -3;
            case RIGHT: return -4;
            case FIRE: return -5;
            case GAME_A: return KEY_NUM1;
            case GAME_B: return KEY_NUM3;
            case GAME_C: return KEY_NUM7;
            case GAME_D: return KEY_NUM9;
            default: throw new IllegalArgumentException();
        }
    }

    public String getKeyName(int keyCode) {
        if (keyCode >= 32 && keyCode < 127) return String.valueOf((char) keyCode);
        return "K" + keyCode;
    }

    protected void keyPressed(int keyCode) {}

    protected void keyReleased(int keyCode) {}

    protected void keyRepeated(int keyCode) {}

    protected void pointerPressed(int x, int y) {}

    protected void pointerReleased(int x, int y) {}

    protected void pointerDragged(int x, int y) {}

    protected void showNotify() {}

    protected void hideNotify() {}

    @Override
    void shown() {
        showNotify();
        repaint();
    }

    @Override
    void hidden() {
        hideNotify();
    }

    public void dispatchKeyPressed(int keyCode) {
        keyPressed(keyCode);
    }

    public void dispatchKeyReleased(int keyCode) {
        keyReleased(keyCode);
    }

    public final void repaint() {
        repaintPending = true;
        Platform.events.execute(this::serviceRepaints);
    }

    public final void repaint(int x, int y, int w, int h) {
        repaint();
    }

    public final void serviceRepaints() {
        if (!repaintPending) return;
        synchronized (PAINT_LOCK) {
            if (!repaintPending) return;
            repaintPending = false;
            if (!isShown()) return;
            Image s = Display.screen();
            paint(new Graphics(s));
            Platform.present(s.pixels, s.w, s.h);
        }
    }
}
