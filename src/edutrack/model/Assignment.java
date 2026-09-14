package edutrack.model;

public class Assignment {

    public final String id;
    public final String courseCode;
    public final String title;
    public final String text;

    public Assignment(String id, String courseCode, String title, String text) {
        this.id = id;
        this.courseCode = courseCode;
        this.title = title;
        this.text = text;
    }

    @Override
    public String toString() {
        return String.format("Assignment[%s, course=%s, title=%s, textLength=%d]", id, courseCode, title, text.length());
    }
}
