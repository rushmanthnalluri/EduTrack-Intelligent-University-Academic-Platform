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
        Edge forward = new Edge(to, adj.get(to).size(), capacity);
        Edge backward = new Edge(from, adj.get(from).size(), 0);
        adj.get(from).add(forward);
        adj.get(to).add(backward);
    }

    public List<Edge> edgesFrom(int u) {
        return adj.get(u);
    }

    /** Pushes {@code amount} units of flow along edge e (negative amounts cancel flow). */
    public void augment(Edge e, int amount) {
        e.flow += amount;
        adj.get(e.to).get(e.rev).flow -= amount;
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
