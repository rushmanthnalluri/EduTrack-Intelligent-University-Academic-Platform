package edutrack.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import edutrack.data.DataStore;
import edutrack.model.Assignment;

public class M2SuffixStructures {

    private static final int MAX_DOC_CHARS = 200_000;
    private static final int DISPLAY_LIMIT = 20;
    private static final int PREVIEW_LEN = 60;

    public static void run(Scanner sc, DataStore ds) {
        int choice;
        do {
            System.out.println("\n--- M2: Suffix Structures for Document Similarity ---");
            System.out.println("1. Suffix Array Index (prefix-doubling) - substring search");
            System.out.println("2. SA-IS Linear-Time Construction - verify vs prefix-doubling");
            System.out.println("3. Kasai LCP - repeated phrases & cross-submission similarity");
            System.out.println("4. Suffix Automaton - occurrence counts & distinct substrings");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    suffixArrayIndex(sc, ds);
                    break;
                case 2:
                    saisConstruction(sc, ds);
                    break;
                case 3:
                    lcpAnalysis(sc, ds);
                    break;
                case 4:
                    suffixAutomatonMenu(sc, ds);
                    break;
                case 0:
                    System.out.println("Returning to main menu...");
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    private static void suffixArrayIndex(Scanner sc, DataStore ds) {
        System.out.println("\n--- Suffix Array Index (Prefix-Doubling) ---");
        String[] doc = chooseDocument(sc, ds);
        if (doc == null) {
            return;
        }
        String text = doc[0];
        String label = doc[1];

        long start = System.nanoTime();
        int[] sa = M2SuffixArray.buildSuffixArray(text);
        long nanos = System.nanoTime() - start;

        System.out.println("\nDocument      : " + label + " (" + text.length() + " chars)");
        System.out.println("Build time    : " + fmtMs(nanos) + " ms");
        printSampleSuffixes(text, sa, 5);

        while (true) {
            String pattern = readLine(sc, "\nEnter substring to search (empty to stop) : ");
            if (pattern.isEmpty()) {
                break;
            }
            start = System.nanoTime();
            List<Integer> occurrences = M2SuffixArray.findOccurrences(text, sa, pattern);
            nanos = System.nanoTime() - start;

            System.out.println("Pattern       : " + preview(pattern, PREVIEW_LEN));
            System.out.println("Occurrences   : " + occurrences.size());
            System.out.println("Query time    : " + fmtMs(nanos) + " ms");
            if (occurrences.isEmpty()) {
                System.out.println("Pattern not found.");
            } else {
                printPositions("Match indices", occurrences);
            }
        }
    }

    private static void saisConstruction(Scanner sc, DataStore ds) {
        System.out.println("\n--- SA-IS Linear-Time Suffix Array Construction ---");
        String[] doc = chooseDocument(sc, ds);
        if (doc == null) {
            return;
        }
        String text = doc[0];
        String label = doc[1];

        long start = System.nanoTime();
        int[] saSais = M2SAIS.buildSuffixArray(text);
        long saisNanos = System.nanoTime() - start;

        start = System.nanoTime();
        int[] saDoubling = M2SuffixArray.buildSuffixArray(text);
        long doublingNanos = System.nanoTime() - start;

        System.out.println("\nDocument           : " + label + " (" + text.length() + " chars)");
        System.out.println("SA-IS build time   : " + fmtMs(saisNanos) + " ms");
        System.out.println("Doubling build time: " + fmtMs(doublingNanos) + " ms");
        System.out.println("Matches prefix-doubling result : "
                + (Arrays.equals(saSais, saDoubling) ? "YES" : "NO"));
        printSampleSuffixes(text, saSais, 5);
    }

    private static void lcpAnalysis(Scanner sc, DataStore ds) {
        System.out.println("\n--- Kasai LCP: Repeated Phrases & Similarity ---");
        System.out.println("1. Longest repeated substrings within one assignment");
        System.out.println("2. Longest common substring between two assignments");
        System.out.println("0. Back");
        System.out.print("Enter your choice : ");

        int choice = readInt(sc);
        switch (choice) {
            case 1:
                repeatedWithinAssignment(sc, ds);
                break;
            case 2:
                commonBetweenAssignments(sc, ds);
                break;
            case 0:
                break;
            default:
                System.out.println("\nInvalid Choice!");
        }
    }

    private static void repeatedWithinAssignment(Scanner sc, DataStore ds) {
        Assignment a = chooseAssignment(sc, ds);
        if (a == null) {
            return;
        }
        String text = a.text;

        long start = System.nanoTime();
        int[] sa = M2SuffixArray.buildSuffixArray(text);
        int[] lcp = M2KasaiLCP.buildLCP(text, sa);
        long nanos = System.nanoTime() - start;

        System.out.println("\n--- Repeated Phrase Report: " + a.id + " ---");
        System.out.println("Text length   : " + text.length());
        System.out.println("SA + LCP time : " + fmtMs(nanos) + " ms");

        int maxIdx = -1;
        for (int i = 0; i < lcp.length; i++) {
            if (maxIdx == -1 || lcp[i] > lcp[maxIdx]) {
                maxIdx = i;
            }
        }
        if (maxIdx == -1 || lcp[maxIdx] == 0) {
            System.out.println("No repeated substring found.");
            return;
        }

        String longest = text.substring(sa[maxIdx], sa[maxIdx] + lcp[maxIdx]);
        List<Integer> all = M2SuffixArray.findOccurrences(text, sa, longest);
        System.out.println("Longest repeated substring length : " + lcp[maxIdx]);
        System.out.println("Phrase : \"" + preview(longest, PREVIEW_LEN) + "\"");
        System.out.println("Occurs " + all.size() + " times.");
        printPositions("Positions", all);

        System.out.println("Top repeated substrings:");
        boolean[] used = new boolean[lcp.length];
        for (int t = 0; t < 5 && t < lcp.length; t++) {
            int best = -1;
            for (int i = 0; i < lcp.length; i++) {
                if (!used[i] && (best == -1 || lcp[i] > lcp[best])) {
                    best = i;
                }
            }
            if (best == -1 || lcp[best] == 0) {
                break;
            }
            used[best] = true;
            String phrase = text.substring(sa[best], sa[best] + lcp[best]);
            System.out.println("  " + (t + 1) + ". length " + lcp[best]
                    + " at suffixes " + sa[best] + " & " + sa[best + 1]
                    + " : \"" + preview(phrase, PREVIEW_LEN) + "\"");
        }
    }

    private static void commonBetweenAssignments(Scanner sc, DataStore ds) {
        System.out.println("\nSelect the FIRST assignment.");
        Assignment a = chooseAssignment(sc, ds);
        if (a == null) {
            return;
        }
        System.out.println("Select the SECOND assignment.");
        Assignment b = chooseAssignment(sc, ds);
        if (b == null) {
            return;
        }

        long start = System.nanoTime();
        int[] lcs = crossLcs(a.text, b.text);
        long nanos = System.nanoTime() - start;
        int best = lcs[0];
        int bestPosA = lcs[1];
        int bestPosB = lcs[2];
        int tieCount = lcs[3];

        System.out.println("\n--- Cross-Submission Similarity Report ---");
        System.out.println("Assignment A  : " + a.id + " (" + a.text.length() + " chars)");
        System.out.println("Assignment B  : " + b.id + " (" + b.text.length() + " chars)");
        System.out.println("SA + LCP time : " + fmtMs(nanos) + " ms");
        if (best == 0) {
            System.out.println("No common substring found.");
            return;
        }

        String phrase = a.text.substring(bestPosA, bestPosA + best);
        List<Integer> inA = naiveFindAll(a.text, phrase);
        List<Integer> inB = naiveFindAll(b.text, phrase);
        System.out.println("Longest common substring length : " + best
                + " (shared by " + tieCount + " suffix pair(s))");
        System.out.println("Phrase : \"" + preview(phrase, PREVIEW_LEN) + "\"");
        System.out.println("In " + a.id + " : " + inA.size() + " occurrence(s)");
        printPositions("  positions", inA);
        System.out.println("In " + b.id + " : " + inB.size() + " occurrence(s)");
        printPositions("  positions", inB);
        if (best >= 40) {
            System.out.println("Assessment : long shared phrase - likely boilerplate or copied text.");
        } else if (best >= 15) {
            System.out.println("Assessment : moderate phrase overlap between submissions.");
        } else {
            System.out.println("Assessment : minimal overlap only.");
        }
    }

    private static void suffixAutomatonMenu(Scanner sc, DataStore ds) {
        System.out.println("\n--- Suffix Automaton ---");
        String[] doc = chooseDocument(sc, ds);
        if (doc == null) {
            return;
        }
        String text = doc[0];
        String label = doc[1];

        long start = System.nanoTime();
        M2SuffixAutomaton automaton = M2SuffixAutomaton.build(text);
        long nanos = System.nanoTime() - start;

        System.out.println("\nDocument           : " + label + " (" + text.length() + " chars)");
        System.out.println("Build time         : " + fmtMs(nanos) + " ms");
        System.out.println("Automaton states   : " + automaton.stateCount());
        System.out.println("Distinct substrings: " + automaton.countDistinctSubstrings());

        int choice;
        do {
            System.out.println("\n1. Substring occurrence query");
            System.out.println("2. Longest common substring with entered string");
            System.out.println("0. Back");
            System.out.print("Enter your choice : ");
            choice = readInt(sc);

            switch (choice) {
                case 1: {
                    String pattern = readLine(sc, "Enter substring X : ");
                    if (pattern.isEmpty()) {
                        System.out.println("Pattern cannot be empty.");
                        break;
                    }
                    start = System.nanoTime();
                    boolean occurs = automaton.contains(pattern);
                    long count = automaton.countOccurrences(pattern);
                    nanos = System.nanoTime() - start;
                    System.out.println("Occurs in document : " + (occurs ? "YES" : "NO"));
                    System.out.println("Occurrence count   : " + count);
                    System.out.println("Query time         : " + fmtMs(nanos) + " ms");
                    break;
                }
                case 2: {
                    String other = readLine(sc, "Enter string Y : ");
                    if (other.isEmpty()) {
                        System.out.println("String cannot be empty.");
                        break;
                    }
                    start = System.nanoTime();
                    int[] result = automaton.longestCommonSubstring(other);
                    nanos = System.nanoTime() - start;
                    String witness = other.substring(result[1] - result[0], result[1]);
                    System.out.println("LCS length : " + result[0]);
                    if (result[0] > 0) {
                        System.out.println("LCS        : \"" + preview(witness, PREVIEW_LEN) + "\"");
                    }
                    System.out.println("Query time : " + fmtMs(nanos) + " ms");
                    break;
                }
                case 0:
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    private static String[] chooseDocument(Scanner sc, DataStore ds) {
        List<Assignment> assignments = ds.assignments();
        int wikiOption = assignments.size() + 1;

        System.out.println("\nSelect a document:");
        for (int i = 0; i < assignments.size(); i += 2) {
            Assignment a = assignments.get(i);
            System.out.printf(" %2d. %-15s (%4d chars)", i + 1, a.id, a.text.length());
            if (i + 1 < assignments.size()) {
                Assignment b = assignments.get(i + 1);
                System.out.printf("   %2d. %-15s (%4d chars)", i + 2, b.id, b.text.length());
            }
            System.out.println();
        }
        System.out.println(" " + wikiOption + ". Wikipedia.txt (first " + MAX_DOC_CHARS + " chars)");
        System.out.println("  0. Cancel");
        System.out.print("Enter document number : ");

        int choice = readInt(sc);
        if (choice == 0) {
            return null;
        }
        if (choice == wikiOption) {
            try {
                String wiki = ds.loadWikipediaText();
                if (wiki.length() > MAX_DOC_CHARS) {
                    System.out.println("Note: document capped at " + MAX_DOC_CHARS
                            + " chars (original " + wiki.length() + ").");
                    wiki = wiki.substring(0, MAX_DOC_CHARS);
                }
                return new String[] { wiki, "Wikipedia.txt" };
            } catch (IOException e) {
                System.out.println("\nCould not load Wikipedia.txt: " + e.getMessage());
                System.out.println("Please make sure DataSets/Wikipedia.txt exists.");
                return null;
            }
        }
        if (choice >= 1 && choice <= assignments.size()) {
            Assignment a = assignments.get(choice - 1);
            return new String[] { a.text, a.id };
        }
        System.out.println("\nInvalid document number!");
        return null;
    }

    private static Assignment chooseAssignment(Scanner sc, DataStore ds) {
        List<Assignment> assignments = ds.assignments();
        System.out.println("\nSelect an assignment:");
        for (int i = 0; i < assignments.size(); i += 2) {
            Assignment a = assignments.get(i);
            System.out.printf(" %2d. %-15s (%4d chars)", i + 1, a.id, a.text.length());
            if (i + 1 < assignments.size()) {
                Assignment b = assignments.get(i + 1);
                System.out.printf("   %2d. %-15s (%4d chars)", i + 2, b.id, b.text.length());
            }
            System.out.println();
        }
        System.out.println("  0. Cancel");
        System.out.print("Enter assignment number : ");

        int choice = readInt(sc);
        if (choice >= 1 && choice <= assignments.size()) {
            return assignments.get(choice - 1);
        }
        if (choice != 0) {
            System.out.println("\nInvalid assignment number!");
        }
        return null;
    }

    public static int longestRepeatedLength(String text) {
        if (text == null) throw new IllegalArgumentException("text must not be null");
        if (text.length() < 2) {
            return 0;
        }
        int[] sa = M2SuffixArray.buildSuffixArray(text);
        int[] lcp = M2KasaiLCP.buildLCP(text, sa);
        int best = 0;
        for (int v : lcp) {
            if (v > best) {
                best = v;
            }
        }
        return best;
    }

    public static int[] crossLcs(String t1, String t2) {
        if (t1 == null || t2 == null) {
            throw new IllegalArgumentException("texts must not be null");
        }
        int n1 = t1.length();
        char separator = chooseSeparator(t1, t2);
        String combined = t1 + separator + t2;
        int[] sa = M2SuffixArray.buildSuffixArray(combined);
        int[] lcp = M2KasaiLCP.buildLCP(combined, sa);
        int best = 0;
        int bestPosA = -1;
        int bestPosB = -1;
        int ties = 0;
        for (int i = 0; i < lcp.length; i++) {
            boolean firstSide = sa[i] < n1;
            boolean secondSide = sa[i + 1] < n1;
            if (firstSide != secondSide) {
                if (lcp[i] > best) {
                    best = lcp[i];
                    bestPosA = Math.min(sa[i], sa[i + 1]);
                    bestPosB = Math.max(sa[i], sa[i + 1]) - n1 - 1;
                    ties = 1;
                } else if (lcp[i] == best) {
                    ties++;
                }
            }
        }
        return new int[] { best, bestPosA, bestPosB, ties };
    }

    public static int crossLcsLength(String t1, String t2) {
        return crossLcs(t1, t2)[0];
    }

    public static List<int[]> topRepeatedSubstrings(String text, int[] sa, int[] lcp, int k) {
        if (text == null || sa == null || lcp == null) {
            throw new IllegalArgumentException("text, suffix array and LCP array must not be null");
        }
        if (sa.length != text.length() || lcp.length != Math.max(0, text.length() - 1)) {
            throw new IllegalArgumentException("suffix array/LCP lengths do not match text");
        }
        if (k < 0) {
            throw new IllegalArgumentException("k must be non-negative");
        }
        if (k == 0 || lcp.length == 0) {
            return new ArrayList<>();
        }
        List<int[]> result = new ArrayList<>();
        Integer[] order = new Integer[lcp.length];
        for (int i = 0; i < lcp.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(lcp[b], lcp[a]));
        List<String> chosen = new ArrayList<>();
        for (int idx : order) {
            if (lcp[idx] == 0) {
                break;
            }
            String phrase = text.substring(sa[idx], sa[idx] + lcp[idx]);
            boolean covered = false;
            for (String existing : chosen) {
                if (existing.contains(phrase)) {
                    covered = true;
                    break;
                }
            }
            if (covered) {
                continue;
            }
            chosen.add(phrase);
            result.add(new int[] { lcp[idx], sa[idx] });
            if (result.size() == k) {
                break;
            }
        }
        return result;
    }

    private static char chooseSeparator(String t1, String t2) {
        boolean[] used = new boolean[1 << 16];
        for (int i = 0; i < t1.length(); i++) {
            used[t1.charAt(i)] = true;
        }
        for (int i = 0; i < t2.length(); i++) {
            used[t2.charAt(i)] = true;
        }
        for (int c = 0; c < used.length; c++) {
            if (!used[c]) {
                return (char) c;
            }
        }
        throw new IllegalArgumentException("no separator character available");
    }

    private static void printSampleSuffixes(String text, int[] sa, int count) {
        System.out.println("First " + Math.min(count, sa.length) + " suffixes in sorted order:");
        int limit = Math.min(count, sa.length);
        for (int i = 0; i < limit; i++) {
            System.out.println("  sa[" + i + "] = " + sa[i] + " : \""
                    + preview(text.substring(sa[i]), 40) + "\"");
        }
    }

    private static void printPositions(String label, List<Integer> positions) {
        System.out.print(label + " : ");
        int limit = Math.min(positions.size(), DISPLAY_LIMIT);
        for (int i = 0; i < limit; i++) {
            System.out.print(positions.get(i));
            if (i < limit - 1) {
                System.out.print(", ");
            }
        }
        if (positions.size() > limit) {
            System.out.print(" ... (" + (positions.size() - limit) + " more)");
        }
        System.out.println();
    }

    private static String preview(String s, int max) {
        String flat = s.replace('\n', ' ').replace('\r', ' ');
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max) + "...";
    }

    private static String fmtMs(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
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

    private static String readLine(Scanner sc, String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    public static List<Integer> naiveFindAll(String text, String pattern) {
        List<Integer> result = new ArrayList<>();
        if (pattern.isEmpty()) {
            return result;
        }
        int idx = text.indexOf(pattern);
        while (idx >= 0) {
            result.add(idx);
            idx = text.indexOf(pattern, idx + 1);
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("--- M2 Suffix Structures Self-Test ---");
        testsRun = 0;
        testsFailed = 0;

        testSuffixArrayEquality();
        testKasaiAgainstNaive();
        testBinarySearchQueries();
        testSuffixAutomaton();
        testCrossAndRepeated();
        testEdgeCases();
        testWithDataStore();

        System.out.println("--------------------------------------");
        System.out.println("Self-test complete: " + (testsRun - testsFailed) + "/" + testsRun + " checks passed.");
        if (testsFailed > 0) {
            System.out.println("RESULT: FAIL (" + testsFailed + " failing)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL PASS");
    }

    private static int testsRun;
    private static int testsFailed;

    private static void check(String name, boolean ok) {
        testsRun++;
        if (!ok) {
            testsFailed++;
        }
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }

    private static void testSuffixArrayEquality() {
        String[] fixed = {
            "mississippi", "abababab", "banana", "abcabxabcd", "aaaa",
            "a", "ab", "ba", "abcdefgh", "aabaaaab", "zyxwvuts", ""
        };
        for (String s : fixed) {
            boolean eq = Arrays.equals(M2SAIS.buildSuffixArray(s), M2SuffixArray.buildSuffixArray(s));
            boolean naiveOk = s.length() <= 200
                    ? Arrays.equals(M2SuffixArray.buildSuffixArray(s), naiveSuffixArray(s))
                    : true;
            check("SA equality on \"" + abbrev(s) + "\" (SA-IS=doubling=" + eq
                    + ", doubling=naive:" + naiveOk + ")", eq && naiveOk);
        }

        String[] repetitive = { "a".repeat(2000), "ab".repeat(1000), "abc".repeat(700) };
        for (String s : repetitive) {
            check("SA-IS = doubling on repetitive len=" + s.length(),
                    Arrays.equals(M2SAIS.buildSuffixArray(s), M2SuffixArray.buildSuffixArray(s)));
        }

        String[] alphabets = { "ab", "abc", "abcde", "abcdefghijklmnopqrstuvwxyz" };
        int[] lengths = { 1, 2, 3, 7, 15, 64, 250, 1000, 5000 };
        for (String alphabet : alphabets) {
            Random rnd = new Random(12345 + alphabet.length());
            int total = 0;
            int matched = 0;
            int naiveTotal = 0;
            int naiveMatched = 0;
            for (int len : lengths) {
                for (int rep = 0; rep < 2; rep++) {
                    String s = randomString(rnd, alphabet, len);
                    total++;
                    if (Arrays.equals(M2SAIS.buildSuffixArray(s), M2SuffixArray.buildSuffixArray(s))) {
                        matched++;
                    }
                    if (len <= 120) {
                        naiveTotal++;
                        if (Arrays.equals(M2SuffixArray.buildSuffixArray(s), naiveSuffixArray(s))) {
                            naiveMatched++;
                        }
                    }
                }
            }
            check("SA-IS = doubling on random strings, alphabet=" + alphabet.length()
                    + " (" + matched + "/" + total + ")", matched == total);
            check("doubling = naive on small random strings, alphabet=" + alphabet.length()
                    + " (" + naiveMatched + "/" + naiveTotal + ")", naiveMatched == naiveTotal);
        }
    }

    private static void testKasaiAgainstNaive() {
        Random rnd = new Random(777);
        int total = 0;
        int matched = 0;
        String[] fixed = { "mississippi", "aaaa", "abababab", "abcabxabcd", "a", "" };
        for (String s : fixed) {
            total++;
            int[] sa = M2SuffixArray.buildSuffixArray(s);
            if (Arrays.equals(M2KasaiLCP.buildLCP(s, sa), naiveLcpArray(s, sa))) {
                matched++;
            }
        }
        for (int t = 0; t < 30; t++) {
            String s = randomString(rnd, "abc", 1 + rnd.nextInt(80));
            total++;
            int[] sa = M2SuffixArray.buildSuffixArray(s);
            if (Arrays.equals(M2KasaiLCP.buildLCP(s, sa), naiveLcpArray(s, sa))) {
                matched++;
            }
        }
        check("Kasai LCP = naive LCP on " + total + " strings (" + matched + "/" + total + ")",
                matched == total);
    }

    private static void testBinarySearchQueries() {
        Random rnd = new Random(999);
        int total = 0;
        int matched = 0;
        for (int t = 0; t < 40; t++) {
            String text = randomString(rnd, "abcd", 1 + rnd.nextInt(120));
            int[] sa = M2SuffixArray.buildSuffixArray(text);
            for (int q = 0; q < 3; q++) {
                String pattern;
                if (q == 0 && text.length() > 2) {
                    int from = rnd.nextInt(text.length() - 1);
                    int to = Math.min(text.length(), from + 1 + rnd.nextInt(6));
                    pattern = text.substring(from, to);
                } else if (q == 1) {
                    pattern = randomString(rnd, "abcd", 1 + rnd.nextInt(5));
                } else {
                    pattern = randomString(rnd, "xyz", 1 + rnd.nextInt(4));
                }
                total++;
                List<Integer> expected = naiveFindAll(text, pattern);
                if (M2SuffixArray.findOccurrences(text, sa, pattern).equals(expected)) {
                    matched++;
                }
            }
        }
        check("SA binary-search occurrences = naive on " + total + " queries ("
                + matched + "/" + total + ")", matched == total);
    }

    private static void testSuffixAutomaton() {
        Random rnd = new Random(31337);
        int countTotal = 0;
        int countMatched = 0;
        int distinctTotal = 0;
        int distinctMatched = 0;
        int lcsTotal = 0;
        int lcsMatched = 0;

        for (int t = 0; t < 25; t++) {
            String text = randomString(rnd, "abc", 1 + rnd.nextInt(100));
            M2SuffixAutomaton automaton = M2SuffixAutomaton.build(text);

            for (int q = 0; q < 3; q++) {
                String pattern;
                if (q == 0 && text.length() > 1) {
                    int from = rnd.nextInt(text.length());
                    int to = Math.min(text.length(), from + 1 + rnd.nextInt(5));
                    pattern = text.substring(from, to);
                } else {
                    pattern = randomString(rnd, q == 1 ? "abc" : "xy", 1 + rnd.nextInt(4));
                }
                countTotal++;
                boolean ok = automaton.contains(pattern) == !naiveFindAll(text, pattern).isEmpty()
                        && automaton.countOccurrences(pattern) == naiveFindAll(text, pattern).size();
                if (ok) {
                    countMatched++;
                }
            }

            if (text.length() <= 150) {
                distinctTotal++;
                if (automaton.countDistinctSubstrings() == naiveDistinctSubstrings(text)) {
                    distinctMatched++;
                }
            }

            String other = randomString(rnd, "abc", 1 + rnd.nextInt(60));
            lcsTotal++;
            int[] res = automaton.longestCommonSubstring(other);
            String witness = other.substring(res[1] - res[0], res[1]);
            int naiveLen = naiveLcsLength(text, other);
            if (res[0] == naiveLen && witness.length() == naiveLen
                    && text.contains(witness) && other.contains(witness)) {
                lcsMatched++;
            }
        }

        check("automaton occurrence counts = naive (" + countMatched + "/" + countTotal + ")",
                countMatched == countTotal);
        check("automaton distinct substrings = naive (" + distinctMatched + "/" + distinctTotal + ")",
                distinctMatched == distinctTotal);
        check("automaton LCS = naive (" + lcsMatched + "/" + lcsTotal + ")",
                lcsMatched == lcsTotal);
    }

    private static void testCrossAndRepeated() {
        Random rnd = new Random(4242);
        int crossTotal = 0;
        int crossMatched = 0;
        for (int t = 0; t < 30; t++) {
            String a = randomString(rnd, "abc", rnd.nextInt(60));
            String b = randomString(rnd, "abc", rnd.nextInt(60));
            crossTotal++;
            if (crossLcsLength(a, b) == naiveLcsLength(a, b)) {
                crossMatched++;
            }
        }
        check("cross-document LCS (SA+Kasai) = naive (" + crossMatched + "/" + crossTotal + ")",
                crossMatched == crossTotal);

        int repTotal = 0;
        int repMatched = 0;
        for (int t = 0; t < 30; t++) {
            String s = randomString(rnd, "ab", rnd.nextInt(80));
            repTotal++;
            if (longestRepeatedLength(s) == naiveLongestRepeatedLength(s)) {
                repMatched++;
            }
        }
        check("longest repeated substring (LCP) = naive (" + repMatched + "/" + repTotal + ")",
                repMatched == repTotal);
    }

    private static void testEdgeCases() {
        String extreme = "" + (char) 0 + (char) 65535 + "ab" + (char) 0 + "ba" + (char) 65535 + (char) 0;
        boolean extremeOk = Arrays.equals(M2SAIS.buildSuffixArray(extreme),
                M2SuffixArray.buildSuffixArray(extreme))
                && Arrays.equals(M2SuffixArray.buildSuffixArray(extreme), naiveSuffixArray(extreme));
        check("alphabet extremes (char 0 and char 65535): SA-IS = doubling = naive", extremeOk);

        Random rnd = new Random(2024);
        int total = 0;
        int matched = 0;
        for (int t = 0; t < 20; t++) {
            String a = randomString(rnd, "ab" + (char) 1, 1 + rnd.nextInt(50));
            String b = randomString(rnd, "bc" + (char) 1, 1 + rnd.nextInt(50));
            total++;
            if (crossLcsLength(a, b) == naiveLcsLength(a, b)) {
                matched++;
            }
        }
        check("cross-LCS robust to separator collision (" + matched + "/" + total + ")",
                matched == total);

        M2SuffixAutomaton incremental = new M2SuffixAutomaton(8);
        String part1 = "ababa";
        for (int i = 0; i < part1.length(); i++) {
            incremental.extend(part1.charAt(i));
        }
        long countBefore = incremental.countOccurrences("aba");
        String part2 = "bab";
        for (int i = 0; i < part2.length(); i++) {
            incremental.extend(part2.charAt(i));
        }
        String whole = part1 + part2;
        boolean incOk = countBefore == naiveFindAll(part1, "aba").size()
                && incremental.countOccurrences("aba") == naiveFindAll(whole, "aba").size()
                && incremental.countOccurrences("bab") == naiveFindAll(whole, "bab").size()
                && incremental.countDistinctSubstrings() == naiveDistinctSubstrings(whole);
        check("automaton recount after extend following a query", incOk);

        String big = randomString(new Random(7), "abcdefghijklmnopqrstuvwxyz", 100_000);
        long distinct = M2SuffixAutomaton.build(big).countDistinctSubstrings();
        check("distinct substrings exceed int range without overflow (" + distinct + ")",
                distinct > Integer.MAX_VALUE);

        check("crossLcs positions agree with length on assignment-like texts",
                crossLcsPositionsValid("the quick brown fox jumps", "a quick brown foxed dog"));
    }

    private static boolean crossLcsPositionsValid(String t1, String t2) {
        int[] r = crossLcs(t1, t2);
        if (r[0] <= 0) {
            return r[1] == -1 && r[2] == -1;
        }
        String phrase = t1.substring(r[1], r[1] + r[0]);
        return phrase.length() == r[0] && t2.startsWith(phrase, r[2])
                && r[0] == naiveLcsLength(t1, t2) && r[3] >= 1;
    }

    private static void testWithDataStore() {
        DataStore ds = new DataStore();
        Assignment a0 = ds.assignments().get(0);
        String text = a0.text;
        int[] sa = M2SuffixArray.buildSuffixArray(text);
        String pattern = text.substring(20, Math.min(60, text.length()));
        List<Integer> occ = M2SuffixArray.findOccurrences(text, sa, pattern);
        check("DataStore assignment search finds expected positions",
                occ.equals(naiveFindAll(text, pattern)) && occ.contains(20));

        M2SuffixAutomaton automaton = M2SuffixAutomaton.build(text);
        check("DataStore automaton count = naive on " + a0.id,
                automaton.countOccurrences(pattern) == occ.size());

        try {
            String wiki = ds.loadWikipediaText();
            wiki = wiki.substring(0, Math.min(50_000, wiki.length()));
            int[] saSais = M2SAIS.buildSuffixArray(wiki);
            int[] saDbl = M2SuffixArray.buildSuffixArray(wiki);
            check("Wikipedia 50k: SA-IS = doubling", Arrays.equals(saSais, saDbl));
            check("Wikipedia 50k: Kasai = naive LCP",
                    Arrays.equals(M2KasaiLCP.buildLCP(wiki, saDbl), naiveLcpArray(wiki, saDbl)));
            String pat = wiki.substring(10_000, 10_030);
            check("Wikipedia 50k: occurrences = naive",
                    M2SuffixArray.findOccurrences(wiki, saDbl, pat).equals(naiveFindAll(wiki, pat)));
        } catch (IOException e) {
            System.out.println("SKIP Wikipedia checks (file not loaded: " + e.getMessage() + ")");
        }
    }

    private static String randomString(Random rnd, String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String abbrev(String s) {
        if (s.length() <= 20) {
            return s;
        }
        return s.substring(0, 20) + "...";
    }

    private static int[] naiveSuffixArray(String text) {
        int n = text.length();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> compareSuffixes(text, a, b));
        int[] sa = new int[n];
        for (int i = 0; i < n; i++) {
            sa[i] = order[i];
        }
        return sa;
    }

    private static int compareSuffixes(String text, int a, int b) {
        int n = text.length();
        while (a < n && b < n) {
            char ca = text.charAt(a);
            char cb = text.charAt(b);
            if (ca != cb) {
                return ca < cb ? -1 : 1;
            }
            a++;
            b++;
        }
        return Integer.compare(n - a, n - b);
    }

    private static int[] naiveLcpArray(String text, int[] sa) {
        int n = text.length();
        if (n < 2) {
            return new int[0];
        }
        int[] lcp = new int[n - 1];
        for (int i = 0; i < n - 1; i++) {
            int a = sa[i];
            int b = sa[i + 1];
            int h = 0;
            while (a + h < n && b + h < n && text.charAt(a + h) == text.charAt(b + h)) {
                h++;
            }
            lcp[i] = h;
        }
        return lcp;
    }

    private static long naiveDistinctSubstrings(String text) {
        HashSet<String> set = new HashSet<>();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j <= n; j++) {
                set.add(text.substring(i, j));
            }
        }
        return set.size();
    }

    private static int naiveLcsLength(String a, String b) {
        int[] dp = new int[b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = b.length(); j >= 1; j--) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[j] = dp[j - 1] + 1;
                    if (dp[j] > best) {
                        best = dp[j];
                    }
                } else {
                    dp[j] = 0;
                }
            }
        }
        return best;
    }

    private static int naiveLongestRepeatedLength(String text) {
        int n = text.length();
        int best = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int h = 0;
                while (j + h < n && text.charAt(i + h) == text.charAt(j + h)) {
                    h++;
                }
                if (h > best) {
                    best = h;
                }
            }
        }
        return best;
    }
}
