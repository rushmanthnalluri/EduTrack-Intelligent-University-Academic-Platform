package edutrack.modules;

import java.util.ArrayList;
import java.util.List;

public class M5VertexCoverApprox {

    public static class ApproxResult {
        public boolean[] cover;
        public int coverSize;
        public int matchingSize;
        public List<int[]> matchingEdges;
    }

    public static ApproxResult approximate(M5Graph g) {
        ApproxResult r = new ApproxResult();
        r.cover = new boolean[g.n];
        r.matchingEdges = new ArrayList<>();
        boolean[] matched = new boolean[g.n];
        for (int u = 0; u < g.n; u++) {
            for (int v = u + 1; v < g.n; v++) {
                if (g.adj[u][v] && !matched[u] && !matched[v]) {
                    matched[u] = true;
                    matched[v] = true;
                    r.cover[u] = true;
                    r.cover[v] = true;
                    r.matchingEdges.add(new int[] { u, v });
                }
            }
        }
        r.matchingSize = r.matchingEdges.size();
        int size = 0;
        for (boolean b : r.cover) {
            if (b) {
                size++;
            }
        }
        r.coverSize = size;
        return r;
    }

    public static boolean isVertexCover(M5Graph g, boolean[] cover) {
        for (int u = 0; u < g.n; u++) {
            for (int v = u + 1; v < g.n; v++) {
                if (g.adj[u][v] && !cover[u] && !cover[v]) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isIndependentSet(M5Graph g, boolean[] set) {
        for (int u = 0; u < g.n; u++) {
            if (!set[u]) {
                continue;
            }
            for (int v = u + 1; v < g.n; v++) {
                if (set[v] && g.adj[u][v]) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int bruteForceMinVertexCover(M5Graph g, boolean[] outCover) {
        requireBruteForceSize(g);
        int best = g.n + 1;
        int bestMask = 0;
        for (int mask = 0; mask < (1 << g.n); mask++) {
            int bits = Integer.bitCount(mask);
            if (bits >= best) {
                continue;
            }
            if (coversAll(g, mask)) {
                best = bits;
                bestMask = mask;
            }
        }
        if (outCover != null && best <= g.n) {
            for (int v = 0; v < g.n; v++) {
                outCover[v] = (bestMask & (1 << v)) != 0;
            }
        }
        return best;
    }

    public static int bruteForceMaxIndependentSet(M5Graph g, boolean[] outSet) {
        requireBruteForceSize(g);
        int best = -1;
        int bestMask = 0;
        for (int mask = 0; mask < (1 << g.n); mask++) {
            int bits = Integer.bitCount(mask);
            if (bits <= best) {
                continue;
            }
            if (isIndependent(g, mask)) {
                best = bits;
                bestMask = mask;
            }
        }
        if (outSet != null && best >= 0) {
            for (int v = 0; v < g.n; v++) {
                outSet[v] = (bestMask & (1 << v)) != 0;
            }
        }
        return best;
    }

    private static void requireBruteForceSize(M5Graph g) {
        if (g.n > 22) {
            throw new IllegalArgumentException(
                    "brute force is limited to graphs with at most 22 vertices (got " + g.n + ")");
        }
    }

    private static boolean coversAll(M5Graph g, int mask) {
        for (int u = 0; u < g.n; u++) {
            if ((mask & (1 << u)) != 0) {
                continue;
            }
            for (int v = u + 1; v < g.n; v++) {
                if ((mask & (1 << v)) == 0 && g.adj[u][v]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isIndependent(M5Graph g, int mask) {
        for (int u = 0; u < g.n; u++) {
            if ((mask & (1 << u)) == 0) {
                continue;
            }
            for (int v = u + 1; v < g.n; v++) {
                if ((mask & (1 << v)) != 0 && g.adj[u][v]) {
                    return false;
                }
            }
        }
        return true;
    }
}
