package edutrack.model;

import java.util.List;
import java.util.Objects;
import java.util.Collections;

public final class Student {

    public final int id;
    public final String name;
    public final String program;
    public final int semester;
    public final double cgpa;
    public final List<String> enrolledCourses;

    public Student(int id, String name, String program, int semester, double cgpa, List<String> enrolledCourses) {
        this.id = id;
        this.name = requireText(name, "name");
        this.program = requireText(program, "program");
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        if (semester < 1 || semester > 8) throw new IllegalArgumentException("semester must be 1..8");
        if (!Double.isFinite(cgpa) || cgpa < 0 || cgpa > 10) {
            throw new IllegalArgumentException("cgpa must be finite and in 0..10");
        }
        this.semester = semester;
        this.cgpa = cgpa;
        this.enrolledCourses = Collections.unmodifiableList(List.copyOf(
                Objects.requireNonNull(enrolledCourses, "enrolledCourses")));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    @Override
    public String toString() {
        return String.format("Student[id=%d, name=%s, program=%s, sem=%d, cgpa=%.2f, courses=%s]",
                id, name, program, semester, cgpa, enrolledCourses);
    }
}
