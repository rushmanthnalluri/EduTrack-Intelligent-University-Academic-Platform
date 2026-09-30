package edutrack.model;

public final class Assignment {
    public final String id;
    public final String courseCode;
    public final String title;
    public final String text;

    public Assignment(String id, String courseCode, String title, String text) {
        this.id = require(id, "Assignment id");
        this.courseCode = require(courseCode, "Assignment course code");
        this.title = require(title, "Assignment title");
        this.text = require(text, "Assignment text");
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format("Assignment[%s, course=%s, title=%s, textLength=%d]",
                id, courseCode, title, text.length());
    }
}
