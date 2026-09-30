package edutrack.modules;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * Hand-written max-flow algorithms for Module M4, all operating on
 * {@link M4FlowNetwork} and mutating its flow values.
 */
public final class M4MaxFlow {

    private static final int RECURSION_NODE_CAP = 4096;

    private M4MaxFlow() {
    }

    public static class Result {
        public final long maxFlow;
        /** Augmenting-path count for Ford-Fulkerson / Edmonds-Karp; level-graph phases for Dinic. */
        public final int phases;

        Result(long maxFlow, int phases) {
            this.maxFlow = maxFlow;
            this.phases = phases;
        }
    }

    // ------------------------------------------------------------------
    // Ford-Fulkerson: repeated DFS augmenting paths
    // ------------------------------------------------------------------
    public static Result fordFulkerson(M4FlowNetwork net, int source, int sink) {
        checkEndpoints(net, source, sink);
        if (source == sink) {
            return new Result(0, 0);
        }
        long maxFlow = 0;
        int augmentingPaths = 0;
        int[] seen = new int[net.nodeCount()];
        int stamp = 0;
        int pushed;
        do {
            stamp++;
            pushed = dfsAugment(net, source, sink, Integer.MAX_VALUE, seen, stamp);
            if (pushed > 0) {
                maxFlow += pushed;
                augmentingPaths++;
            }
        } while (pushed > 0);
        return new Result(maxFlow, augmentingPaths);
    }

    private static int dfsAugment(M4FlowNetwork net, int u, int sink, int f, int[] seen, int stamp) {
        if (u == sink) {
            return f;
        }
        seen[u] = stamp;
        for (M4FlowNetwork.Edge e : net.edgesFrom(u)) {
            if (e.residualCapacity() > 0 && seen[e.to] != stamp) {
                int pushed = dfsAugment(net, e.to, sink, Math.min(f, e.residualCapacity()), seen, stamp);
                if (pushed > 0) {
                    net.augment(e, pushed);
                    return pushed;
                }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // Edmonds-Karp: BFS (shortest) augmenting paths
    // ------------------------------------------------------------------
    public static Result edmondsKarp(M4FlowNetwork net, int source, int sink) {
        checkEndpoints(net, source, sink);
        if (source == sink) {
            return new Result(0, 0);
        }
        long maxFlow = 0;
        int phases = 0;
        int n = net.nodeCount();
        int[] prevNode = new int[n];
        int[] prevEdge = new int[n];

        while (true) {
            Arrays.fill(prevNode, -1);
            prevNode[source] = source;
            Queue<Integer> queue = new ArrayDeque<>();
            queue.add(source);

            while (!queue.isEmpty() && prevNode[sink] == -1) {
                int u = queue.poll();
                List<M4FlowNetwork.Edge> edges = net.edgesFrom(u);
                for (int i = 0; i < edges.size(); i++) {
                    M4FlowNetwork.Edge e = edges.get(i);
                    if (e.residualCapacity() > 0 && prevNode[e.to] == -1) {
                        prevNode[e.to] = u;
                        prevEdge[e.to] = i;
                        queue.add(e.to);
                    }
                }
            }
            if (prevNode[sink] == -1) {
                break;
            }

            int bottleneck = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = prevNode[v]) {
                bottleneck = Math.min(bottleneck,
                        net.edgesFrom(prevNode[v]).get(prevEdge[v]).residualCapacity());
            }
            for (int v = sink; v != source; v = prevNode[v]) {
                net.augment(net.edgesFrom(prevNode[v]).get(prevEdge[v]), bottleneck);
            }
            maxFlow += bottleneck;
            phases++;
        }
        return new Result(maxFlow, phases);
    }

    // ------------------------------------------------------------------
    // Dinic: BFS level graph + DFS blocking flow with current-arc pruning
    // ------------------------------------------------------------------
    public static Result dinic(M4FlowNetwork net, int source, int sink) {
        checkEndpoints(net, source, sink);
        if (source == sink) {
            return new Result(0, 0);
        }
        long maxFlow = 0;
        int phases = 0;
        int n = net.nodeCount();
        int[] level = new int[n];
        int[] next = new int[n];

        while (buildLevels(net, source, sink, level)) {
            phases++;
            Arrays.fill(next, 0);
            int pushed;
            while ((pushed = blockingDfs(net, source, sink, Integer.MAX_VALUE, level, next)) > 0) {
                maxFlow += pushed;
            }
        }
        return new Result(maxFlow, phases);
    }

    private static boolean buildLevels(M4FlowNetwork net, int source, int sink, int[] level) {
        Arrays.fill(level, -1);
        level[source] = 0;
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (M4FlowNetwork.Edge e : net.edgesFrom(u)) {
                if (e.residualCapacity() > 0 && level[e.to] < 0) {
                    level[e.to] = level[u] + 1;
                    queue.add(e.to);
                }
            }
        }
        return level[sink] >= 0;
    }

    private static int blockingDfs(M4FlowNetwork net, int u, int sink, int f, int[] level, int[] next) {
        if (u == sink) {
            return f;
        }
        List<M4FlowNetwork.Edge> edges = net.edgesFrom(u);
        for (; next[u] < edges.size(); next[u]++) {
            M4FlowNetwork.Edge e = edges.get(next[u]);
            if (e.residualCapacity() > 0 && level[e.to] == level[u] + 1) {
                int pushed = blockingDfs(net, e.to, sink, Math.min(f, e.residualCapacity()), level, next);
                if (pushed > 0) {
                    net.augment(e, pushed);
                    return pushed;
                }
            }
        }
        return 0;
    }

    private static void checkEndpoints(M4FlowNetwork net, int source, int sink) {
        if (net.nodeCount() > RECURSION_NODE_CAP) {
            throw new IllegalArgumentException("recursive max-flow implementation is limited to "
                    + RECURSION_NODE_CAP + " nodes");
        }
        if (source < 0 || source >= net.nodeCount() || sink < 0 || sink >= net.nodeCount()) {
            throw new IllegalArgumentException("source/sink out of range for network with "
                    + net.nodeCount() + " nodes: source=" + source + ", sink=" + sink);
        }
    }

    // ------------------------------------------------------------------
    // Validation helper (used by the self-test)
    // ------------------------------------------------------------------

    /**
     * Checks capacity constraints on every edge and flow conservation at every
     * node other than source and sink. The sum of flows over a node's adjacency
     * list equals its net outflow, because reverse edges carry negated flow.
     */
    public static boolean validateFlow(M4FlowNetwork net, int source, int sink) {
        for (int u = 0; u < net.nodeCount(); u++) {
            long balance = 0;
            for (M4FlowNetwork.Edge e : net.edgesFrom(u)) {
                if (e.flow() > e.capacity) {
                    return false;
                }
                if (e.capacity > 0 && e.flow() < 0) {
                    return false;
                }
                if (e.capacity == 0 && e.flow() > 0) {
                    return false;
                }
                if (e.rev < 0 || e.rev >= net.edgesFrom(e.to).size()
                        || net.edgesFrom(e.to).get(e.rev).flow() != -e.flow()) {
                    return false;
                }
                balance += e.flow();
            }
            if (u != source && u != sink && balance != 0) {
                return false;
            }
        }
        return true;
    }
}
