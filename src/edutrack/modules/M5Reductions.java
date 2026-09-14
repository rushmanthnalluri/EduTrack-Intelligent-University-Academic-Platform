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
                if (g.adj[u][v]) {
                    for (int s = 0; s < slots; s++) {
                        enc.clauses.add(new int[] { -enc.varOf(u, s), -enc.varOf(v, s) });
                    }
                }
            }
        }
        return enc;
    }

    public static List<int[]> examScheduling3CNF(M5Graph g, int slots) {
        List<int[]> clauses = new ArrayList<>();
        for (int c = 0; c < g.n; c++) {
            int[] clause = new int[slots];
            for (int s = 0; s < slots; s++) {
                clause[s] = c * slots + s + 1;
            }
            clauses.add(padToThree(clause));
        }
        for (int u = 0; u < g.n; u++) {
            for (int v = u + 1; v < g.n; v++) {
                if (g.adj[u][v]) {
                    for (int s = 0; s < slots; s++) {
                        clauses.add(new int[] { -(u * slots + s + 1), -(v * slots + s + 1),
                                -(v * slots + s + 1) });
                    }
                }
            }
        }
        return clauses;
    }

    private static int[] padToThree(int[] lits) {
        if (lits.length >= 3) {
            return lits;
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = lits[Math.min(i, lits.length - 1)];
        }
        return out;
    }

    public static class GadgetReduction {
        public M5Graph graph;
        public int[] nodeClause;
        public int[] nodeLiteral;
        public int clauseCount;
        public int numVars;
    }

    public static GadgetReduction threeSatToClique(List<int[]> clauses, int numVars) {
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
                red.graph.labels[id] = "c" + ci + ":" + (lit > 0 ? "" : "~") + "x" + Math.abs(lit);
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
        boolean[] assignment = new boolean[red.numVars + 1];
        for (int node : clique) {
            int lit = red.nodeLiteral[node];
            assignment[Math.abs(lit)] = lit > 0;
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
