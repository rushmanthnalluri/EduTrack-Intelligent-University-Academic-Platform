package edutrack.model;

public final class ActivityEvent {
    public final long timestamp;
    public final int studentId;
    public final String action;
    public final String details;

    public ActivityEvent(long timestamp, int studentId, String action, String details) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp must be non-negative");
        }
        if (studentId <= 0) {
            throw new IllegalArgumentException("Student id must be positive");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action must not be blank");
        }
        if (details == null) {
            throw new IllegalArgumentException("Details must not be null");
        }
        this.timestamp = timestamp;
        this.studentId = studentId;
        this.action = action;
        this.details = details;
    }

    @Override
    public String toString() {
        return String.format("Event[ts=%d, student=%d, action=%s, details=%s]",
                timestamp, studentId, action, details);
    }
}
