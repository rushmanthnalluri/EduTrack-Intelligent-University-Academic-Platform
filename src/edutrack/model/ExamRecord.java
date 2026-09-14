package edutrack.model;

public class ExamRecord {

    public static final int MIDSEM_MAX = 30;
    public static final int ENDSEM_MAX = 70;
    public static final int PASS_TOTAL = 40;

    public final int studentId;
    public final String courseCode;
    public final int midsem;
    public final int endsem;

    public ExamRecord(int studentId, String courseCode, int midsem, int endsem) {
        this.studentId = studentId;
        this.courseCode = courseCode;
        this.midsem = midsem;
        this.endsem = endsem;
    }

    public int total() {
        return midsem + endsem;
    }

    public boolean passed() {
        return total() >= PASS_TOTAL;
    }

    public String grade() {
        int t = total();
        if (t >= 90) {
            return "AA";
        } else if (t >= 80) {
            return "AB";
        } else if (t >= 70) {
            return "BB";
        } else if (t >= 60) {
            return "BC";
        } else if (t >= 50) {
            return "CC";
        } else if (t >= 40) {
            return "DD";
        }
        return "F";
    }

    public int gradePoints() {
        switch (grade()) {
            case "AA": return 10;
            case "AB": return 9;
            case "BB": return 8;
            case "BC": return 7;
            case "CC": return 6;
            case "DD": return 5;
            default: return 0;
        }
    }

    @Override
    public String toString() {
        return String.format("Exam[student=%d, course=%s, midsem=%d/%d, endsem=%d/%d, total=%d, grade=%s]",
                studentId, courseCode, midsem, MIDSEM_MAX, endsem, ENDSEM_MAX, total(), grade());
    }
}
