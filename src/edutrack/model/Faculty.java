package edutrack.model;

import java.util.List;

public class Faculty {

    public final int id;
    public final String name;
    public final String department;
    public final List<String> expertise;

    public Faculty(int id, String name, String department, List<String> expertise) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.expertise = expertise;
    }

    @Override
    public String toString() {
        return String.format("Faculty[id=%d, name=%s, dept=%s, expertise=%s]", id, name, department, expertise);
    }
}
