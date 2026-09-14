package edutrack.model;

public class ActivityEvent {

    public final long timestamp;
    public final int studentId;
    public final String action;
    public final String details;

    public ActivityEvent(long timestamp, int studentId, String action, String details) {
        this.timestamp = timestamp;
        this.studentId = studentId;
        this.action = action;
        this.details = details;
    }

    @Override
    public String toString() {
        return String.format("Event[ts=%d, student=%d, action=%s, details=%s]", timestamp, studentId, action, details);
    }
}
