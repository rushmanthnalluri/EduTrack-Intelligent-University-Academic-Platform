package edutrack.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class M5Reductions {

    public static class SchedEncoding {
        public int numVars;
        public int courseCount;
        public int slots;
        public List<int[]> clauses;

        public int varOf(int course, int slot) {
            return course * slots + slot + 1;
        }
    }

    public static SchedEncoding examSchedulingCNF(M5Graph g, int slots) {
        validateSchedulingInput(g, slots);
        SchedEncoding enc = new SchedEncoding();
        enc.courseCount = g.n;
        enc.slots = slots;
        enc.numVars = g.n * slots;
        enc.clauses = new ArrayList<>();

        for (int c = 0; c < g.n; c++) {
            int[] clause = new int[slots];
            for (int s = 0; s < slots; s++) {
                clause[s] = enc.varOf(c, s);
            }
            enc.clauses.add(clause);
        }
        for (int c = 0; c < g.n; c++) {
            for (int s1 = 0; s1 < slots; s1++) {
                for (int s2 = s1 + 1; s2 < slots; s2++) {
                    enc.clauses.add(new int[] { -enc.varOf(c, s1), -enc.varOf(c, s2) });
                }
            }
        }
        for (int u = 0; u < g.n; u++) {
            for (int v = u + 1; v < g.n; v++) {
                if (g.hasEdge(u, v)) {
                    for (int s = 0; s < slots; s++) {
                        enc.clauses.add(new int[] { -enc.varOf(u, s), -enc.varOf(v, s) });
                    }
                }
            }
        }
        return enc;
    }

    /**
     * Produces an equisatisfiable 3-CNF encoding of exam scheduling.
     * Each course receives exactly one slot: one at-least-one constraint and
     * pairwise at-most-one constraints. For more than three slots, a chain of
     * fresh auxiliary variables converts the long at-least-one clause to 3-CNF.
     */
    public static List<int[]> examScheduling3CNF(M5Graph g, int slots) {
        validateSchedulingInput(g, slots);
        List<int[]> clauses = new ArrayList<>();
        int baseVars = g.n * slots;
        int nextAux = baseVars + 1;

        for (int c = 0; c < g.n; c++) {
            int[] vars = new int[slots];
            for (int s = 0; s < slots; s++) {
                vars[s] = c * slots + s + 1;
            }

            if (slots <= 3) {
                clauses.add(padToThree(vars));
            } else {
                // OR(x1,...,xn) encoded as:
                // (x1 v x2 v y1), (~y1 v x3 v y2), ..., (~y(n-3) v x(n-1) v xn)
                int previousAux = 0;
                clauses.add(new int[] { vars[0], vars[1], nextAux });
                previousAux = nextAux++;
                for (int s = 2; s < slots - 2; s++) {
                    int aux = nextAux++;
                    clauses.add(new int[] { -previousAux, vars[s], aux });
                    previousAux = aux;
                }
                clauses.add(new int[] { -previousAux, vars[slots - 2], vars[slots - 1] });
            }

            for (int s1 = 0; s1 < slots; s1++) {
                for (int s2 = s1 + 1; s2 < slots; s2++) {
                    clauses.add(new int[] {
                        -vars[s1], -vars[s2], -vars[s2]
                    });
                }
            }
        }

        for (int u = 0; u < g.n; u++) {
            for (int v = u + 1; v < g.n; v++) {
                if (g.hasEdge(u, v)) {
                    for (int s = 0; s < slots; s++) {
                        int uVar = u * slots + s + 1;
                        int vVar = v * slots + s + 1;
                        clauses.add(new int[] { -uVar, -vVar, -vVar });
                    }
                }
            }
        }
        return clauses;
    }

    private static int[] padToThree(int[] literals) {
        if (literals.length == 0) {
            throw new IllegalArgumentException("clause must contain at least one literal");
        }
        if (literals.length >= 3) {
            return Arrays.copyOf(literals, literals.length);
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = literals[Math.min(i, literals.length - 1)];
        }
        return out;
    }

    private static void validateSchedulingInput(M5Graph g, int slots) {
        if (g == null) throw new IllegalArgumentException("graph must not be null");
        if (slots < 1) throw new IllegalArgumentException("slots must be >= 1");
        try {
            Math.multiplyExact(g.n, slots);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("graph and slot count are too large", e);
        }
    }

    private static void validateThreeSatFormula(List<int[]> clauses, int numVars) {
        if (clauses == null || numVars < 0) {
            throw new IllegalArgumentException("clauses must be non-null and numVars must be non-negative");
        }
        for (int[] clause : clauses) {
            if (clause == null || clause.length != 3) {
                throw new IllegalArgumentException("3-SAT clauses must contain exactly three literals");
            }
            for (int lit : clause) {
                if (lit == 0 || Math.abs((long) lit) > numVars) {
                    throw new IllegalArgumentException("literal outside 1.." + numVars + ": " + lit);
                }
            }
        }
    }

    public static class GadgetReduction {
        public M5Graph graph;
        public int[] nodeClause;
        public int[] nodeLiteral;
        public int clauseCount;
        public int numVars;
    }

    public static GadgetReduction threeSatToClique(List<int[]> clauses, int numVars) {
        validateThreeSatFormula(clauses, numVars);
        int nodes = 0;
        for (int[] c : clauses) {
            nodes += c.length;
        }
        GadgetReduction red = new GadgetReduction();
        red.graph = new M5Graph(nodes);
        red.nodeClause = new int[nodes];
        red.nodeLiteral = new int[nodes];
        red.clauseCount = clauses.size();
        red.numVars = numVars;

        int id = 0;
        for (int ci = 0; ci < clauses.size(); ci++) {
            for (int lit : clauses.get(ci)) {
                red.nodeClause[id] = ci;
                red.nodeLiteral[id] = lit;
                red.graph.setLabel(id, "c" + ci + ":" + (lit > 0 ? "" : "~") + "x" + Math.abs(lit));
                id++;
            }
        }
        for (int i = 0; i < nodes; i++) {
            for (int j = i + 1; j < nodes; j++) {
                if (red.nodeClause[i] != red.nodeClause[j]
                        && red.nodeLiteral[i] != -red.nodeLiteral[j]) {
                    red.graph.addEdge(i, j);
                }
            }
        }
        return red;
    }

    public static boolean[] cliqueToAssignment(GadgetReduction red, int[] clique) {
        if (red == null || red.nodeLiteral == null || red.nodeClause == null || clique == null) {
            throw new IllegalArgumentException("reduction and clique must not be null");
        }
        if (red.graph == null || red.nodeLiteral.length != red.nodeClause.length
                || red.graph.n != red.nodeLiteral.length || red.clauseCount < 0 || red.numVars < 0) {
            throw new IllegalArgumentException("invalid reduction metadata");
        }
        boolean[] seenClause = new boolean[red.clauseCount];
        boolean[] assignment = new boolean[red.numVars + 1];
        boolean[] seenNode = new boolean[red.nodeLiteral.length];
        for (int node : clique) {
            if (node < 0 || node >= red.nodeLiteral.length) {
                throw new IllegalArgumentException("clique vertex out of range: " + node);
            }
            if (seenNode[node]) {
                throw new IllegalArgumentException("clique contains duplicate vertex");
            }
            seenNode[node] = true;
            int clause = red.nodeClause[node];
            int lit = red.nodeLiteral[node];
            if (clause < 0 || clause >= red.clauseCount || lit == 0
                    || Math.abs((long) lit) > red.numVars) {
                throw new IllegalArgumentException("invalid reduction vertex");
            }
            if (seenClause[clause]) {
                throw new IllegalArgumentException("clique contains multiple literals from one clause");
            }
            seenClause[clause] = true;
            assignment[Math.abs(lit)] = lit > 0;
        }
        for (int i = 0; i < clique.length; i++) {
            for (int j = i + 1; j < clique.length; j++) {
                if (!red.graph.hasEdge(clique[i], clique[j])) {
                    throw new IllegalArgumentException("clique vertices are not pairwise adjacent");
                }
            }
        }
        return assignment;
    }

    public static class CliqueSearch {
        public int[] clique = new int[0];
        public boolean exact = true;
        public long expansions;
    }

    private static final long EXPANSION_CAP = 20_000_000L;

    public static CliqueSearch maxClique(M5Graph g) {
        return cliqueSearch(g, g.n);
    }

    public static CliqueSearch cliqueOfSize(M5Graph g, int targetSize) {
        return cliqueSearch(g, targetSize);
    }

    private static CliqueSearch cliqueSearch(M5Graph g, int target) {
        if (g == null) throw new IllegalArgumentException("graph must not be null");
        if (target < 0 || target > g.n) {
            throw new IllegalArgumentException("target clique size must be between 0 and " + g.n);
        }
        CliqueSearch res = new CliqueSearch();
        int[] candidates = new int[g.n];
        for (int i = 0; i < g.n; i++) {
            candidates[i] = i;
        }
        int[][] colored = colorSort(g, candidates);
        long[] expansions = new long[1];
        boolean[] done = new boolean[1];
        expand(g, colored[0], colored[1], 0, new int[g.n], res, expansions, done, target);
        res.expansions = expansions[0];
        return res;
    }

    private static void expand(M5Graph g, int[] cand, int[] colors, int depth, int[] current,
            CliqueSearch res, long[] expansions, boolean[] done, int target) {
        for (int i = cand.length - 1; i >= 0; i--) {
            if (done[0]) {
                return;
            }
            if (++expansions[0] > EXPANSION_CAP) {
                res.exact = false;
                done[0] = true;
                return;
            }
            if (depth + colors[i] <= res.clique.length) {
                return;
            }
            int v = cand[i];
            current[depth] = v;
            int[] newCand = new int[i];
            int m = 0;
            for (int j = 0; j < i; j++) {
                if (g.adj[v][cand[j]]) {
                    newCand[m++] = cand[j];
                }
            }
            if (m == 0) {
                if (depth + 1 > res.clique.length) {
                    res.clique = Arrays.copyOf(current, depth + 1);
                    if (res.clique.length >= target) {
                        done[0] = true;
                        return;
                    }
                }
            } else {
                int[][] colored = colorSort(g, Arrays.copyOf(newCand, m));
                expand(g, colored[0], colored[1], depth + 1, current, res, expansions, done, target);
            }
        }
    }

    private static int[][] colorSort(M5Graph g, int[] cand) {
        List<List<Integer>> classes = new ArrayList<>();
        for (int v : cand) {
            int c = 0;
            while (true) {
                if (c == classes.size()) {
                    classes.add(new ArrayList<>());
                    break;
                }
                boolean conflict = false;
                for (int u : classes.get(c)) {
                    if (g.adj[v][u]) {
                        conflict = true;
                        break;
                    }
                }
                if (!conflict) {
                    break;
                }
                c++;
            }
            classes.get(c).add(v);
        }
        int[] order = new int[cand.length];
        int[] colors = new int[cand.length];
        int p = 0;
        for (int c = 0; c < classes.size(); c++) {
            for (int v : classes.get(c)) {
                order[p] = v;
                colors[p] = c + 1;
                p++;
            }
        }
        return new int[][] { order, colors };
    }
}
