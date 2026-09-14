package edutrack.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import edutrack.data.DataStore;
import edutrack.model.ActivityEvent;
import edutrack.model.Student;
import edutrack.modules.M6ReservoirSampler.SampleResult;

public class M6RandomizedParallel {

    public static final long DEMO_SEED = 42L;
    private static final int BENCHMARK_SIZE = 1_000_000;
    private static final int SAMPLE_PRINT_LIMIT = 30;

    public static final List<String> KNOWN_ACTIONS = List.of(
            "LOGIN", "VIEW_COURSE", "SUBMIT_ASSIGNMENT", "DOWNLOAD_RESOURCE",
            "ATTEND_LECTURE", "TAKE_QUIZ", "LOGOUT");

    public static void run(Scanner sc, DataStore ds) {
        int choice;

        do {
            System.out.println("\n--- Randomized & Parallel Algorithms ---");
            System.out.println("1. Randomized QuickSort (CGPA ranking + 1M benchmark)");
            System.out.println("2. Parallel Merge Sort (Fork/Join vs sequential)");
            System.out.println("3. Reservoir Sampling (activity stream, Algorithm R)");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    runRandomizedQuickSort(ds);
                    break;
                case 2:
                    runParallelMergeSort(ds);
                    break;
                case 3:
                    runReservoirSampling(sc, ds);
                    break;
                case 0:
                    System.out.println("Returning to main menu...");
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    private static void runRandomizedQuickSort(DataStore ds) {
        System.out.println("\n--- Randomized QuickSort: Student CGPA Ranking ---");
        List<Student> students = ds.students();
        Student[] ranked = students.toArray(new Student[0]);

        long start = System.nanoTime();
        long comparisons = M6RandomizedQuickSort.sortStudents(ranked, new Random(DEMO_SEED));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Students ranked : " + ranked.length + " (CGPA descending, name ascending on ties)");
        System.out.println("Pivot selection : uniformly random (Random seed = " + DEMO_SEED + ")");
        System.out.println("Comparisons     : " + comparisons);
        System.out.println("Time taken      : " + elapsedMs + " ms");

        System.out.println("\nTop 10 by CGPA:");
        printStudentRange(ranked, 0, Math.min(10, ranked.length), 1);
        System.out.println("\nBottom 10 by CGPA:");
        int bottomFrom = Math.max(0, ranked.length - 10);
        printStudentRange(ranked, bottomFrom, ranked.length, bottomFrom + 1);

        runQuickSortBenchmark(ds);
    }

    private static void printStudentRange(Student[] ranked, int from, int to, int startRank) {
        for (int i = from; i < to; i++) {
            System.out.printf(" %3d. %-25s CGPA %.2f%n", startRank + (i - from), ranked[i].name, ranked[i].cgpa);
        }
    }

    private static void runQuickSortBenchmark(DataStore ds) {
        System.out.println("\n--- QuickSort Benchmark: " + String.format("%,d", BENCHMARK_SIZE) + " elements ---");
        System.out.println("Input derived from student records (CGPA values repeated + tiny perturbation)");
        double[] base = buildBenchmarkArray(ds, BENCHMARK_SIZE, DEMO_SEED);

        System.out.println("Warming up JIT on a 200,000-element array...");
        double[] warm = buildBenchmarkArray(ds, 200_000, DEMO_SEED + 7);
        M6RandomizedQuickSort.sort(warm.clone(), new Random(DEMO_SEED));
        M6RandomizedQuickSort.sortDeterministic(warm.clone());
        Arrays.sort(warm.clone());

        System.out.println("\n(a) Random input:");

        double[] a = base.clone();
        long start = System.nanoTime();
        long cmpRandomized = M6RandomizedQuickSort.sort(a, new Random(DEMO_SEED));
        long randomizedMs = (System.nanoTime() - start) / 1_000_000;

        double[] b = base.clone();
        start = System.nanoTime();
        Arrays.sort(b);
        long arraysMs = (System.nanoTime() - start) / 1_000_000;

        double[] c = base.clone();
        start = System.nanoTime();
        long cmpDeterministic = M6RandomizedQuickSort.sortDeterministic(c);
        long deterministicMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("  Randomized QuickSort    : " + randomizedMs + " ms (comparisons: "
                + String.format("%,d", cmpRandomized) + ", sorted: " + isSorted(a) + ")");
        System.out.println("  Arrays.sort             : " + arraysMs + " ms (sorted: " + isSorted(b) + ")");
        System.out.println("  Deterministic QuickSort : " + deterministicMs + " ms (comparisons: "
                + String.format("%,d", cmpDeterministic) + ", sorted: " + isSorted(c) + ")");

        System.out.println("\n(b) Already-sorted input:");
        double[] sortedBase = base.clone();
        Arrays.sort(sortedBase);

        double[] d = sortedBase.clone();
        start = System.nanoTime();
        M6RandomizedQuickSort.sort(d, new Random(DEMO_SEED));
        long sortedRandomizedMs = (System.nanoTime() - start) / 1_000_000;

        double[] e = sortedBase.clone();
        start = System.nanoTime();
        Arrays.sort(e);
        long sortedArraysMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("  Randomized QuickSort    : " + sortedRandomizedMs + " ms (sorted: " + isSorted(d) + ")");
        System.out.println("  Arrays.sort             : " + sortedArraysMs + " ms (sorted: " + isSorted(e) + ")");
        System.out.println("  Deterministic QuickSort : skipped at n = " + String.format("%,d", BENCHMARK_SIZE));
        System.out.println("    (first-element pivot on sorted input degrades to O(n^2); a recursive");
        System.out.println("     version would also overflow the call stack, so this implementation");
        System.out.println("     uses an explicit stack and is timed on smaller n to expose the");
        System.out.println("     quadratic growth):");

        int[] sizes = { 10_000, 20_000, 40_000 };
        long prevMs = -1;
        long prevCmp = -1;
        long lastMs = 0;
        int lastN = 0;
        for (int n : sizes) {
            double[] f = Arrays.copyOf(sortedBase, n);
            start = System.nanoTime();
            long cmp = M6RandomizedQuickSort.sortDeterministic(f);
            long ms = (System.nanoTime() - start) / 1_000_000;
            String growth = "";
            if (prevMs >= 0) {
                growth = String.format("  (x%.1f time, x%.1f comparisons vs n=%,d)",
                        ms / (double) Math.max(1, prevMs), cmp / (double) prevCmp, lastN);
            }
            System.out.println("    n = " + String.format("%,7d", n) + " : " + ms + " ms, comparisons: "
                    + String.format("%,d", cmp) + growth);
            prevMs = ms;
            prevCmp = cmp;
            lastMs = ms;
            lastN = n;
        }
        double scale = BENCHMARK_SIZE / (double) lastN;
        System.out.println(String.format(
                "    -> quadratic growth: estimated deterministic time at n=%,d is ~%.1f s (~x%.0f vs n=%,d)",
                BENCHMARK_SIZE, lastMs * scale * scale / 1000.0, scale * scale, lastN));
    }

    private static void runParallelMergeSort(DataStore ds) {
        System.out.println("\n--- Parallel Merge Sort (Fork/Join) vs Sequential Merge Sort ---");
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("Processors available  : " + processors);
        System.out.println("Sequential threshold  : " + String.format("%,d", M6ParallelMergeSort.SEQUENTIAL_THRESHOLD)
                + " elements");
        System.out.println("Array size            : " + String.format("%,d", BENCHMARK_SIZE)
                + " (CGPA values repeated + tiny perturbation)");

        double[] base = buildBenchmarkArray(ds, BENCHMARK_SIZE, DEMO_SEED + 1);

        System.out.println("Warming up JIT on a 200,000-element array...");
        double[] warm = buildBenchmarkArray(ds, 200_000, DEMO_SEED + 8);
        M6ParallelMergeSort.sequentialSort(warm.clone());
        M6ParallelMergeSort.parallelSort(warm.clone(), processors);

        double[] a = base.clone();
        long start = System.nanoTime();
        M6ParallelMergeSort.sequentialSort(a);
        long sequentialMs = (System.nanoTime() - start) / 1_000_000;

        double[] b = base.clone();
        start = System.nanoTime();
        M6ParallelMergeSort.parallelSort(b, processors);
        long parallelMs = (System.nanoTime() - start) / 1_000_000;

        boolean identical = Arrays.equals(a, b);
        System.out.println("\nSequential Merge Sort : " + sequentialMs + " ms (sorted: " + isSorted(a) + ")");
        System.out.println("Parallel Merge Sort   : " + parallelMs + " ms (sorted: " + isSorted(b)
                + ", identical result: " + identical + ")");
        System.out.println("Processors used       : " + processors);
        System.out.println(String.format("Speedup               : %.2fx", sequentialMs / (double) Math.max(1, parallelMs)));
    }

    private static void runReservoirSampling(Scanner sc, DataStore ds) {
        System.out.println("\n--- Reservoir Sampling: Activity Stream (Algorithm R) ---");
        List<ActivityEvent> stream = ds.activityStream();
        System.out.println("Stream size : " + String.format("%,d", stream.size())
                + " events (sampled in ONE pass, O(k) extra memory)");

        int k = readIntWithDefault(sc, "Enter reservoir size k (default 10) : ", 10);
        if (k <= 0) {
            System.out.println("k must be positive; using default 10.");
            k = 10;
        }

        long start = System.nanoTime();
        SampleResult<ActivityEvent> result = M6ReservoirSampler.sample(stream, k, new Random(DEMO_SEED));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\nReservoir size k : " + k);
        System.out.println("Events seen      : " + String.format("%,d", result.seen));
        System.out.println("Sampled events   : " + result.reservoir.size());
        System.out.println("Time taken       : " + elapsedMs + " ms");
        printSample(result.reservoir);

        System.out.print("\nFilter by action type " + KNOWN_ACTIONS + " (blank to skip) : ");
        String action = sc.nextLine().trim().toUpperCase();
        if (!action.isEmpty()) {
            if (!KNOWN_ACTIONS.contains(action)) {
                System.out.println("Unknown action '" + action + "'; skipping filtered sampling.");
            } else {
                start = System.nanoTime();
                SampleResult<ActivityEvent> filtered = M6ReservoirSampler.sample(
                        stream, e -> action.equals(e.action), k, new Random(DEMO_SEED));
                elapsedMs = (System.nanoTime() - start) / 1_000_000;
                System.out.println("\nAction filter    : " + action);
                System.out.println("Matching events  : " + String.format("%,d", filtered.seen));
                System.out.println("Sampled events   : " + filtered.reservoir.size());
                System.out.println("Time taken       : " + elapsedMs + " ms");
                printSample(filtered.reservoir);
            }
        }

        runUniformityDemo();
    }

    private static void printSample(List<ActivityEvent> sample) {
        int limit = Math.min(sample.size(), SAMPLE_PRINT_LIMIT);
        for (int i = 0; i < limit; i++) {
            System.out.println("  " + (i + 1) + ". " + sample.get(i));
        }
        if (sample.size() > limit) {
            System.out.println("  ... (" + (sample.size() - limit) + " more)");
        }
    }

    private static void runUniformityDemo() {
        System.out.println("\n--- Uniformity Demonstration: synthetic stream n=100, k=10, trials=20,000 ---");
        int n = 100;
        int k = 10;
        int trials = 20_000;

        long start = System.nanoTime();
        double[] freqs = selectionFrequencies(n, k, trials, DEMO_SEED);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double expectedFreq = k / (double) n;
        System.out.println(String.format("Expected frequency per position : %.4f (k/n = %d/%d), tolerance +/- 0.0300",
                expectedFreq, k, n));
        int[] positions = { 0, 25, 50, 75, 99 };
        for (int p : positions) {
            System.out.println(String.format("  index %3d : %,5d selections, freq = %.4f %s",
                    p, Math.round(freqs[p] * trials), freqs[p],
                    Math.abs(freqs[p] - expectedFreq) <= 0.03 ? "(within tolerance)" : "(OUT OF TOLERANCE)"));
        }
        System.out.println("Time taken : " + elapsedMs + " ms");
    }

    public static double[] selectionFrequencies(int n, int k, int trials, long seed) {
        double[] freqs = new double[Math.max(0, n)];
        if (n <= 0 || trials <= 0 || k <= 0) {
            return freqs;
        }
        List<Integer> stream = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            stream.add(i);
        }
        int[] counts = new int[n];
        Random rnd = new Random(seed);
        for (int t = 0; t < trials; t++) {
            for (int x : M6ReservoirSampler.sample(stream, k, rnd).reservoir) {
                counts[x]++;
            }
        }
        for (int i = 0; i < n; i++) {
            freqs[i] = counts[i] / (double) trials;
        }
        return freqs;
    }

    public static double[] buildBenchmarkArray(DataStore ds, int size, long seed) {
        List<Student> students = ds.students();
        Random rnd = new Random(seed);
        double[] arr = new double[size];
        for (int i = 0; i < size; i++) {
            arr[i] = students.get(i % students.size()).cgpa + (rnd.nextDouble() - 0.5) * 0.02;
        }
        return arr;
    }

    private static boolean isSorted(double[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i] < a[i - 1]) {
                return false;
            }
        }
        return true;
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
        System.out.println("--- M6 Randomized & Parallel Algorithms: Self-Test ---");
        int failures = 0;

        Random dataRnd = new Random(99L);
        double[] randomData = new double[200_000];
        for (int i = 0; i < randomData.length; i++) {
            randomData[i] = dataRnd.nextDouble() * 1000.0;
        }
        double[] reference = randomData.clone();
        Arrays.sort(reference);

        double[] a = randomData.clone();
        M6RandomizedQuickSort.sort(a, new Random(123L));
        failures += report("Randomized quicksort on seeded random array (matches Arrays.sort)",
                Arrays.equals(a, reference));

        double[] b = randomData.clone();
        M6RandomizedQuickSort.sortDeterministic(b);
        failures += report("Deterministic quicksort on seeded random array (matches Arrays.sort)",
                Arrays.equals(b, reference));

        double[] sortedInput = reference.clone();
        M6RandomizedQuickSort.sort(sortedInput, new Random(123L));
        failures += report("Randomized quicksort on already-sorted input (matches Arrays.sort)",
                Arrays.equals(sortedInput, reference));

        Random dupRnd = new Random(55L);
        double[] dupData = new double[100_000];
        for (int i = 0; i < dupData.length; i++) {
            dupData[i] = dupRnd.nextInt(4);
        }
        double[] dupReference = dupData.clone();
        Arrays.sort(dupReference);
        M6RandomizedQuickSort.sort(dupData, new Random(123L));
        failures += report("Randomized quicksort on duplicate-heavy array (matches Arrays.sort)",
                Arrays.equals(dupData, dupReference));

        double[] allEqual = new double[10_000];
        Arrays.fill(allEqual, 7.5);
        M6RandomizedQuickSort.sort(allEqual, new Random(123L));
        failures += report("Randomized quicksort on all-equal array terminates sorted",
                isSorted(allEqual));

        DataStore ds = new DataStore();
        List<Student> students = ds.students();
        Student[] ranked = students.toArray(new Student[0]);
        M6RandomizedQuickSort.sortStudents(ranked, new Random(123L));
        failures += report("Student ranking ordered by CGPA desc, name asc", isValidStudentOrder(ranked));
        failures += report("Student ranking is a permutation of the input", sameStudents(ranked, students));

        double[] c = randomData.clone();
        M6ParallelMergeSort.sequentialSort(c);
        failures += report("Sequential merge sort (matches Arrays.sort)", Arrays.equals(c, reference));

        double[] d = randomData.clone();
        M6ParallelMergeSort.parallelSort(d, Math.max(2, Runtime.getRuntime().availableProcessors()));
        failures += report("Parallel merge sort (matches Arrays.sort)", Arrays.equals(d, reference));

        List<Integer> stream = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            stream.add(i);
        }
        SampleResult<Integer> sample = M6ReservoirSampler.sample(stream, 10, new Random(7L));
        boolean distinct = new HashSet<>(sample.reservoir).size() == sample.reservoir.size();
        failures += report("Reservoir sample: size k, distinct, all items from the stream",
                sample.reservoir.size() == 10 && distinct && stream.containsAll(sample.reservoir)
                        && sample.seen == 1000);

        SampleResult<Integer> evenSample = M6ReservoirSampler.sample(stream, x -> x % 2 == 0, 10, new Random(7L));
        boolean allEven = true;
        for (int x : evenSample.reservoir) {
            if (x % 2 != 0) {
                allEven = false;
            }
        }
        failures += report("Filtered reservoir: only matching items, correct seen count",
                evenSample.reservoir.size() == 10 && allEven && evenSample.seen == 500);

        List<ActivityEvent> events = ds.activityStream();
        SampleResult<ActivityEvent> eventSample = M6ReservoirSampler.sample(events, 10, new Random(7L));
        failures += report("Reservoir over activity stream: only stream events",
                eventSample.reservoir.size() == 10 && events.containsAll(eventSample.reservoir)
                        && eventSample.seen == events.size());

        failures += report("Reservoir uniformity (n=100, k=10, 20,000 trials, seeded)", uniformityHolds());

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

    private static boolean isValidStudentOrder(Student[] ranked) {
        for (int i = 1; i < ranked.length; i++) {
            if (M6RandomizedQuickSort.compareStudents(ranked[i - 1], ranked[i]) > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStudents(Student[] ranked, List<Student> original) {
        if (ranked.length != original.size()) {
            return false;
        }
        int[] a = rankedIds(ranked);
        int[] b = new int[original.size()];
        for (int i = 0; i < original.size(); i++) {
            b[i] = original.get(i).id;
        }
        Arrays.sort(a);
        Arrays.sort(b);
        return Arrays.equals(a, b);
    }

    private static int[] rankedIds(Student[] ranked) {
        int[] ids = new int[ranked.length];
        for (int i = 0; i < ranked.length; i++) {
            ids[i] = ranked[i].id;
        }
        return ids;
    }

    private static boolean uniformityHolds() {
        int n = 100;
        int k = 10;
        double[] freqs = selectionFrequencies(n, k, 20_000, DEMO_SEED);
        double expectedFreq = k / (double) n;
        for (double freq : freqs) {
            if (Math.abs(freq - expectedFreq) > 0.03) {
                return false;
            }
        }
        return true;
    }
}
