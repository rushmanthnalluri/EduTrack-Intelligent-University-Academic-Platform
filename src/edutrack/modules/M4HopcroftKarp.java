package edutrack.modules;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * Hopcroft-Karp maximum bipartite matching (O(E * sqrt(V))), plus a simple
 * Kuhn augmenting-path implementation used as a correctness reference.
 * The graph is given as adjacency lists from the left side to right-side indices.
 */
public final class M4HopcroftKarp {

    private static final int INF = Integer.MAX_VALUE / 2;

    private M4HopcroftKarp() {
    }

    public static class Result {
        public final int size;
        public final int[] matchLeft;   // left vertex  -> right vertex (-1 if unmatched)
        public final int[] matchRight;  // right vertex -> left vertex  (-1 if unmatched)

        Result(int size, int[] matchLeft, int[] matchRight) {
            this.size = size;
            this.matchLeft = matchLeft;
            this.matchRight = matchRight;
        }
    }

    public static Result maxMatching(int leftCount, int rightCount, List<List<Integer>> adj) {
        checkGraph(leftCount, rightCount, adj);
        int[] matchL = new int[leftCount];
        int[] matchR = new int[rightCount];
        Arrays.fill(matchL, -1);
        Arrays.fill(matchR, -1);
        int[] dist = new int[leftCount];
        int[] nilDist = new int[1];

        int size = 0;
        while (bfs(adj, matchL, matchR, dist, nilDist)) {
            for (int u = 0; u < leftCount; u++) {
                if (matchL[u] == -1 && dfs(u, adj, matchL, matchR, dist, nilDist)) {
                    size++;
                }
            }
        }
        return new Result(size, matchL, matchR);
    }

    /** BFS from all free left vertices; layers the graph up to the shortest augmenting path. */
    private static boolean bfs(List<List<Integer>> adj, int[] matchL, int[] matchR,
            int[] dist, int[] nilDist) {
        Queue<Integer> queue = new ArrayDeque<>();
        for (int u = 0; u < dist.length; u++) {
            if (matchL[u] == -1) {
                dist[u] = 0;
                queue.add(u);
            } else {
                dist[u] = INF;
            }
        }
        nilDist[0] = INF;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            if (dist[u] < nilDist[0]) {
                for (int v : adj.get(u)) {
                    int m = matchR[v];
                    if (m == -1) {
                        nilDist[0] = dist[u] + 1;
                    } else if (dist[m] == INF) {
                        dist[m] = dist[u] + 1;
                        queue.add(m);
                    }
                }
            }
        }
        return nilDist[0] != INF;
    }

    /** DFS along the BFS layering; augments one shortest path per successful call. */
    private static boolean dfs(int u, List<List<Integer>> adj, int[] matchL, int[] matchR,
            int[] dist, int[] nilDist) {
        for (int v : adj.get(u)) {
            int m = matchR[v];
            if ((m == -1 && nilDist[0] == dist[u] + 1)
                    || (m != -1 && dist[m] == dist[u] + 1 && dfs(m, adj, matchL, matchR, dist, nilDist))) {
                matchL[u] = v;
                matchR[v] = u;
                return true;
            }
        }
        dist[u] = INF;
        return false;
    }

    private static void checkGraph(int leftCount, int rightCount, List<List<Integer>> adj) {
        if (leftCount < 0 || rightCount < 0 || adj.size() < leftCount) {
            throw new IllegalArgumentException("adjacency must provide " + leftCount
                    + " left lists, got " + adj.size());
        }
        for (int u = 0; u < leftCount; u++) {
            for (int v : adj.get(u)) {
                if (v < 0 || v >= rightCount) {
                    throw new IllegalArgumentException("adjacency index out of range: " + v
                            + " (right side has " + rightCount + " vertices)");
                }
            }
        }
    }

    /** Simple O(V * E) augmenting-path matching, kept as a reference for cross-checks. */
    public static Result kuhnMatching(int leftCount, int rightCount, List<List<Integer>> adj) {
        checkGraph(leftCount, rightCount, adj);
        int[] matchL = new int[leftCount];
        int[] matchR = new int[rightCount];
        Arrays.fill(matchL, -1);
        Arrays.fill(matchR, -1);
        int[] seen = new int[rightCount];

        int size = 0;
        for (int u = 0; u < leftCount; u++) {
            if (tryKuhn(u, adj, matchL, matchR, seen, u + 1)) {
                size++;
            }
        }
        return new Result(size, matchL, matchR);
    }

    private static boolean tryKuhn(int u, List<List<Integer>> adj, int[] matchL, int[] matchR,
            int[] seen, int stamp) {
        for (int v : adj.get(u)) {
            if (seen[v] != stamp) {
                seen[v] = stamp;
                if (matchR[v] == -1 || tryKuhn(matchR[v], adj, matchL, matchR, seen, stamp)) {
                    matchL[u] = v;
                    matchR[v] = u;
                    return true;
                }
            }
        }
        return false;
    }
}
