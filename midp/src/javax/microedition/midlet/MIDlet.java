package javax.microedition.midlet;

import gowb.Platform;

public abstract class MIDlet {
    protected MIDlet() {}

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() {
        Platform.exit();
    }

    public final void notifyPaused() {}

    public final void resumeRequest() {}

    public final String getAppProperty(String key) {
        return null;
    }

    public final boolean platformRequest(String url) {
        return false;
    }

    public final int checkPermission(String permission) {
        return 1;
    }

    /** Usado pelo frontend para iniciar/encerrar o jogo. */
    public static void callStart(MIDlet m) throws MIDletStateChangeException {
        m.startApp();
    }

    public static void callPause(MIDlet m) {
        m.pauseApp();
    }

    public static void callDestroy(MIDlet m) {
        try {
            m.destroyApp(true);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
