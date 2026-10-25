import com.sun.media.sound.AudioSynthesizer;
import java.io.*;
import java.nio.file.*;
import javax.sound.midi.*;
import javax.sound.sampled.*;

/**
 * Renderiza MIDIs com o sintetizador do Java (Gervill) para WAV e mede volume.
 * java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED tools/Render.java saida_dir [banco.sf2|-] a.mid b.mid ...
 */
public class Render {
    public static void main(String[] a) throws Exception {
        Path out = Paths.get(a[0]);
        Files.createDirectories(out);
        for (int k = 2; k < a.length; k++) {
            Sequence seq = MidiSystem.getSequence(new File(a[k]));
            if (System.getenv("REALCE") != null) Class.forName("gowb.Realce").getMethod("aplica", Sequence.class).invoke(null, seq);
            AudioSynthesizer synth = (AudioSynthesizer) MidiSystem.getSynthesizer();
            AudioFormat fmt = new AudioFormat(44100, 16, 2, true, false);
            AudioInputStream stream = synth.openStream(fmt, null);
            if (!a[1].equals("-")) {
                Soundbank sb = MidiSystem.getSoundbank(new File(a[1]));
                synth.unloadAllInstruments(synth.getDefaultSoundbank());
                synth.loadAllInstruments(sb);
            }
            Receiver r = synth.getReceiver();
            // eventos em ordem, com tempo em microssegundos pelo mapa de andamento
            java.util.List<MidiEvent> ev = new java.util.ArrayList<>();
            for (Track t : seq.getTracks()) for (int i = 0; i < t.size(); i++) ev.add(t.get(i));
            ev.sort(java.util.Comparator.comparingLong(MidiEvent::getTick));
            double usPorTick = 500000.0 / seq.getResolution();
            long ultTick = 0;
            double us = 0;
            for (MidiEvent e : ev) {
                us += (e.getTick() - ultTick) * usPorTick;
                ultTick = e.getTick();
                MidiMessage m = e.getMessage();
                if (m instanceof MetaMessage mm) {
                    if (mm.getType() == 0x51) {
                        byte[] d = mm.getData();
                        int tempo = (d[0] & 255) << 16 | (d[1] & 255) << 8 | d[2] & 255;
                        usPorTick = (double) tempo / seq.getResolution();
                    }
                    continue;
                }
                r.send(m, (long) us);
            }
            long frames = (long) ((us / 1e6 + 1.0) * 44100);
            byte[] buf = new byte[(int) frames * 4];
            int lido = 0;
            while (lido < buf.length) {
                int n = stream.read(buf, lido, buf.length - lido);
                if (n < 0) break;
                lido += n;
            }
            synth.close();
            double soma = 0;
            int pico = 0;
            for (int i = 0; i + 1 < lido; i += 2) {
                int s = (short) ((buf[i] & 255) | buf[i + 1] << 8);
                soma += (double) s * s;
                pico = Math.max(pico, Math.abs(s));
            }
            double rms = Math.sqrt(soma / (lido / 2));
            String nome = Paths.get(a[k]).getFileName().toString().replace(".mid", ".wav");
            AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(buf, 0, lido), fmt, lido / 4), AudioFileFormat.Type.WAVE, out.resolve(nome).toFile());
            System.out.printf("%s: %.1f s, RMS %.0f (%.1f dBFS), pico %d (%.1f dBFS)%n", nome, lido / 4 / 44100.0, rms, 20 * Math.log10(rms / 32768), pico,
                    20 * Math.log10(Math.max(pico, 1) / 32768.0));
        }
    }
}
