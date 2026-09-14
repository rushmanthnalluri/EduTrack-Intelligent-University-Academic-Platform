package edutrack.model;

public class LearningResource {

    public final String id;
    public final String title;
    public final String type;
    public final String courseCode;

    public LearningResource(String id, String title, String type, String courseCode) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.courseCode = courseCode;
    }

    @Override
    public String toString() {
        return String.format("Resource[%s, title=%s, type=%s, course=%s]", id, title, type, courseCode);
    }
}
