package edutrack.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import edutrack.data.DataStore;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.Student;
import modules.KMPSearch;
import modules.RabinKarpSearch;
import modules.ZFunctionSearch;

/**
 * Module M1 - String Algorithms (CO1).
 * KMP keyword search over courses/students, Z-function repeated-phrase
 * detection inside assignment texts, Rabin-Karp code search over IDs,
 * and Aho-Corasick multi-keyword scanning of assignment/Wikipedia text.
 */
public class M1StringAlgorithms {

    private static final String[] DEFAULT_KEYWORDS = {
        "exam", "assignment", "plagiarism", "deadline", "lecture",
        "tutorial", "lab", "viva", "syllabus", "credits"
    };
    private static final int DEFAULT_MIN_PHRASE_LENGTH = 12;
    private static final int RECORD_DISPLAY_LIMIT = 15;
    private static final int POSITION_DISPLAY_LIMIT = 20;
    private static final int TOP_FREQUENT_LIMIT = 10;
    private static final int TOP_LONGEST_LIMIT = 5;
    private static final int MAX_PHRASE_CANDIDATES = 500;
    private static final int PHRASE_DISPLAY_LENGTH = 60;

    private static final int RK_BASE = 256;
    private static final int RK_MOD = 1_000_000_007;

    /** Returns a copy of the default academic keyword set used by the Aho-Corasick scan. */
    public static String[] defaultKeywords() {
        return DEFAULT_KEYWORDS.clone();
    }

    public static void run(Scanner sc, DataStore ds) {
        int choice;
        do {
            System.out.println("\n--- M1: String Algorithms ---");
            System.out.println("1. KMP Search (course codes/names and student names)");
            System.out.println("2. Z-Function Repeated Phrase Detection (assignment text)");
            System.out.println("3. Rabin-Karp Code Search (assignment IDs and course codes)");
            System.out.println("4. Aho-Corasick Multi-Keyword Scan (assignments / Wikipedia)");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    runKmpKeywordSearch(sc, ds);
                    break;
                case 2:
                    runZRepeatedPhrases(sc, ds);
                    break;
                case 3:
                    runRabinKarpCodeSearch(sc, ds);
                    break;
                case 4:
                    runAhoCorasickScan(sc, ds);
                    break;
                case 0:
                    System.out.println("Returning to main menu...");
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    // ------------------------------------------------------------------
    // Option 1: KMP keyword search over course codes/names + student names
    // ------------------------------------------------------------------

    /** One course or student record matched by the KMP keyword search. */
    public static final class KmpMatch {
        public final String recordType;
        public final String idOrCode;
        public final String displayName;
        public final List<Integer> codePositions;
        public final List<Integer> namePositions;

        KmpMatch(String recordType, String idOrCode, String displayName,
                List<Integer> codePositions, List<Integer> namePositions) {
            this.recordType = recordType;
            this.idOrCode = idOrCode;
            this.displayName = displayName;
            this.codePositions = Collections.unmodifiableList(codePositions);
            this.namePositions = Collections.unmodifiableList(namePositions);
        }

        public int occurrenceCount() {
            return codePositions.size() + namePositions.size();
        }
    }

    /**
     * Case-insensitive KMP keyword search over every course code, course name and
     * student name. Positions refer to the original (un-lowercased) record text.
     */
    public static List<KmpMatch> kmpSearchCoursesAndStudents(DataStore ds, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        List<KmpMatch> matches = new ArrayList<>();
        for (Course course : ds.courses()) {
            List<Integer> codeHits = KMPSearch.search(course.code.toLowerCase(Locale.ROOT), needle);
            List<Integer> nameHits = KMPSearch.search(course.name.toLowerCase(Locale.ROOT), needle);
            if (!codeHits.isEmpty() || !nameHits.isEmpty()) {
                matches.add(new KmpMatch("Course", course.code, course.name, codeHits, nameHits));
            }
        }
        for (Student student : ds.students()) {
            List<Integer> nameHits = KMPSearch.search(student.name.toLowerCase(Locale.ROOT), needle);
            if (!nameHits.isEmpty()) {
                matches.add(new KmpMatch("Student", String.valueOf(student.id), student.name,
                        List.of(), nameHits));
            }
        }
        return matches;
    }

    private static void runKmpKeywordSearch(Scanner sc, DataStore ds) {
        System.out.println("\n--- KMP Keyword Search ---");
        String keyword = readLine(sc, "Enter keyword : ");
        if (keyword.isEmpty()) {
            System.out.println("\nKeyword cannot be empty.");
            return;
        }

        long start = System.nanoTime();
        List<KmpMatch> matches = kmpSearchCoursesAndStudents(ds, keyword);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        List<String> courseLines = new ArrayList<>();
        List<String> studentLines = new ArrayList<>();
        int courseOccurrences = 0;
        int studentOccurrences = 0;
        for (KmpMatch match : matches) {
            if (match.recordType.equals("Course")) {
                courseOccurrences += match.occurrenceCount();
                StringBuilder line = new StringBuilder();
                line.append(match.idOrCode).append("  ").append(match.displayName);
                if (!match.codePositions.isEmpty()) {
                    line.append("\n    code matches at: ").append(formatPositions(match.codePositions));
                }
                if (!match.namePositions.isEmpty()) {
                    line.append("\n    name matches at: ").append(formatPositions(match.namePositions));
                }
                courseLines.add(line.toString());
            } else {
                studentOccurrences += match.occurrenceCount();
                studentLines.add(match.idOrCode + "  " + match.displayName
                        + "\n    name matches at: " + formatPositions(match.namePositions));
            }
        }

        System.out.println("\n--- KMP Keyword Search Results ---");
        System.out.println("Keyword             : " + keyword);
        System.out.println("Courses matched     : " + courseLines.size()
                + " (" + courseOccurrences + " occurrences)");
        System.out.println("Students matched    : " + studentLines.size()
                + " (" + studentOccurrences + " occurrences)");
        System.out.println("Time taken          : " + elapsedMs + " ms");

        printRecordBlock("Matching Courses", courseLines);
        printRecordBlock("Matching Students", studentLines);
    }

    private static void printRecordBlock(String title, List<String> lines) {
        System.out.println("\n--- " + title + " ---");
        if (lines.isEmpty()) {
            System.out.println("No matches found.");
            return;
        }
        int limit = Math.min(lines.size(), RECORD_DISPLAY_LIMIT);
        for (int i = 0; i < limit; i++) {
            System.out.println(lines.get(i));
        }
        if (lines.size() > limit) {
            System.out.println("... (" + (lines.size() - limit) + " more records)");
        }
    }

    private static String formatPositions(List<Integer> positions) {
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(positions.size(), POSITION_DISPLAY_LIMIT);
        for (int i = 0; i < limit; i++) {
            sb.append(positions.get(i));
            if (i < limit - 1) {
                sb.append(", ");
            }
        }
        if (positions.size() > limit) {
            sb.append(" ... (").append(positions.size() - limit).append(" more)");
        }
        return sb.append("]").toString();
    }

    // ------------------------------------------------------------------
    // Option 2: Z-function repeated-phrase detection in an assignment text
    // ------------------------------------------------------------------

    /** A maximal repeated substring with its verified occurrence count. */
    public static final class RepeatedPhrase {
        public final String text;
        public final int count;
        public final int firstPosition;

        RepeatedPhrase(String text, int count, int firstPosition) {
            this.text = text;
            this.count = count;
            this.firstPosition = firstPosition;
        }
    }

    private static void runZRepeatedPhrases(Scanner sc, DataStore ds) {
        System.out.println("\n--- Z-Function Repeated Phrase Detection ---");
        List<Assignment> assignments = ds.assignments();
        if (assignments.isEmpty()) {
            System.out.println("\nNo assignments available.");
            return;
        }
        System.out.println("Assignments:");
        for (int i = 0; i < assignments.size(); i++) {
            System.out.printf("%2d) %-14s", i + 1, assignments.get(i).id);
            if ((i + 1) % 3 == 0 || i == assignments.size() - 1) {
                System.out.println();
            }
        }
        System.out.print("Select assignment (1-" + assignments.size() + ") : ");
        int pick = readInt(sc);
        if (pick < 1 || pick > assignments.size()) {
            System.out.println("\nInvalid assignment number.");
            return;
        }
        Assignment assignment = assignments.get(pick - 1);

        System.out.print("Enter minimum phrase length [" + DEFAULT_MIN_PHRASE_LENGTH + "] : ");
        int minLength = readPositiveIntOrDefault(sc, DEFAULT_MIN_PHRASE_LENGTH);

        String text = assignment.text;
        System.out.println("\nAssignment : " + assignment.id + " - " + assignment.title);
        System.out.println("Text length: " + text.length() + " characters");

        long start = System.nanoTime();
        List<RepeatedPhrase> rawPhrases = findRepeatedPhrases(text, minLength);
        List<RepeatedPhrase> phrases = filterSubsumedRepeats(rawPhrases);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\n--- Repeated Phrases (min length " + minLength + ") ---");
        System.out.println("Distinct repeated phrases: " + rawPhrases.size()
                + " (" + phrases.size() + " after removing subsumed sub-repeats)");
        System.out.println("Time taken             : " + elapsedMs + " ms");

        if (phrases.isEmpty()) {
            System.out.println("No repeated phrases of at least " + minLength + " characters found.");
            return;
        }

        List<RepeatedPhrase> byFrequency = new ArrayList<>(phrases);
        sortByCountDesc(byFrequency);
        System.out.println("\nMost frequent (top " + Math.min(TOP_FREQUENT_LIMIT, byFrequency.size()) + "):");
        for (int i = 0; i < Math.min(TOP_FREQUENT_LIMIT, byFrequency.size()); i++) {
            RepeatedPhrase p = byFrequency.get(i);
            System.out.printf("  %2d. \"%s\"%n     occurrences=%d, length=%d, first at index %d%n",
                    i + 1, shorten(p.text), p.count, p.text.length(), p.firstPosition);
        }

        List<RepeatedPhrase> byLength = new ArrayList<>(phrases);
        sortByLengthDesc(byLength);
        System.out.println("\nLongest (top " + Math.min(TOP_LONGEST_LIMIT, byLength.size()) + "):");
        for (int i = 0; i < Math.min(TOP_LONGEST_LIMIT, byLength.size()); i++) {
            RepeatedPhrase p = byLength.get(i);
            System.out.printf("  %2d. \"%s\"%n     occurrences=%d, length=%d, first at index %d%n",
                    i + 1, shorten(p.text), p.count, p.text.length(), p.firstPosition);
        }
    }

    /**
     * Finds maximal repeated substrings of length >= minLength. For every suffix
     * of the text a Z-array is built (same algorithm as modules.ZFunctionSearch);
     * any Z-value >= minLength reveals a substring repeated at two offsets.
     * Occurrence counts are then verified with modules.ZFunctionSearch.
     */
    public static List<RepeatedPhrase> findRepeatedPhrases(String text, int minLength) {
        LinkedHashMap<String, Integer> firstSeenAt = new LinkedHashMap<>();
        int n = text.length();
        boolean capped = false;
        for (int i = 0; i + minLength <= n && !capped; i++) {
            int[] z = buildZArray(text.substring(i));
            for (int j = 1; j < z.length; j++) {
                if (z[j] >= minLength) {
                    String phrase = text.substring(i, i + z[j]);
                    if (!firstSeenAt.containsKey(phrase)) {
                        firstSeenAt.put(phrase, i);
                        if (firstSeenAt.size() >= MAX_PHRASE_CANDIDATES) {
                            capped = true;
                            break;
                        }
                    }
                }
            }
        }
        List<RepeatedPhrase> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : firstSeenAt.entrySet()) {
            int count = ZFunctionSearch.search(text, entry.getKey()).size();
            if (count >= 2) {
                result.add(new RepeatedPhrase(entry.getKey(), count, entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Drops repeats that add no information: a phrase that is a proper substring
     * of a longer repeated phrase with the same occurrence count is explained by
     * the longer one, so only the longest representative of each repeat family
     * (and any shorter phrase occurring strictly more often) is kept.
     */
    public static List<RepeatedPhrase> filterSubsumedRepeats(List<RepeatedPhrase> phrases) {
        List<RepeatedPhrase> kept = new ArrayList<>();
        for (RepeatedPhrase p : phrases) {
            boolean subsumed = false;
            for (RepeatedPhrase q : phrases) {
                if (p != q && q.text.length() > p.text.length()
                        && q.text.contains(p.text) && q.count == p.count) {
                    subsumed = true;
                    break;
                }
            }
            if (!subsumed) {
                kept.add(p);
            }
        }
        return kept;
    }

    /** Standard Z-array: z[i] = length of the longest substring starting at i that is also a prefix of s. */
    private static int[] buildZArray(String s) {
        int n = s.length();
        int[] z = new int[n];
        int left = 0;
        int right = 0;
        for (int i = 1; i < n; i++) {
            if (i <= right) {
                z[i] = Math.min(right - i + 1, z[i - left]);
            }
            while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) {
                z[i]++;
            }
            if (i + z[i] - 1 > right) {
                left = i;
                right = i + z[i] - 1;
            }
        }
        return z;
    }

    private static void sortByCountDesc(List<RepeatedPhrase> list) {
        for (int i = 1; i < list.size(); i++) {
            RepeatedPhrase key = list.get(i);
            int j = i - 1;
            while (j >= 0 && (list.get(j).count < key.count
                    || (list.get(j).count == key.count
                            && list.get(j).text.length() < key.text.length()))) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    private static void sortByLengthDesc(List<RepeatedPhrase> list) {
        for (int i = 1; i < list.size(); i++) {
            RepeatedPhrase key = list.get(i);
            int j = i - 1;
            while (j >= 0 && (list.get(j).text.length() < key.text.length()
                    || (list.get(j).text.length() == key.text.length()
                            && list.get(j).count < key.count))) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    private static String shorten(String phrase) {
        if (phrase.length() <= PHRASE_DISPLAY_LENGTH) {
            return phrase;
        }
        return phrase.substring(0, PHRASE_DISPLAY_LENGTH - 3) + "...";
    }

    // ------------------------------------------------------------------
    // Option 3: Rabin-Karp code search over assignment IDs + course codes
    // ------------------------------------------------------------------

    /** Rolling-hash scan of one record: every hash-equal window and the verified subset. */
    public static final class RKScanResult {
        public final List<Integer> hashHits;
        public final List<Integer> verified;

        RKScanResult(List<Integer> hashHits, List<Integer> verified) {
            this.hashHits = hashHits;
            this.verified = verified;
        }
    }

    /** One record (assignment ID or course code) matched by the Rabin-Karp code search. */
    public static final class RKRecordScan {
        public final String recordType;
        public final String recordId;
        public final int hashHits;
        public final List<Integer> verified;

        RKRecordScan(String recordType, String recordId, int hashHits, List<Integer> verified) {
            this.recordType = recordType;
            this.recordId = recordId;
            this.hashHits = hashHits;
            this.verified = Collections.unmodifiableList(verified);
        }

        public int spurious() {
            return hashHits - verified.size();
        }
    }

    /** Aggregate result of a Rabin-Karp scan over all assignment IDs and course codes. */
    public static final class RKCorpusReport {
        public final int recordsScanned;
        public final int totalHashHits;
        public final int totalVerified;
        public final List<RKRecordScan> matchedRecords;

        RKCorpusReport(int recordsScanned, int totalHashHits, int totalVerified,
                List<RKRecordScan> matchedRecords) {
            this.recordsScanned = recordsScanned;
            this.totalHashHits = totalHashHits;
            this.totalVerified = totalVerified;
            this.matchedRecords = Collections.unmodifiableList(matchedRecords);
        }

        public int spurious() {
            return totalHashHits - totalVerified;
        }
    }

    /**
     * Case-insensitive Rabin-Karp search over every assignment ID and course code.
     * Rolling-hash hits come from the local scan; verified positions are confirmed
     * by modules.RabinKarpSearch.
     */
    public static RKCorpusReport rabinKarpScanIds(DataStore ds, String pattern) {
        String needle = pattern.toUpperCase(Locale.ROOT);
        List<RKRecordScan> matched = new ArrayList<>();
        int recordsScanned = 0;
        int totalHashHits = 0;
        int totalVerified = 0;
        for (Assignment assignment : ds.assignments()) {
            recordsScanned++;
            String id = assignment.id.toUpperCase(Locale.ROOT);
            RKScanResult scan = rabinKarpScan(id, needle);
            List<Integer> verified = RabinKarpSearch.search(id, needle);
            totalHashHits += scan.hashHits.size();
            totalVerified += verified.size();
            if (!verified.isEmpty()) {
                matched.add(new RKRecordScan("Assignment ID", assignment.id,
                        scan.hashHits.size(), verified));
            }
        }
        for (Course course : ds.courses()) {
            recordsScanned++;
            String code = course.code.toUpperCase(Locale.ROOT);
            RKScanResult scan = rabinKarpScan(code, needle);
            List<Integer> verified = RabinKarpSearch.search(code, needle);
            totalHashHits += scan.hashHits.size();
            totalVerified += verified.size();
            if (!verified.isEmpty()) {
                matched.add(new RKRecordScan("Course code", course.code,
                        scan.hashHits.size(), verified));
            }
        }
        return new RKCorpusReport(recordsScanned, totalHashHits, totalVerified, matched);
    }

    private static void runRabinKarpCodeSearch(Scanner sc, DataStore ds) {
        System.out.println("\n--- Rabin-Karp Code Search ---");
        String pattern = readLine(sc, "Enter code pattern : ");
        if (pattern.isEmpty()) {
            System.out.println("\nPattern cannot be empty.");
            return;
        }
        pattern = pattern.toUpperCase(Locale.ROOT);

        long start = System.nanoTime();
        RKCorpusReport report = rabinKarpScanIds(ds, pattern);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        List<String> matchedRecords = new ArrayList<>();
        for (RKRecordScan record : report.matchedRecords) {
            matchedRecords.add(record.recordId
                    + "  hash-hits=" + record.hashHits
                    + " (spurious=" + record.spurious() + ")"
                    + "\n    verified matches at: " + formatPositions(record.verified));
        }

        System.out.println("\n--- Rabin-Karp Code Search Results ---");
        System.out.println("Pattern           : " + pattern);
        System.out.println("Records scanned   : " + report.recordsScanned
                + " (" + ds.assignments().size() + " assignment IDs, "
                + ds.courses().size() + " course codes)");
        System.out.println("Records matched   : " + report.matchedRecords.size());
        System.out.println("Rolling-hash hits : " + report.totalHashHits);
        System.out.println("Verified matches  : " + report.totalVerified);
        System.out.println("Spurious hits     : " + report.spurious());
        System.out.println("Time taken        : " + elapsedMs + " ms");

        printRecordBlock("Matching Records", matchedRecords);
    }

    /**
     * Hand-written Rabin-Karp rolling-hash scan that exposes every window whose
     * hash equals the pattern hash (hits) and the subset confirmed character by
     * character (verified). Matches modules.RabinKarpSearch on verified output.
     */
    public static RKScanResult rabinKarpScan(String text, String pattern) {
        List<Integer> hashHits = new ArrayList<>();
        List<Integer> verified = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || m > n) {
            return new RKScanResult(hashHits, verified);
        }

        long patternHash = 0;
        long textHash = 0;
        long highestPower = 1;
        for (int i = 0; i < m - 1; i++) {
            highestPower = (highestPower * RK_BASE) % RK_MOD;
        }
        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * RK_BASE + pattern.charAt(i)) % RK_MOD;
            textHash = (textHash * RK_BASE + text.charAt(i)) % RK_MOD;
        }

        for (int i = 0; i <= n - m; i++) {
            if (patternHash == textHash) {
                hashHits.add(i);
                if (text.regionMatches(i, pattern, 0, m)) {
                    verified.add(i);
                }
            }
            if (i < n - m) {
                textHash = (textHash - text.charAt(i) * highestPower % RK_MOD + RK_MOD) % RK_MOD;
                textHash = (textHash * RK_BASE + text.charAt(i + m)) % RK_MOD;
            }
        }
        return new RKScanResult(hashHits, verified);
    }

    // ------------------------------------------------------------------
    // Option 4: Aho-Corasick multi-keyword scan
    // ------------------------------------------------------------------

    private static void runAhoCorasickScan(Scanner sc, DataStore ds) {
        System.out.println("\n--- Aho-Corasick Multi-Keyword Scan ---");
        System.out.println("Default keywords: " + String.join(", ", DEFAULT_KEYWORDS));

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String kw : DEFAULT_KEYWORDS) {
            keywords.add(kw);
        }
        String extra = readLine(sc, "Enter extra keywords separated by commas (empty for none) : ");
        if (!extra.isEmpty()) {
            for (String part : extra.split(",")) {
                String kw = part.trim().toLowerCase(Locale.ROOT);
                if (!kw.isEmpty()) {
                    keywords.add(kw);
                }
            }
        }

        long buildStart = System.nanoTime();
        M1AhoCorasick automaton = new M1AhoCorasick();
        for (String kw : keywords) {
            automaton.addPattern(kw);
        }
        automaton.build();
        long buildMs = (System.nanoTime() - buildStart) / 1_000_000;

        System.out.println("\nAutomaton built: " + automaton.patternCount() + " keywords, "
                + automaton.stateCount() + " trie states, build time " + buildMs + " ms");

        System.out.println("\nScan target:");
        System.out.println("1. Assignment texts (" + ds.assignments().size() + " documents)");
        System.out.println("2. Wikipedia document (DataSets/Wikipedia.txt)");
        System.out.println("3. Both");
        System.out.print("Select target : ");
        int target = readInt(sc);
        if (target < 1 || target > 3) {
            System.out.println("\nInvalid target.");
            return;
        }

        if (target == 1 || target == 3) {
            String corpus = concatAssignmentTexts(ds);
            scanAndReport(automaton, "Assignment texts", corpus);
        }
        if (target == 2 || target == 3) {
            String wikipedia;
            try {
                wikipedia = ds.loadWikipediaText();
            } catch (IOException e) {
                System.out.println("\nCould not load Wikipedia.txt: " + e.getMessage());
                System.out.println("Place the file under DataSets/Wikipedia.txt and try again.");
                return;
            }
            scanAndReport(automaton, "Wikipedia document", wikipedia);
        }
    }

    public static String concatAssignmentTexts(DataStore ds) {
        StringBuilder sb = new StringBuilder();
        for (Assignment assignment : ds.assignments()) {
            sb.append(assignment.text).append('\n');
        }
        return sb.toString();
    }

    private static void scanAndReport(M1AhoCorasick automaton, String label, String rawText) {
        String text = rawText.toLowerCase(Locale.ROOT);
        long start = System.nanoTime();
        M1AhoCorasick.Result result = automaton.search(text);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\n--- Aho-Corasick Results: " + label + " ---");
        System.out.println("Text length   : " + text.length() + " characters");
        System.out.println("Total matches : " + result.total);
        System.out.println("Time taken    : " + elapsedMs + " ms");
        System.out.println("\nPer-keyword hit counts:");
        for (int i = 0; i < automaton.patternCount(); i++) {
            System.out.printf("  %-12s : %d%n", automaton.pattern(i), result.counts[i]);
        }
        if (!result.samples.isEmpty()) {
            StringBuilder sb = new StringBuilder("Sample matches: ");
            int limit = Math.min(result.samples.size(), 10);
            for (int i = 0; i < limit; i++) {
                M1AhoCorasick.Match match = result.samples.get(i);
                sb.append('\'').append(automaton.pattern(match.patternIndex))
                        .append("' @ ").append(match.start);
                if (i < limit - 1) {
                    sb.append(", ");
                }
            }
            if (result.total > limit) {
                sb.append(" ...");
            }
            System.out.println(sb);
        }
    }

    // ------------------------------------------------------------------
    // Console input helpers (shared console-menu style)
    // ------------------------------------------------------------------

    private static int readInt(Scanner sc) {
        while (!sc.hasNextInt()) {
            sc.next();
            System.out.print("Please enter a valid number : ");
        }
        int value = sc.nextInt();
        sc.nextLine();
        return value;
    }

    private static String readLine(Scanner sc, String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    private static int readPositiveIntOrDefault(Scanner sc, int defaultValue) {
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(line);
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
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
        // --- KMP (modules.KMPSearch) on known strings ---
        check("KMP classic example",
                KMPSearch.search("ABABDABACDABABCABAB", "ABABCABAB").equals(List.of(10)));
        check("KMP overlapping matches in 'aaaa'",
                KMPSearch.search("aaaa", "aa").equals(List.of(0, 1, 2)));
        check("KMP no match",
                KMPSearch.search("abcdef", "gh").isEmpty());
        check("KMP empty pattern",
                KMPSearch.search("abcdef", "").isEmpty());

        // --- Z-function (modules.ZFunctionSearch + local buildZArray) ---
        check("Z-function overlapping matches in 'aaaa'",
                ZFunctionSearch.search("aaaa", "aa").equals(List.of(0, 1, 2)));
        int[] z = buildZArray("aabcaabxaaaz");
        check("Z-array of 'aabcaabxaaaz'",
                java.util.Arrays.equals(z, new int[] { 0, 1, 0, 0, 3, 1, 0, 0, 2, 2, 1, 0 }));

        List<RepeatedPhrase> phrases = findRepeatedPhrases("deadline met deadline", 3);
        check("Repeated phrase detector finds 'deadline' twice",
                containsPhrase(phrases, "deadline", 2, 0));
        List<RepeatedPhrase> ab = findRepeatedPhrases("ababab", 3);
        check("Repeated phrase detector on 'ababab' (maximal repeats)",
                ab.size() == 2
                        && containsPhrase(ab, "abab", 2, 0)
                        && containsPhrase(ab, "bab", 2, 1));
        check("Repeated phrase detector respects minimum length",
                findRepeatedPhrases("abab", 3).isEmpty());
        List<RepeatedPhrase> filtered = filterSubsumedRepeats(phrases);
        check("Subsumed-repeat filter keeps only 'deadline'",
                filtered.size() == 1 && containsPhrase(filtered, "deadline", 2, 0));
        List<RepeatedPhrase> abFiltered = filterSubsumedRepeats(ab);
        check("Subsumed-repeat filter on 'ababab' keeps only 'abab'",
                abFiltered.size() == 1 && containsPhrase(abFiltered, "abab", 2, 0));

        // --- Rabin-Karp (modules.RabinKarpSearch + local rolling-hash scan) ---
        check("Rabin-Karp classic example",
                RabinKarpSearch.search("AABAACAADAABAABA", "AABA").equals(List.of(0, 9, 12)));
        RKScanResult scan = rabinKarpScan("AABAACAADAABAABA", "AABA");
        check("Rolling-hash scan verified matches equal module result",
                scan.verified.equals(RabinKarpSearch.search("AABAACAADAABAABA", "AABA")));
        check("Rolling-hash hits cover verified matches",
                scan.hashHits.containsAll(scan.verified)
                        && scan.hashHits.size() >= scan.verified.size());
        check("Rolling-hash scan overlapping 'aa' in 'aaaa'",
                rabinKarpScan("aaaa", "aa").verified.equals(List.of(0, 1, 2)));

        // --- Aho-Corasick hand-computed examples ---
        M1AhoCorasick ac = new M1AhoCorasick();
        ac.addPattern("he");
        ac.addPattern("she");
        ac.addPattern("his");
        ac.addPattern("hers");
        ac.build();
        M1AhoCorasick.Result ushers = ac.search("ushers");
        check("Aho-Corasick 'ushers' per-pattern counts (he, she, his, hers)",
                ushers.counts[0] == 1 && ushers.counts[1] == 1
                        && ushers.counts[2] == 0 && ushers.counts[3] == 1
                        && ushers.total == 3);
        boolean sampleOk = false;
        for (M1AhoCorasick.Match match : ushers.samples) {
            if (ac.pattern(match.patternIndex).equals("she") && match.start == 1) {
                sampleOk = true;
            }
        }
        check("Aho-Corasick 'ushers' reports 'she' at index 1", sampleOk);

        M1AhoCorasick overlap = new M1AhoCorasick();
        overlap.addPattern("aa");
        M1AhoCorasick.Result aaaa = overlap.search("aaaa");
        check("Aho-Corasick overlapping 'aa' in 'aaaa' = 3 matches",
                aaaa.counts[0] == 3 && aaaa.total == 3);

        M1AhoCorasick nested = new M1AhoCorasick();
        nested.addPattern("a");
        nested.addPattern("aa");
        M1AhoCorasick.Result nestedResult = nested.search("aaaa");
        check("Aho-Corasick nested patterns 'a','aa' in 'aaaa' = 4 + 3 matches",
                nestedResult.counts[0] == 4 && nestedResult.counts[1] == 3
                        && nestedResult.total == 7);

        M1AhoCorasick none = new M1AhoCorasick();
        none.addPattern("xyz");
        check("Aho-Corasick no match",
                none.search("hello world").total == 0);

        // --- DataStore-driven checks (deterministic sample data, seed 42) ---
        DataStore ds = new DataStore();
        Student first = ds.students().get(0);
        check("DataStore first student is 'Aarav Sharma'",
                first.name.equals("Aarav Sharma"));
        check("KMP 'Sharma' in first student name at index 6",
                KMPSearch.search(first.name, "Sharma").equals(List.of(6)));

        Course cs201 = null;
        for (Course course : ds.courses()) {
            if (course.code.equals("CS201")) {
                cs201 = course;
            }
        }
        check("DataStore course CS201 is 'Data Structures'",
                cs201 != null && cs201.name.equals("Data Structures"));
        check("KMP 'Data' in CS201 name at index 0",
                cs201 != null && KMPSearch.search(cs201.name, "Data").equals(List.of(0)));

        Assignment firstAsg = ds.assignments().get(0);
        check("DataStore first assignment id is 'ASG-CS101-1'",
                firstAsg.id.equals("ASG-CS101-1"));
        check("Rabin-Karp 'CS101' in first assignment id at index 4",
                RabinKarpSearch.search(firstAsg.id, "CS101").equals(List.of(4)));

        M1AhoCorasick corpus = new M1AhoCorasick();
        for (String kw : DEFAULT_KEYWORDS) {
            corpus.addPattern(kw);
        }
        M1AhoCorasick.Result corpusResult = corpus.search(concatAssignmentTexts(ds).toLowerCase(Locale.ROOT));
        long sum = 0;
        for (long c : corpusResult.counts) {
            sum += c;
        }
        check("Aho-Corasick total equals sum of per-keyword counts",
                corpusResult.total == sum);
        check("Aho-Corasick finds 'assignment' at least once per assignment text",
                corpusResult.counts[indexOf(corpus, "assignment")] >= ds.assignments().size());
        check("Repeated phrase detector finds boilerplate in a real assignment",
                !findRepeatedPhrases(firstAsg.text, DEFAULT_MIN_PHRASE_LENGTH).isEmpty());

        // --- Hardening checks: edge cases, thread-safety, public API contracts ---
        check("Aho-Corasick empty text yields zero matches",
                ac.search("").total == 0);
        check("Aho-Corasick with no patterns yields zero matches",
                new M1AhoCorasick().search("anything").total == 0);
        boolean nullRejected = false;
        try {
            ac.search(null);
        } catch (IllegalArgumentException e) {
            nullRejected = true;
        }
        check("Aho-Corasick rejects null text", nullRejected);

        check("Rabin-Karp scan pattern longer than text",
                rabinKarpScan("AB", "ABC").hashHits.isEmpty()
                        && rabinKarpScan("AB", "ABC").verified.isEmpty());
        check("Repeated phrase detector on empty text",
                findRepeatedPhrases("", 5).isEmpty());
        check("Repeated phrase detector min length above text length",
                findRepeatedPhrases("abc", 10).isEmpty());
        check("Subsumed-repeat filter keeps shorter phrase with higher count",
                filterSubsumedRepeats(findRepeatedPhrases("aaa", 1)).size() == 2);

        M1AhoCorasick shared = new M1AhoCorasick();
        shared.addPattern("deadline");
        String sharedText = "deadline deadline deadline";
        long[] totals = new long[2];
        Thread worker1 = new Thread(() -> totals[0] = shared.search(sharedText).total);
        Thread worker2 = new Thread(() -> totals[1] = shared.search(sharedText).total);
        worker1.start();
        worker2.start();
        try {
            worker1.join();
            worker2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        check("Aho-Corasick concurrent lazy-build search is consistent",
                totals[0] == 3 && totals[1] == 3);

        List<KmpMatch> kmpMatches = kmpSearchCoursesAndStudents(ds, "data");
        check("KMP record search 'data' matches exactly CS201 and CS301",
                kmpMatches.size() == 2
                        && kmpMatches.get(0).recordType.equals("Course")
                        && kmpMatches.get(0).idOrCode.equals("CS201")
                        && kmpMatches.get(0).namePositions.equals(List.of(0))
                        && kmpMatches.get(1).idOrCode.equals("CS301"));
        check("KMP record search with empty keyword matches nothing",
                kmpSearchCoursesAndStudents(ds, "").isEmpty());
        List<KmpMatch> sharma = kmpSearchCoursesAndStudents(ds, "sharma");
        boolean sharmaOk = !sharma.isEmpty();
        for (KmpMatch m : sharma) {
            sharmaOk = sharmaOk && m.recordType.equals("Student") && m.occurrenceCount() >= 1;
        }
        check("KMP record search 'sharma' returns only student matches", sharmaOk);

        RKCorpusReport rkReport = rabinKarpScanIds(ds, "CS101");
        check("Rabin-Karp corpus scan 'CS101' totals",
                rkReport.recordsScanned == ds.assignments().size() + ds.courses().size()
                        && rkReport.matchedRecords.size() == 3
                        && rkReport.totalVerified == 3
                        && rkReport.matchedRecords.get(0).recordId.equals("ASG-CS101-1")
                        && rkReport.matchedRecords.get(0).verified.equals(List.of(4))
                        && rkReport.matchedRecords.get(0).recordType.equals("Assignment ID"));

        String[] kw = defaultKeywords();
        kw[0] = "MUTATED";
        check("defaultKeywords returns a defensive copy",
                defaultKeywords()[0].equals("exam") && defaultKeywords().length == 10);

        System.out.println("\nSelf-test: " + (checksRun - checksFailed) + "/" + checksRun + " checks passed.");
        if (checksFailed > 0) {
            System.out.println("SELF-TEST FAILED (" + checksFailed + " failures)");
            System.exit(1);
        }
        System.out.println("ALL CHECKS PASSED");
    }

    private static int indexOf(M1AhoCorasick automaton, String pattern) {
        for (int i = 0; i < automaton.patternCount(); i++) {
            if (automaton.pattern(i).equals(pattern)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsPhrase(List<RepeatedPhrase> phrases, String text,
            int count, int firstPosition) {
        for (RepeatedPhrase phrase : phrases) {
            if (phrase.text.equals(text) && phrase.count == count
                    && phrase.firstPosition == firstPosition) {
                return true;
            }
        }
        return false;
    }
}
