package edutrack.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import edutrack.data.DataStore;
import edutrack.model.Student;

public class M5NPCompleteness {

    public static final String[] CROSS_PROGRAM_MIX = {
        "CS101", "CS201", "CS202", "CS401", "MA101", "MA301", "PH101", "CH101", "HS201", "MG301"
    };

    public static void run(Scanner sc, DataStore ds) {
        int choice;

        do {
            System.out.println("\n--- Exam Scheduling (NP-Completeness & Approximation) ---");
            System.out.println("1. SAT via DPLL (exam-slot assignment)");
            System.out.println("2. 3-SAT -> CLIQUE reduction");
            System.out.println("3. CLIQUE -> INDEPENDENT-SET -> VERTEX-COVER chain");
            System.out.println("4. Vertex-Cover 2-approximation");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    dpllExamScheduling(sc, ds);
                    break;
                case 2:
                    threeSatToCliqueDemo(sc, ds);
                    break;
                case 3:
                    reductionChainDemo(sc, ds);
                    break;
                case 4:
                    vertexCoverApproxDemo(sc, ds);
                    break;
                case 0:
                    System.out.println("Returning to main menu...");
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    public static int[] resolveCodes(M5Graph g, String[] codes) {
        if (g == null || codes == null) throw new IllegalArgumentException("graph and codes must not be null");
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < g.n; i++) {
            index.put(g.label(i), i);
        }
        int[] out = new int[codes.length];
        int m = 0;
        for (String code : codes) {
            Integer idx = index.get(code);
            if (idx != null) {
                out[m++] = idx;
            }
        }
        return Arrays.copyOf(out, m);
    }

    public static int[] decodeSchedule(M5Graph sub, M5Reductions.SchedEncoding enc, boolean[] assignment) {
        if (sub == null || enc == null || assignment == null) {
            throw new IllegalArgumentException("graph, encoding and assignment must not be null");
        }
        if (enc.courseCount != sub.n || enc.slots < 1 || assignment.length <= enc.numVars) {
            throw new IllegalArgumentException("schedule encoding does not match graph/assignment");
        }
        int[] slotOf = new int[sub.n];
        Arrays.fill(slotOf, -1);
        for (int c = 0; c < sub.n; c++) {
            for (int s = 0; s < enc.slots; s++) {
                if (assignment[enc.varOf(c, s)] && slotOf[c] < 0) {
                    slotOf[c] = s;
                }
            }
        }
        return slotOf;
    }

    public static boolean verifySchedule(M5Graph sub, M5Reductions.SchedEncoding enc,
            boolean[] assignment, int[] slotOf) {
        if (sub == null || enc == null || assignment == null || slotOf == null
                || enc.courseCount != sub.n || slotOf.length != sub.n) {
            return false;
        }
        for (int s : slotOf) {
            if (s < 0) {
                return false;
            }
        }
        if (!M5DPLLSolver.satisfies(enc.clauses, assignment)) {
            return false;
        }
        for (int u = 0; u < sub.n; u++) {
            for (int v = u + 1; v < sub.n; v++) {
                if (sub.hasEdge(u, v) && slotOf[u] == slotOf[v]) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int findMinSlots(M5Graph sub, int cliqueSize) {
        for (int s = Math.max(1, cliqueSize); s <= sub.n; s++) {
            M5Reductions.SchedEncoding enc = M5Reductions.examSchedulingCNF(sub, s);
            if (M5DPLLSolver.solve(enc.numVars, enc.clauses).status == M5DPLLSolver.Status.SAT) {
                return s;
            }
        }
        return -1;
    }

    private static void dpllExamScheduling(Scanner sc, DataStore ds) {
        System.out.println("\n--- SAT via DPLL: Exam-Slot Assignment ---");
        long start = System.nanoTime();
        M5Graph g = M5Graph.courseConflictGraph(ds);
        System.out.println("Course-conflict graph : " + g.n + " courses, " + g.edgeCount()
                + " conflict edges (built in " + elapsedMs(start) + " ms)");

        int[] order = g.degreeOrder();
        int shown = Math.min(12, g.n);
        System.out.println("Courses by conflict degree (top " + shown + ") :");
        for (int i = 0; i < shown; i++) {
            System.out.println("  " + g.label(order[i]) + "  degree=" + g.degree(order[i]));
        }

        System.out.println("1. Cross-program mix (default, " + CROSS_PROGRAM_MIX.length
                + " courses, not a clique)");
        System.out.println("2. Use top-N highest-degree courses");
        System.out.println("3. Enter course codes manually");
        System.out.print("Select subset mode : ");
        int mode = readInt(sc);

        int[] subset;
        if (mode == 2) {
            System.out.print("Subset size N (8-12) : ");
            int n = readInt(sc);
            n = Math.max(3, Math.min(n, Math.min(12, g.n)));
            subset = Arrays.copyOf(order, n);
        } else if (mode == 3) {
            subset = readCourseSubset(sc, g);
            if (subset == null) {
                return;
            }
        } else {
            subset = resolveCodes(g, CROSS_PROGRAM_MIX);
        }

        M5Graph sub = g.inducedSubgraph(subset);
        System.out.println("Selected subset       : " + joinLabels(sub, null));
        System.out.println("Induced subgraph      : " + sub.n + " courses, " + sub.edgeCount()
                + " conflict edges");

        start = System.nanoTime();
        M5Reductions.CliqueSearch lb = M5Reductions.maxClique(sub);
        long cliqueMs = elapsedMs(start);
        int cliqueSize = lb.clique.length;
        System.out.println("Maximum clique        : size " + cliqueSize + " (" + joinLabels(sub, lb.clique)
                + ")  [" + cliqueMs + " ms" + (lb.exact ? "" : ", budget-limited") + "]");
        System.out.println("=> at least " + cliqueSize + " exam slots are necessary (clique lower bound)");

        System.out.print("Number of exam slots to try (1-" + sub.n + ") : ");
        int slots = Math.max(1, Math.min(sub.n, readInt(sc)));
        solveSchedule(sub, slots, cliqueSize);

        System.out.print("Search for the minimum feasible number of slots? (1 = yes, 0 = no) : ");
        if (readInt(sc) == 1) {
            for (int s = Math.max(1, cliqueSize); s <= sub.n; s++) {
                System.out.println("Trying " + s + " slot(s) ...");
                if (solveSchedule(sub, s, cliqueSize)) {
                    System.out.println("Minimum feasible number of exam slots = " + s
                            + (s == cliqueSize ? " (matches the clique lower bound)" : ""));
                    break;
                }
            }
        }
    }

    private static boolean solveSchedule(M5Graph sub, int slots, int cliqueSize) {
        if (slots < cliqueSize) {
            System.out.println("UNSAT: " + slots + " slot(s) cannot host a conflict clique of size "
                    + cliqueSize + " (pigeonhole principle).");
            return false;
        }
        long start = System.nanoTime();
        M5Reductions.SchedEncoding enc = M5Reductions.examSchedulingCNF(sub, slots);
        long encMs = elapsedMs(start);
        System.out.println("CNF encoding          : " + enc.numVars + " variables x_{c,s}, "
                + enc.clauses.size() + " clauses (" + encMs + " ms)");

        start = System.nanoTime();
        M5DPLLSolver.Result res = M5DPLLSolver.solve(enc.numVars, enc.clauses);
        long solveMs = elapsedMs(start);
        System.out.println("DPLL                  : " + res.status + " (decisions=" + res.decisions
                + ", time=" + solveMs + " ms)");

        if (res.status != M5DPLLSolver.Status.SAT) {
            return false;
        }
        int[] slotOf = decodeSchedule(sub, enc, res.assignment);
        StringBuilder[] bySlot = new StringBuilder[slots];
        for (int s = 0; s < slots; s++) {
            bySlot[s] = new StringBuilder();
        }
        for (int c = 0; c < sub.n; c++) {
            if (bySlot[slotOf[c]].length() > 0) {
                bySlot[slotOf[c]].append(", ");
            }
            bySlot[slotOf[c]].append(sub.label(c));
        }
        System.out.println("Derived exam schedule:");
        for (int s = 0; s < slots; s++) {
            System.out.println("  Slot " + (s + 1) + " : "
                    + (bySlot[s].length() == 0 ? "-" : bySlot[s].toString()));
        }
        boolean ok = verifySchedule(sub, enc, res.assignment, slotOf);
        System.out.println("Schedule verified against all conflicts : " + (ok ? "yes" : "NO"));
        return ok;
    }

    private static int[] readCourseSubset(Scanner sc, M5Graph g) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < g.n; i++) {
            index.put(g.label(i), i);
        }
        System.out.print("Enter 3-12 course codes separated by spaces : ");
        String line = sc.nextLine().trim();
        LinkedHashSet<Integer> picked = new LinkedHashSet<>();
        for (String p : line.split("[\\s,]+")) {
            if (p.isEmpty()) {
                continue;
            }
            Integer idx = index.get(p.toUpperCase());
            if (idx == null) {
                System.out.println("  (ignoring unknown course code: " + p + ")");
            } else {
                picked.add(idx);
            }
        }
        if (picked.size() < 3 || picked.size() > 12) {
            System.out.println("Need 3-12 valid distinct course codes; got " + picked.size() + ".");
            return null;
        }
        int[] out = new int[picked.size()];
        int i = 0;
        for (int v : picked) {
            out[i++] = v;
        }
        return out;
    }

    private static void threeSatToCliqueDemo(Scanner sc, DataStore ds) {
        System.out.println("\n--- 3-SAT -> CLIQUE Reduction ---");
        System.out.println("1. Built-in example 3-SAT instance");
        System.out.println("2. Scheduling-derived 3-SAT (3 mutually-conflicting courses, 3 slots)");
        System.out.print("Select instance : ");
        int mode = readInt(sc);

        List<int[]> formula;
        int numVars;
        String[] varNames;

        if (mode == 2) {
            M5Graph g = M5Graph.courseConflictGraph(ds);
            M5Reductions.CliqueSearch tri = M5Reductions.cliqueOfSize(g, 3);
            if (tri.clique.length < 3) {
                System.out.println("No triple of mutually-conflicting courses found.");
                return;
            }
            M5Graph sub = g.inducedSubgraph(Arrays.copyOf(tri.clique, 3));
            int slots = 3;
            numVars = sub.n * slots;
            varNames = new String[numVars + 1];
            for (int c = 0; c < sub.n; c++) {
                for (int s = 0; s < slots; s++) {
                    varNames[c * slots + s + 1] = sub.label(c) + "@S" + (s + 1);
                }
            }
            formula = M5Reductions.examScheduling3CNF(sub, slots);
            System.out.println("Scheduling instance : courses " + joinLabels(sub, null)
                    + " must each occupy one of " + slots + " slots; every pair conflicts.");
        } else {
            numVars = BUILTIN_VARS;
            varNames = new String[numVars + 1];
            for (int v = 1; v <= numVars; v++) {
                varNames[v] = "x" + v;
            }
            formula = builtinThreeSat();
        }

        int m = formula.size();
        System.out.println("3-SAT formula       : " + numVars + " variables, " + m + " clauses");
        printFormula(formula, varNames);

        long start = System.nanoTime();
        M5DPLLSolver.Result sat = M5DPLLSolver.solve(numVars, formula);
        System.out.println("DPLL on formula     : " + sat.status + " (decisions=" + sat.decisions
                + ", time=" + elapsedMs(start) + " ms)");

        start = System.nanoTime();
        M5Reductions.GadgetReduction red = M5Reductions.threeSatToClique(formula, numVars);
        System.out.println("Clause-gadget graph : " + red.graph.n + " nodes (one per literal occurrence), "
                + red.graph.edgeCount() + " edges (built in " + elapsedMs(start) + " ms)");

        start = System.nanoTime();
        M5Reductions.CliqueSearch cs = M5Reductions.cliqueOfSize(red.graph, m);
        System.out.println("Clique search (branch-and-bound, target m=" + m + ") : "
                + (cs.clique.length == m ? "clique found" : "no clique of size m")
                + " (expansions=" + cs.expansions + ", time=" + elapsedMs(start) + " ms"
                + (cs.exact ? "" : ", budget-limited") + ")");

        if (cs.clique.length == m) {
            System.out.print("Clique nodes        : ");
            for (int i = 0; i < cs.clique.length; i++) {
                System.out.print((i == 0 ? "" : ", ") + red.graph.label(cs.clique[i]));
            }
            System.out.println();
            boolean[] assignment = M5Reductions.cliqueToAssignment(red, cs.clique);
            StringBuilder ab = new StringBuilder();
            for (int v = 1; v <= numVars; v++) {
                ab.append(varNames[v]).append('=').append(assignment[v] ? 'T' : 'F');
                if (v < numVars) {
                    ab.append(", ");
                }
            }
            System.out.println("Mapped assignment   : " + ab);
            boolean ok = M5DPLLSolver.satisfies(formula, assignment);
            System.out.println("Assignment satisfies the original 3-SAT formula : " + (ok ? "VERIFIED" : "FAILED"));
        }
        boolean agree = (sat.status == M5DPLLSolver.Status.SAT) == (cs.clique.length == m);
        System.out.println("Reduction check     : formula SAT <=> clique of size m exists : "
                + (agree ? "VERIFIED" : "MISMATCH"));
    }

    private static void reductionChainDemo(Scanner sc, DataStore ds) {
        System.out.println("\n--- CLIQUE -> INDEPENDENT-SET -> VERTEX-COVER Chain ---");
        System.out.println("1. 3-SAT gadget graph (built-in instance, 12 nodes)");
        System.out.println("2. Cross-program mix subgraph of the course-conflict graph ("
                + CROSS_PROGRAM_MIX.length + " courses)");
        System.out.print("Select graph : ");
        int mode = readInt(sc);

        M5Graph g;
        if (mode == 2) {
            M5Graph full = M5Graph.courseConflictGraph(ds);
            g = full.inducedSubgraph(resolveCodes(full, CROSS_PROGRAM_MIX));
        } else {
            g = M5Reductions.threeSatToClique(builtinThreeSat(), BUILTIN_VARS).graph;
        }
        System.out.println("G                   : " + g.n + " vertices, " + g.edgeCount() + " edges");

        long start = System.nanoTime();
        M5Reductions.CliqueSearch mc = M5Reductions.maxClique(g);
        long cliqueMs = elapsedMs(start);
        int k = mc.clique.length;
        System.out.println("Max clique in G     : size k = " + k + " (" + joinLabels(g, mc.clique)
                + ")  [" + cliqueMs + " ms" + (mc.exact ? "" : ", budget-limited") + "]");

        M5Graph comp = g.complement();
        System.out.println("Complement graph ~G : " + comp.n + " vertices, " + comp.edgeCount() + " edges");

        boolean[] indep = new boolean[comp.n];
        for (int v : mc.clique) {
            indep[v] = true;
        }
        boolean okIndep = M5VertexCoverApprox.isIndependentSet(comp, indep);
        System.out.println("Step 1: clique of size k in G  =>  independent set of size k in ~G : "
                + (okIndep ? "VERIFIED" : "FAILED"));

        boolean[] cover = new boolean[comp.n];
        for (int v = 0; v < comp.n; v++) {
            cover[v] = !indep[v];
        }
        boolean okCover = M5VertexCoverApprox.isVertexCover(comp, cover);
        int coverSize = comp.n - k;
        System.out.println("Step 2: independent set of size k in ~G  =>  vertex cover of size |V|-k = "
                + coverSize + " in ~G : " + (okCover ? "VERIFIED" : "FAILED"));
        System.out.println("Vertex cover        : " + joinSet(comp, cover));

        start = System.nanoTime();
        int alpha = M5VertexCoverApprox.bruteForceMaxIndependentSet(comp, null);
        int tau = M5VertexCoverApprox.bruteForceMinVertexCover(comp, null);
        long bruteMs = elapsedMs(start);
        System.out.println("Brute force on ~G   : max independent set = " + alpha
                + ", min vertex cover = " + tau + "  [" + bruteMs + " ms]");
        System.out.println("Correspondence      : max clique in G (k=" + k + ") == max independent set in ~G : "
                + (k == alpha ? "VERIFIED" : "FAILED"));
        System.out.println("Gallai identity     : alpha + tau == |V| (" + alpha + " + " + tau + " == "
                + comp.n + ") : " + (alpha + tau == comp.n ? "VERIFIED" : "FAILED"));
        System.out.println("Interpretation      : the " + coverSize + " vertices outside the clique cover "
                + "every edge of ~G; a minimum cover of ~G has exactly |V|-k vertices.");
    }

    private static void vertexCoverApproxDemo(Scanner sc, DataStore ds) {
        System.out.println("\n--- Vertex-Cover 2-Approximation (Maximal Matching) ---");
        long start = System.nanoTime();
        M5Graph g = M5Graph.courseConflictGraph(ds);
        System.out.println("Course-conflict graph : " + g.n + " courses, " + g.edgeCount()
                + " conflict edges (built in " + elapsedMs(start) + " ms)");

        start = System.nanoTime();
        M5VertexCoverApprox.ApproxResult r = M5VertexCoverApprox.approximate(g);
        long approxMs = elapsedMs(start);
        start = System.nanoTime();
        int optFull = M5VertexCoverApprox.bruteForceMinVertexCover(g, null);
        long bruteMs = elapsedMs(start);
        double ratioFull = optFull == 0 ? 1.0 : (double) r.coverSize / optFull;
        System.out.println("Greedy maximal matching : " + r.matchingSize + " edges (" + approxMs + " ms)");
        System.out.println("2-approx vertex cover   : " + r.coverSize + " of " + g.n + " courses");
        System.out.println("Cover courses           : " + joinSet(g, r.cover));
        boolean valid = M5VertexCoverApprox.isVertexCover(g, r.cover);
        System.out.println("Cover valid (every conflict edge has an endpoint in the cover) : "
                + (valid ? "VERIFIED" : "FAILED"));
        System.out.println("Brute-force optimum   : " + optFull + " courses (" + bruteMs + " ms), "
                + "achieved ratio " + String.format("%.3f", ratioFull)
                + " (must be <= 2) : " + (ratioFull <= 2.0 ? "VERIFIED" : "VIOLATED"));
        System.out.println("Interpretation        : moving/resolving these " + r.coverSize
                + " courses clears every exam conflict; the true optimum is " + optFull + ".");

        M5Graph sub = g.inducedSubgraph(resolveCodes(g, CROSS_PROGRAM_MIX));
        System.out.println("Cross-program mix subgraph : " + sub.n + " courses (" + joinLabels(sub, null)
                + "), " + sub.edgeCount() + " conflict edges");
        start = System.nanoTime();
        M5VertexCoverApprox.ApproxResult rs = M5VertexCoverApprox.approximate(sub);
        long subApproxMs = elapsedMs(start);
        start = System.nanoTime();
        int opt = M5VertexCoverApprox.bruteForceMinVertexCover(sub, null);
        long subBruteMs = elapsedMs(start);
        double ratio = opt == 0 ? 1.0 : (double) rs.coverSize / opt;
        System.out.println("On mix subgraph       : approx cover = " + rs.coverSize
                + " (" + subApproxMs + " ms), brute-force optimum = " + opt + " (" + subBruteMs + " ms)");
        System.out.println("Achieved ratio        : " + String.format("%.3f", ratio)
                + " (must be <= 2) : " + (ratio <= 2.0 ? "VERIFIED" : "VIOLATED"));
    }

    public static List<int[]> builtinThreeSat() {
        List<int[]> f = new ArrayList<>();
        f.add(new int[] { 1, 2, 3 });
        f.add(new int[] { -1, 2, -3 });
        f.add(new int[] { 1, -2, 4 });
        f.add(new int[] { -2, -3, -4 });
        return f;
    }

    public static final int BUILTIN_VARS = 4;

    private static void printFormula(List<int[]> formula, String[] varNames) {
        int limit = Math.min(formula.size(), 30);
        for (int i = 0; i < limit; i++) {
            StringBuilder sb = new StringBuilder("  C" + i + " = (");
            for (int j = 0; j < formula.get(i).length; j++) {
                int lit = formula.get(i)[j];
                if (j > 0) {
                    sb.append(" v ");
                }
                if (lit < 0) {
                    sb.append('~');
                }
                sb.append(varNames[Math.abs(lit)]);
            }
            sb.append(')');
            System.out.println(sb);
        }
        if (formula.size() > limit) {
            System.out.println("  ... (" + (formula.size() - limit) + " more clauses)");
        }
    }

    private static String joinLabels(M5Graph g, int[] vertices) {
        StringBuilder sb = new StringBuilder();
        if (vertices == null) {
            for (int i = 0; i < g.n; i++) {
                sb.append(i == 0 ? "" : ", ").append(g.label(i));
            }
        } else {
            for (int i = 0; i < vertices.length; i++) {
                sb.append(i == 0 ? "" : ", ").append(g.label(vertices[i]));
            }
        }
        return sb.toString();
    }

    private static String joinSet(M5Graph g, boolean[] set) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int v = 0; v < g.n; v++) {
            if (set[v]) {
                sb.append(count == 0 ? "" : ", ").append(g.label(v));
                count++;
            }
        }
        return count == 0 ? "(empty)" : sb.toString();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
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

    public static void main(String[] args) {
        System.out.println("=== M5 NP-Completeness & Approximation: Self-Test ===");
        long start;
        int failures = 0;

        List<int[]> satFormula = new ArrayList<>();
        satFormula.add(new int[] { 1, 2 });
        satFormula.add(new int[] { -1, 2 });
        satFormula.add(new int[] { 1, -2 });
        start = System.nanoTime();
        M5DPLLSolver.Result r = M5DPLLSolver.solve(2, satFormula);
        failures += check("DPLL solves known SAT formula and assignment satisfies it",
                r.status == M5DPLLSolver.Status.SAT && r.assignment != null
                        && M5DPLLSolver.satisfies(satFormula, r.assignment),
                start);

        start = System.nanoTime();
        r = M5DPLLSolver.solve(2, pigeonhole(2, 1));
        failures += check("DPLL proves pigeonhole PHP(2,1) UNSAT",
                r.status == M5DPLLSolver.Status.UNSAT, start);

        start = System.nanoTime();
        r = M5DPLLSolver.solve(6, pigeonhole(3, 2));
        failures += check("DPLL proves pigeonhole PHP(3,2) UNSAT",
                r.status == M5DPLLSolver.Status.UNSAT, start);

        List<int[]> php22 = pigeonhole(2, 2);
        start = System.nanoTime();
        r = M5DPLLSolver.solve(4, php22);
        failures += check("DPLL solves pigeonhole PHP(2,2) SAT and assignment satisfies it",
                r.status == M5DPLLSolver.Status.SAT
                        && M5DPLLSolver.satisfies(php22, r.assignment), start);

        M5Graph tri = new M5Graph(3);
        tri.addEdge(0, 1);
        tri.addEdge(1, 2);
        tri.addEdge(0, 2);
        M5Reductions.SchedEncoding enc3 = M5Reductions.examSchedulingCNF(tri, 3);
        start = System.nanoTime();
        r = M5DPLLSolver.solve(enc3.numVars, enc3.clauses);
        boolean schedOk = r.status == M5DPLLSolver.Status.SAT;
        if (schedOk) {
            schedOk = verifySchedule(tri, enc3, r.assignment, decodeSchedule(tri, enc3, r.assignment));
        }
        failures += check("Scheduling encoding: conflict triangle fits into 3 slots (SAT, verified)",
                schedOk, start);

        M5Reductions.SchedEncoding enc2 = M5Reductions.examSchedulingCNF(tri, 2);
        start = System.nanoTime();
        r = M5DPLLSolver.solve(enc2.numVars, enc2.clauses);
        failures += check("Scheduling encoding: conflict triangle in 2 slots is UNSAT",
                r.status == M5DPLLSolver.Status.UNSAT, start);

        M5Graph path = new M5Graph(3);
        path.addEdge(0, 1);
        path.addEdge(1, 2);
        M5Reductions.SchedEncoding encP = M5Reductions.examSchedulingCNF(path, 2);
        start = System.nanoTime();
        r = M5DPLLSolver.solve(encP.numVars, encP.clauses);
        failures += check("Scheduling encoding: path of 3 courses fits into 2 slots (SAT)",
                r.status == M5DPLLSolver.Status.SAT
                        && verifySchedule(path, encP, r.assignment,
                                decodeSchedule(path, encP, r.assignment)), start);

        Random rnd = new Random(42);
        boolean allOk = true;
        int trials = 25;
        start = System.nanoTime();
        for (int t = 0; t < trials; t++) {
            int vars = 3 + rnd.nextInt(3);
            int m = 4 + rnd.nextInt(4);
            List<int[]> formula = random3SAT(vars, m, rnd);
            boolean satSide = M5DPLLSolver.solve(vars, formula).status == M5DPLLSolver.Status.SAT;
            M5Reductions.GadgetReduction red = M5Reductions.threeSatToClique(formula, vars);
            M5Reductions.CliqueSearch cs = M5Reductions.cliqueOfSize(red.graph, m);
            boolean cliqueSide = cs.clique.length == m && cs.exact;
            boolean ok = satSide == cliqueSide;
            if (cliqueSide) {
                boolean[] assignment = M5Reductions.cliqueToAssignment(red, cs.clique);
                ok = ok && M5DPLLSolver.satisfies(formula, assignment);
            }
            allOk = allOk && ok;
        }
        failures += check("3-SAT<->CLIQUE reduction round-trip on " + trials + " seeded random instances",
                allOk, start);

        start = System.nanoTime();
        List<int[]> sched3 = M5Reductions.examScheduling3CNF(tri, 3);
        M5Reductions.GadgetReduction redS = M5Reductions.threeSatToClique(sched3, 9);
        M5Reductions.CliqueSearch csS = M5Reductions.cliqueOfSize(redS.graph, sched3.size());
        boolean schedGadgetOk = csS.clique.length == sched3.size()
                && M5DPLLSolver.satisfies(sched3, M5Reductions.cliqueToAssignment(redS, csS.clique));
        failures += check("Scheduling-derived 3-SAT gadget (" + redS.graph.n + " nodes) has clique of size "
                + sched3.size() + " mapping to a satisfying assignment", schedGadgetOk, start);

        allOk = true;
        start = System.nanoTime();
        for (int t = 0; t < 5; t++) {
            M5Graph g = randomGraph(9, 0.4, rnd);
            M5Reductions.CliqueSearch mc = M5Reductions.maxClique(g);
            int k = mc.clique.length;
            M5Graph comp = g.complement();
            boolean[] indep = new boolean[comp.n];
            for (int v : mc.clique) {
                indep[v] = true;
            }
            boolean[] cover = new boolean[comp.n];
            for (int v = 0; v < comp.n; v++) {
                cover[v] = !indep[v];
            }
            int alpha = M5VertexCoverApprox.bruteForceMaxIndependentSet(comp, null);
            int tau = M5VertexCoverApprox.bruteForceMinVertexCover(comp, null);
            allOk = allOk && mc.exact
                    && M5VertexCoverApprox.isIndependentSet(comp, indep)
                    && M5VertexCoverApprox.isVertexCover(comp, cover)
                    && alpha == k && tau == comp.n - k;
        }
        failures += check("CLIQUE->INDEPENDENT-SET->VERTEX-COVER chain invariants on 5 random graphs",
                allOk, start);

        allOk = true;
        start = System.nanoTime();
        int[] sizes = { 8, 9, 10 };
        double[] probs = { 0.3, 0.6 };
        for (int sn : sizes) {
            for (double p : probs) {
                M5Graph g = randomGraph(sn, p, rnd);
                M5VertexCoverApprox.ApproxResult a = M5VertexCoverApprox.approximate(g);
                int tau = M5VertexCoverApprox.bruteForceMinVertexCover(g, null);
                allOk = allOk && M5VertexCoverApprox.isVertexCover(g, a.cover)
                        && a.coverSize <= 2 * tau
                        && a.coverSize == 2 * a.matchingSize;
            }
        }
        failures += check("Vertex-cover 2-approximation valid and within factor 2 on 6 random graphs",
                allOk, start);

        start = System.nanoTime();
        DataStore ds = new DataStore();
        M5Graph g = M5Graph.courseConflictGraph(ds);
        boolean graphOk = g.n == ds.courses().size() && g.edgeCount() > 0;
        int degreeSum = 0;
        int minDeg = Integer.MAX_VALUE;
        int maxDeg = 0;
        for (int v = 0; v < g.n && graphOk; v++) {
            int d = g.degree(v);
            degreeSum += d;
            minDeg = Math.min(minDeg, d);
            maxDeg = Math.max(maxDeg, d);
            if (g.hasEdge(v, v)) {
                graphOk = false;
            }
            for (int u = 0; u < g.n; u++) {
                if (g.hasEdge(u, v) != g.hasEdge(v, u)) {
                    graphOk = false;
                }
            }
        }
        graphOk = graphOk && degreeSum == 2 * g.edgeCount();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < ds.courses().size(); i++) {
            index.put(ds.courses().get(i).code, i);
        }
        Set<Long> pairs = new HashSet<>();
        for (Student s : ds.students()) {
            List<String> en = s.enrolledCourses;
            for (int a = 0; a < en.size(); a++) {
                for (int b = a + 1; b < en.size(); b++) {
                    int u = index.get(en.get(a));
                    int v = index.get(en.get(b));
                    pairs.add((long) Math.min(u, v) * 64 + Math.max(u, v));
                }
            }
        }
        graphOk = graphOk && pairs.size() == g.edgeCount();
        failures += check("Course-conflict graph from DataStore: symmetric, loop-free, edge count matches "
                + "independent recomputation (" + g.edgeCount() + " edges)", graphOk, start);

        start = System.nanoTime();
        int maxPossible = g.n * (g.n - 1) / 2;
        failures += check("Conflict graph has program-block structure: " + g.edgeCount() + " edges ("
                + maxPossible + " possible), degrees " + minDeg + ".." + maxDeg,
                g.edgeCount() == 131 && g.edgeCount() < maxPossible && minDeg < maxDeg, start);

        start = System.nanoTime();
        M5VertexCoverApprox.ApproxResult full = M5VertexCoverApprox.approximate(g);
        int optFull = M5VertexCoverApprox.bruteForceMinVertexCover(g, null);
        failures += check("2-approx cover on full conflict graph valid; cover=" + full.coverSize
                + " vs brute-force optimum=" + optFull + ", ratio "
                + String.format("%.3f", (double) full.coverSize / optFull) + " <= 2",
                M5VertexCoverApprox.isVertexCover(g, full.cover)
                        && full.coverSize == 2 * full.matchingSize
                        && optFull == 17
                        && full.coverSize <= 2 * optFull,
                start);

        start = System.nanoTime();
        M5Graph mix = g.inducedSubgraph(resolveCodes(g, CROSS_PROGRAM_MIX));
        M5Reductions.CliqueSearch mixMc = M5Reductions.maxClique(mix);
        int mixMinSlots = findMinSlots(mix, mixMc.clique.length);
        M5VertexCoverApprox.ApproxResult mixApprox = M5VertexCoverApprox.approximate(mix);
        int mixOpt = M5VertexCoverApprox.bruteForceMinVertexCover(mix, null);
        failures += check("Cross-program mix subgraph (" + mix.n + " courses, " + mix.edgeCount()
                + " edges): not a clique, clique=" + mixMc.clique.length + ", minSlots=" + mixMinSlots
                + ", vcApprox=" + mixApprox.coverSize + " vs opt=" + mixOpt,
                mix.n == CROSS_PROGRAM_MIX.length
                        && mixMc.clique.length == 5 && mixMinSlots == 5 && mixMinSlots < mix.n
                        && mixApprox.coverSize == 10 && mixOpt == 7
                        && M5VertexCoverApprox.isVertexCover(mix, mixApprox.cover)
                        && mixApprox.coverSize <= 2 * mixOpt,
                start);

        System.out.println("===");
        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static int check(String name, boolean ok, long startNanos) {
        System.out.println((ok ? "PASS" : "FAIL") + " : " + name
                + "  [" + elapsedMs(startNanos) + " ms]");
        return ok ? 0 : 1;
    }

    private static List<int[]> pigeonhole(int pigeons, int holes) {
        List<int[]> clauses = new ArrayList<>();
        for (int p = 0; p < pigeons; p++) {
            int[] clause = new int[holes];
            for (int h = 0; h < holes; h++) {
                clause[h] = p * holes + h + 1;
            }
            clauses.add(clause);
        }
        for (int h = 0; h < holes; h++) {
            for (int p1 = 0; p1 < pigeons; p1++) {
                for (int p2 = p1 + 1; p2 < pigeons; p2++) {
                    clauses.add(new int[] { -(p1 * holes + h + 1), -(p2 * holes + h + 1) });
                }
            }
        }
        return clauses;
    }

    private static List<int[]> random3SAT(int vars, int clauseCount, Random rnd) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < clauseCount; i++) {
            int a = 1 + rnd.nextInt(vars);
            int b;
            do {
                b = 1 + rnd.nextInt(vars);
            } while (b == a);
            int c;
            do {
                c = 1 + rnd.nextInt(vars);
            } while (c == a || c == b);
            out.add(new int[] { rnd.nextBoolean() ? a : -a,
                    rnd.nextBoolean() ? b : -b,
                    rnd.nextBoolean() ? c : -c });
        }
        return out;
    }

    private static M5Graph randomGraph(int n, double p, Random rnd) {
        M5Graph g = new M5Graph(n);
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (rnd.nextDouble() < p) {
                    g.addEdge(u, v);
                }
            }
        }
        return g;
    }
}
