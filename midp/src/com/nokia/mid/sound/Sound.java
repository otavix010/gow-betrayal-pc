package com.nokia.mid.sound;

/** API de som da Nokia: o jogo só a usa para toques no formato da Nokia, que esta versão não traz. */
public class Sound {
    public static final int FORMAT_TONE = 1, FORMAT_WAV = 5;
    public static final int SOUND_PLAYING = 0, SOUND_STOPPED = 1, SOUND_UNINITIALIZED = 3;

    private int state = SOUND_STOPPED;

    public Sound(byte[] data, int type) {}

    public Sound(int freq, long duration) {}

    public void init(byte[] data, int type) {}

    public void play(int loop) {}

    public void stop() {}

    public void release() {
        state = SOUND_UNINITIALIZED;
    }

    public int getState() {
        return state;
    }

    public void setGain(int gain) {}

    public int getGain() {
        return 255;
    }

    public static int getConcurrentSoundCount(int type) {
        return 1;
    }

    public static int[] getSupportedFormats() {
        return new int[] {FORMAT_TONE};
    }
}
