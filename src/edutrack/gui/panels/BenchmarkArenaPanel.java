package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.modules.M1AhoCorasick;
import edutrack.modules.M2KasaiLCP;
import edutrack.modules.M2SAIS;
import edutrack.modules.M2SuffixArray;
import edutrack.modules.M2SuffixAutomaton;
import edutrack.modules.M3DynamicProgramming;
import modules.KMPSearch;
import modules.RabinKarpSearch;
import modules.ZFunctionSearch;

/**
 * Interactive head-to-head benchmark arena for the core string, suffix and DP
 * algorithms. Timings are JVM-local measurements, not portable performance
 * claims; each run uses deterministic workloads and reports the median of
 * repeated measurements.
 */
public final class BenchmarkArenaPanel extends ModulePanel {

    private static final int WARMUPS = 2;
    private static final int MEASURED = 5;

    private final BenchmarkCanvas chart = new BenchmarkCanvas();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] { "Arena", "Algorithm", "Median (µs)", "Runs", "Workload" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JLabel status = new JLabel("Ready — press Run Arena.");
    private final JButton runButton = GuiTheme.primaryButton("Run Benchmark Arena");

    public BenchmarkArenaPanel(DataStore dataStore) {
        super(dataStore);

        JPanel header = sectionHeader(
                "Algorithm Benchmark Arena",
                "Head-to-head microsecond timings for the implemented string, suffix and dynamic-programming algorithms.");
        add(header, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        GuiTheme.styleTable(table);
        table.setAutoCreateRowSorter(false);

        JPanel controls = new JPanel(new BorderLayout(10, 8));
        controls.setOpaque(false);
        controls.add(runButton, BorderLayout.WEST);
        status.setFont(GuiTheme.SMALL);
        status.setForeground(GuiTheme.MUTED);
        controls.add(status, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(chart, BorderLayout.CENTER);
        center.add(new JScrollPane(table), BorderLayout.SOUTH);
        center.add(controls, BorderLayout.NORTH);

        add(card("Live benchmark results", center), BorderLayout.CENTER);

        runButton.addActionListener(e -> runBenchmark());
    }

    private void runBenchmark() {
        runButton.setEnabled(false);
        status.setText("Running deterministic workloads…");
        tableModel.setRowCount(0);
        chart.clear();

        runAsync(() -> BenchmarkSuite.runAll(), results -> {
            for (BenchmarkResult r : results) {
                tableModel.addRow(new Object[] {
                        r.arena, r.algorithm, String.format("%.2f", r.medianMicros),
                        MEASURED, r.workload
                });
            }
            chart.setResults(results);
            status.setText("Completed " + results.size() + " measurements per arena; median of "
                    + MEASURED + " measured runs after " + WARMUPS + " warmups.");
            runButton.setEnabled(true);
        }, error -> {
            runButton.setEnabled(true);
            status.setText("Benchmark failed.");
            showError(error);
        }, runButton);
    }

    private static final class BenchmarkSuite {
        static List<BenchmarkResult> runAll() {
            List<BenchmarkResult> out = new ArrayList<>();
            out.addAll(stringArena());
            out.addAll(suffixArena());
            out.addAll(dpArena());
            return out;
        }

        private static List<BenchmarkResult> stringArena() {
            String text = workloadText(120_000);
            String pattern = "academic-algorithms";
            String[] keywords = { "academic", "algorithms", "assignment", "course", "student" };
            M1AhoCorasick ac = new M1AhoCorasick();
            for (String keyword : keywords) ac.addPattern(keyword);
            ac.build();

            List<BenchmarkResult> out = new ArrayList<>();
            out.add(measure("String search", "KMP", "120k-char text / one pattern",
                    () -> KMPSearch.search(text, pattern)));
            out.add(measure("String search", "Z-Algorithm", "120k-char text / one pattern",
                    () -> ZFunctionSearch.search(text, pattern)));
            out.add(measure("String search", "Rabin-Karp", "120k-char text / one pattern",
                    () -> RabinKarpSearch.search(text, pattern)));
            out.add(measure("String search", "Aho-Corasick", "120k-char text / five patterns",
                    () -> ac.search(text)));
            return out;
        }

        private static List<BenchmarkResult> suffixArena() {
            String text = workloadText(20_000);
            List<BenchmarkResult> out = new ArrayList<>();
            out.add(measure("Suffix structures", "Suffix Array", "20k-char document / build",
                    () -> M2SuffixArray.buildSuffixArray(text)));
            out.add(measure("Suffix structures", "SA-IS", "20k-char document / build",
                    () -> M2SAIS.buildSuffixArray(text)));
            out.add(measure("Suffix structures", "Kasai LCP", "20k-char document / SA + LCP",
                    () -> {
                        int[] sa = M2SuffixArray.buildSuffixArray(text);
                        return M2KasaiLCP.buildLCP(text, sa);
                    }));
            out.add(measure("Suffix structures", "Suffix Automaton", "20k-char document / build",
                    () -> M2SuffixAutomaton.build(text)));
            return out;
        }

        private static List<BenchmarkResult> dpArena() {
            String query = "student assignment algorithms";
            String candidate = "student assignmnet algorithsm";
            int[] dims = { 30, 45, 20, 60, 35, 25, 50, 40, 30 };

            List<BenchmarkResult> out = new ArrayList<>();
            out.add(measure("Dynamic programming", "Levenshtein", "30-char typo query pair",
                    () -> M3DynamicProgramming.levenshtein(query, candidate)));
            out.add(measure("Dynamic programming", "Damerau-Levenshtein", "30-char typo query pair",
                    () -> M3DynamicProgramming.damerauOSA(query, candidate)));
            out.add(measure("Dynamic programming", "Matrix-Chain", "8 matrices / scalar-cost DP",
                    () -> {
                        int[][] split = new int[dims.length - 1][dims.length - 1];
                        return M3DynamicProgramming.matrixChainOrder(dims, split);
                    }));
            return out;
        }

        private static String workloadText(int length) {
            String seed = "student academic course assignment algorithms campus "
                    + "database examination faculty research ";
            StringBuilder sb = new StringBuilder(length);
            while (sb.length() < length) sb.append(seed);
            return sb.substring(0, length);
        }

        private static <T> BenchmarkResult measure(String arena, String algorithm, String workload,
                Supplier<T> operation) {
            for (int i = 0; i < WARMUPS; i++) {
                consume(operation.get());
            }
            double[] samples = new double[MEASURED];
            for (int i = 0; i < MEASURED; i++) {
                long start = System.nanoTime();
                consume(operation.get());
                samples[i] = (System.nanoTime() - start) / 1_000.0;
            }
            Arrays.sort(samples);
            return new BenchmarkResult(arena, algorithm, samples[samples.length / 2], workload);
        }

        private static void consume(Object value) {
            if (value == null) throw new IllegalStateException("Benchmark operation returned null.");
            if (value instanceof int[]) BlackHole.value += ((int[]) value).length;
            else if (value instanceof long[]) BlackHole.value += ((long[]) value).length;
            else if (value instanceof List<?>) BlackHole.value += ((List<?>) value).size();
            else BlackHole.value ^= value.hashCode();
        }
    }

    @FunctionalInterface
    private interface Supplier<T> {
        T get();
    }

    private static final class BlackHole {
        static volatile long value;
    }

    private static final class BenchmarkResult {
        final String arena;
        final String algorithm;
        final double medianMicros;
        final String workload;

        BenchmarkResult(String arena, String algorithm, double medianMicros, String workload) {
            this.arena = arena;
            this.algorithm = algorithm;
            this.medianMicros = medianMicros;
            this.workload = workload;
        }
    }

    private static final class BenchmarkCanvas extends JPanel {
        private List<BenchmarkResult> results = Collections.emptyList();

        BenchmarkCanvas() {
            setOpaque(true);
            setBackground(GuiTheme.CARD_BG);
            setPreferredSize(new Dimension(760, 360));
            setMinimumSize(new Dimension(280, 280));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        void clear() {
            results = Collections.emptyList();
            repaint();
        }

        void setResults(List<BenchmarkResult> results) {
            this.results = new ArrayList<>(results);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(GuiTheme.BODY);
                if (results.isEmpty()) {
                    g2.setColor(GuiTheme.MUTED);
                    g2.drawString("Run the arena to generate comparative timing charts.", 20, 36);
                    return;
                }

                int width = Math.max(1, getWidth() - 50);
                int y = 30;
                for (Map.Entry<String, List<BenchmarkResult>> group : grouped(results).entrySet()) {
                    g2.setColor(GuiTheme.TEXT);
                    g2.setFont(GuiTheme.H2);
                    g2.drawString(group.getKey(), 12, y);
                    y += 26;

                    double max = 1;
                    for (BenchmarkResult r : group.getValue()) max = Math.max(max, r.medianMicros);
                    for (BenchmarkResult r : group.getValue()) {
                        int barWidth = (int) Math.max(2, Math.round((r.medianMicros / max) * (width - 220)));
                        g2.setColor(GuiTheme.ACCENT_SOFT);
                        g2.fillRoundRect(160, y - 15, width - 190, 20, 8, 8);
                        g2.setColor(GuiTheme.ACCENT);
                        g2.fillRoundRect(160, y - 15, barWidth, 20, 8, 8);
                        g2.setColor(GuiTheme.TEXT);
                        g2.setFont(GuiTheme.SMALL_BOLD);
                        g2.drawString(r.algorithm, 4, y);
                        g2.drawString(String.format("%.2f µs", r.medianMicros),
                                Math.min(getWidth() - 70, 166 + barWidth), y);
                        y += 32;
                    }
                    y += 18;
                    if (y > getHeight() - 20) break;
                }
            } finally {
                g2.dispose();
            }
        }

        private Map<String, List<BenchmarkResult>> grouped(List<BenchmarkResult> input) {
            Map<String, List<BenchmarkResult>> grouped = new LinkedHashMap<>();
            for (BenchmarkResult r : input) grouped.computeIfAbsent(r.arena, k -> new ArrayList<>()).add(r);
            return grouped;
        }
    }
}
