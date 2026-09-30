package edutrack.modules;

import java.util.Arrays;

/**
 * Exact Bitmask-DP Traveling Salesperson solver used by the Campus Auditor.
 * The tour starts at node 0, visits every node once, and returns to node 0.
 */
public final class M3CampusTsp {
    private M3CampusTsp() {}

    public static final class TourResult {
        public final int minCost;
        public final int[] path;

        TourResult(int minCost, int[] path) {
            this.minCost = minCost;
            this.path = path;
        }
    }

    public static TourResult solve(int[][] distance) {
        validate(distance);
        int n = distance.length;
        if (n == 1) return new TourResult(0, new int[] {0, 0});
        if (n > 20) throw new IllegalArgumentException("Campus TSP supports at most 20 nodes");

        int size = 1 << n;
        long[][] dp = new long[size][n];
        short[][] parent = new short[size][n];
        for (int mask = 0; mask < size; mask++) {
            Arrays.fill(dp[mask], Long.MAX_VALUE / 4);
            Arrays.fill(parent[mask], (short) -1);
        }
        dp[1][0] = 0;

        for (int mask = 1; mask < size; mask++) {
            if ((mask & 1) == 0) continue;
            for (int u = 0; u < n; u++) {
                long current = dp[mask][u];
                if (current >= Long.MAX_VALUE / 8) continue;
                for (int v = 1; v < n; v++) {
                    if ((mask & (1 << v)) != 0) continue;
                    int next = mask | (1 << v);
                    long candidate = current + distance[u][v];
                    if (candidate < dp[next][v]) {
                        dp[next][v] = candidate;
                        parent[next][v] = (short) u;
                    }
                }
            }
        }

        int full = size - 1;
        long best = Long.MAX_VALUE / 4;
        int last = -1;
        for (int u = 1; u < n; u++) {
            long candidate = dp[full][u] + distance[u][0];
            if (candidate < best) {
                best = candidate;
                last = u;
            }
        }

        int[] path = new int[n + 1];
        path[n] = 0;
        int mask = full;
        int pos = n - 1;
        int current = last;
        while (current != 0 && pos >= 0) {
            path[pos--] = current;
            int previous = parent[mask][current];
            mask ^= 1 << current;
            current = previous;
        }
        path[0] = 0;
        return new TourResult(Math.toIntExact(best), path);
    }

    private static void validate(int[][] distance) {
        if (distance == null || distance.length == 0) {
            throw new IllegalArgumentException("distance matrix must not be empty");
        }
        int n = distance.length;
        for (int i = 0; i < n; i++) {
            if (distance[i] == null || distance[i].length != n) {
                throw new IllegalArgumentException("distance matrix must be square");
            }
            for (int j = 0; j < n; j++) {
                if (distance[i][j] < 0) throw new IllegalArgumentException("distances must be non-negative");
                if (i == j && distance[i][j] != 0) {
                    throw new IllegalArgumentException("diagonal distance must be zero");
                }
            }
        }
    }

    public static void main(String[] args) {
        int[][] d = {
            {0,10,15,20,25,30},
            {10,0,35,25,18,22},
            {15,35,0,30,28,14},
            {20,25,30,0,12,16},
            {25,18,28,12,0,24},
            {30,22,14,16,24,0}
        };
        TourResult r = solve(d);
        if (r.minCost != 85) throw new AssertionError("Expected 85, got " + r.minCost);
        System.out.println("Campus TSP self-test passed: 85 minutes");
    }
}
