import java.io.*;
import java.nio.file.*;
import javax.sound.midi.*;

/** Extrai os MIDIs de um pacote RP e descreve cada um: java tools/ExtraiMidi.java RP1 pasta */
public class ExtraiMidi {
    public static void main(String[] a) throws Exception {
        byte[] d = Files.readAllBytes(Paths.get(a[0]));
        Path out = Paths.get(a[1]);
        Files.createDirectories(out);
        int n = 0;
        for (int i = 0; i + 4 < d.length; i++) {
            if (d[i] != 'M' || d[i + 1] != 'T' || d[i + 2] != 'h' || d[i + 3] != 'd') continue;
            // MThd(14) + ntracks x MTrk
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d, i, d.length - i));
            in.skipBytes(4);
            int hl = in.readInt();
            in.skipBytes(2);
            int tracks = in.readShort();
            in.skipBytes(hl - 4);
            int len = 8 + hl;
            for (int t = 0; t < tracks; t++) {
                in.skipBytes(4);
                int tl = in.readInt();
                in.skipBytes(tl);
                len += 8 + tl;
            }
            byte[] m = java.util.Arrays.copyOfRange(d, i, i + len);
            Path f = out.resolve(String.format("m%02d.mid", n++));
            Files.write(f, m);
            Sequence s = MidiSystem.getSequence(new ByteArrayInputStream(m));
            java.util.TreeSet<String> prog = new java.util.TreeSet<>();
            int notas = 0, velMax = 0, vol = -1;
            for (Track tr : s.getTracks())
                for (int k = 0; k < tr.size(); k++) {
                    MidiMessage mm = tr.get(k).getMessage();
                    if (mm instanceof ShortMessage sm) {
                        if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) prog.add(sm.getChannel() + ":" + sm.getData1());
                        if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            notas++;
                            velMax = Math.max(velMax, sm.getData2());
                        }
                        if (sm.getCommand() == ShortMessage.CONTROL_CHANGE && sm.getData1() == 7) vol = Math.max(vol, sm.getData2());
                    }
                }
            System.out.printf("%s: %d bytes, %.1f s, %d trilhas, %d notas, vel máx %d, CC7 máx %d, programas %s%n", f.getFileName(), len,
                    s.getMicrosecondLength() / 1e6, s.getTracks().length, notas, velMax, vol, prog);
        }
    }
}
