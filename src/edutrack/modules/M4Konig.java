package edutrack.modules;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Konig's theorem: reconstructs a minimum vertex cover of a bipartite graph
 * from a maximum matching. Alternating paths are grown from unmatched left
 * vertices (left->right along non-matching edges, right->left along matching
 * edges); the cover is (left vertices NOT reached) union (right vertices reached).
 */
public final class M4Konig {

    private M4Konig() {
    }

    public static class Cover {
        public final boolean[] leftInCover;
        public final boolean[] rightInCover;
        public final int leftSize;
        public final int rightSize;
        public final int size;

        Cover(boolean[] leftInCover, boolean[] rightInCover, int leftSize, int rightSize) {
            this.leftInCover = leftInCover;
            this.rightInCover = rightInCover;
            this.leftSize = leftSize;
            this.rightSize = rightSize;
            this.size = leftSize + rightSize;
        }
    }

    public static Cover minVertexCover(int leftCount, int rightCount, List<List<Integer>> adj,
            int[] matchLeft, int[] matchRight) {
        checkInputs(leftCount, rightCount, adj, matchLeft, matchRight);
        boolean[] visL = new boolean[leftCount];
        boolean[] visR = new boolean[rightCount];
        Queue<Integer> queue = new ArrayDeque<>();
        for (int u = 0; u < leftCount; u++) {
            if (matchLeft[u] == -1) {
                visL[u] = true;
                queue.add(u); // left u encoded as u, right v encoded as leftCount + v
            }
        }
        while (!queue.isEmpty()) {
            int id = queue.poll();
            if (id < leftCount) {
                for (int v : adj.get(id)) {
                    if (matchLeft[id] != v && !visR[v]) {
                        visR[v] = true;
                        queue.add(leftCount + v);
                    }
                }
            } else {
                int m = matchRight[id - leftCount];
                if (m != -1 && !visL[m]) {
                    visL[m] = true;
                    queue.add(m);
                }
            }
        }

        boolean[] coverL = new boolean[leftCount];
        boolean[] coverR = new boolean[rightCount];
        int leftSize = 0;
        int rightSize = 0;
        for (int u = 0; u < leftCount; u++) {
            coverL[u] = !visL[u];
            if (coverL[u]) {
                leftSize++;
            }
        }
        for (int v = 0; v < rightCount; v++) {
            coverR[v] = visR[v];
            if (coverR[v]) {
                rightSize++;
            }
        }
        return new Cover(coverL, coverR, leftSize, rightSize);
    }

    /** Verifies that every edge of the graph has at least one endpoint in the cover. */
    public static boolean coversAllEdges(List<List<Integer>> adj, Cover cover) {
        for (int u = 0; u < adj.size(); u++) {
            for (int v : adj.get(u)) {
                if (!cover.leftInCover[u] && !cover.rightInCover[v]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The alternating-path construction silently produces a non-cover when the
     * matching arrays are inconsistent, so validate them first: sizes must match
     * the graph and matchLeft[u] == v must imply matchRight[v] == u.
     */
    private static void checkInputs(int leftCount, int rightCount, List<List<Integer>> adj,
            int[] matchLeft, int[] matchRight) {
        if (matchLeft.length != leftCount || matchRight.length != rightCount || adj.size() < leftCount) {
            throw new IllegalArgumentException("matching arrays do not fit the graph: leftCount="
                    + leftCount + ", rightCount=" + rightCount + ", matchLeft.length=" + matchLeft.length
                    + ", matchRight.length=" + matchRight.length + ", adjacency lists=" + adj.size());
        }
        for (int u = 0; u < leftCount; u++) {
            int v = matchLeft[u];
            if (v < -1 || v >= rightCount || (v >= 0 && matchRight[v] != u)) {
                throw new IllegalArgumentException("inconsistent matching at left vertex " + u);
            }
        }
    }
}
