package edutrack.model;

import java.util.Objects;

public final class Course {

    public final String code;
    public final String name;
    public final String department;
    public final int credits;
    public final int semester;

    public Course(String code, String name, String department, int credits, int semester) {
        this.code = requireText(code, "code");
        this.name = requireText(name, "name");
        this.department = requireText(department, "department");
        this.credits = credits;
        this.semester = semester;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format("Course[%s: %s, dept=%s, credits=%d, sem=%d]", code, name, department, credits, semester);
    }
}
