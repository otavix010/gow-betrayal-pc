package gowb;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Synthesizer;

/**
 * Port: o jogo original só toca música nos menus. Aqui o tema do próprio jogo toca mais baixo, em loop, durante as
 * fases, num sintetizador separado (para não disputar canais com os efeitos). Pausa junto com o jogo e respeita a
 * opção de som do jogo.
 */
final class Trilha {
    private Trilha() {}

    static boolean ligada = true;
    /** Volume da trilha nas fases, de 0 a 127. */
    static int volume = 72;

    private static final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "trilha");
        t.setDaemon(true);
        return t;
    });
    private static Sequencer sequencer;
    private static boolean tentou;
    private static volatile boolean tocando;
    private static Field somLigado;

    /** Chamado a cada atualização da janela. */
    static void atualiza() {
        if (!ligada || !Platform.audio || Hooks.loader == null) return;
        boolean deve = deveTocar();
        if (deve == tocando) return;
        tocando = deve;
        fila.execute(() -> aplica(deve));
    }

    /** Nas fases (estado do jogo 100 ou mais), menos na pausa (102/105) e no carregamento (107), com o som do jogo ligado. */
    private static boolean deveTocar() {
        int p = Hooks.estado();
        return p >= 100 && p != 102 && p != 105 && p != 107 && somDoJogo();
    }

    private static boolean somDoJogo() {
        try {
            if (somLigado == null) {
                Object jogo = jogo();
                if (jogo == null) return false;
                somLigado = jogo.getClass().getField("iy");
            }
            Object jogo = jogo();
            return jogo != null && somLigado.getBoolean(jogo);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    /** A instância do jogo (GOWMIDlet.a). */
    private static Object jogo() throws ReflectiveOperationException {
        Field f = Hooks.loader.loadClass("GOWMIDlet").getField("a");
        return f.get(null);
    }

    private static void aplica(boolean tocar) {
        try {
            if (tocar) {
                if (!prepara()) return;
                sequencer.start();
            } else if (sequencer != null && sequencer.isRunning()) {
                sequencer.stop(); // guarda a posição: volta de onde parou
            }
        } catch (Exception e) {
            System.err.println("[trilha] " + e);
        }
    }

    private static boolean prepara() throws Exception {
        if (sequencer != null) return true;
        if (tentou) return false;
        tentou = true;
        byte[] midi = maiorMidi(Hooks.loader.jar.entries.get("RP1"));
        if (midi == null) {
            System.err.println("[trilha] tema não encontrado no jogo");
            return false;
        }
        MidiPlayer.synthReceiver(); // garante a SoundFont carregada
        Synthesizer s = MidiSystem.getSynthesizer();
        MidiPlayer.abre(s);
        if (MidiPlayer.banco != null) {
            s.unloadAllInstruments(s.getDefaultSoundbank());
            s.loadAllInstruments(MidiPlayer.banco);
        }
        // volume geral (SysEx universal "master volume")
        byte[] mv = {(byte) 0xF0, 0x7F, 0x7F, 0x04, 0x01, 0x00, (byte) volume, (byte) 0xF7};
        s.getReceiver().send(new SysexMessage(mv, mv.length), -1);
        Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(midi));
        Sequencer q = MidiSystem.getSequencer(false);
        q.open();
        q.setSequence(seq);
        q.getTransmitter().setReceiver(s.getReceiver());
        q.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
        sequencer = q;
        System.out.printf("[trilha] tema das fases pronto (%.0f s, volume %d)%n", seq.getMicrosecondLength() / 1e6, volume);
        return true;
    }

    /** O MIDI mais longo do pacote de sons: o tema (os outros são efeitos de menos de 3 s). */
    static byte[] maiorMidi(byte[] pacote) throws IOException {
        if (pacote == null) return null;
        byte[] melhor = null;
        for (int i = 0; i + 14 < pacote.length; i++) {
            if (pacote[i] != 'M' || pacote[i + 1] != 'T' || pacote[i + 2] != 'h' || pacote[i + 3] != 'd') continue;
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(pacote, i, pacote.length - i));
                in.skipBytes(4);
                int hl = in.readInt();
                in.skipBytes(2);
                int trilhas = in.readUnsignedShort();
                in.skipBytes(hl - 4);
                int len = 8 + hl;
                for (int t = 0; t < trilhas; t++) {
                    in.skipBytes(4);
                    int tl = in.readInt();
                    in.skipBytes(tl);
                    len += 8 + tl;
                }
                if (melhor == null || len > melhor.length) melhor = java.util.Arrays.copyOfRange(pacote, i, i + len);
            } catch (IOException e) {
                // bloco truncado: ignora
            }
        }
        return melhor;
    }
}
