package edutrack.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JSpinner;

import edutrack.data.DataStore;
import edutrack.features.ExamAnalytics;

public class DashboardPanel extends ModulePanel {

    private static final String[][] MODULES = {
        { "records", "Records Browser", "Students · Faculty · Courses — filter, sort, detail views with marks, GPA & activity" },
        { "manage", "Manage Records", "Add/edit students, faculty, courses · enrollments · marks entry · CSV persistence" },
        { "exams", "Exams & Grades", "Course statistics · Toppers · At-risk students · Report cards with GPA" },
        { "analytics", "Activity Analytics", "Action mix, hourly rhythm, top courses & students" },
        { "benchmark-arena", "Benchmark Arena", "Live microsecond races across string, suffix and dynamic-programming algorithms" },
        { "reports", "Reports & Transcripts", "Transcripts · grade sheets · department summaries · at-risk reports" },
        { "academic-search", "Academic Search", "KMP · Z-Function · Rabin-Karp · Aho-Corasick" },
        { "document-similarity", "Document Similarity", "Suffix Array · SA-IS · Kasai LCP · Suffix Automaton" },
        { "query-optimization", "Query Optimization", "Levenshtein · Damerau · Matrix-Chain · Bitmask DP · Optimal BST" },
        { "resource-allocation", "Resource Allocation", "Hopcroft-Karp · Ford-Fulkerson · Edmonds-Karp · Dinic · König" },
        { "exam-scheduling", "Exam Scheduling", "DPLL SAT · 3-SAT→CLIQUE · Reduction Chain · Vertex-Cover 2-Approx" },
        { "ranking-streams", "Ranking & Streams", "Randomized QuickSort · Parallel Merge Sort · Reservoir Sampling" }
    };

    private final Map<String, JLabel> statValues = new LinkedHashMap<>();
    private final ChartCanvas gradeChart = new ChartCanvas();
    private final ChartCanvas programChart = new ChartCanvas();

    private edutrack.api.ApiServer apiServer;
    private JLabel apiStatus;
    private JButton apiButton;
    private JSpinner apiPort;

    public DashboardPanel(DataStore dataStore, Consumer<String> navigator) {
        super(dataStore);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("EduTrack — Intelligent University Academic Platform");
        title.setFont(GuiTheme.TITLE_FONT);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(
                "Academic records, performance analytics, reports and algorithmic tools over a live dataset.");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);

        JPanel stats = new JPanel(new GridLayout(0, 4, 14, 14));
        stats.setOpaque(false);
        stats.add(statCard("students", "Students", dataStore.students().size()));
        stats.add(statCard("faculty", "Faculty", dataStore.faculty().size()));
        stats.add(statCard("courses", "Courses", dataStore.courses().size()));
        stats.add(statCard("assignments", "Assignments", dataStore.assignments().size()));
        stats.add(statCard("resources", "Resources", dataStore.resources().size()));
        stats.add(statCard("examRecords", "Exam Records", dataStore.examRecords().size()));
        stats.add(statCard("activityEvents", "Activity Events", dataStore.activityStream().size()));

        gradeChart.setPreferredSize(new Dimension(380, 220));
        programChart.setPreferredSize(new Dimension(380, 220));
        programChart.setHorizontal(true);

        JPanel snapshots = new JPanel(new GridLayout(1, 2, 12, 12));
        snapshots.setOpaque(false);
        snapshots.setMinimumSize(new Dimension(0, 0));
        snapshots.add(card("Grade Distribution", gradeChart));
        snapshots.add(card("Students by Program", programChart));

        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
        grid.setOpaque(false);
        grid.setMinimumSize(new Dimension(0, 0));
        for (String[] module : MODULES) {
            grid.add(moduleCard(module[0], module[1], module[2], navigator));
        }
        grid.add(apiCard());

        DashboardScrollPanel content = new DashboardScrollPanel();
        content.setOpaque(false);
        content.setMinimumSize(new Dimension(0, 0));
        content.setBorder(BorderFactory.createEmptyBorder(2, 2, 18, 2));
        content.add(stats);
        content.add(Box.createVerticalStrut(12));
        content.add(snapshots);
        content.add(Box.createVerticalStrut(12));
        content.add(grid);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        refresh();
    }

    private static final class DashboardScrollPanel extends JPanel implements Scrollable {
        DashboardScrollPanel() {
            super();
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
            return Math.max(80, orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height - 32 : visibleRect.width - 32);
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

    public void refresh() {
        updateStat("students", dataStore.students().size());
        updateStat("faculty", dataStore.faculty().size());
        updateStat("courses", dataStore.courses().size());
        updateStat("assignments", dataStore.assignments().size());
        updateStat("resources", dataStore.resources().size());
        updateStat("examRecords", dataStore.examRecords().size());
        updateStat("activityEvents", dataStore.activityStream().size());

        int[] grades = ExamAnalytics.overallGradeDistribution(dataStore);
        double[] gradeValues = new double[grades.length];
        for (int i = 0; i < grades.length; i++) {
            gradeValues[i] = grades[i];
        }
        gradeChart.setData("", ExamAnalytics.GRADES, gradeValues);

        LinkedHashMap<String, Integer> programs = new LinkedHashMap<>();
        for (edutrack.model.Student student : dataStore.students()) {
            programs.merge(student.program, 1, Integer::sum);
        }
        String[] labels = programs.keySet().toArray(new String[0]);
        double[] values = new double[labels.length];
        for (int i = 0; i < labels.length; i++) {
            values[i] = programs.get(labels[i]);
        }
        programChart.setData("", labels, values);
    }

    private void updateStat(String key, int value) {
        JLabel label = statValues.get(key);
        if (label != null) {
            label.setText(String.format("%,d", value));
        }
    }

    private JPanel statCard(String key, String label, int value) {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JLabel number = new JLabel(String.format("%,d", value));
        number.setFont(GuiTheme.H1);
        number.setForeground(GuiTheme.ACCENT_DARK);
        number.setAlignmentX(Component.LEFT_ALIGNMENT);
        statValues.put(key, number);

        JLabel name = new JLabel(label);
        name.setFont(GuiTheme.BODY);
        name.setForeground(GuiTheme.MUTED);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        inner.add(number);
        inner.add(Box.createVerticalStrut(2));
        inner.add(name);
        return statSurface(inner);
    }

    private JPanel statSurface(JPanel content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(GuiTheme.SURFACE);
        panel.setMinimumSize(new Dimension(0, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GuiTheme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel moduleCard(String key, String name, String description, Consumer<String> navigator) {
        JPanel inner = new JPanel(new BorderLayout(6, 6));
        inner.setOpaque(false);

        JLabel title = new JLabel(name);
        title.setFont(GuiTheme.H2);
        title.setForeground(GuiTheme.TEXT);

        JLabel desc = new JLabel("<html><div style='width:250px'>" + description + "</div></html>");
        desc.setFont(GuiTheme.BODY);
        desc.setForeground(GuiTheme.MUTED);

        JButton open = GuiTheme.secondaryButton("Open →");
        open.addActionListener(e -> navigator.accept(key));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(open, BorderLayout.WEST);

        inner.add(title, BorderLayout.NORTH);
        inner.add(desc, BorderLayout.CENTER);
        inner.add(bottom, BorderLayout.SOUTH);
        return card(null, inner);
    }

    private JPanel apiCard() {
        JPanel inner = new JPanel(new BorderLayout(6, 6));
        inner.setOpaque(false);

        JLabel title = new JLabel("REST API Server");
        title.setFont(GuiTheme.H2);
        title.setForeground(GuiTheme.TEXT);

        JLabel desc = new JLabel(
                "<html><div style='width:250px'>Expose records, grades, search and analytics to external academic systems over HTTP/JSON.</div></html>");
        desc.setFont(GuiTheme.BODY);
        desc.setForeground(GuiTheme.MUTED);

        JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);

        JLabel portLabel = new JLabel("Port:");
        portLabel.setFont(GuiTheme.BODY);

        apiPort = new JSpinner(new javax.swing.SpinnerNumberModel(8080, 1, 65535, 1));
        apiButton = GuiTheme.primaryButton("Start API");
        apiStatus = new JLabel("stopped");
        apiStatus.setFont(GuiTheme.BODY);
        apiStatus.setForeground(GuiTheme.MUTED);

        controls.add(portLabel);
        controls.add(apiPort);
        controls.add(apiButton);
        controls.add(apiStatus);
        apiButton.addActionListener(e -> toggleApi());

        inner.add(title, BorderLayout.NORTH);
        inner.add(desc, BorderLayout.CENTER);
        inner.add(controls, BorderLayout.SOUTH);
        return card(null, inner);
    }

    public void shutdown() {
        if (apiServer != null && apiServer.isRunning()) {
            apiServer.stop();
        }
    }

    private void toggleApi() {
        if (apiServer != null && apiServer.isRunning()) {
            apiServer.stop();
            apiStatus.setText("stopped");
            apiStatus.setForeground(GuiTheme.MUTED);
            apiButton.setText("Start API");
            apiPort.setEnabled(true);
            return;
        }

        int port = ((Number) apiPort.getValue()).intValue();
        try {
            apiServer = edutrack.api.ApiServer.start(dataStore, port);
            apiStatus.setText("http://localhost:" + apiServer.getPort() + "/api/summary");
            apiStatus.setForeground(GuiTheme.SUCCESS);
            apiButton.setText("Stop API");
            apiPort.setEnabled(false);
        } catch (java.io.IOException ex) {
            showError(ex);
        }
    }
}
