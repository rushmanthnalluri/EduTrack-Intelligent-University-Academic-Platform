package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.ConsoleArea;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Course;
import edutrack.model.Faculty;
import edutrack.modules.M4HopcroftKarp;
import edutrack.modules.M4Konig;
import edutrack.modules.M4MaxFlow;
import edutrack.modules.M4NetworkFlow;

/**
 * GUI panel for Module M4 - Network Flow for Resource Allocation.
 * (a) bipartite faculty-course canvas with Hopcroft-Karp matching and Konig cover,
 * (b) classroom allocation via Ford-Fulkerson / Edmonds-Karp with a JTable,
 * (c) Dinic vs Edmonds-Karp scaled benchmark with a bar chart.
 * All algorithm runs happen in SwingWorker threads via runAsync; the EDT only
 * touches Swing components.
 */
public class ResourceAllocationPanel extends ModulePanel {

    private final List<Faculty> faculty;
    private final List<Course> courses;
    private final List<List<Integer>> graph;

    private final M4BipartiteCanvas bipartiteCanvas = new M4BipartiteCanvas();
    private final JLabel matchingStats = small("Matching not computed yet.", GuiTheme.TEXT);
    private final JLabel konigStats = small(" ", GuiTheme.TEXT);
    private final JLabel unmatchedFacultyLabel = small("Run the matching to see unmatched", GuiTheme.MUTED);
    private final JLabel unmatchedCoursesLabel = small("faculty and courses.", GuiTheme.MUTED);
    private final JButton matchingButton = GuiTheme.primaryButton("Run Hopcroft-Karp");
    private final JButton konigButton = GuiTheme.secondaryButton("Show König cover");

    private M4HopcroftKarp.Result matching;
    private M4Konig.Cover cover;
    private boolean coverShown;

    private final DefaultTableModel allocationModel = new DefaultTableModel(
            new String[] { "Section", "Room", "Time slot" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel ffStats = body("Ford-Fulkerson (DFS): not run yet");
    private final JLabel ekAllocStats = body("Edmonds-Karp (BFS): not run yet");
    private final JLabel allocNote = body(" ");
    private final JButton allocationButton = GuiTheme.primaryButton("Run FF + Edmonds-Karp");

    private final M4BenchmarkCanvas benchmarkCanvas = new M4BenchmarkCanvas();
    private final JLabel dinicStats = body("Dinic: not run yet");
    private final JLabel ekScaledStats = body("Edmonds-Karp (scaled): not run yet");
    private final JButton benchmarkButton = GuiTheme.primaryButton("Run scaled benchmark (~2 s)");

    private final ConsoleArea console = new ConsoleArea(3);

    public ResourceAllocationPanel(DataStore dataStore) {
        super(dataStore);
        faculty = dataStore.faculty();
        courses = dataStore.courses();
        graph = M4NetworkFlow.facultyCourseGraph(dataStore);

        matchingButton.setActionCommand("m4-run-matching");
        konigButton.setActionCommand("m4-toggle-konig");
        allocationButton.setActionCommand("m4-run-allocation");
        benchmarkButton.setActionCommand("m4-run-benchmark");
        konigButton.setEnabled(false);

        matchingButton.addActionListener(e -> runMatching());
        konigButton.addActionListener(e -> toggleKonig());
        allocationButton.addActionListener(e -> runAllocation());
        benchmarkButton.addActionListener(e -> runBenchmark());

        String[] facultyNames = new String[faculty.size()];
        for (int i = 0; i < faculty.size(); i++) {
            facultyNames[i] = faculty.get(i).name;
        }
        String[] courseCodes = new String[courses.size()];
        for (int i = 0; i < courses.size(); i++) {
            courseCodes[i] = courses.get(i).code;
        }
        bipartiteCanvas.setGraph(facultyNames, courseCodes, graph);

        add(buildHeader(), BorderLayout.NORTH);

        JPanel columns = new JPanel(new BorderLayout(12, 0));
        columns.setOpaque(false);
        columns.add(buildMatchingCard(), BorderLayout.CENTER);
        JPanel rightColumn = new JPanel(new GridLayout(2, 1, 12, 12));
        rightColumn.setOpaque(false);
        rightColumn.setPreferredSize(new Dimension(470, 0));
        rightColumn.add(buildAllocationCard());
        rightColumn.add(buildBenchmarkCard());
        columns.add(rightColumn, BorderLayout.EAST);
        add(columns, BorderLayout.CENTER);

        add(card("Log", console), BorderLayout.SOUTH);

        console.appendLine("[M4] Panel ready: " + faculty.size() + " faculty, " + courses.size()
                + " courses, " + edgeCount() + " eligibility edges.");
        runMatching();
        runAllocation();
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------
    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Network Flow — Resource Allocation");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Hopcroft-Karp matching, Ford-Fulkerson / Edmonds-Karp / Dinic max flow,"
                + " and König's minimum vertex cover over the live dataset.");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        return header;
    }

    private JPanel buildMatchingCard() {
        // Compact stats strip above the canvas; the canvas gets the full card width.
        JPanel stats = new JPanel();
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        stats.setOpaque(false);
        matchingStats.setAlignmentX(Component.LEFT_ALIGNMENT);
        konigStats.setAlignmentX(Component.LEFT_ALIGNMENT);
        unmatchedFacultyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        unmatchedCoursesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.add(matchingStats);
        stats.add(konigStats);
        stats.add(Box.createVerticalStrut(2));
        stats.add(unmatchedFacultyLabel);
        stats.add(unmatchedCoursesLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(matchingButton);
        buttons.add(konigButton);

        JPanel body = new JPanel(new BorderLayout(6, 6));
        body.setOpaque(false);
        body.add(stats, BorderLayout.NORTH);
        body.add(bipartiteCanvas, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        return card("Bipartite matching — Hopcroft-Karp · König cover", body);
    }

    private JPanel buildAllocationCard() {
        JPanel stats = new JPanel();
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        stats.setOpaque(false);
        stats.add(ffStats);
        stats.add(ekAllocStats);
        stats.add(allocNote);

        JTable table = new JTable(allocationModel);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(allocationButton);

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setOpaque(false);
        body.add(stats, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        return card("Classroom allocation — Ford-Fulkerson vs Edmonds-Karp", body);
    }

    private JPanel buildBenchmarkCard() {
        JPanel stats = new JPanel();
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        stats.setOpaque(false);
        stats.add(dinicStats);
        stats.add(ekScaledStats);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(benchmarkButton);

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setOpaque(false);
        body.add(stats, BorderLayout.NORTH);
        body.add(benchmarkCanvas, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        return card("Dinic benchmark — scaled network vs Edmonds-Karp", body);
    }

    // ------------------------------------------------------------------
    // (a) Hopcroft-Karp matching + König cover
    // ------------------------------------------------------------------
    private static class MatchingOutcome {
        M4HopcroftKarp.Result result;
        double ms;
        M4Konig.Cover cover;    // non-null only when the cover view is active
        double coverMs;
    }

    private void runMatching() {
        matchingButton.setEnabled(false);
        matchingButton.setText("running…");
        konigButton.setEnabled(false);
        console.appendLine("[M4] Hopcroft-Karp: computing maximum matching…");
        final boolean recomputeCover = coverShown;
        runAsync(() -> {
            MatchingOutcome out = new MatchingOutcome();
            long start = System.nanoTime();
            out.result = M4HopcroftKarp.maxMatching(faculty.size(), courses.size(), graph);
            out.ms = (System.nanoTime() - start) / 1_000_000.0;
            if (recomputeCover) {
                start = System.nanoTime();
                out.cover = M4Konig.minVertexCover(faculty.size(), courses.size(), graph,
                        out.result.matchLeft, out.result.matchRight);
                out.coverMs = (System.nanoTime() - start) / 1_000_000.0;
            }
            return out;
        }, this::onMatchingDone);
    }

    private void onMatchingDone(MatchingOutcome out) {
        matching = out.result;
        bipartiteCanvas.setMatching(out.result.matchLeft, out.result.matchRight);
        matchingStats.setText("Maximum matching: " + out.result.size + " of " + faculty.size()
                + " faculty / " + courses.size() + " courses  ·  " + fmtMs(out.ms));

        StringBuilder uf = new StringBuilder();
        int ufCount = 0;
        for (int u = 0; u < faculty.size(); u++) {
            if (out.result.matchLeft[u] < 0) {
                if (ufCount++ > 0) {
                    uf.append(", ");
                }
                uf.append(faculty.get(u).name);
            }
        }
        StringBuilder uc = new StringBuilder();
        int ucCount = 0;
        for (int v = 0; v < courses.size(); v++) {
            if (out.result.matchRight[v] < 0) {
                if (ucCount++ > 0) {
                    uc.append(", ");
                }
                uc.append(courses.get(v).code);
            }
        }
        unmatchedFacultyLabel.setText("Unmatched faculty (" + ufCount + "): "
                + (ufCount == 0 ? "none" : uf));
        unmatchedCoursesLabel.setText("Unmatched courses (" + ucCount + "): "
                + (ucCount == 0 ? "none" : uc));

        konigButton.setEnabled(true);
        if (out.cover != null) {
            cover = out.cover;
            bipartiteCanvas.setCover(cover.leftInCover, cover.rightInCover);
            konigStats.setText(konigLine(cover, out.coverMs));
        } else {
            bipartiteCanvas.setCover(null, null);
            konigStats.setText(" ");
        }
        matchingButton.setEnabled(true);
        matchingButton.setText("Run Hopcroft-Karp");
        console.appendLine("[M4] Hopcroft-Karp: matching size " + out.result.size + " in "
                + fmtMs(out.ms) + ".");
    }

    private static class CoverOutcome {
        M4Konig.Cover cover;
        double ms;
    }

    private void toggleKonig() {
        if (matching == null) {
            return;
        }
        if (coverShown) {
            coverShown = false;
            bipartiteCanvas.setCover(null, null);
            konigStats.setText(" ");
            konigButton.setText("Show König cover");
            return;
        }
        coverShown = true;
        konigButton.setEnabled(false);
        final int[] ml = matching.matchLeft;
        final int[] mr = matching.matchRight;
        runAsync(() -> {
            CoverOutcome out = new CoverOutcome();
            long start = System.nanoTime();
            out.cover = M4Konig.minVertexCover(faculty.size(), courses.size(), graph, ml, mr);
            out.ms = (System.nanoTime() - start) / 1_000_000.0;
            return out;
        }, out -> {
            cover = out.cover;
            bipartiteCanvas.setCover(cover.leftInCover, cover.rightInCover);
            konigStats.setText(konigLine(cover, out.ms));
            konigButton.setEnabled(true);
            konigButton.setText("Hide König cover");
            console.appendLine("[M4] König cover: " + cover.size + " vertices (faculty "
                    + cover.leftSize + " + courses " + cover.rightSize + "); |cover| = |matching|.");
        });
    }

    private String konigLine(M4Konig.Cover c, double ms) {
        return "|cover| = " + c.size + " == |matching| (" + c.leftSize + " faculty + "
                + c.rightSize + " courses) · " + fmtMs(ms);
    }

    // ------------------------------------------------------------------
    // (b) Classroom allocation
    // ------------------------------------------------------------------
    private static class AllocationOutcome {
        M4MaxFlow.Result ff;
        double ffMs;
        M4MaxFlow.Result ek;
        double ekMs;
        List<M4NetworkFlow.AllocationRow> rows;
        int totalSections;
        int supply;
    }

    private void runAllocation() {
        allocationButton.setEnabled(false);
        allocationButton.setText("running…");
        console.appendLine("[M4] Allocation: running Ford-Fulkerson + Edmonds-Karp…");
        runAsync(() -> {
            AllocationOutcome out = new AllocationOutcome();
            List<String> rooms = dataStore.rooms();
            List<String> slots = dataStore.timeSlots();

            M4NetworkFlow.AllocationNetwork anFF = M4NetworkFlow.buildAllocationNetwork(dataStore);
            long start = System.nanoTime();
            out.ff = M4MaxFlow.fordFulkerson(anFF.net, anFF.source, anFF.sink);
            out.ffMs = (System.nanoTime() - start) / 1_000_000.0;
            out.rows = M4NetworkFlow.decomposeAllocation(anFF, courses, rooms, slots);

            M4NetworkFlow.AllocationNetwork anEK = M4NetworkFlow.buildAllocationNetwork(dataStore);
            start = System.nanoTime();
            out.ek = M4MaxFlow.edmondsKarp(anEK.net, anEK.source, anEK.sink);
            out.ekMs = (System.nanoTime() - start) / 1_000_000.0;

            int total = 0;
            for (int s : anFF.sections) {
                total += s;
            }
            out.totalSections = total;
            out.supply = rooms.size() * slots.size();
            return out;
        }, this::onAllocationDone);
    }

    private void onAllocationDone(AllocationOutcome out) {
        ffStats.setText("Ford-Fulkerson (DFS): flow " + out.ff.maxFlow + "/" + out.totalSections
                + " sections · " + out.ff.phases + " paths · " + fmtMs(out.ffMs));
        ekAllocStats.setText("Edmonds-Karp (BFS): flow " + out.ek.maxFlow + " · " + out.ek.phases
                + " phases · " + fmtMs(out.ekMs));
        if (out.ff.maxFlow == out.ek.maxFlow) {
            allocNote.setText("OK: both agree — " + out.ff.maxFlow + "/" + out.totalSections
                    + " sections scheduled (supply " + out.supply + ").");
            allocNote.setForeground(GuiTheme.SUCCESS);
        } else {
            allocNote.setText("MISMATCH: FF=" + out.ff.maxFlow + " vs EK=" + out.ek.maxFlow);
            allocNote.setForeground(GuiTheme.ERROR);
        }

        allocationModel.setRowCount(0);
        for (M4NetworkFlow.AllocationRow r : out.rows) {
            allocationModel.addRow(new Object[] {
                    r.courseCode + " · section " + r.section,
                    r.room == null ? "(unresolved)" : r.room,
                    r.slot == null ? "-" : r.slot });
        }
        allocationButton.setEnabled(true);
        allocationButton.setText("Run FF + Edmonds-Karp");
        console.appendLine("[M4] Allocation: " + out.rows.size() + " section(s) assigned to rooms, max flow "
                + out.ff.maxFlow + ".");
    }

    // ------------------------------------------------------------------
    // (c) Dinic benchmark on the scaled network
    // ------------------------------------------------------------------
    private static class BenchmarkOutcome {
        M4MaxFlow.Result dinic;
        double dinicMs;
        M4MaxFlow.Result ek;
        double ekMs;
        int nodes;
        int forwardEdges;
    }

    private void runBenchmark() {
        benchmarkButton.setEnabled(false);
        benchmarkButton.setText("running…");
        dinicStats.setText("Dinic: running on scaled network…");
        ekScaledStats.setText("Edmonds-Karp (scaled): queued…");
        console.appendLine("[M4] Scaled benchmark: cloning courses/rooms x"
                + M4NetworkFlow.SCALE_CLONE_FACTOR + ", running Dinic then Edmonds-Karp…");
        runAsync(() -> {
            BenchmarkOutcome out = new BenchmarkOutcome();
            M4NetworkFlow.ScaledNetwork snDinic = M4NetworkFlow.buildScaledNetwork(dataStore,
                    M4NetworkFlow.SCALE_CLONE_FACTOR, M4NetworkFlow.SCALE_SEED);
            long start = System.nanoTime();
            out.dinic = M4MaxFlow.dinic(snDinic.net, snDinic.source, snDinic.sink);
            out.dinicMs = (System.nanoTime() - start) / 1_000_000.0;

            M4NetworkFlow.ScaledNetwork snEK = M4NetworkFlow.buildScaledNetwork(dataStore,
                    M4NetworkFlow.SCALE_CLONE_FACTOR, M4NetworkFlow.SCALE_SEED);
            start = System.nanoTime();
            out.ek = M4MaxFlow.edmondsKarp(snEK.net, snEK.source, snEK.sink);
            out.ekMs = (System.nanoTime() - start) / 1_000_000.0;
            out.nodes = snDinic.net.nodeCount();
            out.forwardEdges = snDinic.forwardEdges;
            return out;
        }, this::onBenchmarkDone);
    }

    private void onBenchmarkDone(BenchmarkOutcome out) {
        dinicStats.setText("Dinic: flow " + out.dinic.maxFlow + " · " + out.dinic.phases
                + " level-graph phases · " + fmtMs(out.dinicMs));
        ekScaledStats.setText("Edmonds-Karp (scaled): flow " + out.ek.maxFlow + " · "
                + out.ek.phases + " phases · " + fmtMs(out.ekMs));
        benchmarkCanvas.setResults(out.dinicMs, out.dinic.phases, out.ekMs, out.ek.phases,
                out.dinic.maxFlow, out.nodes, out.forwardEdges);
        String verdict = out.dinic.maxFlow == out.ek.maxFlow
                ? "agree on max flow " + out.dinic.maxFlow
                : "MISMATCH (" + out.dinic.maxFlow + " vs " + out.ek.maxFlow + ")";
        console.appendLine("[M4] Benchmark: Dinic " + fmtMs(out.dinicMs) + " vs Edmonds-Karp "
                + fmtMs(out.ekMs) + " — both " + verdict + ".");
        benchmarkButton.setEnabled(true);
        benchmarkButton.setText("Run scaled benchmark (~2 s)");
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------
    private int edgeCount() {
        int count = 0;
        for (List<Integer> list : graph) {
            count += list.size();
        }
        return count;
    }

    private static JLabel body(String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY);
        label.setForeground(GuiTheme.TEXT);
        return label;
    }

    private static JLabel small(String text, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY.deriveFont(12f));
        label.setForeground(color);
        return label;
    }

    private static String fmtMs(double ms) {
        return String.format(Locale.US, "%.3f ms", ms);
    }
}
