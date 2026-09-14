package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.features.ExamAnalytics;
import edutrack.features.ExamAnalytics.CourseStats;
import edutrack.features.ExamAnalytics.ReportCard;
import edutrack.features.ExamAnalytics.StudentGpa;
import edutrack.gui.ChartCanvas;
import edutrack.gui.ConsoleArea;
import edutrack.gui.CsvExporter;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Student;

public class ExamsPanel extends ModulePanel {

    private static final Color WORST_BG = new Color(0xFB, 0xE3, 0xE3);

    private final ConsoleArea console = new ConsoleArea(6);

    private final JButton courseStatsButton;
    private final DefaultTableModel courseStatsModel;
    private final JLabel courseStatsStatus = new JLabel(" ");
    private final ChartCanvas gradeChart = new ChartCanvas();

    private final JSpinner topNSpinner;
    private final JButton toppersButton;
    private final JButton exportButton;
    private final DefaultTableModel toppersModel;
    private final JTable toppersTable;

    private final JButton atRiskButton;
    private final DefaultTableModel atRiskModel;
    private final JLabel atRiskStatus = new JLabel(" ");

    private final JComboBox<String> studentCombo;
    private final JButton reportButton;
    private final DefaultTableModel reportModel;
    private final JLabel reportSummary = new JLabel(" ");

    public ExamsPanel(DataStore dataStore) {
        super(dataStore);

        courseStatsButton = GuiTheme.primaryButton("Compute course stats");
        courseStatsModel = tableModel("Code", "Course", "Students", "Avg", "Min", "Max", "Pass %");
        topNSpinner = new JSpinner(new SpinnerNumberModel(10, 1, dataStore.students().size(), 1));
        toppersButton = GuiTheme.primaryButton("Compute toppers");
        exportButton = GuiTheme.secondaryButton("CSV export");
        toppersModel = tableModel("Rank", "ID", "Name", "Program", "GPA", "CGPA");
        toppersTable = new JTable(toppersModel);
        atRiskButton = GuiTheme.primaryButton("Compute at-risk");
        atRiskModel = tableModel("ID", "Name", "Program", "Fails", "GPA", "Failing courses");
        studentCombo = new JComboBox<>(studentItems(dataStore));
        reportButton = GuiTheme.primaryButton("Show report card");
        reportModel = tableModel("Course", "Name", "Midsem /30", "Endsem /70", "Total", "Grade", "Points");

        courseStatsButton.addActionListener(e -> runCourseStats());
        toppersButton.addActionListener(e -> runToppers());
        exportButton.addActionListener(e -> exportToppers());
        atRiskButton.addActionListener(e -> runAtRisk());
        reportButton.addActionListener(e -> runReportCard());

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Exam Analytics — Midsem & Endsem Records");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                String.format("Per-course statistics · GPA toppers · at-risk detection · report cards over %,d exam records",
                        dataStore.examRecords().size()));
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.addTab("Course Statistics", buildCourseStatsTab());
        tabs.addTab("Toppers", buildToppersTab());
        tabs.addTab("At-Risk", buildAtRiskTab());
        tabs.addTab("Report Card", buildReportCardTab());

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(card("Log", console), BorderLayout.SOUTH);
    }

    private JPanel buildCourseStatsTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(courseStatsButton);
        controls.add(courseStatsStatus);

        JScrollPane tableScroll = new JScrollPane(new JTable(courseStatsModel));

        JPanel chartCard = card(null, gradeChart);
        chartCard.setPreferredSize(new Dimension(430, 0));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(tableScroll, BorderLayout.CENTER);
        center.add(chartCard, BorderLayout.EAST);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildToppersTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("Top N:"));
        controls.add(topNSpinner);
        controls.add(toppersButton);
        controls.add(exportButton);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(new JScrollPane(toppersTable), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildAtRiskTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(atRiskButton);
        controls.add(atRiskStatus);

        JTable table = new JTable(atRiskModel);
        table.setDefaultRenderer(Object.class, new AtRiskRenderer());

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(new JScrollPane(table), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildReportCardTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("Student:"));
        controls.add(studentCombo);
        controls.add(reportButton);
        controls.add(reportSummary);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(new JScrollPane(new JTable(reportModel)), BorderLayout.CENTER);
        return tab;
    }

    private void runCourseStats() {
        console.appendLine("Computing per-course exam statistics...");
        runTask(courseStatsButton, "Compute course stats", () -> {
            long start = System.nanoTime();
            List<CourseStats> stats = ExamAnalytics.courseStats(dataStore);
            int[] overall = ExamAnalytics.overallGradeDistribution(dataStore);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new CourseStatsResult(stats, overall, ms);
        }, result -> {
            courseStatsModel.setRowCount(0);
            for (CourseStats cs : result.stats) {
                courseStatsModel.addRow(new Object[] { cs.code, cs.name, cs.students,
                        String.format("%.1f", cs.avgTotal), cs.minTotal, cs.maxTotal,
                        String.format("%.1f", cs.passPercent) });
            }
            double[] values = new double[ExamAnalytics.GRADES.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = result.overall[i];
            }
            gradeChart.setData("Overall grade distribution — all exam records", ExamAnalytics.GRADES, values);
            courseStatsStatus.setText(result.stats.size() + " courses · " + result.ms + " ms");
            console.appendLine(String.format("Course stats done: %d courses, overall distribution %s in %d ms.",
                    result.stats.size(), distributionText(result.overall), result.ms));
        });
    }

    private void runToppers() {
        final int n = (Integer) topNSpinner.getValue();
        console.appendLine("Computing top " + n + " students by GPA...");
        runTask(toppersButton, "Compute toppers", () -> {
            long start = System.nanoTime();
            List<StudentGpa> tops = ExamAnalytics.toppers(dataStore, n);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new ToppersResult(tops, ms);
        }, result -> {
            toppersModel.setRowCount(0);
            int rank = 1;
            for (StudentGpa sg : result.tops) {
                toppersModel.addRow(new Object[] { rank++, sg.student.id, sg.student.name, sg.student.program,
                        String.format("%.2f", sg.gpa), String.format("%.2f", sg.student.cgpa) });
            }
            console.appendLine(String.format("Toppers done: top %d of %d students in %d ms.",
                    result.tops.size(), dataStore.students().size(), result.ms));
        });
    }

    private void exportToppers() {
        if (toppersModel.getRowCount() == 0) {
            console.appendLine("CSV export skipped: compute toppers first.");
            return;
        }
        if (CsvExporter.exportTable(this, toppersTable, "exam_toppers.csv")) {
            console.appendLine("Toppers table exported to CSV.");
        }
    }

    private void runAtRisk() {
        console.appendLine("Computing at-risk students (>=2 F grades or GPA < 5.0)...");
        runTask(atRiskButton, "Compute at-risk", () -> {
            long start = System.nanoTime();
            List<StudentGpa> risky = ExamAnalytics.atRisk(dataStore);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new AtRiskResult(risky, ms);
        }, result -> {
            atRiskModel.setRowCount(0);
            for (StudentGpa sg : result.risky) {
                atRiskModel.addRow(new Object[] { sg.student.id, sg.student.name, sg.student.program,
                        sg.failCount, sg.gpa, String.join(", ", sg.failingCourses) });
            }
            atRiskStatus.setText(result.risky.size() + " at-risk of " + dataStore.students().size()
                    + " students · " + result.ms + " ms");
            console.appendLine(String.format("At-risk done: %d students flagged in %d ms.",
                    result.risky.size(), result.ms));
        });
    }

    private void runReportCard() {
        String item = (String) studentCombo.getSelectedItem();
        if (item == null) {
            return;
        }
        final int id = Integer.parseInt(item.substring(0, item.indexOf(' ')));
        console.appendLine("Building report card for student " + id + "...");
        runTask(reportButton, "Show report card", () -> {
            long start = System.nanoTime();
            ReportCard rc = ExamAnalytics.reportCard(dataStore, id);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new ReportResult(rc, ms);
        }, result -> {
            reportModel.setRowCount(0);
            if (result.rc == null) {
                reportSummary.setText("No student with id " + id);
                console.appendLine("No student with id " + id + ".");
                return;
            }
            Map<String, Course> coursesByCode = dataStore.coursesByCode();
            for (ExamRecord r : result.rc.records) {
                Course c = coursesByCode.get(r.courseCode);
                reportModel.addRow(new Object[] { r.courseCode, c == null ? r.courseCode : c.name,
                        r.midsem, r.endsem, r.total(), r.grade(), r.gradePoints() });
            }
            reportSummary.setText(String.format("%s — GPA %.2f · %d credits · %d fail(s) · %d ms",
                    result.rc.student.name, result.rc.gpa, result.rc.credits, result.rc.failCount, result.ms));
            console.appendLine(String.format("Report card done: %s, GPA %.2f, %d courses in %d ms.",
                    result.rc.student.name, result.rc.gpa, result.rc.records.size(), result.ms));
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

    private static String[] studentItems(DataStore ds) {
        List<Student> students = ds.students();
        String[] items = new String[students.size()];
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            items[i] = s.id + " — " + s.name;
        }
        return items;
    }

    private static String distributionText(int[] overall) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ExamAnalytics.GRADES.length; i++) {
            sb.append(ExamAnalytics.GRADES[i]).append('=').append(overall[i]);
            if (i < ExamAnalytics.GRADES.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static class AtRiskRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            Object failsObj = table.getModel().getValueAt(modelRow, 3);
            Object gpaObj = table.getModel().getValueAt(modelRow, 4);
            int fails = failsObj instanceof Integer ? (Integer) failsObj : 0;
            double gpa = gpaObj instanceof Double ? (Double) gpaObj : 10.0;
            boolean worst = fails >= 3 || gpa < 4.0;
            if (column == 4 && value instanceof Double) {
                setText(String.format("%.2f", value));
            }
            if (!isSelected) {
                setBackground(worst ? WORST_BG : table.getBackground());
                setForeground(worst ? GuiTheme.ERROR : table.getForeground());
            }
            return this;
        }
    }

    private static class CourseStatsResult {
        final List<CourseStats> stats;
        final int[] overall;
        final long ms;

        CourseStatsResult(List<CourseStats> stats, int[] overall, long ms) {
            this.stats = stats;
            this.overall = overall;
            this.ms = ms;
        }
    }

    private static class ToppersResult {
        final List<StudentGpa> tops;
        final long ms;

        ToppersResult(List<StudentGpa> tops, long ms) {
            this.tops = tops;
            this.ms = ms;
        }
    }

    private static class AtRiskResult {
        final List<StudentGpa> risky;
        final long ms;

        AtRiskResult(List<StudentGpa> risky, long ms) {
            this.risky = risky;
            this.ms = ms;
        }
    }

    private static class ReportResult {
        final ReportCard rc;
        final long ms;

        ReportResult(ReportCard rc, long ms) {
            this.rc = rc;
            this.ms = ms;
        }
    }
}
