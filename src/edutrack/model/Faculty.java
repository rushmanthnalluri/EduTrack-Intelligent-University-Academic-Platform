package edutrack.model;

import java.util.List;
import java.util.Objects;
import java.util.Collections;

public final class Faculty {

    public final int id;
    public final String name;
    public final String department;
    public final List<String> expertise;

    public Faculty(int id, String name, String department, List<String> expertise) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.department = Objects.requireNonNull(department, "department");
        this.expertise = Collections.unmodifiableList(List.copyOf(
                Objects.requireNonNull(expertise, "expertise")));
    }

    @Override
    public String toString() {
        return String.format("Faculty[id=%d, name=%s, dept=%s, expertise=%s]", id, name, department, expertise);
    }
}
