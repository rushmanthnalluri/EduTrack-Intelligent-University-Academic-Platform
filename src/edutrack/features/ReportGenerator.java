package edutrack.features;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import edutrack.data.DataStore;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Student;

/**
 * Reports &amp; Export: the documents an academic office needs — student
 * transcripts, per-course grade sheets, department summaries and the at-risk
 * list. Generators reuse {@link ExamAnalytics} and {@link RecordQueries} so
 * the numbers always agree with the analytics screens. The non-interactive
 * {@code main} runs a self-test ({@code java -cp bin edutrack.features.ReportGenerator});
 * {@link #run(Scanner, DataStore)} is the CLI sub-menu entry point.
 */
public final class ReportGenerator {

    private static final int PREVIEW_LINES = 30;
    private static final String EXPORT_DIR = "exports";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportGenerator() {
    }

    // ---------------------------------------------------------------
    // Transcript (formatted plain text)
    // ---------------------------------------------------------------

    public static String transcript(DataStore ds, int studentId) {
        ExamAnalytics.ReportCard rc = ExamAnalytics.reportCard(ds, studentId);
        if (rc == null) {
            throw new IllegalArgumentException("Unknown student id: " + studentId);
        }
        // Class rank comes from the Records layer (GPA-based, ties share the better rank).
        RecordQueries.StudentDetail detail = RecordQueries.studentDetail(ds, studentId, 0);
        Map<String, Course> coursesByCode = ds.coursesByCode();
        Student s = rc.student;

        List<ExamRecord> records = new ArrayList<>(rc.records);
        records.sort(Comparator.comparing(r -> r.courseCode));

        int attempted = 0;
        int earned = 0;
        for (ExamRecord r : records) {
            Course c = coursesByCode.get(r.courseCode);
            int credits = c == null ? 0 : c.credits;
            attempted += credits;
            if (r.passed()) {
                earned += credits;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(repeat('=', 78)).append('\n');
        sb.append(center("EDUTRACK UNIVERSITY", 78)).append('\n');
        sb.append(center("Intelligent University Academic Platform", 78)).append('\n');
        sb.append(center("OFFICIAL ACADEMIC TRANSCRIPT", 78)).append('\n');
        sb.append(repeat('=', 78)).append('\n');
        sb.append(String.format("%-16s: %d%n", "Student ID", s.id));
        sb.append(String.format("%-16s: %s%n", "Name", s.name));
        sb.append(String.format("%-16s: %s%n", "Program", s.program));
        sb.append(String.format("%-16s: %d%n", "Semester", s.semester));        sb.append(repeat('-', 78)).append('\n');
        sb.append(String.format("%-7s %-34s %7s %6s %7s %5s %-5s %4s%n",
                "Code", "Title", "Credits", "Mid/30", "End/70", "Total", "Grade", "Pts"));
        sb.append(repeat('-', 78)).append('\n');
        for (ExamRecord r : records) {
            Course c = coursesByCode.get(r.courseCode);
            String title = c == null ? r.courseCode : c.name;
            int credits = c == null ? 0 : c.credits;
            sb.append(String.format("%-7s %-34.34s %7d %6d %7d %5d %-5s %4d%n",
                    r.courseCode, title, credits, r.midsem, r.endsem, r.total(), r.grade(), r.gradePoints()));
        }
        sb.append(repeat('-', 78)).append('\n');
        sb.append(String.format("%-17s: %d%n", "Credits attempted", attempted));
        sb.append(String.format("%-17s: %d%n", "Credits earned", earned));
        sb.append(String.format("%-17s: %.2f%n", "Weighted GPA", rc.gpa));
        sb.append(String.format("%-17s: %.2f%n", "Stored CGPA", s.cgpa));
        sb.append(String.format("%-17s: %d of %d (GPA basis)%n", "Class rank", detail.rank, detail.totalStudents));
        if (rc.failCount > 0) {
            sb.append(String.format("%-17s: %d (%s)%n", "Failing courses", rc.failCount,
                    String.join(", ", rc.failingCourses)));
        }
        sb.append(repeat('-', 78)).append('\n');
        sb.append("Generated on ").append(LocalDateTime.now().format(STAMP))
                .append(" | EduTrack Reports & Export").append('\n');
        sb.append(repeat('=', 78)).append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Course grade sheet (CSV rows, header first)
    // ---------------------------------------------------------------

    public static List<String[]> courseGradeSheet(DataStore ds, String courseCode) {
        Course course = ds.coursesByCode().get(courseCode);
        if (course == null) {
            throw new IllegalArgumentException("Unknown course code: " + courseCode);
        }
        Map<Integer, ExamRecord> recordByStudent = new LinkedHashMap<>();
        for (ExamRecord r : ds.examRecords()) {
            if (r.courseCode.equals(courseCode)) {
                recordByStudent.put(r.studentId, r);
            }
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "studentId", "name", "midsem", "endsem", "total", "grade" });
        List<Student> enrolled = new ArrayList<>();
        for (Student s : ds.students()) {
            if (s.enrolledCourses.contains(courseCode)) {
                enrolled.add(s);
            }
        }
        enrolled.sort((a, b) -> {
            int ta = totalOf(recordByStudent.get(a.id));
            int tb = totalOf(recordByStudent.get(b.id));
            if (ta != tb) {
                return Integer.compare(tb, ta);
            }
            return Integer.compare(a.id, b.id);
        });
        for (Student s : enrolled) {
            ExamRecord r = recordByStudent.get(s.id);
            if (r == null) {
                rows.add(new String[] { String.valueOf(s.id), s.name, "", "", "", "" });
            } else {
                rows.add(new String[] {
                        String.valueOf(s.id), s.name, String.valueOf(r.midsem),
                        String.valueOf(r.endsem), String.valueOf(r.total()), r.grade() });
            }
        }
        return rows;
    }

    private static int totalOf(ExamRecord r) {
        return r == null ? -1 : r.total();
    }

    // ---------------------------------------------------------------
    // Department summary (CSV rows, header first)
    // ---------------------------------------------------------------

    public static List<String[]> departmentSummary(DataStore ds) {
        Map<String, String> deptOrder = new LinkedHashMap<>();
        Map<String, Integer> coursesPerDept = new LinkedHashMap<>();
        Map<String, Integer> creditsOf = new LinkedHashMap<>();
        for (Course c : ds.courses()) {
            deptOrder.putIfAbsent(c.department, c.department);
            coursesPerDept.merge(c.department, 1, Integer::sum);
            creditsOf.put(c.code, c.credits);
        }
        Map<String, String> deptOfCourse = new LinkedHashMap<>();
        for (Course c : ds.courses()) {
            deptOfCourse.put(c.code, c.department);
        }

        Map<String, java.util.Set<Integer>> enrolledStudents = new LinkedHashMap<>();
        for (String dept : deptOrder.keySet()) {
            enrolledStudents.put(dept, new java.util.HashSet<>());
        }
        for (Student s : ds.students()) {
            for (String code : s.enrolledCourses) {
                String dept = deptOfCourse.get(code);
                if (dept != null) {
                    enrolledStudents.get(dept).add(s.id);
                }
            }
        }

        Map<String, long[]> examAgg = new LinkedHashMap<>();
        for (String dept : deptOrder.keySet()) {
            // count, totalSum, passed, pointsSum, creditSum
            examAgg.put(dept, new long[5]);
        }
        for (ExamRecord r : ds.examRecords()) {
            String dept = deptOfCourse.get(r.courseCode);
            if (dept == null) {
                continue;
            }
            long[] a = examAgg.get(dept);
            a[0]++;
            a[1] += r.total();
            if (r.passed()) {
                a[2]++;
            }
            int credits = creditsOf.getOrDefault(r.courseCode, 0);
            a[3] += (long) r.gradePoints() * credits;
            a[4] += credits;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "department", "courses", "enrolledStudents", "examRecords",
                "avgTotal", "passPercent", "avgGpa" });
        for (String dept : deptOrder.keySet()) {
            long[] a = examAgg.get(dept);
            double avgTotal = a[0] == 0 ? 0.0 : (double) a[1] / a[0];
            double passPct = a[0] == 0 ? 0.0 : 100.0 * a[2] / a[0];
            double avgGpa = a[4] == 0 ? 0.0 : (double) a[3] / a[4];
            rows.add(new String[] {
                    dept,
                    String.valueOf(coursesPerDept.get(dept)),
                    String.valueOf(enrolledStudents.get(dept).size()),
                    String.valueOf(a[0]),
                    String.format(Locale.ROOT, "%.2f", avgTotal),
                    String.format(Locale.ROOT, "%.2f", passPct),
                    String.format(Locale.ROOT, "%.2f", avgGpa) });
        }
        return rows;
    }

    // ---------------------------------------------------------------
    // At-risk report (CSV rows, header first)
    // ---------------------------------------------------------------

    public static List<String[]> atRiskReport(DataStore ds) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "studentId", "name", "program", "fails", "gpa", "failingCourses" });
        for (ExamAnalytics.StudentGpa sg : ExamAnalytics.atRisk(ds)) {
            rows.add(new String[] {
                    String.valueOf(sg.student.id), sg.student.name, sg.student.program,
                    String.valueOf(sg.failCount),
                    String.format(Locale.ROOT, "%.2f", sg.gpa),
                    String.join("; ", sg.failingCourses) });
        }
        return rows;
    }

    // ---------------------------------------------------------------
    // Persistence helpers
    // ---------------------------------------------------------------

    public static void saveText(Path path, String text) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    public static void saveCsv(Path path, List<String[]> rows) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, csvToString(rows), StandardCharsets.UTF_8);
    }

    /** Renders CSV rows with RFC-4180-style escaping (quote on , " CR LF; "" inside). */
    public static String csvToString(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escapeCsv(row[i]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    // ---------------------------------------------------------------
    // Default export file names
    // ---------------------------------------------------------------

    public static String transcriptFileName(int studentId) {
        return "transcript_" + studentId + ".txt";
    }

    public static String gradeSheetFileName(String courseCode) {
        return "gradesheet_" + courseCode + ".csv";
    }

    public static String departmentSummaryFileName() {
        return "department_summary.csv";
    }

    public static String atRiskFileName() {
        return "atrisk_report.csv";
    }

    // ---------------------------------------------------------------
    // CLI entry point (main-menu sub-module)
    // ---------------------------------------------------------------

    public static void run(Scanner sc, DataStore ds) {
        int choice;
        do {
            System.out.println("\n--- Reports & Export ---");
            System.out.println("1. Transcript");
            System.out.println("2. Course grade sheet");
            System.out.println("3. Department summary");
            System.out.println("4. At-risk report");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    transcriptCli(sc, ds);
                    break;
                case 2:
                    gradeSheetCli(sc, ds);
                    break;
                case 3:
                    csvCli(sc, departmentSummaryFileName(), () -> departmentSummary(ds));
                    break;
                case 4:
                    csvCli(sc, atRiskFileName(), () -> atRiskReport(ds));
                    break;
                case 0:
                    System.out.println("Returning to main menu...");
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    private static void transcriptCli(Scanner sc, DataStore ds) {
        System.out.print("Enter student id : ");
        int id = readInt(sc);
        String text;
        try {
            text = transcript(ds, id);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }
        printPreview(text);
        if (confirmSave(sc)) {
            Path path = Paths.get(EXPORT_DIR, transcriptFileName(id));
            try {
                saveText(path, text);
                System.out.println("Saved to " + path.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("Save failed: " + e.getMessage());
            }
        }
    }

    private static void gradeSheetCli(Scanner sc, DataStore ds) {
        System.out.print("Enter course code : ");
        String code = sc.nextLine().trim();
        List<String[]> rows;
        try {
            rows = courseGradeSheet(ds, code);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }
        printPreview(csvToString(rows));
        if (confirmSave(sc)) {
            Path path = Paths.get(EXPORT_DIR, gradeSheetFileName(code));
            try {
                saveCsv(path, rows);
                System.out.println("Saved to " + path.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("Save failed: " + e.getMessage());
            }
        }
    }

    private interface CsvWork {
        List<String[]> build();
    }

    private static void csvCli(Scanner sc, String fileName, CsvWork work) {
        List<String[]> rows = work.build();
        System.out.println((rows.size() - 1) + " data rows.");
        printPreview(csvToString(rows));
        if (confirmSave(sc)) {
            Path path = Paths.get(EXPORT_DIR, fileName);
            try {
                saveCsv(path, rows);
                System.out.println("Saved to " + path.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("Save failed: " + e.getMessage());
            }
        }
    }

    private static boolean confirmSave(Scanner sc) {
        System.out.print("Save to file? (y/n) : ");
        String answer = sc.nextLine().trim();
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    private static void printPreview(String text) {
        String[] lines = text.split("\n", -1);
        int shown = Math.min(lines.length, PREVIEW_LINES);
        System.out.println("\n--- Preview (first " + shown + " of " + lines.length + " lines) ---");
        for (int i = 0; i < shown; i++) {
            System.out.println(lines[i]);
        }
        if (lines.length > shown) {
            System.out.println("... (" + (lines.length - shown) + " more lines)");
        }
    }

    private static int readInt(Scanner sc) {
        while (!sc.hasNextInt()) {
            sc.next();
            System.out.print("Please enter a valid number : ");
        }
        int value = sc.nextInt();
        sc.nextLine();
        return value;
    }

    // ---------------------------------------------------------------
    // Small text helpers
    // ---------------------------------------------------------------

    private static String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    private static String center(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        int left = (width - text.length()) / 2;
        return repeat(' ', left) + text;
    }

    // ---------------------------------------------------------------
    // Self-test (non-interactive): java -cp bin edutrack.features.ReportGenerator
    // ---------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("--- Report Generator: Self-Test ---");
        int failures = 0;

        DataStore ds = new DataStore();

        // 1. Transcript of student 1000: header fields, course lines, GPA line.
        String t1000 = transcript(ds, 1000);
        Student s1000 = ds.studentsById().get(1000);
        failures += check("transcript(1000) contains name, program and semester lines",
                t1000.contains(s1000.name)
                        && t1000.contains("Program") && t1000.contains(s1000.program)
                        && t1000.contains("Semester") && t1000.contains(String.valueOf(s1000.semester)));
        boolean allCoursesPresent = true;
        for (String code : s1000.enrolledCourses) {
            if (!t1000.contains(code)) {
                allCoursesPresent = false;
            }
        }
        failures += check("transcript(1000) lists every enrolled course " + s1000.enrolledCourses,
                allCoursesPresent);

        ExamAnalytics.ReportCard rc = ExamAnalytics.reportCard(ds, 1000);
        double analyticsGpa = -1;
        for (ExamAnalytics.StudentGpa sg : ExamAnalytics.studentGpas(ds)) {
            if (sg.student.id == 1000) {
                analyticsGpa = sg.gpa;
            }
        }
        failures += check("transcript(1000) GPA line matches ExamAnalytics exactly",
                rc != null && rc.gpa == analyticsGpa
                        && t1000.contains(String.format(Locale.ROOT, "Weighted GPA     : %.2f", analyticsGpa)));
        RecordQueries.StudentDetail detail = RecordQueries.studentDetail(ds, 1000, 0);
        failures += check("transcript(1000) shows RecordQueries class rank",
                t1000.contains(detail.rank + " of " + detail.totalStudents));

        // 2. A CS201 enrollee's transcript contains a CS201 line.
        int cs201Student = -1;
        for (Student s : ds.students()) {
            if (s.enrolledCourses.contains("CS201")) {
                cs201Student = s.id;
                break;
            }
        }
        boolean cs201Line = cs201Student >= 0 && transcript(ds, cs201Student).contains("CS201");
        failures += check("transcript of CS201 enrollee " + cs201Student + " contains CS201", cs201Line);

        // 3. Unknown student id is rejected.
        boolean threw = false;
        try {
            transcript(ds, -1);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        failures += check("transcript of unknown id rejected", threw);

        // 4. Grade sheet for CS201: row count == enrollment, sorted by total desc.
        List<String[]> sheet = courseGradeSheet(ds, "CS201");
        int enrollment = 0;
        for (Student s : ds.students()) {
            if (s.enrolledCourses.contains("CS201")) {
                enrollment++;
            }
        }
        boolean sortedDesc = true;
        int prev = Integer.MAX_VALUE;
        for (int i = 1; i < sheet.size(); i++) {
            String totalCell = sheet.get(i)[4];
            if (totalCell.isEmpty()) {
                continue;
            }
            int total = Integer.parseInt(totalCell);
            if (total > prev) {
                sortedDesc = false;
            }
            prev = total;
        }
        boolean marksConsistent = true;
        Map<String, ExamRecord> cs201Records = new LinkedHashMap<>();
        for (ExamRecord r : ds.examRecords()) {
            if (r.courseCode.equals("CS201")) {
                cs201Records.put(String.valueOf(r.studentId), r);
            }
        }
        for (int i = 1; i < sheet.size(); i++) {
            ExamRecord r = cs201Records.get(sheet.get(i)[0]);
            if (r == null || !String.valueOf(r.midsem).equals(sheet.get(i)[2])
                    || !String.valueOf(r.endsem).equals(sheet.get(i)[3])
                    || !r.grade().equals(sheet.get(i)[5])) {
                marksConsistent = false;
            }
        }
        failures += check("gradesheet CS201 rows == enrollment (" + enrollment + "), sorted desc, marks exact",
                sheet.size() - 1 == enrollment && sortedDesc && marksConsistent
                        && sheet.get(0).length == 6 && sheet.get(0)[0].equals("studentId"));

        threw = false;
        try {
            courseGradeSheet(ds, "NOPE");
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        failures += check("gradesheet of unknown course rejected", threw);

        // 5. Department summary: departments match courses, enrollment sum == total enrollments.
        List<String[]> deptRows = departmentSummary(ds);
        Map<String, Boolean> expectedDepts = new LinkedHashMap<>();
        for (Course c : ds.courses()) {
            expectedDepts.put(c.department, Boolean.TRUE);
        }
        boolean deptMatch = deptRows.size() - 1 == expectedDepts.size();
        int enrolledSum = 0;
        int courseSum = 0;
        for (int i = 1; i < deptRows.size(); i++) {
            if (!expectedDepts.containsKey(deptRows.get(i)[0])) {
                deptMatch = false;
            }
            enrolledSum += Integer.parseInt(deptRows.get(i)[2]);
            courseSum += Integer.parseInt(deptRows.get(i)[1]);
        }
        java.util.Set<Integer> studentsWithEnrollment = new java.util.HashSet<>();
        for (Student s : ds.students()) {
            if (!s.enrolledCourses.isEmpty()) {
                studentsWithEnrollment.add(s.id);
            }
        }
        // Each department row counts distinct students enrolled in at least one course
        // from that department, so department sums may intentionally exceed the cohort size.
        Map<String, java.util.Set<Integer>> expectedEnrolledByDept = new LinkedHashMap<>();
        for (Course c : ds.courses()) {
            expectedEnrolledByDept.putIfAbsent(c.department, new java.util.HashSet<>());
        }
        for (Student s : ds.students()) {
            for (String code : s.enrolledCourses) {
                Course c = ds.coursesByCode().get(code);
                if (c != null) expectedEnrolledByDept.get(c.department).add(s.id);
            }
        }
        boolean distinctEnrollmentCounts = true;
        for (int i = 1; i < deptRows.size(); i++) {
            String dept = deptRows.get(i)[0];
            distinctEnrollmentCounts &= Integer.parseInt(deptRows.get(i)[2])
                    == expectedEnrolledByDept.get(dept).size();
        }
        failures += check("department summary covers all " + expectedDepts.size() + " departments, "
                + "distinct-student counts match, course sum == " + ds.courses().size(),
                deptMatch && distinctEnrollmentCounts && courseSum == ds.courses().size());

        // Spot-check one department against brute force.
        String spot = "Computer Science";
        long cnt = 0;
        long sum = 0;
        long passed = 0;
        for (ExamRecord r : ds.examRecords()) {
            Course c = ds.coursesByCode().get(r.courseCode);
            if (c != null && c.department.equals(spot)) {
                cnt++;
                sum += r.total();
                if (r.passed()) {
                    passed++;
                }
            }
        }
        boolean spotOk = false;
        for (int i = 1; i < deptRows.size(); i++) {
            if (deptRows.get(i)[0].equals(spot)) {
                double avg = cnt == 0 ? 0.0 : (double) sum / cnt;
                double passPct = cnt == 0 ? 0.0 : 100.0 * passed / cnt;
                spotOk = Long.parseLong(deptRows.get(i)[3]) == cnt
                        && deptRows.get(i)[4].equals(String.format(Locale.ROOT, "%.2f", avg))
                        && deptRows.get(i)[5].equals(String.format(Locale.ROOT, "%.2f", passPct));
            }
        }
        failures += check("department summary row for '" + spot + "' matches brute force", spotOk);

        // 6. At-risk report mirrors ExamAnalytics.
        List<String[]> riskRows = atRiskReport(ds);
        List<ExamAnalytics.StudentGpa> risky = ExamAnalytics.atRisk(ds);
        boolean riskMatch = riskRows.size() - 1 == risky.size();
        for (int i = 0; i < risky.size() && riskMatch; i++) {
            ExamAnalytics.StudentGpa sg = risky.get(i);
            String[] row = riskRows.get(i + 1);
            riskMatch = row[0].equals(String.valueOf(sg.student.id))
                    && row[1].equals(sg.student.name)
                    && row[3].equals(String.valueOf(sg.failCount))
                    && row[4].equals(String.format(Locale.ROOT, "%.2f", sg.gpa))
                    && row[5].equals(String.join("; ", sg.failingCourses));
        }
        failures += check("at-risk report matches ExamAnalytics (" + risky.size() + " students, order + values)",
                riskMatch);

        // 7. saveText / saveCsv round-trip in a temp dir (deleted afterwards).
        Path tempDir = null;
        boolean ioOk = false;
        try {
            tempDir = Files.createTempDirectory("edutrack-reports-test");
            Path txt = tempDir.resolve("t.txt");
            saveText(txt, t1000);
            boolean textOk = Files.readString(txt, StandardCharsets.UTF_8).equals(t1000);

            List<String[]> tricky = new ArrayList<>();
            tricky.add(new String[] { "plain", "with,comma", "with\"quote\"", "with\nnewline", "" });
            Path csv = tempDir.resolve("t.csv");
            saveCsv(csv, tricky);
            String written = Files.readString(csv, StandardCharsets.UTF_8);
            boolean csvOk = written.equals("plain,\"with,comma\",\"with\"\"quote\"\"\",\"with\nnewline\",\n");

            Path nested = tempDir.resolve("a/b/c.csv");
            saveCsv(nested, sheet);
            boolean nestedOk = Files.readString(nested, StandardCharsets.UTF_8).equals(csvToString(sheet));
            ioOk = textOk && csvOk && nestedOk;
        } catch (IOException e) {
            ioOk = false;
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
        failures += check("saveText/saveCsv round-trip (incl. escaping + parent dirs) in temp dir", ioOk);

        System.out.println();
        if (failures > 0) {
            System.out.println(failures + " check(s) FAILED");
            System.exit(1);
        }
        System.out.println("All checks passed.");
    }

    private static void deleteRecursively(Path dir) {
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort cleanup of the temp dir
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup of the temp dir
        }
    }

    private static int check(String name, boolean ok) {
        System.out.println(name + " ... " + (ok ? "PASS" : "FAIL"));
        return ok ? 0 : 1;
    }
}
