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
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        this.name = requireText(name, "name");
        this.department = requireText(department, "department");
        this.expertise = Collections.unmodifiableList(List.copyOf(
                Objects.requireNonNull(expertise, "expertise")));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    @Override
    public String toString() {
        return String.format("Faculty[id=%d, name=%s, dept=%s, expertise=%s]", id, name, department, expertise);
    }
}
