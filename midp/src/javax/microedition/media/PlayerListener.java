package javax.microedition.media;

public interface PlayerListener {
    String STARTED = "started", STOPPED = "stopped", END_OF_MEDIA = "endOfMedia", CLOSED = "closed";
    String ERROR = "error", VOLUME_CHANGED = "volumeChanged", DURATION_UPDATED = "durationUpdated";
    String DEVICE_AVAILABLE = "deviceAvailable", DEVICE_UNAVAILABLE = "deviceUnavailable", STOPPED_AT_TIME = "stoppedAtTime";

    void playerUpdate(Player player, String event, Object eventData);
}
