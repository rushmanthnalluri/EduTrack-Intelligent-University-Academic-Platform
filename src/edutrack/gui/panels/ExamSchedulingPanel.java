package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.ConsoleArea;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.modules.M5DPLLSolver;
import edutrack.modules.M5Graph;
import edutrack.modules.M5NPCompleteness;
import edutrack.modules.M5Reductions;
import edutrack.modules.M5VertexCoverApprox;

public class ExamSchedulingPanel extends ModulePanel {

    private static final int CONTROL_COLUMN_WIDTH = 330;
    private static final int CONSOLE_COLUMNS = 34;

    private final M5Graph graph;
    private final int[] degreeOrder;
    private final M5GraphCanvas canvas;
    private final JLabel statsLabel;

    private final JComboBox<String> subsetCombo;
    private final JSpinner slotsSpinner;
    private final JButton solveButton;
    private final JButton minSlotsButton;
    private final JLabel schedStatus;
    private final DefaultTableModel scheduleModel;
    private final M5TimetableCanvas timetableCanvas;
    private final JLabel timetableCaption;

    private final JButton reductionButton;
    private final ConsoleArea reductionConsole;

    private final JButton vcButton;
    private final JButton vcExactButton;
    private final ConsoleArea vcConsole;

    public ExamSchedulingPanel(DataStore dataStore) {
        super(dataStore);

        graph = M5Graph.courseConflictGraph(dataStore);
        degreeOrder = graph.degreeOrder();
        M5Reductions.CliqueSearch topClique = M5Reductions.maxClique(graph);
        int minDeg = Integer.MAX_VALUE;
        int maxDeg = 0;
        for (int v = 0; v < graph.n; v++) {
            minDeg = Math.min(minDeg, graph.degree(v));
            maxDeg = Math.max(maxDeg, graph.degree(v));
        }

        canvas = new M5GraphCanvas();
        canvas.setGraph(graph);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Exam Scheduling — NP-Completeness & Approximation");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("DPLL SAT solving · 3-SAT→CLIQUE→INDEPENDENT-SET→VERTEX-COVER "
                + "reductions · maximal-matching 2-approximation");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsLabel = new JLabel(graph.n + " courses · " + graph.edgeCount() + " conflict edges"
                + " · degree range " + minDeg + "–" + maxDeg
                + " · maximum clique " + topClique.clique.length + " courses");
        statsLabel.setFont(GuiTheme.BODY_BOLD);
        statsLabel.setForeground(GuiTheme.ACCENT_DARK);
        statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(4));
        header.add(statsLabel);

        subsetCombo = new JComboBox<>(new String[] {
            "Cross-program mix (10)",
            "Top degree (8)",
            "Top degree (10)",
            "Top degree (12)"
        });
        slotsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 12, 1));
        solveButton = GuiTheme.primaryButton("Solve with DPLL");
        minSlotsButton = GuiTheme.secondaryButton("Find minimum slots");
        schedStatus = new JLabel();
        setStatus(GuiTheme.MUTED, "Pick a subset and slot count, then solve.");
        scheduleModel = new DefaultTableModel(new String[] { "Slot", "Courses" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        timetableCanvas = new M5TimetableCanvas();
        timetableCaption = new JLabel("Timetable: —");
        timetableCaption.setFont(GuiTheme.BODY);
        timetableCaption.setForeground(GuiTheme.MUTED);

        reductionButton = GuiTheme.primaryButton("Run reduction walkthrough");
        reductionConsole = new ConsoleArea(9);
        narrowConsole(reductionConsole);

        vcButton = GuiTheme.primaryButton("Run 2-approximation (full graph)");
        vcExactButton = GuiTheme.secondaryButton("Exact check on mix subgraph");
        vcConsole = new ConsoleArea(7);
        narrowConsole(vcConsole);

        solveButton.addActionListener(e -> runSolve(false));
        minSlotsButton.addActionListener(e -> runSolve(true));
        reductionButton.addActionListener(e -> runReductionWalkthrough());
        vcButton.addActionListener(e -> runVertexCoverFull());
        vcExactButton.addActionListener(e -> runVertexCoverExact());

        add(header, BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
    }

    private static void narrowConsole(ConsoleArea console) {
        for (Component c : console.getComponents()) {
            if (c instanceof JScrollPane
                    && ((JScrollPane) c).getViewport().getView() instanceof JTextArea) {
                ((JTextArea) ((JScrollPane) c).getViewport().getView()).setColumns(CONSOLE_COLUMNS);
            }
        }
    }

    private static class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 120;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private void setStatus(Color color, String text) {
        schedStatus.setForeground(color);
        schedStatus.setText("<html><body style='width:250px'>" + text + "</body></html>");
    }

    private Component buildBody() {
        JLabel legend = new JLabel("Node colour = department · amber ring = vertex-cover nodes · "
                + "after a SAT solve, colour = assigned exam slot (hover nodes for details)");
        legend.setFont(GuiTheme.BODY);
        legend.setForeground(GuiTheme.MUTED);
        legend.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        JPanel leftInner = new JPanel(new BorderLayout());
        leftInner.setOpaque(false);
        leftInner.add(canvas, BorderLayout.CENTER);
        leftInner.add(legend, BorderLayout.SOUTH);
        JPanel leftCard = card("Course-Conflict Graph (circular layout, grouped by department)", leftInner);
        leftCard.setMinimumSize(new Dimension(420, 320));

        JPanel column = new WidthTrackingPanel();
        column.setOpaque(false);
        column.add(buildSchedulingCard());
        column.add(Box.createVerticalStrut(12));
        column.add(buildReductionCard());
        column.add(Box.createVerticalStrut(12));
        column.add(buildVertexCoverCard());
        column.add(Box.createVerticalGlue());
        JScrollPane rightScroll = new JScrollPane(column);
        rightScroll.setBorder(BorderFactory.createEmptyBorder());
        rightScroll.getViewport().setBackground(GuiTheme.BG);
        rightScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.setPreferredSize(new Dimension(CONTROL_COLUMN_WIDTH, 0));
        rightScroll.setMinimumSize(new Dimension(300, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCard, rightScroll);
        split.setResizeWeight(0.75);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        return split;
    }

    private JPanel buildSchedulingCard() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JPanel subsetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        subsetRow.setOpaque(false);
        subsetRow.add(new JLabel("Course subset:"));
        subsetRow.add(subsetCombo);

        JPanel slotRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        slotRow.setOpaque(false);
        slotRow.add(new JLabel("Exam slots:"));
        slotsSpinner.setPreferredSize(new Dimension(56, 26));
        slotRow.add(slotsSpinner);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttonRow.setOpaque(false);
        buttonRow.add(solveButton);
        buttonRow.add(minSlotsButton);

        schedStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        schedStatus.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JTable table = new JTable(scheduleModel);
        table.setFont(GuiTheme.BODY);
        table.setRowHeight(24);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(280, 110));
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        subsetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        slotRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        timetableCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
        timetableCaption.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
        timetableCanvas.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(subsetRow);
        inner.add(slotRow);
        inner.add(buttonRow);
        inner.add(schedStatus);
        inner.add(tableScroll);
        inner.add(timetableCaption);
        inner.add(timetableCanvas);
        JPanel c = card("DPLL Exam Scheduling (SAT encoding)", inner);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private JPanel buildReductionCard() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.setOpaque(false);
        row.add(reductionButton);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        reductionConsole.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(row);
        inner.add(reductionConsole);
        JPanel c = card("Reduction Walkthrough", inner);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private JPanel buildVertexCoverCard() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row1.setOpaque(false);
        row1.add(vcButton);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row2.setOpaque(false);
        row2.add(vcExactButton);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        vcConsole.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(row1);
        inner.add(row2);
        inner.add(vcConsole);
        JPanel c = card("Vertex-Cover 2-Approximation", inner);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private int[] currentSubset() {
        int choice = subsetCombo.getSelectedIndex();
        if (choice == 1) {
            return java.util.Arrays.copyOf(degreeOrder, Math.min(8, graph.n));
        }
        if (choice == 2) {
            return java.util.Arrays.copyOf(degreeOrder, Math.min(10, graph.n));
        }
        if (choice == 3) {
            return java.util.Arrays.copyOf(degreeOrder, Math.min(12, graph.n));
        }
        return M5NPCompleteness.resolveCodes(graph, M5NPCompleteness.CROSS_PROGRAM_MIX);
    }

    private static class SchedRun {
        M5Graph sub;
        int[] subset;
        int cliqueSize;
        int slots;
        M5Reductions.SchedEncoding enc;
        M5DPLLSolver.Result result;
        int[] slotOf;
        boolean verified;
        long encodeMs;
        long solveMs;
        long cliqueMs;
    }

    private void runSolve(boolean findMinimum) {
        int[] subset = currentSubset();
        int requested = (Integer) slotsSpinner.getValue();
        setBusy(true);
        setStatus(GuiTheme.MUTED, "Solving ...");
        runAsync(() -> {
            M5Graph sub = graph.inducedSubgraph(subset);
            long t0 = System.nanoTime();
            M5Reductions.CliqueSearch mc = M5Reductions.maxClique(sub);
            long cliqueMs = (System.nanoTime() - t0) / 1_000_000;
            int slots = findMinimum ? Math.max(1, mc.clique.length) : requested;
            if (findMinimum) {
                int min = M5NPCompleteness.findMinSlots(sub, mc.clique.length);
                slots = min < 0 ? sub.n : min;
            }
            SchedRun run = solveForSlots(sub, slots);
            run.subset = subset;
            run.cliqueSize = mc.clique.length;
            run.cliqueMs = cliqueMs;
            return run;
        }, run -> {
            setBusy(false);
            showSchedule(run, findMinimum);
        });
    }

    private SchedRun solveForSlots(M5Graph sub, int slots) {
        SchedRun run = new SchedRun();
        run.sub = sub;
        run.slots = slots;
        if (slots < 1) {
            run.result = null;
            return run;
        }
        long t0 = System.nanoTime();
        run.enc = M5Reductions.examSchedulingCNF(sub, slots);
        run.encodeMs = (System.nanoTime() - t0) / 1_000_000;
        t0 = System.nanoTime();
        run.result = M5DPLLSolver.solve(run.enc.numVars, run.enc.clauses);
        run.solveMs = (System.nanoTime() - t0) / 1_000_000;
        if (run.result.status == M5DPLLSolver.Status.SAT) {
            run.slotOf = M5NPCompleteness.decodeSchedule(sub, run.enc, run.result.assignment);
            run.verified = M5NPCompleteness.verifySchedule(sub, run.enc, run.result.assignment, run.slotOf);
        }
        return run;
    }

    private void showSchedule(SchedRun run, boolean foundMinimum) {
        scheduleModel.setRowCount(0);
        String prefix = foundMinimum ? "Minimum-slot search: " : "";
        if (run.slots < run.cliqueSize) {
            setStatus(GuiTheme.ERROR, prefix + "UNSAT - " + run.slots
                    + " slot(s) cannot host the conflict clique of size " + run.cliqueSize
                    + " (pigeonhole principle).");
            canvas.setSchedule(null, 0);
            timetableCanvas.showPlaceholder("UNSAT - no feasible timetable.");
            timetableCaption.setText("Timetable: —");
            return;
        }
        if (run.result != null && run.result.status == M5DPLLSolver.Status.SAT && run.verified) {
            Map<Integer, List<String>> slotToCourses = new TreeMap<>();
            for (int s = 0; s < run.slots; s++) {
                StringBuilder courses = new StringBuilder();
                for (int c = 0; c < run.sub.n; c++) {
                    if (run.slotOf[c] == s) {
                        if (courses.length() > 0) {
                            courses.append(", ");
                        }
                        courses.append(run.sub.labels[c]);
                        slotToCourses.computeIfAbsent(s, k -> new ArrayList<>())
                                .add(run.sub.labels[c]);
                    }
                }
                scheduleModel.addRow(new Object[] { "Slot " + (s + 1),
                        courses.length() == 0 ? "-" : courses.toString() });
            }
            int[] fullSlotOf = new int[graph.n];
            java.util.Arrays.fill(fullSlotOf, -1);
            for (int c = 0; c < run.sub.n; c++) {
                fullSlotOf[run.subset[c]] = run.slotOf[c];
            }
            canvas.setSchedule(fullSlotOf, run.slots);
            timetableCanvas.setSchedule(slotToCourses);
            timetableCaption.setText("Timetable: " + run.sub.n + " courses · " + run.slots
                    + " exam slots (MON–FRI × 09/11/14)");
            setStatus(GuiTheme.SUCCESS, prefix + "SAT - " + run.slots + " slots suffice for "
                    + run.sub.n + " courses (clique bound " + run.cliqueSize + "; CNF "
                    + run.enc.numVars + " vars / " + run.enc.clauses.size() + " clauses; decisions "
                    + run.result.decisions + "; " + run.solveMs + " ms). Schedule verified.");
        } else if (run.result != null && run.result.status == M5DPLLSolver.Status.UNKNOWN) {
            canvas.setSchedule(null, 0);
            timetableCanvas.showPlaceholder("Undecided - solver budget exhausted.");
            timetableCaption.setText("Timetable: —");
            setStatus(GuiTheme.ERROR, prefix + "UNDECIDED - DPLL decision budget exhausted.");
        } else {
            canvas.setSchedule(null, 0);
            timetableCanvas.showPlaceholder("UNSAT - no feasible timetable.");
            timetableCaption.setText("Timetable: —");
            setStatus(GuiTheme.ERROR, prefix + "UNSAT - " + run.slots
                    + " slot(s) infeasible for these " + run.sub.n
                    + " courses (clique bound " + run.cliqueSize + ").");
        }
    }

    private void runReductionWalkthrough() {
        setBusy(true);
        runAsync(() -> {
            StringBuilder sb = new StringBuilder();
            List<int[]> formula = M5NPCompleteness.builtinThreeSat();
            int vars = M5NPCompleteness.BUILTIN_VARS;
            sb.append("3-SAT: ").append(vars).append(" vars, ")
                    .append(formula.size()).append(" clauses\n");
            for (int i = 0; i < formula.size(); i++) {
                sb.append("C").append(i).append(" = (");
                int[] clause = formula.get(i);
                for (int j = 0; j < clause.length; j++) {
                    if (j > 0) {
                        sb.append(" v ");
                    }
                    if (clause[j] < 0) {
                        sb.append('~');
                    }
                    sb.append('x').append(Math.abs(clause[j]));
                }
                sb.append(")\n");
            }
            long t0 = System.nanoTime();
            M5DPLLSolver.Result sat = M5DPLLSolver.solve(vars, formula);
            long dpllMs = (System.nanoTime() - t0) / 1_000_000;
            sb.append("DPLL: ").append(sat.status).append(" (")
                    .append(sat.decisions).append(" dec, ").append(dpllMs).append(" ms)\n");

            t0 = System.nanoTime();
            M5Reductions.GadgetReduction red = M5Reductions.threeSatToClique(formula, vars);
            long redMs = (System.nanoTime() - t0) / 1_000_000;
            sb.append("Gadget: ").append(red.graph.n).append(" nodes, ")
                    .append(red.graph.edgeCount()).append(" edges (").append(redMs).append(" ms)\n");
            t0 = System.nanoTime();
            M5Reductions.CliqueSearch cs = M5Reductions.cliqueOfSize(red.graph, formula.size());
            long cliqueMs = (System.nanoTime() - t0) / 1_000_000;
            boolean cliqueFound = cs.clique.length == formula.size();
            sb.append("Clique m=").append(formula.size()).append(": ")
                    .append(cliqueFound ? "FOUND" : "absent")
                    .append(" (").append(cliqueMs).append(" ms)\n");
            if (cliqueFound) {
                boolean[] assignment = M5Reductions.cliqueToAssignment(red, cs.clique);
                sb.append("Assign: ");
                for (int v = 1; v <= vars; v++) {
                    sb.append("x").append(v).append('=').append(assignment[v] ? 'T' : 'F')
                            .append(v < vars ? " " : "");
                }
                sb.append("\nSatisfies: ")
                        .append(M5DPLLSolver.satisfies(formula, assignment) ? "VERIFIED" : "FAILED")
                        .append("\n");
            }
            sb.append("SAT<=>clique: ")
                    .append((sat.status == M5DPLLSolver.Status.SAT) == cliqueFound
                            ? "VERIFIED" : "MISMATCH")
                    .append("\n\n");

            M5Graph sub = graph.inducedSubgraph(
                    M5NPCompleteness.resolveCodes(graph, M5NPCompleteness.CROSS_PROGRAM_MIX));
            t0 = System.nanoTime();
            M5Reductions.CliqueSearch mc = M5Reductions.maxClique(sub);
            long mcMs = (System.nanoTime() - t0) / 1_000_000;
            int k = mc.clique.length;
            sb.append("Chain: mix subgraph\n");
            sb.append("G: ").append(sub.n).append(" vertices, ")
                    .append(sub.edgeCount()).append(" edges\n");
            sb.append("Max clique k=").append(k).append(" [").append(mcMs).append(" ms]\n");
            appendWrappedLabels(sb, sub, mc.clique);
            M5Graph comp = sub.complement();
            sb.append("~G: ").append(comp.edgeCount()).append(" edges\n");
            boolean[] indep = new boolean[comp.n];
            for (int v : mc.clique) {
                indep[v] = true;
            }
            boolean[] cover = new boolean[comp.n];
            for (int v = 0; v < comp.n; v++) {
                cover[v] = !indep[v];
            }
            sb.append("Indep set in ~G: ")
                    .append(M5VertexCoverApprox.isIndependentSet(comp, indep) ? "VERIFIED" : "FAILED")
                    .append("\n");
            sb.append("Cover |V|-k=").append(comp.n - k).append(": ")
                    .append(M5VertexCoverApprox.isVertexCover(comp, cover) ? "VERIFIED" : "FAILED")
                    .append("\n");
            t0 = System.nanoTime();
            int alpha = M5VertexCoverApprox.bruteForceMaxIndependentSet(comp, null);
            int tau = M5VertexCoverApprox.bruteForceMinVertexCover(comp, null);
            long bruteMs = (System.nanoTime() - t0) / 1_000_000;
            sb.append("Brute: alpha=").append(alpha).append(", tau=").append(tau)
                    .append(" [").append(bruteMs).append(" ms]\n");
            sb.append("k==alpha: ").append(k == alpha ? "VERIFIED" : "FAILED").append("\n");
            sb.append("alpha+tau==|V|: ")
                    .append(alpha + tau == comp.n ? "VERIFIED" : "FAILED").append("\n");
            return sb.toString();
        }, text -> {
            setBusy(false);
            reductionConsole.setText(text);
        });
    }

    private static void appendWrappedLabels(StringBuilder sb, M5Graph g, int[] vertices) {
        StringBuilder line = new StringBuilder("  ");
        for (int i = 0; i < vertices.length; i++) {
            String label = g.labels[vertices[i]] + (i < vertices.length - 1 ? "," : "");
            if (line.length() + label.length() + 1 > 34 && line.length() > 2) {
                sb.append(line).append("\n");
                line = new StringBuilder("  ");
            }
            line.append(label).append(' ');
        }
        sb.append(line.toString().trim()).append("\n");
    }

    private void runVertexCoverFull() {
        setBusy(true);
        runAsync(() -> {
            long t0 = System.nanoTime();
            M5VertexCoverApprox.ApproxResult r = M5VertexCoverApprox.approximate(graph);
            long approxMs = (System.nanoTime() - t0) / 1_000_000;
            t0 = System.nanoTime();
            int opt = M5VertexCoverApprox.bruteForceMinVertexCover(graph, null);
            long bruteMs = (System.nanoTime() - t0) / 1_000_000;
            boolean valid = M5VertexCoverApprox.isVertexCover(graph, r.cover);
            double ratio = opt == 0 ? 1.0 : (double) r.coverSize / opt;
            StringBuilder sb = new StringBuilder();
            sb.append("Full graph: ").append(graph.n).append(" courses, ")
                    .append(graph.edgeCount()).append(" edges\n");
            sb.append("Matching: ").append(r.matchingSize).append(" edges (")
                    .append(approxMs).append(" ms)\n");
            sb.append("Approx cover: ").append(r.coverSize)
                    .append(" (lb: ").append(r.matchingSize).append(")\n");
            sb.append("Cover valid: ").append(valid ? "VERIFIED" : "FAILED").append("\n");
            sb.append("Brute-force opt: ").append(opt).append(" (").append(bruteMs)
                    .append(" ms)\n");
            sb.append("Ratio ").append(String.format("%.3f", ratio))
                    .append(ratio <= 2.0 ? "  VERIFIED <= 2" : "  VIOLATED").append("\n");
            sb.append("Cover = courses to move to\n");
            sb.append("clear all exam conflicts.\n");
            return new Object[] { sb.toString(), r.cover };
        }, out -> {
            setBusy(false);
            vcConsole.setText((String) out[0]);
            canvas.setCover((boolean[]) out[1]);
        });
    }

    private void runVertexCoverExact() {
        setBusy(true);
        runAsync(() -> {
            int[] subset = M5NPCompleteness.resolveCodes(graph, M5NPCompleteness.CROSS_PROGRAM_MIX);
            M5Graph sub = graph.inducedSubgraph(subset);
            long t0 = System.nanoTime();
            M5VertexCoverApprox.ApproxResult r = M5VertexCoverApprox.approximate(sub);
            long approxMs = (System.nanoTime() - t0) / 1_000_000;
            t0 = System.nanoTime();
            int opt = M5VertexCoverApprox.bruteForceMinVertexCover(sub, null);
            long bruteMs = (System.nanoTime() - t0) / 1_000_000;
            boolean valid = M5VertexCoverApprox.isVertexCover(sub, r.cover);
            double ratio = opt == 0 ? 1.0 : (double) r.coverSize / opt;
            boolean[] fullCover = new boolean[graph.n];
            for (int v = 0; v < sub.n; v++) {
                if (r.cover[v]) {
                    fullCover[subset[v]] = true;
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Mix subgraph: ").append(sub.n).append(" courses, ")
                    .append(sub.edgeCount()).append(" edges\n");
            sb.append("Approx cover: ").append(r.coverSize).append(" (matching ")
                    .append(r.matchingSize).append(", ").append(approxMs).append(" ms)\n");
            sb.append("Brute-force opt: ").append(opt).append(" (").append(bruteMs)
                    .append(" ms)\n");
            sb.append("Cover valid: ").append(valid ? "VERIFIED" : "FAILED").append("\n");
            sb.append("Ratio ").append(String.format("%.3f", ratio))
                    .append(ratio <= 2.0 ? "  VERIFIED <= 2" : "  VIOLATED").append("\n");
            sb.append("Cover nodes ringed on canvas.\n");
            return new Object[] { sb.toString(), fullCover };
        }, out -> {
            setBusy(false);
            vcConsole.setText((String) out[0]);
            canvas.setCover((boolean[]) out[1]);
        });
    }

    private void setBusy(boolean busy) {
        solveButton.setEnabled(!busy);
        minSlotsButton.setEnabled(!busy);
        reductionButton.setEnabled(!busy);
        vcButton.setEnabled(!busy);
        vcExactButton.setEnabled(!busy);
    }
}
