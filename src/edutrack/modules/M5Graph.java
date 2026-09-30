package edutrack.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edutrack.data.DataStore;
import edutrack.model.Course;
import edutrack.model.Student;

public class M5Graph {

    public final int n;
    private final boolean[][] adj;
    private final String[] labels;

    public M5Graph(int n) {
        if (n < 0) throw new IllegalArgumentException("node count must be non-negative");
        this.n = n;
        this.adj = new boolean[n][n];
        this.labels = new String[n];
        for (int i = 0; i < n; i++) {
            labels[i] = "v" + i;
        }
    }

    public void addEdge(int u, int v) {
        checkVertex(u);
        checkVertex(v);
        if (u != v) {
            adj[u][v] = true;
            adj[v][u] = true;
        }
    }

    public boolean hasEdge(int u, int v) {
        checkVertex(u);
        checkVertex(v);
        return adj[u][v];
    }

    public String label(int v) {
        checkVertex(v);
        return labels[v];
    }

    public void setLabel(int v, String label) {
        checkVertex(v);
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        labels[v] = label;
    }

    public int edgeCount() {
        int edges = 0;
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (adj[u][v]) {
                    edges++;
                }
            }
        }
        return edges;
    }

    public int degree(int v) {
        checkVertex(v);
        int d = 0;
        for (int u = 0; u < n; u++) {
            if (adj[v][u]) {
                d++;
            }
        }
        return d;
    }

    public List<Integer> neighbors(int v) {
        checkVertex(v);
        List<Integer> out = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            if (adj[v][u]) {
                out.add(u);
            }
        }
        return out;
    }

    public M5Graph complement() {
        M5Graph g = new M5Graph(n);
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (!adj[u][v]) {
                    g.addEdge(u, v);
                }
            }
        }
        System.arraycopy(labels, 0, g.labels, 0, n);
        return g;
    }

    public M5Graph inducedSubgraph(int[] vertices) {
        if (vertices == null) throw new IllegalArgumentException("vertices must not be null");
        boolean[] seen = new boolean[n];
        for (int v : vertices) {
            checkVertex(v);
            if (seen[v]) throw new IllegalArgumentException("induced subgraph vertices must be distinct");
            seen[v] = true;
        }
        M5Graph g = new M5Graph(vertices.length);
        for (int i = 0; i < vertices.length; i++) {
            g.labels[i] = labels[vertices[i]];
            for (int j = i + 1; j < vertices.length; j++) {
                if (adj[vertices[i]][vertices[j]]) {
                    g.addEdge(i, j);
                }
            }
        }
        return g;
    }

    private void checkVertex(int v) {
        if (v < 0 || v >= n) throw new IllegalArgumentException("vertex out of range: " + v);
    }

    public int[] degreeOrder() {
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int byDegree = Integer.compare(degree(b), degree(a));
            return byDegree != 0 ? byDegree : labels[a].compareTo(labels[b]);
        });
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = order[i];
        }
        return out;
    }

    public static M5Graph courseConflictGraph(DataStore ds) {
        if (ds == null) throw new IllegalArgumentException("data store must not be null");
        List<Course> courses = ds.courses();
        M5Graph g = new M5Graph(courses.size());
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < courses.size(); i++) {
            index.put(courses.get(i).code, i);
            g.labels[i] = courses.get(i).code;
        }
        for (Student s : ds.students()) {
            List<String> enrolled = s.enrolledCourses;
            for (int a = 0; a < enrolled.size(); a++) {
                Integer u = index.get(enrolled.get(a));
                if (u == null) {
                    continue;
                }
                for (int b = a + 1; b < enrolled.size(); b++) {
                    Integer v = index.get(enrolled.get(b));
                    if (v != null) {
                        g.addEdge(u, v);
                    }
                }
            }
        }
        return g;
    }
}
