package edutrack.modules;

import java.util.ArrayList;
import java.util.List;

/**
 * Adjacency-list flow network for Module M4.
 * Every directed edge stores its capacity and current flow, and carries the
 * index of its reverse edge so residual capacities can be updated in O(1).
 */
public class M4FlowNetwork {

    public static class Edge {
        public final int to;
        public final int rev;      // index of the reverse edge in edgesFrom(to)
        public final int capacity;
        public int flow;

        Edge(int to, int rev, int capacity) {
            this.to = to;
            this.rev = rev;
            this.capacity = capacity;
            this.flow = 0;
        }

        public int residualCapacity() {
            return capacity - flow;
        }
    }

    private final List<List<Edge>> adj;

    public M4FlowNetwork(int nodeCount) {
        if (nodeCount < 0) throw new IllegalArgumentException("node count must be non-negative");
        adj = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            adj.add(new ArrayList<>());
        }
    }

    public int nodeCount() {
        return adj.size();
    }

    /** Adds a forward edge with the given capacity plus a zero-capacity reverse edge. */
    public void addEdge(int from, int to, int capacity) {
        if (from < 0 || from >= nodeCount() || to < 0 || to >= nodeCount()) {
            throw new IllegalArgumentException("edge endpoint out of range");
        }
        if (from == to) {
            throw new IllegalArgumentException("self-loops are not supported");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
        Edge forward = new Edge(to, adj.get(to).size(), capacity);
        Edge backward = new Edge(from, adj.get(from).size(), 0);
        adj.get(from).add(forward);
        adj.get(to).add(backward);
    }

    public List<Edge> edgesFrom(int u) {
        checkNode(u);
        return java.util.Collections.unmodifiableList(adj.get(u));
    }

    /** Pushes {@code amount} units of flow along edge e (negative amounts cancel flow). */
    public void augment(Edge e, int amount) {
        if (e == null) throw new IllegalArgumentException("edge must not be null");
        if (e.to < 0 || e.to >= nodeCount() || e.rev < 0 || e.rev >= adj.get(e.to).size()) {
            throw new IllegalArgumentException("edge is not valid for this network");
        }
        Edge reverse = adj.get(e.to).get(e.rev);
        int nextFlow = e.flow + amount;
        if (nextFlow > e.capacity || nextFlow < -reverse.capacity) {
            throw new IllegalArgumentException("flow update exceeds edge capacity");
        }
        e.flow = nextFlow;
        reverse.flow -= amount;
    }

    private void checkNode(int u) {
        if (u < 0 || u >= nodeCount()) throw new IllegalArgumentException("node out of range: " + u);
    }

    /** Resets the flow on every edge to zero, so the same network can be reused for another run. */
    public void resetFlow() {
        for (List<Edge> edges : adj) {
            for (Edge e : edges) {
                e.flow = 0;
            }
        }
    }

    /** Counts edges that were added with a positive capacity (i.e. not reverse edges). */
    public int forwardEdgeCount() {
        int count = 0;
        for (List<Edge> edges : adj) {
            for (Edge e : edges) {
                if (e.capacity > 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
