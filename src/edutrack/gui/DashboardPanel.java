package edutrack.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import edutrack.data.DataStore;

public class DashboardPanel extends ModulePanel {

    private static final String[][] MODULES = {
        { "records", "Records Browser", "Students · Faculty · Courses — filter, sort, detail views with marks, GPA & activity" },
        { "manage", "Manage Records", "Add/edit students, faculty, courses · enrollments · marks entry · CSV persistence" },
        { "exams", "Exams & Grades", "Course statistics · Toppers · At-risk students · Report cards with GPA" },
        { "analytics", "Activity Analytics", "100k-event stream: actions, hourly rhythm, top courses & students" },
        { "reports", "Reports & Transcripts", "Transcripts · grade sheets · department summaries · at-risk reports" },
        { "m1", "M1 · Academic Search", "KMP · Z-Function · Rabin-Karp · Aho-Corasick" },
        { "m2", "M2 · Document Similarity", "Suffix Array · SA-IS · Kasai LCP · Suffix Automaton" },
        { "m3", "M3 · Query Correction & Optimization", "Levenshtein · Damerau · Matrix-Chain · Bitmask DP · Optimal BST" },
        { "m4", "M4 · Resource Allocation", "Hopcroft-Karp · Ford-Fulkerson · Edmonds-Karp · Dinic · König" },
        { "m5", "M5 · Exam Scheduling", "DPLL SAT · 3-SAT→CLIQUE · Reduction Chain · Vertex-Cover 2-Approx" },
        { "m6", "M6 · Ranking & Streams", "Randomized QuickSort · Parallel Merge Sort · Reservoir Sampling" }
    };

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
        JLabel subtitle = new JLabel("Six DSA modules over a live academic dataset. Pick a module below or from the sidebar.");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);

        JPanel stats = new JPanel(new GridLayout(1, 7, 12, 12));
        stats.setOpaque(false);
        stats.add(statCard("Students", dataStore.students().size()));
        stats.add(statCard("Faculty", dataStore.faculty().size()));
        stats.add(statCard("Courses", dataStore.courses().size()));
        stats.add(statCard("Assignments", dataStore.assignments().size()));
        stats.add(statCard("Resources", dataStore.resources().size()));
        stats.add(statCard("Exam Records", dataStore.examRecords().size()));
        stats.add(statCard("Activity Events", dataStore.activityStream().size()));

        JPanel grid = new JPanel(new GridLayout(4, 3, 12, 12));
        grid.setOpaque(false);
        for (String[] module : MODULES) {
            grid.add(moduleCard(module[0], module[1], module[2], navigator));
        }
        grid.add(apiCard());

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(grid, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private JPanel statCard(String label, int value) {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        JLabel number = new JLabel(String.format("%,d", value));
        number.setFont(GuiTheme.H1);
        number.setForeground(GuiTheme.ACCENT_DARK);
        number.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(label);
        name.setFont(GuiTheme.BODY);
        name.setForeground(GuiTheme.MUTED);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(number);
        inner.add(Box.createVerticalStrut(2));
        inner.add(name);
        return card(null, inner);
    }

    private JPanel moduleCard(String key, String name, String algorithms, Consumer<String> navigator) {
        JPanel inner = new JPanel(new BorderLayout(6, 6));
        inner.setOpaque(false);
        JLabel title = new JLabel(name);
        title.setFont(GuiTheme.H2);
        title.setForeground(GuiTheme.TEXT);
        JLabel desc = new JLabel("<html>" + algorithms + "</html>");
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
        JLabel desc = new JLabel("<html>Expose records, grades, search and analytics"
                + " to external academic systems over HTTP/JSON.</html>");
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
        if (apiServer != null && apiServer.isRunning()) {            apiServer.stop();
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
