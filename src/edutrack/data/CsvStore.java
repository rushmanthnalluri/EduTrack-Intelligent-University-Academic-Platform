package edutrack.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private static final String MANIFEST_FILE = ".edutrack.manifest";
    private static final String STUDENTS_HEADER = "id,name,program,semester,cgpa,enrolledCourses";
    private static final String FACULTY_HEADER = "id,name,department,expertise";
    private static final String COURSES_HEADER = "code,name,department,credits,semester";
    private static final String EXAMS_HEADER = "studentId,courseCode,midsem,endsem";

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
        if (!Files.isRegularFile(dir.resolve(STUDENTS_FILE))
                || !Files.isRegularFile(dir.resolve(COURSES_FILE))) {
            return false;
        }
        Path manifest = dir.resolve(MANIFEST_FILE);
        return !Files.isRegularFile(manifest) || manifestMatches(dir, manifest);
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
        Path staging = dir.resolve(".edutrack-save-" + UUID.randomUUID());
        Files.createDirectories(staging);
        try {
            writeString(staging.resolve(STUDENTS_FILE), studentsCsv(ds));
            writeString(staging.resolve(FACULTY_FILE), facultyCsv(ds));
            writeString(staging.resolve(COURSES_FILE), coursesCsv(ds));
            writeString(staging.resolve(EXAMS_FILE), examsCsv(ds));

            String manifest = MANIFEST_FILE + "\n"
                    + STUDENTS_FILE + "=" + sha256(staging.resolve(STUDENTS_FILE)) + "\n"
                    + FACULTY_FILE + "=" + sha256(staging.resolve(FACULTY_FILE)) + "\n"
                    + COURSES_FILE + "=" + sha256(staging.resolve(COURSES_FILE)) + "\n"
                    + EXAMS_FILE + "=" + sha256(staging.resolve(EXAMS_FILE)) + "\n";
            writeString(staging.resolve(MANIFEST_FILE), manifest);

            for (String name : new String[] {
                    STUDENTS_FILE, FACULTY_FILE, COURSES_FILE, EXAMS_FILE }) {
                moveReplace(staging.resolve(name), dir.resolve(name));
            }
            moveReplace(staging.resolve(MANIFEST_FILE), dir.resolve(MANIFEST_FILE));
        } finally {
            deleteRecursively(staging);
        }
    }

    private static String studentsCsv(DataStore ds) {
        StringBuilder sb = new StringBuilder("id,name,program,semester,cgpa,enrolledCourses\n");
        for (Student s : ds.students()) {
            sb.append(s.id).append(',')
                    .append(escape(s.name)).append(',')
                    .append(escape(s.program)).append(',')
                    .append(s.semester).append(',')
                    .append(s.cgpa).append(',')
                    .append(escape(String.join(";", s.enrolledCourses))).append('\n');
        }
        return sb.toString();
    }

    private static String facultyCsv(DataStore ds) {
        StringBuilder sb = new StringBuilder("id,name,department,expertise\n");
        for (Faculty f : ds.faculty()) {
            sb.append(f.id).append(',')
                    .append(escape(f.name)).append(',')
                    .append(escape(f.department)).append(',')
                    .append(escape(String.join(";", f.expertise))).append('\n');
        }
        return sb.toString();
    }

    private static String coursesCsv(DataStore ds) {
        StringBuilder sb = new StringBuilder("code,name,department,credits,semester\n");
        for (Course c : ds.courses()) {
            sb.append(escape(c.code)).append(',')
                    .append(escape(c.name)).append(',')
                    .append(escape(c.department)).append(',')
                    .append(c.credits).append(',')
                    .append(c.semester).append('\n');
        }
        return sb.toString();
    }

    private static String examsCsv(DataStore ds) {
        StringBuilder sb = new StringBuilder("studentId,courseCode,midsem,endsem\n");
        for (ExamRecord r : ds.examRecords()) {
            sb.append(r.studentId).append(',')
                    .append(escape(r.courseCode)).append(',')
                    .append(r.midsem).append(',')
                    .append(r.endsem).append('\n');
        }
        return sb.toString();
    }

    private static void writeString(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean manifestMatches(Path dir, Path manifest) {
        try {
            List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
            if (lines.size() != 5 || !MANIFEST_FILE.equals(lines.get(0))) {
                return false;
            }
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split("=", 2);
                if (parts.length != 2 || !sha256(dir.resolve(parts[0])).equals(parts[1])) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    public static boolean deleteCoreFiles(Path dir) throws IOException {
        List<Path> existing = new ArrayList<>();
        for (String name : new String[] {
                STUDENTS_FILE, FACULTY_FILE, COURSES_FILE, EXAMS_FILE, MANIFEST_FILE }) {
            Path path = dir.resolve(name);
            if (Files.exists(path)) {
                existing.add(path);
            }
        }
        if (existing.isEmpty()) {
            return false;
        }

        Path quarantine = dir.resolve(".edutrack-reset-" + UUID.randomUUID());
        Files.createDirectories(quarantine);
        List<Path[]> moved = new ArrayList<>();
        try {
            for (Path path : existing) {
                Path target = quarantine.resolve(path.getFileName().toString());
                moveReplace(path, target);
                moved.add(new Path[] { path, target });
            }
            deleteRecursively(quarantine);
            return true;
        } catch (IOException failure) {
            for (int i = moved.size() - 1; i >= 0; i--) {
                Path[] pair = moved.get(i);
                try {
                    moveReplace(pair[1], pair[0]);
                } catch (IOException ignored) {
                    // Preserve the original failure; rollback is best effort.
                }
            }
            deleteRecursively(quarantine);
            throw failure;
        }
    }

    private static List<String[]> parse(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        List<String[]> all = parseCsvContent(content);
        if (all.isEmpty()) {
            throw new IOException("CSV file is empty: " + file.getFileName());
        }
        String expectedHeader;
        int expectedColumns;
        switch (file.getFileName().toString()) {
            case STUDENTS_FILE:
                expectedHeader = STUDENTS_HEADER;
                expectedColumns = 6;
                break;
            case FACULTY_FILE:
                expectedHeader = FACULTY_HEADER;
                expectedColumns = 4;
                break;
            case COURSES_FILE:
                expectedHeader = COURSES_HEADER;
                expectedColumns = 5;
                break;
            case EXAMS_FILE:
                expectedHeader = EXAMS_HEADER;
                expectedColumns = 4;
                break;
            default:
                throw new IOException("Unsupported CSV file: " + file.getFileName());
        }
        if (all.get(0).length != expectedColumns
                || !expectedHeader.equals(String.join(",", all.get(0)))) {
            throw new IOException("Invalid CSV header/schema in " + file.getFileName());
        }
        all.remove(0);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).length != expectedColumns) {
                throw new IOException("Invalid column count in " + file.getFileName()
                        + " at data row " + (i + 2));
            }
        }
        return all;
    }

    /** Parses RFC-4180-style CSV, including quoted fields spanning physical lines. */
    private static List<String[]> parseCsvContent(String content) throws IOException {
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean justClosedQuote = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        justClosedQuote = true;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }

            if (justClosedQuote) {
                if (ch == ',' ) {
                    fields.add(field.toString());
                    field.setLength(0);
                    justClosedQuote = false;
                    continue;
                }
                if (ch == '\n' || ch == '\r') {
                    fields.add(field.toString());
                    field.setLength(0);
                    rows.add(fields.toArray(new String[0]));
                    fields = new ArrayList<>();
                    justClosedQuote = false;
                    if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    continue;
                }
                throw new IOException("Malformed CSV: unexpected character after closing quote");
            }

            if (ch == '"') {
                if (field.length() != 0) {
                    throw new IOException("Malformed CSV: quote must begin a field");
                }
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (!field.isEmpty() || !fields.isEmpty()) {
                    fields.add(field.toString());
                    field.setLength(0);
                    rows.add(fields.toArray(new String[0]));
                    fields = new ArrayList<>();
                }
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(ch);
            }
        }

        if (inQuotes) {
            throw new IOException("Malformed CSV: unterminated quoted field");
        }
        if (justClosedQuote || !field.isEmpty() || !fields.isEmpty()) {
            fields.add(field.toString());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Staging/quarantine cleanup must not hide the original operation result.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
