package javax.microedition.lcdui;

import gowb.Platform;

public abstract class Displayable {
    private String title;

    Displayable() {}

    public boolean isShown() {
        return Display.current() == this;
    }

    public int getWidth() {
        return Platform.width;
    }

    public int getHeight() {
        return Platform.height;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String t) {
        title = t;
    }

    protected void sizeChanged(int w, int h) {}

    void shown() {}

    void hidden() {}
}
