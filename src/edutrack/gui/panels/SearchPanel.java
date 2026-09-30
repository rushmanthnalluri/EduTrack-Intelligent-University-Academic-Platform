package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.Faculty;
import edutrack.model.LearningResource;
import edutrack.model.Student;
import edutrack.search.SearchResult;
import edutrack.search.SearchService;

/**
 * Smart Search panel: global cross-entity search with grouped results and
 * "Did you mean" suggestions. The query runs via runAsync; the constructor
 * only builds the UI.
 */
public class SearchPanel extends ModulePanel {

    private static final String HINT = "Type a query, e.g. 'data', 'cs301', 'aarav sharma'";

    private final JTextField queryField;
    private final JButton searchButton;
    private final JLabel statusLabel;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JPanel suggestionCard;
    private final JPanel suggestionBox;

    private List<SearchResult> lastResults = new ArrayList<>();

    public SearchPanel(DataStore dataStore) {
        super(dataStore);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Smart Search");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Global search across courses, students, faculty, assignments and resources"
                        + " — KMP substring matching with Levenshtein did-you-mean fallback");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        queryField = new JTextField(30);
        queryField.setFont(GuiTheme.H2);
        searchButton = GuiTheme.primaryButton("Search");
        statusLabel = new JLabel(HINT);
        statusLabel.setFont(GuiTheme.BODY_BOLD);
        statusLabel.setForeground(GuiTheme.MUTED);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        controls.add(queryField);
        controls.add(searchButton);

        JPanel searchRow = new JPanel(new BorderLayout(8, 8));
        searchRow.setOpaque(false);
        searchRow.add(controls, BorderLayout.NORTH);
        searchRow.add(statusLabel, BorderLayout.SOUTH);

        suggestionBox = new JPanel();
        suggestionBox.setLayout(new BoxLayout(suggestionBox, BoxLayout.Y_AXIS));
        suggestionBox.setOpaque(false);
        suggestionCard = card("No exact matches — did you mean?", suggestionBox);
        suggestionCard.setVisible(false);

        tableModel = new DefaultTableModel(new String[] { "Kind", "ID", "Title", "Details" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setFont(GuiTheme.BODY);
        table.setForeground(GuiTheme.TEXT);
        table.setGridColor(GuiTheme.CARD_BORDER);
        table.setSelectionBackground(GuiTheme.ACCENT_SOFT);
        table.setSelectionForeground(GuiTheme.TEXT);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(0).setMaxWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setMaxWidth(180);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(suggestionCard, BorderLayout.NORTH);
        center.add(card("Results", new JScrollPane(table)), BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(12, 12));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(card("Find students, courses, faculty, assignments, resources", searchRow),
                BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        searchButton.addActionListener(e -> executeSearch());
        queryField.addActionListener(e -> executeSearch());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < lastResults.size()) {
                            showDetails(lastResults.get(modelRow));
                        }
                    }
                }
            }
        });
    }

    /** Sets the query field and executes the search (callable from the main window). */
    public void runQuery(String query) {
        SwingUtilities.invokeLater(() -> {
            queryField.setText(query);
            executeSearch();
        });
    }

    private void executeSearch() {
        String query = queryField.getText().trim();
        if (query.isEmpty()) {
            tableModel.setRowCount(0);
            suggestionCard.setVisible(false);
            statusLabel.setText(HINT);
            statusLabel.setForeground(GuiTheme.MUTED);
            return;
        }
        searchButton.setEnabled(false);
        statusLabel.setText("Searching for '" + query + "' ...");
        statusLabel.setForeground(GuiTheme.MUTED);
        runAsync(() -> {
            long start = System.nanoTime();
            List<SearchResult> results = SearchService.search(dataStore, query);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new SearchOutcome(results, ms);
        }, outcome -> {
            searchButton.setEnabled(true);
            lastResults = outcome.results;
            tableModel.setRowCount(0);
            boolean suggestionsOnly = !outcome.results.isEmpty()
                    && outcome.results.get(0).kind.equals("Suggestion");
            for (SearchResult result : outcome.results) {
                tableModel.addRow(new Object[] {
                    result.kind, result.id, result.title, result.subtitle
                });
            }
            rebuildSuggestions(outcome.results, suggestionsOnly);
            if (outcome.results.isEmpty()) {
                statusLabel.setText("No matches in " + outcome.ms
                        + " ms — try a different spelling.");
                statusLabel.setForeground(GuiTheme.ERROR);
            } else if (suggestionsOnly) {
                statusLabel.setText("No exact matches — " + outcome.results.size()
                        + " suggestions in " + outcome.ms + " ms");
                statusLabel.setForeground(GuiTheme.ERROR);
            } else {
                statusLabel.setText(outcome.results.size() + " results in "
                        + outcome.ms + " ms");
                statusLabel.setForeground(GuiTheme.ACCENT_DARK);
            }
        }, error -> {
            searchButton.setEnabled(true);
            statusLabel.setText("Error: " + error.getMessage());
            statusLabel.setForeground(GuiTheme.ERROR);
            showError(error);
        });
    }

    private void rebuildSuggestions(List<SearchResult> results, boolean suggestionsOnly) {
        suggestionBox.removeAll();
        if (suggestionsOnly) {
            for (SearchResult result : results) {
                JLabel label = new JLabel(result.title + "   (" + result.subtitle + ")");
                label.setFont(GuiTheme.BODY_BOLD);
                label.setForeground(GuiTheme.ACCENT_DARK);
                label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                suggestionBox.add(label);
            }
        }
        suggestionCard.setVisible(suggestionsOnly);
        suggestionCard.revalidate();
        suggestionCard.repaint();
    }

    private void showDetails(SearchResult result) {
        String body;
        switch (result.kind) {
            case "Course":
                body = courseDetails(result.id);
                break;
            case "Student":
                body = studentDetails(result.id);
                break;
            case "Suggestion":
                body = suggestionDetails(result);
                break;
            case "Faculty":
                body = facultyDetails(result.id);
                break;
            case "Assignment":
                body = assignmentDetails(result.id);
                break;
            case "Resource":
                body = resourceDetails(result.id);
                break;
            default:
                body = result.title + "\n" + result.subtitle;
        }
        JOptionPane.showMessageDialog(this, body,
                result.kind + " — " + result.title, JOptionPane.INFORMATION_MESSAGE);
    }

    private String courseDetails(String code) {
        Course course = dataStore.coursesByCode().get(code);
        if (course == null) {
            return "Course " + code;
        }
        int enrolled = 0;
        for (Student student : dataStore.students()) {
            if (student.enrolledCourses.contains(code)) {
                enrolled++;
            }
        }
        return course.code + " — " + course.name
                + "\nDepartment : " + course.department
                + "\nCredits    : " + course.credits
                + "\nSemester   : " + course.semester
                + "\nEnrolled   : " + enrolled + " students";
    }

    private String studentDetails(String id) {
        Student student;
        try {
            student = dataStore.studentsById().get(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            student = null;
        }
        if (student == null) {
            return "Student " + id;
        }
        return student.id + " — " + student.name
                + "\nProgram  : " + student.program
                + "\nSemester : " + student.semester
                + String.format("%nCGPA     : %.2f", student.cgpa)
                + "\nCourses  : " + joinCapped(student.enrolledCourses, 8);
    }

    private String suggestionDetails(SearchResult result) {
        String id = result.id;
        String subtitle = result.subtitle == null ? "" : result.subtitle;
        if (subtitle.startsWith("Course ·")) return courseDetails(id);
        if (subtitle.startsWith("Student ·")) return studentDetails(id);
        if (subtitle.startsWith("Faculty ·")) return facultyDetails(id);
        if (subtitle.startsWith("Assignment ·")) return assignmentDetails(id);
        if (subtitle.startsWith("Resource ·")) return resourceDetails(id);
        return "No details available for " + id;
    }

    private String facultyDetails(String id) {
        for (Faculty faculty : dataStore.faculty()) {
            if (String.valueOf(faculty.id).equals(id)) {
                return faculty.id + " — " + faculty.name
                        + "\nDepartment : " + faculty.department
                        + "\nCan teach  : " + joinCapped(faculty.expertise, 10);
            }
        }
        return "Faculty " + id;
    }

    private String assignmentDetails(String id) {
        for (Assignment assignment : dataStore.assignments()) {
            if (assignment.id.equals(id)) {
                return assignment.id
                        + "\nTitle  : " + assignment.title
                        + "\nCourse : " + assignment.courseCode
                        + "\nText   : " + assignment.text.length() + " characters";
            }
        }
        return "Assignment " + id;
    }

    private String resourceDetails(String id) {
        for (LearningResource resource : dataStore.resources()) {
            if (resource.id.equals(id)) {
                return resource.id + " — " + resource.title
                        + "\nType   : " + resource.type
                        + "\nCourse : " + resource.courseCode;
            }
        }
        return "Resource " + id;
    }

    private static String joinCapped(List<String> items, int limit) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(items.size(), limit);
        for (int i = 0; i < shown; i++) {
            sb.append(items.get(i));
            if (i < shown - 1) {
                sb.append(", ");
            }
        }
        if (items.size() > shown) {
            sb.append(", …");
        }
        return sb.toString();
    }

    private static final class SearchOutcome {
        final List<SearchResult> results;
        final long ms;

        SearchOutcome(List<SearchResult> results, long ms) {
            this.results = results;
            this.ms = ms;
        }
    }
}
