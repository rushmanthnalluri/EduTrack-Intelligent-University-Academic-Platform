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
        if (!this.code.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("code must contain only letters and digits");
        }
        if (credits < 1 || credits > 6) {
            throw new IllegalArgumentException("credits must be 1..6");
        }
        if (semester < 1 || semester > 8) {
            throw new IllegalArgumentException("semester must be 1..8");
        }
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
