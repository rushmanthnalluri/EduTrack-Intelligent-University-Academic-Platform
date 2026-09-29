package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

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
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.ConsoleArea;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.ActivityEvent;
import edutrack.model.Student;
import edutrack.modules.M6ParallelMergeSort;
import edutrack.modules.M6RandomizedParallel;
import edutrack.modules.M6RandomizedQuickSort;
import edutrack.modules.M6ReservoirSampler;
import edutrack.modules.M6ReservoirSampler.SampleResult;

public class RankingStreamsPanel extends ModulePanel {

    private static final int BENCHMARK_SIZE = 1_000_000;

    private final ConsoleArea console = new ConsoleArea(6);

    private final JSpinner topNSpinner;
    private final DefaultTableModel rankingModel;
    private final JLabel rankingStats = new JLabel(" ");
    private final JButton rankButton;
    private final JButton benchmarkButton;
    private final M6BarChart benchmarkChart = new M6BarChart();
    private final M6BarChart adversarialChart = new M6BarChart();

    private final JButton mergeButton;
    private final JLabel mergeStats = new JLabel(" ");
    private final M6BarChart mergeChart = new M6BarChart();

    private final JSpinner kSpinner;
    private final JComboBox<String> actionCombo;
    private final JButton sampleButton;
    private final JButton uniformityButton;
    private final DefaultTableModel sampleModel;
    private final JLabel sampleStats = new JLabel(" ");
    private final M6BarChart uniformityChart = new M6BarChart();

    public RankingStreamsPanel(DataStore dataStore) {
        super(dataStore);

        topNSpinner = new JSpinner(new SpinnerNumberModel(10, 1, dataStore.students().size(), 1));
        rankingModel = tableModel("Rank", "ID", "Name", "Program", "CGPA");
        rankButton = GuiTheme.primaryButton("Rank students");
        benchmarkButton = GuiTheme.secondaryButton("Run 1M benchmark");
        mergeButton = GuiTheme.primaryButton("Run 1M benchmark");
        kSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
        List<String> actions = new ArrayList<>();
        actions.add("ALL");
        actions.addAll(M6RandomizedParallel.KNOWN_ACTIONS);
        actionCombo = new JComboBox<>(actions.toArray(new String[0]));
        sampleButton = GuiTheme.primaryButton("Sample stream");
        uniformityButton = GuiTheme.secondaryButton("Uniformity demo");
        sampleModel = tableModel("Timestamp", "Student", "Action", "Details");

        rankButton.addActionListener(e -> runRanking());
        benchmarkButton.addActionListener(e -> runQuickSortBenchmark());
        mergeButton.addActionListener(e -> runMergeBenchmark());
        sampleButton.addActionListener(e -> runSampling());
        uniformityButton.addActionListener(e -> runUniformityDemo());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.addTab("CGPA Ranking & QuickSort", buildRankingTab());
        tabs.addTab("Parallel Merge Sort", buildMergeTab());
        tabs.addTab("Reservoir Sampling", buildReservoirTab());

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Ranking & Streams — Randomized & Parallel Algorithms");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Randomized QuickSort · Parallel Merge Sort (ForkJoin) · Reservoir Sampling over live academic data");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(card("Log", console), BorderLayout.SOUTH);
    }

    private JPanel buildRankingTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("Top N:"));
        controls.add(topNSpinner);
        controls.add(rankButton);
        controls.add(benchmarkButton);
        controls.add(rankingStats);

        JScrollPane tableScroll = new JScrollPane(new JTable(rankingModel));
        tableScroll.setPreferredSize(new Dimension(200, 240));

        JPanel charts = new JPanel(new GridLayout(1, 2, 12, 12));
        charts.setOpaque(false);
        charts.add(card("1M benchmark — random input (ms)", benchmarkChart));
        charts.add(card("Adversarial sorted input — deterministic comparisons", adversarialChart));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(tableScroll, BorderLayout.NORTH);
        center.add(charts, BorderLayout.CENTER);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildMergeTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(mergeButton);
        controls.add(mergeStats);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(card("Sequential vs Fork/Join parallel merge sort (ms)", mergeChart), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildReservoirTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("k:"));
        controls.add(kSpinner);
        controls.add(new JLabel("Action:"));
        controls.add(actionCombo);
        controls.add(sampleButton);
        controls.add(uniformityButton);
        controls.add(sampleStats);

        JScrollPane tableScroll = new JScrollPane(new JTable(sampleModel));
        tableScroll.setPreferredSize(new Dimension(200, 220));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(tableScroll, BorderLayout.NORTH);
        center.add(card("Uniformity demo — selection frequency per stream position (n=100, k=10, 20,000 trials)",
                uniformityChart), BorderLayout.CENTER);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private void runRanking() {
        final int n = (Integer) topNSpinner.getValue();
        console.appendLine("Ranking " + dataStore.students().size()
                + " students by CGPA (randomized quicksort, seed " + M6RandomizedParallel.DEMO_SEED + ")...");
        runTask(rankButton, "Rank students", () -> {
            Student[] ranked = dataStore.students().toArray(new Student[0]);
            long start = System.nanoTime();
            long comparisons = M6RandomizedQuickSort.sortStudents(ranked, new Random(M6RandomizedParallel.DEMO_SEED));
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new RankingResult(ranked, comparisons, ms);
        }, result -> {
            rankingModel.setRowCount(0);
            int limit = Math.min(n, result.ranked.length);
            for (int i = 0; i < limit; i++) {
                Student s = result.ranked[i];
                rankingModel.addRow(new Object[] { i + 1, s.id, s.name, s.program, String.format("%.2f", s.cgpa) });
            }
            rankingStats.setText(String.format("%,d comparisons · %d ms", result.comparisons, result.ms));
            console.appendLine(String.format("Ranking done: %,d comparisons in %d ms; showing top %d.",
                    result.comparisons, result.ms, limit));
        });
    }

    private void runQuickSortBenchmark() {
        console.appendLine("QuickSort benchmark on " + String.format("%,d", BENCHMARK_SIZE) + " elements running...");
        runTask(benchmarkButton, "Run 1M benchmark", () -> {
            BenchmarkResult r = new BenchmarkResult();
            double[] base = M6RandomizedParallel.buildBenchmarkArray(
                    dataStore, BENCHMARK_SIZE, M6RandomizedParallel.DEMO_SEED);

            double[] warm = M6RandomizedParallel.buildBenchmarkArray(dataStore, 200_000, M6RandomizedParallel.DEMO_SEED + 7);
            M6RandomizedQuickSort.sort(warm.clone(), new Random(M6RandomizedParallel.DEMO_SEED));
            M6RandomizedQuickSort.sortDeterministic(warm.clone());
            Arrays.sort(warm.clone());

            double[] a = base.clone();
            long start = System.nanoTime();
            r.randomizedCmp = M6RandomizedQuickSort.sort(a, new Random(M6RandomizedParallel.DEMO_SEED));
            r.randomizedMs = (System.nanoTime() - start) / 1_000_000;

            double[] b = base.clone();
            start = System.nanoTime();
            Arrays.sort(b);
            r.arraysMs = (System.nanoTime() - start) / 1_000_000;

            double[] c = base.clone();
            start = System.nanoTime();
            r.deterministicCmp = M6RandomizedQuickSort.sortDeterministic(c);
            r.deterministicMs = (System.nanoTime() - start) / 1_000_000;

            double[] sortedBase = base.clone();
            Arrays.sort(sortedBase);

            double[] d = sortedBase.clone();
            start = System.nanoTime();
            M6RandomizedQuickSort.sort(d, new Random(M6RandomizedParallel.DEMO_SEED));
            r.sortedRandomizedMs = (System.nanoTime() - start) / 1_000_000;

            double[] e = sortedBase.clone();
            start = System.nanoTime();
            Arrays.sort(e);
            r.sortedArraysMs = (System.nanoTime() - start) / 1_000_000;

            int[] sizes = { 10_000, 20_000, 40_000 };
            r.advN = sizes;
            r.advCmp = new long[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                double[] f = Arrays.copyOf(sortedBase, sizes[i]);
                r.advCmp[i] = M6RandomizedQuickSort.sortDeterministic(f);
            }
            return r;
        }, r -> {
            benchmarkChart.setData(List.of(
                    new M6BarChart.Bar("Rand. QS", r.randomizedMs, GuiTheme.ACCENT),
                    new M6BarChart.Bar("Arrays.sort", r.arraysMs, GuiTheme.MUTED),
                    new M6BarChart.Bar("Det. QS", r.deterministicMs, GuiTheme.ACCENT_DARK)), "%.0f ms");
            List<M6BarChart.Bar> advBars = new ArrayList<>();
            for (int i = 0; i < r.advN.length; i++) {
                advBars.add(new M6BarChart.Bar("n=" + r.advN[i] / 1000 + "k", r.advCmp[i] / 1e6, GuiTheme.ERROR));
            }
            adversarialChart.setData(advBars, "%.0fM cmp");
            console.appendLine(String.format(
                    "Random input : randomized %d ms (%,d cmp) · Arrays.sort %d ms · deterministic %d ms (%,d cmp)",
                    r.randomizedMs, r.randomizedCmp, r.arraysMs, r.deterministicMs, r.deterministicCmp));
            console.appendLine(String.format(
                    "Sorted input : randomized %d ms · Arrays.sort %d ms · deterministic O(n^2): %.0fM → %.0fM → %.0fM comparisons (10k→20k→40k)",
                    r.sortedRandomizedMs, r.sortedArraysMs,
                    r.advCmp[0] / 1e6, r.advCmp[1] / 1e6, r.advCmp[2] / 1e6));
        });
    }

    private void runMergeBenchmark() {
        console.appendLine("Merge sort benchmark (sequential vs Fork/Join parallel) running...");
        runTask(mergeButton, "Run 1M benchmark", () -> {
            int processors = Runtime.getRuntime().availableProcessors();
            double[] base = M6RandomizedParallel.buildBenchmarkArray(
                    dataStore, BENCHMARK_SIZE, M6RandomizedParallel.DEMO_SEED + 1);

            double[] warm = M6RandomizedParallel.buildBenchmarkArray(dataStore, 200_000, M6RandomizedParallel.DEMO_SEED + 8);
            M6ParallelMergeSort.sequentialSort(warm.clone());
            M6ParallelMergeSort.parallelSort(warm.clone(), processors);

            double[] a = base.clone();
            long start = System.nanoTime();
            M6ParallelMergeSort.sequentialSort(a);
            long seqMs = (System.nanoTime() - start) / 1_000_000;

            double[] b = base.clone();
            start = System.nanoTime();
            M6ParallelMergeSort.parallelSort(b, processors);
            long parMs = (System.nanoTime() - start) / 1_000_000;

            return new MergeResult(seqMs, parMs, processors, Arrays.equals(a, b));
        }, r -> {
            mergeChart.setData(List.of(
                    new M6BarChart.Bar("Sequential", r.seqMs, GuiTheme.MUTED),
                    new M6BarChart.Bar("Parallel", r.parMs, GuiTheme.SUCCESS)), "%.0f ms");
            mergeStats.setText(String.format("sequential %d ms · parallel %d ms · %.2fx speedup on %d processors",
                    r.seqMs, r.parMs, r.seqMs / (double) Math.max(1, r.parMs), r.processors));
            console.appendLine(String.format("Merge sort done: %d ms sequential vs %d ms parallel (%.2fx, %d processors, identical result: %s)",
                    r.seqMs, r.parMs, r.seqMs / (double) Math.max(1, r.parMs), r.processors, r.identical));
        });
    }

    private void runSampling() {
        final int k = (Integer) kSpinner.getValue();
        final String action = (String) actionCombo.getSelectedItem();
        console.appendLine("Reservoir sampling: k=" + k + ", filter=" + action + " (one pass, Algorithm R)...");
        runTask(sampleButton, "Sample stream", () -> {
            List<ActivityEvent> stream = dataStore.activityStream();
            long start = System.nanoTime();
            SampleResult<ActivityEvent> result = "ALL".equals(action)
                    ? M6ReservoirSampler.sample(stream, k, new Random(M6RandomizedParallel.DEMO_SEED))
                    : M6ReservoirSampler.sample(stream, ev -> action.equals(ev.action), k,
                            new Random(M6RandomizedParallel.DEMO_SEED));
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new SamplingResult(result.reservoir, result.seen, ms);
        }, r -> {
            sampleModel.setRowCount(0);
            for (ActivityEvent ev : r.events) {
                sampleModel.addRow(new Object[] { ev.timestamp, ev.studentId, ev.action, ev.details });
            }
            sampleStats.setText(String.format("%,d events seen · %d sampled · %d ms",
                    r.seen, r.events.size(), r.ms));
            console.appendLine(String.format("Sampling done: %,d events seen, %d sampled in %d ms.",
                    r.seen, r.events.size(), r.ms));
        });
    }

    private void runUniformityDemo() {
        console.appendLine("Uniformity demo: n=100, k=10, 20,000 trials running...");
        runTask(uniformityButton, "Uniformity demo", () -> {
            long start = System.nanoTime();
            double[] freqs = M6RandomizedParallel.selectionFrequencies(100, 10, 20_000, M6RandomizedParallel.DEMO_SEED);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new UniformityResult(freqs, 10 / 100.0, ms);
        }, r -> {
            List<M6BarChart.Bar> bars = new ArrayList<>();
            for (int i = 0; i < r.freqs.length; i++) {
                bars.add(new M6BarChart.Bar(String.valueOf(i), r.freqs[i], GuiTheme.ACCENT));
            }
            uniformityChart.setData(bars, "%.3f");
            uniformityChart.setTarget(r.expected, String.format("expected k/n = %.4f", r.expected));
            console.appendLine(String.format(
                    "Uniformity demo done in %d ms; each position selected with frequency ~= %.4f.", r.ms, r.expected));
        });
    }

    private <T> void runTask(JButton button, String label, Callable<T> work, Consumer<T> onDone) {
        button.setEnabled(false);
        button.setText("Running…");
        runAsync(work, result -> {
            button.setEnabled(true);
            button.setText(label);
            onDone.accept(result);
        }, error -> {
            button.setEnabled(true);
            button.setText(label);
            showError(error);
        });
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static class RankingResult {
        final Student[] ranked;
        final long comparisons;
        final long ms;

        RankingResult(Student[] ranked, long comparisons, long ms) {
            this.ranked = ranked;
            this.comparisons = comparisons;
            this.ms = ms;
        }
    }

    private static class BenchmarkResult {
        long randomizedMs;
        long randomizedCmp;
        long arraysMs;
        long deterministicMs;
        long deterministicCmp;
        long sortedRandomizedMs;
        long sortedArraysMs;
        int[] advN;
        long[] advCmp;
    }

    private static class MergeResult {
        final long seqMs;
        final long parMs;
        final int processors;
        final boolean identical;

        MergeResult(long seqMs, long parMs, int processors, boolean identical) {
            this.seqMs = seqMs;
            this.parMs = parMs;
            this.processors = processors;
            this.identical = identical;
        }
    }

    private static class SamplingResult {
        final List<ActivityEvent> events;
        final long seen;
        final long ms;

        SamplingResult(List<ActivityEvent> events, long seen, long ms) {
            this.events = events;
            this.seen = seen;
            this.ms = ms;
        }
    }

    private static class UniformityResult {
        final double[] freqs;
        final double expected;
        final long ms;

        UniformityResult(double[] freqs, double expected, long ms) {
            this.freqs = freqs;
            this.expected = expected;
            this.ms = ms;
        }
    }
}
