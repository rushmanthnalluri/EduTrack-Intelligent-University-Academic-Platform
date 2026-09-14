package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import edutrack.data.DataStore;
import edutrack.gui.ConsoleArea;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Assignment;
import edutrack.modules.M2KasaiLCP;
import edutrack.modules.M2SAIS;
import edutrack.modules.M2SuffixArray;
import edutrack.modules.M2SuffixAutomaton;
import edutrack.modules.M2SuffixStructures;

public class M2Panel extends ModulePanel {

    private static final int WIKI_CAP = 200_000;
    private static final int DISPLAY_CAP = 20_000;
    private static final int MAX_HIGHLIGHTS = 1_500;
    private static final int TOP_REPEATS = 20;
    private static final String NONE = "—";

    private final JComboBox<DocItem> docCombo = new JComboBox<>();
    private final JLabel docInfo = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("Ready");

    private final JButton buildButton = GuiTheme.primaryButton("Build Index (doubling + SA-IS)");
    private final JLabel doublingLabel = valueLabel();
    private final JLabel saisLabel = valueLabel();
    private final JLabel matchLabel = valueLabel();
    private final JTextField queryField = new JTextField(24);
    private final JButton searchButton = GuiTheme.secondaryButton("Search & Highlight");
    private final JLabel queryInfo = new JLabel(" ");
    private final JTextPane docView = new JTextPane();
    private final JLabel docViewNote = new JLabel(" ");
    private final Highlighter.HighlightPainter matchPainter =
            new DefaultHighlighter.DefaultHighlightPainter(GuiTheme.HIGHLIGHT);
    private final ConsoleArea console = new ConsoleArea(8);

    private final JButton repeatsButton = GuiTheme.primaryButton("Find Repeated Phrases");
    private final JLabel repeatsInfo = new JLabel(" ");
    private final DefaultTableModel repeatsModel;

    private final JComboBox<String> simComboA;
    private final JComboBox<String> simComboB;
    private final JButton compareButton = GuiTheme.primaryButton("Compare");
    private final JLabel simLength = new JLabel(" ");
    private final JLabel simPhrase = new JLabel(" ");
    private final JLabel simOccA = new JLabel(" ");
    private final JLabel simOccB = new JLabel(" ");
    private final JLabel simAssessment = new JLabel(" ");

    private final JButton autoBuildButton = GuiTheme.primaryButton("Build Automaton");
    private final JLabel autoStates = valueLabel();
    private final JLabel autoDistinct = valueLabel();
    private final JLabel autoBuilt = valueLabel();
    private final JTextField autoQueryField = new JTextField(20);
    private final JButton autoCountButton = GuiTheme.secondaryButton("Count Occurrences");
    private final JLabel autoQueryResult = new JLabel(" ");
    private final JTextField autoLcsField = new JTextField(20);
    private final JButton autoLcsButton = GuiTheme.secondaryButton("Compute LCS");
    private final JLabel autoLcsResult = new JLabel(" ");

    private String currentText;
    private int displayLength;
    private int[] suffixArray;
    private M2SuffixAutomaton automaton;

    public M2Panel(DataStore dataStore) {
        super(dataStore);

        for (Assignment a : dataStore.assignments()) {
            docCombo.addItem(new DocItem(a.id + " (" + a.text.length() + " chars)", a.text, false));
        }
        docCombo.addItem(new DocItem("Wikipedia.txt (first " + WIKI_CAP + " chars)", null, true));

        repeatsModel = new DefaultTableModel(
                new String[] { "#", "Length", "Occurrences", "Positions (first 6)", "Phrase" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        String[] ids = dataStore.assignments().stream().map(a -> a.id).toArray(String[]::new);
        simComboA = new JComboBox<>(ids);
        simComboB = new JComboBox<>(ids);
        if (ids.length > 1) {
            simComboB.setSelectedIndex(1);
        }

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.addTab("Index & Search", buildIndexTab());
        tabs.addTab("Repeated Phrases (Kasai LCP)", buildRepeatsTab());
        tabs.addTab("Cross-Submission Similarity", buildSimilarityTab());
        tabs.addTab("Suffix Automaton", buildAutomatonTab());
        add(tabs, BorderLayout.CENTER);

        statusLabel.setFont(GuiTheme.BODY);
        statusLabel.setForeground(GuiTheme.MUTED);
        add(statusLabel, BorderLayout.SOUTH);

        wireActions();
        onDocumentSelected();
    }

    private Component buildHeader() {
        JPanel inner = new JPanel(new BorderLayout(6, 6));
        inner.setOpaque(false);
        JLabel title = new JLabel("M2 · Document Indexing & Similarity — Suffix Structures");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        JLabel subtitle = new JLabel("Suffix Array (prefix-doubling) · SA-IS · Kasai LCP · Suffix Automaton");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);

        JPanel selectorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        selectorRow.setOpaque(false);
        JLabel selectorLabel = new JLabel("Document:");
        selectorLabel.setFont(GuiTheme.BODY_BOLD);
        selectorRow.add(selectorLabel);
        selectorRow.add(docCombo);
        docInfo.setFont(GuiTheme.BODY);
        docInfo.setForeground(GuiTheme.MUTED);
        selectorRow.add(docInfo);

        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);
        textBlock.add(title);
        textBlock.add(Box.createVerticalStrut(2));
        textBlock.add(subtitle);

        inner.add(textBlock, BorderLayout.NORTH);
        inner.add(selectorRow, BorderLayout.CENTER);
        return card(null, inner);
    }

    private Component buildIndexTab() {
        JPanel buildRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buildRow.setOpaque(false);
        buildRow.add(buildButton);
        buildRow.add(labeled("Doubling:", doublingLabel));
        buildRow.add(Box.createHorizontalStrut(10));
        buildRow.add(labeled("SA-IS:", saisLabel));
        buildRow.add(Box.createHorizontalStrut(10));
        buildRow.add(labeled("Arrays match:", matchLabel));

        JPanel queryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        queryRow.setOpaque(false);
        JLabel queryLabel = new JLabel("Substring:");
        queryLabel.setFont(GuiTheme.BODY_BOLD);
        queryRow.add(queryLabel);
        queryRow.add(queryField);
        queryRow.add(searchButton);
        queryInfo.setFont(GuiTheme.BODY);
        queryInfo.setForeground(GuiTheme.MUTED);
        queryRow.add(queryInfo);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setOpaque(false);
        controls.add(buildRow);
        controls.add(queryRow);

        docView.setEditable(false);
        docView.setFont(GuiTheme.MONO);
        docView.setForeground(GuiTheme.TEXT);
        JScrollPane docScroll = new JScrollPane(docView);
        docViewNote.setFont(GuiTheme.BODY);
        docViewNote.setForeground(GuiTheme.MUTED);
        JPanel docPanel = new JPanel(new BorderLayout(4, 4));
        docPanel.setOpaque(false);
        docPanel.add(docScroll, BorderLayout.CENTER);
        docPanel.add(docViewNote, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true,
                card("Document (read-only, matches highlighted)", docPanel),
                card("Log", console));
        split.setResizeWeight(0.75);
        split.setBorder(BorderFactory.createEmptyBorder());

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tab.add(card("Suffix Array Index", controls), BorderLayout.NORTH);
        tab.add(split, BorderLayout.CENTER);
        return tab;
    }

    private Component buildRepeatsTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        controls.add(repeatsButton);
        repeatsInfo.setFont(GuiTheme.BODY);
        repeatsInfo.setForeground(GuiTheme.MUTED);
        controls.add(repeatsInfo);

        JTable table = new JTable(repeatsModel);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);
        table.getColumnModel().getColumn(4).setPreferredWidth(520);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tab.add(card("Longest Repeated Substrings within the Selected Document", controls),
                BorderLayout.NORTH);
        tab.add(card(null, new JScrollPane(table)), BorderLayout.CENTER);
        return tab;
    }

    private Component buildSimilarityTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("Assignment A:"));
        controls.add(simComboA);
        controls.add(Box.createHorizontalStrut(6));
        controls.add(new JLabel("Assignment B:"));
        controls.add(simComboB);
        controls.add(Box.createHorizontalStrut(6));
        controls.add(compareButton);

        JPanel results = new JPanel();
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setOpaque(false);
        styleResultLabel(simLength, true);
        styleResultLabel(simPhrase, false);
        styleResultLabel(simOccA, false);
        styleResultLabel(simOccB, false);
        styleResultLabel(simAssessment, true);
        results.add(simLength);
        results.add(Box.createVerticalStrut(6));
        results.add(simPhrase);
        results.add(Box.createVerticalStrut(6));
        results.add(simOccA);
        results.add(simOccB);
        results.add(Box.createVerticalStrut(6));
        results.add(simAssessment);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tab.add(card("Longest Common Substring between Two Submissions (SA + Kasai LCP)", controls),
                BorderLayout.NORTH);
        tab.add(card("Similarity Report", results), BorderLayout.CENTER);
        return tab;
    }

    private Component buildAutomatonTab() {
        JPanel buildRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buildRow.setOpaque(false);
        buildRow.add(autoBuildButton);
        buildRow.add(labeled("States:", autoStates));
        buildRow.add(Box.createHorizontalStrut(10));
        buildRow.add(labeled("Distinct substrings:", autoDistinct));
        buildRow.add(Box.createHorizontalStrut(10));
        buildRow.add(labeled("Built in:", autoBuilt));

        JPanel queryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        queryRow.setOpaque(false);
        queryRow.add(new JLabel("Substring X:"));
        queryRow.add(autoQueryField);
        queryRow.add(autoCountButton);
        autoQueryResult.setFont(GuiTheme.BODY);
        autoQueryResult.setForeground(GuiTheme.MUTED);
        queryRow.add(autoQueryResult);

        JPanel lcsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        lcsRow.setOpaque(false);
        lcsRow.add(new JLabel("Compare string Y:"));
        lcsRow.add(autoLcsField);
        lcsRow.add(autoLcsButton);
        autoLcsResult.setFont(GuiTheme.BODY);
        autoLcsResult.setForeground(GuiTheme.MUTED);
        lcsRow.add(autoLcsResult);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setOpaque(false);
        controls.add(buildRow);
        controls.add(queryRow);
        controls.add(lcsRow);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tab.add(card("Suffix Automaton of the Selected Document", controls), BorderLayout.NORTH);
        return tab;
    }

    private void wireActions() {
        docCombo.addActionListener(e -> onDocumentSelected());
        buildButton.addActionListener(e -> buildIndex(null));
        searchButton.addActionListener(e -> onSearch());
        queryField.addActionListener(e -> onSearch());
        repeatsButton.addActionListener(e -> onRepeats());
        compareButton.addActionListener(e -> onCompare());
        autoBuildButton.addActionListener(e -> buildAutomaton(null));
        autoCountButton.addActionListener(e -> onAutomatonCount());
        autoQueryField.addActionListener(e -> onAutomatonCount());
        autoLcsButton.addActionListener(e -> onAutomatonLcs());
        autoLcsField.addActionListener(e -> onAutomatonLcs());
    }

    private void onDocumentSelected() {
        DocItem item = (DocItem) docCombo.getSelectedItem();
        if (item == null) {
            return;
        }
        invalidateState();
        if (!item.wiki) {
            currentText = item.text;
            docInfo.setText(item.text.length() + " chars");
            setDocumentView(item.text);
            setStatus("Loaded " + item.label);
            return;
        }
        currentText = null;
        docInfo.setText("loading Wikipedia.txt ...");
        docView.setText("");
        docViewNote.setText(" ");
        setStatus("Loading Wikipedia.txt ...");
        runAsync(() -> {
            String wiki = dataStore.loadWikipediaText();
            int original = wiki.length();
            boolean truncated = original > WIKI_CAP;
            if (truncated) {
                wiki = wiki.substring(0, WIKI_CAP);
            }
            return new LoadedDoc(wiki, original, truncated);
        }, doc -> {
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            currentText = doc.text;
            String note = String.format("%,d chars", doc.text.length());
            if (doc.truncated) {
                note += String.format(" (truncated from %,d)", doc.originalLength);
            }
            docInfo.setText(note);
            setDocumentView(doc.text);
            setStatus("Wikipedia.txt loaded");
        }, err -> {
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            docInfo.setText("Wikipedia.txt unavailable");
            setStatus("Could not load Wikipedia.txt");
            showError(new IOException("Could not load Wikipedia.txt: " + err.getMessage()));
        });
    }

    private void invalidateState() {
        currentText = null;
        suffixArray = null;
        automaton = null;
        doublingLabel.setText(NONE);
        saisLabel.setText(NONE);
        matchLabel.setText(NONE);
        matchLabel.setForeground(GuiTheme.TEXT);
        queryInfo.setText(" ");
        repeatsModel.setRowCount(0);
        repeatsInfo.setText(" ");
        autoStates.setText(NONE);
        autoDistinct.setText(NONE);
        autoBuilt.setText(NONE);
        autoQueryResult.setText(" ");
        autoLcsResult.setText(" ");
        docView.getHighlighter().removeAllHighlights();
        console.clear();
    }

    private void setDocumentView(String text) {
        displayLength = Math.min(text.length(), DISPLAY_CAP);
        docView.setText(text.substring(0, displayLength));
        docView.setCaretPosition(0);
        if (text.length() > DISPLAY_CAP) {
            docViewNote.setText(String.format("Showing first %,d of %,d chars; searching covers the full document.",
                    DISPLAY_CAP, text.length()));
        } else {
            docViewNote.setText(" ");
        }
    }

    private void buildIndex(Runnable after) {
        final DocItem item = (DocItem) docCombo.getSelectedItem();
        final String text = currentText;
        if (item == null) {
            return;
        }
        if (text == null) {
            setStatus("Document is still loading ...");
            return;
        }
        buildButton.setEnabled(false);
        setStatus("Building suffix arrays for " + item.label + " ...");
        runAsync(() -> {
            long t0 = System.nanoTime();
            int[] doubling = M2SuffixArray.buildSuffixArray(text);
            long t1 = System.nanoTime();
            int[] sais = M2SAIS.buildSuffixArray(text);
            long t2 = System.nanoTime();
            return new IndexResult(doubling, Arrays.equals(doubling, sais), t1 - t0, t2 - t1);
        }, res -> {
            buildButton.setEnabled(true);
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            suffixArray = res.sa;
            doublingLabel.setText(fmtMs(res.doublingNanos));
            saisLabel.setText(fmtMs(res.saisNanos));
            matchLabel.setText(res.match ? "YES" : "NO");
            matchLabel.setForeground(res.match ? GuiTheme.SUCCESS : GuiTheme.ERROR);
            console.appendLine("index: " + String.format("%,d", text.length()) + " chars, doubling "
                    + fmtMs(res.doublingNanos) + ", SA-IS " + fmtMs(res.saisNanos)
                    + ", arrays match: " + (res.match ? "YES" : "NO"));
            setStatus("Index built for " + item.label);
            if (after != null) {
                after.run();
            }
        }, err -> {
            buildButton.setEnabled(true);
            setStatus("Index build failed");
            showError(err);
        });
    }

    private void onSearch() {
        if (currentText == null) {
            setStatus("Document is still loading ...");
            return;
        }
        if (queryField.getText().isEmpty()) {
            setStatus("Enter a substring to search.");
            return;
        }
        if (suffixArray == null) {
            setStatus("Index not built yet - building it first ...");
            buildIndex(this::runSearch);
            return;
        }
        runSearch();
    }

    private void runSearch() {
        final DocItem item = (DocItem) docCombo.getSelectedItem();
        final String text = currentText;
        final int[] sa = suffixArray;
        final String query = queryField.getText();
        if (item == null || text == null || sa == null || query.isEmpty()) {
            return;
        }
        searchButton.setEnabled(false);
        setStatus("Searching ...");
        runAsync(() -> {
            long t0 = System.nanoTime();
            List<Integer> positions = M2SuffixArray.findOccurrences(text, sa, query);
            long nanos = System.nanoTime() - t0;
            return new QueryResult(query, positions, nanos);
        }, res -> {
            searchButton.setEnabled(true);
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            int highlighted = applyHighlights(res.pattern, res.positions);
            StringBuilder info = new StringBuilder();
            info.append(res.positions.size()).append(" occurrence(s) in ").append(fmtMs(res.nanos));
            if (!res.positions.isEmpty()) {
                info.append(" at ").append(firstPositions(res.positions, 8));
            }
            if (highlighted < res.positions.size()) {
                info.append(String.format(" — highlighted %,d of %,d", highlighted, res.positions.size()));
            }
            queryInfo.setText(info.toString());
            console.appendLine("search \"" + abbrev(res.pattern) + "\": " + info);
            setStatus("Search complete");
        }, err -> {
            searchButton.setEnabled(true);
            setStatus("Search failed");
            showError(err);
        });
    }

    private int applyHighlights(String pattern, List<Integer> positions) {
        Highlighter highlighter = docView.getHighlighter();
        highlighter.removeAllHighlights();
        int m = pattern.length();
        int applied = 0;
        try {
            for (int p : positions) {
                if (applied >= MAX_HIGHLIGHTS) {
                    break;
                }
                if (p + m > displayLength) {
                    continue;
                }
                highlighter.addHighlight(p, p + m, matchPainter);
                applied++;
            }
        } catch (BadLocationException ignored) {
        }
        return applied;
    }

    private void onRepeats() {
        final DocItem item = (DocItem) docCombo.getSelectedItem();
        final String text = currentText;
        if (item == null) {
            return;
        }
        if (text == null) {
            setStatus("Document is still loading ...");
            return;
        }
        repeatsButton.setEnabled(false);
        setStatus("Computing Kasai LCP ...");
        runAsync(() -> {
            long t0 = System.nanoTime();
            int[] sa = suffixArray != null ? suffixArray : M2SuffixArray.buildSuffixArray(text);
            int[] lcp = M2KasaiLCP.buildLCP(text, sa);
            List<int[]> tops = M2SuffixStructures.topRepeatedSubstrings(text, sa, lcp, TOP_REPEATS);
            List<Object[]> rows = new ArrayList<>();
            int rank = 1;
            for (int[] entry : tops) {
                String phrase = text.substring(entry[1], entry[1] + entry[0]);
                List<Integer> occ = M2SuffixArray.findOccurrences(text, sa, phrase);
                rows.add(new Object[] {
                        rank++, entry[0], occ.size(), firstPositions(occ, 6), preview(phrase, 90) });
            }
            long nanos = System.nanoTime() - t0;
            return new RepeatsResult(sa, rows, nanos);
        }, res -> {
            repeatsButton.setEnabled(true);
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            suffixArray = res.sa;
            repeatsModel.setRowCount(0);
            for (Object[] row : res.rows) {
                repeatsModel.addRow(row);
            }
            repeatsInfo.setText(res.rows.size() + " distinct repeated phrase(s) in " + fmtMs(res.nanos));
            console.appendLine("kasai lcp: " + res.rows.size() + " repeated phrase(s), " + fmtMs(res.nanos));
            setStatus("Kasai LCP complete");
        }, err -> {
            repeatsButton.setEnabled(true);
            setStatus("Kasai LCP failed");
            showError(err);
        });
    }

    private void onCompare() {
        int indexA = simComboA.getSelectedIndex();
        int indexB = simComboB.getSelectedIndex();
        if (indexA < 0 || indexB < 0) {
            return;
        }
        Assignment a = dataStore.assignments().get(indexA);
        Assignment b = dataStore.assignments().get(indexB);
        compareButton.setEnabled(false);
        setStatus("Comparing " + a.id + " vs " + b.id + " ...");
        runAsync(() -> {
            long t0 = System.nanoTime();
            int[] r = M2SuffixStructures.crossLcs(a.text, b.text);
            long nanos = System.nanoTime() - t0;
            String phrase = r[0] > 0 ? a.text.substring(r[1], r[1] + r[0]) : "";
            List<Integer> inA = r[0] > 0 ? M2SuffixStructures.naiveFindAll(a.text, phrase) : List.of();
            List<Integer> inB = r[0] > 0 ? M2SuffixStructures.naiveFindAll(b.text, phrase) : List.of();
            return new SimResult(a.id, b.id, r[0], r[3], phrase, inA, inB, nanos);
        }, res -> {
            compareButton.setEnabled(true);
            if (res.length == 0) {
                simLength.setText(res.idA + " vs " + res.idB + ": no common substring found.");
                simPhrase.setText(" ");
                simOccA.setText(" ");
                simOccB.setText(" ");
                simAssessment.setText(" ");
            } else {
                simLength.setText(res.idA + " vs " + res.idB + ": LCS length " + res.length
                        + " chars (" + res.ties + " suffix pair(s)) in " + fmtMs(res.nanos));
                simPhrase.setText("<html>Shared phrase: \"" + escapeHtml(preview(res.phrase, 200))
                        + "\"</html>");
                simOccA.setText(res.idA + ": " + res.inA.size() + " occurrence(s) at "
                        + firstPositions(res.inA, 8));
                simOccB.setText(res.idB + ": " + res.inB.size() + " occurrence(s) at "
                        + firstPositions(res.inB, 8));
                simAssessment.setText(assessment(res.length));
                simAssessment.setForeground(res.length >= 40 ? GuiTheme.ERROR
                        : res.length >= 15 ? GuiTheme.ACCENT_DARK : GuiTheme.SUCCESS);
            }
            console.appendLine("similarity " + res.idA + " vs " + res.idB + ": lcs=" + res.length);
            setStatus("Comparison complete");
        }, err -> {
            compareButton.setEnabled(true);
            setStatus("Comparison failed");
            showError(err);
        });
    }

    private void buildAutomaton(Runnable after) {
        final DocItem item = (DocItem) docCombo.getSelectedItem();
        final String text = currentText;
        if (item == null) {
            return;
        }
        if (text == null) {
            setStatus("Document is still loading ...");
            return;
        }
        autoBuildButton.setEnabled(false);
        setStatus("Building suffix automaton ...");
        runAsync(() -> {
            long t0 = System.nanoTime();
            M2SuffixAutomaton built = M2SuffixAutomaton.build(text);
            long nanos = System.nanoTime() - t0;
            return new AutoResult(built, built.stateCount(), built.countDistinctSubstrings(), nanos);
        }, res -> {
            autoBuildButton.setEnabled(true);
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            automaton = res.automaton;
            autoStates.setText(String.format("%,d", res.states));
            autoDistinct.setText(String.format("%,d", res.distinct));
            autoBuilt.setText(fmtMs(res.nanos));
            console.appendLine("automaton: " + String.format("%,d", res.states) + " states, "
                    + String.format("%,d", res.distinct) + " distinct substrings, " + fmtMs(res.nanos));
            setStatus("Automaton built");
            if (after != null) {
                after.run();
            }
        }, err -> {
            autoBuildButton.setEnabled(true);
            setStatus("Automaton build failed");
            showError(err);
        });
    }

    private void onAutomatonCount() {
        if (currentText == null) {
            setStatus("Document is still loading ...");
            return;
        }
        if (autoQueryField.getText().isEmpty()) {
            setStatus("Enter a substring X first.");
            return;
        }
        if (automaton == null) {
            setStatus("Automaton not built yet - building it first ...");
            buildAutomaton(this::runAutomatonCount);
            return;
        }
        runAutomatonCount();
    }

    private void runAutomatonCount() {
        final DocItem item = (DocItem) docCombo.getSelectedItem();
        final M2SuffixAutomaton sam = automaton;
        final String query = autoQueryField.getText();
        if (item == null || sam == null || query.isEmpty()) {
            return;
        }
        autoCountButton.setEnabled(false);
        runAsync(() -> {
            long t0 = System.nanoTime();
            boolean occurs = sam.contains(query);
            long count = sam.countOccurrences(query);
            long nanos = System.nanoTime() - t0;
            return new AutoQueryResult(query, occurs, count, nanos);
        }, res -> {
            autoCountButton.setEnabled(true);
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            autoQueryResult.setText("occurs: " + (res.occurs ? "YES" : "NO") + ", "
                    + res.count + " occurrence(s), " + fmtMs(res.nanos));
            setStatus("Automaton query complete");
        }, err -> {
            autoCountButton.setEnabled(true);
            showError(err);
        });
    }

    private void onAutomatonLcs() {
        if (currentText == null) {
            setStatus("Document is still loading ...");
            return;
        }
        if (autoLcsField.getText().isEmpty()) {
            setStatus("Enter a compare string Y first.");
            return;
        }
        if (automaton == null) {
            setStatus("Automaton not built yet - building it first ...");
            buildAutomaton(this::runAutomatonLcs);
            return;
        }
        runAutomatonLcs();
    }

    private void runAutomatonLcs() {
        final DocItem item = (DocItem) docCombo.getSelectedItem();
        final M2SuffixAutomaton sam = automaton;
        final String other = autoLcsField.getText();
        if (item == null || sam == null || other.isEmpty()) {
            return;
        }
        autoLcsButton.setEnabled(false);
        runAsync(() -> {
            long t0 = System.nanoTime();
            int[] r = sam.longestCommonSubstring(other);
            long nanos = System.nanoTime() - t0;
            String witness = other.substring(r[1] - r[0], r[1]);
            return new AutoLcsResult(r[0], witness, nanos);
        }, res -> {
            autoLcsButton.setEnabled(true);
            if (docCombo.getSelectedItem() != item) {
                return;
            }
            if (res.length == 0) {
                autoLcsResult.setText("no common substring, " + fmtMs(res.nanos));
            } else {
                autoLcsResult.setText("LCS length " + res.length + ": \"" + preview(res.witness, 60)
                        + "\", " + fmtMs(res.nanos));
            }
            setStatus("Automaton LCS complete");
        }, err -> {
            autoLcsButton.setEnabled(true);
            showError(err);
        });
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel(NONE);
        label.setFont(GuiTheme.BODY_BOLD);
        label.setForeground(GuiTheme.TEXT);
        return label;
    }

    private static JPanel labeled(String name, JLabel value) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(GuiTheme.BODY);
        nameLabel.setForeground(GuiTheme.MUTED);
        panel.add(nameLabel);
        panel.add(value);
        return panel;
    }

    private static void styleResultLabel(JLabel label, boolean bold) {
        label.setFont(bold ? GuiTheme.BODY_BOLD : GuiTheme.BODY);
        label.setForeground(GuiTheme.TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static String assessment(int length) {
        if (length >= 40) {
            return "Assessment: long shared phrase - likely boilerplate or copied text.";
        }
        if (length >= 15) {
            return "Assessment: moderate phrase overlap between submissions.";
        }
        return "Assessment: minimal overlap only.";
    }

    private static String fmtMs(long nanos) {
        return String.format("%.3f ms", nanos / 1_000_000.0);
    }

    private static String preview(String s, int max) {
        String flat = s.replace('\n', ' ').replace('\r', ' ');
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max) + "...";
    }

    private static String abbrev(String s) {
        return s.length() <= 30 ? s : s.substring(0, 30) + "...";
    }

    private static String firstPositions(List<Integer> positions, int limit) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(positions.size(), limit);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(positions.get(i));
        }
        if (positions.size() > shown) {
            sb.append(" ... (+").append(positions.size() - shown).append(" more)");
        }
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class DocItem {
        final String label;
        final String text;
        final boolean wiki;

        DocItem(String label, String text, boolean wiki) {
            this.label = label;
            this.text = text;
            this.wiki = wiki;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class LoadedDoc {
        final String text;
        final int originalLength;
        final boolean truncated;

        LoadedDoc(String text, int originalLength, boolean truncated) {
            this.text = text;
            this.originalLength = originalLength;
            this.truncated = truncated;
        }
    }

    private static final class IndexResult {
        final int[] sa;
        final boolean match;
        final long doublingNanos;
        final long saisNanos;

        IndexResult(int[] sa, boolean match, long doublingNanos, long saisNanos) {
            this.sa = sa;
            this.match = match;
            this.doublingNanos = doublingNanos;
            this.saisNanos = saisNanos;
        }
    }

    private static final class QueryResult {
        final String pattern;
        final List<Integer> positions;
        final long nanos;

        QueryResult(String pattern, List<Integer> positions, long nanos) {
            this.pattern = pattern;
            this.positions = positions;
            this.nanos = nanos;
        }
    }

    private static final class RepeatsResult {
        final int[] sa;
        final List<Object[]> rows;
        final long nanos;

        RepeatsResult(int[] sa, List<Object[]> rows, long nanos) {
            this.sa = sa;
            this.rows = rows;
            this.nanos = nanos;
        }
    }

    private static final class SimResult {
        final String idA;
        final String idB;
        final int length;
        final int ties;
        final String phrase;
        final List<Integer> inA;
        final List<Integer> inB;
        final long nanos;

        SimResult(String idA, String idB, int length, int ties, String phrase,
                List<Integer> inA, List<Integer> inB, long nanos) {
            this.idA = idA;
            this.idB = idB;
            this.length = length;
            this.ties = ties;
            this.phrase = phrase;
            this.inA = inA;
            this.inB = inB;
            this.nanos = nanos;
        }
    }

    private static final class AutoResult {
        final M2SuffixAutomaton automaton;
        final int states;
        final long distinct;
        final long nanos;

        AutoResult(M2SuffixAutomaton automaton, int states, long distinct, long nanos) {
            this.automaton = automaton;
            this.states = states;
            this.distinct = distinct;
            this.nanos = nanos;
        }
    }

    private static final class AutoQueryResult {
        final String query;
        final boolean occurs;
        final long count;
        final long nanos;

        AutoQueryResult(String query, boolean occurs, long count, long nanos) {
            this.query = query;
            this.occurs = occurs;
            this.count = count;
            this.nanos = nanos;
        }
    }

    private static final class AutoLcsResult {
        final int length;
        final String witness;
        final long nanos;

        AutoLcsResult(int length, String witness, long nanos) {
            this.length = length;
            this.witness = witness;
            this.nanos = nanos;
        }
    }
}
