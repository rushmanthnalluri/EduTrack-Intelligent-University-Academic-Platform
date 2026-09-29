package edutrack.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import edutrack.data.DataStore;
import edutrack.gui.panels.AcademicSearchPanel;
import edutrack.gui.panels.AnalyticsPanel;
import edutrack.gui.panels.DocumentSimilarityPanel;
import edutrack.gui.panels.ExamSchedulingPanel;
import edutrack.gui.panels.ExamsPanel;
import edutrack.gui.panels.ManagePanel;
import edutrack.gui.panels.QueryOptimizationPanel;
import edutrack.gui.panels.RankingStreamsPanel;
import edutrack.gui.panels.RecordsPanel;
import edutrack.gui.panels.ReportsPanel;
import edutrack.gui.panels.ResourceAllocationPanel;
import edutrack.gui.panels.SearchPanel;

public class EduTrackGUI extends JFrame {

    private final DataStore dataStore;
    private final JPanel cards = new JPanel(new CardLayout());
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private String currentCard = "dashboard";
    private DashboardPanel dashboard;
    private RecordsPanel recordsPanel;
    private JLabel statusCounts;

    public EduTrackGUI(DataStore dataStore) {
        super("EduTrack — Intelligent University Academic Platform");
        this.dataStore = dataStore;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1300, 820);
        setLocationRelativeTo(null);
        setIconImage(createAppIcon());

        cards.setBackground(GuiTheme.BG);

        dashboard = new DashboardPanel(dataStore, this::showCard);
        cards.add(dashboard, "dashboard");

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                dashboard.shutdown();
            }
        });

        recordsPanel = new RecordsPanel(dataStore);
        cards.add(recordsPanel, "records");
        cards.add(new ManagePanel(dataStore), "manage");
        cards.add(new ExamsPanel(dataStore), "exams");
        cards.add(new AnalyticsPanel(dataStore), "analytics");
        cards.add(new ReportsPanel(dataStore), "reports");

        SearchPanel searchPanel = new SearchPanel(dataStore);
        cards.add(searchPanel, "search");

        cards.add(new AcademicSearchPanel(dataStore), "academic-search");
        cards.add(new DocumentSimilarityPanel(dataStore), "document-similarity");
        cards.add(new QueryOptimizationPanel(dataStore), "query-optimization");
        cards.add(new ResourceAllocationPanel(dataStore), "resource-allocation");
        cards.add(new ExamSchedulingPanel(dataStore), "exam-scheduling");
        cards.add(new RankingStreamsPanel(dataStore), "ranking-streams");

        JPanel sidebar = buildSidebar(searchPanel);
        JPanel statusBar = buildStatusBar();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(sidebar, BorderLayout.WEST);
        getContentPane().add(cards, BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        showCard("dashboard");
    }

    public void showCard(String key) {
        if ("dashboard".equals(key)) {
            dashboard.refresh();
        } else if ("records".equals(key)) {
            recordsPanel.refresh();
        }

        currentCard = key;
        ((CardLayout) cards.getLayout()).show(cards, key);
        cards.revalidate();
        cards.repaint();
        refreshNavSelection();
        refreshStatusBar();
    }

    private JPanel buildSidebar(SearchPanel searchPanel) {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(GuiTheme.SIDEBAR_BG);
        sidebar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        sidebar.setPreferredSize(new Dimension(230, 0));

        JLabel brand = new JLabel("EDUTRACK");
        brand.setFont(GuiTheme.TITLE_FONT);
        brand.setForeground(java.awt.Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setBorder(BorderFactory.createEmptyBorder(20, 18, 0, 12));

        JLabel tagline = new JLabel("University Academic Platform");
        tagline.setFont(GuiTheme.BODY);
        tagline.setForeground(GuiTheme.SIDEBAR_FG);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagline.setBorder(BorderFactory.createEmptyBorder(2, 18, 8, 12));

        sidebar.add(brand);
        sidebar.add(tagline);

        javax.swing.JTextField searchField = new javax.swing.JTextField();
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GuiTheme.SIDEBAR_LINE),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        searchField.setBackground(new java.awt.Color(0x111B29));
        searchField.setForeground(java.awt.Color.WHITE);
        searchField.setCaretColor(java.awt.Color.WHITE);
        searchField.setFont(GuiTheme.BODY);
        searchField.setToolTipText("Smart search: courses, students, faculty, assignments, resources");
        searchField.putClientProperty("JTextField.placeholderText", "Search everything…");

        JPanel searchWrap = new JPanel();
        searchWrap.setLayout(new BoxLayout(searchWrap, BoxLayout.Y_AXIS));
        searchWrap.setOpaque(false);
        searchWrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, GuiTheme.SIDEBAR_LINE),
                BorderFactory.createEmptyBorder(6, 12, 12, 12)));
        searchWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        searchWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel searchLabel = new JLabel("SMART SEARCH");
        searchLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
        searchLabel.setForeground(GuiTheme.MUTED);
        searchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
        searchWrap.add(searchLabel);
        searchWrap.add(searchField);
        sidebar.add(searchWrap);

        searchField.addActionListener(e -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) {
                searchPanel.runQuery(query);
            }
            showCard("search");
        });

        addNavButton(sidebar, "dashboard", "Dashboard");
        addNavButton(sidebar, "records", "Records");
        addNavButton(sidebar, "manage", "Manage Records");
        addNavButton(sidebar, "exams", "Exams & Grades");
        addNavButton(sidebar, "analytics", "Activity Analytics");
        addNavButton(sidebar, "reports", "Reports & Transcripts");
        addNavButton(sidebar, "academic-search", "Academic Search");
        addNavButton(sidebar, "document-similarity", "Document Similarity");
        addNavButton(sidebar, "query-optimization", "Query Optimization");
        addNavButton(sidebar, "resource-allocation", "Resource Allocation");
        addNavButton(sidebar, "exam-scheduling", "Exam Scheduling");
        addNavButton(sidebar, "ranking-streams", "Ranking & Streams");

        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    private void addNavButton(JPanel sidebar, String key, String label) {
        JButton button = new JButton(label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(GuiTheme.BODY_BOLD);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, GuiTheme.SIDEBAR_LINE),
                BorderFactory.createEmptyBorder(12, 18, 12, 12)));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> showCard(key));
        navButtons.put(key, button);
        sidebar.add(button);
    }

    private void refreshNavSelection() {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            boolean selected = entry.getKey().equals(currentCard);
            entry.getValue().setBackground(selected ? GuiTheme.SIDEBAR_SELECTED : GuiTheme.SIDEBAR_BG);
            entry.getValue().setForeground(selected ? java.awt.Color.WHITE : GuiTheme.SIDEBAR_FG);
        }
    }

    private JPanel buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(GuiTheme.CARD_BG);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, GuiTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));

        JLabel left = new JLabel("Ready");
        left.setFont(GuiTheme.BODY);
        left.setForeground(GuiTheme.MUTED);

        statusCounts = new JLabel();
        statusCounts.setFont(GuiTheme.BODY);
        statusCounts.setForeground(GuiTheme.MUTED);

        status.add(left, BorderLayout.WEST);
        status.add(statusCounts, BorderLayout.EAST);
        refreshStatusBar();
        return status;
    }

    private void refreshStatusBar() {
        if (statusCounts == null) {
            return;
        }
        statusCounts.setText(String.format(
                "%,d students · %,d faculty · %,d courses · %,d assignments · %,d exam records · %,d activity events",
                dataStore.students().size(), dataStore.faculty().size(), dataStore.courses().size(),
                dataStore.assignments().size(), dataStore.examRecords().size(), dataStore.activityStream().size()));
    }

    private static java.awt.Image createAppIcon() {
        int size = 64;
        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = icon.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(GuiTheme.ACCENT);
        graphics.fillRoundRect(0, 0, size, size, 18, 18);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 38));
        java.awt.FontMetrics fm = graphics.getFontMetrics();
        graphics.drawString("E", (size - fm.stringWidth("E")) / 2,
                (size + fm.getAscent() - fm.getDescent()) / 2);
        graphics.dispose();
        return icon;
    }

    public static void main(String[] args) {
        GuiTheme.applyGlobalDefaults();
        DataStore dataStore = new DataStore();
        SwingUtilities.invokeLater(() -> new EduTrackGUI(dataStore).setVisible(true));
    }
}
