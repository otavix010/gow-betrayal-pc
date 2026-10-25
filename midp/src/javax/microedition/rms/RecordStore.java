package javax.microedition.rms;

import gowb.Platform;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Armazenamento de registros do MIDP, gravado em disco (um arquivo .rms por store) a cada alteração. */
public class RecordStore {
    public static final int AUTHMODE_PRIVATE = 0, AUTHMODE_ANY = 1;

    private static final Map<String, RecordStore> OPEN = new HashMap<>();

    private final String name;
    private final TreeMap<Integer, byte[]> records = new TreeMap<>();
    private int nextId = 1, version;
    private long modified;
    private int openCount;

    private RecordStore(String name) {
        this.name = name;
    }

    private static Path file(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) sb.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        return Platform.saveDir.resolve(sb + ".rms");
    }

    public static synchronized RecordStore openRecordStore(String name, boolean create) throws RecordStoreException {
        RecordStore rs = OPEN.get(name);
        if (rs == null) {
            Path f = file(name);
            rs = new RecordStore(name);
            if (Files.exists(f)) rs.load(f);
            else if (!create) throw new RecordStoreNotFoundException(name);
            else rs.save();
            OPEN.put(name, rs);
        }
        rs.openCount++;
        return rs;
    }

    public static RecordStore openRecordStore(String name, boolean create, int authmode, boolean writable)
            throws RecordStoreException {
        return openRecordStore(name, create);
    }

    public static RecordStore openRecordStore(String name, String vendor, String suite) throws RecordStoreException {
        return openRecordStore(name, false);
    }

    public static synchronized void deleteRecordStore(String name) throws RecordStoreException {
        RecordStore rs = OPEN.get(name);
        if (rs != null && rs.openCount > 0) throw new RecordStoreException("store aberto: " + name);
        OPEN.remove(name);
        try {
            if (!Files.deleteIfExists(file(name))) throw new RecordStoreNotFoundException(name);
        } catch (IOException e) {
            throw new RecordStoreException(e.toString());
        }
    }

    public static synchronized String[] listRecordStores() {
        List<String> out = new ArrayList<>();
        try (var s = Files.list(Platform.saveDir)) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.endsWith(".rms")) out.add(readName(p, n.substring(0, n.length() - 4)));
            });
        } catch (IOException e) {
            return null;
        }
        return out.isEmpty() ? null : out.toArray(new String[0]);
    }

    private static String readName(Path p, String fallback) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(p))) {
            return in.readUTF();
        } catch (IOException e) {
            return fallback;
        }
    }

    private void load(Path f) throws RecordStoreException {
        try (InputStream raw = Files.newInputStream(f); DataInputStream in = new DataInputStream(raw)) {
            in.readUTF();
            nextId = in.readInt();
            version = in.readInt();
            modified = in.readLong();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int id = in.readInt();
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                records.put(id, b);
            }
        } catch (IOException e) {
            throw new RecordStoreException("save corrompido: " + f + " (" + e + ")");
        }
    }

    private void save() throws RecordStoreException {
        try {
            Files.createDirectories(Platform.saveDir);
            Path f = file(name), tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (OutputStream raw = Files.newOutputStream(tmp); DataOutputStream out = new DataOutputStream(raw)) {
                out.writeUTF(name);
                out.writeInt(nextId);
                out.writeInt(version);
                out.writeLong(modified);
                out.writeInt(records.size());
                for (Map.Entry<Integer, byte[]> e : records.entrySet()) {
                    out.writeInt(e.getKey());
                    out.writeInt(e.getValue().length);
                    out.write(e.getValue());
                }
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RecordStoreFullException(e.toString());
        }
    }

    private void changed() throws RecordStoreException {
        version++;
        modified = System.currentTimeMillis();
        save();
    }

    private void checkOpen() throws RecordStoreNotOpenException {
        if (openCount <= 0) throw new RecordStoreNotOpenException(name);
    }

    public void closeRecordStore() throws RecordStoreException {
        synchronized (RecordStore.class) {
            checkOpen();
            if (--openCount == 0) OPEN.remove(name);
        }
    }

    public synchronized String getName() throws RecordStoreNotOpenException {
        checkOpen();
        return name;
    }

    public synchronized int getVersion() throws RecordStoreNotOpenException {
        checkOpen();
        return version;
    }

    public synchronized long getLastModified() throws RecordStoreNotOpenException {
        checkOpen();
        return modified;
    }

    public synchronized int getNumRecords() throws RecordStoreNotOpenException {
        checkOpen();
        return records.size();
    }

    public synchronized int getSize() throws RecordStoreNotOpenException {
        checkOpen();
        int s = 0;
        for (byte[] b : records.values()) s += b.length;
        return s;
    }

    public synchronized int getSizeAvailable() throws RecordStoreNotOpenException {
        checkOpen();
        return 1 << 20;
    }

    public synchronized int getNextRecordID() throws RecordStoreException {
        checkOpen();
        return nextId;
    }

    public synchronized int addRecord(byte[] data, int off, int len) throws RecordStoreException {
        checkOpen();
        byte[] b = new byte[len];
        if (len > 0) System.arraycopy(data, off, b, 0, len);
        int id = nextId++;
        records.put(id, b);
        changed();
        return id;
    }

    public synchronized void setRecord(int id, byte[] data, int off, int len) throws RecordStoreException {
        checkOpen();
        if (!records.containsKey(id)) throw new InvalidRecordIDException("id " + id);
        byte[] b = new byte[len];
        if (len > 0) System.arraycopy(data, off, b, 0, len);
        records.put(id, b);
        changed();
    }

    public synchronized void deleteRecord(int id) throws RecordStoreException {
        checkOpen();
        if (records.remove(id) == null) throw new InvalidRecordIDException("id " + id);
        changed();
    }

    public synchronized int getRecordSize(int id) throws RecordStoreException {
        checkOpen();
        byte[] b = records.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        return b.length;
    }

    public synchronized byte[] getRecord(int id) throws RecordStoreException {
        checkOpen();
        byte[] b = records.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        return b.length == 0 ? null : b.clone();
    }

    public synchronized int getRecord(int id, byte[] buf, int off) throws RecordStoreException {
        checkOpen();
        byte[] b = records.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        System.arraycopy(b, 0, buf, off, b.length);
        return b.length;
    }
}
