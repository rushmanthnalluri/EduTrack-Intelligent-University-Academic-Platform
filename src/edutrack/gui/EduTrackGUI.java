package edutrack.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;

import edutrack.data.DataStore;
import edutrack.gui.panels.AcademicSearchPanel;
import edutrack.gui.panels.AnalyticsPanel;
import edutrack.gui.panels.BenchmarkArenaPanel;
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
    private ExamsPanel examsPanel;
    private ReportsPanel reportsPanel;
    private ResourceAllocationPanel resourceAllocationPanel;
    private RankingStreamsPanel rankingStreamsPanel;
    private AcademicSearchPanel academicSearchPanel;
    private DocumentSimilarityPanel documentSimilarityPanel;
    private JLabel statusCounts;
    private JLabel statusLeft;
    private JLabel statusMessage;

    public EduTrackGUI(DataStore dataStore) {
        super("EduTrack — Intelligent University Academic Platform");
        this.dataStore = dataStore;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int minWidth = Math.min(960, Math.max(720, screen.width - 40));
        int minHeight = Math.min(640, Math.max(560, screen.height - 80));
        int width = Math.min(1380, Math.max(minWidth, screen.width - 80));
        int height = Math.min(860, Math.max(minHeight, screen.height - 120));
        setMinimumSize(new Dimension(minWidth, minHeight));
        setSize(width, height);
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
        examsPanel = new ExamsPanel(dataStore);
        cards.add(examsPanel, "exams");
        cards.add(new AnalyticsPanel(dataStore), "analytics");
        cards.add(new BenchmarkArenaPanel(dataStore), "benchmark-arena");
        reportsPanel = new ReportsPanel(dataStore);
        cards.add(reportsPanel, "reports");

        SearchPanel searchPanel = new SearchPanel(dataStore);
        cards.add(searchPanel, "search");
        academicSearchPanel = new AcademicSearchPanel(dataStore);
        cards.add(academicSearchPanel, "academic-search");
        documentSimilarityPanel = new DocumentSimilarityPanel(dataStore);
        cards.add(documentSimilarityPanel, "document-similarity");
        cards.add(new QueryOptimizationPanel(dataStore), "query-optimization");
        resourceAllocationPanel = new ResourceAllocationPanel(dataStore);
        cards.add(resourceAllocationPanel, "resource-allocation");
        cards.add(new ExamSchedulingPanel(dataStore), "exam-scheduling");
        rankingStreamsPanel = new RankingStreamsPanel(dataStore);
        cards.add(rankingStreamsPanel, "ranking-streams");

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(GuiTheme.BG);
        getContentPane().add(buildSidebar(searchPanel), BorderLayout.WEST);
        getContentPane().add(cards, BorderLayout.CENTER);
        getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

        showCard("dashboard");
    }

    public void showCard(String key) {
        if ("dashboard".equals(key)) {
            dashboard.refresh();
        } else if ("records".equals(key)) {
            recordsPanel.refresh();
        } else if ("exams".equals(key)) {
            examsPanel.refresh();
        } else if ("reports".equals(key)) {
            reportsPanel.refresh();
        } else if ("resource-allocation".equals(key)) {
            resourceAllocationPanel.refresh();
        } else if ("ranking-streams".equals(key)) {
            rankingStreamsPanel.refresh();
        } else if ("academic-search".equals(key)) {
            academicSearchPanel.refresh();
        } else if ("document-similarity".equals(key)) {
            documentSimilarityPanel.refresh();
        }

        currentCard = key;
        ((CardLayout) cards.getLayout()).show(cards, key);
        cards.revalidate();
        cards.repaint();
        refreshNavSelection();
        refreshStatusBar();
    }

    private JScrollPane buildSidebar(SearchPanel searchPanel) {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(GuiTheme.SIDEBAR_BG);
        sidebar.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        sidebar.setPreferredSize(new Dimension(254, 720));

        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(22, 12, 18, 12));

        JLabel name = new JLabel("EDUTRACK");
        name.setFont(new Font("Segoe UI", Font.BOLD, 22));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("Academic Intelligence Platform");
        tagline.setFont(GuiTheme.SMALL);
        tagline.setForeground(new Color(0x9EABC0));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        brand.add(name);
        brand.add(Box.createVerticalStrut(4));
        brand.add(tagline);
        sidebar.add(brand);

        JTextField searchField = new JTextField();
        searchField.setMinimumSize(new Dimension(0, 38));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setFont(GuiTheme.BODY);
        searchField.setForeground(Color.WHITE);
        searchField.setBackground(new Color(0x202C42));
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(new CompoundBorder(
                new LineBorder(GuiTheme.SIDEBAR_LINE, 1, true),
                BorderFactory.createEmptyBorder(8, 11, 8, 11)));
        searchField.setToolTipText("Search courses, students, faculty, assignments and resources");
        searchField.putClientProperty("JTextField.placeholderText", "Search everything…");

        JPanel searchWrap = new JPanel();
        searchWrap.setLayout(new BoxLayout(searchWrap, BoxLayout.Y_AXIS));
        searchWrap.setOpaque(false);
        searchWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchWrap.setBorder(BorderFactory.createEmptyBorder(0, 4, 18, 4));

        JLabel searchLabel = new JLabel("QUICK SEARCH");
        searchLabel.setFont(GuiTheme.SMALL_BOLD);
        searchLabel.setForeground(new Color(0x8795AB));
        searchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchWrap.add(searchLabel);
        searchWrap.add(Box.createVerticalStrut(6));
        searchWrap.add(searchField);
        sidebar.add(searchWrap);

        searchField.addActionListener(e -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) {
                searchPanel.runQuery(query);
            }
            showCard("search");
        });

        addSectionLabel(sidebar, "WORKSPACE");
        addNavButton(sidebar, "dashboard", "Dashboard");
        addNavButton(sidebar, "records", "Records");
        addNavButton(sidebar, "manage", "Manage Records");
        addNavButton(sidebar, "exams", "Exams & Grades");
        addNavButton(sidebar, "analytics", "Activity Analytics");
        addNavButton(sidebar, "benchmark-arena", "Benchmark Arena");
        addNavButton(sidebar, "reports", "Reports & Transcripts");

        addSectionLabel(sidebar, "ALGORITHMS");
        addNavButton(sidebar, "academic-search", "Academic Search");
        addNavButton(sidebar, "document-similarity", "Document Similarity");
        addNavButton(sidebar, "query-optimization", "Query Optimization");
        addNavButton(sidebar, "resource-allocation", "Resource Allocation");
        addNavButton(sidebar, "exam-scheduling", "Exam Scheduling");
        addNavButton(sidebar, "ranking-streams", "Ranking & Streams");

        sidebar.add(Box.createVerticalGlue());

        JLabel hint = new JLabel("Select a module to get started");
        hint.setFont(GuiTheme.SMALL);
        hint.setForeground(new Color(0x7F8CA0));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(hint);

        JScrollPane scroll = new JScrollPane(sidebar);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(254, 0));
        scroll.getViewport().setBackground(GuiTheme.SIDEBAR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return scroll;
    }

    private void addSectionLabel(JPanel sidebar, String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.SMALL_BOLD);
        label.setForeground(new Color(0x738198));
        label.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 8));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(label);
    }

    private void addNavButton(JPanel sidebar, String key, String label) {
        JButton button = new JButton(label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(GuiTheme.BODY_BOLD);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBackground(GuiTheme.SIDEBAR_BG);
        button.setForeground(GuiTheme.SIDEBAR_FG);
        button.setBorder(new CompoundBorder(
                new LineBorder(GuiTheme.SIDEBAR_BG, 1, true),
                BorderFactory.createEmptyBorder(10, 13, 10, 10)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);

        button.addActionListener(e -> showCard(key));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!key.equals(currentCard)) {
                    button.setBackground(GuiTheme.SIDEBAR_HOVER);
                    button.setBorder(new CompoundBorder(
                            new LineBorder(GuiTheme.SIDEBAR_LINE, 1, true),
                            BorderFactory.createEmptyBorder(10, 13, 10, 10)));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!key.equals(currentCard)) {
                    button.setBackground(GuiTheme.SIDEBAR_BG);
                    button.setBorder(new CompoundBorder(
                            new LineBorder(GuiTheme.SIDEBAR_BG, 1, true),
                            BorderFactory.createEmptyBorder(10, 13, 10, 10)));
                }
            }
        });

        navButtons.put(key, button);
        sidebar.add(button);
    }

    private void refreshNavSelection() {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            boolean selected = entry.getKey().equals(currentCard);
            JButton button = entry.getValue();
            button.setBackground(selected ? GuiTheme.SIDEBAR_SELECTED : GuiTheme.SIDEBAR_BG);
            button.setForeground(selected ? GuiTheme.SIDEBAR_SELECTED_TEXT : GuiTheme.SIDEBAR_FG);
            button.setBorder(new CompoundBorder(
                    new LineBorder(selected ? GuiTheme.SIDEBAR_SELECTED : GuiTheme.SIDEBAR_BG, 1, true),
                    BorderFactory.createEmptyBorder(10, 13, 10, 10)));
        }
    }

    private JPanel buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(GuiTheme.SURFACE);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, GuiTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(7, 18, 7, 18)));

        statusMessage = new JLabel("Ready  •  " + currentCard.replace('-', ' '));
        statusMessage.setFont(GuiTheme.SMALL_BOLD);
        statusMessage.setForeground(GuiTheme.MUTED);

        statusCounts = new JLabel();
        statusCounts.setFont(GuiTheme.SMALL);
        statusCounts.setForeground(GuiTheme.MUTED);

        status.add(statusMessage, BorderLayout.WEST);
        status.add(statusCounts, BorderLayout.EAST);
        refreshStatusBar();
        return status;
    }

    private void refreshStatusBar() {
        if (statusMessage != null) {
            statusMessage.setText("Ready  •  " + currentCard.replace('-', ' '));
        }
        if (statusCounts == null) {
            return;
        }
        statusCounts.setText(String.format(
                "%,d students   •   %,d faculty   •   %,d courses   •   %,d assignments   •   %,d exams   •   %,d events",
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
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("Segoe UI", Font.BOLD, 38));
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
