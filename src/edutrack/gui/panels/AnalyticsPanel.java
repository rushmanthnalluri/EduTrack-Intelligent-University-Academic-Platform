package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.features.ActivityAnalytics;
import edutrack.gui.ChartCanvas;
import edutrack.gui.ConsoleArea;
import edutrack.gui.CsvExporter;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Student;

/**
 * Activity analytics dashboard over the activity event stream. The constructor
 * only builds the UI; every scan runs via runAsync on a SwingWorker while
 * the compute button stays disabled. Results land in a stat strip, three
 * ChartCanvas bar charts, a top-10 students table and a dark console log.
 */
public class AnalyticsPanel extends ModulePanel {

    private static final int TOP_K = 10;

    private final JButton computeButton;
    private final JButton exportButton;
    private final JLabel statusLabel;
    private final JLabel eventsValue;
    private final JLabel daysValue;
    private final JLabel avgValue;
    private final JLabel peakValue;
    private final JLabel coursePctValue;
    private final ChartCanvas actionChart;
    private final ChartCanvas hourChart;
    private final ChartCanvas coursesChart;
    private final DefaultTableModel studentsModel;
    private final JTable studentsTable;
    private final ConsoleArea console;

    public AnalyticsPanel(DataStore dataStore) {
        super(dataStore);

        computeButton = GuiTheme.primaryButton("Compute analytics");
        exportButton = GuiTheme.secondaryButton("CSV export");
        exportButton.setEnabled(false);
        statusLabel = new JLabel("Press 'Compute analytics' to scan the activity event stream.");
        statusLabel.setFont(GuiTheme.BODY_BOLD);
        statusLabel.setForeground(GuiTheme.ACCENT_DARK);

        JLabel title = new JLabel("Activity Analytics");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Activity analytics · action mix, hourly rhythm, courses and students");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitle);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(statusLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(computeButton);
        buttons.add(exportButton);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(titleBox, BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        eventsValue = statValue();
        daysValue = statValue();
        avgValue = statValue();
        peakValue = statValue();
        coursePctValue = statValue();

        JPanel stats = new JPanel(new GridLayout(1, 5, 12, 12));
        stats.setOpaque(false);
        stats.add(statCard("Total events", eventsValue));
        stats.add(statCard("Active days", daysValue));
        stats.add(statCard("Avg events / day", avgValue));
        stats.add(statCard("Peak hour (UTC)", peakValue));
        stats.add(statCard("Course-related", coursePctValue));

        actionChart = new ChartCanvas();
        actionChart.setHorizontal(true);
        hourChart = new ChartCanvas();
        hourChart.setPreferredSize(new Dimension(900, 165));
        hourChart.setLabelStep(2);
        coursesChart = new ChartCanvas();

        studentsModel = tableModel("Rank", "Student ID", "Name", "Program", "Events");
        studentsTable = styledTable(studentsModel);
        int[] widths = { 40, 68, 122, 118, 55 };
        for (int c = 0; c < widths.length; c++) {
            studentsTable.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        }
        console = new ConsoleArea(5);

        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        double[] weights = { 0.34, 0.30, 0.36 };
        java.awt.Component[] cards = {
            card("Events per action", actionChart),
            card("Top 10 courses by activity", coursesChart),
            card("Top 10 most active students", new JScrollPane(studentsTable))
        };
        for (int i = 0; i < cards.length; i++) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = i;
            gbc.weightx = weights[i];
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new java.awt.Insets(0, i == 0 ? 0 : 6, 0, i == cards.length - 1 ? 0 : 6);
            row.add(cards[i], gbc);
        }

        JPanel middle = new JPanel(new BorderLayout(12, 12));
        middle.setOpaque(false);
        middle.add(card("Events per hour of day (UTC)", hourChart), BorderLayout.NORTH);
        middle.add(row, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setOpaque(false);
        content.add(stats, BorderLayout.NORTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(card("Log", console), BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        computeButton.addActionListener(e -> runAnalytics());
        exportButton.addActionListener(e -> {
            if (studentsModel.getRowCount() == 0) {
                console.appendLine("Nothing to export yet — run the analytics first.");
                return;
            }
            CsvExporter.exportTable(this, studentsTable, "analytics_top_students.csv");
        });
    }

    // ------------------------------------------------------------------
    // Background computation
    // ------------------------------------------------------------------

    private void runAnalytics() {
        computeButton.setEnabled(false);
        exportButton.setEnabled(false);
        statusLabel.setText("Running analytics over 100,000 events ...");
        console.appendLine(String.format("Scanning %,d events (action mix, hourly/daily buckets, "
                + "top courses, top students) ...", dataStore.activityStream().size()));
        runAsync(() -> {
            long start = System.nanoTime();
            ActivityAnalytics.AnalyticsResult result =
                    ActivityAnalytics.analyze(dataStore.activityStream(), TOP_K);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Outcome(result, ms);
        }, outcome -> {
            computeButton.setEnabled(true);
            exportButton.setEnabled(true);
            populate(outcome.result);
            logResult(outcome.result, outcome.ms);
            statusLabel.setText(String.format("Done · %,d events analyzed in %d ms.",
                    outcome.result.summary.totalEvents, outcome.ms));
        }, error -> {
            computeButton.setEnabled(true);
            statusLabel.setText("Analytics failed: " + error.getMessage());
            console.appendLine("Error: " + error.getMessage());
            showError(error);
        });
    }

    private void populate(ActivityAnalytics.AnalyticsResult result) {
        ActivityAnalytics.Summary summary = result.summary;
        eventsValue.setText(String.format("%,d", summary.totalEvents));
        daysValue.setText(String.format("%,d", summary.distinctDays));
        avgValue.setText(String.format(Locale.US, "%,.1f", summary.avgEventsPerDay));
        peakValue.setText(String.format("%02d:00", summary.peakHour));
        coursePctValue.setText(String.format(Locale.US, "%.1f%%", summary.courseRelatedPercent));

        double[] actionValues = new double[ActivityAnalytics.KNOWN_ACTIONS.length];
        String[] actionLabels = new String[ActivityAnalytics.KNOWN_ACTIONS.length];
        for (int i = 0; i < ActivityAnalytics.KNOWN_ACTIONS.length; i++) {
            String action = ActivityAnalytics.KNOWN_ACTIONS[i];
            actionValues[i] = result.actionCounts.get(action);
            actionLabels[i] = action.replace('_', ' ');
        }
        actionChart.setData("", actionLabels, actionValues);

        String[] hourLabels = new String[ActivityAnalytics.HOURS_PER_DAY];
        double[] hourValues = new double[ActivityAnalytics.HOURS_PER_DAY];
        for (int h = 0; h < hourLabels.length; h++) {
            hourLabels[h] = String.format("%02d", h);
            hourValues[h] = result.hourCounts[h];
        }
        hourChart.setData("", hourLabels, hourValues);

        String[] courseLabels = new String[result.topCourses.size()];
        double[] courseValues = new double[courseLabels.length];
        for (int i = 0; i < courseLabels.length; i++) {
            courseLabels[i] = result.topCourses.get(i).courseCode;
            courseValues[i] = result.topCourses.get(i).events;
        }
        coursesChart.setData("", courseLabels, courseValues);

        studentsModel.setRowCount(0);
        Map<Integer, Student> byId = dataStore.studentsById();
        int rank = 1;
        for (ActivityAnalytics.StudentActivity activity : result.topStudents) {
            Student student = byId.get(activity.studentId);
            studentsModel.addRow(new Object[] {
                rank++, activity.studentId,
                student == null ? "—" : student.name,
                student == null ? "—" : student.program,
                activity.events
            });
        }
    }

    private void logResult(ActivityAnalytics.AnalyticsResult result, long ms) {
        ActivityAnalytics.Summary summary = result.summary;
        LocalDate firstDay = result.dayCounts.firstKey();
        LocalDate lastDay = result.dayCounts.lastKey();
        console.appendLine(String.format(Locale.US,
                "Summary: %,d events · %d active days (%s → %s UTC) · %,.1f events/day · peak %02d:00 "
                        + "(%,d events) · %.1f%% course-related · %d ms",
                summary.totalEvents, summary.distinctDays, firstDay, lastDay,
                summary.avgEventsPerDay, summary.peakHour, summary.peakHourCount,
                summary.courseRelatedPercent, ms));

        List<String> actionParts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : result.actionCounts.entrySet()) {
            actionParts.add(String.format("%,d %s", entry.getValue(), entry.getKey()));
        }
        console.appendLine("Per-action: " + String.join(" · ", actionParts));

        List<String> courseParts = new ArrayList<>();
        for (ActivityAnalytics.CourseActivity course : result.topCourses) {
            courseParts.add(String.format("%s %,d", course.courseCode, course.events));
        }
        console.appendLine("Top courses: " + String.join(" · ", courseParts));

        List<String> studentParts = new ArrayList<>();
        for (ActivityAnalytics.StudentActivity activity : result.topStudents) {
            studentParts.add(String.format("#%d %,d", activity.studentId, activity.events));
        }
        console.appendLine("Top students: " + String.join(" · ", studentParts));
    }

    private static final class Outcome {
        final ActivityAnalytics.AnalyticsResult result;
        final long ms;

        Outcome(ActivityAnalytics.AnalyticsResult result, long ms) {
            this.result = result;
            this.ms = ms;
        }
    }

    // ------------------------------------------------------------------
    // Small UI helpers
    // ------------------------------------------------------------------

    private JPanel statCard(String label, JLabel value) {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(label);
        name.setFont(GuiTheme.BODY);
        name.setForeground(GuiTheme.MUTED);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(value);
        inner.add(Box.createVerticalStrut(2));
        inner.add(name);
        return card(null, inner);
    }

    private static JLabel statValue() {
        JLabel label = new JLabel("—");
        label.setFont(GuiTheme.H1);
        label.setForeground(GuiTheme.ACCENT_DARK);
        return label;
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(GuiTheme.BODY);
        table.setForeground(GuiTheme.TEXT);
        table.setGridColor(GuiTheme.CARD_BORDER);
        table.setSelectionBackground(GuiTheme.ACCENT_SOFT);
        table.setSelectionForeground(GuiTheme.TEXT);
        return table;
    }
}
