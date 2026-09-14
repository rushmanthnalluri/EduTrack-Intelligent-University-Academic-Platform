package edutrack.features;

import edutrack.data.DataStore;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.Student;

/**
 * Validation and suggestion helpers backing the Manage Records (CRUD) panel.
 * Validators return null when the input is acceptable, otherwise a short
 * human-readable error message describing the first problem found.
 *
 * main() is a non-interactive self-test (PASS/FAIL lines, exit 1 on failure);
 * it never calls saveToDisk() or resetToGenerated().
 */
public final class ManageSupport {

    private ManageSupport() {
    }

    /** @return one more than the largest existing student id (1 when empty). */
    public static int nextFreeStudentId(DataStore ds) {
        int max = 0;
        for (Student s : ds.students()) {
            max = Math.max(max, s.id);
        }
        return max + 1;
    }

    /** @return one more than the largest existing faculty id (1 when empty). */
    public static int nextFreeFacultyId(DataStore ds) {
        int max = 0;
        for (Faculty f : ds.faculty()) {
            max = Math.max(max, f.id);
        }
        return max + 1;
    }

    /**
     * Base course-code suggestion without a store check: uppercase initials of
     * the course name plus "101" ("Quantum Computing" -> "QC101").
     */
    public static String suggestCourseCode(String name) {
        return initials(name) + "101";
    }

    /**
     * Store-checked course-code suggestion: uppercase initials plus the first
     * free number starting at 101 ("Quantum Computing" -> QC101, or QC102 when
     * QC101 is taken, and so on).
     */
    public static String suggestCourseCode(DataStore ds, String name) {
        String prefix = initials(name);
        for (int n = 101; n < 100_000; n++) {
            String candidate = prefix + n;
            if (!ds.coursesByCode().containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No free course code for prefix " + prefix);
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            for (String word : name.trim().split("\\s+")) {
                if (!word.isEmpty() && Character.isLetter(word.charAt(0))) {
                    sb.append(Character.toUpperCase(word.charAt(0)));
                }
                if (sb.length() >= 4) {
                    break;
                }
            }
        }
        return sb.length() == 0 ? "X" : sb.toString();
    }

    /** @return null when valid, otherwise an error message. */
    public static String validateStudent(int id, String name, int semester, double cgpa) {
        if (id <= 0) {
            return "Student id must be a positive number";
        }
        if (name == null || name.isBlank()) {
            return "Student name must not be empty";
        }
        if (semester < 1 || semester > 8) {
            return "Semester must be 1..8";
        }
        if (cgpa < 0.0 || cgpa > 10.0) {
            return "CGPA must be 0..10";
        }
        return null;
    }

    /** @return null when valid, otherwise an error message. */
    public static String validateFaculty(int id, String name) {
        if (id <= 0) {
            return "Faculty id must be a positive number";
        }
        if (name == null || name.isBlank()) {
            return "Faculty name must not be empty";
        }
        return null;
    }

    /** @return null when valid, otherwise an error message. */
    public static String validateCourse(String code, String name, int credits, int semester) {
        if (code == null || code.isBlank()) {
            return "Course code must not be empty";
        }
        if (!code.matches("[A-Za-z0-9]+")) {
            return "Course code must be letters and digits only (no spaces)";
        }
        if (name == null || name.isBlank()) {
            return "Course name must not be empty";
        }
        if (credits < 1 || credits > 6) {
            return "Credits must be 1..6";
        }
        if (semester < 1 || semester > 8) {
            return "Semester must be 1..8";
        }
        return null;
    }

    /** @return null when valid, otherwise an error message. */
    public static String validateMarks(int midsem, int endsem) {
        if (midsem < 0 || midsem > ExamRecord.MIDSEM_MAX) {
            return "Midsem marks must be 0.." + ExamRecord.MIDSEM_MAX;
        }
        if (endsem < 0 || endsem > ExamRecord.ENDSEM_MAX) {
            return "Endsem marks must be 0.." + ExamRecord.ENDSEM_MAX;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Non-interactive self-test: java -cp bin edutrack.features.ManageSupport
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        int failures = 0;
        DataStore ds = new DataStore();

        int expectedStudent = ds.students().stream().mapToInt(s -> s.id).max().orElse(0) + 1;
        int nextStudent = nextFreeStudentId(ds);
        failures += report("nextFreeStudentId = max+1 on real store (" + nextStudent + ")",
                nextStudent == expectedStudent && !ds.studentsById().containsKey(nextStudent));

        int expectedFaculty = ds.faculty().stream().mapToInt(f -> f.id).max().orElse(0) + 1;
        int nextFaculty = nextFreeFacultyId(ds);
        boolean facultyIdFree = ds.faculty().stream().noneMatch(f -> f.id == nextFaculty);
        failures += report("nextFreeFacultyId = max+1 on real store (" + nextFaculty + ")",
                nextFaculty == expectedFaculty && facultyIdFree);

        String qc = suggestCourseCode(ds, "Quantum Computing");
        failures += report("suggestCourseCode(ds, 'Quantum Computing') unique, QC-prefixed (" + qc + ")",
                qc.startsWith("QC") && !ds.coursesByCode().containsKey(qc));
        if (!ds.isLoadedFromDisk()) {
            failures += report("suggestCourseCode(ds, 'Quantum Computing') = QC101 on generated store",
                    qc.equals("QC101"));
        }

        String cs = suggestCourseCode(ds, "Cyber Security");
        failures += report("suggestCourseCode skips taken CS codes (" + cs + ")",
                cs.startsWith("CS") && !ds.coursesByCode().containsKey(cs));

        failures += report("suggestCourseCode(name) base suggestion = QC101",
                suggestCourseCode("Quantum Computing").equals("QC101"));

        String blank = suggestCourseCode(ds, "   ");
        failures += report("suggestCourseCode(ds, blank) still free (" + blank + ")",
                blank.startsWith("X") && !ds.coursesByCode().containsKey(blank));

        failures += report("validateStudent accepts valid input",
                validateStudent(1200, "Test Student", 3, 8.5) == null);
        failures += report("validateStudent rejects id 0", validateStudent(0, "Test", 3, 8.5) != null);
        failures += report("validateStudent rejects negative id", validateStudent(-5, "Test", 3, 8.5) != null);
        failures += report("validateStudent rejects blank name", validateStudent(1200, "  ", 3, 8.5) != null);
        failures += report("validateStudent rejects null name", validateStudent(1200, null, 3, 8.5) != null);
        failures += report("validateStudent rejects semester 0", validateStudent(1200, "Test", 0, 8.5) != null);
        failures += report("validateStudent rejects semester 9", validateStudent(1200, "Test", 9, 8.5) != null);
        failures += report("validateStudent rejects cgpa -0.5", validateStudent(1200, "Test", 3, -0.5) != null);
        failures += report("validateStudent rejects cgpa 10.5", validateStudent(1200, "Test", 3, 10.5) != null);
        failures += report("validateStudent accepts boundary (sem 8, cgpa 10)",
                validateStudent(1, "T", 8, 10.0) == null);

        failures += report("validateFaculty accepts valid input", validateFaculty(600, "Dr. Test") == null);
        failures += report("validateFaculty rejects id 0", validateFaculty(0, "Dr. Test") != null);
        failures += report("validateFaculty rejects blank name", validateFaculty(600, " ") != null);

        failures += report("validateCourse accepts valid input",
                validateCourse("QC101", "Quantum Computing", 4, 3) == null);
        failures += report("validateCourse rejects blank code", validateCourse(" ", "Name", 4, 3) != null);
        failures += report("validateCourse rejects code with space", validateCourse("QC 101", "Name", 4, 3) != null);
        failures += report("validateCourse rejects blank name", validateCourse("QC101", "", 4, 3) != null);
        failures += report("validateCourse rejects credits 0", validateCourse("QC101", "Name", 0, 3) != null);
        failures += report("validateCourse rejects credits 7", validateCourse("QC101", "Name", 7, 3) != null);
        failures += report("validateCourse rejects semester 9", validateCourse("QC101", "Name", 4, 9) != null);

        failures += report("validateMarks accepts (0,0)", validateMarks(0, 0) == null);
        failures += report("validateMarks accepts (30,70)",
                validateMarks(ExamRecord.MIDSEM_MAX, ExamRecord.ENDSEM_MAX) == null);
        failures += report("validateMarks rejects midsem -1", validateMarks(-1, 50) != null);
        failures += report("validateMarks rejects midsem 31",
                validateMarks(ExamRecord.MIDSEM_MAX + 1, 50) != null);
        failures += report("validateMarks rejects endsem -1", validateMarks(15, -1) != null);
        failures += report("validateMarks rejects endsem 71",
                validateMarks(15, ExamRecord.ENDSEM_MAX + 1) != null);

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
