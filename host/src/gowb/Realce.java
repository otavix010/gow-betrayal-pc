package gowb;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/**
 * Port: os efeitos do jogo foram compostos com notas graves demais (kalimba, flautas e tambores nas notas 0 a 36,
 * quase infrassom), que num sintetizador GM viram um grunhido. Sobe essas notas por oitavas até a região em que o
 * instrumento soa como pancada ou metal. A melodia de cada efeito (as notas e o ritmo) não muda.
 */
public final class Realce {
    private Realce() {}

    /** Instrumentos "melódicos" usados como efeito: no mínimo a nota 48 (Dó 3). */
    private static final int[] TONAIS = {76, 77, 78, 79, 90, 97, 108, 115, 119};
    /** Instrumentos de percussão afinada: no mínimo a nota 24 (Dó 1), para continuarem graves. */
    private static final int[] PERCUSSAO = {47, 116, 117, 118};

    public static void aplica(Sequence seq) {
        for (Track t : seq.getTracks()) {
            int[] programa = new int[16];
            for (int i = 0; i < t.size(); i++) {
                MidiEvent e = t.get(i);
                MidiMessage m = e.getMessage();
                if (!(m instanceof ShortMessage s)) continue;
                int ch = s.getChannel(), cmd = s.getCommand();
                if (cmd == ShortMessage.PROGRAM_CHANGE) {
                    programa[ch] = s.getData1();
                    continue;
                }
                if (ch == 9 || (cmd != ShortMessage.NOTE_ON && cmd != ShortMessage.NOTE_OFF)) continue;
                int nota = s.getData1(), minimo = minimo(programa[ch]);
                while (nota < minimo) nota += 12;
                if (nota != s.getData1() && nota <= 127) {
                    try {
                        s.setMessage(cmd, ch, nota, s.getData2());
                    } catch (InvalidMidiDataException ignored) {
                        // nota válida por construção
                    }
                }
            }
        }
    }

    private static int minimo(int programa) {
        for (int p : TONAIS) if (p == programa) return 48;
        for (int p : PERCUSSAO) if (p == programa) return 24;
        return 36;
    }
}
