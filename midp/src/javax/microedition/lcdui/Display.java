package javax.microedition.lcdui;

import gowb.Platform;
import javax.microedition.midlet.MIDlet;

public class Display {
    private static final Display INSTANCE = new Display();
    private static volatile Displayable current;
    private static Image screen;

    private Display() {}

    public static Display getDisplay(MIDlet m) {
        return INSTANCE;
    }

    /** Tela atual (para o frontend). */
    public static Displayable current() {
        return current;
    }

    /** Buffer da tela usado pelo paint() dos Canvas comuns. */
    static synchronized Image screen() {
        if (screen == null || screen.w != Platform.width || screen.h != Platform.height)
            screen = Image.createImage(Platform.width, Platform.height);
        return screen;
    }

    public Displayable getCurrent() {
        return current;
    }

    public void setCurrent(Displayable next) {
        Displayable old = current;
        if (old == next) return;
        current = next;
        Platform.events.execute(() -> {
            if (old != null) old.hidden();
            if (next != null) next.shown();
        });
    }

    public void setCurrent(Alert alert, Displayable next) {
        setCurrent(next);
    }

    public void callSerially(Runnable r) {
        Platform.events.execute(r);
    }

    public boolean vibrate(int ms) {
        return false;
    }

    public boolean flashBacklight(int ms) {
        return false;
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 1 << 24;
    }

    public int numAlphaLevels() {
        return 256;
    }
}
