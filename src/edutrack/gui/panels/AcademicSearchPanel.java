package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.ConsoleArea;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Assignment;
import edutrack.modules.M1AhoCorasick;
import edutrack.modules.M1StringAlgorithms;

/**
 * GUI panel for academic string search and matching: KMP keyword search,
 * Z-function repeated-phrase detection, Rabin-Karp code search and Aho-Corasick
 * multi-keyword scanning. All algorithm work runs via runAsync on SwingWorker
 * background threads; the constructor only builds the UI.
 */
public class AcademicSearchPanel extends ModulePanel {

    private JComboBox<String> zAssignmentCombo;
    private boolean refreshingAssignments;

    private static final String[] SCAN_TARGETS = {
        "Assignment texts", "Wikipedia document", "Both"
    };
    private static final int POSITION_DISPLAY_LIMIT = 12;
    private static final int PHRASE_DISPLAY_LENGTH = 80;

    public AcademicSearchPanel(DataStore dataStore) {
        super(dataStore);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Academic String Algorithms");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "KMP · Z-Function · Rabin-Karp · Aho-Corasick over courses, students, assignments and documents");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.addTab("KMP Search", buildKmpTab());
        tabs.addTab("Z-Function Repeats", buildZTab());
        tabs.addTab("Rabin-Karp", buildRabinKarpTab());
        tabs.addTab("Aho-Corasick", buildAhoCorasickTab());

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /** Refreshes assignment-backed selectors after CRUD changes. */
    public void refresh() {
        int previous = zAssignmentCombo == null ? 0 : zAssignmentCombo.getSelectedIndex();
        refreshingAssignments = true;
        try {
            refreshAssignmentSelector();
            if (zAssignmentCombo.getItemCount() > 0) {
                zAssignmentCombo.setSelectedIndex(Math.min(Math.max(previous, 0),
                        zAssignmentCombo.getItemCount() - 1));
            }
        } finally {
            refreshingAssignments = false;
        }
    }

    private void refreshAssignmentSelector() {
        if (zAssignmentCombo == null) {
            return;
        }
        int previous = zAssignmentCombo.getSelectedIndex();
        zAssignmentCombo.removeAllItems();
        List<Assignment> assignments = dataStore.assignments();
        for (int i = 0; i < assignments.size(); i++) {
            zAssignmentCombo.addItem((i + 1) + ")  " + assignments.get(i).id);
        }
        if (zAssignmentCombo.getItemCount() > 0) {
            zAssignmentCombo.setSelectedIndex(Math.min(Math.max(previous, 0),
                    zAssignmentCombo.getItemCount() - 1));
        }
    }

    // ------------------------------------------------------------------
    // Tab (a): KMP keyword search
    // ------------------------------------------------------------------

    private JComponent buildKmpTab() {
        JTextField keywordField = new JTextField("data", 18);
        JButton searchButton = GuiTheme.primaryButton("Search (KMP)");
        JLabel summary = summaryLabel("Enter a keyword and run the search.");

        DefaultTableModel model = tableModel("Record", "ID / Code", "Name",
                "Code match positions", "Name match positions");
        JTable table = styledTable(model);
        ConsoleArea console = new ConsoleArea(4);

        JPanel controls = controlRow();
        controls.add(fieldLabel("Keyword:"));
        controls.add(keywordField);
        controls.add(searchButton);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);
        south.add(summary, BorderLayout.NORTH);
        south.add(card("Log", console), BorderLayout.CENTER);

        JPanel tab = tabPanel();
        tab.add(card("Search course codes/names and student names", controls), BorderLayout.NORTH);
        tab.add(card("Matching records", new JScrollPane(table)), BorderLayout.CENTER);
        tab.add(south, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> {
            String keyword = keywordField.getText().trim();
            if (keyword.isEmpty()) {
                console.appendLine("Enter a keyword first.");
                return;
            }
            searchButton.setEnabled(false);
            console.appendLine("KMP search for '" + keyword + "' ...");
            runAsync(() -> {
                long start = System.nanoTime();
                List<M1StringAlgorithms.KmpMatch> matches =
                        M1StringAlgorithms.kmpSearchCoursesAndStudents(dataStore, keyword);
                long ms = (System.nanoTime() - start) / 1_000_000;
                return new KmpOutcome(matches, ms);
            }, outcome -> {
                searchButton.setEnabled(true);
                model.setRowCount(0);
                int courses = 0;
                int students = 0;
                int courseOcc = 0;
                int studentOcc = 0;
                for (M1StringAlgorithms.KmpMatch match : outcome.matches) {
                    model.addRow(new Object[] {
                        match.recordType, match.idOrCode, match.displayName,
                        match.codePositions.isEmpty() ? "—" : formatPositions(match.codePositions),
                        match.namePositions.isEmpty() ? "—" : formatPositions(match.namePositions)
                    }, searchButton);
                    if (match.recordType.equals("Course")) {
                        courses++;
                        courseOcc += match.occurrenceCount();
                    } else {
                        students++;
                        studentOcc += match.occurrenceCount();
                    }
                }
                summary.setText(String.format(
                        "%d courses (%d occurrences) · %d students (%d occurrences) · %d ms",
                        courses, courseOcc, students, studentOcc, outcome.ms));
                console.appendLine(String.format(
                        "Done: %d records matched, %d total occurrences, %d ms.",
                        outcome.matches.size(), courseOcc + studentOcc, outcome.ms));
            }, error -> {
                searchButton.setEnabled(true);
                console.appendLine("Error: " + error.getMessage());
                showError(error);
            }, scanButton);
        });
        return tab;
    }

    private static final class KmpOutcome {
        final List<M1StringAlgorithms.KmpMatch> matches;
        final long ms;

        KmpOutcome(List<M1StringAlgorithms.KmpMatch> matches, long ms) {
            this.matches = matches;
            this.ms = ms;
        }
    }

    // ------------------------------------------------------------------
    // Tab (b): Z-function repeated-phrase detection
    // ------------------------------------------------------------------

    private JComponent buildZTab() {
        zAssignmentCombo = new JComboBox<>();
        zAssignmentCombo.setFont(GuiTheme.BODY);
        refreshAssignmentSelector();
        JSpinner minLengthSpinner = new JSpinner(new SpinnerNumberModel(12, 1, 500, 1));
        JButton detectButton = GuiTheme.primaryButton("Detect repeats (Z-function)");
        JLabel summary = summaryLabel("Pick an assignment and detect repeated phrases.");

        DefaultTableModel model = tableModel("Repeated phrase", "Length",
                "Occurrences", "First index");
        JTable table = styledTable(model);
        ConsoleArea console = new ConsoleArea(4);

        JPanel controls = controlRow();
        controls.add(fieldLabel("Assignment:"));
        controls.add(zAssignmentCombo);
        controls.add(fieldLabel("Min phrase length:"));
        controls.add(minLengthSpinner);
        controls.add(detectButton);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);
        south.add(summary, BorderLayout.NORTH);
        south.add(card("Log", console), BorderLayout.CENTER);

        JPanel tab = tabPanel();
        tab.add(card("Repeated phrases inside one assignment text", controls), BorderLayout.NORTH);
        tab.add(card("Repeated phrases (subsumed sub-repeats removed)", new JScrollPane(table)),
                BorderLayout.CENTER);
        tab.add(south, BorderLayout.SOUTH);

        detectButton.addActionListener(e -> {
            List<Assignment> assignments = dataStore.assignments();
            int index = zAssignmentCombo.getSelectedIndex();
            int minLength = ((Number) minLengthSpinner.getValue()).intValue();
            if (index < 0 || index >= assignments.size()) {
                console.appendLine("Select an assignment first.");
                return;
            }
            Assignment assignment = assignments.get(index);
            detectButton.setEnabled(false);
            console.appendLine("Scanning " + assignment.id + " (text length "
                    + assignment.text.length() + ") with min length " + minLength + " ...");
            runAsync(() -> {
                long start = System.nanoTime();
                List<M1StringAlgorithms.RepeatedPhrase> raw =
                        M1StringAlgorithms.findRepeatedPhrases(assignment.text, minLength);
                List<M1StringAlgorithms.RepeatedPhrase> kept =
                        M1StringAlgorithms.filterSubsumedRepeats(raw);
                long ms = (System.nanoTime() - start) / 1_000_000;
                return new ZOutcome(raw.size(), kept, ms);
            }, outcome -> {
                detectButton.setEnabled(true);
                List<M1StringAlgorithms.RepeatedPhrase> phrases = new ArrayList<>(outcome.phrases);
                phrases.sort((a, b) -> {
                    if (a.count != b.count) {
                        return Integer.compare(b.count, a.count);
                    }
                    return Integer.compare(b.text.length(), a.text.length());
                });
                model.setRowCount(0);
                for (M1StringAlgorithms.RepeatedPhrase phrase : phrases) {
                    model.addRow(new Object[] {
                        shorten(phrase.text), phrase.text.length(), phrase.count,
                        phrase.firstPosition
                    });
                }
                summary.setText(String.format(
                        "%d distinct repeated phrases (%d after filtering) · %d ms",
                        outcome.rawCount, phrases.size(), outcome.ms));
                console.appendLine(String.format(
                        "Done: %d raw candidates, %d informative repeats, %d ms.",
                        outcome.rawCount, phrases.size(), outcome.ms));
            }, error -> {
                detectButton.setEnabled(true);
                console.appendLine("Error: " + error.getMessage());
                showError(error);
            }, detectButton);
        });
        return tab;
    }

    private static final class ZOutcome {
        final int rawCount;
        final List<M1StringAlgorithms.RepeatedPhrase> phrases;
        final long ms;

        ZOutcome(int rawCount, List<M1StringAlgorithms.RepeatedPhrase> phrases, long ms) {
            this.rawCount = rawCount;
            this.phrases = phrases;
            this.ms = ms;
        }
    }

    // ------------------------------------------------------------------
    // Tab (c): Rabin-Karp code search
    // ------------------------------------------------------------------

    private JComponent buildRabinKarpTab() {
        JTextField patternField = new JTextField("CS101", 14);
        JButton searchButton = GuiTheme.primaryButton("Search (Rabin-Karp)");
        JLabel summary = summaryLabel("Enter a code pattern and run the search.");

        DefaultTableModel model = tableModel("Record", "Type", "Hash hits",
                "Verified match positions", "Spurious");
        JTable table = styledTable(model);
        ConsoleArea console = new ConsoleArea(4);

        JPanel controls = controlRow();
        controls.add(fieldLabel("Code pattern:"));
        controls.add(patternField);
        controls.add(searchButton);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);
        south.add(summary, BorderLayout.NORTH);
        south.add(card("Log", console), BorderLayout.CENTER);

        JPanel tab = tabPanel();
        tab.add(card("Search assignment IDs and course codes", controls), BorderLayout.NORTH);
        tab.add(card("Matching records", new JScrollPane(table)), BorderLayout.CENTER);
        tab.add(south, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> {
            String pattern = patternField.getText().trim();
            if (pattern.isEmpty()) {
                console.appendLine("Enter a code pattern first.");
                return;
            }
            searchButton.setEnabled(false);
            console.appendLine("Rabin-Karp scan for '" + pattern + "' ...");
            runAsync(() -> {
                long start = System.nanoTime();
                M1StringAlgorithms.RKCorpusReport report =
                        M1StringAlgorithms.rabinKarpScanIds(dataStore, pattern);
                long ms = (System.nanoTime() - start) / 1_000_000;
                return new RKOutcome(report, ms);
            }, outcome -> {
                searchButton.setEnabled(true);
                model.setRowCount(0);
                for (M1StringAlgorithms.RKRecordScan record : outcome.report.matchedRecords) {
                    model.addRow(new Object[] {
                        record.recordId, record.recordType, record.hashHits,
                        formatPositions(record.verified), record.spurious()
                    }, searchButton);
                }
                summary.setText(String.format(
                        "%d records scanned · %d matched · %d hash hits · %d verified · %d spurious · %d ms",
                        outcome.report.recordsScanned, outcome.report.matchedRecords.size(),
                        outcome.report.totalHashHits, outcome.report.totalVerified,
                        outcome.report.spurious(), outcome.ms));
                console.appendLine(String.format(
                        "Done: %d verified matches in %d records, %d ms.",
                        outcome.report.totalVerified, outcome.report.matchedRecords.size(),
                        outcome.ms));
            }, error -> {
                searchButton.setEnabled(true);
                console.appendLine("Error: " + error.getMessage());
                showError(error);
            });
        });
        return tab;
    }

    private static final class RKOutcome {
        final M1StringAlgorithms.RKCorpusReport report;
        final long ms;

        RKOutcome(M1StringAlgorithms.RKCorpusReport report, long ms) {
            this.report = report;
            this.ms = ms;
        }
    }

    // ------------------------------------------------------------------
    // Tab (d): Aho-Corasick multi-keyword scan
    // ------------------------------------------------------------------

    private JComponent buildAhoCorasickTab() {
        DefaultListModel<String> keywordModel = new DefaultListModel<>();
        for (String keyword : M1StringAlgorithms.defaultKeywords()) {
            keywordModel.addElement(keyword);
        }
        JList<String> keywordList = new JList<>(keywordModel);
        keywordList.setFont(GuiTheme.BODY);
        keywordList.setVisibleRowCount(3);
        keywordList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTextField keywordField = new JTextField(12);
        JButton addButton = GuiTheme.secondaryButton("Add keyword");
        JComboBox<String> targetCombo = new JComboBox<>(SCAN_TARGETS);
        targetCombo.setFont(GuiTheme.BODY);
        JButton scanButton = GuiTheme.primaryButton("Scan (Aho-Corasick)");
        JLabel summary = summaryLabel("Add keywords if needed, pick a target and scan.");

        DefaultTableModel model = tableModel("Keyword", "Assignment hits",
                "Wikipedia hits", "Total");
        JTable table = styledTable(model);
        M1KeywordBarChart chart = new M1KeywordBarChart();
        ConsoleArea console = new ConsoleArea(4);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setOpaque(false);
        JPanel row1 = controlRow();
        row1.add(fieldLabel("New keyword:"));
        row1.add(keywordField);
        row1.add(addButton);
        row1.add(Box.createHorizontalStrut(12));
        row1.add(fieldLabel("Target:"));
        row1.add(targetCombo);
        row1.add(scanButton);
        JPanel row2 = controlRow();
        row2.add(fieldLabel("Active keywords:"));
        JScrollPane keywordScroll = new JScrollPane(keywordList);
        keywordScroll.setPreferredSize(new java.awt.Dimension(360, 66));
        row2.add(keywordScroll);
        controls.add(row1);
        controls.add(row2);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(card("Per-keyword hit counts", new JScrollPane(table)), BorderLayout.CENTER);
        center.add(card("Hit count chart", chart), BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);
        south.add(summary, BorderLayout.NORTH);
        south.add(card("Log", console), BorderLayout.CENTER);

        JPanel tab = tabPanel();
        tab.add(card("Multi-keyword scan", controls), BorderLayout.NORTH);
        tab.add(center, BorderLayout.CENTER);
        tab.add(south, BorderLayout.SOUTH);

        Runnable addKeyword = () -> {
            String keyword = keywordField.getText().trim().toLowerCase(Locale.ROOT);
            if (keyword.isEmpty()) {
                return;
            }
            if (!keywordModel.contains(keyword)) {
                keywordModel.addElement(keyword);
                console.appendLine("Added keyword '" + keyword + "'.");
            } else {
                console.appendLine("Keyword '" + keyword + "' is already in the set.");
            }
            keywordField.setText("");
        };
        addButton.addActionListener(e -> addKeyword.run());
        keywordField.addActionListener(e -> addKeyword.run());

        scanButton.addActionListener(e -> {
            List<String> keywords = new ArrayList<>();
            for (int i = 0; i < keywordModel.size(); i++) {
                keywords.add(keywordModel.getElementAt(i));
            }
            if (keywords.isEmpty()) {
                console.appendLine("Add at least one keyword first.");
                return;
            }
            int target = targetCombo.getSelectedIndex();
            boolean scanAssignments = target == 0 || target == 2;
            boolean scanWikipedia = target == 1 || target == 2;

            scanButton.setEnabled(false);
            console.appendLine("Building automaton over " + keywords.size() + " keywords ...");
            runAsync(() -> {
                AcOutcome outcome = new AcOutcome(keywords);
                long buildStart = System.nanoTime();
                M1AhoCorasick automaton = new M1AhoCorasick();
                for (String keyword : keywords) {
                    automaton.addPattern(keyword);
                }
                automaton.build();
                outcome.buildMs = (System.nanoTime() - buildStart) / 1_000_000;
                outcome.states = automaton.stateCount();

                if (scanAssignments) {
                    String corpus = M1StringAlgorithms.concatAssignmentTexts(dataStore)
                            .toLowerCase(Locale.ROOT);
                    long start = System.nanoTime();
                    M1AhoCorasick.Result result = automaton.search(corpus);
                    outcome.assignmentMs = (System.nanoTime() - start) / 1_000_000;
                    outcome.assignmentCounts = result.counts;
                    outcome.assignmentTotal = result.total;
                    outcome.assignmentChars = corpus.length();
                }
                if (scanWikipedia) {
                    try {
                        String wikipedia = dataStore.loadWikipediaText()
                                .toLowerCase(Locale.ROOT);
                        long start = System.nanoTime();
                        M1AhoCorasick.Result result = automaton.search(wikipedia);
                        outcome.wikiMs = (System.nanoTime() - start) / 1_000_000;
                        outcome.wikiCounts = result.counts;
                        outcome.wikiTotal = result.total;
                        outcome.wikiChars = wikipedia.length();
                    } catch (java.io.IOException ex) {
                        outcome.wikiError = ex.getMessage();
                    }
                }
                return outcome;
            }, outcome -> {
                scanButton.setEnabled(true);
                model.setRowCount(0);
                long[] totals = new long[outcome.keywords.size()];
                for (int i = 0; i < outcome.keywords.size(); i++) {
                    long assignmentHits = outcome.assignmentCounts == null
                            ? 0 : outcome.assignmentCounts[i];
                    long wikiHits = outcome.wikiCounts == null ? 0 : outcome.wikiCounts[i];
                    totals[i] = assignmentHits + wikiHits;
                    model.addRow(new Object[] {
                        outcome.keywords.get(i),
                        outcome.assignmentCounts == null ? "—" : assignmentHits,
                        outcome.wikiCounts == null ? "—" : wikiHits,
                        totals[i]
                    });
                }
                chart.setData(outcome.keywords, totals);

                StringBuilder summaryText = new StringBuilder();
                summaryText.append(outcome.keywords.size()).append(" keywords · ")
                        .append(outcome.states).append(" trie states");
                if (outcome.assignmentCounts != null) {
                    summaryText.append(" · assignments: ").append(outcome.assignmentTotal)
                            .append(" matches (").append(outcome.assignmentMs).append(" ms)");
                }
                if (outcome.wikiCounts != null) {
                    summaryText.append(" · wikipedia: ").append(outcome.wikiTotal)
                            .append(" matches (").append(outcome.wikiMs).append(" ms)");
                }
                summary.setText(summaryText.toString());

                console.appendLine("Automaton built in " + outcome.buildMs + " ms ("
                        + outcome.states + " states).");
                if (outcome.assignmentCounts != null) {
                    console.appendLine(String.format(
                            "Assignment texts: %,d chars, %d total matches, %d ms.",
                            outcome.assignmentChars, outcome.assignmentTotal,
                            outcome.assignmentMs));
                }
                if (outcome.wikiCounts != null) {
                    console.appendLine(String.format(
                            "Wikipedia: %,d chars, %d total matches, %d ms.",
                            outcome.wikiChars, outcome.wikiTotal, outcome.wikiMs));
                }
                if (outcome.wikiError != null) {
                    console.appendLine("Wikipedia unavailable: " + outcome.wikiError);
                    console.appendLine("Place DataSets/Wikipedia.txt under the working directory.");
                }
            }, error -> {
                scanButton.setEnabled(true);
                console.appendLine("Error: " + error.getMessage());
                showError(error);
            });
        });
        return tab;
    }

    private static final class AcOutcome {
        final List<String> keywords;
        int states;
        long buildMs;
        long[] assignmentCounts;
        long assignmentTotal;
        long assignmentMs;
        int assignmentChars;
        long[] wikiCounts;
        long wikiTotal;
        long wikiMs;
        int wikiChars;
        String wikiError;

        AcOutcome(List<String> keywords) {
            this.keywords = keywords;
        }
    }

    // ------------------------------------------------------------------
    // Small UI helpers
    // ------------------------------------------------------------------

    private static JPanel tabPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    private static JPanel controlRow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY);
        label.setForeground(GuiTheme.TEXT);
        return label;
    }

    private static JLabel summaryLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY_BOLD);
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
        table.setAutoCreateRowSorter(true);
        return table;
    }

    private static String formatPositions(List<Integer> positions) {
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(positions.size(), POSITION_DISPLAY_LIMIT);
        for (int i = 0; i < limit; i++) {
            sb.append(positions.get(i));
            if (i < limit - 1) {
                sb.append(", ");
            }
        }
        if (positions.size() > limit) {
            sb.append(" … +").append(positions.size() - limit);
        }
        return sb.append("]").toString();
    }

    private static String shorten(String phrase) {
        if (phrase.length() <= PHRASE_DISPLAY_LENGTH) {
            return phrase;
        }
        return phrase.substring(0, PHRASE_DISPLAY_LENGTH - 3) + "...";
    }
}
