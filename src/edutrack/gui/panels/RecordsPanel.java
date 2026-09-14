package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import edutrack.data.DataStore;
import edutrack.features.RecordQueries;
import edutrack.gui.CsvExporter;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Course;
import edutrack.model.Faculty;
import edutrack.model.Student;

/**
 * Records Browser: three tabs (Students / Faculty / Courses) with a live KMP
 * substring filter, sortable non-editable tables, CSV export and themed detail
 * dialogs backed by {@link RecordQueries}. The constructor only builds the UI;
 * detail bundles (the student one scans the 100k-event activity stream) load
 * via runAsync with the triggering button disabled meanwhile.
 */
public class RecordsPanel extends ModulePanel {

    private static final int ACTIVITY_TAIL = 25;

    private final Map<String, Integer> enrollmentByCourse;

    public RecordsPanel(DataStore dataStore) {
        super(dataStore);
        this.enrollmentByCourse = computeEnrollments(dataStore);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Records Browser");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Browse students, faculty and courses · KMP filter · sortable tables · detail views · CSV export");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.addTab("Students", buildStudentsTab());
        tabs.addTab("Faculty", buildFacultyTab());
        tabs.addTab("Courses", buildCoursesTab());

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private static Map<String, Integer> computeEnrollments(DataStore ds) {
        Map<String, Integer> map = new HashMap<>();
        for (Student s : ds.students()) {
            for (String code : s.enrolledCourses) {
                map.merge(code, 1, Integer::sum);
            }
        }
        return map;
    }

    // ------------------------------------------------------------------
    // Students tab
    // ------------------------------------------------------------------

    private JComponent buildStudentsTab() {
        JTextField filterField = new JTextField(22);
        JButton filterButton = GuiTheme.primaryButton("Filter");
        JButton clearButton = GuiTheme.secondaryButton("Clear");
        JButton detailsButton = GuiTheme.secondaryButton("Details…");
        JButton exportButton = GuiTheme.secondaryButton("Export CSV");
        JLabel countLabel = countLabel();

        RecordsTableModel model = new RecordsTableModel(
                new String[] { "ID", "Name", "Program", "Semester", "CGPA", "Courses" },
                new Class<?>[] { Integer.class, String.class, String.class,
                        Integer.class, Double.class, Integer.class });
        JTable table = styledTable(model);
        setWidths(table, 70, 220, 170, 90, 80, 90);

        JPanel controls = controlRow();
        controls.add(fieldLabel("Filter (name / program / id):"));
        controls.add(filterField);
        controls.add(filterButton);
        controls.add(clearButton);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(detailsButton);
        controls.add(exportButton);

        JPanel tab = tabPanel();
        tab.add(card("Filter & actions", controls), BorderLayout.NORTH);
        tab.add(card("Students", new JScrollPane(table)), BorderLayout.CENTER);
        tab.add(countLabel, BorderLayout.SOUTH);

        Runnable applyFilter = () -> {
            List<Student> rows = RecordQueries.filterStudents(dataStore, filterField.getText());
            model.setRowCount(0);
            for (Student s : rows) {
                model.addRow(new Object[] {
                        s.id, s.name, s.program, s.semester, s.cgpa, s.enrolledCourses.size()
                });
            }
            countLabel.setText(String.format("%d of %d students shown · double-click a row for details",
                    rows.size(), dataStore.students().size()));
        };
        applyFilter.run();
        wireFilter(filterField, filterButton, clearButton, applyFilter);
        exportButton.addActionListener(e -> CsvExporter.exportTable(this, table, "students.csv"));

        Runnable openDetails = () -> {
            int view = table.getSelectedRow();
            if (view < 0) {
                countLabel.setText("Select a student row first (or double-click it).");
                return;
            }
            int id = (Integer) model.getValueAt(table.convertRowIndexToModel(view), 0);
            detailsButton.setEnabled(false);
            countLabel.setText("Loading details for student " + id + " (scanning activity stream) …");
            runAsync(() -> RecordQueries.studentDetail(dataStore, id, ACTIVITY_TAIL), detail -> {
                detailsButton.setEnabled(true);
                applyFilter.run();
                RecordsDetailDialog.showStudent(SwingUtilities.getWindowAncestor(this), detail);
            }, error -> {
                detailsButton.setEnabled(true);
                applyFilter.run();
                showError(error);
            });
        };
        detailsButton.addActionListener(e -> openDetails.run());
        onDoubleClick(table, openDetails);
        return tab;
    }

    // ------------------------------------------------------------------
    // Faculty tab
    // ------------------------------------------------------------------

    private JComponent buildFacultyTab() {
        JTextField filterField = new JTextField(22);
        JButton filterButton = GuiTheme.primaryButton("Filter");
        JButton clearButton = GuiTheme.secondaryButton("Clear");
        JButton detailsButton = GuiTheme.secondaryButton("Details…");
        JButton exportButton = GuiTheme.secondaryButton("Export CSV");
        JLabel countLabel = countLabel();

        RecordsTableModel model = new RecordsTableModel(
                new String[] { "ID", "Name", "Department", "Expertise" },
                new Class<?>[] { Integer.class, String.class, String.class, String.class });
        JTable table = styledTable(model);
        setWidths(table, 70, 220, 170, 320);

        JPanel controls = controlRow();
        controls.add(fieldLabel("Filter (name / department):"));
        controls.add(filterField);
        controls.add(filterButton);
        controls.add(clearButton);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(detailsButton);
        controls.add(exportButton);

        JPanel tab = tabPanel();
        tab.add(card("Filter & actions", controls), BorderLayout.NORTH);
        tab.add(card("Faculty", new JScrollPane(table)), BorderLayout.CENTER);
        tab.add(countLabel, BorderLayout.SOUTH);

        Runnable applyFilter = () -> {
            List<Faculty> rows = RecordQueries.filterFaculty(dataStore, filterField.getText());
            model.setRowCount(0);
            for (Faculty f : rows) {
                model.addRow(new Object[] {
                        f.id, f.name, f.department, String.join(", ", f.expertise)
                });
            }
            countLabel.setText(String.format("%d of %d faculty shown · double-click a row for details",
                    rows.size(), dataStore.faculty().size()));
        };
        applyFilter.run();
        wireFilter(filterField, filterButton, clearButton, applyFilter);
        exportButton.addActionListener(e -> CsvExporter.exportTable(this, table, "faculty.csv"));

        Runnable openDetails = () -> {
            int view = table.getSelectedRow();
            if (view < 0) {
                countLabel.setText("Select a faculty row first (or double-click it).");
                return;
            }
            int id = (Integer) model.getValueAt(table.convertRowIndexToModel(view), 0);
            detailsButton.setEnabled(false);
            countLabel.setText("Loading details for faculty " + id + " …");
            runAsync(() -> RecordQueries.facultyDetail(dataStore, id), detail -> {
                detailsButton.setEnabled(true);
                applyFilter.run();
                RecordsDetailDialog.showFaculty(SwingUtilities.getWindowAncestor(this), detail);
            }, error -> {
                detailsButton.setEnabled(true);
                applyFilter.run();
                showError(error);
            });
        };
        detailsButton.addActionListener(e -> openDetails.run());
        onDoubleClick(table, openDetails);
        return tab;
    }

    // ------------------------------------------------------------------
    // Courses tab
    // ------------------------------------------------------------------

    private JComponent buildCoursesTab() {
        JTextField filterField = new JTextField(22);
        JButton filterButton = GuiTheme.primaryButton("Filter");
        JButton clearButton = GuiTheme.secondaryButton("Clear");
        JButton detailsButton = GuiTheme.secondaryButton("Details…");
        JButton exportButton = GuiTheme.secondaryButton("Export CSV");
        JLabel countLabel = countLabel();

        RecordsTableModel model = new RecordsTableModel(
                new String[] { "Code", "Name", "Department", "Credits", "Semester", "Enrolled" },
                new Class<?>[] { String.class, String.class, String.class,
                        Integer.class, Integer.class, Integer.class });
        JTable table = styledTable(model);
        setWidths(table, 80, 260, 150, 80, 90, 90);

        JPanel controls = controlRow();
        controls.add(fieldLabel("Filter (code / name / department):"));
        controls.add(filterField);
        controls.add(filterButton);
        controls.add(clearButton);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(detailsButton);
        controls.add(exportButton);

        JPanel tab = tabPanel();
        tab.add(card("Filter & actions", controls), BorderLayout.NORTH);
        tab.add(card("Courses", new JScrollPane(table)), BorderLayout.CENTER);
        tab.add(countLabel, BorderLayout.SOUTH);

        Runnable applyFilter = () -> {
            List<Course> rows = RecordQueries.filterCourses(dataStore, filterField.getText());
            model.setRowCount(0);
            for (Course c : rows) {
                model.addRow(new Object[] {
                        c.code, c.name, c.department, c.credits, c.semester,
                        enrollmentByCourse.getOrDefault(c.code, 0)
                });
            }
            countLabel.setText(String.format("%d of %d courses shown · double-click a row for details",
                    rows.size(), dataStore.courses().size()));
        };
        applyFilter.run();
        wireFilter(filterField, filterButton, clearButton, applyFilter);
        exportButton.addActionListener(e -> CsvExporter.exportTable(this, table, "courses.csv"));

        Runnable openDetails = () -> {
            int view = table.getSelectedRow();
            if (view < 0) {
                countLabel.setText("Select a course row first (or double-click it).");
                return;
            }
            String code = (String) model.getValueAt(table.convertRowIndexToModel(view), 0);
            detailsButton.setEnabled(false);
            countLabel.setText("Loading details for course " + code + " …");
            runAsync(() -> RecordQueries.courseDetail(dataStore, code), detail -> {
                detailsButton.setEnabled(true);
                applyFilter.run();
                RecordsDetailDialog.showCourse(SwingUtilities.getWindowAncestor(this), detail);
            }, error -> {
                detailsButton.setEnabled(true);
                applyFilter.run();
                showError(error);
            });
        };
        detailsButton.addActionListener(e -> openDetails.run());
        onDoubleClick(table, openDetails);
        return tab;
    }

    // ------------------------------------------------------------------
    // Shared UI helpers (package-private so RecordsDetailDialog can reuse)
    // ------------------------------------------------------------------

    static JPanel tabPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    static JPanel controlRow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);
        return panel;
    }

    static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY);
        label.setForeground(GuiTheme.TEXT);
        return label;
    }

    static JLabel countLabel() {
        JLabel label = new JLabel(" ");
        label.setFont(GuiTheme.BODY);
        label.setForeground(GuiTheme.MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        return label;
    }

    static final class RecordsTableModel extends DefaultTableModel {
        private final Class<?>[] columnClasses;

        RecordsTableModel(String[] columns, Class<?>[] columnClasses) {
            super(columns, 0);
            this.columnClasses = columnClasses;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return columnClasses[column];
        }
    }

    static JTable styledTable(RecordsTableModel model) {
        JTable table = new JTable(model);
        table.setFont(GuiTheme.BODY);
        table.setForeground(GuiTheme.TEXT);
        table.setGridColor(GuiTheme.CARD_BORDER);
        table.setSelectionBackground(GuiTheme.ACCENT_SOFT);
        table.setSelectionForeground(GuiTheme.TEXT);
        table.setRowSorter(new TableRowSorter<>(model));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        return table;
    }

    static void setWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void wireFilter(JTextField field, JButton filterButton, JButton clearButton,
            Runnable applyFilter) {
        field.addActionListener(e -> applyFilter.run());
        filterButton.addActionListener(e -> applyFilter.run());
        clearButton.addActionListener(e -> {
            if (!field.getText().isEmpty()) {
                field.setText("");
            } else {
                applyFilter.run();
            }
        });
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter.run();
            }
        });
    }

    private static void onDoubleClick(JTable table, Runnable action) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && table.rowAtPoint(e.getPoint()) >= 0) {
                    action.run();
                }
            }
        });
    }
}
