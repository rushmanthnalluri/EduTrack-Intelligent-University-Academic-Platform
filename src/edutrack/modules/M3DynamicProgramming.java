package edutrack.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import edutrack.data.DataStore;
import edutrack.model.ActivityEvent;
import edutrack.model.Course;
import edutrack.model.Student;

public class M3DynamicProgramming {

    private static final Set<String> COURSE_ACTIONS = Set.of(
            "VIEW_COURSE", "SUBMIT_ASSIGNMENT", "DOWNLOAD_RESOURCE", "ATTEND_LECTURE", "TAKE_QUIZ");

    private static final int TOP_MATCHES = 5;
    private static final int OBST_KEY_COUNT = 12;
    private static final int MCM_MATRIX_COUNT = 8;
    /** Practical cap: two primitive arrays at 2^22 entries stay within a manageable heap budget. */
    private static final int MAX_BITMASK_ITEMS = 22;

    public static void run(Scanner sc, DataStore ds) {
        int choice;

        do {
            System.out.println("\n--- Advanced Dynamic Programming (M3) ---");
            System.out.println("1. Levenshtein Fuzzy Query Correction");
            System.out.println("2. Damerau-Levenshtein vs Levenshtein Comparison");
            System.out.println("3. Matrix-Chain Multiplication (Data Pipeline)");
            System.out.println("4. Bitmask DP Course Combination Explorer");
            System.out.println("5. Optimal BST (Course Access Optimizer)");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    runLevenshtein(sc, ds);
                    break;
                case 2:
                    runDamerau(sc, ds);
                    break;
                case 3:
                    runMatrixChain(ds);
                    break;
                case 4:
                    runBitmaskDP(sc, ds);
                    break;
                case 5:
                    runOptimalBST(ds);
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
    // Option 1: Levenshtein fuzzy query correction
    // ------------------------------------------------------------------

    private static void runLevenshtein(Scanner sc, DataStore ds) {
        System.out.println("\n--- Levenshtein Fuzzy Query Correction ---");
        String query = readLine(sc, "Enter a course or student name (typos allowed) : ");
        if (query.isEmpty()) {
            System.out.println("\nQuery cannot be empty.");
            return;
        }

        List<Candidate> candidates = buildCandidates(ds);
        String q = query.toLowerCase();

        long start = System.nanoTime();
        for (Candidate c : candidates) {
            c.lev = levenshtein(q, c.name.toLowerCase());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        candidates.sort(Comparator.comparingInt((Candidate c) -> c.lev).thenComparing(c -> c.name));

        System.out.println("\n--- Levenshtein Results ---");
        System.out.println("Query             : " + query);
        System.out.println("Candidates scored : " + candidates.size() + " (course + student names, case-insensitive)");
        System.out.println("Time taken        : " + elapsedMs + " ms");
        System.out.println("Top " + TOP_MATCHES + " closest matches:");
        int limit = Math.min(TOP_MATCHES, candidates.size());
        for (int i = 0; i < limit; i++) {
            Candidate c = candidates.get(i);
            System.out.println((i + 1) + ". [dist=" + c.lev + "] " + c.kind + ": " + c.name + " (" + c.info + ")");
        }
    }

    // ------------------------------------------------------------------
    // Option 2: Damerau-Levenshtein (OSA) vs Levenshtein
    // ------------------------------------------------------------------

    private static void runDamerau(Scanner sc, DataStore ds) {
        System.out.println("\n--- Damerau-Levenshtein vs Levenshtein ---");
        String query = readLine(sc, "Enter a course or student name (typos allowed) : ");
        if (query.isEmpty()) {
            System.out.println("\nQuery cannot be empty.");
            return;
        }

        long start = System.nanoTime();
        List<Candidate> candidates = fuzzyRank(ds, query);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\n--- Comparison Results ---");
        System.out.println("Query             : " + query);
        System.out.println("Candidates scored : " + candidates.size());
        System.out.println("Time taken        : " + elapsedMs + " ms");
        System.out.println("Top " + TOP_MATCHES + " closest matches (ranked by Damerau distance):");
        System.out.println("Rank  Lev  Dam   Name");
        int limit = Math.min(TOP_MATCHES, candidates.size());
        for (int i = 0; i < limit; i++) {
            Candidate c = candidates.get(i);
            String marker = c.dam < c.lev ? "   * differs (transposition?)" : "";
            System.out.printf("%-6d %-4d %-5d %s: %s (%s)%s%n",
                    i + 1, c.lev, c.dam, c.kind, c.name, c.info, marker);
        }

        int differCount = 0;
        for (Candidate c : candidates) {
            if (c.dam != c.lev) {
                differCount++;
            }
        }
        System.out.println("\nDistances differ for " + differCount + " of " + candidates.size() + " candidates.");

        if (differCount > 0) {
            List<Candidate> gaps = new ArrayList<>(candidates);
            gaps.sort(Comparator.comparingInt((Candidate c) -> c.lev - c.dam).reversed()
                    .thenComparingInt(c -> c.lev).thenComparing(c -> c.name));
            System.out.println("Largest gaps (Levenshtein - Damerau):");
            int shown = 0;
            for (Candidate c : gaps) {
                if (c.dam >= c.lev || shown >= TOP_MATCHES) {
                    break;
                }
                System.out.println("  " + c.kind + ": " + c.name + "  Lev=" + c.lev + " Dam=" + c.dam
                        + "  (transpositions are cheaper here)");
                shown++;
            }
        }
    }

    // ------------------------------------------------------------------
    // Option 3: Matrix-Chain Multiplication
    // ------------------------------------------------------------------

    private static void runMatrixChain(DataStore ds) {
        System.out.println("\n--- Matrix-Chain Multiplication (Data Pipeline) ---");
        Map<String, Integer> enrollment = countEnrollment(ds);
        List<Course> courses = ds.courses();
        int[] dims = buildPipelineDims(enrollment, courses);
        int n = dims.length - 1;

        System.out.println("Pipeline of " + n + " academic-data transformations.");
        System.out.println("Each stage dimension = course credits x 10 + current enrollment.");
        System.out.println("\nStage  Course  Credits  Enrollment  Dimension");
        for (int i = 0; i < dims.length; i++) {
            Course c = courses.get(i);
            int enrolled = enrollment.getOrDefault(c.code, 0);
            System.out.printf("%-6d %-7s %-8d %-11d %d%n", i, c.code, c.credits, enrolled, dims[i]);
        }

        System.out.println("\nMatrix chain (A_i has dims[i] x dims[i+1]):");
        for (int i = 0; i < n; i++) {
            System.out.println("A" + i + " : " + dims[i] + " x " + dims[i + 1]);
        }

        int[][] split = new int[n][n];
        long start = System.nanoTime();
        long minCost = matrixChainOrder(dims, split);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\nMinimum scalar multiplications : " + minCost);
        System.out.println("Optimal parenthesization       : " + buildParenthesization(split, 0, n - 1));
        System.out.println("Time taken                     : " + elapsedMs + " ms");
    }

    public static long matrixChainOrder(int[] dims, int[][] split) {
        int n = dims.length - 1;
        if (n <= 0) {
            return 0;
        }
        long[][] m = new long[n][n];
        for (int len = 2; len <= n; len++) {
            for (int i = 0; i + len - 1 < n; i++) {
                int j = i + len - 1;
                m[i][j] = Long.MAX_VALUE;
                for (int k = i; k < j; k++) {
                    long q = m[i][k] + m[k + 1][j] + (long) dims[i] * dims[k + 1] * dims[j + 1];
                    if (q < m[i][j]) {
                        m[i][j] = q;
                        split[i][j] = k;
                    }
                }
            }
        }
        return m[0][n - 1];
    }

    public static String buildParenthesization(int[][] split, int i, int j) {
        if (i == j) {
            return "A" + i;
        }
        int k = split[i][j];
        return "(" + buildParenthesization(split, i, k) + " " + buildParenthesization(split, k + 1, j) + ")";
    }

    // ------------------------------------------------------------------
    // Option 4: Bitmask DP course combination explorer
    // ------------------------------------------------------------------

    private static void runBitmaskDP(Scanner sc, DataStore ds) {
        System.out.println("\n--- Bitmask DP Course Combination Explorer ---");
        System.out.print("Enter maximum credit budget : ");
        int budget = readInt(sc);
        System.out.print("Enter semester (1-8) : ");
        int semester = readInt(sc);

        if (budget < 0) {
            System.out.println("\nBudget cannot be negative.");
            return;
        }

        List<Course> pool = new ArrayList<>();
        for (Course c : ds.courses()) {
            if (c.semester == semester) {
                pool.add(c);
            }
        }
        if (pool.isEmpty()) {
            System.out.println("\nNo courses found for semester " + semester + ".");
            return;
        }

        Map<String, Integer> popularity = countCourseActivity(ds);
        int n = pool.size();
        int[] credits = new int[n];
        long[] values = new long[n];

        System.out.println("\nCourse pool for semester " + semester + " (" + n + " courses):");
        System.out.println("Idx  Code    Credits  Popularity  Name");
        for (int i = 0; i < n; i++) {
            Course c = pool.get(i);
            credits[i] = c.credits;
            values[i] = popularity.getOrDefault(c.code, 0);
            System.out.printf("%-4d %-7s %-8d %-11d %s%n", i, c.code, credits[i], values[i], c.name);
        }
        System.out.println("(Popularity = course-related events for that code in the activity stream)");

        long start = System.nanoTime();
        long[] result = bitmaskBestSubset(credits, values, budget);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        long bestValue = result[0];
        int bestMask = (int) result[1];

        System.out.println("\nSubsets examined (bitmask DP) : " + (1 << n));
        System.out.println("Time taken                    : " + elapsedMs + " ms");

        if (bestMask == 0) {
            if (anyFits(credits, budget)) {
                System.out.println("Optimal selection is empty: every course within budget has value 0.");
            } else {
                System.out.println("No course fits within a budget of " + budget + " credits.");
            }
            return;
        }

        int totalCredits = 0;
        System.out.println("Optimal selection:");
        for (int i = 0; i < n; i++) {
            if ((bestMask & (1 << i)) != 0) {
                System.out.println("  - " + pool.get(i).code + " " + pool.get(i).name
                        + " (" + credits[i] + " credits, value " + values[i] + ")");
                totalCredits += credits[i];
            }
        }
        System.out.println("Total credits : " + totalCredits + " / " + budget);
        System.out.println("Total value   : " + bestValue + " (course-related activity events)");
    }

    private static boolean anyFits(int[] credits, int budget) {
        for (int c : credits) {
            if (c <= budget) {
                return true;
            }
        }
        return false;
    }

    public static long[] bitmaskBestSubset(int[] credits, long[] values, int budget) {
        if (credits == null || values == null || credits.length != values.length) {
            throw new IllegalArgumentException("credits and values must be non-null and have equal length");
        }
        if (budget < 0) {
            throw new IllegalArgumentException("budget must be non-negative");
        }
        int n = credits.length;
        if (n > MAX_BITMASK_ITEMS) {
            throw new IllegalArgumentException(
                    "Bitmask enumeration supports at most " + MAX_BITMASK_ITEMS + " items, got " + n);
        }
        int size = 1 << n;
        int[] creditSum = new int[size];
        long[] valueSum = new long[size];

        for (int mask = 1; mask < size; mask++) {
            int bit = Integer.numberOfTrailingZeros(mask);
            int prev = mask & (mask - 1);
            creditSum[mask] = creditSum[prev] + credits[bit];
            valueSum[mask] = valueSum[prev] + values[bit];
        }

        long bestValue = -1;
        int bestMask = 0;
        for (int mask = 0; mask < size; mask++) {
            if (creditSum[mask] <= budget && valueSum[mask] > bestValue) {
                bestValue = valueSum[mask];
                bestMask = mask;
            }
        }
        return new long[] { bestValue, bestMask };
    }

    static long bruteForceBestValue(int[] credits, long[] values, int budget) {
        if (credits == null || values == null || credits.length != values.length) {
            throw new IllegalArgumentException("credits and values must be non-null and have equal length");
        }
        if (budget < 0) {
            throw new IllegalArgumentException("budget must be non-negative");
        }
        int n = credits.length;
        if (n > MAX_BITMASK_ITEMS) {
            throw new IllegalArgumentException(
                    "Brute force supports at most " + MAX_BITMASK_ITEMS + " items, got " + n);
        }
        long best = 0;
        for (int mask = 0; mask < (1 << n); mask++) {
            int c = 0;
            long v = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    c += credits[i];
                    v += values[i];
                }
            }
            if (c <= budget && v > best) {
                best = v;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Option 5: Optimal BST on course access frequencies
    // ------------------------------------------------------------------

    private static void runOptimalBST(DataStore ds) {
        System.out.println("\n--- Optimal BST (Course Access Optimizer) ---");
        Map<String, Integer> freq = countCourseActivity(ds);

        String[] keys = selectTopCodes(freq, OBST_KEY_COUNT);
        int n = keys.length;
        int[] f = new int[n];
        for (int i = 0; i < n; i++) {
            f[i] = freq.get(keys[i]);
        }

        System.out.println("Top " + n + " most-accessed course codes (sorted keys, frequencies):");
        System.out.println("Key     Frequency");
        for (int i = 0; i < n; i++) {
            System.out.printf("%-7s %d%n", keys[i], f[i]);
        }

        int[][] root = new int[n][n];
        long start = System.nanoTime();
        long optCost = optimalBstCost(f, root);
        long obstMs = (System.nanoTime() - start) / 1_000_000;

        long balCost = balancedBstCost(f, 0, n - 1, 1);

        System.out.println("\nMinimum expected search cost (OBST) : " + optCost);
        System.out.println("Time taken (OBST DP)                : " + obstMs + " ms");

        System.out.println("\nOBST preorder (code, freq, depth):");
        printObstPreorder(keys, f, root, 0, n - 1, 1);

        System.out.println("\nBalanced BST expected cost (same frequencies) : " + balCost);
        double savings = balCost == 0 ? 0.0 : 100.0 * (balCost - optCost) / balCost;
        System.out.printf("OBST improvement over balanced BST  : %d (%.1f%% lower)%n", balCost - optCost, savings);
    }

    public static long optimalBstCost(int[] freq, int[][] root) {
        int n = freq.length;
        if (n == 0) {
            return 0;
        }
        long[][] cost = new long[n][n];
        for (int i = 0; i < n; i++) {
            cost[i][i] = freq[i];
            root[i][i] = i;
        }
        for (int len = 2; len <= n; len++) {
            for (int i = 0; i + len - 1 < n; i++) {
                int j = i + len - 1;
                long sum = 0;
                for (int k = i; k <= j; k++) {
                    sum += freq[k];
                }
                cost[i][j] = Long.MAX_VALUE;
                for (int r = i; r <= j; r++) {
                    long left = r > i ? cost[i][r - 1] : 0;
                    long right = r < j ? cost[r + 1][j] : 0;
                    long c = left + right + sum;
                    if (c < cost[i][j]) {
                        cost[i][j] = c;
                        root[i][j] = r;
                    }
                }
            }
        }
        return cost[0][n - 1];
    }

    private static void printObstPreorder(String[] keys, int[] freq, int[][] root, int i, int j, int depth) {
        if (i > j) {
            return;
        }
        int r = root[i][j];
        System.out.println("  ".repeat(depth) + keys[r] + " (freq=" + freq[r] + ", depth=" + depth + ")");
        printObstPreorder(keys, freq, root, i, r - 1, depth + 1);
        printObstPreorder(keys, freq, root, r + 1, j, depth + 1);
    }

    public static long balancedBstCost(int[] freq, int lo, int hi, int depth) {
        if (lo > hi) {
            return 0;
        }
        int mid = (lo + hi) / 2;
        return (long) freq[mid] * depth
                + balancedBstCost(freq, lo, mid - 1, depth + 1)
                + balancedBstCost(freq, mid + 1, hi, depth + 1);
    }

    // ------------------------------------------------------------------
    // Shared DP algorithms and data helpers
    // ------------------------------------------------------------------

    public static int levenshtein(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[m][n];
    }

    public static int damerauOSA(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[m][n];
    }

    public static final class Candidate {
        public final String kind;
        public final String name;
        public final String info;
        public int lev;
        public int dam;

        public Candidate(String kind, String name, String info) {
            this.kind = kind;
            this.name = name;
            this.info = info;
        }
    }

    public static List<Candidate> buildCandidates(DataStore ds) {
        List<Candidate> list = new ArrayList<>();
        for (Course c : ds.courses()) {
            list.add(new Candidate("Course", c.name, c.code));
        }
        for (Student s : ds.students()) {
            list.add(new Candidate("Student", s.name, "id=" + s.id + ", " + s.program));
        }
        return list;
    }

    public static List<Candidate> fuzzyRank(DataStore ds, String query) {
        List<Candidate> candidates = buildCandidates(ds);
        String q = query.toLowerCase();
        for (Candidate c : candidates) {
            String name = c.name.toLowerCase();
            c.lev = levenshtein(q, name);
            c.dam = damerauOSA(q, name);
        }
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.dam)
                .thenComparingInt(c -> c.lev).thenComparing(c -> c.name));
        return candidates;
    }

    public static int[] buildPipelineDims(Map<String, Integer> enrollment, List<Course> courses) {
        int stages = Math.min(MCM_MATRIX_COUNT + 1, courses.size());
        int[] dims = new int[stages];
        for (int i = 0; i < stages; i++) {
            Course c = courses.get(i);
            dims[i] = c.credits * 10 + enrollment.getOrDefault(c.code, 0);
        }
        return dims;
    }

    public static Map<String, Integer> countEnrollment(DataStore ds) {
        Map<String, Integer> counts = new HashMap<>();
        for (Student s : ds.students()) {
            for (String code : s.enrolledCourses) {
                counts.merge(code, 1, Integer::sum);
            }
        }
        return counts;
    }

    public static Map<String, Integer> countCourseActivity(DataStore ds) {
        Map<String, Integer> counts = new HashMap<>();
        for (ActivityEvent e : ds.activityStream()) {
            if (COURSE_ACTIONS.contains(e.action) && !"-".equals(e.details)) {
                counts.merge(e.details, 1, Integer::sum);
            }
        }
        return counts;
    }

    public static String[] selectTopCodes(Map<String, Integer> freq, int limit) {
        List<String> codes = new ArrayList<>(freq.keySet());
        codes.sort(Comparator.comparingInt((String c) -> freq.get(c)).reversed()
                .thenComparing(Comparator.naturalOrder()));
        int n = Math.min(limit, codes.size());
        String[] keys = new String[n];
        for (int i = 0; i < n; i++) {
            keys[i] = codes.get(i);
        }
        Arrays.sort(keys);
        return keys;
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

    // ------------------------------------------------------------------
    // Non-interactive self-test
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("--- M3 Dynamic Programming Self-Test ---");
        int failures = 0;

        // Levenshtein on known pairs
        failures += expectEq("Levenshtein('kitten','sitting') = 3", 3, levenshtein("kitten", "sitting"));
        failures += expectEq("Levenshtein('ca','ac') = 2", 2, levenshtein("ca", "ac"));

        // Damerau-Levenshtein (OSA) on known pairs
        failures += expectEq("Damerau-OSA('ca','ac') = 1", 1, damerauOSA("ca", "ac"));
        failures += expectEq("Damerau-OSA('kitten','sitting') = 3", 3, damerauOSA("kitten", "sitting"));
        failures += expectEq("Damerau-OSA('abc','abc') = 0", 0, damerauOSA("abc", "abc"));

        // Edit-distance edge cases: empty strings
        failures += expectEq("Levenshtein('','abc') = 3", 3, levenshtein("", "abc"));
        failures += expectEq("Damerau-OSA('ab','') = 2", 2, damerauOSA("ab", ""));

        // Matrix-chain multiplication on the classic textbook example
        int[] dims = { 10, 30, 5, 60 };
        int[][] split = new int[3][3];
        long mcmCost = matrixChainOrder(dims, split);
        failures += expectEq("MCM cost dims {10,30,5,60} = 4500", 4500L, mcmCost);
        failures += expectStr("MCM parenthesization = ((A0 A1) A2)",
                "((A0 A1) A2)", buildParenthesization(split, 0, 2));

        // Matrix-chain edge cases: single matrix and degenerate dimension list
        int[][] split1 = new int[1][1];
        failures += expectEq("MCM single matrix dims {7,11} = 0", 0L,
                matrixChainOrder(new int[] { 7, 11 }, split1));
        failures += expectStr("MCM single matrix parenthesization = A0",
                "A0", buildParenthesization(split1, 0, 0));
        failures += expectEq("MCM degenerate dims {7} = 0", 0L,
                matrixChainOrder(new int[] { 7 }, new int[1][1]));

        // Bitmask DP vs brute force on random small sets
        Random rnd = new Random(7);
        int trials = 200;
        boolean bitmaskOk = true;
        for (int t = 0; t < trials && bitmaskOk; t++) {
            int n = 1 + rnd.nextInt(8);
            int[] credits = new int[n];
            long[] values = new long[n];
            for (int i = 0; i < n; i++) {
                credits[i] = 1 + rnd.nextInt(6);
                values[i] = rnd.nextInt(51);
            }
            int budget = rnd.nextInt(16);
            long dp = bitmaskBestSubset(credits, values, budget)[0];
            long bf = bruteForceBestValue(credits, values, budget);
            if (dp != bf) {
                bitmaskOk = false;
            }
        }
        failures += report("Bitmask DP matches brute force (" + trials + " random sets)", bitmaskOk);

        // Bitmask DP edge cases
        failures += expectEq("Bitmask empty pool, budget 5 = 0", 0L,
                bitmaskBestSubset(new int[0], new long[0], 5)[0]);
        failures += expectEq("Bitmask budget 2 < any credit = 0", 0L,
                bitmaskBestSubset(new int[] { 3, 4 }, new long[] { 9, 9 }, 2)[0]);
        failures += expectEq("Bitmask single fitting course = its value", 12L,
                bitmaskBestSubset(new int[] { 3 }, new long[] { 12 }, 3)[0]);
        failures += expectEq("Bitmask budget 0 = 0", 0L,
                bitmaskBestSubset(new int[] { 2, 3 }, new long[] { 7, 8 }, 0)[0]);

        // Optimal BST on a known textbook example (keys 10,12,20 with frequencies 34,8,50)
        int[] obstFreq = { 34, 8, 50 };
        int[][] obstRoot = new int[3][3];
        failures += expectEq("OBST cost freqs {34,8,50} = 142", 142L, optimalBstCost(obstFreq, obstRoot));
        failures += expectEq("Balanced BST cost freqs {34,8,50} = 176", 176L,
                balancedBstCost(obstFreq, 0, 2, 1));

        // Optimal BST edge cases
        failures += expectEq("OBST single key freq {42} = 42", 42L,
                optimalBstCost(new int[] { 42 }, new int[1][1]));
        failures += expectEq("OBST all-zero freqs {0,0,0} = 0", 0L,
                optimalBstCost(new int[] { 0, 0, 0 }, new int[3][3]));
        failures += expectEq("OBST empty freq array = 0", 0L,
                optimalBstCost(new int[0], new int[0][0]));
        failures += expectEq("Balanced BST empty range = 0", 0L,
                balancedBstCost(new int[0], 0, -1, 1));

        // DataStore integration checks
        DataStore ds = new DataStore();

        Map<String, Integer> enroll = countEnrollment(ds);
        long enrollSum = 0;
        for (int v : enroll.values()) {
            enrollSum += v;
        }
        long expectedEnroll = 0;
        for (Student s : ds.students()) {
            expectedEnroll += s.enrolledCourses.size();
        }
        failures += expectEq("Enrollment counts match student records", expectedEnroll, enrollSum);

        Map<String, Integer> activity = countCourseActivity(ds);
        long actSum = 0;
        for (int v : activity.values()) {
            actSum += v;
        }
        long expectedAct = 0;
        for (ActivityEvent e : ds.activityStream()) {
            if (COURSE_ACTIONS.contains(e.action)) {
                expectedAct++;
            }
        }
        failures += expectEq("Activity frequency counts match stream", expectedAct, actSum);

        // End-to-end: a typo query resolves to the right course name
        List<Candidate> candidates = buildCandidates(ds);
        for (Candidate c : candidates) {
            c.lev = levenshtein("data structurs", c.name.toLowerCase());
        }
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.lev).thenComparing(c -> c.name));
        Candidate best = candidates.get(0);
        boolean typoOk = best.kind.equals("Course") && best.name.equals("Data Structures") && best.lev == 1;
        failures += report("Typo query 'data structurs' -> 'Data Structures' (dist 1)", typoOk);

        // OBST must be at least as good as a balanced BST on real frequencies
        String[] keys = selectTopCodes(activity, OBST_KEY_COUNT);
        int[] f = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            f[i] = activity.get(keys[i]);
        }
        long realObst = optimalBstCost(f, new int[keys.length][keys.length]);
        long realBal = balancedBstCost(f, 0, keys.length - 1, 1);
        failures += report("OBST cost (" + realObst + ") <= balanced BST cost (" + realBal
                + ") on real frequencies", realObst <= realBal);

        // Pipeline dimensions must stay positive and consistent with the course catalog
        int[] pipelineDims = buildPipelineDims(enroll, ds.courses());
        boolean dimsOk = pipelineDims.length == Math.min(MCM_MATRIX_COUNT + 1, ds.courses().size());
        for (int d : pipelineDims) {
            dimsOk = dimsOk && d > 0;
        }
        failures += report("Pipeline dims derived from enrollment are positive (" + pipelineDims.length
                + " stages)", dimsOk);

        System.out.println();
        if (failures > 0) {
            System.out.println("SELF-TEST FAILED: " + failures + " check(s) failed.");
            System.exit(1);
        }
        System.out.println("All self-tests passed.");
    }

    private static int expectEq(String label, long expected, long actual) {
        if (expected == actual) {
            System.out.println("PASS  " + label);
            return 0;
        }
        System.out.println("FAIL  " + label + " (expected " + expected + ", got " + actual + ")");
        return 1;
    }

    private static int expectStr(String label, String expected, String actual) {
        if (expected.equals(actual)) {
            System.out.println("PASS  " + label);
            return 0;
        }
        System.out.println("FAIL  " + label + " (expected \"" + expected + "\", got \"" + actual + "\")");
        return 1;
    }

    private static int report(String label, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + label);
        return ok ? 0 : 1;
    }
}
