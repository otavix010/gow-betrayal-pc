package gowb;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.microedition.media.Control;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;

/** Player MIDP para MIDI: um Sequencer por player, todos tocando no mesmo sintetizador. */
public class MidiPlayer implements Player {
    private static Synthesizer synth;
    /** SoundFont carregada (compartilhada com a trilha das fases), ou null. */
    static volatile javax.sound.midi.Soundbank banco;
    private static boolean synthTried;

    /** Latência do sintetizador: o padrão do Java é 120 ms, o que atrasa o som do golpe. */
    private static final long LATENCIA_US = 40_000;

    /** Abre o sintetizador antes do jogo pedir o primeiro som (carregar a SoundFont leva um instante). */
    static void preparar() {
        Thread t = new Thread(MidiPlayer::synthReceiver, "audio-preparo");
        t.setDaemon(true);
        t.start();
    }

    /** Sintetizador compartilhado (null se o áudio estiver desligado ou falhar). */
    static synchronized Receiver synthReceiver() {
        if (!Platform.audio) return null;
        if (!synthTried) {
            synthTried = true;
            try {
                long t0 = System.nanoTime();
                Synthesizer s = MidiSystem.getSynthesizer();
                abre(s);
                if (Platform.soundfont != null && java.nio.file.Files.isRegularFile(Platform.soundfont)) {
                    var bank = MidiSystem.getSoundbank(Platform.soundfont.toFile());
                    banco = bank;
                    s.unloadAllInstruments(s.getDefaultSoundbank());
                    s.loadAllInstruments(bank);
                }
                synth = s;
                System.out.printf("[audio] sintetizador pronto em %d ms (latência %d ms, banco: %s)%n", (System.nanoTime() - t0) / 1_000_000,
                        s.getLatency() / 1000, Platform.soundfont == null ? "interno do Java" : Platform.soundfont.getFileName());
            } catch (Exception e) {
                System.err.println("[audio] sem sintetizador: " + e);
            }
        }
        try {
            return synth == null ? null : synth.getReceiver();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Abre com latência menor pela interface interna do Gervill (o manifesto do app exporta com.sun.media.sound);
     * se não der, abre do jeito padrão.
     */
    static void abre(Synthesizer s) throws Exception {
        if (s instanceof com.sun.media.sound.AudioSynthesizer as) {
            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("latency", LATENCIA_US);
            info.put("format", new javax.sound.sampled.AudioFormat(48000, 16, 2, true, false));
            try {
                as.open(null, info);
                return;
            } catch (Exception e) {
                System.err.println("[audio] latência baixa indisponível: " + e);
            }
        }
        s.open();
    }

    private final byte[] data;
    private static final boolean LOG = System.getenv("GOWB_AUDIO_LOG") != null;
    private static final long T0 = System.nanoTime();
    private static int proximoId;
    private final int id = proximoId++;

    private void log(String s) {
        if (LOG) System.out.printf("[audio %6d ms] #%d %s%n", (System.nanoTime() - T0) / 1_000_000, id, s);
    }
    private final List<PlayerListener> listeners = new CopyOnWriteArrayList<>();
    private volatile int state = UNREALIZED;
    private Sequence seq;
    private Sequencer sequencer;
    private Receiver out;
    private int loops = 1;
    private int volume = 100;
    private boolean muted;

    public MidiPlayer(byte[] data) {
        this.data = data;
    }

    @Override
    public synchronized void realize() throws MediaException {
        if (state == CLOSED) throw new IllegalStateException();
        if (state >= REALIZED) return;
        try {
            seq = MidiSystem.getSequence(new ByteArrayInputStream(data));
            if (Platform.efeitosRealcados && seq.getMicrosecondLength() < 3_000_000) Realce.aplica(seq);
        } catch (Exception e) {
            throw new MediaException("MIDI inválido: " + e);
        }
        state = REALIZED;
    }

    @Override
    public synchronized void prefetch() throws MediaException {
        realize();
        if (state >= PREFETCHED) return;
        try {
            sequencer = MidiSystem.getSequencer(false);
            sequencer.open();
            sequencer.setSequence(seq);
            out = synthReceiver();
            // Sem sintetizador o sequenciador toca "mudo", mantendo os tempos que o jogo consulta.
            if (out != null) sequencer.getTransmitter().setReceiver(new VolumeReceiver());
            sequencer.addMetaEventListener(this::meta);
        } catch (Exception e) {
            throw new MediaException("sequenciador: " + e);
        }
        state = PREFETCHED;
    }

    private void meta(MetaMessage m) {
        if (m.getType() != 47) return;
        // fim da sequência: se não há mais repetições o sequenciador para sozinho
        synchronized (this) {
            if (state == STARTED && sequencer != null && !sequencer.isRunning()) state = PREFETCHED;
            else return;
        }
        fire(PlayerListener.END_OF_MEDIA, getMediaTime());
    }

    @Override
    public synchronized void start() throws MediaException {
        prefetch();
        if (state == STARTED) return;
        sequencer.setLoopCount(loops < 0 ? Sequencer.LOOP_CONTINUOUSLY : loops - 1);
        if (sequencer.getTickPosition() >= sequencer.getTickLength()) sequencer.setTickPosition(0);
        sequencer.start();
        state = STARTED;
        log("start (" + data.length + " bytes, " + getDuration() / 1000 + " ms, loops " + loops + ")");
        fire(PlayerListener.STARTED, getMediaTime());
    }

    @Override
    public synchronized void stop() {
        if (state != STARTED) return;
        sequencer.stop();
        allNotesOff();
        state = PREFETCHED;
        log("stop em " + getMediaTime() / 1000 + " ms");
        fire(PlayerListener.STOPPED, getMediaTime());
    }

    private void allNotesOff() {
        if (out == null) return;
        try {
            for (int ch = 0; ch < 16; ch++) out.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 123, 0), -1);
        } catch (Exception ignored) {
        }
    }

    @Override
    public synchronized void deallocate() {
        if (state == STARTED) stop();
        if (state == PREFETCHED) {
            sequencer.close();
            sequencer = null;
            state = REALIZED;
        }
    }

    @Override
    public synchronized void close() {
        if (state == CLOSED) return;
        deallocate();
        state = CLOSED;
        fire(PlayerListener.CLOSED, null);
    }

    @Override
    public synchronized long setMediaTime(long us) throws MediaException {
        if (state == UNREALIZED || state == CLOSED) throw new IllegalStateException();
        if (sequencer == null) prefetch();
        sequencer.setMicrosecondPosition(Math.max(0, us));
        return sequencer.getMicrosecondPosition();
    }

    @Override
    public synchronized long getMediaTime() {
        return sequencer == null ? 0 : sequencer.getMicrosecondPosition();
    }

    @Override
    public int getState() {
        // o fim natural da música não avisa a tempo: confere se o sequenciador ainda toca
        Sequencer s = sequencer;
        if (state == STARTED && s != null && !s.isRunning()) state = PREFETCHED;
        return state;
    }

    @Override
    public long getDuration() {
        return seq == null ? TIME_UNKNOWN : seq.getMicrosecondLength();
    }

    @Override
    public String getContentType() {
        return "audio/midi";
    }

    @Override
    public synchronized void setLoopCount(int count) {
        if (count == 0) throw new IllegalArgumentException();
        loops = count;
    }

    @Override
    public void addPlayerListener(PlayerListener l) {
        if (l != null) listeners.add(l);
    }

    @Override
    public void removePlayerListener(PlayerListener l) {
        listeners.remove(l);
    }

    private void fire(String ev, Object data) {
        for (PlayerListener l : listeners) l.playerUpdate(this, ev, data);
    }

    @Override
    public Control getControl(String type) {
        return type != null && type.endsWith("VolumeControl") ? volumeControl : null;
    }

    @Override
    public Control[] getControls() {
        return new Control[] {volumeControl};
    }

    private final VolumeControl volumeControl = new VolumeControl() {
        public void setMute(boolean m) {
            muted = m;
        }

        public boolean isMuted() {
            return muted;
        }

        public int setLevel(int l) {
            volume = Math.max(0, Math.min(100, l));
            return volume;
        }

        public int getLevel() {
            return volume;
        }
    };

    /** Aplica volume/mudo do player às notas antes de mandar ao sintetizador. */
    private final class VolumeReceiver implements Receiver {
        @Override
        public void send(javax.sound.midi.MidiMessage m, long t) {
            Receiver r = out;
            if (r == null) return;
            if (m instanceof ShortMessage s && s.getCommand() == ShortMessage.NOTE_ON && s.getData2() > 0) {
                if (muted) return;
                if (volume < 100) {
                    try {
                        m = new ShortMessage(ShortMessage.NOTE_ON, s.getChannel(), s.getData1(), Math.max(1, s.getData2() * volume / 100));
                    } catch (Exception ignored) {
                    }
                }
            }
            r.send(m, t);
        }

        @Override
        public void close() {}
    }

    /** Formatos que não são MIDI (o jogo só usa MIDI): player que não toca nada. */
    public static final class Silent implements Player {
        private final String type;
        private int state = UNREALIZED;

        public Silent(String type) {
            this.type = type;
        }

        public void realize() {
            if (state < REALIZED) state = REALIZED;
        }

        public void prefetch() {
            if (state < PREFETCHED) state = PREFETCHED;
        }

        public void start() {
            state = PREFETCHED;
        }

        public void stop() {}

        public void deallocate() {
            if (state > REALIZED) state = REALIZED;
        }

        public void close() {
            state = CLOSED;
        }

        public long setMediaTime(long t) {
            return 0;
        }

        public long getMediaTime() {
            return 0;
        }

        public int getState() {
            return state;
        }

        public long getDuration() {
            return TIME_UNKNOWN;
        }

        public String getContentType() {
            return type;
        }

        public void setLoopCount(int c) {}

        public void addPlayerListener(PlayerListener l) {}

        public void removePlayerListener(PlayerListener l) {}

        public Control getControl(String t) {
            return null;
        }

        public Control[] getControls() {
            return new Control[0];
        }
    }
}
