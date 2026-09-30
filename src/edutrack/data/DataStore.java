package edutrack.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edutrack.model.ActivityEvent;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.LearningResource;
import edutrack.model.Student;

public class DataStore {

    private static final long SEED = 42L;
    private static final int STUDENT_COUNT = 200;
    private static final int ACTIVITY_COUNT = 100_000;

    private static final String[][] COURSE_DATA = {
        { "CS101", "Programming Fundamentals", "Computer Science", "4", "1" },
        { "CS102", "Object Oriented Programming", "Computer Science", "4", "2" },
        { "CS201", "Data Structures", "Computer Science", "4", "3" },
        { "CS202", "Design and Analysis of Algorithms", "Computer Science", "4", "4" },
        { "CS301", "Database Management Systems", "Computer Science", "3", "5" },
        { "CS302", "Operating Systems", "Computer Science", "4", "5" },
        { "CS303", "Computer Networks", "Computer Science", "3", "6" },
        { "CS304", "Compiler Design", "Computer Science", "3", "6" },
        { "CS401", "Machine Learning", "Computer Science", "4", "7" },
        { "CS402", "Distributed Systems", "Computer Science", "3", "8" },
        { "MA101", "Calculus and Differential Equations", "Mathematics", "4", "1" },
        { "MA201", "Linear Algebra", "Mathematics", "3", "2" },
        { "MA301", "Probability and Statistics", "Mathematics", "3", "4" },
        { "MA302", "Discrete Mathematics", "Mathematics", "3", "3" },
        { "PH101", "Engineering Physics", "Physics", "3", "1" },
        { "CH101", "Engineering Chemistry", "Chemistry", "3", "1" },
        { "EE101", "Basic Electronics", "Electrical", "3", "2" },
        { "HS101", "Communication Skills", "Humanities", "2", "1" },
        { "HS201", "Engineering Economics", "Humanities", "2", "5" },
        { "MG301", "Project Management", "Management", "3", "7" }
    };

    private static final String[] FIRST_NAMES = {
        "Aarav", "Vivaan", "Aditya", "Arjun", "Sai", "Reyansh", "Krishna", "Ishaan",
        "Rohan", "Kabir", "Dev", "Yash", "Ananya", "Diya", "Aadhya", "Myra",
        "Sara", "Ira", "Priya", "Kavya", "Anika", "Navya", "Riya", "Meera"
    };

    private static final String[] LAST_NAMES = {
        "Sharma", "Verma", "Patel", "Gupta", "Mehta", "Iyer", "Reddy", "Nair",
        "Singh", "Khan", "Das", "Kulkarni", "Joshi", "Chatterjee", "Bose", "Rao",
        "Pillai", "Agarwal", "Malhotra", "Chopra", "Bansal", "Sinha", "Menon", "Desai"
    };

    private static final String[] PROGRAMS = {
        "B.Tech CSE", "B.Tech ECE", "B.Tech ME", "B.Sc Mathematics", "B.Sc Physics"
    };

    // Course pools per program (indices into COURSE_DATA). Students mostly enroll in
    // courses of their own program, which gives the course-conflict graph a realistic
    // block structure instead of being close to complete.
    private static final int[][] PROGRAM_POOLS = {
        { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 17 },
        { 0, 1, 2, 11, 14, 16, 17, 18 },
        { 10, 14, 15, 16, 18, 19 },
        { 2, 3, 8, 10, 11, 12, 13 },
        { 12, 14, 15, 17, 18 }
    };

    private static final String[] FACULTY_NAMES = {
        "Dr. Anil Kapoor", "Dr. Sunita Rao", "Dr. Vikram Sethi", "Dr. Neha Kulkarni",
        "Dr. Rajesh Iyer", "Dr. Farah Khan", "Dr. Manoj Pillai", "Dr. Kavita Desai",
        "Dr. Suresh Menon", "Dr. Alka Chatterjee", "Dr. Pradeep Sinha", "Dr. Ritu Malhotra"
    };

    private static final String[] BOILERPLATE_SENTENCES = {
        "This assignment covers the core concepts discussed in lectures.",
        "Students must submit the report before the deadline.",
        "Plagiarism in any form will result in strict disciplinary action.",
        "The time complexity of the algorithm is analysed in detail.",
        "All answers must be handwritten and scanned clearly.",
        "Refer to the course textbook for the required background material."
    };

    private static final String[] TOPIC_SENTENCES = {
        "This assignment covers the fundamental concepts of %s.",
        "Students are expected to analyse the problem statement carefully.",
        "The solution must include proper documentation and references.",
        "Question one requires implementing the algorithm discussed in class.",
        "Question two focuses on analysing the time complexity of the solution.",
        "Question three involves comparing alternative approaches.",
        "The report should contain sample test cases and their outputs.",
        "Marks will be awarded for clarity of explanation and correctness.",
        "Late submissions will attract a penalty as per university policy.",
        "Group discussions are encouraged but copying is strictly prohibited."
    };

    private static final String[][] TEXTBOOK_DATA = {
        { "Introduction to Algorithms (CLRS)", "CS202" },
        { "Database System Concepts", "CS301" },
        { "Operating System Concepts", "CS302" },
        { "Computer Networks (Tanenbaum)", "CS303" },
        { "Compilers: Principles Techniques and Tools", "CS304" },
        { "Pattern Recognition and Machine Learning", "CS401" },
        { "Distributed Systems (Tanenbaum)", "CS402" },
        { "Calculus (James Stewart)", "MA101" },
        { "Linear Algebra Done Right", "MA201" },
        { "Introduction to Probability (Blitzstein)", "MA301" },
        { "Discrete Mathematics (Rosen)", "MA302" },
        { "Fundamentals of Physics (Halliday)", "PH101" },
        { "Organic Chemistry (Clayden)", "CH101" },
        { "Electronic Devices (Boylestad)", "EE101" },
        { "The Art of Public Speaking", "HS101" }
    };

    private static final String[] ACTIONS = {
        "LOGIN", "VIEW_COURSE", "SUBMIT_ASSIGNMENT", "DOWNLOAD_RESOURCE",
        "ATTEND_LECTURE", "TAKE_QUIZ", "LOGOUT"
    };

    private List<Student> students;
    private List<Faculty> faculty;
    private List<Course> courses;
    private List<Assignment> assignments;
    private List<LearningResource> resources;
    private final List<ActivityEvent> activityStream;
    private List<ExamRecord> examRecords;
    private Map<Integer, Student> studentsById;
    private Map<String, Course> coursesByCode;
    private final List<String> rooms;
    private final List<String> timeSlots;
    private final boolean loadedFromDisk;
    private volatile boolean dirty;
    private volatile long revision;

    public DataStore() {
        this(true);
    }

    /**
     * @param loadFromDisk when true and saved CSV data exists (see CsvStore), the core
     *                     records (students, faculty, courses, exam records) are loaded
     *                     from disk; otherwise they are generated deterministically.
     *                     Assignments, resources and the activity stream are always generated.
     */
    public DataStore(boolean loadFromDisk) {
        List<Course> coreCourses = null;
        List<Student> coreStudents = null;
        List<Faculty> coreFaculty = null;
        List<ExamRecord> coreExams = null;

        if (loadFromDisk) {
            Path dir = CsvStore.resolveDir();
            if (CsvStore.coreFilesExist(dir)) {
                try {
                    coreCourses = CsvStore.loadCourses(dir);
                    coreStudents = CsvStore.loadStudents(dir);
                    coreFaculty = Files.isRegularFile(dir.resolve(CsvStore.FACULTY_FILE))
                            ? CsvStore.loadFaculty(dir) : buildFaculty(coreCourses);
                    coreExams = Files.isRegularFile(dir.resolve(CsvStore.EXAMS_FILE))
                            ? CsvStore.loadExams(dir) : new ArrayList<>();
                } catch (Exception e) {
                    System.err.println("EduTrack: saved data unreadable (" + e.getMessage()
                            + "); falling back to generated data.");
                    coreCourses = null;
                }
            }
        }

        if (coreCourses != null) {
            try {
                validateLoadedData(coreCourses, coreStudents, coreFaculty, coreExams);
            } catch (RuntimeException e) {
                System.err.println("EduTrack: saved data failed validation (" + e.getMessage()
                        + "); falling back to generated data.");
                coreCourses = null;
            }
        }

        if (coreCourses != null) {
            this.courses = immutable(coreCourses);
            this.students = immutable(coreStudents);
            this.faculty = immutable(coreFaculty);
            this.examRecords = immutable(coreExams);
            this.loadedFromDisk = true;
        } else {
            this.courses = immutable(buildCourses());
            this.students = immutable(buildStudents(this.courses));
            this.faculty = immutable(buildFaculty(this.courses));
            this.examRecords = immutable(buildExamRecords(this.students));
            this.loadedFromDisk = false;
        }
        this.assignments = immutable(buildAssignments(this.courses));
        this.resources = immutable(buildResources(this.courses));
        this.activityStream = immutable(buildActivityStream(this.students, this.courses));
        rebuildIndexes();
        this.rooms = List.of("R101", "R102", "R103", "R201", "R202", "R203", "R301", "R401");
        this.timeSlots = List.of(
                "MON-09", "MON-11", "MON-14",
                "TUE-09", "TUE-11", "TUE-14",
                "WED-09", "WED-11", "WED-14",
                "THU-09", "THU-11", "THU-14",
                "FRI-09", "FRI-11", "FRI-14");
    }

    public List<Student> students() {
        return students;
    }

    public List<Faculty> faculty() {
        return faculty;
    }

    public List<Course> courses() {
        return courses;
    }

    public List<Assignment> assignments() {
        return assignments;
    }

    public List<LearningResource> resources() {
        return resources;
    }

    public List<ActivityEvent> activityStream() {
        return activityStream;
    }

    public List<ExamRecord> examRecords() {
        return examRecords;
    }

    public Map<Integer, Student> studentsById() {
        return studentsById;
    }

    public Map<String, Course> coursesByCode() {
        return coursesByCode;
    }

    public boolean isLoadedFromDisk() {
        return loadedFromDisk;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Monotonic in-memory data revision used to reject stale asynchronous results. */
    public long revision() {
        return revision;
    }

    /** Atomically captures every collection/index reference for one revision. */
    public synchronized DataSnapshot snapshot() {
        return new DataSnapshot(revision, loadedFromDisk,
                students, faculty, courses, assignments, resources, activityStream,
                examRecords, studentsById, coursesByCode, rooms, timeSlots);
    }

    // ------------------------------------------------------------------
    // Record management (copy-on-write: CRUD swaps in a fresh list, so
    // background readers keep a consistent snapshot — re-fetch after edits).
    // ------------------------------------------------------------------

    public synchronized void addStudent(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("Student must not be null");
        }
        if (studentsById.containsKey(student.id)) {
            throw new IllegalArgumentException("Student id already exists: " + student.id);
        }
        if (student.name == null || student.name.isBlank()) {
            throw new IllegalArgumentException("Student name must not be empty");
        }
        if (student.program.isBlank()) {
            throw new IllegalArgumentException("Student program must not be blank");
        }
        if (student.semester < 1 || student.semester > 8) {
            throw new IllegalArgumentException("Semester must be 1..8");
        }
        if (student.cgpa < 0.0 || student.cgpa > 10.0) {
            throw new IllegalArgumentException("CGPA must be 0..10");
        }
        java.util.HashSet<String> enrolledCodes = new java.util.HashSet<>();
        for (String code : student.enrolledCourses) {
            requireCourse(code);
            if (!enrolledCodes.add(code)) {
                throw new IllegalArgumentException("Student enrollments contain duplicate course: " + code);
            }
        }
        List<Student> copy = new ArrayList<>(students);
        copy.add(new Student(student.id, student.name, student.program, student.semester,
                student.cgpa, new ArrayList<>(student.enrolledCourses)));
        students = immutable(copy);
        rebuildIndexes();
        dirty = true;
        revision++;
    }

    public synchronized boolean removeStudent(int id) {
        if (!studentsById.containsKey(id)) {
            return false;
        }
        List<Student> copy = new ArrayList<>(students);
        copy.removeIf(s -> s.id == id);
        students = immutable(copy);
        List<ExamRecord> exams = new ArrayList<>(examRecords);
        exams.removeIf(r -> r.studentId == id);
        examRecords = immutable(exams);
        rebuildIndexes();
        dirty = true;
        revision++;
        return true;
    }

    public synchronized void addFaculty(Faculty member) {
        if (member == null) {
            throw new IllegalArgumentException("Faculty must not be null");
        }
        boolean idTaken = faculty.stream().anyMatch(f -> f.id == member.id);
        if (idTaken) {
            throw new IllegalArgumentException("Faculty id already exists: " + member.id);
        }
        if (member.name == null || member.name.isBlank()) {
            throw new IllegalArgumentException("Faculty name must not be empty");
        }
        if (member.department.isBlank()) {
            throw new IllegalArgumentException("Faculty department must not be blank");
        }
        java.util.HashSet<String> expertiseCodes = new java.util.HashSet<>();
        for (String code : member.expertise) {
            requireCourse(code);
            if (!expertiseCodes.add(code)) {
                throw new IllegalArgumentException("Faculty expertise contains duplicate course: " + code);
            }
        }
        List<Faculty> copy = new ArrayList<>(faculty);
        copy.add(new Faculty(member.id, member.name, member.department, new ArrayList<>(member.expertise)));
        faculty = immutable(copy);
        dirty = true;
        revision++;
    }

    public synchronized boolean removeFaculty(int id) {
        List<Faculty> copy = new ArrayList<>(faculty);
        boolean removed = copy.removeIf(f -> f.id == id);
        if (removed) {
            faculty = immutable(copy);
            dirty = true;
            revision++;
        }
        return removed;
    }

    public synchronized void addCourse(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        if (coursesByCode.containsKey(course.code)) {
            throw new IllegalArgumentException("Course code already exists: " + course.code);
        }
        if (course.name == null || course.name.isBlank()) {
            throw new IllegalArgumentException("Course name must not be empty");
        }
        if (course.department.isBlank()) {
            throw new IllegalArgumentException("Course department must not be blank");
        }
        if (course.credits <= 0 || course.semester < 1 || course.semester > 8) {
            throw new IllegalArgumentException("Credits must be > 0 and semester 1..8");
        }
        if (!course.code.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Course code must contain only letters and digits");
        }
        List<Course> copy = new ArrayList<>(courses);
        copy.add(course);
        courses = immutable(copy);

        List<Assignment> newAssignments = new ArrayList<>(assignments);
        Random assignmentRandom = new Random(SEED + 2 + course.code.hashCode());
        for (int n = 1; n <= 2; n++) {
            newAssignments.add(new Assignment("ASG-" + course.code + "-" + n, course.code,
                    course.name + " Assignment " + n,
                    buildAssignmentText(assignmentRandom, course, n)));
        }
        assignments = immutable(newAssignments);

        int nextResourceId = 1;
        for (LearningResource resource : resources) {
            if (resource.id.startsWith("RES-")) {
                try {
                    nextResourceId = Math.max(nextResourceId,
                            Integer.parseInt(resource.id.substring(4)) + 1);
                } catch (NumberFormatException ignored) {
                    // Ignore non-generated resource IDs.
                }
            }
        }
        List<LearningResource> newResources = new ArrayList<>(resources);
        newResources.add(new LearningResource("RES-" + nextResourceId,
                course.name + " Lecture Notes", "notes", course.code));
        resources = immutable(newResources);

        List<Faculty> facultyCopy = new ArrayList<>(faculty);
        for (int i = 0; i < facultyCopy.size(); i++) {
            Faculty f = facultyCopy.get(i);
            if (f.department.equals(course.department) && !f.expertise.contains(course.code)) {
                List<String> expertise = new ArrayList<>(f.expertise);
                expertise.add(course.code);
                expertise.sort(null);
                facultyCopy.set(i, new Faculty(f.id, f.name, f.department, expertise));
                break;
            }
        }
        faculty = immutable(facultyCopy);

        rebuildIndexes();
        dirty = true;
        revision++;
    }

    public synchronized boolean removeCourse(String code) {
        if (!coursesByCode.containsKey(code)) {
            return false;
        }
        List<Student> studentCopy = new ArrayList<>(students.size());
        for (Student s : students) {
            List<String> enrolled = new ArrayList<>(s.enrolledCourses);
            enrolled.remove(code);
            studentCopy.add(new Student(s.id, s.name, s.program, s.semester, s.cgpa, enrolled));
        }
        List<Faculty> facultyCopy = new ArrayList<>(faculty.size());
        for (Faculty f : faculty) {
            List<String> expertise = new ArrayList<>(f.expertise);
            expertise.remove(code);
            facultyCopy.add(new Faculty(f.id, f.name, f.department, expertise));
        }
        List<Course> courseCopy = new ArrayList<>(courses);
        courseCopy.removeIf(c -> c.code.equals(code));
        List<ExamRecord> exams = new ArrayList<>(examRecords);
        exams.removeIf(r -> r.courseCode.equals(code));
        List<Assignment> asg = new ArrayList<>(assignments);
        asg.removeIf(a -> a.courseCode.equals(code));
        List<LearningResource> res = new ArrayList<>(resources);
        res.removeIf(r -> r.courseCode.equals(code));

        students = immutable(studentCopy);
        faculty = immutable(facultyCopy);
        courses = immutable(courseCopy);
        examRecords = immutable(exams);
        assignments = immutable(asg);
        resources = immutable(res);
        rebuildIndexes();
        dirty = true;
        revision++;
        return true;
    }

    public synchronized void enroll(int studentId, String courseCode) {
        Student student = requireStudent(studentId);
        requireCourse(courseCode);
        if (!student.enrolledCourses.contains(courseCode)) {
            List<Student> copy = new ArrayList<>(students);
            List<String> enrolled = new ArrayList<>(student.enrolledCourses);
            enrolled.add(courseCode);
            enrolled.sort(null);
            copy.set(indexOfStudent(studentId),
                    new Student(student.id, student.name, student.program, student.semester, student.cgpa, enrolled));
            students = immutable(copy);
            rebuildIndexes();
            dirty = true;
            revision++;
        }
    }

    public synchronized boolean drop(int studentId, String courseCode) {
        Student student = requireStudent(studentId);
        if (!student.enrolledCourses.contains(courseCode)) {
            return false;
        }
        List<Student> copy = new ArrayList<>(students);
        List<String> enrolled = new ArrayList<>(student.enrolledCourses);
        enrolled.remove(courseCode);
        copy.set(indexOfStudent(studentId),
                new Student(student.id, student.name, student.program, student.semester, student.cgpa, enrolled));
        students = immutable(copy);
        List<ExamRecord> exams = new ArrayList<>(examRecords);
        exams.removeIf(r -> r.studentId == studentId && r.courseCode.equals(courseCode));
        examRecords = immutable(exams);
        rebuildIndexes();
        dirty = true;
        revision++;
        return true;
    }

    public synchronized void upsertExamRecord(ExamRecord record) {
        Student student = requireStudent(record.studentId);
        requireCourse(record.courseCode);
        if (!student.enrolledCourses.contains(record.courseCode)) {
            throw new IllegalArgumentException(
                    "Student " + record.studentId + " is not enrolled in " + record.courseCode);
        }
        if (record.midsem < 0 || record.midsem > ExamRecord.MIDSEM_MAX
                || record.endsem < 0 || record.endsem > ExamRecord.ENDSEM_MAX) {
            throw new IllegalArgumentException("Marks out of range (midsem 0.." + ExamRecord.MIDSEM_MAX
                    + ", endsem 0.." + ExamRecord.ENDSEM_MAX + ")");
        }
        List<ExamRecord> copy = new ArrayList<>(examRecords);
        copy.removeIf(r -> r.studentId == record.studentId && r.courseCode.equals(record.courseCode));
        copy.add(record);
        examRecords = immutable(copy);
        dirty = true;
        revision++;
    }

    public synchronized boolean removeExamRecord(int studentId, String courseCode) {
        List<ExamRecord> copy = new ArrayList<>(examRecords);
        boolean removed = copy.removeIf(r -> r.studentId == studentId && r.courseCode.equals(courseCode));
        if (removed) {
            examRecords = immutable(copy);
            dirty = true;
            revision++;
        }
        return removed;
    }

    public synchronized void saveToDisk() throws IOException {
        CsvStore.saveAll(this, CsvStore.resolveDir());
        dirty = false;
    }

    /**
     * Deletes the saved CSV files; generated data returns on the next application start.
     */
    public synchronized boolean resetToGenerated() throws IOException {
        boolean reset = CsvStore.deleteCoreFiles(CsvStore.resolveDir());
        if (reset) {
            // The live in-memory dataset is intentionally retained until restart,
            // so deleting its persisted copy means the current state is no longer
            // safely persisted and must remain marked dirty.
            dirty = true;
        }
        return reset;
    }

    private Student requireStudent(int id) {
        Student student = studentsById.get(id);
        if (student == null) {
            throw new IllegalArgumentException("Unknown student id: " + id);
        }
        return student;
    }

    private Course requireCourse(String code) {
        if (code == null || code.isBlank() || !code.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Course code must contain only letters and digits");
        }
        Course course = coursesByCode.get(code);
        if (course == null) {
            throw new IllegalArgumentException("Unknown course code: " + code);
        }
        return course;
    }

    private void rebuildIndexes() {
        Map<Integer, Student> byId = new LinkedHashMap<>();
        for (Student s : students) {
            byId.put(s.id, s);
        }
        Map<String, Course> byCode = new LinkedHashMap<>();
        for (Course c : courses) {
            byCode.put(c.code, c);
        }
        studentsById = Collections.unmodifiableMap(byId);
        coursesByCode = Collections.unmodifiableMap(byCode);
    }

    public List<String> rooms() {
        return rooms;
    }

    public List<String> timeSlots() {
        return timeSlots;
    }

    public String loadWikipediaText() throws IOException {
        Path[] candidates = {
            Paths.get("DataSets", "Wikipedia.txt"),
            Paths.get("Text_Hack", "DataSets", "Wikipedia.txt")
        };
        for (Path path : candidates) {
            if (Files.exists(path)) {
                return Files.readString(path);
            }
        }
        throw new IOException("Wikipedia.txt not found; tried: " + Arrays.toString(candidates));
    }


    private static <T> List<T> immutable(List<T> list) {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    private int indexOfStudent(int id) {
        for (int i = 0; i < students.size(); i++) {
            if (students.get(i).id == id) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown student id: " + id);
    }

    static void validateLoadedData(List<Course> courses, List<Student> students,
            List<Faculty> faculty, List<ExamRecord> exams) {
        if (courses == null || students == null || faculty == null || exams == null
                || courses.isEmpty() || students.isEmpty()) {
            throw new IllegalArgumentException("saved data must contain at least one student and one course");
        }
        java.util.HashSet<String> courseCodes = new java.util.HashSet<>();
        for (Course c : courses) {
            if (c == null || c.code == null || c.code.isBlank() || !courseCodes.add(c.code)
                    || c.name == null || c.name.isBlank() || c.department == null || c.department.isBlank()
                    || c.credits < 1 || c.credits > 6 || c.semester < 1 || c.semester > 8) {
                throw new IllegalArgumentException("invalid or duplicate course in saved data");
            }
        }
        java.util.HashSet<Integer> studentIds = new java.util.HashSet<>();
        for (Student s : students) {
            if (s == null || s.id <= 0 || !studentIds.add(s.id)
                    || s.name == null || s.name.isBlank() || s.program == null || s.program.isBlank()
                    || s.semester < 1 || s.semester > 8 || !Double.isFinite(s.cgpa)
                    || s.cgpa < 0 || s.cgpa > 10) {
                throw new IllegalArgumentException("invalid or duplicate student in saved data");
            }
            java.util.HashSet<String> enrolled = new java.util.HashSet<>();
            for (String code : s.enrolledCourses) {
                if (code == null || !courseCodes.contains(code) || !enrolled.add(code)) {
                    throw new IllegalArgumentException("invalid student course enrollment in saved data");
                }
            }
        }
        java.util.HashSet<Integer> facultyIds = new java.util.HashSet<>();
        for (Faculty f : faculty) {
            if (f == null || f.id <= 0 || !facultyIds.add(f.id)
                    || f.name == null || f.name.isBlank() || f.department == null || f.department.isBlank()) {
                throw new IllegalArgumentException("invalid or duplicate faculty in saved data");
            }
            java.util.HashSet<String> expertise = new java.util.HashSet<>();
            for (String code : f.expertise) {
                if (code == null || !courseCodes.contains(code) || !expertise.add(code)) {
                    throw new IllegalArgumentException("invalid faculty expertise in saved data");
                }
            }
        }
        java.util.HashSet<String> examKeys = new java.util.HashSet<>();
        for (ExamRecord r : exams) {
            if (r == null || !studentIds.contains(r.studentId) || !courseCodes.contains(r.courseCode)
                    || r.midsem < 0 || r.midsem > ExamRecord.MIDSEM_MAX
                    || r.endsem < 0 || r.endsem > ExamRecord.ENDSEM_MAX
                    || !studentHasCourse(students, r.studentId, r.courseCode)
                    || !examKeys.add(r.studentId + "\u0000" + r.courseCode)) {
                throw new IllegalArgumentException("invalid or duplicate exam record in saved data");
            }
        }
    }

    private static boolean studentHasCourse(List<Student> students, int studentId, String courseCode) {
        for (Student s : students) {
            if (s.id == studentId) {
                return s.enrolledCourses.contains(courseCode);
            }
        }
        return false;
    }

    private static List<Course> buildCourses() {
        List<Course> list = new ArrayList<>();
        for (String[] row : COURSE_DATA) {
            list.add(new Course(row[0], row[1], row[2], Integer.parseInt(row[3]), Integer.parseInt(row[4])));
        }
        return list;
    }

    private static List<Student> buildStudents(List<Course> courses) {
        Random rnd = new Random(SEED);
        List<Student> list = new ArrayList<>();
        for (int i = 0; i < STUDENT_COUNT; i++) {
            String name = FIRST_NAMES[i % FIRST_NAMES.length] + " "
                    + LAST_NAMES[(i / FIRST_NAMES.length + i * 7) % LAST_NAMES.length];
            int programIndex = rnd.nextInt(PROGRAMS.length);
            String program = PROGRAMS[programIndex];
            int semester = 1 + rnd.nextInt(8);
            double cgpa = Math.round((6.0 + rnd.nextDouble() * 4.0) * 100.0) / 100.0;

            int[] pool = PROGRAM_POOLS[programIndex];
            List<Course> poolCourses = new ArrayList<>();
            for (int index : pool) {
                poolCourses.add(courses.get(index));
            }
            for (int j = poolCourses.size() - 1; j > 0; j--) {
                int k = rnd.nextInt(j + 1);
                Course tmp = poolCourses.get(j);
                poolCourses.set(j, poolCourses.get(k));
                poolCourses.set(k, tmp);
            }
            int enrollCount = 4 + rnd.nextInt(3);
            List<String> enrolled = new ArrayList<>();
            for (int j = 0; j < Math.min(enrollCount, poolCourses.size()); j++) {
                enrolled.add(poolCourses.get(j).code);
            }
            enrolled.sort(null);

            list.add(new Student(1000 + i, name, program, semester, cgpa, enrolled));
        }
        return list;
    }

    private static List<Faculty> buildFaculty(List<Course> courses) {
        Random rnd = new Random(SEED + 1);
        List<LinkedHashSet<String>> expertiseSets = new ArrayList<>();
        for (int i = 0; i < FACULTY_NAMES.length; i++) {
            expertiseSets.add(new LinkedHashSet<>());
        }
        for (int i = 0; i < courses.size(); i++) {
            expertiseSets.get(i % FACULTY_NAMES.length).add(courses.get(i).code);
        }
        for (LinkedHashSet<String> set : expertiseSets) {
            int extra = 2 + rnd.nextInt(3);
            while (set.size() < extra + 1) {
                set.add(courses.get(rnd.nextInt(courses.size())).code);
            }
        }
        List<Faculty> list = new ArrayList<>();
        for (int i = 0; i < FACULTY_NAMES.length; i++) {
            LinkedHashSet<String> expertise = expertiseSets.get(i);
            String dept = courses.stream()
                    .filter(c -> expertise.contains(c.code))
                    .findFirst().get().department;
            list.add(new Faculty(500 + i, FACULTY_NAMES[i], dept, new ArrayList<>(expertise)));
        }
        return list;
    }

    private static List<Assignment> buildAssignments(List<Course> courses) {
        Random rnd = new Random(SEED + 2);
        List<Assignment> list = new ArrayList<>();
        for (Course course : courses) {
            for (int n = 1; n <= 2; n++) {
                String id = "ASG-" + course.code + "-" + n;
                String title = course.name + " Assignment " + n;
                list.add(new Assignment(id, course.code, title, buildAssignmentText(rnd, course, n)));
            }
        }
        return list;
    }

    private static String buildAssignmentText(Random rnd, Course course, int n) {
        StringBuilder sb = new StringBuilder();
        sb.append(course.name).append(" assignment ").append(n).append(". ");
        int boiler = 2 + rnd.nextInt(2);
        for (int i = 0; i < boiler; i++) {
            sb.append(BOILERPLATE_SENTENCES[rnd.nextInt(BOILERPLATE_SENTENCES.length)]).append(' ');
        }
        int topics = 4 + rnd.nextInt(3);
        for (int i = 0; i < topics; i++) {
            sb.append(String.format(TOPIC_SENTENCES[rnd.nextInt(TOPIC_SENTENCES.length)], course.name)).append(' ');
        }
        return sb.toString().trim();
    }

    private static List<LearningResource> buildResources(List<Course> courses) {
        List<LearningResource> list = new ArrayList<>();
        int seq = 1;
        for (String[] row : TEXTBOOK_DATA) {
            list.add(new LearningResource("RES-" + seq++, row[0], "textbook", row[1]));
        }
        for (Course course : courses) {
            list.add(new LearningResource("RES-" + seq++, course.name + " Lecture Notes", "notes", course.code));
        }
        return list;
    }

    private static List<ActivityEvent> buildActivityStream(List<Student> students, List<Course> courses) {
        Random rnd = new Random(SEED + 3);
        double[] weights = { 0.22, 0.22, 0.12, 0.14, 0.15, 0.10, 0.05 };
        int popularCount = Math.min(6, courses.size());

        List<ActivityEvent> list = new ArrayList<>(ACTIVITY_COUNT);
        long timestamp = 1_700_000_000_000L;
        for (int i = 0; i < ACTIVITY_COUNT; i++) {
            timestamp += 1_000 + rnd.nextInt(60_000);
            Student student = students.get(rnd.nextInt(students.size()));

            double roll = rnd.nextDouble();
            int actionIndex = 0;
            double cumulative = 0.0;
            for (int a = 0; a < weights.length; a++) {
                cumulative += weights[a];
                if (roll < cumulative) {
                    actionIndex = a;
                    break;
                }
            }
            String action = ACTIONS[actionIndex];

            String details = "-";
            if (actionIndex >= 1 && actionIndex <= 5) {
                if (rnd.nextDouble() < 0.7) {
                    details = courses.get(rnd.nextInt(popularCount)).code;
                } else {
                    details = courses.get(rnd.nextInt(courses.size())).code;
                }
            }
            list.add(new ActivityEvent(timestamp, student.id, action, details));
        }
        return list;
    }

    private static List<ExamRecord> buildExamRecords(List<Student> students) {
        Random rnd = new Random(SEED + 4);
        List<ExamRecord> list = new ArrayList<>();
        for (Student s : students) {
            double ability = (s.cgpa - 6.0) / 4.0;
            for (String code : s.enrolledCourses) {
                int midsem = clampMarks(ExamRecord.MIDSEM_MAX
                        * (0.35 + 0.55 * ability + rnd.nextGaussian() * 0.12), ExamRecord.MIDSEM_MAX);
                int endsem = clampMarks(ExamRecord.ENDSEM_MAX
                        * (0.33 + 0.57 * ability + rnd.nextGaussian() * 0.13), ExamRecord.ENDSEM_MAX);
                list.add(new ExamRecord(s.id, code, midsem, endsem));
            }
        }
        return list;
    }

    private static int clampMarks(double value, int max) {
        long rounded = Math.round(value);
        return (int) Math.max(Math.round(max * 0.03), Math.min(max, rounded));
    }
}
