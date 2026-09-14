package edutrack.model;

import java.util.List;

public class Student {

    public final int id;
    public final String name;
    public final String program;
    public final int semester;
    public final double cgpa;
    public final List<String> enrolledCourses;

    public Student(int id, String name, String program, int semester, double cgpa, List<String> enrolledCourses) {
        this.id = id;
        this.name = name;
        this.program = program;
        this.semester = semester;
        this.cgpa = cgpa;
        this.enrolledCourses = enrolledCourses;
    }

    @Override
    public String toString() {
        return String.format("Student[id=%d, name=%s, program=%s, sem=%d, cgpa=%.2f, courses=%s]",
                id, name, program, semester, cgpa, enrolledCourses);
    }
}
