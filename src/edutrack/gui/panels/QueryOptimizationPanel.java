package edutrack.gui.panels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.ConsoleArea;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Course;
import edutrack.modules.M3DynamicProgramming;
import edutrack.modules.M3DynamicProgramming.Candidate;

public class QueryOptimizationPanel extends ModulePanel {

    private static final int TOP_MATCHES = 5;
    private static final int OBST_KEY_COUNT = 12;

    // Query correction tab
    private JTextField queryField;
    private JButton queryButton;
    private JLabel queryStatus;
    private DefaultTableModel queryModel;
    private ConsoleArea queryGaps;

    // Matrix-chain tab
    private JButton mcmButton;
    private JLabel mcmStatus;
    private DefaultTableModel mcmModel;
    private JLabel mcmCost;
    private JLabel mcmDims;
    private ConsoleArea mcmOutput;

    // Bitmask explorer tab
    private JComboBox<String> semesterCombo;
    private JSpinner budgetSpinner;
    private JButton bitmaskButton;
    private DefaultTableModel poolModel;
    private DefaultTableModel chosenModel;
    private JLabel bitmaskSummary;
    private ConsoleArea bitmaskLog;

    // Optimal BST tab
    private JButton obstButton;
    private M3ObstCanvas obstCanvas;
    private JLabel obstCostLabel;
    private JLabel obstBalancedLabel;
    private JLabel obstImproveLabel;
    private JLabel obstTimeLabel;
    private CostBars costBars;
    private ConsoleArea obstPreorder;

    public QueryOptimizationPanel(DataStore dataStore) {
        super(dataStore);

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.addTab("Query Correction", buildQueryTab());
        tabs.addTab("Matrix-Chain", buildMcmTab());
        tabs.addTab("Bitmask Explorer", buildBitmaskTab());
        tabs.addTab("Optimal BST", buildObstTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Query Correction & Optimization");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Levenshtein · Damerau-Levenshtein · Matrix-Chain · Bitmask DP · Optimal BST — advanced dynamic programming on academic data.");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        return header;
    }

    // ------------------------------------------------------------------
    // Tab (a): query correction
    // ------------------------------------------------------------------

    private JPanel buildQueryTab() {
        JPanel tab = tabBase();

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        JLabel prompt = new JLabel("Course or student name (typos allowed):");
        prompt.setFont(GuiTheme.BODY);
        queryField = new JTextField(24);
        queryField.setFont(GuiTheme.BODY);
        queryButton = GuiTheme.primaryButton("Correct Query");
        queryStatus = new JLabel("Try for example: data structurs");
        queryStatus.setFont(GuiTheme.BODY);
        queryStatus.setForeground(GuiTheme.MUTED);
        controls.add(prompt);
        controls.add(queryField);
        controls.add(queryButton);
        controls.add(queryStatus);

        queryButton.addActionListener(e -> runQueryCorrection());
        queryField.addActionListener(e -> runQueryCorrection());

        queryModel = tableModel("Rank", "Kind", "Name", "Details", "Levenshtein", "Damerau", "Differs?");
        JTable table = new JTable(queryModel);
        styleTable(table);

        queryGaps = new ConsoleArea(5);

        tab.add(card(null, controls), BorderLayout.NORTH);
        tab.add(card("Top " + TOP_MATCHES + " matches — Levenshtein & Damerau side by side",
                new JScrollPane(table)), BorderLayout.CENTER);
        tab.add(card("Transposition insight", queryGaps), BorderLayout.SOUTH);
        return tab;
    }

    private void runQueryCorrection() {
        String query = queryField.getText().trim();
        if (query.isEmpty()) {
            queryStatus.setText("Please enter a name to correct.");
            return;
        }
        queryButton.setEnabled(false);
        queryStatus.setText("Scoring candidates...");
        runAsync(() -> {
            long start = System.nanoTime();
            List<Candidate> ranked = M3DynamicProgramming.fuzzyRank(dataStore, query);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            int differCount = 0;
            for (Candidate c : ranked) {
                if (c.dam != c.lev) {
                    differCount++;
                }
            }
            List<String> gapLines = new ArrayList<>();
            if (differCount > 0) {
                List<Candidate> gaps = new ArrayList<>(ranked);
                gaps.sort((a, b) -> {
                    int byGap = Integer.compare(b.lev - b.dam, a.lev - a.dam);
                    if (byGap != 0) {
                        return byGap;
                    }
                    int byLev = Integer.compare(a.lev, b.lev);
                    return byLev != 0 ? byLev : a.name.compareTo(b.name);
                });
                for (Candidate c : gaps) {
                    if (c.dam >= c.lev || gapLines.size() >= TOP_MATCHES) {
                        break;
                    }
                    gapLines.add("  " + c.kind + ": " + c.name + "  Lev=" + c.lev + " Dam=" + c.dam
                            + "  (transpositions are cheaper here)");
                }
            }
            return new QueryOutcome(ranked, differCount, gapLines, elapsedMs);
        }, outcome -> {
            queryModel.setRowCount(0);
            int limit = Math.min(TOP_MATCHES, outcome.ranked.size());
            for (int i = 0; i < limit; i++) {
                Candidate c = outcome.ranked.get(i);
                queryModel.addRow(new Object[] {
                        i + 1, c.kind, c.name, c.info, c.lev, c.dam,
                        c.dam < c.lev ? "differs (transposition?)" : ""
                });
            }
            queryStatus.setText("Scored " + outcome.ranked.size() + " candidates in "
                    + outcome.elapsedMs + " ms.");
            queryGaps.setText("Distances differ for " + outcome.differCount + " of "
                    + outcome.ranked.size() + " candidates.\n");
            if (outcome.differCount > 0) {
                queryGaps.appendLine("Largest gaps (Levenshtein - Damerau):");
                for (String line : outcome.gapLines) {
                    queryGaps.appendLine(line);
                }
            } else {
                queryGaps.appendLine("No transposition-sensitive candidates for this query.");
            }
            queryButton.setEnabled(true);
        }, error -> {
            queryButton.setEnabled(true);
            queryStatus.setText("Query correction failed.");
            showError(error);
        }, queryButton);   }

    // ------------------------------------------------------------------
    // Tab (b): matrix-chain multiplication
    // ------------------------------------------------------------------

    private JPanel buildMcmTab() {
        JPanel tab = tabBase();

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        mcmButton = GuiTheme.primaryButton("Compute Optimal Pipeline");
        mcmStatus = new JLabel("Stage dimension = course credits × 10 + enrollment.");
        mcmStatus.setFont(GuiTheme.BODY);
        mcmStatus.setForeground(GuiTheme.MUTED);
        controls.add(mcmButton);
        controls.add(mcmStatus);
        mcmButton.addActionListener(e -> runMatrixChain());

        mcmModel = tableModel("Matrix", "Rows", "Cols", "Row-dimension source course");
        JTable table = new JTable(mcmModel);
        styleTable(table);

        JPanel labels = new JPanel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        labels.setOpaque(false);
        mcmCost = valueLabel("Minimum scalar multiplications: —");
        mcmDims = valueLabel("Dimension chain: —");
        mcmDims.setFont(GuiTheme.BODY);
        labels.add(mcmCost);
        labels.add(Box.createVerticalStrut(4));
        labels.add(mcmDims);

        mcmOutput = new ConsoleArea(3);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);
        south.add(card(null, labels), BorderLayout.NORTH);
        south.add(card("Optimal parenthesization", mcmOutput), BorderLayout.CENTER);

        tab.add(card(null, controls), BorderLayout.NORTH);
        tab.add(card("Transformation pipeline (A_i is dims[i] × dims[i+1])",
                new JScrollPane(table)), BorderLayout.CENTER);
        tab.add(south, BorderLayout.SOUTH);
        return tab;
    }

    private void runMatrixChain() {
        mcmButton.setEnabled(false);
        mcmStatus.setText("Computing...");
        runAsync(() -> {
            Map<String, Integer> enrollment = M3DynamicProgramming.countEnrollment(dataStore);
            List<Course> courses = dataStore.courses();
            int[] dims = M3DynamicProgramming.buildPipelineDims(enrollment, courses);
            int n = dims.length - 1;
            int[][] split = new int[n][n];
            long start = System.nanoTime();
            long minCost = M3DynamicProgramming.matrixChainOrder(dims, split);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            String parens = n > 0 ? M3DynamicProgramming.buildParenthesization(split, 0, n - 1) : "(none)";
            return new McmOutcome(dims, courses, minCost, parens, elapsedMs);
        }, outcome -> {
            mcmModel.setRowCount(0);
            int n = outcome.dims.length - 1;
            for (int i = 0; i < n; i++) {
                Course source = i < outcome.courses.size() ? outcome.courses.get(i) : null;
                mcmModel.addRow(new Object[] {
                        "A" + i, outcome.dims[i], outcome.dims[i + 1],
                        source == null ? "-" : source.code + " — " + source.name
                });
            }
            StringBuilder chain = new StringBuilder("Dimension chain: [");
            for (int i = 0; i < outcome.dims.length; i++) {
                if (i > 0) {
                    chain.append(", ");
                }
                chain.append(outcome.dims[i]);
            }
            chain.append("]");
            mcmDims.setText(chain.toString());
            mcmCost.setText(String.format("Minimum scalar multiplications: %,d", outcome.minCost));
            mcmOutput.setText(outcome.parens + "\n");
            mcmOutput.appendLine("Time taken: " + outcome.elapsedMs + " ms");
            mcmStatus.setText("Done in " + outcome.elapsedMs + " ms.");
            mcmButton.setEnabled(true);
        }, error -> {
            mcmButton.setEnabled(true);
            mcmStatus.setText("Matrix-chain computation failed.");
            showError(error);
        }, mcmButton);   }

    // ------------------------------------------------------------------
    // Tab (c): bitmask DP course combination explorer
    // ------------------------------------------------------------------

    private JPanel buildBitmaskTab() {
        JPanel tab = tabBase();

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        JLabel semLabel = new JLabel("Semester:");
        semLabel.setFont(GuiTheme.BODY);
        semesterCombo = new JComboBox<>(new String[] { "1", "2", "3", "4", "5", "6", "7", "8" });
        semesterCombo.setFont(GuiTheme.BODY);
        JLabel budgetLabel = new JLabel("Credit budget:");
        budgetLabel.setFont(GuiTheme.BODY);
        budgetSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 60, 1));
        budgetSpinner.setFont(GuiTheme.BODY);
        bitmaskButton = GuiTheme.primaryButton("Find Best Combination");
        controls.add(semLabel);
        controls.add(semesterCombo);
        controls.add(budgetLabel);
        controls.add(budgetSpinner);
        controls.add(bitmaskButton);
        bitmaskButton.addActionListener(e -> runBitmaskExplorer());

        poolModel = tableModel("Code", "Course", "Credits", "Value (popularity)");
        JTable poolTable = new JTable(poolModel);
        styleTable(poolTable);
        chosenModel = tableModel("Code", "Course", "Credits", "Value (popularity)");
        JTable chosenTable = new JTable(chosenModel);
        styleTable(chosenTable);

        JPanel tables = new JPanel(new GridLayout(1, 2, 12, 12));
        tables.setOpaque(false);
        tables.add(card("Course pool (semester)", new JScrollPane(poolTable)));
        tables.add(card("Optimal subset", new JScrollPane(chosenTable)));

        bitmaskSummary = valueLabel("Pick a semester and budget, then run the explorer.");
        bitmaskLog = new ConsoleArea(4);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);
        south.add(card(null, bitmaskSummary), BorderLayout.NORTH);
        south.add(card("Log", bitmaskLog), BorderLayout.CENTER);

        tab.add(card(null, controls), BorderLayout.NORTH);
        tab.add(tables, BorderLayout.CENTER);
        tab.add(south, BorderLayout.SOUTH);
        return tab;
    }

    private void runBitmaskExplorer() {
        int semester = semesterCombo.getSelectedIndex() + 1;
        int budget = (Integer) budgetSpinner.getValue();
        bitmaskButton.setEnabled(false);
        runAsync(() -> {
            List<Course> pool = new ArrayList<>();
            for (Course c : dataStore.courses()) {
                if (c.semester == semester) {
                    pool.add(c);
                }
            }
            Map<String, Integer> popularity = M3DynamicProgramming.countCourseActivity(dataStore);
            int n = pool.size();
            int[] credits = new int[n];
            long[] values = new long[n];
            for (int i = 0; i < n; i++) {
                credits[i] = pool.get(i).credits;
                values[i] = popularity.getOrDefault(pool.get(i).code, 0);
            }
            long start = System.nanoTime();
            long[] result = M3DynamicProgramming.bitmaskBestSubset(credits, values, budget);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new BitmaskOutcome(semester, budget, pool, credits, values,
                    result[0], (int) result[1], elapsedMs);
        }, outcome -> {
            poolModel.setRowCount(0);
            for (int i = 0; i < outcome.pool.size(); i++) {
                Course c = outcome.pool.get(i);
                poolModel.addRow(new Object[] { c.code, c.name, outcome.credits[i], outcome.values[i] });
            }
            chosenModel.setRowCount(0);
            int totalCredits = 0;
            for (int i = 0; i < outcome.pool.size(); i++) {
                if ((outcome.bestMask & (1 << i)) != 0) {
                    Course c = outcome.pool.get(i);
                    chosenModel.addRow(new Object[] { c.code, c.name, outcome.credits[i], outcome.values[i] });
                    totalCredits += outcome.credits[i];
                }
            }
            int n = outcome.pool.size();
            StringBuilder summary = new StringBuilder();
            bitmaskLog.setText("Semester " + outcome.semester + " pool: " + n
                    + " course(s); subsets examined (bitmask DP): " + (1 << n) + "\n");
            if (outcome.bestMask == 0) {
                boolean anyFits = false;
                for (int cr : outcome.credits) {
                    if (cr <= outcome.budget) {
                        anyFits = true;
                        break;
                    }
                }
                summary.append(anyFits
                        ? "Optimal selection is empty (every course within budget has value 0)."
                        : "No course fits within a budget of " + outcome.budget + " credits.");
                bitmaskLog.appendLine(summary.toString());
            } else {
                summary.append(String.format("Total credits %d / %d · Total value %,d · Time %d ms",
                        totalCredits, outcome.budget, outcome.bestValue, outcome.elapsedMs));
                for (int i = 0; i < outcome.pool.size(); i++) {
                    if ((outcome.bestMask & (1 << i)) != 0) {
                        Course c = outcome.pool.get(i);
                        bitmaskLog.appendLine("  + " + c.code + " " + c.name
                                + " (" + outcome.credits[i] + " credits, value " + outcome.values[i] + ")");
                    }
                }
            }
            bitmaskSummary.setText(summary.toString());
            bitmaskButton.setEnabled(true);
        }, error -> {
            bitmaskButton.setEnabled(true);
            bitmaskSummary.setText("Bitmask explorer failed.");
            showError(error);
        }, bitmaskButton);   }

    // ------------------------------------------------------------------
    // Tab (d): optimal BST with tree canvas
    // ------------------------------------------------------------------

    private JPanel buildObstTab() {
        JPanel tab = tabBase();

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        obstButton = GuiTheme.primaryButton("Build Optimal BST");
        JLabel info = new JLabel("Top " + OBST_KEY_COUNT
                + " most-accessed course codes · keys sorted · x = inorder index, y = depth");
        info.setFont(GuiTheme.BODY);
        info.setForeground(GuiTheme.MUTED);
        controls.add(obstButton);
        controls.add(info);
        obstButton.addActionListener(e -> runOptimalBst());

        obstCanvas = new M3ObstCanvas();

        obstCostLabel = valueLabel("Minimum expected search cost (OBST): —");
        obstBalancedLabel = valueLabel("Balanced BST expected cost: —");
        obstImproveLabel = valueLabel("Improvement: —");
        obstTimeLabel = valueLabel("Time (OBST DP): —");

        JPanel costPanel = new JPanel();
        costPanel.setLayout(new BoxLayout(costPanel, BoxLayout.Y_AXIS));
        costPanel.setOpaque(false);
        costPanel.add(obstCostLabel);
        costPanel.add(Box.createVerticalStrut(2));
        costPanel.add(obstBalancedLabel);
        costPanel.add(Box.createVerticalStrut(2));
        costPanel.add(obstImproveLabel);
        costPanel.add(Box.createVerticalStrut(2));
        costPanel.add(obstTimeLabel);
        costPanel.add(Box.createVerticalStrut(8));
        costBars = new CostBars();
        costBars.setAlignmentX(LEFT_ALIGNMENT);
        costPanel.add(costBars);

        obstPreorder = new ConsoleArea(7);

        JPanel south = new JPanel(new GridLayout(1, 2, 12, 12));
        south.setOpaque(false);
        south.add(card("Expected search cost", costPanel));
        south.add(card("Preorder walk (code, freq, depth)", obstPreorder));

        tab.add(card(null, controls), BorderLayout.NORTH);
        tab.add(card("Optimal BST tree — rounded nodes show course code + frequency", obstCanvas),
                BorderLayout.CENTER);
        tab.add(south, BorderLayout.SOUTH);
        return tab;
    }

    private void runOptimalBst() {
        obstButton.setEnabled(false);
        runAsync(() -> {
            Map<String, Integer> freq = M3DynamicProgramming.countCourseActivity(dataStore);
            String[] keys = M3DynamicProgramming.selectTopCodes(freq, OBST_KEY_COUNT);
            int n = keys.length;
            int[] f = new int[n];
            for (int i = 0; i < n; i++) {
                f[i] = freq.get(keys[i]);
            }
            int[][] root = new int[n][n];
            long start = System.nanoTime();
            long optCost = M3DynamicProgramming.optimalBstCost(f, root);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            long balCost = M3DynamicProgramming.balancedBstCost(f, 0, n - 1, 1);
            return new ObstOutcome(keys, f, root, optCost, balCost, elapsedMs,
                    preorderText(keys, f, root));
        }, outcome -> {
            obstCanvas.setData(outcome.keys, outcome.freq, outcome.root);
            obstCostLabel.setText(String.format("Minimum expected search cost (OBST): %,d", outcome.optCost));
            obstBalancedLabel.setText(String.format("Balanced BST expected cost: %,d", outcome.balCost));
            double savings = outcome.balCost == 0 ? 0.0
                    : 100.0 * (outcome.balCost - outcome.optCost) / outcome.balCost;
            obstImproveLabel.setText(String.format("Improvement: %,d (%.1f%% lower)",
                    outcome.balCost - outcome.optCost, savings));
            obstTimeLabel.setText("Time (OBST DP): " + outcome.elapsedMs + " ms");
            costBars.setData(outcome.optCost, outcome.balCost);
            obstPreorder.setText(outcome.preorder);
            obstButton.setEnabled(true);
        }, error -> {
            obstButton.setEnabled(true);
            showError(error);
        }, obstButton);
    }

    private static String preorderText(String[] keys, int[] freq, int[][] root) {
        if (keys.length == 0) {
            return "(no keys)\n";
        }
        StringBuilder sb = new StringBuilder();
        appendPreorder(sb, keys, freq, root, 0, keys.length - 1, 1);
        return sb.toString();
    }

    private static void appendPreorder(StringBuilder sb, String[] keys, int[] freq, int[][] root,
            int i, int j, int depth) {
        if (i > j) {
            return;
        }
        int r = root[i][j];
        sb.append("  ".repeat(depth)).append(keys[r])
                .append("  (freq=").append(freq[r]).append(", depth=").append(depth).append(")\n");
        appendPreorder(sb, keys, freq, root, i, r - 1, depth + 1);
        appendPreorder(sb, keys, freq, root, r + 1, j, depth + 1);
    }

    // ------------------------------------------------------------------
    // Small Swing helpers and result holders
    // ------------------------------------------------------------------

    private JPanel tabBase() {
        JPanel tab = new JPanel(new BorderLayout(12, 12));
        tab.setOpaque(false);
        tab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return tab;
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static void styleTable(JTable table) {
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY_BOLD);
        label.setForeground(GuiTheme.TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static final class CostBars extends JPanel {
        private long obst = -1;
        private long balanced = -1;

        CostBars() {
            setOpaque(false);
            setPreferredSize(new Dimension(460, 78));
            setMinimumSize(new Dimension(220, 78));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        }

        void setData(long obstCost, long balancedCost) {
            this.obst = obstCost;
            this.balanced = balancedCost;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            try {
                if (obst < 0 || balanced < 0) {
                    g2.setFont(GuiTheme.BODY);
                    g2.setColor(GuiTheme.MUTED);
                    g2.drawString("Run the OBST builder to compare costs.", 4, 24);
                    return;
                }
                int labelW = 112;
                int valueW = 96;
                int maxBar = Math.max(10, getWidth() - labelW - valueW - 16);
                long max = Math.max(1, Math.max(obst, balanced));
                int barH = 20;
                int y1 = 10;
                int y2 = y1 + barH + 12;
                drawBar(g2, labelW, y1, barH, maxBar, obst, max, GuiTheme.ACCENT, "Optimal BST");
                drawBar(g2, labelW, y2, barH, maxBar, balanced, max, new Color(0x94A3B8), "Balanced BST");
            } finally {
                g2.dispose();
            }
        }

        private void drawBar(Graphics2D g2, int x, int y, int h, int maxBar, long value, long max,
                Color color, String label) {
            g2.setFont(GuiTheme.BODY);
            g2.setColor(GuiTheme.TEXT);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = y + h / 2 + fm.getAscent() / 2 - 2;
            g2.drawString(label, x - fm.stringWidth(label) - 10, baseline);
            int w = (int) Math.max(2, Math.round(value * (double) maxBar / max));
            g2.setColor(color);
            g2.fillRoundRect(x, y, w, h, 8, 8);
            g2.setColor(GuiTheme.TEXT);
            g2.setFont(GuiTheme.BODY_BOLD);
            g2.drawString(String.format("%,d", value), x + w + 8, baseline);
        }
    }

    private static final class QueryOutcome {
        final List<Candidate> ranked;
        final int differCount;
        final List<String> gapLines;
        final long elapsedMs;

        QueryOutcome(List<Candidate> ranked, int differCount, List<String> gapLines, long elapsedMs) {
            this.ranked = ranked;
            this.differCount = differCount;
            this.gapLines = gapLines;
            this.elapsedMs = elapsedMs;
        }
    }

    private static final class McmOutcome {
        final int[] dims;
        final List<Course> courses;
        final long minCost;
        final String parens;
        final long elapsedMs;

        McmOutcome(int[] dims, List<Course> courses, long minCost, String parens, long elapsedMs) {
            this.dims = dims;
            this.courses = courses;
            this.minCost = minCost;
            this.parens = parens;
            this.elapsedMs = elapsedMs;
        }
    }

    private static final class BitmaskOutcome {
        final int semester;
        final int budget;
        final List<Course> pool;
        final int[] credits;
        final long[] values;
        final long bestValue;
        final int bestMask;
        final long elapsedMs;

        BitmaskOutcome(int semester, int budget, List<Course> pool, int[] credits, long[] values,
                long bestValue, int bestMask, long elapsedMs) {
            this.semester = semester;
            this.budget = budget;
            this.pool = pool;
            this.credits = credits;
            this.values = values;
            this.bestValue = bestValue;
            this.bestMask = bestMask;
            this.elapsedMs = elapsedMs;
        }
    }

    private static final class ObstOutcome {
        final String[] keys;
        final int[] freq;
        final int[][] root;
        final long optCost;
        final long balCost;
        final long elapsedMs;
        final String preorder;

        ObstOutcome(String[] keys, int[] freq, int[][] root, long optCost, long balCost,
                long elapsedMs, String preorder) {
            this.keys = keys;
            this.freq = freq;
            this.root = root;
            this.optCost = optCost;
            this.balCost = balCost;
            this.elapsedMs = elapsedMs;
            this.preorder = preorder;
        }
    }
}
