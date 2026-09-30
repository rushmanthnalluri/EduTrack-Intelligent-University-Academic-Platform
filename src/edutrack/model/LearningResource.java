package edutrack.model;

public final class LearningResource {
    public final String id;
    public final String title;
    public final String type;
    public final String courseCode;

    public LearningResource(String id, String title, String type, String courseCode) {
        this.id = require(id, "Resource id");
        this.title = require(title, "Resource title");
        this.type = require(type, "Resource type");
        this.courseCode = require(courseCode, "Resource course code");
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format("Resource[%s, title=%s, type=%s, course=%s]",
                id, title, type, courseCode);
    }
}
