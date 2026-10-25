package javax.microedition.media;

public interface Player extends Controllable {
    int UNREALIZED = 100, REALIZED = 200, PREFETCHED = 300, STARTED = 400, CLOSED = 0;
    long TIME_UNKNOWN = -1;

    void realize() throws MediaException;

    void prefetch() throws MediaException;

    void start() throws MediaException;

    void stop() throws MediaException;

    void deallocate();

    void close();

    long setMediaTime(long now) throws MediaException;

    long getMediaTime();

    int getState();

    long getDuration();

    String getContentType();

    void setLoopCount(int count);

    void addPlayerListener(PlayerListener l);

    void removePlayerListener(PlayerListener l);
}
