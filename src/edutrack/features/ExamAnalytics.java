package edutrack.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import edutrack.data.DataStore;
import edutrack.data.DataSnapshot;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Student;
import edutrack.modules.M6RandomizedQuickSort;

public class ExamAnalytics {

    public static final String[] GRADES = { "AA", "AB", "BB", "BC", "CC", "DD", "F" };

    private static final long SORT_SEED = 42L;

    public static class CourseStats {
        public final String code;
        public final String name;
        public final int students;
        public final double avgTotal;
        public final int minTotal;
        public final int maxTotal;
        public final double passPercent;
        public final int[] gradeCounts;

        CourseStats(String code, String name, int students, double avgTotal, int minTotal, int maxTotal,
                double passPercent, int[] gradeCounts) {
            this.code = code;
            this.name = name;
            this.students = students;
            this.avgTotal = avgTotal;
            this.minTotal = minTotal;
            this.maxTotal = maxTotal;
            this.passPercent = passPercent;
            this.gradeCounts = gradeCounts;
        }
    }

    public static class StudentGpa {
        public final Student student;
        public final double gpa;
        public final int credits;
        public final int failCount;
        public final List<String> failingCourses;

        StudentGpa(Student student, double gpa, int credits, int failCount, List<String> failingCourses) {
            this.student = student;
            this.gpa = gpa;
            this.credits = credits;
            this.failCount = failCount;
            this.failingCourses = failingCourses;
        }
    }

    public static class ReportCard {
        public final Student student;
        public final List<ExamRecord> records;
        public final double gpa;
        public final int credits;
        public final int failCount;
        public final List<String> failingCourses;

        ReportCard(Student student, List<ExamRecord> records, double gpa, int credits, int failCount,
                List<String> failingCourses) {
            this.student = student;
            this.records = records;
            this.gpa = gpa;
            this.credits = credits;
            this.failCount = failCount;
            this.failingCourses = failingCourses;
        }
    }

    private ExamAnalytics() {
    }

    public static List<CourseStats> courseStats(DataStore ds) {
        Map<String, Accum> byCourse = new LinkedHashMap<>();
        for (Course c : ds.courses()) {
            byCourse.put(c.code, new Accum());
        }
        for (ExamRecord r : ds.examRecords()) {
            Accum a = byCourse.get(r.courseCode);
            if (a != null) {
                a.add(r);
            }
        }
        Map<String, Course> coursesByCode = ds.coursesByCode();
        List<CourseStats> out = new ArrayList<>();
        for (Map.Entry<String, Accum> e : byCourse.entrySet()) {
            Course c = coursesByCode.get(e.getKey());
            out.add(e.getValue().toCourseStats(e.getKey(), c == null ? e.getKey() : c.name));
        }
        return out;
    }

    /** Snapshot-safe course statistics for multi-collection API reads. */
    public static List<CourseStats> courseStats(DataSnapshot snapshot) {
        Map<String, Accum> byCourse = new LinkedHashMap<>();
        for (Course c : snapshot.courses()) {
            byCourse.put(c.code, new Accum());
        }
        for (ExamRecord r : snapshot.examRecords()) {
            Accum a = byCourse.get(r.courseCode);
            if (a != null) {
                a.add(r);
            }
        }
        List<CourseStats> out = new ArrayList<>();
        for (Map.Entry<String, Accum> e : byCourse.entrySet()) {
            Course c = snapshot.coursesByCode().get(e.getKey());
            out.add(e.getValue().toCourseStats(e.getKey(), c == null ? e.getKey() : c.name));
        }
        return out;
    }

    public static int[] overallGradeDistribution(DataStore ds) {
        int[] counts = new int[GRADES.length];
        for (ExamRecord r : ds.examRecords()) {
            counts[gradeIndex(r.grade())]++;
        }
        return counts;
    }

    public static List<StudentGpa> studentGpas(DataStore ds) {
        Map<Integer, List<ExamRecord>> byStudent = new LinkedHashMap<>();
        for (Student s : ds.students()) {
            byStudent.put(s.id, new ArrayList<>());
        }
        for (ExamRecord r : ds.examRecords()) {
            List<ExamRecord> list = byStudent.get(r.studentId);
            if (list != null) {
                list.add(r);
            }
        }
        Map<String, Course> coursesByCode = ds.coursesByCode();
        List<StudentGpa> out = new ArrayList<>();
        for (Student s : ds.students()) {
            out.add(computeGpa(s, byStudent.get(s.id), coursesByCode));
        }
        return out;
    }

    public static List<StudentGpa> toppers(DataStore ds, int n) {
        List<StudentGpa> all = studentGpas(ds);
        StudentGpa[] arr = all.toArray(new StudentGpa[0]);
        randomizedSort(arr, TOPPER_ORDER, new Random(SORT_SEED));
        int limit = Math.max(0, Math.min(n, arr.length));
        return new ArrayList<>(Arrays.asList(arr).subList(0, limit));
    }

    public static List<StudentGpa> atRisk(DataStore ds) {
        List<StudentGpa> risky = new ArrayList<>();
        for (StudentGpa sg : studentGpas(ds)) {
            if (sg.failCount >= 2 || sg.gpa < 5.0) {
                risky.add(sg);
            }
        }
        StudentGpa[] arr = risky.toArray(new StudentGpa[0]);
        randomizedSort(arr, WORST_FIRST_ORDER, new Random(SORT_SEED));
        return new ArrayList<>(Arrays.asList(arr));
    }

    public static ReportCard reportCard(DataStore ds, int studentId) {
        Student s = ds.studentsById().get(studentId);
        if (s == null) {
            return null;
        }
        List<ExamRecord> records = new ArrayList<>();
        for (ExamRecord r : ds.examRecords()) {
            if (r.studentId == studentId) {
                records.add(r);
            }
        }
        StudentGpa sg = computeGpa(s, records, ds.coursesByCode());
        return new ReportCard(s, records, sg.gpa, sg.credits, sg.failCount, sg.failingCourses);
    }

    /** Snapshot-safe report card for multi-collection API reads. */
    public static ReportCard reportCard(DataSnapshot snapshot, int studentId) {
        Student s = snapshot.studentsById().get(studentId);
        if (s == null) {
            return null;
        }
        List<ExamRecord> records = new ArrayList<>();
        for (ExamRecord r : snapshot.examRecords()) {
            if (r.studentId == studentId) {
                records.add(r);
            }
        }
        StudentGpa sg = computeGpa(s, records, snapshot.coursesByCode());
        return new ReportCard(s, records, sg.gpa, sg.credits, sg.failCount, sg.failingCourses);
    }

    private static StudentGpa computeGpa(Student s, List<ExamRecord> records, Map<String, Course> coursesByCode) {
        long points = 0;
        int credits = 0;
        int fails = 0;
        List<String> failing = new ArrayList<>();
        for (ExamRecord r : records) {
            Course c = coursesByCode.get(r.courseCode);
            int cr = c == null ? 0 : c.credits;
            points += (long) r.gradePoints() * cr;
            credits += cr;
            if (!r.passed()) {
                fails++;
                failing.add(r.courseCode);
            }
        }
        double gpa = credits == 0 ? 0.0 : points / (double) credits;
        return new StudentGpa(s, gpa, credits, fails, failing);
    }

    private static int gradeIndex(String grade) {
        for (int i = 0; i < GRADES.length; i++) {
            if (GRADES[i].equals(grade)) {
                return i;
            }
        }
        return GRADES.length - 1;
    }

    private static final Comparator<StudentGpa> TOPPER_ORDER = (x, y) -> {
        int c = Double.compare(y.gpa, x.gpa);
        if (c != 0) {
            return c;
        }
        c = x.student.name.compareTo(y.student.name);
        if (c != 0) {
            return c;
        }
        return Integer.compare(x.student.id, y.student.id);
    };

    private static final Comparator<StudentGpa> WORST_FIRST_ORDER = (x, y) -> {
        int c = Double.compare(x.gpa, y.gpa);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(y.failCount, x.failCount);
        if (c != 0) {
            return c;
        }
        return Integer.compare(x.student.id, y.student.id);
    };

    // M6RandomizedQuickSort's public API only covers double[] and Student-by-CGPA;
    // GPA ranking needs a different comparator, so this mirrors its approach here:
    // uniform random pivot, 3-way partition, recursion into the smaller side only.
    private static void randomizedSort(StudentGpa[] a, Comparator<StudentGpa> cmp, Random rnd) {
        sort(a, 0, a.length - 1, cmp, rnd);
    }

    private static void sort(StudentGpa[] a, int lo, int hi, Comparator<StudentGpa> cmp, Random rnd) {
        while (lo < hi) {
            int pivotIndex = lo + rnd.nextInt(hi - lo + 1);
            swap(a, pivotIndex, lo);
            StudentGpa pivot = a[lo];
            int lt = lo;
            int i = lo;
            int gt = hi;
            while (i <= gt) {
                int c = cmp.compare(a[i], pivot);
                if (c < 0) {
                    swap(a, lt++, i++);
                } else if (c > 0) {
                    swap(a, i, gt--);
                } else {
                    i++;
                }
            }
            if (lt - lo < hi - gt) {
                sort(a, lo, lt - 1, cmp, rnd);
                lo = gt + 1;
            } else {
                sort(a, gt + 1, hi, cmp, rnd);
                hi = lt - 1;
            }
        }
    }

    private static void swap(StudentGpa[] a, int i, int j) {
        StudentGpa tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    private static class Accum {
        int count;
        long sum;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int passed;
        int[] gradeCounts = new int[GRADES.length];

        void add(ExamRecord r) {
            count++;
            sum += r.total();
            min = Math.min(min, r.total());
            max = Math.max(max, r.total());
            if (r.passed()) {
                passed++;
            }
            gradeCounts[gradeIndex(r.grade())]++;
        }

        CourseStats toCourseStats(String code, String name) {
            double avg = count == 0 ? 0.0 : (double) sum / count;
            double passPct = count == 0 ? 0.0 : 100.0 * passed / count;
            return new CourseStats(code, name, count, avg, count == 0 ? 0 : min, count == 0 ? 0 : max,
                    passPct, gradeCounts.clone());
        }
    }

    public static void run(Scanner sc, DataStore ds) {
        int choice;

        do {
            System.out.println("\n--- Exam Analytics ---");
            System.out.println("1. Course statistics");
            System.out.println("2. Toppers");
            System.out.println("3. At-risk students");
            System.out.println("4. Student report card");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    showCourseStats(ds);
                    break;
                case 2:
                    showToppers(sc, ds);
                    break;
                case 3:
                    showAtRisk(ds);
                    break;
                case 4:
                    showReportCard(sc, ds);
                    break;
                case 0:
                    System.out.println("Returning to main menu...");
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    private static void showCourseStats(DataStore ds) {
        long start = System.nanoTime();
        List<CourseStats> stats = courseStats(ds);
        int[] overall = overallGradeDistribution(ds);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\n--- Course Statistics (" + ds.examRecords().size() + " exam records) ---");
        System.out.printf("%-6s %-28.28s %4s %6s %4s %4s %6s %3s %3s %3s %3s %3s %3s %3s%n",
                "Code", "Course", "Stu", "Avg", "Min", "Max", "Pass%",
                GRADES[0], GRADES[1], GRADES[2], GRADES[3], GRADES[4], GRADES[5], GRADES[6]);
        for (CourseStats cs : stats) {
            System.out.printf("%-6s %-28.28s %4d %6.1f %4d %4d %6.1f %3d %3d %3d %3d %3d %3d %3d%n",
                    cs.code, cs.name, cs.students, cs.avgTotal, cs.minTotal, cs.maxTotal, cs.passPercent,
                    cs.gradeCounts[0], cs.gradeCounts[1], cs.gradeCounts[2], cs.gradeCounts[3],
                    cs.gradeCounts[4], cs.gradeCounts[5], cs.gradeCounts[6]);
        }
        StringBuilder dist = new StringBuilder("Overall grade distribution : ");
        for (int i = 0; i < GRADES.length; i++) {
            dist.append(GRADES[i]).append('=').append(overall[i]);
            if (i < GRADES.length - 1) {
                dist.append("  ");
            }
        }
        System.out.println(dist);
        System.out.println("Time taken : " + elapsedMs + " ms");
    }

    private static void showToppers(Scanner sc, DataStore ds) {
        int n = readIntWithDefault(sc, "Top N (default 10) : ", 10);

        long start = System.nanoTime();
        List<StudentGpa> tops = toppers(ds, n);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\n--- Toppers by GPA (gradePoints * credits / credits) ---");
        System.out.printf("%4s %-6s %-25s %-18s %6s %6s%n", "Rank", "ID", "Name", "Program", "GPA", "CGPA");
        int rank = 1;
        for (StudentGpa sg : tops) {
            System.out.printf("%4d %-6d %-25.25s %-18s %6.2f %6.2f%n",
                    rank++, sg.student.id, sg.student.name, sg.student.program, sg.gpa, sg.student.cgpa);
        }
        System.out.println("Time taken : " + elapsedMs + " ms");
    }

    private static void showAtRisk(DataStore ds) {
        long start = System.nanoTime();
        List<StudentGpa> risky = atRisk(ds);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\n--- At-Risk Students (>=2 F grades or GPA < 5.0): " + risky.size() + " found ---");
        System.out.printf("%-6s %-25s %-18s %5s %6s  %s%n", "ID", "Name", "Program", "Fails", "GPA",
                "Failing courses");
        int limit = Math.min(risky.size(), 30);
        for (int i = 0; i < limit; i++) {
            StudentGpa sg = risky.get(i);
            System.out.printf("%-6d %-25.25s %-18s %5d %6.2f  %s%n",
                    sg.student.id, sg.student.name, sg.student.program, sg.failCount, sg.gpa,
                    String.join(", ", sg.failingCourses));
        }
        if (risky.size() > limit) {
            System.out.println("... (" + (risky.size() - limit) + " more)");
        }
        System.out.println("Time taken : " + elapsedMs + " ms");
    }

    private static void showReportCard(Scanner sc, DataStore ds) {
        System.out.print("Enter student id : ");
        int id = readInt(sc);

        long start = System.nanoTime();
        ReportCard rc = reportCard(ds, id);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        if (rc == null) {
            System.out.println("\nNo student with id " + id + ".");
            return;
        }
        System.out.println("\n--- Report Card: " + rc.student.name + " (id=" + rc.student.id + ", "
                + rc.student.program + ", sem " + rc.student.semester + ") ---");
        System.out.printf("%-6s %-28.28s %7s %8s %5s %-5s %6s%n",
                "Code", "Course", "Mid/30", "End/70", "Total", "Grade", "Points");
        Map<String, Course> coursesByCode = ds.coursesByCode();
        for (ExamRecord r : rc.records) {
            Course c = coursesByCode.get(r.courseCode);
            System.out.printf("%-6s %-28.28s %7d %8d %5d %-5s %6d%n",
                    r.courseCode, c == null ? r.courseCode : c.name, r.midsem, r.endsem, r.total(),
                    r.grade(), r.gradePoints());
        }
        System.out.printf("GPA : %.2f  (credits: %d, fails: %d)%n", rc.gpa, rc.credits, rc.failCount);
        System.out.println("Time taken : " + elapsedMs + " ms");
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

    private static int readIntWithDefault(Scanner sc, String prompt, int defaultValue) {
        System.out.print(prompt);
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static void main(String[] args) {
        System.out.println("--- Exam Analytics: Self-Test ---");
        int failures = 0;

        DataStore ds = new DataStore();
        List<ExamRecord> records = ds.examRecords();

        failures += report("Exam record count is the promised 981", records.size() == 981);

        String code = null;
        for (Course c : ds.courses()) {
            String candidate = c.code;
            for (ExamRecord r : records) {
                if (r.courseCode.equals(candidate)) {
                    code = candidate;
                    break;
                }
            }
            if (code != null) {
                break;
            }
        }
        CourseStats stats = null;
        for (CourseStats cs : courseStats(ds)) {
            if (cs.code.equals(code)) {
                stats = cs;
            }
        }
        int bn = 0;
        long bsum = 0;
        int bmin = Integer.MAX_VALUE;
        int bmax = Integer.MIN_VALUE;
        int bpassed = 0;
        int[] bgrades = new int[GRADES.length];
        for (ExamRecord r : records) {
            if (!r.courseCode.equals(code)) {
                continue;
            }
            bn++;
            bsum += r.total();
            bmin = Math.min(bmin, r.total());
            bmax = Math.max(bmax, r.total());
            if (r.passed()) {
                bpassed++;
            }
            bgrades[gradeIndex(r.grade())]++;
        }
        double bavg = (double) bsum / bn;
        double bpass = 100.0 * bpassed / bn;
        boolean statsMatch = stats != null && stats.students == bn && stats.avgTotal == bavg
                && stats.minTotal == bmin && stats.maxTotal == bmax && stats.passPercent == bpass
                && Arrays.equals(stats.gradeCounts, bgrades);
        failures += report("Course stats for " + code + " match independent brute-force pass (exactly)", statsMatch);

        int id = ds.students().get(0).id;
        long pts = 0;
        int cr = 0;
        for (ExamRecord r : records) {
            if (r.studentId != id) {
                continue;
            }
            int credits = ds.coursesByCode().get(r.courseCode).credits;
            pts += (long) r.gradePoints() * credits;
            cr += credits;
        }
        double expectedGpa = pts / (double) cr;
        StudentGpa byHand = null;
        for (StudentGpa sg : studentGpas(ds)) {
            if (sg.student.id == id) {
                byHand = sg;
            }
        }
        failures += report("GPA of student " + id + " matches hand computation (exactly)",
                byHand != null && byHand.gpa == expectedGpa);

        int topN = 15;
        List<StudentGpa> tops = toppers(ds, topN);
        boolean ordered = tops.size() == topN;
        for (int i = 1; i < tops.size() && ordered; i++) {
            StudentGpa x = tops.get(i - 1);
            StudentGpa y = tops.get(i);
            if (Double.compare(y.gpa, x.gpa) > 0) {
                ordered = false;
            } else if (x.gpa == y.gpa && x.student.name.compareTo(y.student.name) > 0) {
                ordered = false;
            }
        }
        failures += report("Toppers ordering valid (non-increasing GPA, name asc on ties)", ordered);

        List<StudentGpa> all = studentGpas(ds);
        double[] negated = new double[all.size()];
        for (int i = 0; i < all.size(); i++) {
            negated[i] = -all.get(i).gpa;
        }
        M6RandomizedQuickSort.sort(negated, new Random(42L));
        boolean valuesMatch = true;
        for (int i = 0; i < topN; i++) {
            if (-negated[i] != tops.get(i).gpa) {
                valuesMatch = false;
            }
        }
        failures += report("Toppers GPA values match independent M6RandomizedQuickSort ranking", valuesMatch);

        Set<Integer> expectedRisk = new HashSet<>();
        Map<Integer, List<ExamRecord>> byStudent = new LinkedHashMap<>();
        for (ExamRecord r : records) {
            byStudent.computeIfAbsent(r.studentId, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<Integer, List<ExamRecord>> e : byStudent.entrySet()) {
            long p = 0;
            int c = 0;
            int f = 0;
            for (ExamRecord r : e.getValue()) {
                int credits = ds.coursesByCode().get(r.courseCode).credits;
                p += (long) r.gradePoints() * credits;
                c += credits;
                if (!r.passed()) {
                    f++;
                }
            }
            double g = c == 0 ? 0.0 : p / (double) c;
            if (f >= 2 || g < 5.0) {
                expectedRisk.add(e.getKey());
            }
        }
        Set<Integer> actualRisk = new HashSet<>();
        for (StudentGpa sg : atRisk(ds)) {
            actualRisk.add(sg.student.id);
        }
        failures += report("At-risk membership matches independent filter", expectedRisk.equals(actualRisk));

        int[] dist = overallGradeDistribution(ds);
        int distSum = 0;
        for (int v : dist) {
            distSum += v;
        }
        failures += report("Overall grade distribution sums to " + records.size(), distSum == records.size());

        int perCourseSum = 0;
        for (CourseStats cs : courseStats(ds)) {
            for (int v : cs.gradeCounts) {
                perCourseSum += v;
            }
        }
        failures += report("Per-course grade counts sum to " + records.size(), perCourseSum == records.size());

        ReportCard rc = reportCard(ds, id);
        int recordCount = 0;
        for (ExamRecord r : records) {
            if (r.studentId == id) {
                recordCount++;
            }
        }
        failures += report("Report card rows and GPA consistent for student " + id,
                rc != null && rc.records.size() == recordCount && rc.gpa == expectedGpa);

        failures += report("Unknown student id yields no report card", reportCard(ds, -1) == null);

        System.out.println();
        if (failures > 0) {
            System.out.println(failures + " check(s) FAILED");
            System.exit(1);
        }
        System.out.println("All checks passed.");
    }

    private static int report(String name, boolean ok) {
        System.out.println(name + " ... " + (ok ? "PASS" : "FAIL"));
        return ok ? 0 : 1;
    }
}
