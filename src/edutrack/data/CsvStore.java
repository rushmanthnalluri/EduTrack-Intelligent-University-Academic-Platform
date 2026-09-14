package edutrack.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.Student;

/**
 * CSV persistence for the core academic records. File layout (one header row each):
 *   students.csv : id,name,program,semester,cgpa,enrolledCourses (';'-separated codes)
 *   faculty.csv  : id,name,department,expertise (';'-separated course codes)
 *   courses.csv  : code,name,department,credits,semester
 *   exams.csv    : studentId,courseCode,midsem,endsem
 */
public final class CsvStore {

    public static final String STUDENTS_FILE = "students.csv";
    public static final String FACULTY_FILE = "faculty.csv";
    public static final String COURSES_FILE = "courses.csv";
    public static final String EXAMS_FILE = "exams.csv";

    private CsvStore() {
    }

    public static Path resolveDir() {
        if (Files.isDirectory(Paths.get("DataSets"))) {
            return Paths.get("DataSets");
        }
        if (Files.isDirectory(Paths.get("Text_Hack", "DataSets"))) {
            return Paths.get("Text_Hack", "DataSets");
        }
        return Paths.get("DataSets");
    }

    public static boolean coreFilesExist(Path dir) {
        return Files.isRegularFile(dir.resolve(STUDENTS_FILE))
                && Files.isRegularFile(dir.resolve(COURSES_FILE));
    }

    public static List<Course> loadCourses(Path dir) throws IOException {
        List<Course> list = new ArrayList<>();
        for (String[] row : parse(dir.resolve(COURSES_FILE))) {
            list.add(new Course(row[0], row[1], row[2], Integer.parseInt(row[3]), Integer.parseInt(row[4])));
        }
        return list;
    }

    public static List<Student> loadStudents(Path dir) throws IOException {
        List<Student> list = new ArrayList<>();
        for (String[] row : parse(dir.resolve(STUDENTS_FILE))) {
            List<String> enrolled = new ArrayList<>();
            if (row.length > 5 && !row[5].isEmpty()) {
                for (String code : row[5].split(";")) {
                    enrolled.add(code.trim());
                }
            }
            list.add(new Student(Integer.parseInt(row[0]), row[1], row[2],
                    Integer.parseInt(row[3]), Double.parseDouble(row[4]), enrolled));
        }
        return list;
    }

    public static List<Faculty> loadFaculty(Path dir) throws IOException {
        List<Faculty> list = new ArrayList<>();
        for (String[] row : parse(dir.resolve(FACULTY_FILE))) {
            List<String> expertise = new ArrayList<>();
            if (row.length > 3 && !row[3].isEmpty()) {
                for (String code : row[3].split(";")) {
                    expertise.add(code.trim());
                }
            }
            list.add(new Faculty(Integer.parseInt(row[0]), row[1], row[2], expertise));
        }
        return list;
    }

    public static List<ExamRecord> loadExams(Path dir) throws IOException {
        List<ExamRecord> list = new ArrayList<>();
        for (String[] row : parse(dir.resolve(EXAMS_FILE))) {
            list.add(new ExamRecord(Integer.parseInt(row[0]), row[1],
                    Integer.parseInt(row[2]), Integer.parseInt(row[3])));
        }
        return list;
    }

    public static void saveAll(DataStore ds, Path dir) throws IOException {
        Files.createDirectories(dir);

        StringBuilder sb = new StringBuilder("id,name,program,semester,cgpa,enrolledCourses\n");
        for (Student s : ds.students()) {
            sb.append(s.id).append(',')
                    .append(escape(s.name)).append(',')
                    .append(escape(s.program)).append(',')
                    .append(s.semester).append(',')
                    .append(s.cgpa).append(',')
                    .append(escape(String.join(";", s.enrolledCourses))).append('\n');
        }
        Files.writeString(dir.resolve(STUDENTS_FILE), sb.toString(), StandardCharsets.UTF_8);

        sb = new StringBuilder("id,name,department,expertise\n");
        for (Faculty f : ds.faculty()) {
            sb.append(f.id).append(',')
                    .append(escape(f.name)).append(',')
                    .append(escape(f.department)).append(',')
                    .append(escape(String.join(";", f.expertise))).append('\n');
        }
        Files.writeString(dir.resolve(FACULTY_FILE), sb.toString(), StandardCharsets.UTF_8);

        sb = new StringBuilder("code,name,department,credits,semester\n");
        for (Course c : ds.courses()) {
            sb.append(escape(c.code)).append(',')
                    .append(escape(c.name)).append(',')
                    .append(escape(c.department)).append(',')
                    .append(c.credits).append(',')
                    .append(c.semester).append('\n');
        }
        Files.writeString(dir.resolve(COURSES_FILE), sb.toString(), StandardCharsets.UTF_8);

        sb = new StringBuilder("studentId,courseCode,midsem,endsem\n");
        for (ExamRecord r : ds.examRecords()) {
            sb.append(r.studentId).append(',')
                    .append(escape(r.courseCode)).append(',')
                    .append(r.midsem).append(',')
                    .append(r.endsem).append('\n');
        }
        Files.writeString(dir.resolve(EXAMS_FILE), sb.toString(), StandardCharsets.UTF_8);
    }

    public static boolean deleteCoreFiles(Path dir) {
        boolean deleted = false;
        for (String name : new String[] { STUDENTS_FILE, FACULTY_FILE, COURSES_FILE, EXAMS_FILE }) {
            try {
                deleted |= Files.deleteIfExists(dir.resolve(name));
            } catch (IOException e) {
                // leave the file in place
            }
        }
        return deleted;
    }

    private static List<String[]> parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            rows.add(parseLine(line).toArray(new String[0]));
        }
        return rows;
    }

    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
