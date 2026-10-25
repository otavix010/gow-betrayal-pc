import java.io.File;
import javax.sound.midi.*;

/** Lista os eventos de um MIDI: java tools/DumpMidi.java a.mid */
public class DumpMidi {
    public static void main(String[] a) throws Exception {
        for (String f : a) {
            Sequence s = MidiSystem.getSequence(new File(f));
            System.out.println("== " + new File(f).getName() + " res " + s.getResolution());
            StringBuilder sb = new StringBuilder();
            for (Track t : s.getTracks())
                for (int i = 0; i < t.size(); i++) {
                    MidiEvent e = t.get(i);
                    MidiMessage m = e.getMessage();
                    if (m instanceof ShortMessage sm) {
                        String c = switch (sm.getCommand()) {
                            case ShortMessage.NOTE_ON -> sm.getData2() == 0 ? "off" : "on";
                            case ShortMessage.NOTE_OFF -> "off";
                            case ShortMessage.PROGRAM_CHANGE -> "prog";
                            case ShortMessage.CONTROL_CHANGE -> "cc" + sm.getData1();
                            case ShortMessage.PITCH_BEND -> "bend";
                            default -> "cmd" + sm.getCommand();
                        };
                        sb.append(e.getTick()).append(':').append(sm.getChannel()).append(c).append('=').append(sm.getCommand() == ShortMessage.PITCH_BEND ? ((sm.getData2() << 7) | sm.getData1()) - 8192 : sm.getCommand() == ShortMessage.CONTROL_CHANGE ? sm.getData2() : sm.getData1()).append(' ');
                    } else if (m instanceof MetaMessage mm) {
                        sb.append(e.getTick()).append(":meta").append(mm.getType()).append(' ');
                    } else {
                        sb.append(e.getTick()).append(":sysex ");
                    }
                }
            System.out.println(sb);
        }
    }
}
