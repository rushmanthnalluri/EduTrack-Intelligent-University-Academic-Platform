package edutrack.model;

public class Course {

    public final String code;
    public final String name;
    public final String department;
    public final int credits;
    public final int semester;

    public Course(String code, String name, String department, int credits, int semester) {
        this.code = code;
        this.name = name;
        this.department = department;
        this.credits = credits;
        this.semester = semester;
    }

    @Override
    public String toString() {
        return String.format("Course[%s: %s, dept=%s, credits=%d, sem=%d]", code, name, department, credits, semester);
    }
}
