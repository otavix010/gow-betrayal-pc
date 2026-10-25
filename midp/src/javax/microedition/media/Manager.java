package javax.microedition.media;

import gowb.MidiPlayer;
import java.io.IOException;
import java.io.InputStream;

public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone", MIDI_DEVICE_LOCATOR = "device://midi";

    private Manager() {}

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[] {"audio/midi", "audio/x-midi", "audio/mid"};
    }

    public static String[] getSupportedProtocols(String contentType) {
        return new String[0];
    }

    public static Player createPlayer(InputStream in, String type) throws IOException, MediaException {
        if (in == null) throw new IllegalArgumentException();
        byte[] data = in.readAllBytes();
        if (type != null && !type.contains("mid")) return new MidiPlayer.Silent(type);
        return new MidiPlayer(data);
    }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        throw new MediaException("sem suporte: " + locator);
    }

    public static void playTone(int note, int duration, int volume) {}
}
