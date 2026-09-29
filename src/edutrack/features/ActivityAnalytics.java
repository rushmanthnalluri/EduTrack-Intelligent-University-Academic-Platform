package edutrack.features;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edutrack.data.DataStore;
import edutrack.model.ActivityEvent;

/**
 * Large-scale, read-only analytics over the activity event activity stream:
 * per-action counts, hour-of-day and per-day buckets (UTC, deterministic),
 * top-K courses and students, and a headline summary. Every computation is
 * a pure function of the event list. The non-interactive main() self-test
 * cross-checks each computation against independent brute-force passes and
 * exits 1 on any failure.
 */
public final class ActivityAnalytics {

    public static final String[] KNOWN_ACTIONS = {
        "LOGIN", "VIEW_COURSE", "SUBMIT_ASSIGNMENT", "DOWNLOAD_RESOURCE",
        "ATTEND_LECTURE", "TAKE_QUIZ", "LOGOUT"
    };
    public static final int HOURS_PER_DAY = 24;

    private ActivityAnalytics() {
    }

    public static boolean isCourseRelated(ActivityEvent event) {
        return event.details != null && !event.details.isEmpty() && !"-".equals(event.details);
    }

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    public static final class CourseActivity {
        public final String courseCode;
        public final int events;

        public CourseActivity(String courseCode, int events) {
            this.courseCode = courseCode;
            this.events = events;
        }
    }

    public static final class StudentActivity {
        public final int studentId;
        public final int events;

        public StudentActivity(int studentId, int events) {
            this.studentId = studentId;
            this.events = events;
        }
    }

    public static final class Summary {
        public final int totalEvents;
        public final int distinctDays;
        public final double avgEventsPerDay;
        public final int peakHour;
        public final int peakHourCount;
        public final int courseRelatedEvents;
        public final double courseRelatedPercent;

        public Summary(int totalEvents, int distinctDays, double avgEventsPerDay,
                int peakHour, int peakHourCount, int courseRelatedEvents, double courseRelatedPercent) {
            this.totalEvents = totalEvents;
            this.distinctDays = distinctDays;
            this.avgEventsPerDay = avgEventsPerDay;
            this.peakHour = peakHour;
            this.peakHourCount = peakHourCount;
            this.courseRelatedEvents = courseRelatedEvents;
            this.courseRelatedPercent = courseRelatedPercent;
        }
    }

    public static final class AnalyticsResult {
        public final LinkedHashMap<String, Integer> actionCounts;
        public final int[] hourCounts;
        public final TreeMap<LocalDate, Integer> dayCounts;
        public final List<CourseActivity> topCourses;
        public final List<StudentActivity> topStudents;
        public final Summary summary;

        public AnalyticsResult(LinkedHashMap<String, Integer> actionCounts, int[] hourCounts,
                TreeMap<LocalDate, Integer> dayCounts, List<CourseActivity> topCourses,
                List<StudentActivity> topStudents, Summary summary) {
            this.actionCounts = actionCounts;
            this.hourCounts = hourCounts;
            this.dayCounts = dayCounts;
            this.topCourses = topCourses;
            this.topStudents = topStudents;
            this.summary = summary;
        }
    }

    // ------------------------------------------------------------------
    // Computations
    // ------------------------------------------------------------------

    public static LinkedHashMap<String, Integer> countByAction(List<ActivityEvent> events) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String action : KNOWN_ACTIONS) {
            counts.put(action, 0);
        }
        for (ActivityEvent event : events) {
            counts.merge(event.action, 1, Integer::sum);
        }
        return counts;
    }

    public static int[] countByHour(List<ActivityEvent> events) {
        int[] hours = new int[HOURS_PER_DAY];
        for (ActivityEvent event : events) {
            LocalTime time = LocalTime.ofInstant(Instant.ofEpochMilli(event.timestamp), ZoneOffset.UTC);
            hours[time.getHour()]++;
        }
        return hours;
    }

    public static TreeMap<LocalDate, Integer> countByDay(List<ActivityEvent> events) {
        TreeMap<LocalDate, Integer> days = new TreeMap<>();
        for (ActivityEvent event : events) {
            LocalDate day = Instant.ofEpochMilli(event.timestamp).atZone(ZoneOffset.UTC).toLocalDate();
            days.merge(day, 1, Integer::sum);
        }
        return days;
    }

    public static List<CourseActivity> topCourses(List<ActivityEvent> events, int k) {
        Map<String, Integer> counts = new HashMap<>();
        for (ActivityEvent event : events) {
            if (isCourseRelated(event)) {
                counts.merge(event.details, 1, Integer::sum);
            }
        }
        List<CourseActivity> ranked = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ranked.add(new CourseActivity(entry.getKey(), entry.getValue()));
        }
        ranked.sort((a, b) -> a.events != b.events
                ? Integer.compare(b.events, a.events)
                : a.courseCode.compareTo(b.courseCode));
        return new ArrayList<>(ranked.subList(0, Math.min(Math.max(k, 0), ranked.size())));
    }

    public static List<StudentActivity> topStudents(List<ActivityEvent> events, int k) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (ActivityEvent event : events) {
            counts.merge(event.studentId, 1, Integer::sum);
        }
        List<StudentActivity> ranked = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            ranked.add(new StudentActivity(entry.getKey(), entry.getValue()));
        }
        ranked.sort((a, b) -> a.events != b.events
                ? Integer.compare(b.events, a.events)
                : Integer.compare(a.studentId, b.studentId));
        return new ArrayList<>(ranked.subList(0, Math.min(Math.max(k, 0), ranked.size())));
    }

    public static Summary summarize(List<ActivityEvent> events) {
        int[] hours = countByHour(events);
        TreeMap<LocalDate, Integer> days = countByDay(events);
        int courseRelated = 0;
        for (ActivityEvent event : events) {
            if (isCourseRelated(event)) {
                courseRelated++;
            }
        }
        int peakHour = 0;
        for (int h = 1; h < hours.length; h++) {
            if (hours[h] > hours[peakHour]) {
                peakHour = h;
            }
        }
        int total = events.size();
        int distinctDays = days.size();
        double avg = distinctDays == 0 ? 0.0 : (double) total / distinctDays;
        double pct = total == 0 ? 0.0 : 100.0 * courseRelated / total;
        return new Summary(total, distinctDays, avg, peakHour, hours[peakHour], courseRelated, pct);
    }

    public static AnalyticsResult analyze(List<ActivityEvent> events, int topK) {
        return new AnalyticsResult(countByAction(events), countByHour(events), countByDay(events),
                topCourses(events, topK), topStudents(events, topK), summarize(events));
    }

    // ------------------------------------------------------------------
    // Self-test (non-interactive): java -cp bin edutrack.features.ActivityAnalytics
    // ------------------------------------------------------------------

    private static int checks;

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        DataStore dataStore = new DataStore();
        List<ActivityEvent> events = dataStore.activityStream();

        AnalyticsResult first = analyze(events, 10);
        AnalyticsResult second = analyze(events, 10);

        check(failures, "stream holds 100,000 events", events.size() == 100_000);

        // Per-action counts vs a brute-force independent recount.
        Map<String, Integer> bruteActions = new HashMap<>();
        for (ActivityEvent event : events) {
            bruteActions.merge(event.action, 1, Integer::sum);
        }
        boolean actionsMatch = bruteActions.size() == KNOWN_ACTIONS.length;
        for (String action : KNOWN_ACTIONS) {
            Integer brute = bruteActions.get(action);
            actionsMatch &= brute != null && brute.equals(first.actionCounts.get(action));
        }
        check(failures, "per-action counts match brute-force independent pass", actionsMatch);
        int actionSum = 0;
        for (int count : first.actionCounts.values()) {
            actionSum += count;
        }
        check(failures, "per-action counts sum to 100,000", actionSum == 100_000);

        // Hour buckets: total plus an arithmetic pass that avoids java.time entirely.
        int[] bruteHours = new int[HOURS_PER_DAY];
        for (ActivityEvent event : events) {
            bruteHours[(int) (event.timestamp % 86_400_000L / 3_600_000L)]++;
        }
        int hourSum = 0;
        for (int count : first.hourCounts) {
            hourSum += count;
        }
        check(failures, "hour buckets sum to 100,000", hourSum == 100_000);
        check(failures, "hour buckets match arithmetic (non-java.time) pass",
                Arrays.equals(bruteHours, first.hourCounts));

        // Day buckets: total, sorted order and distinct-day consistency.
        int daySum = 0;
        LocalDate previous = null;
        boolean daysSorted = true;
        for (Map.Entry<LocalDate, Integer> entry : first.dayCounts.entrySet()) {
            daySum += entry.getValue();
            if (previous != null && !entry.getKey().isAfter(previous)) {
                daysSorted = false;
            }
            previous = entry.getKey();
        }
        check(failures, "per-day counts sum to 100,000", daySum == 100_000);
        check(failures, "per-day buckets sorted by date", daysSorted);
        check(failures, "distinct days consistent with day buckets",
                first.summary.distinctDays == first.dayCounts.size());

        // Top courses: top-1 and the full top-10 vs an independent ranking.
        Map<String, Integer> bruteCourses = new HashMap<>();
        for (ActivityEvent event : events) {
            if (isCourseRelated(event)) {
                bruteCourses.merge(event.details, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> courseRank = new ArrayList<>(bruteCourses.entrySet());
        courseRank.sort((a, b) -> !a.getValue().equals(b.getValue())
                ? Integer.compare(b.getValue(), a.getValue())
                : a.getKey().compareTo(b.getKey()));
        check(failures, "top-1 course matches independent max",
                !courseRank.isEmpty()
                        && courseRank.get(0).getKey().equals(first.topCourses.get(0).courseCode)
                        && courseRank.get(0).getValue().intValue() == first.topCourses.get(0).events);
        boolean coursesMatch = first.topCourses.size() == Math.min(10, courseRank.size());
        for (int i = 0; coursesMatch && i < first.topCourses.size(); i++) {
            coursesMatch = first.topCourses.get(i).courseCode.equals(courseRank.get(i).getKey())
                    && first.topCourses.get(i).events == courseRank.get(i).getValue();
        }
        check(failures, "top-10 courses match independent ranking", coursesMatch);

        // Top students: top-1 and the full top-10 vs an independent ranking.
        Map<Integer, Integer> bruteStudents = new HashMap<>();
        for (ActivityEvent event : events) {
            bruteStudents.merge(event.studentId, 1, Integer::sum);
        }
        List<Map.Entry<Integer, Integer>> studentRank = new ArrayList<>(bruteStudents.entrySet());
        studentRank.sort((a, b) -> !a.getValue().equals(b.getValue())
                ? Integer.compare(b.getValue(), a.getValue())
                : Integer.compare(a.getKey(), b.getKey()));
        check(failures, "top-1 student matches independent max",
                !studentRank.isEmpty()
                        && studentRank.get(0).getKey().intValue() == first.topStudents.get(0).studentId
                        && studentRank.get(0).getValue().intValue() == first.topStudents.get(0).events);
        boolean studentsMatch = first.topStudents.size() == Math.min(10, studentRank.size());
        for (int i = 0; studentsMatch && i < first.topStudents.size(); i++) {
            studentsMatch = first.topStudents.get(i).studentId == studentRank.get(i).getKey()
                    && first.topStudents.get(i).events == studentRank.get(i).getValue();
        }
        check(failures, "top-10 students match independent ranking", studentsMatch);
        boolean idsKnown = true;
        for (StudentActivity activity : first.topStudents) {
            idsKnown &= dataStore.studentsById().containsKey(activity.studentId);
        }
        check(failures, "top student ids resolve via studentsById()", idsKnown);

        // Summary internal consistency.
        Summary summary = first.summary;
        int maxHourCount = 0;
        for (int count : first.hourCounts) {
            maxHourCount = Math.max(maxHourCount, count);
        }
        check(failures, "peak hour is a true maximum",
                summary.peakHourCount == maxHourCount
                        && first.hourCounts[summary.peakHour] == maxHourCount);
        check(failures, "avg events/day consistent",
                Math.abs(summary.avgEventsPerDay - (double) summary.totalEvents / summary.distinctDays) < 1e-9);
        check(failures, "course-related percent consistent",
                Math.abs(summary.courseRelatedPercent
                        - 100.0 * summary.courseRelatedEvents / summary.totalEvents) < 1e-9);

        // Determinism: two full runs must produce identical results.
        check(failures, "determinism: two analyze() runs identical", sameResult(first, second));

        System.out.println("----");
        System.out.printf("total=%,d days=%d avg/day=%.1f peak=%02d:00 UTC (%,d) course-related=%.1f%%%n",
                summary.totalEvents, summary.distinctDays, summary.avgEventsPerDay,
                summary.peakHour, summary.peakHourCount, summary.courseRelatedPercent);
        System.out.println("actions=" + first.actionCounts);
        System.out.println("top course=" + first.topCourses.get(0).courseCode
                + " (" + String.format("%,d", first.topCourses.get(0).events) + " events)");
        System.out.println("top student=#" + first.topStudents.get(0).studentId
                + " (" + String.format("%,d", first.topStudents.get(0).events) + " events)");

        if (!failures.isEmpty()) {
            System.out.println("SELF-TEST FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("ALL " + checks + " CHECKS PASSED");
    }

    private static void check(List<String> failures, String name, boolean ok) {
        checks++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) {
            failures.add(name);
        }
    }

    private static boolean sameResult(AnalyticsResult a, AnalyticsResult b) {
        if (!a.actionCounts.equals(b.actionCounts)
                || !Arrays.equals(a.hourCounts, b.hourCounts)
                || !a.dayCounts.equals(b.dayCounts)
                || a.topCourses.size() != b.topCourses.size()
                || a.topStudents.size() != b.topStudents.size()) {
            return false;
        }
        for (int i = 0; i < a.topCourses.size(); i++) {
            if (!a.topCourses.get(i).courseCode.equals(b.topCourses.get(i).courseCode)
                    || a.topCourses.get(i).events != b.topCourses.get(i).events) {
                return false;
            }
        }
        for (int i = 0; i < a.topStudents.size(); i++) {
            if (a.topStudents.get(i).studentId != b.topStudents.get(i).studentId
                    || a.topStudents.get(i).events != b.topStudents.get(i).events) {
                return false;
            }
        }
        Summary x = a.summary;
        Summary y = b.summary;
        return x.totalEvents == y.totalEvents
                && x.distinctDays == y.distinctDays
                && x.avgEventsPerDay == y.avgEventsPerDay
                && x.peakHour == y.peakHour
                && x.peakHourCount == y.peakHourCount
                && x.courseRelatedEvents == y.courseRelatedEvents
                && x.courseRelatedPercent == y.courseRelatedPercent;
    }
}
