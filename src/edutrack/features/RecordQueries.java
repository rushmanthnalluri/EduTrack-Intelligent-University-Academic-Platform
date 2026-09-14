package edutrack.features;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import edutrack.data.DataStore;
import edutrack.model.ActivityEvent;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.LearningResource;
import edutrack.model.Student;
import modules.KMPSearch;

/**
 * Static query logic behind the Records Browser: case-insensitive substring
 * filters (matched with KMP) and per-entity detail bundles used by the GUI
 * detail dialogs. The non-interactive {@code main} runs a self-test that
 * cross-checks every query against brute-force recomputation.
 */
public final class RecordQueries {

    private static final double EPS = 1e-9;

    private RecordQueries() {
    }

    // ---------------------------------------------------------------
    // Filters (KMP substring matching over lowercased text)
    // ---------------------------------------------------------------

    public static List<Student> filterStudents(DataStore ds, String query) {
        String needle = normalize(query);
        List<Student> result = new ArrayList<>();
        for (Student s : ds.students()) {
            if (needle.isEmpty()
                    || kmpContains(s.name, needle)
                    || kmpContains(s.program, needle)
                    || kmpContains(String.valueOf(s.id), needle)) {
                result.add(s);
            }
        }
        return result;
    }

    public static List<Faculty> filterFaculty(DataStore ds, String query) {
        String needle = normalize(query);
        List<Faculty> result = new ArrayList<>();
        for (Faculty f : ds.faculty()) {
            if (needle.isEmpty()
                    || kmpContains(f.name, needle)
                    || kmpContains(f.department, needle)) {
                result.add(f);
            }
        }
        return result;
    }

    public static List<Course> filterCourses(DataStore ds, String query) {
        String needle = normalize(query);
        List<Course> result = new ArrayList<>();
        for (Course c : ds.courses()) {
            if (needle.isEmpty()
                    || kmpContains(c.code, needle)
                    || kmpContains(c.name, needle)
                    || kmpContains(c.department, needle)) {
                result.add(c);
            }
        }
        return result;
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean kmpContains(String text, String needleLower) {
        if (text == null) {
            return false;
        }
        return !KMPSearch.search(text.toLowerCase(Locale.ROOT), needleLower).isEmpty();
    }

    // ---------------------------------------------------------------
    // GPA / ranking
    // ---------------------------------------------------------------

    /** GPA from exam records: sum(gradePoints * credits) / sum(credits). */
    public static double gpaOf(List<ExamRecord> records, Map<String, Course> coursesByCode) {
        double points = 0.0;
        int credits = 0;
        for (ExamRecord r : records) {
            Course c = coursesByCode.get(r.courseCode);
            if (c == null) {
                continue;
            }
            points += r.gradePoints() * c.credits;
            credits += c.credits;
        }
        return credits == 0 ? 0.0 : points / credits;
    }

    /** GPA of every student, keyed by student id (0.0 when no records). */
    public static Map<Integer, Double> allGpas(DataStore ds) {
        Map<Integer, List<ExamRecord>> byStudent = new HashMap<>();
        for (ExamRecord r : ds.examRecords()) {
            byStudent.computeIfAbsent(r.studentId, k -> new ArrayList<>()).add(r);
        }
        Map<Integer, Double> gpas = new HashMap<>();
        for (Student s : ds.students()) {
            gpas.put(s.id, gpaOf(byStudent.getOrDefault(s.id, List.of()), ds.coursesByCode()));
        }
        return gpas;
    }

    // ---------------------------------------------------------------
    // Detail bundles
    // ---------------------------------------------------------------

    public static final class StudentDetail {
        public final Student student;
        public final List<Course> courses;
        public final List<ExamRecord> examRecords;
        public final double gpa;
        /** 1 = highest GPA in the cohort (ties share the better rank). */
        public final int rank;
        public final int totalStudents;
        /** Percentage of students whose GPA is strictly lower, 0..100. */
        public final double percentile;
        public final int activityTotal;
        /** Last N activity events for the student, oldest first. */
        public final List<ActivityEvent> recentActivity;

        StudentDetail(Student student, List<Course> courses, List<ExamRecord> examRecords,
                double gpa, int rank, int totalStudents, double percentile,
                int activityTotal, List<ActivityEvent> recentActivity) {
            this.student = student;
            this.courses = courses;
            this.examRecords = examRecords;
            this.gpa = gpa;
            this.rank = rank;
            this.totalStudents = totalStudents;
            this.percentile = percentile;
            this.activityTotal = activityTotal;
            this.recentActivity = recentActivity;
        }
    }

    public static StudentDetail studentDetail(DataStore ds, int studentId, int lastN) {
        Student student = ds.studentsById().get(studentId);
        if (student == null) {
            throw new IllegalArgumentException("Unknown student id: " + studentId);
        }
        List<Course> courses = new ArrayList<>();
        for (String code : student.enrolledCourses) {
            Course c = ds.coursesByCode().get(code);
            if (c != null) {
                courses.add(c);
            }
        }
        List<ExamRecord> records = new ArrayList<>();
        for (ExamRecord r : ds.examRecords()) {
            if (r.studentId == studentId) {
                records.add(r);
            }
        }
        double gpa = gpaOf(records, ds.coursesByCode());

        Map<Integer, Double> gpas = allGpas(ds);
        int higher = 0;
        int lower = 0;
        for (Map.Entry<Integer, Double> e : gpas.entrySet()) {
            if (e.getKey().intValue() == studentId) {
                continue;
            }
            if (e.getValue() > gpa + EPS) {
                higher++;
            } else if (e.getValue() < gpa - EPS) {
                lower++;
            }
        }
        int rank = 1 + higher;
        double percentile = 100.0 * lower / gpas.size();

        int activityTotal = 0;
        Deque<ActivityEvent> tail = new ArrayDeque<>(Math.max(1, lastN));
        for (ActivityEvent ev : ds.activityStream()) {
            if (ev.studentId == studentId) {
                activityTotal++;
                if (lastN > 0) {
                    if (tail.size() == lastN) {
                        tail.pollFirst();
                    }
                    tail.addLast(ev);
                }
            }
        }
        return new StudentDetail(student, courses, records, gpa, rank, gpas.size(),
                percentile, activityTotal, new ArrayList<>(tail));
    }

    public static final class CourseDetail {
        public final Course course;
        public final int enrollmentCount;
        public final List<Faculty> assignedFaculty;
        public final List<Assignment> assignments;
        public final List<LearningResource> resources;
        public final int examCount;
        public final double avgTotal;
        public final int minTotal;
        public final int maxTotal;
        public final double passPercent;

        CourseDetail(Course course, int enrollmentCount, List<Faculty> assignedFaculty,
                List<Assignment> assignments, List<LearningResource> resources,
                int examCount, double avgTotal, int minTotal, int maxTotal, double passPercent) {
            this.course = course;
            this.enrollmentCount = enrollmentCount;
            this.assignedFaculty = assignedFaculty;
            this.assignments = assignments;
            this.resources = resources;
            this.examCount = examCount;
            this.avgTotal = avgTotal;
            this.minTotal = minTotal;
            this.maxTotal = maxTotal;
            this.passPercent = passPercent;
        }
    }

    public static CourseDetail courseDetail(DataStore ds, String courseCode) {
        Course course = ds.coursesByCode().get(courseCode);
        if (course == null) {
            throw new IllegalArgumentException("Unknown course code: " + courseCode);
        }
        int enrollment = 0;
        for (Student s : ds.students()) {
            if (s.enrolledCourses.contains(courseCode)) {
                enrollment++;
            }
        }
        List<Faculty> faculty = new ArrayList<>();
        for (Faculty f : ds.faculty()) {
            if (f.expertise.contains(courseCode)) {
                faculty.add(f);
            }
        }
        List<Assignment> assignments = new ArrayList<>();
        for (Assignment a : ds.assignments()) {
            if (a.courseCode.equals(courseCode)) {
                assignments.add(a);
            }
        }
        List<LearningResource> resources = new ArrayList<>();
        for (LearningResource r : ds.resources()) {
            if (r.courseCode.equals(courseCode)) {
                resources.add(r);
            }
        }
        int count = 0;
        int passed = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double sum = 0.0;
        for (ExamRecord r : ds.examRecords()) {
            if (!r.courseCode.equals(courseCode)) {
                continue;
            }
            int total = r.total();
            count++;
            sum += total;
            min = Math.min(min, total);
            max = Math.max(max, total);
            if (r.passed()) {
                passed++;
            }
        }
        double avg = count == 0 ? 0.0 : sum / count;
        double passPct = count == 0 ? 0.0 : 100.0 * passed / count;
        return new CourseDetail(course, enrollment, faculty, assignments, resources,
                count, avg, count == 0 ? 0 : min, count == 0 ? 0 : max, passPct);
    }

    public static final class FacultyDetail {
        public final Faculty faculty;
        public final List<CourseLoad> courses;

        FacultyDetail(Faculty faculty, List<CourseLoad> courses) {
            this.faculty = faculty;
            this.courses = courses;
        }

        public static final class CourseLoad {
            public final Course course;
            public final int enrollment;

            CourseLoad(Course course, int enrollment) {
                this.course = course;
                this.enrollment = enrollment;
            }
        }
    }

    public static FacultyDetail facultyDetail(DataStore ds, int facultyId) {
        Faculty faculty = null;
        for (Faculty f : ds.faculty()) {
            if (f.id == facultyId) {
                faculty = f;
                break;
            }
        }
        if (faculty == null) {
            throw new IllegalArgumentException("Unknown faculty id: " + facultyId);
        }
        Map<String, Integer> enrollments = new HashMap<>();
        for (Student s : ds.students()) {
            for (String code : s.enrolledCourses) {
                enrollments.merge(code, 1, Integer::sum);
            }
        }
        List<FacultyDetail.CourseLoad> loads = new ArrayList<>();
        for (String code : faculty.expertise) {
            Course c = ds.coursesByCode().get(code);
            if (c != null) {
                loads.add(new FacultyDetail.CourseLoad(c, enrollments.getOrDefault(code, 0)));
            }
        }
        return new FacultyDetail(faculty, loads);
    }

    // ---------------------------------------------------------------
    // Self-test (non-interactive): java -cp bin edutrack.features.RecordQueries
    // ---------------------------------------------------------------

    public static void main(String[] args) {
        DataStore ds = new DataStore();
        int failures = 0;

        // 1. Known value: filter "data" finds CS201 (Data Structures).
        List<Course> dataCourses = filterCourses(ds, "data");
        boolean foundCs201 = false;
        for (Course c : dataCourses) {
            if (c.code.equals("CS201")) {
                foundCs201 = true;
            }
        }
        failures += check("filter 'data' finds CS201", foundCs201);

        // 2. KMP-based filters agree with naive contains() on every entity type.
        String[] queries = { "", "data", "CS", "b.tech", "Sharma", "human", "101", "zzz-none" };
        boolean consistent = true;
        for (String q : queries) {
            consistent &= sameStudentIds(filterStudents(ds, q), naiveStudents(ds, q));
            consistent &= sameCourseCodes(filterCourses(ds, q), naiveCourses(ds, q));
            consistent &= sameFacultyIds(filterFaculty(ds, q), naiveFaculty(ds, q));
        }
        failures += check("KMP filters match naive contains() over " + queries.length + " queries",
                consistent);

        // 3. Empty / blank / null queries return everything.
        failures += check("empty query returns all students/faculty/courses",
                filterStudents(ds, "").size() == ds.students().size()
                        && filterFaculty(ds, null).size() == ds.faculty().size()
                        && filterCourses(ds, "   ").size() == ds.courses().size());

        // 4. GPA of a fixed student, recomputed by hand from ds.examRecords().
        int fixedId = ds.students().get(0).id;
        double manualPoints = 0.0;
        int manualCredits = 0;
        int manualRecords = 0;
        for (ExamRecord r : ds.examRecords()) {
            if (r.studentId == fixedId) {
                Course c = ds.coursesByCode().get(r.courseCode);
                manualPoints += r.gradePoints() * c.credits;
                manualCredits += c.credits;
                manualRecords++;
            }
        }
        double manualGpa = manualPoints / manualCredits;
        StudentDetail fixed = studentDetail(ds, fixedId, 25);
        failures += check("GPA of student " + fixedId + " matches hand computation",
                Math.abs(fixed.gpa - manualGpa) < EPS);
        failures += check("student detail course/record counts consistent",
                fixed.examRecords.size() == manualRecords
                        && fixed.courses.size() == ds.studentsById().get(fixedId).enrolledCourses.size());

        // 5. Rank / percentile sanity against hand computation from allGpas().
        Map<Integer, Double> gpas = allGpas(ds);
        int bestId = -1;
        int worstId = -1;
        double best = -1.0;
        double worst = Double.MAX_VALUE;
        for (Map.Entry<Integer, Double> e : gpas.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                bestId = e.getKey();
            }
            if (e.getValue() < worst) {
                worst = e.getValue();
                worstId = e.getKey();
            }
        }
        failures += check("top GPA student has rank 1, bottom has percentile 0",
                studentDetail(ds, bestId, 0).rank == 1
                        && studentDetail(ds, worstId, 0).percentile == 0.0);

        int[] sample = { fixedId, bestId, worstId,
                ds.students().get(77).id, ds.students().get(150).id };
        boolean rankOk = true;
        for (int id : sample) {
            StudentDetail d = studentDetail(ds, id, 3);
            double g = gpas.get(id);
            int higher = 0;
            int lower = 0;
            for (Map.Entry<Integer, Double> e : gpas.entrySet()) {
                if (e.getKey().intValue() == id) {
                    continue;
                }
                if (e.getValue() > g + EPS) {
                    higher++;
                } else if (e.getValue() < g - EPS) {
                    lower++;
                }
            }
            rankOk &= d.rank == 1 + higher;
            rankOk &= Math.abs(d.percentile - 100.0 * lower / gpas.size()) < EPS;
            rankOk &= d.rank >= 1 && d.rank <= gpas.size();
            rankOk &= d.totalStudents == gpas.size();
        }
        failures += check("rank/percentile match hand computation for " + sample.length + " students",
                rankOk);

        // 6. Activity scan totals and tail match a brute-force pass.
        boolean activityOk = true;
        for (int id : new int[] { fixedId, ds.students().get(123).id }) {
            StudentDetail d = studentDetail(ds, id, 25);
            List<ActivityEvent> brute = new ArrayList<>();
            for (ActivityEvent ev : ds.activityStream()) {
                if (ev.studentId == id) {
                    brute.add(ev);
                }
            }
            activityOk &= d.activityTotal == brute.size();
            activityOk &= d.recentActivity.size() == Math.min(25, brute.size());
            int offset = brute.size() - d.recentActivity.size();
            for (int i = 0; i < d.recentActivity.size(); i++) {
                activityOk &= d.recentActivity.get(i) == brute.get(offset + i);
            }
        }
        failures += check("activity scan totals and tail match brute force", activityOk);

        // 7. Course detail (CS201) matches brute force.
        CourseDetail cs201 = courseDetail(ds, "CS201");
        int enroll = 0;
        for (Student s : ds.students()) {
            if (s.enrolledCourses.contains("CS201")) {
                enroll++;
            }
        }
        int cnt = 0;
        int passCnt = 0;
        int mn = Integer.MAX_VALUE;
        int mx = Integer.MIN_VALUE;
        double sum = 0.0;
        for (ExamRecord r : ds.examRecords()) {
            if (r.courseCode.equals("CS201")) {
                cnt++;
                sum += r.total();
                mn = Math.min(mn, r.total());
                mx = Math.max(mx, r.total());
                if (r.passed()) {
                    passCnt++;
                }
            }
        }
        failures += check("CS201 detail matches brute force",
                cs201.enrollmentCount == enroll && cs201.examCount == cnt
                        && Math.abs(cs201.avgTotal - sum / cnt) < EPS
                        && cs201.minTotal == mn && cs201.maxTotal == mx
                        && Math.abs(cs201.passPercent - 100.0 * passCnt / cnt) < EPS);
        failures += check("CS201 has faculty, 2 assignments and resources",
                !cs201.assignedFaculty.isEmpty()
                        && cs201.assignments.size() == 2
                        && !cs201.resources.isEmpty());

        // 8. Faculty detail course loads match brute force.
        Faculty f0 = ds.faculty().get(0);
        FacultyDetail fd = facultyDetail(ds, f0.id);
        boolean loadsOk = fd.courses.size() == f0.expertise.size();
        for (FacultyDetail.CourseLoad load : fd.courses) {
            int e2 = 0;
            for (Student s : ds.students()) {
                if (s.enrolledCourses.contains(load.course.code)) {
                    e2++;
                }
            }
            loadsOk &= load.enrollment == e2;
        }
        failures += check("faculty detail course loads match brute force", loadsOk);

        // 9. Unknown ids are rejected.
        boolean threwStudent = false;
        try {
            studentDetail(ds, -1, 5);
        } catch (IllegalArgumentException expected) {
            threwStudent = true;
        }
        boolean threwCourse = false;
        try {
            courseDetail(ds, "NOPE");
        } catch (IllegalArgumentException expected) {
            threwCourse = true;
        }
        boolean threwFaculty = false;
        try {
            facultyDetail(ds, -1);
        } catch (IllegalArgumentException expected) {
            threwFaculty = true;
        }
        failures += check("unknown student/course/faculty ids rejected",
                threwStudent && threwCourse && threwFaculty);

        System.out.println("---");
        if (failures > 0) {
            System.out.println("RESULT: FAIL (" + failures + " check(s) failed)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
    }

    private static int check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        return ok ? 0 : 1;
    }

    private static boolean sameStudentIds(List<Student> a, List<Student> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).id != b.get(i).id) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameCourseCodes(List<Course> a, List<Course> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).code.equals(b.get(i).code)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameFacultyIds(List<Faculty> a, List<Faculty> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).id != b.get(i).id) {
                return false;
            }
        }
        return true;
    }

    private static List<Student> naiveStudents(DataStore ds, String query) {
        String n = normalize(query);
        List<Student> out = new ArrayList<>();
        for (Student s : ds.students()) {
            if (n.isEmpty()
                    || s.name.toLowerCase(Locale.ROOT).contains(n)
                    || s.program.toLowerCase(Locale.ROOT).contains(n)
                    || String.valueOf(s.id).contains(n)) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<Course> naiveCourses(DataStore ds, String query) {
        String n = normalize(query);
        List<Course> out = new ArrayList<>();
        for (Course c : ds.courses()) {
            if (n.isEmpty()
                    || c.code.toLowerCase(Locale.ROOT).contains(n)
                    || c.name.toLowerCase(Locale.ROOT).contains(n)
                    || c.department.toLowerCase(Locale.ROOT).contains(n)) {
                out.add(c);
            }
        }
        return out;
    }

    private static List<Faculty> naiveFaculty(DataStore ds, String query) {
        String n = normalize(query);
        List<Faculty> out = new ArrayList<>();
        for (Faculty f : ds.faculty()) {
            if (n.isEmpty()
                    || f.name.toLowerCase(Locale.ROOT).contains(n)
                    || f.department.toLowerCase(Locale.ROOT).contains(n)) {
                out.add(f);
            }
        }
        return out;
    }
}
