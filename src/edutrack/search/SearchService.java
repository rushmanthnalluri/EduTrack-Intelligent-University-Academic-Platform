package edutrack.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import edutrack.data.DataStore;
import edutrack.data.DataSnapshot;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.Faculty;
import edutrack.model.LearningResource;
import edutrack.model.Student;
import edutrack.modules.M3DynamicProgramming;
import modules.KMPSearch;

/**
 * Smart Search engine: global cross-entity search over students, faculty,
 * courses, assignments and resources.
 *
 * Pipeline: case-insensitive substring matching per field via KMP
 * (modules.KMPSearch); multi-term queries use AND semantics (every term must
 * hit some field of the record). Scoring rewards identifier matches over name
 * matches over secondary fields, and earlier fields over later ones:
 * exact code/id equality (100) &gt; code/id prefix (80) &gt; name/title prefix (60)
 * &gt; id substring (45) &gt; name/title substring (40) &gt; secondary-field
 * substring (25), minus the field index. Results are sorted by score desc,
 * then title. When nothing matches exactly, a fuzzy fallback runs Levenshtein
 * (M3) over course codes/name words and student name words and returns
 * "Did you mean" suggestions.
 */
public class SearchService {

    private static final int MAX_RESULTS = 50;
    private static final int MAX_SUGGESTIONS = 10;
    private static final int KIND_SECTION_DISPLAY_LIMIT = 10;

    private static final int CAT_ID = 0;
    private static final int CAT_NAME = 1;
    private static final int CAT_OTHER = 2;

    private static final int SCORE_ID_EXACT = 100;
    private static final int SCORE_ID_PREFIX = 80;
    private static final int SCORE_NAME_PREFIX = 60;
    private static final int SCORE_ID_SUBSTRING = 45;
    private static final int SCORE_NAME_SUBSTRING = 40;
    private static final int SCORE_OTHER_SUBSTRING = 25;

    /** Display order for grouping results by kind. */
    private static final List<String> KIND_ORDER = List.of(
            "Course", "Student", "Faculty", "Assignment", "Resource", "Suggestion");

    private static final class Field {
        final String lower;
        final int category;

        Field(String text, int category) {
            this.lower = text.toLowerCase(Locale.ROOT);
            this.category = category;
        }
    }

    private static final class Candidate {
        final String kind;
        final String id;
        final String title;
        final String subtitle;
        final List<Field> fields;

        Candidate(String kind, String id, String title, String subtitle, List<Field> fields) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.fields = fields;
        }
    }

    // ------------------------------------------------------------------
    // Engine
    // ------------------------------------------------------------------

    /** Snapshot-safe search used by the REST API. */
    public static List<SearchResult> search(DataSnapshot snapshot, String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return List.of();
        }
        String queryLower = normalized.toLowerCase(Locale.ROOT);
        String[] terms = queryLower.split(" ");
        List<SearchResult> results = new ArrayList<>();
        for (Candidate candidate : candidates(snapshot)) {
            if (allTermsMatch(candidate, terms)) {
                results.add(new SearchResult(candidate.kind, candidate.id, candidate.title,
                        candidate.subtitle, score(candidate, queryLower, terms)));
            }
        }
        if (results.isEmpty()) {
            return fuzzySuggestions(snapshot, queryLower, terms);
        }
        results.sort((a, b) -> {
            if (a.score != b.score) return Integer.compare(b.score, a.score);
            int byTitle = a.title.compareTo(b.title);
            return byTitle != 0 ? byTitle : a.id.compareTo(b.id);
        });
        return results.size() > MAX_RESULTS
                ? new ArrayList<>(results.subList(0, MAX_RESULTS)) : results;
    }

    public static List<SearchResult> search(DataStore ds, String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return List.of();
        }
        String queryLower = normalized.toLowerCase(Locale.ROOT);
        String[] terms = queryLower.split(" ");

        List<SearchResult> results = new ArrayList<>();
        for (Candidate candidate : candidates(ds)) {
            if (!allTermsMatch(candidate, terms)) {
                continue;
            }
            results.add(new SearchResult(candidate.kind, candidate.id, candidate.title,
                    candidate.subtitle, score(candidate, queryLower, terms)));
        }

        if (results.isEmpty()) {
            return fuzzySuggestions(ds, queryLower, terms);
        }

        results.sort((a, b) -> {
            if (a.score != b.score) {
                return Integer.compare(b.score, a.score);
            }
            int byTitle = a.title.compareTo(b.title);
            return byTitle != 0 ? byTitle : a.id.compareTo(b.id);
        });
        if (results.size() > MAX_RESULTS) {
            return new ArrayList<>(results.subList(0, MAX_RESULTS));
        }
        return results;
    }

    private static String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ");
    }

    private static boolean allTermsMatch(Candidate candidate, String[] terms) {
        for (String term : terms) {
            boolean termHit = false;
            for (Field field : candidate.fields) {
                if (!KMPSearch.search(field.lower, term).isEmpty()) {
                    termHit = true;
                    break;
                }
            }
            if (!termHit) {
                return false;
            }
        }
        return true;
    }

    private static int score(Candidate candidate, String queryLower, String[] terms) {
        for (int i = 0; i < candidate.fields.size(); i++) {
            int fieldScore = fieldScore(candidate.fields.get(i), queryLower);
            if (fieldScore > 0) {
                return fieldScore - i;
            }
        }
        int total = 0;
        for (String term : terms) {
            int best = 0;
            for (int i = 0; i < candidate.fields.size(); i++) {
                best = Math.max(best, fieldScore(candidate.fields.get(i), term) - i);
            }
            total += best;
        }
        return total;
    }

    private static int fieldScore(Field field, String needle) {
        switch (field.category) {
            case CAT_ID:
                if (field.lower.equals(needle)) {
                    return SCORE_ID_EXACT;
                }
                if (field.lower.startsWith(needle)) {
                    return SCORE_ID_PREFIX;
                }
                if (!KMPSearch.search(field.lower, needle).isEmpty()) {
                    return SCORE_ID_SUBSTRING;
                }
                return 0;
            case CAT_NAME:
                if (field.lower.startsWith(needle)) {
                    return SCORE_NAME_PREFIX;
                }
                if (!KMPSearch.search(field.lower, needle).isEmpty()) {
                    return SCORE_NAME_SUBSTRING;
                }
                return 0;
            default:
                if (!KMPSearch.search(field.lower, needle).isEmpty()) {
                    return SCORE_OTHER_SUBSTRING;
                }
                return 0;
        }
    }

    private static List<Candidate> candidates(DataSnapshot snapshot) {
        List<Candidate> list = new ArrayList<>();
        for (Course course : snapshot.courses()) {
            list.add(new Candidate("Course", course.code, course.name,
                    course.department + " · " + course.credits + " credits · Sem " + course.semester,
                    List.of(new Field(course.code, CAT_ID), new Field(course.name, CAT_NAME),
                            new Field(course.department, CAT_OTHER))));
        }
        for (Student student : snapshot.students()) {
            list.add(new Candidate("Student", String.valueOf(student.id), student.name,
                    student.program + " · Sem " + student.semester
                            + String.format(" · CGPA %.2f", student.cgpa),
                    List.of(new Field(String.valueOf(student.id), CAT_ID),
                            new Field(student.name, CAT_NAME), new Field(student.program, CAT_OTHER))));
        }
        for (Faculty faculty : snapshot.faculty()) {
            list.add(new Candidate("Faculty", String.valueOf(faculty.id), faculty.name,
                    faculty.department + " · Teaches " + joinCapped(faculty.expertise, 6),
                    List.of(new Field(String.valueOf(faculty.id), CAT_ID),
                            new Field(faculty.name, CAT_NAME), new Field(faculty.department, CAT_OTHER))));
        }
        for (Assignment assignment : snapshot.assignments()) {
            list.add(new Candidate("Assignment", assignment.id, assignment.title,
                    "Course " + assignment.courseCode,
                    List.of(new Field(assignment.id, CAT_ID), new Field(assignment.title, CAT_NAME))));
        }
        for (LearningResource resource : snapshot.resources()) {
            list.add(new Candidate("Resource", resource.id, resource.title,
                    resource.type + " · Course " + resource.courseCode,
                    List.of(new Field(resource.id, CAT_ID), new Field(resource.title, CAT_NAME),
                            new Field(resource.type, CAT_OTHER))));
        }
        return list;
    }

    private static List<Candidate> candidates(DataStore ds) {
        List<Candidate> list = new ArrayList<>();
        for (Course course : ds.courses()) {
            list.add(new Candidate("Course", course.code, course.name,
                    course.department + " · " + course.credits + " credits · Sem "
                            + course.semester,
                    List.of(new Field(course.code, CAT_ID), new Field(course.name, CAT_NAME),
                            new Field(course.department, CAT_OTHER))));
        }
        for (Student student : ds.students()) {
            list.add(new Candidate("Student", String.valueOf(student.id), student.name,
                    student.program + " · Sem " + student.semester
                            + String.format(" · CGPA %.2f", student.cgpa),
                    List.of(new Field(String.valueOf(student.id), CAT_ID),
                            new Field(student.name, CAT_NAME),
                            new Field(student.program, CAT_OTHER))));
        }
        for (Faculty faculty : ds.faculty()) {
            list.add(new Candidate("Faculty", String.valueOf(faculty.id), faculty.name,
                    faculty.department + " · Teaches " + joinCapped(faculty.expertise, 6),
                    List.of(new Field(String.valueOf(faculty.id), CAT_ID),
                            new Field(faculty.name, CAT_NAME),
                            new Field(faculty.department, CAT_OTHER))));
        }
        for (Assignment assignment : ds.assignments()) {
            list.add(new Candidate("Assignment", assignment.id, assignment.title,
                    "Course " + assignment.courseCode,
                    List.of(new Field(assignment.id, CAT_ID),
                            new Field(assignment.title, CAT_NAME))));
        }
        for (LearningResource resource : ds.resources()) {
            list.add(new Candidate("Resource", resource.id, resource.title,
                    resource.type + " · Course " + resource.courseCode,
                    List.of(new Field(resource.id, CAT_ID),
                            new Field(resource.title, CAT_NAME),
                            new Field(resource.type, CAT_OTHER))));
        }
        return list;
    }

    private static String joinCapped(List<String> items, int limit) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(items.size(), limit);
        for (int i = 0; i < shown; i++) {
            sb.append(items.get(i));
            if (i < shown - 1) {
                sb.append(", ");
            }
        }
        if (items.size() > shown) {
            sb.append(", …");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Fuzzy fallback ("Did you mean")
    // ------------------------------------------------------------------

    private static List<SearchResult> fuzzySuggestions(DataSnapshot snapshot, String queryLower,
            String[] terms) {
        return fuzzySuggestionsFromCandidates(snapshot, queryLower, terms);
    }

    private static List<SearchResult> fuzzySuggestions(DataStore ds, String queryLower,
            String[] terms) {
        List<ScoredEntity> pool = new ArrayList<>();
        for (Course course : ds.courses()) {
            List<String> words = new ArrayList<>();
            words.add(course.code.toLowerCase(Locale.ROOT));
            for (String word : course.name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                words.add(word);
            }
            words.add(course.name.toLowerCase(Locale.ROOT));
            int distance = fuzzyDistance(terms, queryLower, words);
            if (distance >= 0) {
                pool.add(new ScoredEntity("Course", course.code, course.name,
                        "Course · " + course.code, distance));
            }
        }
        for (Student student : ds.students()) {
            List<String> words = new ArrayList<>();
            for (String word : student.name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                words.add(word);
            }
            words.add(student.name.toLowerCase(Locale.ROOT));
            int distance = fuzzyDistance(terms, queryLower, words);
            if (distance >= 0) {
                pool.add(new ScoredEntity("Student", String.valueOf(student.id), student.name,
                        "Student · " + student.id, distance));
            }
        }

        for (Faculty faculty : ds.faculty()) {
            List<String> words = new ArrayList<>();
            words.add(String.valueOf(faculty.id));
            words.add(faculty.name.toLowerCase(Locale.ROOT));
            words.add(faculty.department.toLowerCase(Locale.ROOT));
            words.addAll(faculty.expertise.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList());
            int distance = fuzzyDistance(terms, queryLower, words);
            if (distance >= 0) pool.add(new ScoredEntity("Faculty", String.valueOf(faculty.id),
                    faculty.name, "Faculty · " + faculty.id, distance));
        }
        for (Assignment assignment : ds.assignments()) {
            List<String> words = List.of(assignment.id.toLowerCase(Locale.ROOT),
                    assignment.title.toLowerCase(Locale.ROOT), assignment.courseCode.toLowerCase(Locale.ROOT));
            int distance = fuzzyDistance(terms, queryLower, words);
            if (distance >= 0) pool.add(new ScoredEntity("Assignment", assignment.id,
                    assignment.title, "Assignment · " + assignment.courseCode, distance));
        }
        for (LearningResource resource : ds.resources()) {
            List<String> words = List.of(resource.id.toLowerCase(Locale.ROOT),
                    resource.title.toLowerCase(Locale.ROOT), resource.courseCode.toLowerCase(Locale.ROOT),
                    resource.type.toLowerCase(Locale.ROOT));
            int distance = fuzzyDistance(terms, queryLower, words);
            if (distance >= 0) pool.add(new ScoredEntity("Resource", resource.id,
                    resource.title, "Resource · " + resource.courseCode, distance));
        }

        pool.sort((a, b) -> {
            if (a.distance != b.distance) {
                return Integer.compare(a.distance, b.distance);
            }
            return a.title.compareTo(b.title);
        });

        List<SearchResult> suggestions = new ArrayList<>();
        int limit = Math.min(pool.size(), MAX_SUGGESTIONS);
        for (int i = 0; i < limit; i++) {
            ScoredEntity entity = pool.get(i);
            suggestions.add(new SearchResult("Suggestion", entity.id,
                    "Did you mean: " + entity.title, entity.subtitle, 100 - entity.distance));
        }
        return suggestions;
    }

    private static List<SearchResult> fuzzySuggestionsFromCandidates(DataSnapshot snapshot,
            String queryLower, String[] terms) {
        List<ScoredEntity> pool = new ArrayList<>();
        for (Candidate c : candidates(snapshot)) {
            List<String> words = new ArrayList<>();
            for (Field f : c.fields) {
                words.add(f.lower);
                for (String word : f.lower.split("[^a-z0-9]+")) {
                    if (!word.isEmpty()) words.add(word);
                }
            }
            int distance = fuzzyDistance(terms, queryLower, words);
            if (distance >= 0) {
                pool.add(new ScoredEntity(c.kind, c.id, c.title, c.kind + " · " + c.id, distance));
            }
        }
        pool.sort((a, b) -> {
            if (a.distance != b.distance) return Integer.compare(a.distance, b.distance);
            int byTitle = a.title.compareTo(b.title);
            return byTitle != 0 ? byTitle : a.id.compareTo(b.id);
        });
        List<SearchResult> suggestions = new ArrayList<>();
        for (int i = 0; i < Math.min(pool.size(), MAX_SUGGESTIONS); i++) {
            ScoredEntity e = pool.get(i);
            suggestions.add(new SearchResult("Suggestion", e.id,
                    "Did you mean: " + e.title, e.subtitle, 100 - e.distance));
        }
        return suggestions;
    }

    private static final class ScoredEntity {
        final String kind;
        final String id;
        final String title;
        final String subtitle;
        final int distance;

        ScoredEntity(String kind, String id, String title, String subtitle, int distance) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.distance = distance;
        }
    }

    /** Total Levenshtein distance of the query to the best-matching words, or -1 if out of tolerance. */
    private static int fuzzyDistance(String[] terms, String queryLower, List<String> words) {
        int wholeTolerance = Math.max(2, queryLower.length() / 3);
        for (String word : words) {
            if (M3DynamicProgramming.levenshtein(queryLower, word) <= wholeTolerance) {
                return M3DynamicProgramming.levenshtein(queryLower, word);
            }
        }
        int total = 0;
        for (String term : terms) {
            int tolerance = Math.max(2, term.length() / 3);
            int best = Integer.MAX_VALUE;
            for (String word : words) {
                best = Math.min(best, M3DynamicProgramming.levenshtein(term, word));
            }
            if (best > tolerance) {
                return -1;
            }
            total += best;
        }
        return total;
    }

    // ------------------------------------------------------------------
    // CLI loop
    // ------------------------------------------------------------------

    public static void run(Scanner sc, DataStore ds) {
        while (true) {
            System.out.println("\n--- Smart Search ---");
            System.out.print("Enter search query (empty or 0 to exit) : ");
            String query = sc.nextLine().trim();
            if (query.isEmpty() || query.equals("0")) {
                System.out.println("Leaving Smart Search...");
                return;
            }

            long start = System.nanoTime();
            List<SearchResult> results = search(ds, query);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            System.out.println("\nResults: " + results.size() + " in " + elapsedMs + " ms");
            if (results.isEmpty()) {
                System.out.println("No matches and no suggestions.");
                continue;
            }
            for (String kind : KIND_ORDER) {
                List<SearchResult> group = new ArrayList<>();
                for (SearchResult result : results) {
                    if (result.kind.equals(kind)) {
                        group.add(result);
                    }
                }
                if (group.isEmpty()) {
                    continue;
                }
                System.out.println("\n" + kind + "s (" + group.size() + "):");
                int limit = Math.min(group.size(), KIND_SECTION_DISPLAY_LIMIT);
                for (int i = 0; i < limit; i++) {
                    System.out.println("  " + group.get(i));
                }
                if (group.size() > limit) {
                    System.out.println("  ... (" + (group.size() - limit) + " more)");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Non-interactive self-test
    // ------------------------------------------------------------------

    private static int checksRun = 0;
    private static int checksFailed = 0;

    private static void check(String name, boolean condition) {
        checksRun++;
        if (condition) {
            System.out.println("PASS: " + name);
        } else {
            checksFailed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        DataStore ds = new DataStore();

        // 'data' ranks CS201 'Data Structures' first among courses.
        long start = System.nanoTime();
        List<SearchResult> data = search(ds, "data");
        long dataMs = (System.nanoTime() - start) / 1_000_000;
        check("'data' returns results", !data.isEmpty());
        check("'data' first course is CS201 Data Structures",
                firstOfKind(data, "Course") != null
                        && firstOfKind(data, "Course").id.equals("CS201")
                        && firstOfKind(data, "Course").title.equals("Data Structures"));
        check("results sorted by score descending", isScoreDescending(data));

        // Exact code 'cs301' hits CS301 as the top result overall.
        List<SearchResult> cs301 = search(ds, "cs301");
        check("'cs301' top result is Course CS301",
                !cs301.isEmpty()
                        && cs301.get(0).kind.equals("Course")
                        && cs301.get(0).id.equals("CS301"));
        check("'cs301' top result outranks assignment-id substring hits",
                cs301.get(0).score > scoreOf(cs301, "Assignment", "ASG-CS301-1"));
        check("uppercase 'CS301' behaves identically",
                !search(ds, "CS301").isEmpty()
                        && search(ds, "CS301").get(0).id.equals("CS301"));

        // Multi-term AND: 'machine learning' hits CS401.
        List<SearchResult> ml = search(ds, "machine learning");
        check("'machine learning' first course is CS401 Machine Learning",
                firstOfKind(ml, "Course") != null
                        && firstOfKind(ml, "Course").id.equals("CS401"));
        check("multi-term AND excludes records matching only one term",
                firstOfKind(search(ds, "database networks"), "Course") == null);

        // Student-name query finds the right student.
        Student firstStudent = ds.students().get(0);
        List<SearchResult> byName = search(ds, firstStudent.name);
        check("student-name query finds student " + firstStudent.id,
                contains(byName, "Student", String.valueOf(firstStudent.id)));
        check("full-name query ranks that student first among students",
                firstOfKind(byName, "Student") != null
                        && firstOfKind(byName, "Student").id.equals(String.valueOf(firstStudent.id)));
        List<SearchResult> reversed = search(ds, "sharma aarav");
        check("reversed multi-term still finds the student",
                contains(reversed, "Student", String.valueOf(firstStudent.id)));

        // Gibberish yields 'Did you mean' suggestions.
        List<SearchResult> fuzzy = search(ds, "algoritms");
        check("gibberish yields suggestions", !fuzzy.isEmpty());
        check("suggestions have kind Suggestion and 'Did you mean' titles",
                fuzzy.get(0).kind.equals("Suggestion")
                        && fuzzy.get(0).title.startsWith("Did you mean: "));
        check("'algoritms' suggests the Algorithms course CS202",
                contains(fuzzy, "Suggestion", "CS202"));
        check("misspelled first name suggests students",
                !search(ds, "aarv").isEmpty()
                        && search(ds, "aarv").get(0).kind.equals("Suggestion"));

        // Empty / blank / null queries return empty.
        check("empty query returns empty", search(ds, "").isEmpty());
        check("blank query returns empty", search(ds, "    ").isEmpty());
        check("null query returns empty", search(ds, null).isEmpty());

        // Result cap.
        check("results capped at " + MAX_RESULTS, search(ds, "a").size() <= MAX_RESULTS);

        System.out.println("\nTiming: 'data' over students+faculty+courses+assignments+resources"
                + " took " + dataMs + " ms");
        System.out.println("Self-test: " + (checksRun - checksFailed) + "/" + checksRun
                + " checks passed.");
        if (checksFailed > 0) {
            System.out.println("SELF-TEST FAILED (" + checksFailed + " failures)");
            System.exit(1);
        }
        System.out.println("ALL CHECKS PASSED");
    }

    private static SearchResult firstOfKind(List<SearchResult> results, String kind) {
        for (SearchResult result : results) {
            if (result.kind.equals(kind)) {
                return result;
            }
        }
        return null;
    }

    private static boolean contains(List<SearchResult> results, String kind, String id) {
        for (SearchResult result : results) {
            if (result.kind.equals(kind) && result.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static int scoreOf(List<SearchResult> results, String kind, String id) {
        for (SearchResult result : results) {
            if (result.kind.equals(kind) && result.id.equals(id)) {
                return result.score;
            }
        }
        return -1;
    }

    private static boolean isScoreDescending(List<SearchResult> results) {
        for (int i = 1; i < results.size(); i++) {
            if (results.get(i - 1).score < results.get(i).score) {
                return false;
            }
        }
        return true;
    }
}
